package com.ordermanagement.order.application;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderItemView(
        UUID productId,
        String sku,
        String name,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal) {
}
