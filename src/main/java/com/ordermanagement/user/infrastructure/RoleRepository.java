package com.ordermanagement.user.infrastructure;

import java.util.Optional;
import java.util.UUID;

import com.ordermanagement.user.domain.Role;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);
}
