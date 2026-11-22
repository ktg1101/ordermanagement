package com.ordermanagement.shared.exception;

import java.util.Map;

import org.springframework.http.HttpStatus;

/** 409 - xung đột với trạng thái hiện tại (trùng dữ liệu, sai trạng thái, hết hàng...). */
public class ConflictException extends ApiException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message, null);
    }

    public ConflictException(String code, String message, Map<String, Object> details) {
        super(HttpStatus.CONFLICT, code, message, details);
    }
}
