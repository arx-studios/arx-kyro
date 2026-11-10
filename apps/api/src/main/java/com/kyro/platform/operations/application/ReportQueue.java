package com.kyro.platform.operations.application;

import com.kyro.platform.common.errors.ApiException;
import org.springframework.http.HttpStatus;

/** Queue filter of the report list. OPEN means OPEN plus IN_REVIEW. OPEN and MINE are oldest-first. */
public enum ReportQueue {
    OPEN(true),
    MINE(true),
    RESOLVED(false),
    ALL(false);

    private final boolean oldestFirst;

    ReportQueue(boolean oldestFirst) {
        this.oldestFirst = oldestFirst;
    }

    public boolean oldestFirst() {
        return oldestFirst;
    }

    public static ReportQueue parse(String value) {
        if (value == null || value.isBlank()) {
            return OPEN;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Unknown queue: " + value, null);
        }
    }
}
