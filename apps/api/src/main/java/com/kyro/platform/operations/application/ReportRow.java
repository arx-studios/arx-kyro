package com.kyro.platform.operations.application;

import java.time.Instant;
import java.util.UUID;

/** One report joined with the facts about its request that staff need. Never exposed to the request's recipient. */
public record ReportRow(
        UUID id,
        UUID reporterId,
        UUID requestId,
        String requestTitle,
        UUID requestOwnerId,
        String requestStatus,
        String reason,
        String details,
        String status,
        UUID assignedTo,
        Instant createdAt,
        String resolution,
        String resolutionNote,
        UUID resolvedBy,
        Instant resolvedAt,
        boolean onHold) {

    public boolean isResolved() {
        return "RESOLVED".equals(status);
    }
}
