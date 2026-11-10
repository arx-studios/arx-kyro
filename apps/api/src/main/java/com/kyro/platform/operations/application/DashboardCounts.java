package com.kyro.platform.operations.application;

import java.time.Instant;

public record DashboardCounts(
        long unassignedCases, long myOpenCases, long openReports, long unassignedReports, long activeHolds, Instant generatedAt) {}
