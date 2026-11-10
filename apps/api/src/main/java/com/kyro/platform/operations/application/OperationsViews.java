package com.kyro.platform.operations.application;

import com.kyro.platform.identity.application.UserSummaryView;
import java.util.List;
import java.util.UUID;

/** Read models handed from the service to the api layer. */
public final class OperationsViews {

    private OperationsViews() {}

    public record ReportItem(ReportRow row, UserSummaryView reporter, UserSummaryView assignee) {}

    public record ReportPage(List<ReportItem> items, String nextCursor) {}

    public record HoldView(HoldRow row, UserSummaryView placedBy) {}

    public record ReportDetail(
            ReportItem item,
            UserSummaryView resolvedBy,
            UUID latestCaseId,
            HoldView hold,
            List<ReportItem> otherReports,
            boolean canResolve) {}

    public record ResolveCommand(String resolution, String note, boolean holdRequest) {}
}
