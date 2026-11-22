package com.ordermanagement.shared.common.paging;

import java.util.Locale;

/**
 * Dựng pattern cho {@code lower(col) like :pattern}. Luôn trả về chuỗi không null
 * (rỗng =&gt; {@code "%"}) để query không phải xử lý tham số null, và escape ký tự đặc biệt của LIKE
 * để người dùng gõ {@code %} hoặc {@code _} không biến thành wildcard.
 * PostgreSQL dùng dấu {@code \} làm ký tự escape mặc định cho LIKE.
 */
public final class LikePatterns {

    private LikePatterns() {
    }

    public static String contains(String text) {
        if (text == null || text.isBlank()) {
            return "%";
        }
        String escaped = text.trim()
                .toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
