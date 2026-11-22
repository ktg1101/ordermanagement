/**
 * Module {@code user}: người dùng và vai trò (bảng {@code users}, {@code roles}, {@code user_roles}).
 *
 * <ul>
 *   <li>{@code api}: controller và request DTO (REST).</li>
 *   <li>{@code application}: use case ({@code UserService}) và view trả ra ngoài. Đây là phần duy nhất
 *       module khác được phép dùng.</li>
 *   <li>{@code domain}: entity và quy tắc nghiệp vụ, không phụ thuộc lớp nào khác.</li>
 *   <li>{@code infrastructure}: Spring Data JPA repository.</li>
 * </ul>
 */
package com.ordermanagement.user;
