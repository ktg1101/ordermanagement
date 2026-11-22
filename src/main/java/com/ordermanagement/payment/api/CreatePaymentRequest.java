package com.ordermanagement.payment.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code provider}: cổng thanh toán (ví dụ MOCK, VNPAY, MOMO). Hiện là chuỗi tự do, chuẩn hóa về chữ hoa. */
public record CreatePaymentRequest(@NotBlank @Size(max = 50) String provider) {
}
