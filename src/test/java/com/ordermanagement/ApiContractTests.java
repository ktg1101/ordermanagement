package com.ordermanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Hợp đồng REST qua HTTP thật: mã trạng thái, dạng lỗi {@code application/problem+json} với {@code code}
 * máy đọc được, validation và luồng đặt hàng - thanh toán đầu-cuối. Cần PostgreSQL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiContractTests {

    private static final String JSON = "application/json";

    @Value("${local.server.port}")
    private int port;

    @Autowired
    private JdbcTemplate jdbc;

    private final HttpClient client = HttpClient.newHttpClient();

    @AfterEach
    void cleanUp() {
        TestData.cleanup(jdbc);
    }

    // ---- Lỗi chung --------------------------------------------------------------------------

    @Test
    void invalidBodyReturnsValidationProblemWithFieldErrors() throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/users",
                "{\"email\":\"not-an-email\",\"password\":\"short\",\"fullName\":\"\"}");

        assertProblem(response, 400, "VALIDATION_FAILED");
        assertTrue(response.body().contains("\"errors\""), response.body());
        assertTrue(response.body().contains("\"field\":\"email\""), response.body());
        assertTrue(response.body().contains("\"field\":\"password\""), response.body());
    }

    @Test
    void malformedJsonAndBadIdsReturnBadRequest() throws Exception {
        assertProblem(send("POST", "/api/v1/users", "{not json"), 400, "MALFORMED_REQUEST");
        assertProblem(send("GET", "/api/v1/users/not-a-uuid", null), 400, "INVALID_PARAMETER");
    }

    @Test
    void unknownResourcesReturnNotFoundWithResourceCode() throws Exception {
        String id = UUID.randomUUID().toString();

        assertProblem(send("GET", "/api/v1/users/" + id, null), 404, "USER_NOT_FOUND");
        assertProblem(send("GET", "/api/v1/products/" + id, null), 404, "PRODUCT_NOT_FOUND");
        assertProblem(send("GET", "/api/v1/orders/" + id, null), 404, "ORDER_NOT_FOUND");
        assertProblem(send("GET", "/api/v1/payments/" + id, null), 404, "PAYMENT_NOT_FOUND");
    }

    // ---- User & Product ---------------------------------------------------------------------

    @Test
    void createUserNeverExposesThePasswordAndRejectsDuplicates() throws Exception {
        String email = TestData.uniqueEmail();
        String body = userJson(email);

        HttpResponse<String> created = send("POST", "/api/v1/users", body);
        assertEquals(201, created.statusCode(), created.body());
        assertNotNull(created.headers().firstValue("Location").orElse(null));
        assertFalse(created.body().toLowerCase().contains("password"), created.body());
        assertTrue(created.body().contains("\"CUSTOMER\""), created.body());

        assertProblem(send("POST", "/api/v1/users", body), 409, "EMAIL_ALREADY_EXISTS");
    }

    @Test
    void userCanBeUpdatedDisabledAndEnabled() throws Exception {
        String id = field(send("POST", "/api/v1/users", userJson(TestData.uniqueEmail())).body(), "id");

        HttpResponse<String> updated = send("PUT", "/api/v1/users/" + id, "{\"fullName\":\"Renamed\"}");
        assertEquals(200, updated.statusCode(), updated.body());
        assertTrue(updated.body().contains("\"fullName\":\"Renamed\""), updated.body());

        assertEquals(204, send("DELETE", "/api/v1/users/" + id, null).statusCode());
        assertTrue(send("GET", "/api/v1/users/" + id, null).body().contains("\"status\":\"DISABLED\""));
        assertTrue(send("POST", "/api/v1/users/" + id + "/enable", null).body().contains("\"status\":\"ACTIVE\""));
    }

    @Test
    void productCrudValidatesInputAndRejectsDuplicateSku() throws Exception {
        String sku = TestData.uniqueSku();

        assertProblem(send("POST", "/api/v1/products", productJson(sku, "-1.00")), 400, "VALIDATION_FAILED");
        assertProblem(send("POST", "/api/v1/products", productJson(sku, "10.123")), 400, "VALIDATION_FAILED");

        HttpResponse<String> created = send("POST", "/api/v1/products", productJson(sku, "50000.00"));
        assertEquals(201, created.statusCode(), created.body());
        String id = field(created.body(), "id");
        assertProblem(send("POST", "/api/v1/products", productJson(sku, "1.00")), 409, "SKU_ALREADY_EXISTS");

        HttpResponse<String> updated = send("PUT", "/api/v1/products/" + id,
                "{\"name\":\"Renamed\",\"price\":60000.00,\"active\":true}");
        assertEquals(200, updated.statusCode(), updated.body());

        HttpResponse<String> list = send("GET", "/api/v1/products?q=" + sku + "&size=5", null);
        assertEquals(200, list.statusCode(), list.body());
        assertTrue(list.body().contains("\"totalElements\":1"), list.body());

        assertEquals(204, send("DELETE", "/api/v1/products/" + id, null).statusCode());
        assertTrue(send("GET", "/api/v1/products/" + id, null).body().contains("\"active\":false"));
    }

    @Test
    void stockAdjustmentRejectsGoingNegative() throws Exception {
        String productId = createProduct("10.00");

        assertEquals(200, send("POST", "/api/v1/inventory/" + productId + "/adjustments", "{\"delta\":5}").statusCode());
        assertProblem(send("POST", "/api/v1/inventory/" + productId + "/adjustments", "{\"delta\":-6}"),
                422, "STOCK_CANNOT_BE_NEGATIVE");
        assertProblem(send("POST", "/api/v1/inventory/" + productId + "/adjustments", "{}"), 400, "VALIDATION_FAILED");
    }

    // ---- Đặt hàng - thanh toán đầu-cuối ----------------------------------------------------

    @Test
    void orderLifecycleOverHttp() throws Exception {
        String userId = field(send("POST", "/api/v1/users", userJson(TestData.uniqueEmail())).body(), "id");
        String productId = createProduct("50000.00");
        send("POST", "/api/v1/inventory/" + productId + "/adjustments", "{\"delta\":10}");

        HttpResponse<String> created = send("POST", "/api/v1/orders", orderJson(userId, productId, 3));
        assertEquals(201, created.statusCode(), created.body());
        String orderId = field(created.body(), "id");
        assertTrue(created.body().contains("\"status\":\"PENDING\""), created.body());
        assertTrue(created.body().contains("\"totalAmount\":150000.00"), created.body());

        HttpResponse<String> stock = send("GET", "/api/v1/inventory/" + productId, null);
        assertTrue(stock.body().contains("\"availableQuantity\":7"), stock.body());
        assertTrue(stock.body().contains("\"reservedQuantity\":3"), stock.body());

        // Thiếu header Idempotency-Key => 400; có key => 201, gửi lại cùng key => 200 cùng payment.
        assertEquals(400, send("POST", "/api/v1/orders/" + orderId + "/payments", "{\"provider\":\"mock\"}").statusCode());
        String key = TestData.uniqueKey();
        HttpResponse<String> payment = send("POST", "/api/v1/orders/" + orderId + "/payments",
                "{\"provider\":\"mock\"}", "Idempotency-Key", key);
        assertEquals(201, payment.statusCode(), payment.body());
        String paymentId = field(payment.body(), "id");
        HttpResponse<String> replay = send("POST", "/api/v1/orders/" + orderId + "/payments",
                "{\"provider\":\"mock\"}", "Idempotency-Key", key);
        assertEquals(200, replay.statusCode(), replay.body());
        assertEquals(paymentId, field(replay.body(), "id"));
        assertFalse(payment.body().contains(key), "idempotency key must not be exposed");

        assertTrue(send("POST", "/api/v1/payments/" + paymentId + "/submit", null).body()
                .contains("\"status\":\"PROCESSING\""));
        HttpResponse<String> callback = send("POST", "/api/v1/payments/" + paymentId + "/callback",
                "{\"outcome\":\"SUCCEEDED\",\"providerReference\":\"TXN-1\"}");
        assertEquals(200, callback.statusCode(), callback.body());
        assertTrue(callback.body().contains("\"status\":\"SUCCEEDED\""), callback.body());
        assertTrue(send("GET", "/api/v1/orders/" + orderId, null).body().contains("\"status\":\"CONFIRMED\""));

        HttpResponse<String> completed = send("POST", "/api/v1/orders/" + orderId + "/complete", null);
        assertTrue(completed.body().contains("\"status\":\"COMPLETED\""), completed.body());
        assertProblem(send("POST", "/api/v1/orders/" + orderId + "/cancel", null), 409, "ORDER_INVALID_STATE");

        HttpResponse<String> list = send("GET", "/api/v1/orders?userId=" + userId + "&status=COMPLETED", null);
        assertTrue(list.body().contains("\"totalElements\":1"), list.body());
    }

    @Test
    void insufficientStockReturnsConflictListingTheShortages() throws Exception {
        String userId = field(send("POST", "/api/v1/users", userJson(TestData.uniqueEmail())).body(), "id");
        String productId = createProduct("10.00");
        send("POST", "/api/v1/inventory/" + productId + "/adjustments", "{\"delta\":2}");

        HttpResponse<String> response = send("POST", "/api/v1/orders", orderJson(userId, productId, 5));

        assertProblem(response, 409, "INSUFFICIENT_STOCK");
        assertTrue(response.body().contains("\"shortages\""), response.body());
        assertTrue(response.body().contains(productId), response.body());
        assertTrue(send("GET", "/api/v1/inventory/" + productId, null).body().contains("\"availableQuantity\":2"));
    }

    @Test
    void invalidOrderRequestsAreRejected() throws Exception {
        String userId = field(send("POST", "/api/v1/users", userJson(TestData.uniqueEmail())).body(), "id");
        String productId = createProduct("10.00");

        assertProblem(send("POST", "/api/v1/orders", "{\"userId\":\"" + userId + "\",\"items\":[]}"),
                400, "VALIDATION_FAILED");
        assertProblem(send("POST", "/api/v1/orders", orderJson(userId, productId, 0)), 400, "VALIDATION_FAILED");
        assertProblem(send("POST", "/api/v1/orders", orderJson(UUID.randomUUID().toString(), productId, 1)),
                404, "USER_NOT_FOUND");
    }

    // ---- Helper -----------------------------------------------------------------------------

    private String createProduct(String price) throws Exception {
        HttpResponse<String> response = send("POST", "/api/v1/products", productJson(TestData.uniqueSku(), price));
        assertEquals(201, response.statusCode(), response.body());
        return field(response.body(), "id");
    }

    private static String userJson(String email) {
        return "{\"email\":\"%s\",\"password\":\"Passw0rd!x\",\"fullName\":\"API Tester\"}".formatted(email);
    }

    private static String productJson(String sku, String price) {
        return "{\"sku\":\"%s\",\"name\":\"Widget\",\"price\":%s}".formatted(sku, price);
    }

    private static String orderJson(String userId, String productId, int quantity) {
        return "{\"userId\":\"%s\",\"items\":[{\"productId\":\"%s\",\"quantity\":%d}]}"
                .formatted(userId, productId, quantity);
    }

    /** Lấy giá trị chuỗi của field cấp cao nhất đầu tiên có tên {@code name} (đủ cho response phẳng của API này). */
    private static String field(String json, String name) {
        Matcher matcher = Pattern.compile("\"" + Pattern.quote(name) + "\":\"([^\"]*)\"").matcher(json);
        assertTrue(matcher.find(), "field '" + name + "' not found in: " + json);
        return matcher.group(1);
    }

    private static void assertProblem(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        assertTrue(contentType.startsWith("application/problem+json"), "content-type was " + contentType);
        assertTrue(response.body().contains("\"code\":\"" + code + "\""), "expected " + code + " in: " + response.body());
    }

    private HttpResponse<String> send(String method, String path, String body, String... headers) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", JSON);
        for (int i = 0; i + 1 < headers.length; i += 2) {
            builder.header(headers[i], headers[i + 1]);
        }
        HttpRequest.BodyPublisher publisher = body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body);
        return client.send(builder.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString());
    }
}
