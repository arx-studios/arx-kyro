package com.kyro.platform.education.domain;

import java.util.UUID;

/** {@code kind} is DOCUMENT_HASH_MATCH or FEE_REFERENCE_MATCH. */
public record DuplicateMatch(String kind, UUID otherRequestId, RequestStatus otherStatus) {}
