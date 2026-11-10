package com.kyro.platform.audit.application;

import java.util.Map;
import java.util.UUID;

// FROZEN CONTRACT: change signatures only with a DECISIONS.md entry.
public record AuditEntry(String action, String entityType, UUID entityId, Map<String, Object> details) {

    public AuditEntry {
        details = details == null ? Map.of() : details;
    }
}
