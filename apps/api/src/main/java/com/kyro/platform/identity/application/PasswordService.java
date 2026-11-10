package com.kyro.platform.identity.application;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.errors.FieldError;
import com.kyro.platform.common.errors.UnprocessableException;
import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.identity.domain.UserAccount;
import com.kyro.platform.identity.domain.UserAccountRepository;
import com.kyro.platform.identity.domain.UserStatus;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Password reset (emailed single-use token) and authenticated password change. */
@Service
public class PasswordService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserAccountRepository users;
    private final PasswordEncoder encoder;
    private final AccountMailer mailer;
    private final AuditService audit;
    private final PasswordResetProperties props;
    private final CurrentActor actor;
    private final Clock clock;
    private final TransactionTemplate tx;

    public PasswordService(
            UserAccountRepository users,
            PasswordEncoder encoder,
            AccountMailer mailer,
            AuditService audit,
            PasswordResetProperties props,
            CurrentActor actor,
            Clock clock,
            TransactionTemplate tx) {
        this.users = users;
        this.encoder = encoder;
        this.mailer = mailer;
        this.audit = audit;
        this.props = props;
        this.actor = actor;
        this.clock = clock;
        this.tx = tx;
    }

    /** Always completes silently: whether the address is registered is never revealed. */
    public void requestReset(String email, String requestedIp) {
        Optional<UserAccount> found = users.findByNormalizedEmail(AuthService.normalizeEmail(email));
        if (found.isEmpty() || found.get().status() != UserStatus.ACTIVE) {
            return;
        }
        UserAccount user = found.get();
        String rawToken = newRawToken();
        tx.executeWithoutResult(status -> {
            users.insertPasswordResetToken(
                    user.id(), AuthService.sha256Hex(rawToken), Instant.now(clock).plus(props.tokenTtl()), requestedIp);
            audit.record(new AuditEntry(AuditActions.PASSWORD_RESET_REQUESTED, "USER", user.id(), Map.of()));
        });
        mailer.sendPasswordReset(user.email(), user.displayName(), rawToken);
    }

    /** Single-use: the token is consumed atomically; any failure rolls the whole change back. */
    public void confirmReset(String rawToken, String newPassword) {
        Instant now = Instant.now(clock);
        String hash = encoder.encode(newPassword);
        UserAccount user = tx.execute(status -> {
            UUID userId = users.consumePasswordResetToken(AuthService.sha256Hex(rawToken), now)
                    .orElseThrow(() -> new UnprocessableException("TOKEN_INVALID", "The reset token is invalid or has expired."));
            UserAccount account = users.findById(userId)
                    .filter(u -> u.status() == UserStatus.ACTIVE)
                    .orElseThrow(() -> new UnprocessableException("TOKEN_INVALID", "The reset token is invalid or has expired."));
            users.updatePasswordHash(userId, hash);
            users.consumeAllPasswordResetTokens(userId, now);
            audit.record(new AuditEntry(AuditActions.PASSWORD_RESET_COMPLETED, "USER", userId, Map.of()));
            return account;
        });
        mailer.sendPasswordChanged(user.email(), user.displayName());
    }

    /**
     * Changes the caller's password. Not transactional as a whole so the failure audit row survives the exception.
     *
     * @return the new credentials version (the API layer stores it in the caller's session so it stays signed in)
     */
    public long changePassword(String currentPassword, String newPassword) {
        UUID userId = actor.require().userId();
        UserAccount user = users.findById(userId).orElseThrow();
        if (!encoder.matches(currentPassword, user.passwordHash())) {
            audit.record(new AuditEntry(AuditActions.LOGIN_FAILED, "USER", userId, Map.of("reason", "CURRENT_PASSWORD_INCORRECT")));
            throw new UnprocessableException(
                    "CURRENT_PASSWORD_INCORRECT",
                    "The current password is incorrect.",
                    List.of(new FieldError("currentPassword", "is incorrect")));
        }
        String hash = encoder.encode(newPassword);
        Long version = tx.execute(status -> {
            long v = users.updatePasswordHash(userId, hash);
            audit.record(new AuditEntry(AuditActions.PASSWORD_CHANGED, "USER", userId, Map.of()));
            return v;
        });
        mailer.sendPasswordChanged(user.email(), user.displayName());
        return version == null ? 0L : version;
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
