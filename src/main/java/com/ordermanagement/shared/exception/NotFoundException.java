package com.ordermanagement.shared.exception;

import java.util.Locale;

import org.springframework.http.HttpStatus;

/** 404 - không tìm thấy tài nguyên. Mã lỗi có dạng {@code <RESOURCE>_NOT_FOUND}. */
public class NotFoundException extends ApiException {

    public NotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND,
                resource.toUpperCase(Locale.ROOT) + "_NOT_FOUND",
                resource + " not found: " + id,
                null);
    }
}
