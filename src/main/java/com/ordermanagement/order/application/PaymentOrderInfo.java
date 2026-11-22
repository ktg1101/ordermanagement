package com.ordermanagement.order.application;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Hợp đồng giữa {@code order} và {@code payment}: những gì thanh toán cần biết về đơn mà không phải
 * đụng tới entity của {@code order}. {@code payable} = đơn đang PENDING (còn có thể thanh toán).
 */
public record PaymentOrderInfo(UUID orderId, BigDecimal totalAmount, String currency, boolean payable) {
}
