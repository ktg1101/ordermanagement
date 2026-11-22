package com.ordermanagement.order.domain;

/**
 * Vòng đời đơn hàng (khớp {@code ck_orders_status}):
 * <pre>
 *   PENDING ──thanh toán thành công──▶ CONFIRMED ──xuất kho──▶ COMPLETED
 *      │                                  │
 *      └────────────── hủy ───────────────┴──▶ CANCELLED
 * </pre>
 * COMPLETED và CANCELLED là trạng thái kết thúc.
 */
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        return switch (this) {
            case PENDING -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == COMPLETED || next == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }
}
