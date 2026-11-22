package com.ordermanagement.inventory.application;

import java.util.UUID;

/** Một sản phẩm không đủ hàng, kèm số lượng đã yêu cầu. Được đưa vào body lỗi {@code INSUFFICIENT_STOCK}. */
public record StockShortage(UUID productId, int requestedQuantity) {
}
