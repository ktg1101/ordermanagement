/**
 * Feature {@code payment}: thanh toán cho đơn hàng (bảng {@code payments}).
 *
 * <p>Phụ thuộc vào {@code order}. Mỗi lần thanh toán có {@code idempotency_key}
 * riêng và mỗi đơn chỉ có tối đa một payment thành công.
 */
package org.ordermanagement.payment;
