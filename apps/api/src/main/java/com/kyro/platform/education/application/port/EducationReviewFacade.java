package com.kyro.platform.education.application.port;

import com.kyro.platform.common.storage.PresignedUrl;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

// FROZEN CONTRACT: change signatures only with a DECISIONS.md entry.
/**
 * What the verification module may ask of the education module. Implemented by API-2 (education), consumed by
 * API-3 (verification). All state changes run in the caller's transaction and write status history + audit rows.
 */
public interface EducationReviewFacade {

    /** Summaries for queue listings, keyed by request id. Unknown ids are absent. */
    Map<UUID, ReviewSummary> getReviewSummaries(Collection<UUID> requestIds);

    /**
     * Full private view of the request (documents, status history, duplicate indicators) for staff review.
     *
     * @throws com.kyro.platform.common.errors.NotFoundException if the request does not exist
     */
    ReviewSnapshot getReviewSnapshot(UUID requestId);

    /** SUBMITTED -> IN_REVIEW. Throws ConflictException(INVALID_STATE_TRANSITION) from any other status. */
    void markInReview(UUID requestId, UUID verifierId);

    /**
     * IN_REVIEW -> APPROVED / REJECTED / CHANGES_REQUESTED. {@code messageToRecipient} becomes the recipient-visible
     * status history note (required by the verification module for REJECTED and CHANGES_REQUESTED).
     */
    void applyDecision(UUID requestId, VerificationOutcome outcome, UUID verifierId, String messageToRecipient);

    /** Short-lived presigned GET for one document of the request. Audits DOCUMENT_VIEW_URL_ISSUED. 404 if the document is not on the request. */
    PresignedUrl issueDocumentViewUrl(UUID requestId, UUID documentId, UUID actorId);
}
