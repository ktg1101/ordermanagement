package org.ordermanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Kiểm tra health endpoint và kết nối PostgreSQL thật.
 * Cần PostgreSQL đang chạy (xem README: {@code docker compose up -d postgres}).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTests {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void healthEndpointReportsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
    }

    @Test
    void databaseConnectionIsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health/db");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
    }

    @Test
    void readinessProbeIsUp() throws Exception {
        HttpResponse<String> response = get("/actuator/health/readiness");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
