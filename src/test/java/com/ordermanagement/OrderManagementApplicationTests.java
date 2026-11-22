package com.ordermanagement;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** Context khởi động được: Flyway chạy V1..V3 và Hibernate {@code validate} khớp với toàn bộ entity. Cần PostgreSQL. */
@SpringBootTest
class OrderManagementApplicationTests {

    @Test
    void contextLoads() {
    }

}
