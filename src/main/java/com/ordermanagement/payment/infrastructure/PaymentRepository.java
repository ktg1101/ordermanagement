package com.ordermanagement.payment.infrastructure;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.ordermanagement.payment.domain.Payment;
import com.ordermanagement.payment.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    List<Payment> findByOrderIdOrderByAttemptNumberAsc(UUID orderId);

    boolean existsByOrderIdAndStatusIn(UUID orderId, Collection<PaymentStatus> statuses);

    @Query("select coalesce(max(p.attemptNumber), 0) from Payment p where p.orderId = :orderId")
    int findMaxAttemptNumber(@Param("orderId") UUID orderId);

    /**
     * Chỉ lấy {@code orderId} (không nạp entity vào persistence context). Service cần khóa dòng đơn
     * trước rồi mới đọc payment, để không đọc phải trạng thái cũ trong lúc chờ khóa.
     */
    @Query("select p.orderId from Payment p where p.id = :id")
    Optional<UUID> findOrderIdById(@Param("id") UUID id);
}
