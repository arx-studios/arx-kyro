package com.kyro.platform.identity.domain;

import com.kyro.platform.common.security.Role;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Read model of a user with roles and public profile names. {@code passwordHash} must never leave the module. */
public record UserAccount(
        UUID id,
        String email,
        String passwordHash,
        String displayName,
        String countryCode,
        UserStatus status,
        Instant emailVerifiedAt,
        Instant createdAt,
        Set<Role> roles,
        String recipientPublicName,
        String donorPublicName) {

    public boolean emailVerified() {
        return emailVerifiedAt != null;
    }
}
