package com.kyro.platform.identity.application;

import java.time.Instant;
import java.util.UUID;

// FROZEN CONTRACT
public record ReviewRecipientView(UUID id, String displayName, String email, boolean emailVerified, Instant memberSince) {}
