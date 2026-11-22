package com.ordermanagement.shared.common.paging;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Chuẩn hóa tham số phân trang từ client: page &gt;= 0, 1 &lt;= size &lt;= {@value #MAX_SIZE}.
 * Sort luôn do service quyết định, không nhận tên field từ client (tránh lộ cấu trúc entity
 * và lỗi 500 khi sort theo field không tồn tại).
 */
public final class PageRequests {

    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_SIZE = 100;

    private PageRequests() {
    }

    public static Pageable of(int page, int size, Sort sort) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_SIZE);
        return PageRequest.of(safePage, safeSize, sort);
    }
}
