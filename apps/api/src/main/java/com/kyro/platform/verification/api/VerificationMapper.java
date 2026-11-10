package com.kyro.platform.verification.api;

import com.kyro.platform.contract.model.CaseOutcome;
import com.kyro.platform.contract.model.CaseStatus;
import com.kyro.platform.contract.model.Decision;
import com.kyro.platform.contract.model.DocumentContentType;
import com.kyro.platform.contract.model.DocumentStatus;
import com.kyro.platform.contract.model.DocumentType;
import com.kyro.platform.contract.model.DuplicateIndicator;
import com.kyro.platform.contract.model.DuplicateKind;
import com.kyro.platform.contract.model.ExpenseType;
import com.kyro.platform.contract.model.Money;
import com.kyro.platform.contract.model.RequestDocument;
import com.kyro.platform.contract.model.RequestStatus;
import com.kyro.platform.contract.model.ReviewRecipient;
import com.kyro.platform.contract.model.ReviewRequestView;
import com.kyro.platform.contract.model.StatusHistoryEntry;
import com.kyro.platform.contract.model.UserSummary;
import com.kyro.platform.contract.model.VerificationCaseDetail;
import com.kyro.platform.contract.model.VerificationCasePage;
import com.kyro.platform.contract.model.VerificationCaseSummary;
import com.kyro.platform.contract.model.VerificationDecisionView;
import com.kyro.platform.education.application.port.ReviewDocument;
import com.kyro.platform.education.application.port.ReviewSnapshot;
import com.kyro.platform.identity.application.ReviewRecipientView;
import com.kyro.platform.identity.application.UserSummaryView;
import com.kyro.platform.verification.application.CaseRow;
import com.kyro.platform.verification.application.VerificationViews.CaseDetail;
import com.kyro.platform.verification.application.VerificationViews.CaseListItem;
import com.kyro.platform.verification.application.VerificationViews.CasePage;
import com.kyro.platform.verification.application.VerificationViews.DecisionView;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

final class VerificationMapper {

    private VerificationMapper() {}

    static VerificationCasePage toPage(CasePage page) {
        return new VerificationCasePage(page.items().stream().map(VerificationMapper::toSummary).toList(), page.nextCursor());
    }

    private static VerificationCaseSummary toSummary(CaseListItem item) {
        CaseRow row = item.row();
        var s = item.summary();
        VerificationCaseSummary dto = new VerificationCaseSummary(
                row.id(),
                row.requestId(),
                s == null ? "(request unavailable)" : s.title(),
                s == null ? "" : s.recipientPublicName(),
                s == null ? new Money(0L, "INR") : new Money(s.amountMinor(), s.currency()),
                CaseStatus.fromValue(row.status()),
                row.submissionNumber(),
                at(row.openedAt()));
        applyCommon(dto::setAssignedTo, dto::setClosedAt, dto::setOutcome, row, item.assignee());
        return dto;
    }

    static VerificationCaseDetail toDetail(CaseDetail d) {
        CaseRow row = d.row();
        ReviewSnapshot r = d.request();
        VerificationCaseDetail dto = new VerificationCaseDetail(
                row.id(),
                row.requestId(),
                r.title(),
                r.publicDisplayName(),
                new Money(r.amountMinor(), r.currency()),
                CaseStatus.fromValue(row.status()),
                row.submissionNumber(),
                at(row.openedAt()),
                toRequestView(r, d.recipient()),
                r.duplicateIndicators().stream()
                        .map(i -> new DuplicateIndicator(
                                DuplicateKind.fromValue(i.kind()),
                                i.otherRequestId(),
                                RequestStatus.fromValue(i.otherRequestStatus()),
                                i.detail()))
                        .toList(),
                d.decisions().stream().map(VerificationMapper::toDecision).toList(),
                d.canDecide());
        applyCommon(dto::setAssignedTo, dto::setClosedAt, dto::setOutcome, row, d.assignee());
        return dto;
    }

    private static void applyCommon(
            java.util.function.Consumer<UserSummary> assignedTo,
            java.util.function.Consumer<OffsetDateTime> closedAt,
            java.util.function.Consumer<CaseOutcome> outcome,
            CaseRow row,
            UserSummaryView assignee) {
        if (row.assignedTo() != null) {
            assignedTo.accept(
                    assignee != null
                            ? new UserSummary(assignee.id(), assignee.displayName())
                            : new UserSummary(row.assignedTo(), "Unknown verifier"));
        }
        if (row.closedAt() != null) {
            closedAt.accept(at(row.closedAt()));
        }
        if (row.outcome() != null) {
            outcome.accept(CaseOutcome.fromValue(row.outcome()));
        }
    }

    private static VerificationDecisionView toDecision(DecisionView v) {
        var d = v.row();
        UUID by = d.decidedBy();
        VerificationDecisionView dto = new VerificationDecisionView(
                d.id(),
                Decision.fromValue(d.decision()),
                d.rationale(),
                v.decidedBy() != null
                        ? new UserSummary(v.decidedBy().id(), v.decidedBy().displayName())
                        : new UserSummary(by, "Unknown verifier"),
                at(d.createdAt()));
        dto.setMessageToRecipient(d.messageToRecipient());
        return dto;
    }

    private static ReviewRequestView toRequestView(ReviewSnapshot r, ReviewRecipientView recipient) {
        ReviewRecipient rec = recipient == null
                ? new ReviewRecipient(r.recipientUserId(), r.publicDisplayName(), "", false, at(r.createdAt()))
                : new ReviewRecipient(
                        recipient.id(), recipient.displayName(), recipient.email(), recipient.emailVerified(), at(recipient.memberSince()));
        ReviewRequestView dto = new ReviewRequestView(
                r.requestId(),
                r.title(),
                ExpenseType.fromValue(r.expenseType()),
                new Money(r.amountMinor(), r.currency()),
                RequestStatus.fromValue(r.status()),
                r.publicDisplayName(),
                r.editable(),
                r.submissionCount(),
                r.documents().stream().map(VerificationMapper::toDocument).toList(),
                r.statusHistory().stream().map(VerificationMapper::toHistory).toList(),
                at(r.createdAt()),
                at(r.updatedAt()),
                r.version(),
                rec);
        dto.setStory(r.story());
        dto.setInstitutionName(r.institutionName());
        dto.setInstitutionCity(r.institutionCity());
        dto.setInstitutionCountry(r.institutionCountry());
        dto.setCourseName(r.courseName());
        dto.setFeeReference(r.feeReference());
        dto.setDueDate(r.dueDate());
        dto.setReviewerFeedback(r.reviewerFeedback());
        dto.setSubmittedAt(r.submittedAt() == null ? null : at(r.submittedAt()));
        dto.setApprovedAt(r.approvedAt() == null ? null : at(r.approvedAt()));
        return dto;
    }

    private static RequestDocument toDocument(ReviewDocument d) {
        RequestDocument dto = new RequestDocument(
                d.id(),
                DocumentType.fromValue(d.documentType()),
                d.originalFileName(),
                DocumentContentType.fromValue(d.contentType()),
                d.sizeBytes(),
                DocumentStatus.fromValue(d.status()),
                at(d.createdAt()));
        dto.setUploadedAt(d.uploadedAt() == null ? null : at(d.uploadedAt()));
        return dto;
    }

    private static StatusHistoryEntry toHistory(com.kyro.platform.education.application.port.ReviewStatusHistoryEntry h) {
        StatusHistoryEntry dto = new StatusHistoryEntry(RequestStatus.fromValue(h.toStatus()), at(h.changedAt()));
        if (h.fromStatus() != null) {
            dto.setFromStatus(RequestStatus.fromValue(h.fromStatus()));
        }
        dto.setNote(h.note());
        return dto;
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
