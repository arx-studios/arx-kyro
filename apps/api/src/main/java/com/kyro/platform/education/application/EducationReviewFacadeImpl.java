package com.kyro.platform.education.application;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.common.errors.NotFoundException;
import com.kyro.platform.common.storage.ObjectStorage;
import com.kyro.platform.common.storage.PresignedUrl;
import com.kyro.platform.education.application.port.DuplicateIndicatorView;
import com.kyro.platform.education.application.port.EducationReviewFacade;
import com.kyro.platform.education.application.port.ReviewDocument;
import com.kyro.platform.education.application.port.ReviewSnapshot;
import com.kyro.platform.education.application.port.ReviewStatusHistoryEntry;
import com.kyro.platform.education.application.port.ReviewSummary;
import com.kyro.platform.education.application.port.VerificationOutcome;
import com.kyro.platform.education.domain.DuplicateMatch;
import com.kyro.platform.education.domain.EducationStore;
import com.kyro.platform.education.domain.FundingRequest;
import com.kyro.platform.education.domain.RequestDocument;
import com.kyro.platform.education.domain.RequestStatus;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff-facing port used by the verification module. All changes run in the caller's transaction. */
@Service
class EducationReviewFacadeImpl implements EducationReviewFacade {

    private final EducationStore store;
    private final RequestTransitions transitions;
    private final ObjectStorage storage;
    private final KyroProperties props;
    private final AuditService audit;

    EducationReviewFacadeImpl(
            EducationStore store, RequestTransitions transitions, ObjectStorage storage, KyroProperties props, AuditService audit) {
        this.store = store;
        this.transitions = transitions;
        this.storage = storage;
        this.props = props;
        this.audit = audit;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ReviewSummary> getReviewSummaries(Collection<UUID> requestIds) {
        Map<UUID, ReviewSummary> out = new LinkedHashMap<>();
        store.findRequests(requestIds).forEach((id, r) -> out.put(
                id, new ReviewSummary(id, r.recipientUserId(), r.title(), r.publicDisplayName(), r.amountMinor(), r.currency())));
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public ReviewSnapshot getReviewSnapshot(UUID requestId) {
        FundingRequest r = store.findRequest(requestId, false).orElseThrow(NotFoundException::new);
        RequestDetail d = new RequestDetail(r, store.documents(requestId), store.history(requestId));
        List<ReviewDocument> docs = d.documents().stream()
                .map(x -> new ReviewDocument(
                        x.id(), x.documentType(), x.originalFileName(), x.contentType(), x.sizeBytes(), x.status(),
                        x.createdAt(), x.uploadedAt()))
                .toList();
        List<ReviewStatusHistoryEntry> history = d.history().stream()
                .map(h -> new ReviewStatusHistoryEntry(
                        h.fromStatus() == null ? null : h.fromStatus().name(), h.toStatus().name(), h.changedAt(), h.note()))
                .toList();
        List<DuplicateIndicatorView> dups = store.findDuplicates(r).stream().map(EducationReviewFacadeImpl::toView).toList();
        return new ReviewSnapshot(
                r.id(), r.recipientUserId(), r.status().name(), r.title(), r.story(), r.expenseType(), r.institutionName(),
                r.institutionCity(), r.institutionCountry(), r.courseName(), r.feeReference(), r.amountMinor(), r.currency(),
                r.dueDate(), r.submissionCount(), r.publicDisplayName(), d.editable(), d.reviewerFeedback(), r.submittedAt(),
                r.approvedAt(), r.createdAt(), r.updatedAt(), r.version(), docs, history, dups);
    }

    @Override
    @Transactional
    public void markInReview(UUID requestId, UUID verifierId) {
        FundingRequest r = store.findRequest(requestId, true).orElseThrow(NotFoundException::new);
        transitions.apply(r, RequestStatus.IN_REVIEW, verifierId, null);
    }

    @Override
    @Transactional
    public void applyDecision(UUID requestId, VerificationOutcome outcome, UUID verifierId, String messageToRecipient) {
        FundingRequest r = store.findRequest(requestId, true).orElseThrow(NotFoundException::new);
        RequestStatus target =
                switch (outcome) {
                    case APPROVED -> RequestStatus.APPROVED;
                    case REJECTED -> RequestStatus.REJECTED;
                    case CHANGES_REQUESTED -> RequestStatus.CHANGES_REQUESTED;
                };
        // The request must be IN_REVIEW; any other status fails the transition guard with 409.
        if (r.status() != RequestStatus.IN_REVIEW) {
            throw new com.kyro.platform.common.errors.ConflictException(
                    "INVALID_STATE_TRANSITION", "A decision can only be applied to a request IN_REVIEW (was " + r.status() + ").");
        }
        transitions.apply(r, target, verifierId, messageToRecipient);
    }

    @Override
    @Transactional
    public PresignedUrl issueDocumentViewUrl(UUID requestId, UUID documentId, UUID actorId) {
        RequestDocument d = store.findDocument(requestId, documentId, false).orElseThrow(NotFoundException::new);
        if (!RequestDocument.UPLOADED.equals(d.status())) {
            throw new NotFoundException();
        }
        PresignedUrl url = storage.presignGet(d.storageKey(), props.documents().viewUrlTtl());
        audit.record(new AuditEntry(
                AuditActions.DOCUMENT_VIEW_URL_ISSUED,
                "REQUEST_DOCUMENT",
                documentId,
                Map.of("requestId", requestId.toString(), "viewerUserId", actorId.toString())));
        return url;
    }

    private static DuplicateIndicatorView toView(DuplicateMatch m) {
        String detail = m.kind().equals("DOCUMENT_HASH_MATCH")
                ? "An identical document was uploaded to another request."
                : "Another request uses the same fee reference at the same institution.";
        return new DuplicateIndicatorView(m.kind(), m.otherRequestId(), m.otherStatus().name(), detail);
    }
}
