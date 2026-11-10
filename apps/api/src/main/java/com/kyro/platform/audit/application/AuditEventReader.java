package com.kyro.platform.audit.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Read port over audit_events. Keyset order is (occurred_at DESC, id DESC). */
public interface AuditEventReader {

    /** Returns up to {@code limit} rows strictly after the cursor position (null = from the newest). */
    List<AuditEventRecord> find(
            String entityType, UUID entityId, UUID actorUserId, Instant afterOccurredAt, UUID afterId, int limit);
}
