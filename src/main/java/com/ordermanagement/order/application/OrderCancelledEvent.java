package com.ordermanagement.order.application;

import java.util.UUID;

/**
 * Được publish đồng bộ, <b>trong cùng transaction</b> với việc hủy đơn. {@code payment} lắng nghe để
 * hủy các lần thanh toán đang chờ và hoàn tiền lần đã thành công; nhờ vậy {@code order} không phải
 * phụ thuộc ngược vào {@code payment}, và việc hủy đơn + hoàn tiền + trả hàng về kho là một đơn vị
 * nguyên tử (lỗi ở bước nào cũng rollback tất cả).
 */
public record OrderCancelledEvent(UUID orderId) {
}
