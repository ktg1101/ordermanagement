package com.ordermanagement.payment.application;

/**
 * Kết quả tạo thanh toán. {@code created == false} nghĩa là request này là lần gửi lại của một
 * Idempotency-Key đã dùng: trả lại đúng payment cũ thay vì tạo thêm (API trả 200 thay vì 201).
 */
public record PaymentCreation(PaymentView payment, boolean created) {
}
