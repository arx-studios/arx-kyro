package com.kyro.platform.operations.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence port of the operations module. All state transitions are conditional updates. */
public interface ReportRepository {

    /** Owner of the request when it is currently in the public discovery view (approved and not on hold). */
    Optional<UUID> findPublicRequestOwner(UUID requestId);

    /** Inserts an OPEN report; empty when the reporter already has an unresolved report on the request. */
    Optional<Inserted> insertReport(UUID reporterId, UUID requestId, String reason, String details);

    record Inserted(UUID id, Instant createdAt) {}

    Optional<ReportRow> find(UUID reportId);

    List<ReportRow> page(ReportQueue queue, UUID actorId, Instant afterAt, UUID afterId, int limit);

    List<ReportRow> otherReports(UUID requestId, UUID excludeReportId);

    /** OPEN to IN_REVIEW for the given assignee. */
    boolean claim(UUID reportId, UUID userId, Instant at);

    /** IN_REVIEW to RESOLVED, only by the assignee. */
    boolean resolve(UUID reportId, UUID assignee, String resolution, String note, Instant at);

    /** Inserts an active hold; false when one is already active for the request. */
    boolean insertHold(UUID requestId, UUID reportId, String reason, UUID placedBy, Instant at);

    /** Releases the active hold; false when none is active. */
    boolean releaseHold(UUID requestId, UUID releasedBy, Instant at);

    Optional<HoldRow> activeHold(UUID requestId);

    Optional<UUID> latestCaseId(UUID requestId);

    DashboardCounts counts(UUID actorId, Instant now);
}
