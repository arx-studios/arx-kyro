package com.kyro.platform.operations.application;

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
import com.kyro.platform.identity.application.UserDirectory;
import com.kyro.platform.identity.application.UserSummaryView;
import com.kyro.platform.operations.application.OperationsViews.HoldView;
import com.kyro.platform.operations.application.OperationsViews.ReportDetail;
import com.kyro.platform.operations.application.OperationsViews.ReportItem;
import com.kyro.platform.operations.application.OperationsViews.ReportPage;
import com.kyro.platform.operations.application.OperationsViews.ResolveCommand;
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
 * User reports, the staff report queue and moderation holds. Every state change runs in one transaction together with
 * its audit row. Audit details never contain report text or the reporter's identity.
 */
@Service
public class ReportService {

    static final String REPORT_ENTITY = "USER_REPORT";
    static final String REQUEST_ENTITY = "FUNDING_REQUEST";

    private final ReportRepository repo;
    private final UserDirectory users;
    private final AuditService audit;
    private final Clock clock;

    ReportService(ReportRepository repo, UserDirectory users, AuditService audit, Clock clock) {
        this.repo = repo;
        this.users = users;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- user side ----------------------------------------------------------------------------------------------

    @Transactional
    public ReportRepository.Inserted create(UUID requestId, String reason, String details, KyroPrincipal actor) {
        String cleaned = details == null ? null : details.strip();
        if (cleaned != null && cleaned.isEmpty()) {
            cleaned = null;
        }
        if ("OTHER".equals(reason) && cleaned == null) {
            throw new UnprocessableException(
                    "VALIDATION_FAILED",
                    "The report is not valid.",
                    List.of(new FieldError("details", "is required when the reason is OTHER")));
        }
        UUID owner = repo.findPublicRequestOwner(requestId).orElseThrow(NotFoundException::new);
        if (owner.equals(actor.userId())) {
            throw new UnprocessableException(
                    "VALIDATION_FAILED",
                    "The report is not valid.",
                    List.of(new FieldError("requestId", "you cannot report your own request")));
        }
        ReportRepository.Inserted inserted = repo.insertReport(actor.userId(), requestId, reason, cleaned)
                .orElseThrow(() -> new ConflictException(
                        "REPORT_ALREADY_OPEN", "You already have an open report on this request."));
        audit.record(new AuditEntry(
                AuditActions.REPORT_CREATED,
                REPORT_ENTITY,
                inserted.id(),
                Map.of("requestId", requestId.toString(), "reason", reason)));
        return inserted;
    }

    // ---- staff queries ------------------------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public ReportPage list(ReportQueue queue, String cursor, int limit, KyroPrincipal actor) {
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
        List<ReportRow> rows = repo.page(queue, actor.userId(), afterAt, afterId, limit + 1);
        String next = null;
        if (rows.size() > limit) {
            rows = rows.subList(0, limit);
            ReportRow last = rows.get(rows.size() - 1);
            next = Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString((last.createdAt() + "|" + last.id()).getBytes(StandardCharsets.UTF_8));
        }
        return new ReportPage(items(rows), next);
    }

    @Transactional(readOnly = true)
    public ReportDetail get(UUID reportId, KyroPrincipal actor) {
        return detail(load(reportId), actor);
    }

    @Transactional(readOnly = true)
    public DashboardCounts dashboard(KyroPrincipal actor) {
        return repo.counts(actor.userId(), clock.instant());
    }

    // ---- staff commands -----------------------------------------------------------------------------------------

    /** Claims the report. Re-claiming a report the caller already holds is a no-op. */
    @Transactional(noRollbackFor = ForbiddenException.class)
    public ReportDetail assign(UUID reportId, KyroPrincipal actor) {
        ReportRow row = load(reportId);
        guardSelfReview(row, actor, "assign");
        if (row.isResolved()) {
            throw new ConflictException("INVALID_STATE_TRANSITION", "This report is already resolved.");
        }
        if (row.assignedTo() != null) {
            if (actor.userId().equals(row.assignedTo())) {
                return detail(row, actor);
            }
            throw new ConflictException("REPORT_ALREADY_ASSIGNED", "This report is already assigned to another reviewer.");
        }
        if (!repo.claim(reportId, actor.userId(), clock.instant())) {
            throw new ConflictException("REPORT_ALREADY_ASSIGNED", "This report is already assigned to another reviewer.");
        }
        audit.record(new AuditEntry(
                AuditActions.REPORT_ASSIGNED,
                REPORT_ENTITY,
                reportId,
                Map.of("requestId", row.requestId().toString(), "assignedTo", actor.userId().toString())));
        return detail(load(reportId), actor);
    }

    /** Resolves the report as the assignee and optionally places a moderation hold, atomically. */
    @Transactional(noRollbackFor = ForbiddenException.class)
    public ReportDetail resolve(UUID reportId, ResolveCommand command, KyroPrincipal actor) {
        ReportRow row = load(reportId);
        guardSelfReview(row, actor, "resolve");
        if (row.isResolved()) {
            throw new ConflictException("INVALID_STATE_TRANSITION", "This report is already resolved.");
        }
        if (row.assignedTo() == null || !actor.userId().equals(row.assignedTo())) {
            throw new ConflictException(
                    "REPORT_NOT_ASSIGNED_TO_YOU", "Only the assigned reviewer can resolve this report.");
        }
        String note = command.note() == null ? "" : command.note().strip();
        List<FieldError> errors = new ArrayList<>();
        if (note.length() < 10 || note.length() > 2000) {
            errors.add(new FieldError("note", "must be between 10 and 2000 characters"));
        }
        if (command.holdRequest() && !"ACTION_TAKEN".equals(command.resolution())) {
            errors.add(new FieldError("holdRequest", "is allowed only with ACTION_TAKEN"));
        }
        if (!errors.isEmpty()) {
            throw new UnprocessableException("VALIDATION_FAILED", "The resolution is not valid.", errors);
        }
        Instant now = clock.instant();
        if (!repo.resolve(reportId, actor.userId(), command.resolution(), note, now)) {
            throw new ConflictException(
                    "REPORT_NOT_ASSIGNED_TO_YOU", "Only the assigned reviewer can resolve this report.");
        }
        audit.record(new AuditEntry(
                AuditActions.REPORT_RESOLVED,
                REPORT_ENTITY,
                reportId,
                Map.of(
                        "requestId", row.requestId().toString(),
                        "resolution", command.resolution(),
                        "holdRequest", command.holdRequest())));
        if (command.holdRequest()) {
            String reason = "Report " + row.reason() + ": " + note;
            if (reason.length() > 500) {
                reason = reason.substring(0, 500);
            }
            // Idempotent: an already active hold is kept as is and nothing new is audited.
            if (repo.insertHold(row.requestId(), reportId, reason, actor.userId(), now)) {
                audit.record(new AuditEntry(
                        AuditActions.REQUEST_HOLD_PLACED,
                        REQUEST_ENTITY,
                        row.requestId(),
                        Map.of("reportId", reportId.toString())));
            }
        }
        return detail(load(reportId), actor);
    }

    /** ADMIN only (route level). 404 when there is no active hold. */
    @Transactional
    public void releaseHold(UUID requestId, KyroPrincipal actor) {
        if (!repo.releaseHold(requestId, actor.userId(), clock.instant())) {
            throw new NotFoundException();
        }
        audit.record(new AuditEntry(
                AuditActions.REQUEST_HOLD_RELEASED,
                REQUEST_ENTITY,
                requestId,
                Map.of("releasedBy", actor.userId().toString())));
    }

    // ---- helpers ------------------------------------------------------------------------------------------------

    private ReportRow load(UUID reportId) {
        return repo.find(reportId).orElseThrow(NotFoundException::new);
    }

    /** Staff may not act on a report about their own request or one they filed. Nothing is written before this. */
    private void guardSelfReview(ReportRow row, KyroPrincipal actor, String action) {
        boolean ownRequest = actor.userId().equals(row.requestOwnerId());
        boolean ownReport = actor.userId().equals(row.reporterId());
        if (ownRequest || ownReport) {
            audit.record(new AuditEntry(
                    AuditActions.ACCESS_DENIED,
                    REPORT_ENTITY,
                    row.id(),
                    Map.of("resource", "user_report", "reason", "SELF_REVIEW", "action", action)));
            throw new ForbiddenException(
                    "SELF_REVIEW_FORBIDDEN", "You cannot review a report about your own request or one you filed.");
        }
    }

    private List<ReportItem> items(List<ReportRow> rows) {
        Set<UUID> ids = new HashSet<>();
        for (ReportRow r : rows) {
            ids.add(r.reporterId());
            if (r.assignedTo() != null) {
                ids.add(r.assignedTo());
            }
        }
        Map<UUID, UserSummaryView> names = users.findSummaries(ids);
        return rows.stream().map(r -> item(r, names)).toList();
    }

    private static ReportItem item(ReportRow r, Map<UUID, UserSummaryView> names) {
        return new ReportItem(r, names.get(r.reporterId()), r.assignedTo() == null ? null : names.get(r.assignedTo()));
    }

    private ReportDetail detail(ReportRow row, KyroPrincipal actor) {
        List<ReportRow> others = repo.otherReports(row.requestId(), row.id());
        HoldRow hold = repo.activeHold(row.requestId()).orElse(null);
        Set<UUID> ids = new HashSet<>();
        ids.add(row.reporterId());
        if (row.assignedTo() != null) {
            ids.add(row.assignedTo());
        }
        if (row.resolvedBy() != null) {
            ids.add(row.resolvedBy());
        }
        if (hold != null) {
            ids.add(hold.placedBy());
        }
        for (ReportRow o : others) {
            ids.add(o.reporterId());
            if (o.assignedTo() != null) {
                ids.add(o.assignedTo());
            }
        }
        Map<UUID, UserSummaryView> names = users.findSummaries(ids);
        boolean canResolve = "IN_REVIEW".equals(row.status())
                && actor.userId().equals(row.assignedTo())
                && !actor.userId().equals(row.requestOwnerId())
                && !actor.userId().equals(row.reporterId());
        return new ReportDetail(
                item(row, names),
                row.resolvedBy() == null ? null : names.get(row.resolvedBy()),
                repo.latestCaseId(row.requestId()).orElse(null),
                hold == null ? null : new HoldView(hold, names.get(hold.placedBy())),
                others.stream().map(o -> item(o, names)).toList(),
                canResolve);
    }
}
