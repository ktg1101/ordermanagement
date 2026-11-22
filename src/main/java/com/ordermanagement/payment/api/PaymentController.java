package com.ordermanagement.payment.api;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import com.ordermanagement.payment.application.PaymentCreation;
import com.ordermanagement.payment.application.PaymentService;
import com.ordermanagement.payment.application.PaymentView;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    /**
     * Tạo lần thanh toán cho đơn. Bắt buộc có header {@code Idempotency-Key}: 201 khi tạo mới,
     * 200 khi là lần gửi lại của cùng key.
     */
    @PostMapping("/api/v1/orders/{orderId}/payments")
    public ResponseEntity<PaymentView> create(
            @PathVariable("orderId") UUID orderId,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentCreation result = paymentService.create(orderId, request.provider(), idempotencyKey);
        if (!result.created()) {
            return ResponseEntity.ok(result.payment());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/api/v1/payments/{id}").buildAndExpand(result.payment().id()).toUri();
        return ResponseEntity.created(location).body(result.payment());
    }

    @GetMapping("/api/v1/orders/{orderId}/payments")
    public List<PaymentView> listByOrder(@PathVariable("orderId") UUID orderId) {
        return paymentService.listByOrder(orderId);
    }

    @GetMapping("/api/v1/payments/{id}")
    public PaymentView get(@PathVariable("id") UUID id) {
        return paymentService.get(id);
    }

    /** PENDING → PROCESSING (đã gửi sang cổng thanh toán). */
    @PostMapping("/api/v1/payments/{id}/submit")
    public PaymentView submit(@PathVariable("id") UUID id) {
        return paymentService.submit(id);
    }

    /** Callback/webhook từ cổng thanh toán: PROCESSING → SUCCEEDED | FAILED. */
    @PostMapping("/api/v1/payments/{id}/callback")
    public PaymentView callback(@PathVariable("id") UUID id, @Valid @RequestBody PaymentCallbackRequest request) {
        return paymentService.applyOutcome(id, request.outcome(), request.providerReference(), request.failureCode());
    }

    @PostMapping("/api/v1/payments/{id}/cancel")
    public PaymentView cancel(@PathVariable("id") UUID id) {
        return paymentService.cancel(id);
    }
}
