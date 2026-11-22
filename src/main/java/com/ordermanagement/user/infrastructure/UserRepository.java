package com.ordermanagement.user.infrastructure;

import java.util.Collection;
import java.util.UUID;

import com.ordermanagement.user.domain.User;
import com.ordermanagement.user.domain.UserStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    boolean existsByEmail(String email);

    /**
     * Tìm theo email/họ tên. {@code pattern} và {@code statuses} luôn khác null
     * (xem {@code LikePatterns}) nên không có tham số null trong query.
     */
    @Query("select u from User u "
            + "where u.status in :statuses "
            + "and (lower(u.email) like :pattern or lower(u.fullName) like :pattern)")
    Page<User> search(@Param("pattern") String pattern,
                      @Param("statuses") Collection<UserStatus> statuses,
                      Pageable pageable);
}
