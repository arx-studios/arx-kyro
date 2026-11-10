package com.kyro.platform.identity.infrastructure;

import com.kyro.platform.common.security.Role;
import com.kyro.platform.identity.domain.ConsentRow;
import com.kyro.platform.identity.domain.UserAccount;
import com.kyro.platform.identity.domain.UserAccountRepository;
import com.kyro.platform.identity.domain.UserStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserAccountRepository implements UserAccountRepository {

    private static final String SELECT =
            """
            SELECT u.id, u.email, u.password_hash, u.display_name, u.country_code, u.status,
                   u.email_verified_at, u.created_at,
                   rp.public_display_name AS recipient_name, dp.public_display_name AS donor_name
            FROM users u
            LEFT JOIN recipient_profiles rp ON rp.user_id = u.id
            LEFT JOIN donor_profiles dp ON dp.user_id = u.id
            """;

    private final JdbcTemplate jdbc;

    JdbcUserAccountRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<UserAccount> findByNormalizedEmail(String emailNormalized) {
        return load(SELECT + " WHERE u.email_normalized = ?", emailNormalized);
    }

    @Override
    public Optional<UserAccount> findById(UUID id) {
        return load(SELECT + " WHERE u.id = ?", id);
    }

    private Optional<UserAccount> load(String sql, Object arg) {
        return jdbc.query(sql, (rs, i) -> {
                    UUID id = rs.getObject("id", UUID.class);
                    Timestamp verified = rs.getTimestamp("email_verified_at");
                    return new UserAccount(
                            id,
                            rs.getString("email"),
                            rs.getString("password_hash"),
                            rs.getString("display_name"),
                            rs.getString("country_code"),
                            UserStatus.valueOf(rs.getString("status")),
                            verified == null ? null : verified.toInstant(),
                            rs.getTimestamp("created_at").toInstant(),
                            rolesOf(id),
                            rs.getString("recipient_name"),
                            rs.getString("donor_name"));
                }, arg)
                .stream()
                .findFirst();
    }

    private Set<Role> rolesOf(UUID userId) {
        List<String> codes = jdbc.queryForList("SELECT role_code FROM user_roles WHERE user_id = ?", String.class, userId);
        Set<Role> roles = EnumSet.noneOf(Role.class);
        codes.forEach(c -> roles.add(Role.valueOf(c)));
        return roles;
    }

    @Override
    public UUID insertUser(
            String email,
            String emailNormalized,
            String passwordHash,
            String displayName,
            String countryCode,
            Set<Role> roles,
            String publicDisplayName) {
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO users (email, email_normalized, password_hash, display_name, country_code, status)
                VALUES (?, ?, ?, ?, ?, 'PENDING_VERIFICATION') RETURNING id
                """,
                UUID.class,
                email,
                emailNormalized,
                passwordHash,
                displayName,
                countryCode);
        for (Role role : roles) {
            jdbc.update("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", id, role.name());
        }
        if (roles.contains(Role.RECIPIENT)) {
            jdbc.update("INSERT INTO recipient_profiles (user_id, public_display_name) VALUES (?, ?)", id, publicDisplayName);
        }
        if (roles.contains(Role.DONOR)) {
            jdbc.update("INSERT INTO donor_profiles (user_id, public_display_name) VALUES (?, ?)", id, publicDisplayName);
        }
        return id;
    }

    @Override
    public void insertConsent(UUID userId, String policyType, String policyVersion, String ipAddress, String userAgent) {
        jdbc.update(
                "INSERT INTO consent_records (user_id, policy_type, policy_version, ip_address, user_agent) VALUES (?, ?, ?, ?, ?)",
                userId,
                policyType,
                policyVersion,
                truncate(ipAddress, 45),
                truncate(userAgent, 512));
    }

    @Override
    public void insertVerificationToken(UUID userId, String tokenHash, Instant expiresAt) {
        jdbc.update(
                "INSERT INTO email_verification_tokens (user_id, token_hash, expires_at) VALUES (?, ?, ?)",
                userId,
                tokenHash,
                Timestamp.from(expiresAt));
    }

    @Override
    public Optional<UUID> consumeVerificationToken(String tokenHash, Instant now) {
        return jdbc.query(
                        """
                        UPDATE email_verification_tokens SET consumed_at = ?
                        WHERE token_hash = ? AND consumed_at IS NULL AND expires_at > ?
                        RETURNING user_id
                        """,
                        (rs, i) -> rs.getObject("user_id", UUID.class),
                        Timestamp.from(now),
                        tokenHash,
                        Timestamp.from(now))
                .stream()
                .findFirst();
    }

    @Override
    public void markEmailVerified(UUID userId, Instant now) {
        jdbc.update(
                """
                UPDATE users SET email_verified_at = COALESCE(email_verified_at, ?),
                       status = CASE WHEN status = 'PENDING_VERIFICATION' THEN 'ACTIVE' ELSE status END,
                       updated_at = ?, version = version + 1
                WHERE id = ?
                """,
                Timestamp.from(now),
                Timestamp.from(now),
                userId);
    }

    @Override
    public void insertPasswordResetToken(UUID userId, String tokenHash, Instant expiresAt, String requestedIp) {
        jdbc.update(
                "INSERT INTO password_reset_tokens (user_id, token_hash, expires_at, requested_ip) VALUES (?, ?, ?, ?)",
                userId,
                tokenHash,
                Timestamp.from(expiresAt),
                truncate(requestedIp, 45));
    }

    @Override
    public Optional<UUID> consumePasswordResetToken(String tokenHash, Instant now) {
        return jdbc.query(
                        """
                        UPDATE password_reset_tokens SET consumed_at = ?
                        WHERE token_hash = ? AND consumed_at IS NULL AND expires_at > ?
                        RETURNING user_id
                        """,
                        (rs, i) -> rs.getObject("user_id", UUID.class),
                        Timestamp.from(now),
                        tokenHash,
                        Timestamp.from(now))
                .stream()
                .findFirst();
    }

    @Override
    public void consumeAllPasswordResetTokens(UUID userId, Instant now) {
        jdbc.update(
                "UPDATE password_reset_tokens SET consumed_at = ? WHERE user_id = ? AND consumed_at IS NULL",
                Timestamp.from(now),
                userId);
    }

    @Override
    public long updatePasswordHash(UUID userId, String passwordHash) {
        Long v = jdbc.queryForObject(
                """
                UPDATE users SET password_hash = ?, credentials_version = credentials_version + 1,
                       updated_at = now(), version = version + 1
                WHERE id = ? RETURNING credentials_version
                """,
                Long.class,
                passwordHash,
                userId);
        return v == null ? 0L : v;
    }

    @Override
    public long credentialsVersion(UUID userId) {
        Long v = jdbc.queryForObject("SELECT credentials_version FROM users WHERE id = ?", Long.class, userId);
        return v == null ? 0L : v;
    }

    @Override
    public List<ConsentRow> consentHistory(UUID userId) {
        return jdbc.query(
                "SELECT policy_type, policy_version, accepted_at FROM consent_records WHERE user_id = ?"
                        + " ORDER BY accepted_at DESC, policy_type",
                (rs, i) -> new ConsentRow(
                        rs.getString("policy_type"),
                        rs.getString("policy_version"),
                        rs.getTimestamp("accepted_at").toInstant()),
                userId);
    }

    @Override
    public void updateDisplayName(UUID userId, String displayName) {
        jdbc.update("UPDATE users SET display_name = ?, updated_at = now(), version = version + 1 WHERE id = ?", displayName, userId);
    }

    @Override
    public void updateRecipientPublicName(UUID userId, String publicName) {
        jdbc.update(
                "UPDATE recipient_profiles SET public_display_name = ?, updated_at = now(), version = version + 1 WHERE user_id = ?",
                publicName,
                userId);
    }

    @Override
    public void updateDonorPublicName(UUID userId, String publicName) {
        jdbc.update(
                "UPDATE donor_profiles SET public_display_name = ?, updated_at = now(), version = version + 1 WHERE user_id = ?",
                publicName,
                userId);
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
