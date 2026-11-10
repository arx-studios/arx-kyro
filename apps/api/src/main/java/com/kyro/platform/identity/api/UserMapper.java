package com.kyro.platform.identity.api;

import com.kyro.platform.common.security.Role;
import com.kyro.platform.contract.model.CurrentUser;
import com.kyro.platform.contract.model.MfaStatus;
import com.kyro.platform.contract.model.ProfileSummary;
import com.kyro.platform.identity.application.mfa.MfaStatusView;
import com.kyro.platform.identity.domain.UserAccount;
import java.time.ZoneOffset;
import java.util.Comparator;

final class UserMapper {

    private UserMapper() {}

    static CurrentUser toCurrentUser(UserAccount u) {
        return toCurrentUser(u, null);
    }

    /** {@code mfa} is null for users without a staff role (the field is then omitted). */
    static CurrentUser toCurrentUser(UserAccount u, MfaStatusView mfa) {
        CurrentUser dto = new CurrentUser(
                u.id(),
                u.email(),
                u.displayName(),
                u.countryCode(),
                u.roles().stream()
                        .sorted(Comparator.naturalOrder())
                        .map(r -> com.kyro.platform.contract.model.Role.valueOf(r.name()))
                        .toList(),
                u.emailVerified(),
                u.createdAt().atOffset(ZoneOffset.UTC));
        if (u.recipientPublicName() != null) {
            dto.setRecipientProfile(new ProfileSummary(u.recipientPublicName()));
        }
        if (u.donorPublicName() != null) {
            dto.setDonorProfile(new ProfileSummary(u.donorPublicName()));
        }
        if (mfa != null) {
            dto.setMfa(new MfaStatus(mfa.required(), mfa.enrolled(), mfa.verified()));
        }
        return dto;
    }

    static Role role(String name) {
        return Role.valueOf(name);
    }
}
