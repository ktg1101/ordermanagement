package com.ordermanagement.inventory.application;

import java.time.Instant;
import java.util.UUID;

/**
 * Tồn kho công khai. {@code availableQuantity} là số còn bán được,
 * {@code reservedQuantity} là số đang giữ cho các đơn chưa hoàn tất.
 */
public record InventoryView(UUID productId, int availableQuantity, int reservedQuantity, Instant updatedAt) {

    /** Sản phẩm chưa có dòng tồn kho: coi như 0/0. */
    static InventoryView empty(UUID productId) {
        return new InventoryView(productId, 0, 0, null);
    }
}
