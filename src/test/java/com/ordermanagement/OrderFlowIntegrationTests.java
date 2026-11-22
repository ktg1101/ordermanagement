package com.ordermanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.ordermanagement.inventory.application.InventoryService;
import com.ordermanagement.inventory.application.InventoryView;
import com.ordermanagement.inventory.application.ProductService;
import com.ordermanagement.inventory.application.ProductView;
import com.ordermanagement.order.application.OrderLineCommand;
import com.ordermanagement.order.application.OrderService;
import com.ordermanagement.order.application.OrderView;
import com.ordermanagement.order.domain.OrderStatus;
import com.ordermanagement.payment.application.PaymentCreation;
import com.ordermanagement.payment.application.PaymentOutcome;
import com.ordermanagement.payment.application.PaymentService;
import com.ordermanagement.payment.application.PaymentView;
import com.ordermanagement.payment.domain.PaymentStatus;
import com.ordermanagement.shared.exception.ApiException;
import com.ordermanagement.user.application.UserService;
import com.ordermanagement.user.application.UserView;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Các tình huống nghiệp vụ cơ bản chạy trên PostgreSQL thật, gọi thẳng service của từng module.
 * Không dùng {@code @Transactional} ở test để mỗi use case commit/rollback đúng như khi chạy thật.
 */
@SpringBootTest
class OrderFlowIntegrationTests {

    @Autowired
    private UserService userService;
    @Autowired
    private ProductService productService;
    @Autowired
    private InventoryService inventoryService;
    @Autowired
    private OrderService orderService;
    @Autowired
    private PaymentService paymentService;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        TestData.cleanup(jdbc);
    }

    // ---- User & Product ---------------------------------------------------------------------

    @Test
    void userEmailIsUniqueIgnoringCase() {
        String email = TestData.uniqueEmail();
        UserView created = userService.create(email, "Passw0rd!x", "First");

        assertEquals(email, created.email());
        assertEquals(List.of("CUSTOMER"), created.roles());
        assertError("EMAIL_ALREADY_EXISTS", () -> userService.create(email.toUpperCase(), "Passw0rd!x", "Second"));
    }

    @Test
    void productSkuIsUniqueAndNormalized() {
        String sku = TestData.uniqueSku();
        ProductView created = productService.create(sku.toLowerCase(), "Widget", null, new BigDecimal("10.00"));

        assertEquals(sku, created.sku());
        assertError("SKU_ALREADY_EXISTS", () -> productService.create(sku, "Other", null, new BigDecimal("1.00")));
    }

    @Test
    void stockCannotBeAdjustedBelowZero() {
        ProductView product = newProduct("10.00", 3);

        assertError("STOCK_CANNOT_BE_NEGATIVE", () -> inventoryService.adjust(product.id(), -4));
        assertStock(product, 3, 0);
        inventoryService.adjust(product.id(), -3);
        assertStock(product, 0, 0);
    }

    // ---- Tạo đơn & tồn kho ------------------------------------------------------------------

    @Test
    void placingAnOrderReservesStockAndSnapshotsPrices() {
        UserView user = newUser();
        ProductView product = newProduct("50000.00", 10);

        OrderView order = placeOrder(user, product, 3);

        assertEquals(OrderStatus.PENDING, order.status());
        assertMoney("150000.00", order.totalAmount());
        assertEquals(1, order.items().size());
        assertStock(product, 7, 3);

        // Đổi giá sau khi đặt không làm thay đổi đơn cũ.
        productService.update(product.id(), "Renamed", null, new BigDecimal("99999.00"), true);
        OrderView reloaded = orderService.get(order.id());
        assertMoney("50000.00", reloaded.items().get(0).unitPrice());
        assertMoney("150000.00", reloaded.totalAmount());
    }

    @Test
    void duplicateLinesForTheSameProductAreMerged() {
        UserView user = newUser();
        ProductView product = newProduct("10.00", 10);

        OrderView order = orderService.create(user.id(),
                List.of(new OrderLineCommand(product.id(), 1), new OrderLineCommand(product.id(), 2)));

        assertEquals(1, order.items().size());
        assertEquals(3, order.items().get(0).quantity());
        assertStock(product, 7, 3);
    }

    @Test
    void insufficientStockRejectsTheWholeOrderAndHoldsNothing() {
        UserView user = newUser();
        ProductView plenty = newProduct("10.00", 10);
        ProductView soldOut = newProduct("20.00", 1);

        ApiException error = assertThrows(ApiException.class, () -> orderService.create(user.id(),
                List.of(new OrderLineCommand(plenty.id(), 2), new OrderLineCommand(soldOut.id(), 5))));

        assertEquals("INSUFFICIENT_STOCK", error.getCode());
        List<?> shortages = (List<?>) error.getDetails().get("shortages");
        assertEquals(1, shortages.size());
        // Rollback: sản phẩm còn hàng cũng không bị giữ dở.
        assertStock(plenty, 10, 0);
        assertStock(soldOut, 1, 0);
    }

    @Test
    void invalidOrdersAreRejected() {
        UserView user = newUser();
        ProductView product = newProduct("10.00", 5);

        assertError("ORDER_EMPTY", () -> orderService.create(user.id(), List.of()));

        UserView disabled = newUser();
        userService.disable(disabled.id());
        assertError("USER_DISABLED", () -> placeOrder(disabled, product, 1));

        productService.deactivate(product.id());
        assertError("PRODUCT_NOT_AVAILABLE", () -> placeOrder(user, product, 1));

        assertError("PRODUCT_NOT_FOUND", () -> orderService.create(user.id(),
                List.of(new OrderLineCommand(java.util.UUID.randomUUID(), 1))));
        assertStock(product, 5, 0);
    }

    @Test
    void concurrentOrdersNeverOversellStock() throws Exception {
        UserView user = newUser();
        ProductView product = newProduct("10000.00", 5);
        int attempts = 12;

        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            results.add(pool.submit(() -> {
                start.await();
                try {
                    placeOrder(user, product, 1);
                    return "OK";
                } catch (ApiException e) {
                    return e.getCode();
                }
            }));
        }
        start.countDown();

        int succeeded = 0;
        int soldOut = 0;
        List<String> unexpected = new ArrayList<>();
        for (Future<String> result : results) {
            String outcome = result.get(60, TimeUnit.SECONDS);
            switch (outcome) {
                case "OK" -> succeeded++;
                case "INSUFFICIENT_STOCK" -> soldOut++;
                default -> unexpected.add(outcome);
            }
        }
        pool.shutdown();

        assertTrue(unexpected.isEmpty(), "unexpected outcomes: " + unexpected);
        assertEquals(5, succeeded);
        assertEquals(attempts - 5, soldOut);
        assertStock(product, 0, 5);
    }

    // ---- Vòng đời đơn + thanh toán ----------------------------------------------------------

    @Test
    void paidOrderIsConfirmedThenCompletedAndStockIsConsumed() {
        UserView user = newUser();
        ProductView product = newProduct("100000.00", 10);
        OrderView order = placeOrder(user, product, 2);

        assertError("ORDER_INVALID_STATE", () -> orderService.complete(order.id())); // chưa thanh toán

        PaymentView payment = pay(order);
        assertEquals(PaymentStatus.SUCCEEDED, payment.status());
        assertMoney("200000.00", payment.amount());
        assertEquals(OrderStatus.CONFIRMED, orderService.get(order.id()).status());
        assertStock(product, 8, 2); // vẫn đang giữ cho tới khi xuất kho

        OrderView completed = orderService.complete(order.id());
        assertEquals(OrderStatus.COMPLETED, completed.status());
        assertStock(product, 8, 0); // hàng đã xuất kho
        assertEquals(OrderStatus.COMPLETED, orderService.complete(order.id()).status()); // idempotent

        assertError("ORDER_INVALID_STATE", () -> orderService.cancel(order.id()));
        assertStock(product, 8, 0);
    }

    @Test
    void cancellingAPendingOrderReleasesStockAndCancelsItsPayment() {
        UserView user = newUser();
        ProductView product = newProduct("10.00", 10);
        OrderView order = placeOrder(user, product, 4);
        PaymentView payment = paymentService.create(order.id(), "MOCK", TestData.uniqueKey()).payment();
        paymentService.submit(payment.id());

        OrderView cancelled = orderService.cancel(order.id());

        assertEquals(OrderStatus.CANCELLED, cancelled.status());
        assertStock(product, 10, 0);
        assertEquals(PaymentStatus.CANCELLED, paymentService.get(payment.id()).status());
        // Hủy lần nữa là no-op, tồn kho không bị cộng thêm.
        assertEquals(OrderStatus.CANCELLED, orderService.cancel(order.id()).status());
        assertStock(product, 10, 0);
    }

    @Test
    void cancellingAPaidOrderRefundsThePaymentAndReleasesStock() {
        UserView user = newUser();
        ProductView product = newProduct("10.00", 10);
        OrderView order = placeOrder(user, product, 4);
        PaymentView payment = pay(order);
        assertEquals(OrderStatus.CONFIRMED, orderService.get(order.id()).status());

        orderService.cancel(order.id());

        assertEquals(OrderStatus.CANCELLED, orderService.get(order.id()).status());
        assertEquals(PaymentStatus.REFUNDED, paymentService.get(payment.id()).status());
        assertStock(product, 10, 0);
    }

    @Test
    void failedPaymentKeepsTheOrderPendingAndAllowsARetry() {
        UserView user = newUser();
        ProductView product = newProduct("10.00", 5);
        OrderView order = placeOrder(user, product, 1);

        PaymentView first = paymentService.create(order.id(), "MOCK", TestData.uniqueKey()).payment();
        // Đang có lần thanh toán chưa xong thì không tạo thêm được.
        assertError("PAYMENT_IN_PROGRESS", () -> paymentService.create(order.id(), "MOCK", TestData.uniqueKey()));

        paymentService.submit(first.id());
        PaymentView failed = paymentService.applyOutcome(first.id(), PaymentOutcome.FAILED, null, "CARD_DECLINED");
        assertEquals(PaymentStatus.FAILED, failed.status());
        assertEquals("CARD_DECLINED", failed.failureCode());
        assertEquals(OrderStatus.PENDING, orderService.get(order.id()).status());
        assertStock(product, 4, 1); // hàng vẫn được giữ để khách thử lại

        PaymentView second = paymentService.create(order.id(), "MOCK", TestData.uniqueKey()).payment();
        assertEquals(2, second.attemptNumber());
        paymentService.submit(second.id());
        paymentService.applyOutcome(second.id(), PaymentOutcome.SUCCEEDED, "TXN-2", null);
        assertEquals(OrderStatus.CONFIRMED, orderService.get(order.id()).status());
        assertEquals(2, paymentService.listByOrder(order.id()).size());
    }

    @Test
    void createPaymentIsIdempotentPerKey() {
        UserView user = newUser();
        ProductView product = newProduct("10.00", 5);
        OrderView order = placeOrder(user, product, 1);
        OrderView otherOrder = placeOrder(user, product, 1);
        String key = TestData.uniqueKey();

        PaymentCreation first = paymentService.create(order.id(), "mock", key);
        PaymentCreation replay = paymentService.create(order.id(), "MOCK", key);

        assertTrue(first.created());
        assertFalse(replay.created());
        assertEquals(first.payment().id(), replay.payment().id());
        assertEquals(1, paymentService.listByOrder(order.id()).size());
        assertError("IDEMPOTENCY_KEY_REUSED", () -> paymentService.create(otherOrder.id(), "MOCK", key));
        assertError("IDEMPOTENCY_KEY_REQUIRED", () -> paymentService.create(order.id(), "MOCK", "  "));
    }

    @Test
    void repeatedCallbacksAreIdempotentAndOutcomeFieldsAreRequired() {
        UserView user = newUser();
        ProductView product = newProduct("10.00", 5);
        OrderView order = placeOrder(user, product, 1);
        PaymentView payment = paymentService.create(order.id(), "MOCK", TestData.uniqueKey()).payment();

        // Callback khi chưa submit (PENDING) là không hợp lệ.
        assertError("PAYMENT_INVALID_STATE",
                () -> paymentService.applyOutcome(payment.id(), PaymentOutcome.SUCCEEDED, "TXN-1", null));
        paymentService.submit(payment.id());
        assertError("PROVIDER_REFERENCE_REQUIRED",
                () -> paymentService.applyOutcome(payment.id(), PaymentOutcome.SUCCEEDED, null, null));
        assertError("FAILURE_CODE_REQUIRED",
                () -> paymentService.applyOutcome(payment.id(), PaymentOutcome.FAILED, null, null));

        paymentService.applyOutcome(payment.id(), PaymentOutcome.SUCCEEDED, "TXN-1", null);
        PaymentView again = paymentService.applyOutcome(payment.id(), PaymentOutcome.SUCCEEDED, "TXN-1", null);

        assertEquals(PaymentStatus.SUCCEEDED, again.status());
        assertEquals(OrderStatus.CONFIRMED, orderService.get(order.id()).status());
    }

    @Test
    void lateSuccessCallbackForACancelledOrderIsRejected() {
        UserView user = newUser();
        ProductView product = newProduct("10.00", 5);
        OrderView order = placeOrder(user, product, 2);
        PaymentView payment = paymentService.create(order.id(), "MOCK", TestData.uniqueKey()).payment();
        paymentService.submit(payment.id());

        orderService.cancel(order.id());

        assertError("ORDER_NOT_PAYABLE",
                () -> paymentService.applyOutcome(payment.id(), PaymentOutcome.SUCCEEDED, "TXN-LATE", null));
        assertEquals(OrderStatus.CANCELLED, orderService.get(order.id()).status());
        assertEquals(PaymentStatus.CANCELLED, paymentService.get(payment.id()).status());
        assertStock(product, 5, 0);
        // Đơn đã hủy thì không tạo thanh toán mới.
        assertError("ORDER_NOT_PAYABLE", () -> paymentService.create(order.id(), "MOCK", TestData.uniqueKey()));
    }

    // ---- Helper -----------------------------------------------------------------------------

    private UserView newUser() {
        return userService.create(TestData.uniqueEmail(), "Passw0rd!x", "Test User");
    }

    private ProductView newProduct(String price, int stock) {
        ProductView product = productService.create(TestData.uniqueSku(), "Test product", null, new BigDecimal(price));
        if (stock > 0) {
            inventoryService.adjust(product.id(), stock);
        }
        return product;
    }

    private OrderView placeOrder(UserView user, ProductView product, int quantity) {
        return orderService.create(user.id(), List.of(new OrderLineCommand(product.id(), quantity)));
    }

    /** Tạo → gửi → callback thành công; trả về payment sau cùng. */
    private PaymentView pay(OrderView order) {
        PaymentView payment = paymentService.create(order.id(), "MOCK", TestData.uniqueKey()).payment();
        paymentService.submit(payment.id());
        return paymentService.applyOutcome(payment.id(), PaymentOutcome.SUCCEEDED, "TXN-" + payment.id(), null);
    }

    private void assertStock(ProductView product, int available, int reserved) {
        InventoryView stock = inventoryService.get(product.id());
        assertEquals(available, stock.availableQuantity(), "available quantity");
        assertEquals(reserved, stock.reservedQuantity(), "reserved quantity");
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    private static void assertError(String code, Executable action) {
        ApiException error = assertThrows(ApiException.class, action);
        assertEquals(code, error.getCode());
    }
}
