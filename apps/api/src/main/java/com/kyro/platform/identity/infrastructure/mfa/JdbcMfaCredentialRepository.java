package com.kyro.platform.identity.infrastructure.mfa;

import com.kyro.platform.common.security.MfaEnrollmentLookup;
import com.kyro.platform.identity.application.mfa.MfaCredentialRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcMfaCredentialRepository implements MfaCredentialRepository, MfaEnrollmentLookup {

    private final JdbcTemplate jdbc;

    JdbcMfaCredentialRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Credential> find(UUID userId) {
        return jdbc.query(
                        "SELECT secret_ciphertext, key_id, confirmed_at, last_used_step FROM user_mfa_credentials WHERE user_id = ?",
                        (rs, i) -> {
                            Timestamp confirmed = rs.getTimestamp("confirmed_at");
                            long step = rs.getLong("last_used_step");
                            return new Credential(
                                    rs.getString("secret_ciphertext"),
                                    rs.getString("key_id"),
                                    confirmed == null ? null : confirmed.toInstant(),
                                    rs.wasNull() ? null : step);
                        },
                        userId)
                .stream()
                .findFirst();
    }

    @Override
    public boolean saveUnconfirmed(UUID userId, String secretCiphertext, String keyId) {
        return jdbc.update(
                        """
                        INSERT INTO user_mfa_credentials (user_id, secret_ciphertext, key_id)
                        VALUES (?, ?, ?)
                        ON CONFLICT (user_id) DO UPDATE
                           SET secret_ciphertext = EXCLUDED.secret_ciphertext, key_id = EXCLUDED.key_id,
                               last_used_step = NULL, updated_at = now(), version = user_mfa_credentials.version + 1
                         WHERE user_mfa_credentials.confirmed_at IS NULL
                        """,
                        userId,
                        secretCiphertext,
                        keyId)
                > 0;
    }

    @Override
    public boolean confirm(UUID userId, long step) {
        return jdbc.update(
                        """
                        UPDATE user_mfa_credentials
                           SET confirmed_at = now(), last_used_step = ?, updated_at = now(), version = version + 1
                         WHERE user_id = ? AND confirmed_at IS NULL
                        """,
                        step,
                        userId)
                > 0;
    }

    @Override
    public boolean advanceStep(UUID userId, long step) {
        return jdbc.update(
                        """
                        UPDATE user_mfa_credentials
                           SET last_used_step = ?, updated_at = now(), version = version + 1
                         WHERE user_id = ? AND confirmed_at IS NOT NULL AND (last_used_step IS NULL OR last_used_step < ?)
                        """,
                        step,
                        userId,
                        step)
                > 0;
    }

    @Override
    public void replaceRecoveryCodes(UUID userId, List<String> codeHashes) {
        jdbc.update("DELETE FROM user_mfa_recovery_codes WHERE user_id = ?", userId);
        for (String hash : codeHashes) {
            jdbc.update("INSERT INTO user_mfa_recovery_codes (user_id, code_hash) VALUES (?, ?)", userId, hash);
        }
    }

    @Override
    public boolean consumeRecoveryCode(UUID userId, String codeHash) {
        return jdbc.update(
                        "UPDATE user_mfa_recovery_codes SET used_at = now() WHERE user_id = ? AND code_hash = ? AND used_at IS NULL",
                        userId,
                        codeHash)
                > 0;
    }

    @Override
    public void reset(UUID userId) {
        jdbc.update("DELETE FROM user_mfa_recovery_codes WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM user_mfa_credentials WHERE user_id = ?", userId);
        jdbc.update("UPDATE users SET credentials_version = credentials_version + 1, updated_at = now() WHERE id = ?", userId);
    }

    @Override
    public boolean userExists(UUID userId) {
        Integer n = jdbc.queryForObject("SELECT count(*) FROM users WHERE id = ?", Integer.class, userId);
        return n != null && n > 0;
    }

    @Override
    public boolean isEnrolled(UUID userId) {
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM user_mfa_credentials WHERE user_id = ? AND confirmed_at IS NOT NULL", Integer.class, userId);
        return n != null && n > 0;
    }
}
