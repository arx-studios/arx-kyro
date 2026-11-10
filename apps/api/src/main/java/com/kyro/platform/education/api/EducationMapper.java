package com.kyro.platform.education.api;

import com.kyro.platform.contract.model.DocumentContentType;
import com.kyro.platform.contract.model.DocumentStatus;
import com.kyro.platform.contract.model.DocumentType;
import com.kyro.platform.contract.model.EducationRequest;
import com.kyro.platform.contract.model.EducationRequestSummary;
import com.kyro.platform.contract.model.ExpenseType;
import com.kyro.platform.contract.model.Money;
import com.kyro.platform.contract.model.RequestStatus;
import com.kyro.platform.contract.model.StatusHistoryEntry;
import com.kyro.platform.education.application.RequestDetail;
import com.kyro.platform.education.domain.FundingRequest;
import com.kyro.platform.education.domain.RequestDocument;
import com.kyro.platform.education.domain.RequestSummaryRow;
import com.kyro.platform.education.domain.StatusHistoryItem;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class EducationMapper {

    private EducationMapper() {}

    static OffsetDateTime time(Instant i) {
        return i == null ? null : i.atOffset(ZoneOffset.UTC);
    }

    static Money money(long amountMinor, String currency) {
        return new Money().amountMinor(amountMinor).currency(currency);
    }

    static EducationRequest toContract(RequestDetail d) {
        FundingRequest r = d.request();
        EducationRequest out = new EducationRequest()
                .id(r.id())
                .title(r.title())
                .expenseType(ExpenseType.fromValue(r.expenseType()))
                .amount(money(r.amountMinor(), r.currency()))
                .story(r.story())
                .institutionName(r.institutionName())
                .institutionCity(r.institutionCity())
                .institutionCountry(r.institutionCountry())
                .courseName(r.courseName())
                .feeReference(r.feeReference())
                .dueDate(r.dueDate())
                .status(RequestStatus.fromValue(r.status().name()))
                .publicDisplayName(r.publicDisplayName())
                .editable(d.editable())
                .submissionCount(r.submissionCount())
                .reviewerFeedback(d.reviewerFeedback())
                .submittedAt(time(r.submittedAt()))
                .approvedAt(time(r.approvedAt()))
                .createdAt(time(r.createdAt()))
                .updatedAt(time(r.updatedAt()))
                .version(r.version());
        d.documents().forEach(doc -> out.addDocumentsItem(toContract(doc)));
        d.history().forEach(h -> out.addStatusHistoryItem(toContract(h)));
        return out;
    }

    static com.kyro.platform.contract.model.RequestDocument toContract(RequestDocument d) {
        return new com.kyro.platform.contract.model.RequestDocument()
                .id(d.id())
                .documentType(DocumentType.fromValue(d.documentType()))
                .originalFileName(d.originalFileName())
                .contentType(DocumentContentType.fromValue(d.contentType()))
                .sizeBytes(d.sizeBytes())
                .status(DocumentStatus.fromValue(d.status()))
                .createdAt(time(d.createdAt()))
                .uploadedAt(time(d.uploadedAt()));
    }

    static StatusHistoryEntry toContract(StatusHistoryItem h) {
        return new StatusHistoryEntry()
                .fromStatus(h.fromStatus() == null ? null : RequestStatus.fromValue(h.fromStatus().name()))
                .toStatus(RequestStatus.fromValue(h.toStatus().name()))
                .changedAt(time(h.changedAt()))
                .note(h.note());
    }

    static EducationRequestSummary toContract(RequestSummaryRow s) {
        return new EducationRequestSummary()
                .id(s.id())
                .title(s.title())
                .status(RequestStatus.fromValue(s.status().name()))
                .amount(money(s.amountMinor(), s.currency()))
                .dueDate(s.dueDate())
                .documentCount(s.documentCount())
                .updatedAt(time(s.updatedAt()));
    }
}
