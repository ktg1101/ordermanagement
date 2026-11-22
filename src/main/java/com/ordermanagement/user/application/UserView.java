package com.ordermanagement.user.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ordermanagement.user.domain.UserStatus;

/**
 * Dữ liệu user công khai: vừa là response của API, vừa là hợp đồng để module khác đọc.
 * Cố ý không chứa {@code passwordHash}.
 */
public record UserView(
        UUID id,
        String email,
        String fullName,
        UserStatus status,
        List<String> roles,
        Instant createdAt,
        Instant updatedAt) {
}
