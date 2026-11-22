package com.ordermanagement.inventory.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Dữ liệu sản phẩm công khai: response của API và hợp đồng cho {@code inventory}/{@code order}. */
public record ProductView(
        UUID id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
