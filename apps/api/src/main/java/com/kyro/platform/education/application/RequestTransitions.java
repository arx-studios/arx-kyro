package com.kyro.platform.education.application;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.errors.ConflictException;
import com.kyro.platform.education.domain.EducationStore;
import com.kyro.platform.education.domain.FundingRequest;
import com.kyro.platform.education.domain.RequestStatus;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Applies a lifecycle transition: guarded update, status history row and audit row, in the caller's transaction. */
@Component
class RequestTransitions {

    private final EducationStore store;
    private final AuditService audit;
    private final Clock clock;

    RequestTransitions(EducationStore store, AuditService audit, Clock clock) {
        this.store = store;
        this.audit = audit;
        this.clock = clock;
    }

    /** @return the transition timestamp */
    Instant apply(FundingRequest r, RequestStatus to, UUID changedBy, String note) {
        RequestAccess.requireTransition(r, to);
        Instant now = Instant.now(clock);
        if (!store.transition(r.id(), r.status(), to, r.version(), now)) {
            throw new ConflictException("INVALID_STATE_TRANSITION", "The request was modified concurrently; reload and retry.");
        }
        store.addHistory(r.id(), r.status(), to, changedBy, note);
        Map<String, Object> details = new HashMap<>();
        details.put("from", r.status().name());
        details.put("to", to.name());
        audit.record(new AuditEntry(AuditActions.REQUEST_STATUS_CHANGED, "FUNDING_REQUEST", r.id(), details));
        return now;
    }
}
