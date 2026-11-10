package com.kyro.platform.identity.application;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.common.errors.ApiException;
import com.kyro.platform.common.errors.ForbiddenException;
import com.kyro.platform.common.errors.UnprocessableException;
import com.kyro.platform.common.security.Role;
import com.kyro.platform.identity.domain.UserAccount;
import com.kyro.platform.identity.domain.UserAccountRepository;
import com.kyro.platform.identity.domain.UserStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Registration, email verification and credential checks. Session handling lives in the API layer. */
@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<Role> SELF_SERVICE_ROLES = Set.of(Role.RECIPIENT, Role.DONOR);

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final VerificationMailer mailer;
    private final AuditService audit;
    private final KyroProperties props;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final String dummyHash;

    public AuthService(
            UserAccountRepository users,
            PasswordEncoder encoder,
            VerificationMailer mailer,
            AuditService audit,
            KyroProperties props,
            Clock clock,
            TransactionTemplate tx) {
        this.users = users;
        this.encoder = encoder;
        this.mailer = mailer;
        this.audit = audit;
        this.props = props;
        this.clock = clock;
        this.tx = tx;
        this.dummyHash = encoder.encode("kyro-timing-equalizer");
    }

    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Always completes silently for well-formed input, whether or not the email exists. */
    public void register(RegisterCommand cmd) {
        if (!SELF_SERVICE_ROLES.containsAll(cmd.roles())) {
            throw new UnprocessableException("ROLE_NOT_ALLOWED", "Only RECIPIENT and DONOR roles can be self-registered.");
        }
        if (!props.policy().termsVersion().equals(cmd.termsVersion())
                || !props.policy().privacyVersion().equals(cmd.privacyVersion())) {
            throw new UnprocessableException(
                    "CONSENT_VERSION_MISMATCH", "Consent must reference the current terms and privacy versions.");
        }
        String normalized = normalizeEmail(cmd.email());
        String hash = encoder.encode(cmd.password()); // always hash: equalises timing between new and existing emails

        Optional<UserAccount> existing = users.findByNormalizedEmail(normalized);
        if (existing.isPresent()) {
            existingEmail(existing.get());
            return;
        }
        String rawToken = newRawToken();
        UUID userId;
        try {
            userId = tx.execute(status -> {
                UUID id = users.insertUser(
                        cmd.email().trim(), normalized, hash, cmd.displayName().trim(), cmd.countryCode(), cmd.roles(), cmd.publicDisplayName().trim());
                users.insertConsent(id, "TERMS", cmd.termsVersion(), cmd.ipAddress(), cmd.userAgent());
                users.insertConsent(id, "PRIVACY", cmd.privacyVersion(), cmd.ipAddress(), cmd.userAgent());
                users.insertVerificationToken(id, sha256Hex(rawToken), Instant.now(clock).plus(props.auth().verificationTokenTtl()));
                audit.record(new AuditEntry(
                        AuditActions.USER_REGISTERED,
                        "USER",
                        id,
                        Map.of("roles", cmd.roles().stream().map(Role::name).sorted().toList())));
                return id;
            });
        } catch (DuplicateKeyException race) {
            users.findByNormalizedEmail(normalized).ifPresent(this::existingEmail);
            return;
        }
        mailer.sendVerification(cmd.email().trim(), cmd.displayName().trim(), rawToken);
    }

    private void existingEmail(UserAccount user) {
        audit.record(new AuditEntry(AuditActions.REGISTRATION_ATTEMPT_EXISTING_EMAIL, "USER", user.id(), Map.of()));
        mailer.sendExistingAccountNotice(user.email());
    }

    public void verifyEmail(String rawToken) {
        Instant now = Instant.now(clock);
        tx.executeWithoutResult(status -> {
            UUID userId = users.consumeVerificationToken(sha256Hex(rawToken), now)
                    .orElseThrow(() -> new UnprocessableException("TOKEN_INVALID", "The verification token is invalid or has expired."));
            users.markEmailVerified(userId, now);
            audit.record(new AuditEntry(AuditActions.EMAIL_VERIFIED, "USER", userId, Map.of()));
        });
    }

    /** Always completes silently; sends a fresh token only to existing, unverified accounts. */
    public void resendVerification(String email) {
        Optional<UserAccount> user = users.findByNormalizedEmail(normalizeEmail(email));
        if (user.isEmpty() || user.get().status() != UserStatus.PENDING_VERIFICATION) {
            return;
        }
        String rawToken = newRawToken();
        users.insertVerificationToken(
                user.get().id(), sha256Hex(rawToken), Instant.now(clock).plus(props.auth().verificationTokenTtl()));
        mailer.sendVerification(user.get().email(), user.get().displayName(), rawToken);
    }

    /**
     * Verifies credentials. Deliberately not transactional so the LOGIN_FAILED audit row survives the thrown
     * exception. Returns the account (with roles) on success.
     */
    public UserAccount authenticate(String email, String password) {
        Optional<UserAccount> found = users.findByNormalizedEmail(normalizeEmail(email));
        boolean matches = encoder.matches(password, found.map(UserAccount::passwordHash).orElse(dummyHash));
        if (found.isEmpty() || !matches) {
            audit.record(new AuditEntry(
                    AuditActions.LOGIN_FAILED, "USER", found.map(UserAccount::id).orElse(null), Map.of("reason", "INVALID_CREDENTIALS")));
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "Email or password is incorrect.", null);
        }
        UserAccount user = found.get();
        if (user.status() == UserStatus.PENDING_VERIFICATION) {
            audit.record(new AuditEntry(AuditActions.LOGIN_FAILED, "USER", user.id(), Map.of("reason", "EMAIL_NOT_VERIFIED")));
            throw new ForbiddenException("EMAIL_NOT_VERIFIED", "Verify your email address before logging in.");
        }
        if (user.status() == UserStatus.SUSPENDED) {
            audit.record(new AuditEntry(AuditActions.LOGIN_FAILED, "USER", user.id(), Map.of("reason", "SUSPENDED")));
            throw new ForbiddenException("FORBIDDEN", "This account is suspended.");
        }
        return user;
    }

    /** Called by the API layer after the session has been established (so the actor is populated). */
    public void recordLoginSucceeded(UUID userId) {
        audit.record(new AuditEntry(AuditActions.LOGIN_SUCCEEDED, "USER", userId, Map.of()));
    }

    public void recordLogout(UUID userId) {
        audit.record(new AuditEntry(AuditActions.LOGOUT, "USER", userId, Map.of()));
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
