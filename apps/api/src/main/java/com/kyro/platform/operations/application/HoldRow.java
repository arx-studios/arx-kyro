package com.kyro.platform.operations.application;

import java.time.Instant;
import java.util.UUID;

public record HoldRow(UUID requestId, UUID reportId, String reason, UUID placedBy, Instant placedAt) {}
