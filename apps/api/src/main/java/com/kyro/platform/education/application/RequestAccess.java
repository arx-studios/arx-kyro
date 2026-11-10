package com.kyro.platform.education.application;

import com.kyro.platform.common.errors.ConflictException;
import com.kyro.platform.common.errors.NotFoundException;
import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.education.domain.EducationStore;
import com.kyro.platform.education.domain.FundingRequest;
import com.kyro.platform.education.domain.RequestStatus;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Loading, object-level authorization and state-transition helpers shared by the education services. */
@Component
class RequestAccess {

    private final EducationStore store;
    private final CurrentActor actor;
    private final AccessAuditor auditor;

    RequestAccess(EducationStore store, CurrentActor actor, AccessAuditor auditor) {
        this.store = store;
        this.actor = actor;
        this.auditor = auditor;
    }

    /** The caller's own request. Someone else's request is a 404 plus an ACCESS_DENIED audit row. */
    FundingRequest loadOwned(UUID requestId, boolean forUpdate, String resource) {
        FundingRequest r = store.findRequest(requestId, forUpdate).orElseThrow(NotFoundException::new);
        if (!r.recipientUserId().equals(actor.require().userId())) {
            auditor.denied(requestId, resource, "NOT_OWNER");
            throw new NotFoundException();
        }
        return r;
    }

    static void requireEditable(FundingRequest r) {
        if (!r.status().isEditable()) {
            throw new ConflictException(
                    "INVALID_STATE_TRANSITION", "A request in status " + r.status() + " can no longer be edited.");
        }
    }

    static void requireTransition(FundingRequest r, RequestStatus to) {
        if (!r.status().canTransitionTo(to)) {
            throw new ConflictException(
                    "INVALID_STATE_TRANSITION", "Cannot move a request from " + r.status() + " to " + to + ".");
        }
    }
}
