package com.kyro.platform.education.application.port;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

// FROZEN CONTRACT
/**
 * Private, staff-only view of a request. Enum-like values are the contract enum names as strings
 * (RequestStatus, ExpenseType, ...). Maps onto the OpenAPI {@code ReviewRequestView} (the recipient block is
 * added by verification via {@code UserDirectory.findRecipient(recipientUserId)}).
 */
public record ReviewSnapshot(
        UUID requestId,
        UUID recipientUserId,
        String status,
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
        String publicDisplayName,
        boolean editable,
        String reviewerFeedback,
        Instant submittedAt,
        Instant approvedAt,
        Instant createdAt,
        Instant updatedAt,
        long version,
        List<ReviewDocument> documents,
        List<ReviewStatusHistoryEntry> statusHistory,
        List<DuplicateIndicatorView> duplicateIndicators) {}
