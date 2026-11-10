package com.kyro.platform.education.application.port;

import java.time.Instant;
import java.util.UUID;

// FROZEN CONTRACT
/** Document metadata only; never a storage key or URL. */
public record ReviewDocument(
        UUID id,
        String documentType,
        String originalFileName,
        String contentType,
        long sizeBytes,
        String status,
        Instant createdAt,
        Instant uploadedAt) {}
