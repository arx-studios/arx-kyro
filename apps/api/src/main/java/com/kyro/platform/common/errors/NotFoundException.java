package com.kyro.platform.common.errors;

import org.springframework.http.HttpStatus;

// FROZEN CONTRACT
/** 404 NOT_FOUND. Also used for objects the caller may not see, so existence is not revealed. */
public class NotFoundException extends ApiException {

    public NotFoundException() {
        this("The requested resource was not found.");
    }

    public NotFoundException(String detail) {
        super(HttpStatus.NOT_FOUND, "NOT_FOUND", detail, null);
    }
}
