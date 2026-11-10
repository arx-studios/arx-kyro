package com.kyro.platform.education.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record RequestSummaryRow(
        UUID id,
        String title,
        RequestStatus status,
        long amountMinor,
        String currency,
        LocalDate dueDate,
        int documentCount,
        Instant updatedAt) {}
