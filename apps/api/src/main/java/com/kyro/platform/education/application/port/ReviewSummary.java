package com.kyro.platform.education.application.port;

import java.util.UUID;

// FROZEN CONTRACT
public record ReviewSummary(
        UUID requestId,
        UUID recipientUserId,
        String title,
        String recipientPublicName,
        long amountMinor,
        String currency) {}
