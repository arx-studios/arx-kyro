package com.kyro.platform.common.errors;

import org.springframework.http.HttpStatus;

// FROZEN CONTRACT
/** 403 with a specific code (FORBIDDEN, EMAIL_NOT_VERIFIED, SELF_REVIEW_FORBIDDEN, CSRF_INVALID). */
public class ForbiddenException extends ApiException {

    public ForbiddenException(String code, String detail) {
        super(HttpStatus.FORBIDDEN, code, detail, null);
    }
}
