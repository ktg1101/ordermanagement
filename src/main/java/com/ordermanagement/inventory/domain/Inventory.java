package com.ordermanagement.inventory.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Tồn kho của một sản phẩm (khóa chính = {@code product_id}).
 *
 * <p>Entity này chỉ dùng để <b>đọc</b>. Mọi thay đổi số lượng đi qua các câu UPDATE có điều kiện
 * trong {@code InventoryRepository} (guard nằm trong mệnh đề WHERE và luôn tăng {@code version}),
 * nên hai request tranh nhau cùng một dòng không bao giờ làm số lượng âm hay bán vượt.
 */
@Entity
@Table(name = "inventory")
public class Inventory {

    @Id
    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "available_quantity", nullable = false)
    private int availableQuantity;

    @Column(name = "reserved_quantity", nullable = false)
    private int reservedQuantity;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Inventory() {
    }

    public UUID getProductId() {
        return productId;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }

    public int getReservedQuantity() {
        return reservedQuantity;
    }

    public long getVersion() {
        return version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
