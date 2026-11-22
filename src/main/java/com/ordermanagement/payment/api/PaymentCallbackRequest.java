package com.ordermanagement.payment.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.ordermanagement.payment.application.PaymentOutcome;

/**
 * Mô phỏng callback/webhook của cổng thanh toán. {@code providerReference} bắt buộc khi SUCCEEDED,
 * {@code failureCode} bắt buộc khi FAILED (kiểm tra ở service vì phụ thuộc giá trị của {@code outcome}).
 */
public record PaymentCallbackRequest(
        @NotNull PaymentOutcome outcome,
        @Size(max = 150) String providerReference,
        @Size(max = 100) String failureCode) {
}
