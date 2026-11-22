package com.ordermanagement.payment.domain;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.ordermanagement.shared.common.persistence.AuditableEntity;
import com.ordermanagement.shared.exception.ConflictException;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Một lần (attempt) thanh toán cho một đơn. Chỉ giữ {@code orderId} (không map quan hệ sang entity
 * của {@code order}) để hai module không dính vào domain của nhau.
 */
@Entity
@Table(name = "payments")
public class Payment extends AuditableEntity {

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status = PaymentStatus.PENDING;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2, updatable = false)
    private BigDecimal amount;

    // Cột là char(3): cần khai báo CHAR để Hibernate schema validation khớp kiểu.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private String currency;

    @Column(name = "provider", nullable = false, length = 50, updatable = false)
    private String provider;

    @Column(name = "provider_reference", length = 150)
    private String providerReference;

    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false)
    private String idempotencyKey;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    protected Payment() {
    }

    public static Payment initiate(UUID orderId, int attemptNumber, BigDecimal amount, String currency,
                                   String provider, String idempotencyKey) {
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive");
        }
        Payment payment = new Payment();
        payment.orderId = orderId;
        payment.attemptNumber = attemptNumber;
        payment.status = PaymentStatus.PENDING;
        payment.amount = amount;
        payment.currency = currency;
        payment.provider = provider;
        payment.idempotencyKey = idempotencyKey;
        return payment;
    }

    /** PENDING → PROCESSING: đã gửi sang cổng thanh toán. */
    public void markProcessing() {
        transitionTo(PaymentStatus.PROCESSING);
    }

    /** PROCESSING → SUCCEEDED, ghi lại mã giao dịch của cổng thanh toán. */
    public void markSucceeded(String providerReference) {
        transitionTo(PaymentStatus.SUCCEEDED);
        this.providerReference = providerReference;
        this.failureCode = null;
    }

    /** PROCESSING → FAILED, ghi lại mã lỗi. */
    public void markFailed(String failureCode) {
        transitionTo(PaymentStatus.FAILED);
        this.failureCode = failureCode;
    }

    /** PENDING | PROCESSING → CANCELLED. */
    public void cancel() {
        transitionTo(PaymentStatus.CANCELLED);
    }

    /** SUCCEEDED → REFUNDED (đơn bị hủy sau khi đã thu tiền). */
    public void refund() {
        transitionTo(PaymentStatus.REFUNDED);
    }

    private void transitionTo(PaymentStatus next) {
        if (!status.canTransitionTo(next)) {
            throw new ConflictException("PAYMENT_INVALID_STATE",
                    "Payment cannot move from " + status + " to " + next + ".");
        }
        this.status = next;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderReference() {
        return providerReference;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getFailureCode() {
        return failureCode;
    }
}
