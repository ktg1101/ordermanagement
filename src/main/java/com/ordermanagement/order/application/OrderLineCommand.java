package com.ordermanagement.order.application;

import java.util.UUID;

/** Một dòng của yêu cầu đặt hàng. */
public record OrderLineCommand(UUID productId, int quantity) {

    public OrderLineCommand {
        if (productId == null) {
            throw new IllegalArgumentException("productId is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, got " + quantity);
        }
    }
}
