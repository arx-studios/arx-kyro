package com.kyro.platform.audit.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventRecord(
        UUID id,
        Instant occurredAt,
        UUID actorUserId,
        String action,
        String entityType,
        UUID entityId,
        String correlationId,
        Map<String, Object> details) {}
