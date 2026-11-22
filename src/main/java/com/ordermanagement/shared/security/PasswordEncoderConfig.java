package com.ordermanagement.shared.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Băm mật khẩu bằng BCrypt. Dự án mới chỉ dùng module {@code spring-security-crypto} (chưa kéo cả
 * Spring Security vào) nên chưa có filter chain nào chặn API; xác thực/phân quyền sẽ làm ở phase sau
 * và cũng đặt trong package này.
 */
@Configuration(proxyBeanMethods = false)
class PasswordEncoderConfig {

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
