package com.kyro.platform.verification.application;

import com.kyro.platform.education.application.port.ReviewSnapshot;
import com.kyro.platform.education.application.port.ReviewSummary;
import com.kyro.platform.identity.application.ReviewRecipientView;
import com.kyro.platform.identity.application.UserSummaryView;
import java.util.List;

/** Read models returned by {@link VerificationCaseService}; mapped to the contract DTOs in the api package. */
public final class VerificationViews {

    private VerificationViews() {}

    /** {@code summary} and {@code assignee} may be null when the request or user can no longer be resolved. */
    public record CaseListItem(CaseRow row, ReviewSummary summary, UserSummaryView assignee) {}

    public record CasePage(List<CaseListItem> items, String nextCursor) {}

    public record DecisionView(DecisionRow row, UserSummaryView decidedBy) {}

    public record CaseDetail(
            CaseRow row,
            UserSummaryView assignee,
            ReviewSnapshot request,
            ReviewRecipientView recipient,
            List<DecisionView> decisions,
            boolean canDecide) {}

    /** Verifier input of a decision. {@code decision} is APPROVE, REJECT or REQUEST_CHANGES. */
    public record DecisionCommand(String decision, String rationale, String messageToRecipient) {}
}
