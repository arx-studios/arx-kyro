package com.kyro.platform.common.errors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Maps every failure to an RFC 9457 problem+json body with {@code code} and {@code correlationId}. Never leaks stack traces. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<Map<String, Object>> api(ApiException ex, HttpServletRequest req) {
        return respond(ex.getStatus(), ex.getCode(), ex.getMessage(), req, ex.getErrors());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Map<String, Object>> bodyInvalid(MethodArgumentNotValidException ex, HttpServletRequest req) {
        List<FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new FieldError(fe.getField(), String.valueOf(fe.getDefaultMessage())))
                .toList();
        return validation(req, errors);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<Map<String, Object>> paramInvalid(HandlerMethodValidationException ex, HttpServletRequest req) {
        List<FieldError> errors = ex.getParameterValidationResults().stream()
                .flatMap(r -> r.getResolvableErrors().stream()
                        .map(e -> new FieldError(
                                r.getMethodParameter().getParameterName() == null
                                        ? "parameter"
                                        : r.getMethodParameter().getParameterName(),
                                String.valueOf(e.getDefaultMessage()))))
                .toList();
        return validation(req, errors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Map<String, Object>> constraint(ConstraintViolationException ex, HttpServletRequest req) {
        List<FieldError> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldError(v.getPropertyPath().toString(), v.getMessage()))
                .toList();
        return validation(req, errors);
    }

    /**
     * The contract restricts {@code RegisterRequest.roles} to RECIPIENT/DONOR, so asking for VERIFIER or ADMIN fails
     * enum deserialisation. PLAN section 5 requires 422 ROLE_NOT_ALLOWED for that case, not a generic 400.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Map<String, Object>> unreadable(HttpMessageNotReadableException ex, HttpServletRequest req) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof tools.jackson.databind.DatabindException de
                    && de.getPath().stream().anyMatch(ref -> "roles".equals(ref.getPropertyName()))) {
                return respond(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "ROLE_NOT_ALLOWED",
                        "Only RECIPIENT and DONOR roles can be self-registered.",
                        req,
                        List.of());
            }
            if (t.getCause() == t) {
                break;
            }
        }
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request could not be parsed.", req, List.of());
    }

    @ExceptionHandler({
        MethodArgumentTypeMismatchException.class,
        MissingServletRequestParameterException.class
    })
    ResponseEntity<Map<String, Object>> malformed(Exception ex, HttpServletRequest req) {
        return respond(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "The request could not be parsed.", req, List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Map<String, Object>> denied(AccessDeniedException ex, HttpServletRequest req) {
        return respond(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not have permission to perform this action.", req, List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<Map<String, Object>> unauthenticated(AuthenticationException ex, HttpServletRequest req) {
        return respond(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.", req, List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Map<String, Object>> noResource(NoResourceFoundException ex, HttpServletRequest req) {
        return respond(HttpStatus.NOT_FOUND, "NOT_FOUND", "The requested resource was not found.", req, List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Map<String, Object>> method(HttpRequestMethodNotSupportedException ex, HttpServletRequest req) {
        return respond(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed.", req, List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Map<String, Object>> mediaType(HttpMediaTypeNotSupportedException ex, HttpServletRequest req) {
        return respond(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Unsupported content type.", req, List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception ex, HttpServletRequest req) {
        log.error("Unhandled exception for {} {}", req.getMethod(), req.getRequestURI(), ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred.", req, List.of());
    }

    private ResponseEntity<Map<String, Object>> validation(HttpServletRequest req, List<FieldError> errors) {
        return respond(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", "One or more fields are invalid.", req, errors);
    }

    private ResponseEntity<Map<String, Object>> respond(
            HttpStatus status, String code, String detail, HttpServletRequest req, List<FieldError> errors) {
        Map<String, Object> body = Problems.body(status.value(), code, detail, req.getRequestURI(), errors);
        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
    }
}
