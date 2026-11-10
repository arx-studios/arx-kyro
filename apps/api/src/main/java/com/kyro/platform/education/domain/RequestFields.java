package com.kyro.platform.education.domain;

import java.time.LocalDate;

/** The user-editable columns of a funding request. */
public record RequestFields(
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
        LocalDate dueDate) {}
