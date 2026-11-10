package com.kyro.platform.devseed;

import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.common.security.SecretCipher;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates the fictional dev users (profile {@code local} only, and only when {@code kyro.seed.enabled}). Idempotent.
 * Runs at {@code @Order(0)}; other seeders (education demo requests) must use a higher order value.
 */
@Component
@Profile("local")
@Order(0)
class IdentityDevSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(IdentityDevSeeder.class);

    /** Base32 TOTP secret of the dev admin and verifier. Local seed only; allowlisted in gitleaks. */
    static final String DEV_TOTP_SECRET = "KYROLOCALDEVTOTPAAAAAAAAAAAAAAAA";

    private record SeedUser(String email, String displayName, List<String> roles, String recipientName, String donorName) {}

    private static final List<SeedUser> USERS = List.of(
            new SeedUser("admin@kyro.local", "Demo Admin", List.of("ADMIN", "VERIFIER"), null, null),
            new SeedUser("verifier@kyro.local", "Demo Verifier", List.of("VERIFIER"), null, null),
            new SeedUser("donor.demo@kyro.local", "Demo Donor", List.of("DONOR"), null, "Demo Donor"),
            new SeedUser("recipient.demo@kyro.local", "Demo Recipient", List.of("RECIPIENT"), "Demo Recipient", null));

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final KyroProperties props;
    private final SecretCipher cipher;

    IdentityDevSeeder(JdbcTemplate jdbc, PasswordEncoder encoder, KyroProperties props, SecretCipher cipher) {
        this.cipher = cipher;
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.seed().enabled()) {
            return;
        }
        String hash = encoder.encode(props.seed().password());
        for (SeedUser u : USERS) {
            Integer exists = jdbc.queryForObject("SELECT count(*) FROM users WHERE email_normalized = ?", Integer.class, u.email());
            if (exists != null && exists > 0) {
                seedMfa(u); // idempotent: also upgrades dev databases seeded before MFA existed
                seedConsents(u);
                continue;
            }
            UUID id = jdbc.queryForObject(
                    """
                    INSERT INTO users (email, email_normalized, password_hash, display_name, country_code, status, email_verified_at)
                    VALUES (?, ?, ?, ?, 'IN', 'ACTIVE', now()) RETURNING id
                    """,
                    UUID.class,
                    u.email(),
                    u.email(),
                    hash,
                    u.displayName());
            for (String role : u.roles()) {
                jdbc.update("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", id, role);
            }
            if (u.recipientName() != null) {
                jdbc.update("INSERT INTO recipient_profiles (user_id, public_display_name) VALUES (?, ?)", id, u.recipientName());
            }
            if (u.donorName() != null) {
                jdbc.update("INSERT INTO donor_profiles (user_id, public_display_name) VALUES (?, ?)", id, u.donorName());
            }
            log.info("Seeded dev user {} {}", u.email(), u.roles());
            seedMfa(u);
            seedConsents(u);
        }
    }

    /** Seeded users accept the current policies (otherwise the consent gate would block every dev login). Idempotent. */
    private void seedConsents(SeedUser u) {
        for (String[] c : new String[][] {
            {"TERMS", props.policy().termsVersion()}, {"PRIVACY", props.policy().privacyVersion()}
        }) {
            jdbc.update(
                    """
                    INSERT INTO consent_records (user_id, policy_type, policy_version)
                    SELECT id, ?, ? FROM users WHERE email_normalized = ?
                      AND NOT EXISTS (SELECT 1 FROM consent_records r WHERE r.user_id = users.id AND r.policy_type = ?)
                    """,
                    c[0], c[1], u.email(), c[0]);
        }
    }

    /** Staff dev users get a confirmed TOTP credential with the well-known dev secret (local profile only). */
    private void seedMfa(SeedUser u) {
        if (!u.roles().contains("ADMIN") && !u.roles().contains("VERIFIER")) {
            return;
        }
        jdbc.update(
                """
                INSERT INTO user_mfa_credentials (user_id, secret_ciphertext, key_id, confirmed_at)
                SELECT id, ?, ?, now() FROM users WHERE email_normalized = ?
                ON CONFLICT (user_id) DO NOTHING
                """,
                cipher.encrypt(DEV_TOTP_SECRET),
                SecretCipher.KEY_ID,
                u.email());
    }
}
