package com.kyro.platform.audit.application;

// FROZEN CONTRACT: change signatures only with a DECISIONS.md entry.
/**
 * Appends an immutable audit event. Participates in the caller's transaction (same DataSource), so a rolled-back
 * business change leaves no audit row. Actor, roles, correlation id and client IP are filled from the context.
 */
public interface AuditService {

    void record(AuditEntry entry);
}
