package com.kyro.platform.verification.application;

import java.time.Instant;
import java.util.UUID;

/** An immutable decision. {@code decision} is APPROVE, REJECT or REQUEST_CHANGES. */
public record DecisionRow(
        UUID id,
        UUID caseId,
        UUID decidedBy,
        String decision,
        String rationale,
        String messageToRecipient,
        Instant createdAt) {}
