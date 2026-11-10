package com.kyro.platform.verification.application;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.errors.ApiException;
import com.kyro.platform.common.errors.ConflictException;
import com.kyro.platform.common.errors.FieldError;
import com.kyro.platform.common.errors.ForbiddenException;
import com.kyro.platform.common.errors.NotFoundException;
import com.kyro.platform.common.errors.UnprocessableException;
import com.kyro.platform.common.security.KyroPrincipal;
import com.kyro.platform.common.storage.PresignedUrl;
import com.kyro.platform.education.application.port.EducationReviewFacade;
import com.kyro.platform.education.application.port.ReviewSnapshot;
import com.kyro.platform.education.application.port.ReviewSummary;
import com.kyro.platform.education.application.port.VerificationOutcome;
import com.kyro.platform.identity.application.ReviewRecipientView;
import com.kyro.platform.identity.application.UserDirectory;
import com.kyro.platform.identity.application.UserSummaryView;
import com.kyro.platform.verification.application.VerificationViews.CaseDetail;
import com.kyro.platform.verification.application.VerificationViews.CaseListItem;
import com.kyro.platform.verification.application.VerificationViews.CasePage;
import com.kyro.platform.verification.application.VerificationViews.DecisionCommand;
import com.kyro.platform.verification.application.VerificationViews.DecisionView;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verification workflow: queue, claim, decision and document access. Every state change runs in one transaction with
 * its audit rows and the matching {@link EducationReviewFacade} call, so a failure leaves nothing half-done.
 */
@Service
public class VerificationCaseService {

    static final String ENTITY_TYPE = "VerificationCase";

    private final CaseRepository cases;
    private final EducationReviewFacade education;
    private final UserDirectory users;
    private final AuditService audit;
    private final Clock clock;

    VerificationCaseService(
            CaseRepository cases, EducationReviewFacade education, UserDirectory users, AuditService audit, Clock clock) {
        this.cases = cases;
        this.education = education;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- events -------------------------------------------------------------------------------------------------

    /** Opens a case for a submission. Idempotent per (request, submissionNumber). */
    @Transactional
    public void openCase(UUID requestId, int submissionNumber) {
        cases.insertOpen(requestId, submissionNumber, clock.instant()).ifPresent(caseId -> audit.record(new AuditEntry(
                AuditActions.CASE_OPENED,
                ENTITY_TYPE,
                caseId,
                Map.of("requestId", requestId.toString(), "submissionNumber", submissionNumber))));
    }

    /** Closes any open or assigned case of a withdrawn request with outcome WITHDRAWN. */
    @Transactional
    public void closeForWithdrawal(UUID requestId) {
        for (UUID caseId : cases.closeUnclosedForRequest(requestId, "WITHDRAWN", clock.instant())) {
            audit.record(new AuditEntry(
                    AuditActions.CASE_CLOSED,
                    ENTITY_TYPE,
                    caseId,
                    Map.of("requestId", requestId.toString(), "outcome", "WITHDRAWN")));
        }
    }

    // ---- queries ------------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public CasePage list(Queue queue, String cursor, int limit, KyroPrincipal actor) {
        Instant afterAt = null;
        UUID afterId = null;
        if (cursor != null && !cursor.isBlank()) {
            try {
                String raw = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
                int sep = raw.indexOf('|');
                afterAt = Instant.parse(raw.substring(0, sep));
                afterId = UUID.fromString(raw.substring(sep + 1));
            } catch (RuntimeException e) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Invalid cursor.", null);
            }
        }
        List<CaseRow> rows = cases.page(queue, actor.userId(), afterAt, afterId, limit + 1);
        String next = null;
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
            CaseRow last = rows.get(rows.size() - 1);
            next = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString((last.openedAt() + "|" + last.id()).getBytes(StandardCharsets.UTF_8));
        }
        Map<UUID, ReviewSummary> summaries =
                education.getReviewSummaries(rows.stream().map(CaseRow::requestId).distinct().toList());
        Map<UUID, UserSummaryView> assignees = users.findSummaries(
                rows.stream().map(CaseRow::assignedTo).filter(java.util.Objects::nonNull).distinct().toList());
        List<CaseListItem> items = rows.stream()
                .map(r -> new CaseListItem(
                        r, summaries.get(r.requestId()), r.assignedTo() == null ? null : assignees.get(r.assignedTo())))
                .toList();
        return new CasePage(items, next);
    }

    @Transactional(readOnly = true)
    public CaseDetail get(UUID caseId, KyroPrincipal actor) {
        return detail(load(caseId), actor);
    }

    // ---- commands -----------------------------------------------------------------------------------------------

    /** Claims the case for the caller. Re-claiming a case the caller already holds is a no-op. */
    @Transactional(noRollbackFor = ForbiddenException.class)
    public CaseDetail assign(UUID caseId, KyroPrincipal actor) {
        CaseRow row = load(caseId);
        guardSelfReview(row, actor, "assign");
        if (row.isAssigned() && actor.userId().equals(row.assignedTo())) {
            return detail(row, actor);
        }
        if (row.isAssigned()) {
            throw new ConflictException("CASE_ALREADY_ASSIGNED", "This case is already assigned to another verifier.");
        }
        if (row.isClosed()) {
            throw new ConflictException("INVALID_STATE_TRANSITION", "This case is closed.");
        }
        if (!cases.claim(caseId, actor.userId(), clock.instant())) {
            // lost a race with another verifier between the read and the conditional update
            throw new ConflictException("CASE_ALREADY_ASSIGNED", "This case is already assigned to another verifier.");
        }
        education.markInReview(row.requestId(), actor.userId());
        audit.record(new AuditEntry(
                AuditActions.CASE_ASSIGNED,
                ENTITY_TYPE,
                caseId,
                Map.of("requestId", row.requestId().toString(), "assignedTo", actor.userId().toString())));
        return detail(load(caseId), actor);
    }

    /** Records the decision, closes the case and applies the outcome to the request, atomically. */
    @Transactional(noRollbackFor = ForbiddenException.class)
    public CaseDetail decide(UUID caseId, DecisionCommand command, KyroPrincipal actor) {
        CaseRow row = load(caseId);
        guardSelfReview(row, actor, "decide");
        if (row.isClosed()) {
            throw new ConflictException("INVALID_STATE_TRANSITION", "This case is already closed.");
        }
        if (!row.isAssigned() || !actor.userId().equals(row.assignedTo())) {
            throw new ConflictException("CASE_NOT_ASSIGNED_TO_YOU", "Only the assigned verifier can decide this case.");
        }
        VerificationOutcome outcome = outcomeOf(command.decision());
        String rationale = command.rationale() == null ? "" : command.rationale().strip();
        String message = command.messageToRecipient() == null ? null : command.messageToRecipient().strip();
        if (message != null && message.isEmpty()) {
            message = null;
        }
        List<FieldError> errors = new ArrayList<>();
        if (rationale.length() < 10 || rationale.length() > 2000) {
            errors.add(new FieldError("rationale", "must be between 10 and 2000 characters"));
        }
        if (message != null && message.length() > 2000) {
            errors.add(new FieldError("messageToRecipient", "must be at most 2000 characters"));
        }
        if (outcome != VerificationOutcome.APPROVED && message == null) {
            errors.add(new FieldError("messageToRecipient", "is required when rejecting or requesting changes"));
        }
        if (!errors.isEmpty()) {
            throw new UnprocessableException("VALIDATION_FAILED", "The decision is not valid.", errors);
        }

        Instant now = clock.instant();
        if (!cases.closeAssigned(caseId, actor.userId(), outcome.name(), now)) {
            throw new ConflictException("CASE_NOT_ASSIGNED_TO_YOU", "Only the assigned verifier can decide this case.");
        }
        cases.insertDecision(caseId, actor.userId(), command.decision(), rationale, message, now);
        education.applyDecision(row.requestId(), outcome, actor.userId(), message);
        audit.record(new AuditEntry(
                AuditActions.VERIFICATION_DECIDED,
                ENTITY_TYPE,
                caseId,
                Map.of("decision", command.decision(), "caseId", caseId.toString(), "requestId", row.requestId().toString())));
        audit.record(new AuditEntry(
                AuditActions.CASE_CLOSED,
                ENTITY_TYPE,
                caseId,
                Map.of("requestId", row.requestId().toString(), "outcome", outcome.name())));
        return detail(load(caseId), actor);
    }

    /** Presigned GET for one document of the case's request; the education facade audits each issuance. */
    @Transactional
    public PresignedUrl issueDocumentViewUrl(UUID caseId, UUID documentId, KyroPrincipal actor) {
        CaseRow row = load(caseId);
        return education.issueDocumentViewUrl(row.requestId(), documentId, actor.userId());
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private CaseRow load(UUID caseId) {
        return cases.find(caseId).orElseThrow(NotFoundException::new);
    }

    private UUID requestOwner(CaseRow row) {
        ReviewSummary summary = education.getReviewSummaries(List.of(row.requestId())).get(row.requestId());
        if (summary == null) {
            throw new NotFoundException();
        }
        return summary.recipientUserId();
    }

    /** A verifier may never act on their own request. Nothing is written before this check. */
    private void guardSelfReview(CaseRow row, KyroPrincipal actor, String action) {
        if (actor.userId().equals(requestOwner(row))) {
            audit.record(new AuditEntry(
                    AuditActions.ACCESS_DENIED,
                    ENTITY_TYPE,
                    row.id(),
                    Map.of("resource", "verification_case", "reason", "SELF_REVIEW", "action", action)));
            throw new ForbiddenException("SELF_REVIEW_FORBIDDEN", "You cannot review your own request.");
        }
    }

    private static VerificationOutcome outcomeOf(String decision) {
        return switch (decision) {
            case "APPROVE" -> VerificationOutcome.APPROVED;
            case "REJECT" -> VerificationOutcome.REJECTED;
            case "REQUEST_CHANGES" -> VerificationOutcome.CHANGES_REQUESTED;
            default -> throw new UnprocessableException(
                    "VALIDATION_FAILED", "Unknown decision.", List.of(new FieldError("decision", "is not supported")));
        };
    }

    private CaseDetail detail(CaseRow row, KyroPrincipal actor) {
        ReviewSnapshot snapshot = education.getReviewSnapshot(row.requestId());
        ReviewRecipientView recipient = users.findRecipient(snapshot.recipientUserId()).orElse(null);
        List<DecisionRow> decisions = cases.decisionsForRequest(row.requestId());
        Set<UUID> ids = new HashSet<>();
        decisions.forEach(d -> ids.add(d.decidedBy()));
        if (row.assignedTo() != null) {
            ids.add(row.assignedTo());
        }
        Map<UUID, UserSummaryView> names = users.findSummaries(ids);
        List<DecisionView> views = decisions.stream()
                .map(d -> new DecisionView(d, names.get(d.decidedBy())))
                .toList();
        boolean canDecide = row.isAssigned()
                && actor.userId().equals(row.assignedTo())
                && !actor.userId().equals(snapshot.recipientUserId());
        return new CaseDetail(
                row, row.assignedTo() == null ? null : names.get(row.assignedTo()), snapshot, recipient, views, canDecide);
    }
}
