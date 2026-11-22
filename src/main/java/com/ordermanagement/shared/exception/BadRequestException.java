package com.ordermanagement.shared.exception;

import org.springframework.http.HttpStatus;

/** 400 - dữ liệu đầu vào sai mà Bean Validation không bắt được (ràng buộc phụ thuộc nhiều field/header). */
public class BadRequestException extends ApiException {

    public BadRequestException(String code, String message) {
        super(HttpStatus.BAD_REQUEST, code, message, null);
    }
}
