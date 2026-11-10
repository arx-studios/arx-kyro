package com.kyro.platform.education.application.port;

import java.time.Instant;
import java.util.UUID;

// FROZEN CONTRACT
/** Published after a status transition of an education request; the notification module e-mails the recipient. */
public record EducationRequestStatusChanged(
        UUID requestId, UUID recipientUserId, String title, String fromStatus, String toStatus, Instant occurredAt) {}
