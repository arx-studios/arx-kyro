package com.kyro.platform.education.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record FundingRequest(
        UUID id,
        UUID recipientUserId,
        RequestStatus status,
        String title,
        String story,
        String expenseType,
        String institutionName,
        String institutionCity,
        String institutionCountry,
        String courseName,
        String feeReference,
        long amountMinor,
        String currency,
        LocalDate dueDate,
        int submissionCount,
        Instant submittedAt,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt,
        long version,
        String publicDisplayName) {

    public RequestFields fields() {
        return new RequestFields(title, story, expenseType, institutionName, institutionCity, institutionCountry,
                courseName, feeReference, amountMinor, currency, dueDate);
    }
}
