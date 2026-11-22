package com.ordermanagement.user.application;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import com.ordermanagement.shared.common.paging.LikePatterns;
import com.ordermanagement.shared.common.paging.PageRequests;
import com.ordermanagement.shared.common.paging.PageResponse;
import com.ordermanagement.shared.exception.BusinessRuleException;
import com.ordermanagement.shared.exception.ConflictException;
import com.ordermanagement.shared.exception.NotFoundException;
import com.ordermanagement.user.domain.Role;
import com.ordermanagement.user.domain.User;
import com.ordermanagement.user.domain.UserStatus;
import com.ordermanagement.user.infrastructure.RoleRepository;
import com.ordermanagement.user.infrastructure.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cổng vào duy nhất của module {@code user}. Module khác (ví dụ {@code order})
 * chỉ được gọi các method public ở đây.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    public static final String DEFAULT_ROLE_CODE = "CUSTOMER";

    /** BCrypt chỉ xử lý tối đa 72 byte; dài hơn sẽ bị từ chối thay vì bị cắt âm thầm. */
    private static final int MAX_PASSWORD_BYTES = 72;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserView create(String email, String rawPassword, String fullName) {
        String normalizedEmail = normalizeEmail(email);
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw new BusinessRuleException("PASSWORD_TOO_LONG",
                    "Password must be at most " + MAX_PASSWORD_BYTES + " bytes.");
        }
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw emailTaken(normalizedEmail);
        }
        Role role = roleRepository.findByCode(DEFAULT_ROLE_CODE)
                .orElseThrow(() -> new IllegalStateException("Role " + DEFAULT_ROLE_CODE + " is not seeded"));
        User user = User.register(normalizedEmail, passwordEncoder.encode(rawPassword), fullName.trim(), role);
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Hai request đăng ký cùng email chạy song song: unique constraint là chốt chặn cuối.
            throw emailTaken(normalizedEmail);
        }
        return toView(user);
    }

    public UserView get(UUID id) {
        return toView(find(id));
    }

    public PageResponse<UserView> list(String q, UserStatus status, int page, int size) {
        Collection<UserStatus> statuses = status == null ? EnumSet.allOf(UserStatus.class) : EnumSet.of(status);
        Pageable pageable = PageRequests.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt").and(Sort.by("id")));
        return PageResponse.from(userRepository.search(LikePatterns.contains(q), statuses, pageable).map(this::toView));
    }

    @Transactional
    public UserView updateProfile(UUID id, String fullName) {
        User user = find(id);
        user.rename(fullName.trim());
        userRepository.saveAndFlush(user);
        return toView(user);
    }

    /** "Xóa" user = khóa tài khoản (FK RESTRICT nên không xóa cứng được khi đã có đơn). Idempotent. */
    @Transactional
    public UserView disable(UUID id) {
        User user = find(id);
        user.disable();
        userRepository.saveAndFlush(user);
        return toView(user);
    }

    @Transactional
    public UserView enable(UUID id) {
        User user = find(id);
        user.enable();
        userRepository.saveAndFlush(user);
        return toView(user);
    }

    /** Dùng cho module khác: user phải tồn tại (404) và đang ACTIVE (422). */
    public UserView requireActive(UUID id) {
        UserView user = get(id);
        if (user.status() != UserStatus.ACTIVE) {
            throw new BusinessRuleException("USER_DISABLED", "User is disabled: " + id);
        }
        return user;
    }

    private User find(UUID id) {
        return userRepository.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    private UserView toView(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getCode).sorted().toList();
        return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getStatus(), roles,
                user.getCreatedAt(), user.getUpdatedAt());
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static ConflictException emailTaken(String email) {
        return new ConflictException("EMAIL_ALREADY_EXISTS", "Email is already registered: " + email);
    }
}
