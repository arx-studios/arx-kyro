package com.kyro.platform.education.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Persistence port of the education module (implemented with JdbcTemplate in infrastructure). */
public interface EducationStore {

    UUID insertRequest(UUID recipientUserId, RequestFields fields);

    /** {@code forUpdate} takes a row lock for the rest of the transaction. */
    Optional<FundingRequest> findRequest(UUID id, boolean forUpdate);

    Map<UUID, FundingRequest> findRequests(Collection<UUID> ids);

    /** Conditional on {@code version}; bumps the version. Returns false if the row changed meanwhile. */
    boolean updateFields(UUID id, long expectedVersion, RequestFields fields);

    /**
     * Conditional on status and version. SUBMITTED increments submission_count and sets submitted_at;
     * APPROVED sets approved_at. Bumps the version.
     */
    boolean transition(UUID id, RequestStatus from, RequestStatus to, long expectedVersion, Instant now);

    void addHistory(UUID requestId, RequestStatus from, RequestStatus to, UUID changedBy, String note);

    List<StatusHistoryItem> history(UUID requestId);

    List<RequestSummaryRow> listByRecipient(UUID recipientUserId);

    void insertDocument(RequestDocument doc);

    Optional<RequestDocument> findDocument(UUID requestId, UUID documentId, boolean forUpdate);

    /** Non-deleted documents, oldest first. */
    List<RequestDocument> documents(UUID requestId);

    int countActiveDocuments(UUID requestId);

    int countUploadedDocuments(UUID requestId);

    void markUploaded(UUID documentId, String sha256, Instant at);

    void markRejected(UUID documentId);

    void markDeleted(UUID documentId, Instant at);

    List<DuplicateMatch> findDuplicates(FundingRequest request);
}
