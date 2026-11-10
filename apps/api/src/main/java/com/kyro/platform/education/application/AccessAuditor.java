package com.kyro.platform.education.application;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes ACCESS_DENIED audit rows in their own transaction: the caller is about to throw 404, which rolls back its
 * own transaction, and the denial must survive.
 */
@Component
class AccessAuditor {

    private final AuditService audit;

    AccessAuditor(AuditService audit) {
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void denied(UUID requestId, String resource, String reason) {
        audit.record(new AuditEntry(
                AuditActions.ACCESS_DENIED, "FUNDING_REQUEST", requestId, Map.of("resource", resource, "reason", reason)));
    }
}
