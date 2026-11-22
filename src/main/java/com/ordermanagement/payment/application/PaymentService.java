package com.ordermanagement.payment.application;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

import com.ordermanagement.order.application.OrderService;
import com.ordermanagement.order.application.PaymentOrderInfo;
import com.ordermanagement.payment.domain.Payment;
import com.ordermanagement.payment.domain.PaymentStatus;
import com.ordermanagement.payment.infrastructure.PaymentRepository;
import com.ordermanagement.shared.exception.BadRequestException;
import com.ordermanagement.shared.exception.ConflictException;
import com.ordermanagement.shared.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cổng vào duy nhất của module {@code payment}. Phụ thuộc {@code order} (một chiều); ngược lại
 * {@code order} chỉ "nói" với payment qua {@link com.ordermanagement.order.application.OrderCancelledEvent}.
 *
 * <p><b>Quy tắc khóa:</b> mọi thao tác ghi đều khóa dòng đơn hàng trước ({@code lockForPayment}), rồi
 * mới đọc/sửa payment. Cùng một thứ tự khóa với {@code OrderService.cancel} nên không có deadlock, và
 * các thao tác trên cùng một đơn (tạo thanh toán, callback, hủy đơn) chạy tuần tự.
 *
 * <p><b>Bất biến:</b> một đơn có tối đa một payment đang hoạt động (PENDING/PROCESSING) và tối đa một
 * payment SUCCEEDED (ràng buộc thêm ở DB bằng partial unique index). Payment đang hoạt động ⇒ đơn PENDING.
 */
@Service
@Transactional(readOnly = true)
public class PaymentService {

    private static final Collection<PaymentStatus> ACTIVE_STATUSES =
            EnumSet.of(PaymentStatus.PENDING, PaymentStatus.PROCESSING);
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 100;

    private final PaymentRepository paymentRepository;
    private final OrderService orderService;

    public PaymentService(PaymentRepository paymentRepository, OrderService orderService) {
        this.paymentRepository = paymentRepository;
        this.orderService = orderService;
    }

    /**
     * Tạo một lần thanh toán PENDING cho đơn, với số tiền = tổng đơn. Idempotent theo {@code idempotencyKey}:
     * gửi lại cùng key (cùng đơn, cùng provider) trả lại payment cũ; cùng key nhưng khác đơn/provider
     * là 409 {@code IDEMPOTENCY_KEY_REUSED}.
     */
    @Transactional
    public PaymentCreation create(UUID orderId, String provider, String idempotencyKey) {
        String key = requireValidKey(idempotencyKey);
        String normalizedProvider = provider.trim().toUpperCase(Locale.ROOT);

        Optional<PaymentCreation> replay = replay(key, orderId, normalizedProvider);
        if (replay.isPresent()) {
            return replay.get();
        }

        PaymentOrderInfo order = orderService.lockForPayment(orderId); // 404 nếu đơn không tồn tại

        // Request trùng key chạy song song có thể đã tạo xong trong lúc ta chờ khóa.
        replay = replay(key, orderId, normalizedProvider);
        if (replay.isPresent()) {
            return replay.get();
        }

        if (!order.payable()) {
            throw new ConflictException("ORDER_NOT_PAYABLE", "Order is not awaiting payment: " + orderId);
        }
        if (paymentRepository.existsByOrderIdAndStatusIn(orderId, ACTIVE_STATUSES)) {
            throw new ConflictException("PAYMENT_IN_PROGRESS",
                    "Order already has a payment in progress: " + orderId);
        }

        int attemptNumber = paymentRepository.findMaxAttemptNumber(orderId) + 1;
        Payment payment = Payment.initiate(orderId, attemptNumber, order.totalAmount(), order.currency(),
                normalizedProvider, key);
        paymentRepository.saveAndFlush(payment);
        return new PaymentCreation(toView(payment), true);
    }

    public PaymentView get(UUID id) {
        return toView(paymentRepository.findById(id).orElseThrow(() -> new NotFoundException("Payment", id)));
    }

    public List<PaymentView> listByOrder(UUID orderId) {
        orderService.requireExists(orderId);
        return paymentRepository.findByOrderIdOrderByAttemptNumberAsc(orderId).stream().map(this::toView).toList();
    }

    /** PENDING → PROCESSING: đã gửi sang cổng thanh toán. Gọi lại khi đã PROCESSING là no-op. */
    @Transactional
    public PaymentView submit(UUID paymentId) {
        Payment payment = lockPayment(paymentId).payment();
        if (payment.getStatus() == PaymentStatus.PROCESSING) {
            return toView(payment);
        }
        payment.markProcessing();
        paymentRepository.saveAndFlush(payment);
        return toView(payment);
    }

    /**
     * Xử lý callback từ cổng thanh toán (PROCESSING → SUCCEEDED | FAILED). Idempotent: cổng thường gửi
     * lại callback nên cùng kết quả lần nữa chỉ trả về trạng thái hiện tại.
     * <ul>
     *   <li>SUCCEEDED: đơn phải còn PENDING; payment thành công và đơn chuyển sang CONFIRMED
     *       trong cùng một transaction.</li>
     *   <li>FAILED: đơn vẫn PENDING và hàng vẫn đang giữ, khách có thể thử lại bằng payment mới.</li>
     * </ul>
     */
    @Transactional
    public PaymentView applyOutcome(UUID paymentId, PaymentOutcome outcome, String providerReference,
                                    String failureCode) {
        if (outcome == PaymentOutcome.SUCCEEDED && isBlank(providerReference)) {
            throw new BadRequestException("PROVIDER_REFERENCE_REQUIRED",
                    "providerReference is required when the outcome is SUCCEEDED.");
        }
        if (outcome == PaymentOutcome.FAILED && isBlank(failureCode)) {
            throw new BadRequestException("FAILURE_CODE_REQUIRED",
                    "failureCode is required when the outcome is FAILED.");
        }

        LockedPayment locked = lockPayment(paymentId);
        Payment payment = locked.payment();

        if (outcome == PaymentOutcome.SUCCEEDED) {
            if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                return toView(payment);
            }
            PaymentOrderInfo order = locked.order();
            if (!order.payable()) {
                throw new ConflictException("ORDER_NOT_PAYABLE",
                        "Order is not awaiting payment: " + order.orderId());
            }
            if (payment.getAmount().compareTo(order.totalAmount()) != 0
                    || !payment.getCurrency().equals(order.currency())) {
                throw new ConflictException("PAYMENT_AMOUNT_MISMATCH",
                        "Payment amount does not match the order total.");
            }
            payment.markSucceeded(providerReference.trim());
            paymentRepository.saveAndFlush(payment);
            orderService.markPaid(order.orderId());
        } else {
            if (payment.getStatus() == PaymentStatus.FAILED) {
                return toView(payment);
            }
            payment.markFailed(failureCode.trim());
            paymentRepository.saveAndFlush(payment);
        }
        return toView(payment);
    }

    /** Hủy một lần thanh toán đang PENDING/PROCESSING. Gọi lại khi đã CANCELLED là no-op. */
    @Transactional
    public PaymentView cancel(UUID paymentId) {
        Payment payment = lockPayment(paymentId).payment();
        if (payment.getStatus() == PaymentStatus.CANCELLED) {
            return toView(payment);
        }
        payment.cancel();
        paymentRepository.saveAndFlush(payment);
        return toView(payment);
    }

    /**
     * Phản ứng với việc đơn bị hủy: payment đang hoạt động bị hủy, payment đã thu tiền được hoàn tiền.
     * Chạy trong transaction của {@code OrderService.cancel} (dòng đơn đã được khóa).
     */
    @Transactional
    public void onOrderCancelled(UUID orderId) {
        for (Payment payment : paymentRepository.findByOrderIdOrderByAttemptNumberAsc(orderId)) {
            if (payment.getStatus().isActive()) {
                payment.cancel();
            } else if (payment.getStatus() == PaymentStatus.SUCCEEDED) {
                payment.refund();
            }
        }
        paymentRepository.flush();
    }

    // ---- Nội bộ ----------------------------------------------------------------------------

    private record LockedPayment(Payment payment, PaymentOrderInfo order) {
    }

    /**
     * Khóa dòng đơn rồi mới nạp payment. Bước tra {@code orderId} chỉ đọc một cột (không nạp entity)
     * để payment được đọc <i>sau</i> khi có khóa, tránh dùng trạng thái cũ.
     */
    private LockedPayment lockPayment(UUID paymentId) {
        UUID orderId = paymentRepository.findOrderIdById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment", paymentId));
        PaymentOrderInfo order = orderService.lockForPayment(orderId);
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new NotFoundException("Payment", paymentId));
        return new LockedPayment(payment, order);
    }

    private Optional<PaymentCreation> replay(String key, UUID orderId, String provider) {
        return paymentRepository.findByIdempotencyKey(key).map(existing -> {
            if (!existing.getOrderId().equals(orderId) || !existing.getProvider().equals(provider)) {
                throw new ConflictException("IDEMPOTENCY_KEY_REUSED",
                        "Idempotency-Key was already used for a different request.");
            }
            return new PaymentCreation(toView(existing), false);
        });
    }

    private static String requireValidKey(String key) {
        if (isBlank(key)) {
            throw new BadRequestException("IDEMPOTENCY_KEY_REQUIRED", "Idempotency-Key header is required.");
        }
        String trimmed = key.trim();
        if (trimmed.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new BadRequestException("IDEMPOTENCY_KEY_INVALID",
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters.");
        }
        return trimmed;
    }

    private static boolean isBlank(String text) {
        return text == null || text.isBlank();
    }

    private PaymentView toView(Payment payment) {
        return new PaymentView(payment.getId(), payment.getOrderId(), payment.getAttemptNumber(),
                payment.getStatus(), payment.getAmount(), payment.getCurrency(), payment.getProvider(),
                payment.getProviderReference(), payment.getFailureCode(), payment.getCreatedAt(),
                payment.getUpdatedAt());
    }
}
