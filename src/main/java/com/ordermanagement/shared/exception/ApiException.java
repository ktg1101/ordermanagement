package com.ordermanagement.shared.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/**
 * Lỗi nghiệp vụ dự đoán được. {@link GlobalExceptionHandler} chuyển nó thành
 * {@code application/problem+json} với {@code code} máy đọc được (ví dụ
 * {@code INSUFFICIENT_STOCK}) để client xử lý theo mã, không phải parse message.
 */
public abstract class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, Object> details;

    protected ApiException(HttpStatus status, String code, String message, Map<String, Object> details) {
        super(message);
        this.status = status;
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** Thuộc tính bổ sung sẽ được ghi thẳng vào body lỗi (ví dụ danh sách sản phẩm thiếu hàng). */
    public Map<String, Object> getDetails() {
        return details;
    }
}
