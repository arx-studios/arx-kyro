package com.kyro.platform.identity.domain;

import java.time.Instant;

/** One row of the append-only consent history. */
public record ConsentRow(String policyType, String policyVersion, Instant acceptedAt) {}
