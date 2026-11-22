package com.ordermanagement;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Dữ liệu cho integration test. Mọi bản ghi do test tạo đều có tiền tố nhận diện
 * ({@code it-...@test.invalid} cho user, {@code IT-...} cho SKU) nên {@link #cleanup} chỉ xóa đúng
 * phần đó và không đụng vào dữ liệu dev có sẵn trong cùng database.
 */
final class TestData {

    private static final String USER_FILTER = "(SELECT id FROM users WHERE email LIKE 'it-%@test.invalid')";
    private static final String ORDER_FILTER = "(SELECT id FROM orders WHERE user_id IN " + USER_FILTER + ")";
    private static final String PRODUCT_FILTER = "(SELECT id FROM products WHERE sku LIKE 'IT-%')";

    private TestData() {
    }

    static String uniqueEmail() {
        return "it-" + shortId().toLowerCase() + "@test.invalid";
    }

    static String uniqueSku() {
        return "IT-" + shortId().toUpperCase();
    }

    static String uniqueKey() {
        return "it-key-" + UUID.randomUUID();
    }

    /** Xóa theo thứ tự phụ thuộc khóa ngoại (tất cả FK đều RESTRICT). */
    static void cleanup(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM payments WHERE order_id IN " + ORDER_FILTER);
        jdbc.update("DELETE FROM order_items WHERE order_id IN " + ORDER_FILTER);
        jdbc.update("DELETE FROM orders WHERE user_id IN " + USER_FILTER);
        jdbc.update("DELETE FROM inventory WHERE product_id IN " + PRODUCT_FILTER);
        jdbc.update("DELETE FROM products WHERE sku LIKE 'IT-%'");
        jdbc.update("DELETE FROM user_roles WHERE user_id IN " + USER_FILTER);
        jdbc.update("DELETE FROM users WHERE email LIKE 'it-%@test.invalid'");
    }

    private static String shortId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
