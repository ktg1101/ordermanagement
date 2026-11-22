package com.ordermanagement.order.application;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ordermanagement.inventory.application.InventoryService;
import com.ordermanagement.inventory.application.ProductService;
import com.ordermanagement.inventory.application.ProductView;
import com.ordermanagement.inventory.application.StockLine;
import com.ordermanagement.order.domain.Order;
import com.ordermanagement.order.domain.OrderItem;
import com.ordermanagement.order.domain.OrderStatus;
import com.ordermanagement.order.infrastructure.OrderRepository;
import com.ordermanagement.shared.common.paging.PageRequests;
import com.ordermanagement.shared.common.paging.PageResponse;
import com.ordermanagement.shared.exception.BusinessRuleException;
import com.ordermanagement.shared.exception.NotFoundException;
import com.ordermanagement.user.application.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cổng vào duy nhất của module {@code order}. Phụ thuộc: {@code user} và {@code inventory}
 * (gồm sản phẩm), chỉ qua lớp {@code application} của chúng. {@code payment} gọi ngược vào đây qua
 * {@link #lockForPayment(UUID)} và {@link #markPaid(UUID)}.
 *
 * <p>Ranh giới transaction: mỗi method ghi là một transaction duy nhất bao trùm cả việc giữ/trả
 * hàng, đổi trạng thái đơn và (khi hủy) các phản ứng của {@code payment}. Mọi thay đổi đơn đều
 * khóa dòng đơn trước ({@code findByIdForUpdate}).
 */
@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final char[] ORDER_NUMBER_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final int ORDER_NUMBER_SUFFIX_LENGTH = 8;

    private final OrderRepository orderRepository;
    private final UserService userService;
    private final ProductService productService;
    private final InventoryService inventoryService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public OrderService(OrderRepository orderRepository,
                        UserService userService,
                        ProductService productService,
                        InventoryService inventoryService,
                        ApplicationEventPublisher eventPublisher,
                        Clock clock) {
        this.orderRepository = orderRepository;
        this.userService = userService;
        this.productService = productService;
        this.inventoryService = inventoryService;
        this.eventPublisher = eventPublisher;
        this.clock = clock;
    }

    /**
     * Đặt hàng: kiểm tra user/sản phẩm, chụp giá, giữ hàng, lưu đơn PENDING. Tất cả hoặc không gì cả:
     * nếu thiếu hàng, ném 409 {@code INSUFFICIENT_STOCK} và transaction rollback (không còn hàng bị giữ dở).
     * Các dòng trùng sản phẩm được gộp số lượng.
     */
    @Transactional
    public OrderView create(UUID userId, List<OrderLineCommand> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new BusinessRuleException("ORDER_EMPTY", "An order needs at least one item.");
        }
        userService.requireActive(userId);

        Map<UUID, Integer> quantities = new LinkedHashMap<>();
        for (OrderLineCommand line : lines) {
            quantities.merge(line.productId(), line.quantity(), Integer::sum);
        }
        Map<UUID, ProductView> products = productService.getSellableByIds(quantities.keySet());

        Order order = Order.place(generateOrderNumber(), userId, Order.DEFAULT_CURRENCY);
        quantities.forEach((productId, quantity) -> {
            ProductView product = products.get(productId);
            order.addItem(productId, product.sku(), product.name(), product.price(), quantity);
        });
        if (order.getTotalAmount().signum() <= 0) {
            // payments.amount phải > 0 nên đơn 0 đồng sẽ không bao giờ thanh toán được.
            throw new BusinessRuleException("ORDER_TOTAL_INVALID", "Order total must be greater than zero.");
        }

        inventoryService.reserve(stockLines(order));
        orderRepository.saveAndFlush(order);
        return toView(order);
    }

    public OrderView get(UUID id) {
        return toView(orderRepository.findById(id).orElseThrow(() -> new NotFoundException("Order", id)));
    }

    /** {@code userId} và {@code status} đều tùy chọn (null = không lọc). */
    public PageResponse<OrderView> list(UUID userId, OrderStatus status, int page, int size) {
        Collection<OrderStatus> statuses = status == null ? EnumSet.allOf(OrderStatus.class) : EnumSet.of(status);
        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        var result = userId == null
                ? orderRepository.findByStatusIn(statuses, pageable)
                : orderRepository.findByUserIdAndStatusIn(userId, statuses, pageable);
        return PageResponse.from(result.map(this::toView));
    }

    /**
     * Hủy đơn PENDING/CONFIRMED: trả hàng về kho và publish {@link OrderCancelledEvent}
     * để {@code payment} hủy/hoàn tiền. Gọi lại trên đơn đã hủy là no-op (idempotent).
     */
    @Transactional
    public OrderView cancel(UUID id) {
        Order order = findForUpdate(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return toView(order);
        }
        order.cancel(); // 409 ORDER_INVALID_STATE nếu đã COMPLETED
        inventoryService.release(stockLines(order));
        orderRepository.saveAndFlush(order);
        eventPublisher.publishEvent(new OrderCancelledEvent(order.getId()));
        return toView(order);
    }

    /** Hoàn tất đơn CONFIRMED: xuất kho (hàng đang giữ được tiêu thụ). Idempotent. */
    @Transactional
    public OrderView complete(UUID id) {
        Order order = findForUpdate(id);
        if (order.getStatus() == OrderStatus.COMPLETED) {
            return toView(order);
        }
        order.complete(); // 409 ORDER_INVALID_STATE nếu chưa thanh toán (PENDING) hoặc đã hủy
        inventoryService.consume(stockLines(order));
        orderRepository.saveAndFlush(order);
        return toView(order);
    }

    // ---- Hợp đồng dành cho module payment -------------------------------------------------

    /**
     * Khóa dòng đơn (giữ tới hết transaction của người gọi) và trả về thông tin cần để thanh toán.
     * Không ném lỗi khi đơn không còn PENDING; người gọi tự quyết định dựa vào {@code payable}.
     */
    @Transactional
    public PaymentOrderInfo lockForPayment(UUID orderId) {
        Order order = findForUpdate(orderId);
        return new PaymentOrderInfo(order.getId(), order.getTotalAmount(), order.getCurrency(),
                order.getStatus() == OrderStatus.PENDING);
    }

    /** Thanh toán thành công: PENDING → CONFIRMED. Hàng vẫn ở trạng thái đang giữ cho tới khi hoàn tất. */
    @Transactional
    public void markPaid(UUID orderId) {
        Order order = findForUpdate(orderId);
        order.confirm();
        orderRepository.saveAndFlush(order);
    }

    /** 404 nếu đơn không tồn tại. */
    public void requireExists(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new NotFoundException("Order", orderId);
        }
    }

    // ---- Nội bộ ----------------------------------------------------------------------------

    private Order findForUpdate(UUID id) {
        return orderRepository.findByIdForUpdate(id).orElseThrow(() -> new NotFoundException("Order", id));
    }

    private static List<StockLine> stockLines(Order order) {
        return order.getItems().stream()
                .map(item -> new StockLine(item.getProductId(), item.getQuantity()))
                .toList();
    }

    private OrderView toView(Order order) {
        List<OrderItemView> items = order.getItems().stream().map(OrderService::toView).toList();
        return new OrderView(order.getId(), order.getOrderNumber(), order.getUserId(), order.getStatus(),
                order.getTotalAmount(), order.getCurrency(), items, order.getCreatedAt(), order.getUpdatedAt());
    }

    private static OrderItemView toView(OrderItem item) {
        return new OrderItemView(item.getProductId(), item.getProductSku(), item.getProductName(),
                item.getUnitPrice(), item.getQuantity(), item.lineTotal());
    }

    /** Dạng {@code ORD-20261122-K7M2QX9A}: ngày (UTC) + 8 ký tự ngẫu nhiên, bỏ các ký tự dễ nhầm (0/O, 1/I). */
    private String generateOrderNumber() {
        StringBuilder number = new StringBuilder("ORD-")
                .append(DateTimeFormatter.BASIC_ISO_DATE.format(LocalDate.now(clock)))
                .append('-');
        for (int i = 0; i < ORDER_NUMBER_SUFFIX_LENGTH; i++) {
            number.append(ORDER_NUMBER_ALPHABET[random.nextInt(ORDER_NUMBER_ALPHABET.length)]);
        }
        return number.toString();
    }
}
