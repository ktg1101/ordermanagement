/**
 * Module {@code payment}: thanh toán và vòng đời PENDING → PROCESSING → SUCCEEDED / FAILED,
 * CANCELLED, REFUNDED (bảng {@code payments}).
 *
 * <p>Phụ thuộc {@code order} (một chiều, chỉ qua {@code application}) và lắng nghe
 * {@code OrderCancelledEvent} để hủy hoặc hoàn tiền.
 *
 * <ul>
 *   <li>{@code api}: controller và request DTO (REST), gồm callback của cổng thanh toán.</li>
 *   <li>{@code application}: use case ({@code PaymentService}) và view.</li>
 *   <li>{@code domain}: entity {@code Payment}, state machine {@code PaymentStatus}.</li>
 *   <li>{@code infrastructure}: Spring Data JPA repository và listener sự kiện của {@code order}.</li>
 * </ul>
 */
package com.ordermanagement.payment;
