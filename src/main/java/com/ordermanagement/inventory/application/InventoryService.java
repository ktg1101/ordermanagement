package com.ordermanagement.inventory.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import com.ordermanagement.inventory.domain.Inventory;
import com.ordermanagement.inventory.infrastructure.InventoryRepository;
import com.ordermanagement.shared.exception.BusinessRuleException;
import com.ordermanagement.shared.exception.ConflictException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cổng vào duy nhất của module {@code inventory} cho phần tồn kho.
 *
 * <p>Vòng đời tồn kho của một đơn:
 * <pre>
 *   tạo đơn      reserve  : available -= q, reserved += q
 *   hủy đơn      release  : reserved  -= q, available += q
 *   hoàn tất đơn consume  : reserved  -= q          (hàng đã xuất kho)
 * </pre>
 *
 * <p>Các method ghi dùng {@code @Transactional} mặc định (REQUIRED) nên khi được {@code order} gọi,
 * chúng chạy chung transaction với đơn hàng: lỗi ở bất kỳ bước nào sẽ rollback cả việc giữ hàng.
 */
@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final ProductService productService;

    public InventoryService(InventoryRepository inventoryRepository, ProductService productService) {
        this.inventoryRepository = inventoryRepository;
        this.productService = productService;
    }

    public InventoryView get(UUID productId) {
        productService.get(productId); // 404 nếu sản phẩm không tồn tại
        return inventoryRepository.findById(productId)
                .map(InventoryService::toView)
                .orElseGet(() -> InventoryView.empty(productId));
    }

    /** Nhập kho (delta &gt; 0) hoặc kiểm kê giảm (delta &lt; 0). Tự tạo dòng tồn kho nếu chưa có. */
    @Transactional
    public InventoryView adjust(UUID productId, int delta) {
        productService.get(productId);
        inventoryRepository.ensureRow(productId);
        if (delta != 0 && inventoryRepository.adjustAvailable(productId, delta) == 0) {
            throw new BusinessRuleException("STOCK_CANNOT_BE_NEGATIVE",
                    "Adjustment of " + delta + " would make available stock negative.");
        }
        // Entity chưa được nạp trong persistence context của transaction này nên đọc ra giá trị mới nhất.
        return inventoryRepository.findById(productId)
                .map(InventoryService::toView)
                .orElseThrow(() -> new IllegalStateException("Inventory row missing for " + productId));
    }

    /**
     * Giữ hàng cho nhiều sản phẩm, tất cả hoặc không gì cả. Nếu có sản phẩm thiếu hàng sẽ ném
     * 409 {@code INSUFFICIENT_STOCK} liệt kê <i>toàn bộ</i> sản phẩm thiếu; transaction rollback
     * nên các dòng đã giữ trước đó được hoàn lại.
     */
    @Transactional
    public void reserve(Collection<StockLine> lines) {
        List<StockShortage> shortages = new ArrayList<>();
        for (StockLine line : normalize(lines)) {
            if (inventoryRepository.reserve(line.productId(), line.quantity()) == 0) {
                shortages.add(new StockShortage(line.productId(), line.quantity()));
            }
        }
        if (!shortages.isEmpty()) {
            throw new ConflictException("INSUFFICIENT_STOCK",
                    "Not enough stock for " + shortages.size() + " product(s).",
                    Map.of("shortages", shortages));
        }
    }

    /** Hoàn hàng đang giữ về kho (hủy đơn). */
    @Transactional
    public void release(Collection<StockLine> lines) {
        for (StockLine line : normalize(lines)) {
            if (inventoryRepository.release(line.productId(), line.quantity()) == 0) {
                throw reservationMismatch(line);
            }
        }
    }

    /** Xuất kho: hàng đang giữ được tiêu thụ (hoàn tất đơn). */
    @Transactional
    public void consume(Collection<StockLine> lines) {
        for (StockLine line : normalize(lines)) {
            if (inventoryRepository.consume(line.productId(), line.quantity()) == 0) {
                throw reservationMismatch(line);
            }
        }
    }

    /**
     * Gộp các dòng trùng sản phẩm và sắp theo productId. Mọi transaction đều khóa các dòng tồn kho
     * theo cùng một thứ tự nên hai đơn nhiều sản phẩm chạy song song không thể deadlock lẫn nhau.
     */
    private static List<StockLine> normalize(Collection<StockLine> lines) {
        Map<UUID, Integer> merged = new TreeMap<>(Comparator.naturalOrder());
        for (StockLine line : lines) {
            merged.merge(line.productId(), line.quantity(), Integer::sum);
        }
        return merged.entrySet().stream()
                .map(entry -> new StockLine(entry.getKey(), entry.getValue()))
                .toList();
    }

    /** Dữ liệu tồn kho không khớp với đơn: lỗi hệ thống, không phải lỗi của client (=&gt; 500, rollback). */
    private static IllegalStateException reservationMismatch(StockLine line) {
        return new IllegalStateException("Reserved stock is lower than " + line.quantity()
                + " for product " + line.productId());
    }

    private static InventoryView toView(Inventory inventory) {
        return new InventoryView(inventory.getProductId(), inventory.getAvailableQuantity(),
                inventory.getReservedQuantity(), inventory.getUpdatedAt());
    }
}
