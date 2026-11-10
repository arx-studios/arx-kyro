package com.kyro.platform.funding.application;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Writes ACCESS_DENIED rows in their own transaction so they survive the 404 that rolls back the caller. */
@Component("pledgeAccessAuditor")
class AccessAuditor {

    private final AuditService audit;

    AccessAuditor(AuditService audit) {
        this.audit = audit;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void denied(UUID pledgeId, String resource) {
        audit.record(new AuditEntry(
                AuditActions.ACCESS_DENIED, "PLEDGE", pledgeId, Map.of("resource", resource, "reason", "NOT_OWNER")));
    }
}
