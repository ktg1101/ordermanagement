package com.ordermanagement.shared.common.paging;

import java.util.List;

import org.springframework.data.domain.Page;

/**
 * Dạng phân trang ổn định cho API (không serialize trực tiếp {@code PageImpl}
 * vì cấu trúc JSON của nó không phải hợp đồng được hứa).
 */
public record PageResponse<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
