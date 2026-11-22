package com.ordermanagement.inventory.application;

import java.util.UUID;

/** Một dòng thao tác tồn kho: {@code quantity} đơn vị của {@code productId}. */
public record StockLine(UUID productId, int quantity) {

    public StockLine {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got " + quantity);
        }
    }
}
