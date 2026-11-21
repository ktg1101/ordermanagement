/**
 * Feature {@code user}: người dùng và phân quyền (bảng {@code users}, {@code roles},
 * {@code user_roles}).
 *
 * <p>Mỗi feature tự chứa các lớp của nó theo cấu trúc:
 * {@code api} (controller, DTO), {@code service}, {@code domain} (entity),
 * {@code repository}. Feature khác chỉ được gọi qua lớp {@code service} public,
 * không truy cập trực tiếp {@code repository} hay {@code domain} nội bộ.
 */
package org.ordermanagement.user;
