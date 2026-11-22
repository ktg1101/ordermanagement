package com.ordermanagement.order.domain;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import com.ordermanagement.shared.common.persistence.AuditableEntity;
import com.ordermanagement.shared.exception.ConflictException;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Aggregate root của đơn hàng. Tên entity JPA là {@code SalesOrder} vì {@code Order} là từ khóa
 * của JPQL/HQL ({@code ORDER BY}); tên bảng vẫn là {@code orders}.
 *
 * <p>Mọi chuyển trạng thái đi qua {@link #transitionTo(OrderStatus)} để quy tắc nằm ở một chỗ.
 */
@Entity(name = "SalesOrder")
@Table(name = "orders")
public class Order extends AuditableEntity {

    public static final String DEFAULT_CURRENCY = "VND";

    @Column(name = "order_number", nullable = false, length = 40, updatable = false)
    private String orderNumber;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OrderStatus status = OrderStatus.PENDING;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO.setScale(2);

    // Cột là char(3): cần khai báo CHAR để Hibernate schema validation (ddl-auto: validate) khớp kiểu.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("productSku ASC")
    @BatchSize(size = 50)
    private List<OrderItem> items = new ArrayList<>();

    protected Order() {
    }

    public static Order place(String orderNumber, UUID userId, String currency) {
        Order order = new Order();
        order.orderNumber = orderNumber;
        order.userId = userId;
        order.currency = currency;
        order.status = OrderStatus.PENDING;
        return order;
    }

    /** Thêm dòng hàng (chụp giá tại thời điểm này) và cộng vào tổng tiền. */
    public void addItem(UUID productId, String sku, String name, BigDecimal unitPrice, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got " + quantity);
        }
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
        OrderItem item = new OrderItem(this, productId, sku, name, unitPrice, quantity);
        items.add(item);
        totalAmount = totalAmount.add(item.lineTotal());
    }

    /** PENDING → CONFIRMED (khi thanh toán thành công). */
    public void confirm() {
        transitionTo(OrderStatus.CONFIRMED);
    }

    /** CONFIRMED → COMPLETED (đã xuất kho/giao hàng). */
    public void complete() {
        transitionTo(OrderStatus.COMPLETED);
    }

    /** PENDING | CONFIRMED → CANCELLED. */
    public void cancel() {
        transitionTo(OrderStatus.CANCELLED);
    }

    private void transitionTo(OrderStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new ConflictException("ORDER_INVALID_STATE",
                    "Order " + orderNumber + " cannot move from " + status + " to " + next + ".");
        }
        this.status = next;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public UUID getUserId() {
        return userId;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}
