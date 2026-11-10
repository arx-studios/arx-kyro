package com.kyro.platform.identity.application.mfa;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence for TOTP credentials and recovery codes (secrets arrive and leave encrypted). */
public interface MfaCredentialRepository {

    record Credential(String secretCiphertext, String keyId, Instant confirmedAt, Long lastUsedStep) {
        public boolean confirmed() {
            return confirmedAt != null;
        }
    }

    Optional<Credential> find(UUID userId);

    /** Inserts or replaces an unconfirmed credential. Returns false when a confirmed one exists. */
    boolean saveUnconfirmed(UUID userId, String secretCiphertext, String keyId);

    /** Marks the credential confirmed and records the used step; false when nothing was unconfirmed. */
    boolean confirm(UUID userId, long step);

    /** Advances {@code last_used_step} only if {@code step} is newer; false means replay. */
    boolean advanceStep(UUID userId, long step);

    void replaceRecoveryCodes(UUID userId, List<String> codeHashes);

    /** Single use: true only for the first successful consumption of an unused code. */
    boolean consumeRecoveryCode(UUID userId, String codeHash);

    /** Deletes the credential and recovery codes and bumps credentials_version. */
    void reset(UUID userId);

    boolean userExists(UUID userId);
}
