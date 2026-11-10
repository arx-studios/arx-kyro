package com.kyro.platform.identity.domain;

import com.kyro.platform.common.security.Role;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface UserAccountRepository {

    Optional<UserAccount> findByNormalizedEmail(String emailNormalized);

    Optional<UserAccount> findById(UUID id);

    /** Inserts the user (PENDING_VERIFICATION) with roles and the profiles matching those roles. */
    UUID insertUser(
            String email,
            String emailNormalized,
            String passwordHash,
            String displayName,
            String countryCode,
            Set<Role> roles,
            String publicDisplayName);

    void insertConsent(UUID userId, String policyType, String policyVersion, String ipAddress, String userAgent);

    void insertVerificationToken(UUID userId, String tokenHash, Instant expiresAt);

    /** Atomically consumes a valid (unexpired, unconsumed) token; returns the user id, or empty if none. */
    Optional<UUID> consumeVerificationToken(String tokenHash, Instant now);

    void markEmailVerified(UUID userId, Instant now);

    void updateDisplayName(UUID userId, String displayName);

    void updateRecipientPublicName(UUID userId, String publicName);

    void updateDonorPublicName(UUID userId, String publicName);
}
