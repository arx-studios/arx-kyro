package com.kyro.platform.education.application.port;

import java.time.Instant;

// FROZEN CONTRACT
public record ReviewStatusHistoryEntry(String fromStatus, String toStatus, Instant changedAt, String note) {}
