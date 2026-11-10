package com.kyro.platform.identity.application;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.errors.ApiException;
import com.kyro.platform.common.errors.FieldError;
import com.kyro.platform.common.errors.UnprocessableException;
import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.common.security.KyroPrincipal;
import com.kyro.platform.identity.domain.UserAccount;
import com.kyro.platform.identity.domain.UserAccountRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserAccountRepository users;
    private final CurrentActor actor;
    private final AuditService audit;

    public AccountService(UserAccountRepository users, CurrentActor actor, AuditService audit) {
        this.users = users;
        this.actor = actor;
        this.audit = audit;
    }

    public UserAccount current() {
        KyroPrincipal p = actor.require();
        return users.findById(p.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.", null));
    }

    public UserAccount get(UUID id) {
        return users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.", null));
    }

    @Transactional
    public UserAccount updateProfile(String displayName, String recipientPublicName, String donorPublicName) {
        UserAccount user = current();
        List<FieldError> errors = new ArrayList<>();
        if (recipientPublicName != null && user.recipientPublicName() == null) {
            errors.add(new FieldError("recipientPublicDisplayName", "You do not have a recipient profile."));
        }
        if (donorPublicName != null && user.donorPublicName() == null) {
            errors.add(new FieldError("donorPublicDisplayName", "You do not have a donor profile."));
        }
        if (!errors.isEmpty()) {
            throw new UnprocessableException("VALIDATION_FAILED", "One or more fields are invalid.", errors);
        }
        if (displayName != null) {
            users.updateDisplayName(user.id(), displayName.trim());
        }
        if (recipientPublicName != null) {
            users.updateRecipientPublicName(user.id(), recipientPublicName.trim());
        }
        if (donorPublicName != null) {
            users.updateDonorPublicName(user.id(), donorPublicName.trim());
        }
        audit.record(new AuditEntry(
                AuditActions.PROFILE_UPDATED,
                "USER",
                user.id(),
                java.util.Map.of(
                        "displayName", displayName != null,
                        "recipientPublicDisplayName", recipientPublicName != null,
                        "donorPublicDisplayName", donorPublicName != null)));
        return get(user.id());
    }
}
