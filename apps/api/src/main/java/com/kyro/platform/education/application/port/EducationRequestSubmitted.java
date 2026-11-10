package com.kyro.platform.education.application.port;

import java.time.Instant;
import java.util.UUID;

// FROZEN CONTRACT
/** Published inside the submit transaction; verification opens a case in a synchronous listener. */
public record EducationRequestSubmitted(UUID requestId, UUID recipientUserId, int submissionNumber, Instant submittedAt) {}
