package com.ordermanagement.payment.domain;

/**
 * Vòng đời một lần thanh toán (khớp {@code ck_payments_status}):
 * <pre>
 *   PENDING ──submit──▶ PROCESSING ──callback ok──▶ SUCCEEDED ──đơn bị hủy──▶ REFUNDED
 *      │                    │
 *      │                    └──callback lỗi──▶ FAILED
 *      └──────── hủy ───────┴──────────────▶ CANCELLED
 * </pre>
 * FAILED, CANCELLED, REFUNDED là trạng thái kết thúc. Muốn thử lại sau khi FAILED/CANCELLED thì tạo
 * một lần thanh toán mới (attempt_number tăng dần).
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    REFUNDED;

    public boolean canTransitionTo(PaymentStatus next) {
        return switch (this) {
            case PENDING -> next == PROCESSING || next == CANCELLED;
            case PROCESSING -> next == SUCCEEDED || next == FAILED || next == CANCELLED;
            case SUCCEEDED -> next == REFUNDED;
            case FAILED, CANCELLED, REFUNDED -> false;
        };
    }

    /** Lần thanh toán còn đang diễn ra (chiếm "chỗ" duy nhất của đơn). */
    public boolean isActive() {
        return this == PENDING || this == PROCESSING;
    }
}
