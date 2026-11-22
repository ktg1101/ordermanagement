package com.ordermanagement.order.infrastructure;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;

import com.ordermanagement.order.domain.Order;
import com.ordermanagement.order.domain.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    /**
     * Khóa dòng đơn hàng ({@code SELECT ... FOR UPDATE}) cho tới hết transaction. Mọi thao tác đổi
     * trạng thái đơn hoặc thanh toán của đơn đó đều khóa dòng này đầu tiên nên chúng chạy tuần tự
     * (ví dụ: hủy đơn và callback thanh toán thành công không thể chen vào nhau).
     * Tên entity trong JPQL là {@code SalesOrder}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SalesOrder o where o.id = :id")
    Optional<Order> findByIdForUpdate(@Param("id") UUID id);

    Page<Order> findByStatusIn(Collection<OrderStatus> statuses, Pageable pageable);

    Page<Order> findByUserIdAndStatusIn(UUID userId, Collection<OrderStatus> statuses, Pageable pageable);
}
