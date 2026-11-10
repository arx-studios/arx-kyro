package com.kyro.platform.verification.application;

import com.kyro.platform.common.errors.ApiException;
import org.springframework.http.HttpStatus;

/** Queue filter of the case list. Open queues are oldest-first; CLOSED and ALL are newest-first. */
public enum Queue {
    UNASSIGNED(true),
    MINE(true),
    CLOSED(false),
    ALL(false);

    private final boolean oldestFirst;

    Queue(boolean oldestFirst) {
        this.oldestFirst = oldestFirst;
    }

    public boolean oldestFirst() {
        return oldestFirst;
    }

    public static Queue parse(String value) {
        if (value == null || value.isBlank()) {
            return UNASSIGNED;
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Unknown queue: " + value, null);
        }
    }
}
