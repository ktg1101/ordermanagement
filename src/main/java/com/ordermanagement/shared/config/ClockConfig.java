package com.ordermanagement.shared.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Nguồn thời gian dùng chung (UTC) để code nghiệp vụ không gọi {@code now()} trực tiếp và test được. */
@Configuration(proxyBeanMethods = false)
class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
