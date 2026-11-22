package com.ordermanagement.inventory.infrastructure;

import java.util.UUID;

import com.ordermanagement.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Các câu UPDATE có điều kiện: trả về số dòng bị ảnh hưởng, {@code 0} nghĩa là guard không thỏa
 * (thiếu hàng / không đủ hàng đang giữ). Mỗi câu là một thao tác nguyên tử ở mức dòng trong PostgreSQL,
 * nên không cần đọc-rồi-ghi và không có cửa sổ race.
 *
 * <p>Không dùng {@code clearAutomatically}: nó sẽ detach cả entity khác (ví dụ Order) đang được
 * sửa trong cùng transaction.
 */
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    /** Tạo dòng tồn kho (0/0) nếu chưa có. An toàn khi gọi song song. */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT INTO inventory (product_id) VALUES (:productId) ON CONFLICT (product_id) DO NOTHING",
            nativeQuery = true)
    int ensureRow(@Param("productId") UUID productId);

    /** Giữ hàng cho đơn: available -= qty, reserved += qty, chỉ khi còn đủ available. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE inventory "
            + "SET available_quantity = available_quantity - :quantity, "
            + "    reserved_quantity = reserved_quantity + :quantity, "
            + "    version = version + 1, updated_at = now() "
            + "WHERE product_id = :productId AND available_quantity >= :quantity",
            nativeQuery = true)
    int reserve(@Param("productId") UUID productId, @Param("quantity") int quantity);

    /** Trả hàng đã giữ (hủy đơn): reserved -= qty, available += qty. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE inventory "
            + "SET available_quantity = available_quantity + :quantity, "
            + "    reserved_quantity = reserved_quantity - :quantity, "
            + "    version = version + 1, updated_at = now() "
            + "WHERE product_id = :productId AND reserved_quantity >= :quantity",
            nativeQuery = true)
    int release(@Param("productId") UUID productId, @Param("quantity") int quantity);

    /** Xuất kho khi đơn hoàn tất: hàng đang giữ được tiêu thụ (reserved -= qty). */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE inventory "
            + "SET reserved_quantity = reserved_quantity - :quantity, "
            + "    version = version + 1, updated_at = now() "
            + "WHERE product_id = :productId AND reserved_quantity >= :quantity",
            nativeQuery = true)
    int consume(@Param("productId") UUID productId, @Param("quantity") int quantity);

    /** Điều chỉnh số lượng sẵn bán (nhập kho dương, kiểm kê âm). Không cho phép xuống dưới 0. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE inventory "
            + "SET available_quantity = available_quantity + :delta, "
            + "    version = version + 1, updated_at = now() "
            + "WHERE product_id = :productId AND available_quantity + :delta >= 0",
            nativeQuery = true)
    int adjustAvailable(@Param("productId") UUID productId, @Param("delta") int delta);
}
