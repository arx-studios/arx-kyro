package com.kyro.platform.education.application.port;

import com.kyro.platform.common.errors.NotFoundException;
import com.kyro.platform.common.storage.PresignedUrl;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Placeholder facade for API-3's unit tests (test sources only, not a Spring bean). Returns empty results and
 * ignores state changes; mock or subclass it with Mockito/overrides as needed.
 */
public class NoOpEducationReviewFacade implements EducationReviewFacade {

    @Override
    public Map<UUID, ReviewSummary> getReviewSummaries(Collection<UUID> requestIds) {
        return Map.of();
    }

    @Override
    public ReviewSnapshot getReviewSnapshot(UUID requestId) {
        throw new NotFoundException();
    }

    @Override
    public void markInReview(UUID requestId, UUID verifierId) {}

    @Override
    public void applyDecision(UUID requestId, VerificationOutcome outcome, UUID verifierId, String messageToRecipient) {}

    @Override
    public PresignedUrl issueDocumentViewUrl(UUID requestId, UUID documentId, UUID actorId) {
        throw new NotFoundException();
    }
}
