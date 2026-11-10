package com.kyro.platform.identity.api;

import com.kyro.platform.common.security.SessionAttributes;
import com.kyro.platform.contract.api.MeApi;
import com.kyro.platform.contract.model.CurrentUser;
import com.kyro.platform.contract.model.UpdateProfileRequest;
import com.kyro.platform.identity.application.AccountService;
import com.kyro.platform.identity.application.mfa.MfaService;
import com.kyro.platform.identity.domain.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MeController implements MeApi {

    private final AccountService accounts;
    private final MfaService mfa;
    private final HttpServletRequest request;

    MeController(AccountService accounts, MfaService mfa, HttpServletRequest request) {
        this.accounts = accounts;
        this.mfa = mfa;
        this.request = request;
    }

    @Override
    public ResponseEntity<CurrentUser> getCurrentUser() {
        return ResponseEntity.ok(withMfa(accounts.current()));
    }

    @Override
    public ResponseEntity<CurrentUser> updateMyProfile(UpdateProfileRequest body) {
        return ResponseEntity.ok(withMfa(accounts.updateProfile(
                body.getDisplayName(), body.getRecipientPublicDisplayName(), body.getDonorPublicDisplayName())));
    }

    private CurrentUser withMfa(UserAccount user) {
        HttpSession session = request.getSession(false);
        boolean verified = session != null && session.getAttribute(SessionAttributes.MFA_VERIFIED_AT) != null;
        return UserMapper.toCurrentUser(user, mfa.status(user.id(), user.roles(), verified));
    }
}
