package com.kyro.platform.education.application;

import com.kyro.platform.education.domain.FundingRequest;
import com.kyro.platform.education.domain.RequestDocument;
import com.kyro.platform.education.domain.RequestStatus;
import com.kyro.platform.education.domain.StatusHistoryItem;
import java.util.List;

/** A request with its non-deleted documents and status history (recipient-visible notes). */
public record RequestDetail(FundingRequest request, List<RequestDocument> documents, List<StatusHistoryItem> history) {

    public boolean editable() {
        return request.status().isEditable();
    }

    /** Latest reviewer message while the request sits in CHANGES_REQUESTED or REJECTED; otherwise null. */
    public String reviewerFeedback() {
        RequestStatus s = request.status();
        if (s != RequestStatus.CHANGES_REQUESTED && s != RequestStatus.REJECTED) {
            return null;
        }
        for (int i = history.size() - 1; i >= 0; i--) {
            StatusHistoryItem h = history.get(i);
            if (h.toStatus() == s && h.note() != null && !h.note().isBlank()) {
                return h.note();
            }
        }
        return null;
    }
}
