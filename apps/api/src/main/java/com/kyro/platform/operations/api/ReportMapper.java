package com.kyro.platform.operations.api;

import com.kyro.platform.contract.model.AdminDashboard;
import com.kyro.platform.contract.model.ReportDetail;
import com.kyro.platform.contract.model.ReportPage;
import com.kyro.platform.contract.model.ReportReason;
import com.kyro.platform.contract.model.ReportResolution;
import com.kyro.platform.contract.model.ReportStatus;
import com.kyro.platform.contract.model.ReportSummary;
import com.kyro.platform.contract.model.RequestHold;
import com.kyro.platform.contract.model.RequestStatus;
import com.kyro.platform.contract.model.UserSummary;
import com.kyro.platform.identity.application.UserSummaryView;
import com.kyro.platform.operations.application.DashboardCounts;
import com.kyro.platform.operations.application.OperationsViews;
import com.kyro.platform.operations.application.OperationsViews.ReportItem;
import com.kyro.platform.operations.application.ReportRow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

final class ReportMapper {

    private ReportMapper() {}

    static ReportPage toPage(OperationsViews.ReportPage page) {
        return new ReportPage(page.items().stream().map(ReportMapper::toSummary).toList(), page.nextCursor());
    }

    static ReportSummary toSummary(ReportItem item) {
        ReportRow r = item.row();
        ReportSummary dto = new ReportSummary(
                r.id(),
                r.requestId(),
                r.requestTitle(),
                ReportReason.fromValue(r.reason()),
                ReportStatus.fromValue(r.status()),
                user(item.reporter(), r.reporterId(), "Unknown user"),
                at(r.createdAt()),
                r.onHold());
        if (r.assignedTo() != null) {
            dto.setAssignedTo(user(item.assignee(), r.assignedTo(), "Unknown reviewer"));
        }
        if (r.resolvedAt() != null) {
            dto.setResolvedAt(at(r.resolvedAt()));
        }
        if (r.resolution() != null) {
            dto.setResolution(ReportResolution.fromValue(r.resolution()));
        }
        return dto;
    }

    static ReportDetail toDetail(OperationsViews.ReportDetail d) {
        ReportRow r = d.item().row();
        ReportDetail dto = new ReportDetail(
                r.id(),
                r.requestId(),
                r.requestTitle(),
                ReportReason.fromValue(r.reason()),
                ReportStatus.fromValue(r.status()),
                user(d.item().reporter(), r.reporterId(), "Unknown user"),
                at(r.createdAt()),
                r.onHold(),
                RequestStatus.fromValue(r.requestStatus()),
                d.otherReports().stream().map(ReportMapper::toSummary).toList(),
                d.canResolve());
        if (r.assignedTo() != null) {
            dto.setAssignedTo(user(d.item().assignee(), r.assignedTo(), "Unknown reviewer"));
        }
        if (r.resolvedAt() != null) {
            dto.setResolvedAt(at(r.resolvedAt()));
        }
        if (r.resolution() != null) {
            dto.setResolution(ReportResolution.fromValue(r.resolution()));
        }
        dto.setDetails(r.details());
        dto.setResolutionNote(r.resolutionNote());
        if (r.resolvedBy() != null) {
            dto.setResolvedBy(user(d.resolvedBy(), r.resolvedBy(), "Unknown reviewer"));
        }
        dto.setLatestCaseId(d.latestCaseId());
        if (d.hold() != null) {
            RequestHold hold = new RequestHold(
                    d.hold().row().requestId(),
                    d.hold().row().reason(),
                    user(d.hold().placedBy(), d.hold().row().placedBy(), "Unknown reviewer"),
                    at(d.hold().row().placedAt()));
            hold.setReportId(d.hold().row().reportId());
            dto.setHold(hold);
        }
        return dto;
    }

    static AdminDashboard toDashboard(DashboardCounts c) {
        return new AdminDashboard(
                c.unassignedCases(), c.myOpenCases(), c.openReports(), c.unassignedReports(), c.activeHolds(), at(c.generatedAt()));
    }

    private static UserSummary user(UserSummaryView view, UUID fallbackId, String fallbackName) {
        return view != null ? new UserSummary(view.id(), view.displayName()) : new UserSummary(fallbackId, fallbackName);
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
