package com.kyro.platform.support;

import com.kyro.platform.common.security.KyroPrincipal;
import com.kyro.platform.common.security.Role;
import com.kyro.platform.common.security.SecretCipher;
import com.kyro.platform.common.security.SessionAttributes;
import com.kyro.platform.identity.application.mfa.Totp;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

// FROZEN CONTRACT (test support)
/**
 * Creates ACTIVE users directly in the database and hands back a ready-to-use logged-in {@link MockHttpSession}.
 * The session is built without calling the login endpoint, so it never touches the login rate limiter.
 */
@Component
public class TestUsers {

    public static final String PASSWORD = "CorrectHorse!2026";

    /** {@code session} carries the authenticated SecurityContext; pass it with {@code .session(user.session())}. */
    public record TestUser(UUID id, String email, String password, MockHttpSession session) {}

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final SecretCipher cipher;

    public TestUsers(JdbcTemplate jdbc, PasswordEncoder encoder, SecretCipher cipher) {
        this.cipher = cipher;
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    /** Staff (VERIFIER/ADMIN) sessions are MFA-verified by default so existing admin ITs run under enforcement. */
    public TestUser createActiveUser(Role... roles) {
        return create(true, roles);
    }

    /** Like {@link #createActiveUser} but the session has not passed MFA (use to test MFA_REQUIRED / enrollment). */
    public TestUser createActiveUserWithoutMfa(Role... roles) {
        return create(false, roles);
    }

    private TestUser create(boolean mfaVerified, Role... roles) {
        Set<Role> roleSet = roles.length == 0 ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(Arrays.asList(roles));
        String email = "user-" + UUID.randomUUID() + "@kyro.local";
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO users (email, email_normalized, password_hash, display_name, country_code, status, email_verified_at)
                VALUES (?, ?, ?, ?, 'IN', 'ACTIVE', now()) RETURNING id
                """,
                UUID.class,
                email,
                email,
                encoder.encode(PASSWORD),
                "Test User");
        for (Role r : roleSet) {
            jdbc.update("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", id, r.name());
        }
        if (roleSet.contains(Role.RECIPIENT)) {
            jdbc.update("INSERT INTO recipient_profiles (user_id, public_display_name) VALUES (?, ?)", id, "Test Recipient");
        }
        if (roleSet.contains(Role.DONOR)) {
            jdbc.update("INSERT INTO donor_profiles (user_id, public_display_name) VALUES (?, ?)", id, "Test Donor");
        }
        return new TestUser(id, email, PASSWORD, sessionFor(id, email, roleSet, mfaVerified));
    }

    /** A fresh logged-in session for an existing user (staff sessions are MFA-verified). */
    public static MockHttpSession sessionFor(UUID id, String email, Set<Role> roles) {
        return sessionFor(id, email, roles, true);
    }

    /** A fresh session for an existing user that has NOT passed MFA (e.g. what login produces). */
    public static MockHttpSession sessionWithoutMfa(UUID id, String email, Set<Role> roles) {
        return sessionFor(id, email, roles, false);
    }

    /** Stores a confirmed TOTP credential (encrypted) for the user and returns its base32 secret. */
    public String enrollTotp(UUID userId) {
        String secret = Totp.newSecret();
        enrollTotp(userId, secret);
        return secret;
    }

    public void enrollTotp(UUID userId, String base32Secret) {
        jdbc.update("DELETE FROM user_mfa_credentials WHERE user_id = ?", userId);
        jdbc.update(
                "INSERT INTO user_mfa_credentials (user_id, secret_ciphertext, key_id, confirmed_at) VALUES (?, ?, ?, now())",
                userId,
                cipher.encrypt(base32Secret),
                SecretCipher.KEY_ID);
    }

    /** The TOTP code valid right now for the secret. */
    public static String totpCode(String base32Secret) {
        return Totp.code(base32Secret, Totp.stepAt(Instant.now()));
    }

    /** The code for the step {@code stepsFromNow} away (negative = past); allowed-skew-steps is 1. */
    public static String totpCode(String base32Secret, int stepsFromNow) {
        return Totp.code(base32Secret, Totp.stepAt(Instant.now()) + stepsFromNow);
    }

    /**
     * A fresh logged-in session at credentials version 0. {@code mfaVerified} only matters for VERIFIER/ADMIN: when
     * true the session carries {@code MFA_VERIFIED_AT}.
     */
    public static MockHttpSession sessionFor(UUID id, String email, Set<Role> roles, boolean mfaVerified) {
        KyroPrincipal principal = new KyroPrincipal(id, email, roles);
        List<SimpleGrantedAuthority> authorities =
                roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        session.setAttribute(SessionAttributes.CREDENTIALS_VERSION, 0L);
        if (mfaVerified && (roles.contains(Role.VERIFIER) || roles.contains(Role.ADMIN))) {
            session.setAttribute(SessionAttributes.MFA_VERIFIED_AT, Instant.now());
        }
        return session;
    }
}
