package com.kyro.platform.common.errors;

import java.util.List;
import org.springframework.http.HttpStatus;

// FROZEN CONTRACT
/** 422 with a specific code (VALIDATION_FAILED, REQUEST_INCOMPLETE, DOCUMENT_INVALID, ...). */
public class UnprocessableException extends ApiException {

    public UnprocessableException(String code, String detail, List<FieldError> errors) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, code, detail, errors);
    }

    public UnprocessableException(String code, String detail) {
        this(code, detail, List.of());
    }
}
