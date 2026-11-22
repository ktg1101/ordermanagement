package com.ordermanagement.user.domain;

/** Khớp với {@code ck_users_status}. DISABLED là "xóa mềm": không cho đặt hàng nhưng giữ lịch sử đơn. */
public enum UserStatus {
    ACTIVE,
    DISABLED
}
