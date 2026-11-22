/**
 * Phần dùng chung, không phụ thuộc module nghiệp vụ nào.
 *
 * <ul>
 *   <li>{@code config}: cấu hình bean dùng chung (ví dụ {@code Clock}).</li>
 *   <li>{@code exception}: các lỗi nghiệp vụ ({@code ApiException} và con) và
 *       {@code GlobalExceptionHandler} trả về {@code application/problem+json}.</li>
 *   <li>{@code security}: mã hóa mật khẩu; chỗ đặt xác thực/phân quyền ở phase sau.</li>
 *   <li>{@code common}: tiện ích dùng chung (phân trang, entity gốc có audit).</li>
 * </ul>
 */
package com.ordermanagement.shared;
