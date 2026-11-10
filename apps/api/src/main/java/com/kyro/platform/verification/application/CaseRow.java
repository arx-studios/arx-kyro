package com.kyro.platform.verification.application;

import java.time.Instant;
import java.util.UUID;

/** A verification case as stored. {@code status} is OPEN, ASSIGNED or CLOSED; {@code outcome} is set once CLOSED. */
public record CaseRow(
        UUID id,
        UUID requestId,
        int submissionNumber,
        String status,
        String outcome,
        UUID assignedTo,
        Instant assignedAt,
        Instant openedAt,
        Instant closedAt,
        long version) {

    public boolean isOpen() {
        return "OPEN".equals(status);
    }

    public boolean isAssigned() {
        return "ASSIGNED".equals(status);
    }

    public boolean isClosed() {
        return "CLOSED".equals(status);
    }
}
