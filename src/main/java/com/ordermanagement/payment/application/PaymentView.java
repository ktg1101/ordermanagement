package com.ordermanagement.payment.application;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.ordermanagement.payment.domain.PaymentStatus;

/** Thanh toán công khai: response của API. Không lộ {@code idempotency_key}. */
public record PaymentView(
        UUID id,
        UUID orderId,
        int attemptNumber,
        PaymentStatus status,
        BigDecimal amount,
        String currency,
        String provider,
        String providerReference,
        String failureCode,
        Instant createdAt,
        Instant updatedAt) {
}
