package com.kyro.platform.education.domain;

import java.time.Instant;

public record StatusHistoryItem(RequestStatus fromStatus, RequestStatus toStatus, Instant changedAt, String note) {}
