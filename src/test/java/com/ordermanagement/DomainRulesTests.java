package com.ordermanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.ordermanagement.order.domain.Order;
import com.ordermanagement.order.domain.OrderStatus;
import com.ordermanagement.payment.domain.Payment;
import com.ordermanagement.payment.domain.PaymentStatus;
import com.ordermanagement.shared.common.paging.LikePatterns;
import com.ordermanagement.shared.exception.ConflictException;

import org.junit.jupiter.api.Test;

/** Unit test thuần (không cần Spring/PostgreSQL) cho state machine và quy tắc trong domain. */
class DomainRulesTests {

    @Test
    void orderStatusTransitionsMatchTheLifecycle() {
        Map<OrderStatus, Set<OrderStatus>> allowed = Map.of(
                OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED),
                OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.COMPLETED, OrderStatus.CANCELLED),
                OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class),
                OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));

        for (OrderStatus from : OrderStatus.values()) {
            for (OrderStatus to : OrderStatus.values()) {
                assertEquals(allowed.get(from).contains(to), from.canTransitionTo(to), from + " -> " + to);
            }
        }
    }

    @Test
    void paymentStatusTransitionsMatchTheLifecycle() {
        Map<PaymentStatus, Set<PaymentStatus>> allowed = Map.of(
                PaymentStatus.PENDING, EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.CANCELLED),
                PaymentStatus.PROCESSING,
                EnumSet.of(PaymentStatus.SUCCEEDED, PaymentStatus.FAILED, PaymentStatus.CANCELLED),
                PaymentStatus.SUCCEEDED, EnumSet.of(PaymentStatus.REFUNDED),
                PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class),
                PaymentStatus.CANCELLED, EnumSet.noneOf(PaymentStatus.class),
                PaymentStatus.REFUNDED, EnumSet.noneOf(PaymentStatus.class));

        for (PaymentStatus from : PaymentStatus.values()) {
            for (PaymentStatus to : PaymentStatus.values()) {
                assertEquals(allowed.get(from).contains(to), from.canTransitionTo(to), from + " -> " + to);
            }
        }
    }

    @Test
    void orderTotalIsTheSumOfItsLines() {
        Order order = Order.place("ORD-TEST", UUID.randomUUID(), Order.DEFAULT_CURRENCY);
        order.addItem(UUID.randomUUID(), "A", "Item A", new BigDecimal("10.50"), 2);
        order.addItem(UUID.randomUUID(), "B", "Item B", new BigDecimal("3.00"), 1);

        assertEquals(0, new BigDecimal("24.00").compareTo(order.getTotalAmount()));
        assertEquals(2, order.getItems().size());
    }

    @Test
    void orderRejectsInvalidLines() {
        Order order = Order.place("ORD-TEST", UUID.randomUUID(), Order.DEFAULT_CURRENCY);

        assertThrows(IllegalArgumentException.class,
                () -> order.addItem(UUID.randomUUID(), "A", "Item A", new BigDecimal("1.00"), 0));
        assertThrows(IllegalArgumentException.class,
                () -> order.addItem(UUID.randomUUID(), "A", "Item A", new BigDecimal("-1.00"), 1));
    }

    @Test
    void orderCannotSkipOrRepeatSteps() {
        Order order = Order.place("ORD-TEST", UUID.randomUUID(), Order.DEFAULT_CURRENCY);

        assertThrows(ConflictException.class, order::complete); // PENDING -> COMPLETED không hợp lệ
        order.confirm();
        order.complete();
        assertEquals(OrderStatus.COMPLETED, order.getStatus());
        assertThrows(ConflictException.class, order::cancel); // đã hoàn tất thì không hủy được
    }

    @Test
    void cancelledOrderIsTerminal() {
        Order order = Order.place("ORD-TEST", UUID.randomUUID(), Order.DEFAULT_CURRENCY);
        order.cancel();

        assertThrows(ConflictException.class, order::confirm);
        assertThrows(ConflictException.class, order::cancel);
    }

    @Test
    void paymentFollowsItsLifecycle() {
        Payment payment = newPayment();

        assertThrows(ConflictException.class, () -> payment.markSucceeded("TXN-1")); // chưa PROCESSING
        payment.markProcessing();
        payment.markSucceeded("TXN-1");
        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals("TXN-1", payment.getProviderReference());

        assertThrows(ConflictException.class, payment::cancel); // đã thu tiền thì phải hoàn, không hủy
        payment.refund();
        assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
    }

    @Test
    void failedPaymentRecordsTheFailureCode() {
        Payment payment = newPayment();
        payment.markProcessing();
        payment.markFailed("CARD_DECLINED");

        assertEquals(PaymentStatus.FAILED, payment.getStatus());
        assertEquals("CARD_DECLINED", payment.getFailureCode());
        assertThrows(ConflictException.class, payment::markProcessing);
    }

    @Test
    void paymentAmountMustBePositive() {
        assertThrows(IllegalArgumentException.class, () ->
                Payment.initiate(UUID.randomUUID(), 1, BigDecimal.ZERO, "VND", "MOCK", "key"));
    }

    @Test
    void likePatternsEscapeWildcards() {
        assertEquals("%", LikePatterns.contains(null));
        assertEquals("%", LikePatterns.contains("  "));
        assertEquals("%abc%", LikePatterns.contains("  ABC "));
        assertEquals("%50\\%\\_off%", LikePatterns.contains("50%_off"));
    }

    private static Payment newPayment() {
        return Payment.initiate(UUID.randomUUID(), 1, new BigDecimal("100.00"), "VND", "MOCK", "key");
    }
}
