package com.ordermanagement.payment.infrastructure;

import com.ordermanagement.order.application.OrderCancelledEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Cầu nối sự kiện: {@code order} publish {@link OrderCancelledEvent} đồng bộ trong transaction hủy đơn,
 * listener này chạy cùng transaction đó (không dùng {@code @TransactionalEventListener} và không {@code @Async})
 * để hủy/hoàn tiền thanh toán là một phần nguyên tử của việc hủy đơn.
 */
@Component
public class PaymentOrderEventListener {

    private final PaymentService paymentService;

    public PaymentOrderEventListener(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @EventListener
    public void onOrderCancelled(OrderCancelledEvent event) {
        paymentService.onOrderCancelled(event.orderId());
    }
}
