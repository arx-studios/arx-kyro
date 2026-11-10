package com.kyro.platform.education.application.port;

import java.time.Instant;
import java.util.UUID;

// FROZEN CONTRACT
/** Published inside the withdraw transaction; verification closes any open case with outcome WITHDRAWN. */
public record EducationRequestWithdrawn(UUID requestId, UUID recipientUserId, Instant withdrawnAt) {}
