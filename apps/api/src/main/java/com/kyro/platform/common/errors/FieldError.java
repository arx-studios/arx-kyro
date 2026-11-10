package com.kyro.platform.common.errors;

/** A single field-level validation problem, rendered in the Problem {@code errors[]} array. */
public record FieldError(String field, String message) {}
