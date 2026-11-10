package com.kyro.platform.common.errors;

import java.util.List;
import org.springframework.http.HttpStatus;

// FROZEN CONTRACT: change signatures only with a DECISIONS.md entry.
/** Base type for all expected API failures; rendered as RFC 9457 problem+json by {@link GlobalExceptionHandler}. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldError> errors;

    public ApiException(HttpStatus status, String code, String detail, List<FieldError> errors) {
        super(detail);
        this.status = status;
        this.code = code;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public List<FieldError> getErrors() {
        return errors;
    }
}
