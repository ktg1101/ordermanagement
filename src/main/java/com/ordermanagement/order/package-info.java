/**
 * Module {@code order}: đơn hàng và vòng đời PENDING → CONFIRMED → COMPLETED / CANCELLED
 * (bảng {@code orders}, {@code order_items}).
 *
 * <p>Phụ thuộc {@code user} và {@code inventory} (chỉ qua {@code application}). Với {@code payment},
 * quan hệ là một chiều: {@code payment} gọi {@code order}, còn {@code order} chỉ phát
 * {@code OrderCancelledEvent}.
 *
 * <ul>
 *   <li>{@code api}: controller và request DTO (REST).</li>
 *   <li>{@code application}: use case ({@code OrderService}), view và sự kiện. Đây là phần duy nhất
 *       module khác được phép dùng.</li>
 *   <li>{@code domain}: aggregate {@code Order}, {@code OrderItem}, state machine {@code OrderStatus}.</li>
 *   <li>{@code infrastructure}: Spring Data JPA repository.</li>
 * </ul>
 */
package com.ordermanagement.order;
