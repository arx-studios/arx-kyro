package com.kyro.platform.education.domain;

import java.time.Instant;
import java.util.UUID;

public record RequestDocument(
        UUID id,
        UUID requestId,
        UUID uploadedBy,
        String documentType,
        String storageKey,
        String originalFileName,
        String contentType,
        long sizeBytes,
        String sha256,
        String status,
        Instant createdAt,
        Instant uploadedAt) {

    public static final String PENDING_UPLOAD = "PENDING_UPLOAD";
    public static final String UPLOADED = "UPLOADED";
    public static final String REJECTED = "REJECTED";
    public static final String DELETED = "DELETED";
}
