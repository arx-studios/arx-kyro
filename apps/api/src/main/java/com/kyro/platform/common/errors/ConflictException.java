package com.kyro.platform.common.errors;

import org.springframework.http.HttpStatus;

// FROZEN CONTRACT
/** 409 with a specific code (INVALID_STATE_TRANSITION, STALE_VERSION, CASE_ALREADY_ASSIGNED, ...). */
public class ConflictException extends ApiException {

    public ConflictException(String code, String detail) {
        super(HttpStatus.CONFLICT, code, detail, null);
    }
}
