package com.kyro.platform.common.storage;

import java.time.Instant;
import java.util.Map;

// FROZEN CONTRACT
public record PresignedUrl(String url, Map<String, String> requiredHeaders, Instant expiresAt) {}
