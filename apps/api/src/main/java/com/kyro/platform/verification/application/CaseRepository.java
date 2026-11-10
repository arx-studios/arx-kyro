package com.kyro.platform.verification.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port of the verification module. All writes are conditional so concurrent callers cannot double-act. */
public interface CaseRepository {

    Optional<CaseRow> find(UUID caseId);

    /** Opens a case; empty when a case for (request, submission) or another open case already exists. */
    Optional<UUID> insertOpen(UUID requestId, int submissionNumber, Instant openedAt);

    /** OPEN -> ASSIGNED for {@code userId}. False when the case is no longer OPEN. */
    boolean claim(UUID caseId, UUID userId, Instant at);

    /** ASSIGNED (to {@code assignee}) -> CLOSED with the outcome. False when the precondition no longer holds. */
    boolean closeAssigned(UUID caseId, UUID assignee, String outcome, Instant at);

    /** Closes every non-closed case of the request; returns the ids closed. */
    List<UUID> closeUnclosedForRequest(UUID requestId, String outcome, Instant at);

    void insertDecision(UUID caseId, UUID decidedBy, String decision, String rationale, String messageToRecipient, Instant at);

    /** Decisions across all cases of the request, newest first. */
    List<DecisionRow> decisionsForRequest(UUID requestId);

    /** Keyset page ordered by (opened_at, id) in the queue's direction; the cursor is exclusive. */
    List<CaseRow> page(Queue queue, UUID userId, Instant afterOpenedAt, UUID afterId, int limit);
}
