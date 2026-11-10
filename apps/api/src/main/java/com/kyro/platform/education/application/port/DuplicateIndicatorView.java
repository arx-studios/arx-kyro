package com.kyro.platform.education.application.port;

import java.util.UUID;

// FROZEN CONTRACT
/** {@code kind} is a contract DuplicateKind: DOCUMENT_HASH_MATCH or FEE_REFERENCE_MATCH. */
public record DuplicateIndicatorView(String kind, UUID otherRequestId, String otherRequestStatus, String detail) {}
