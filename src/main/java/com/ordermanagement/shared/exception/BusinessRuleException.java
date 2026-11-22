package com.ordermanagement.shared.exception;

import org.springframework.http.HttpStatus;

/** 422 - request hợp lệ về cú pháp nhưng vi phạm quy tắc nghiệp vụ (user bị khóa, sản phẩm ngừng bán...). */
public class BusinessRuleException extends ApiException {

    public BusinessRuleException(String code, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, message, null);
    }
}
