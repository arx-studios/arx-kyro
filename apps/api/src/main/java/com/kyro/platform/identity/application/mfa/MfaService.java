package com.kyro.platform.identity.application.mfa;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.errors.ConflictException;
import com.kyro.platform.common.errors.ForbiddenException;
import com.kyro.platform.common.errors.NotFoundException;
import com.kyro.platform.common.errors.UnprocessableException;
import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.common.security.KyroPrincipal;
import com.kyro.platform.common.security.MfaProperties;
import com.kyro.platform.common.security.Role;
import com.kyro.platform.common.security.SecretCipher;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Admin/verifier TOTP MFA. Secrets are encrypted at rest and never logged or audited. Failure audits are written
 * outside the transaction that fails, so they survive the thrown exception.
 */
@Service
public class MfaService {

    private static final String RECOVERY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** What the SPA needs to render the QR code; the secret is only ever shown here. */
    public record Enrollment(String secret, String otpauthUri, String issuer, String accountName) {
        @Override
        public String toString() {
            return "Enrollment[accountName=" + accountName + "]";
        }
    }

    private final MfaCredentialRepository credentials;
    private final SecretCipher cipher;
    private final MfaProperties props;
    private final CurrentActor actor;
    private final AuditService audit;
    private final Clock clock;
    private final TransactionTemplate tx;

    public MfaService(
            MfaCredentialRepository credentials,
            SecretCipher cipher,
            MfaProperties props,
            CurrentActor actor,
            AuditService audit,
            Clock clock,
            TransactionTemplate tx) {
        this.credentials = credentials;
        this.cipher = cipher;
        this.props = props;
        this.actor = actor;
        this.audit = audit;
        this.clock = clock;
        this.tx = tx;
    }

    public MfaStatusView status(UUID userId, Set<Role> roles, boolean sessionVerified) {
        if (!roles.contains(Role.VERIFIER) && !roles.contains(Role.ADMIN)) {
            return null;
        }
        boolean enrolled = credentials.find(userId).map(MfaCredentialRepository.Credential::confirmed).orElse(false);
        return new MfaStatusView(props.adminEnforced(), enrolled, sessionVerified);
    }

    public Enrollment startEnrollment() {
        KyroPrincipal me = actor.require();
        String secret = Totp.newSecret();
        boolean saved = credentials.saveUnconfirmed(me.userId(), cipher.encrypt(secret), SecretCipher.KEY_ID);
        if (!saved) {
            throw new ConflictException("MFA_ALREADY_ENROLLED", "Multi-factor authentication is already set up.");
        }
        audit.record(new AuditEntry(AuditActions.MFA_ENROLLMENT_STARTED, "USER", me.userId(), Map.of()));
        String issuer = props.issuer();
        String label = enc(issuer) + ":" + enc(me.email());
        String uri = "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + enc(issuer)
                + "&algorithm=SHA1&digits=6&period=" + Totp.STEP_SECONDS;
        return new Enrollment(secret, uri, issuer, me.email());
    }

    /** Confirms enrollment with a first valid code and returns the one-time recovery codes. */
    public List<String> confirmEnrollment(String code) {
        KyroPrincipal me = actor.require();
        var cred = credentials.find(me.userId());
        if (cred.isPresent() && cred.get().confirmed()) {
            throw new ConflictException("MFA_ALREADY_ENROLLED", "Multi-factor authentication is already set up.");
        }
        if (cred.isEmpty()) {
            fail(me.userId(), "TOTP_ENROLLMENT");
            throw new UnprocessableException("MFA_CODE_INVALID", "Start enrollment first, then enter a code.");
        }
        OptionalLong step = Totp.match(cipher.decrypt(cred.get().secretCiphertext()), code, Instant.now(clock), props.allowedSkewSteps(), -1);
        if (step.isEmpty()) {
            fail(me.userId(), "TOTP_ENROLLMENT");
            throw invalid();
        }
        List<String> codes = newRecoveryCodes();
        Boolean ok = tx.execute(status -> {
            if (!credentials.confirm(me.userId(), step.getAsLong())) {
                return false;
            }
            credentials.replaceRecoveryCodes(me.userId(), codes.stream().map(MfaService::hash).toList());
            audit.record(new AuditEntry(AuditActions.MFA_ENROLLED, "USER", me.userId(), Map.of()));
            return true;
        });
        if (!Boolean.TRUE.equals(ok)) {
            throw new ConflictException("MFA_ALREADY_ENROLLED", "Multi-factor authentication is already set up.");
        }
        return codes;
    }

    /** Verifies a TOTP code or a recovery code (exactly one). Throws 422 MFA_CODE_INVALID on failure. */
    public void verify(String code, String recoveryCode) {
        KyroPrincipal me = actor.require();
        if ((code == null) == (recoveryCode == null)) {
            throw new UnprocessableException("VALIDATION_FAILED", "Provide exactly one of code or recoveryCode.");
        }
        var cred = credentials.find(me.userId());
        if (cred.isEmpty() || !cred.get().confirmed()) {
            throw new ForbiddenException("MFA_ENROLLMENT_REQUIRED", "Set up multi-factor authentication first.");
        }
        if (code != null) {
            long last = cred.get().lastUsedStep() == null ? -1 : cred.get().lastUsedStep();
            OptionalLong step =
                    Totp.match(cipher.decrypt(cred.get().secretCiphertext()), code, Instant.now(clock), props.allowedSkewSteps(), last);
            if (step.isEmpty() || !credentials.advanceStep(me.userId(), step.getAsLong())) {
                fail(me.userId(), "TOTP");
                throw invalid();
            }
            audit.record(new AuditEntry(AuditActions.MFA_VERIFIED, "USER", me.userId(), Map.of("method", "TOTP")));
        } else {
            if (!credentials.consumeRecoveryCode(me.userId(), hash(recoveryCode.trim().toUpperCase()))) {
                fail(me.userId(), "RECOVERY_CODE");
                throw invalid();
            }
            audit.record(new AuditEntry(AuditActions.MFA_RECOVERY_CODE_USED, "USER", me.userId(), Map.of()));
            audit.record(new AuditEntry(AuditActions.MFA_VERIFIED, "USER", me.userId(), Map.of("method", "RECOVERY_CODE")));
        }
    }

    /** ADMIN only (route level), never on oneself. Kills the target's sessions through credentials_version. */
    public void reset(UUID targetUserId) {
        KyroPrincipal me = actor.require();
        if (me.userId().equals(targetUserId)) {
            throw new ForbiddenException("FORBIDDEN", "You cannot reset your own multi-factor authentication.");
        }
        if (!credentials.userExists(targetUserId)) {
            throw new NotFoundException();
        }
        tx.executeWithoutResult(status -> {
            credentials.reset(targetUserId);
            audit.record(new AuditEntry(AuditActions.MFA_RESET, "USER", targetUserId, Map.of()));
        });
    }

    private void fail(UUID userId, String method) {
        audit.record(new AuditEntry(AuditActions.MFA_FAILED, "USER", userId, Map.of("method", method)));
    }

    private static UnprocessableException invalid() {
        return new UnprocessableException("MFA_CODE_INVALID", "The code is not valid.");
    }

    private List<String> newRecoveryCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < props.recoveryCodeCount(); i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 10; j++) {
                if (j == 5) {
                    sb.append('-');
                }
                sb.append(RECOVERY_ALPHABET.charAt(RANDOM.nextInt(RECOVERY_ALPHABET.length())));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    static String hash(String recoveryCode) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(recoveryCode.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
