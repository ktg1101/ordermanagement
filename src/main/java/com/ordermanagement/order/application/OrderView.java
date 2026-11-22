package com.ordermanagement.order.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ordermanagement.order.domain.OrderStatus;

/** Đơn hàng công khai: response của API. */
public record OrderView(
        UUID id,
        String orderNumber,
        UUID userId,
        OrderStatus status,
        BigDecimal totalAmount,
        String currency,
        List<OrderItemView> items,
        Instant createdAt,
        Instant updatedAt) {
}
