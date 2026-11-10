package com.kyro.platform.identity.application;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.common.errors.UnprocessableException;
import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.identity.domain.ConsentRow;
import com.kyro.platform.identity.domain.UserAccountRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consent history and re-acceptance of policy versions (append-only {@code consent_records}). */
@Service
public class ConsentService {

    /** {@code accepted*Version} is the latest accepted version, or null when never accepted. */
    public record Status(
            String currentTerms,
            String currentPrivacy,
            String acceptedTerms,
            String acceptedPrivacy,
            boolean reacceptRequired,
            List<ConsentRow> history) {}

    private final UserAccountRepository users;
    private final KyroProperties props;
    private final CurrentActor actor;
    private final AuditService audit;

    public ConsentService(UserAccountRepository users, KyroProperties props, CurrentActor actor, AuditService audit) {
        this.users = users;
        this.props = props;
        this.actor = actor;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Status status() {
        return statusOf(actor.require().userId());
    }

    @Transactional
    public Status accept(String termsVersion, String privacyVersion, String ip, String userAgent) {
        if (!props.policy().termsVersion().equals(termsVersion)
                || !props.policy().privacyVersion().equals(privacyVersion)) {
            throw new UnprocessableException(
                    "CONSENT_VERSION_MISMATCH", "Consent must reference the current terms and privacy versions.");
        }
        UUID userId = actor.require().userId();
        Status before = statusOf(userId);
        // Only versions not yet accepted are appended, so repeated clicks do not grow the history.
        boolean termsNeeded = !termsVersion.equals(before.acceptedTerms());
        boolean privacyNeeded = !privacyVersion.equals(before.acceptedPrivacy());
        if (termsNeeded) {
            users.insertConsent(userId, "TERMS", termsVersion, ip, userAgent);
        }
        if (privacyNeeded) {
            users.insertConsent(userId, "PRIVACY", privacyVersion, ip, userAgent);
        }
        if (termsNeeded || privacyNeeded) {
            audit.record(new AuditEntry(
                    AuditActions.CONSENT_ACCEPTED,
                    "USER",
                    userId,
                    Map.of("termsVersion", termsVersion, "privacyVersion", privacyVersion)));
        }
        return statusOf(userId);
    }

    private Status statusOf(UUID userId) {
        List<ConsentRow> history = users.consentHistory(userId);
        String terms = latest(history, "TERMS");
        String privacy = latest(history, "PRIVACY");
        boolean reaccept = !props.policy().termsVersion().equals(terms) || !props.policy().privacyVersion().equals(privacy);
        return new Status(props.policy().termsVersion(), props.policy().privacyVersion(), terms, privacy, reaccept, history);
    }

    private static String latest(List<ConsentRow> newestFirst, String type) {
        return newestFirst.stream()
                .filter(r -> r.policyType().equals(type))
                .map(ConsentRow::policyVersion)
                .findFirst()
                .orElse(null);
    }
}
