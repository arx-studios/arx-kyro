package com.kyro.platform.identity.api;

import com.kyro.platform.common.security.SessionAttributes;
import com.kyro.platform.contract.api.MfaApi;
import com.kyro.platform.contract.model.CurrentUser;
import com.kyro.platform.contract.model.MfaRecoveryCodes;
import com.kyro.platform.contract.model.MfaVerifyRequest;
import com.kyro.platform.contract.model.TotpCodeInput;
import com.kyro.platform.contract.model.TotpEnrollment;
import com.kyro.platform.identity.application.AccountService;
import com.kyro.platform.identity.application.mfa.MfaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class MfaController implements MfaApi {

    private final MfaService mfa;
    private final AccountService accounts;
    private final HttpServletRequest request;
    private final Clock clock;

    MfaController(MfaService mfa, AccountService accounts, HttpServletRequest request, Clock clock) {
        this.mfa = mfa;
        this.accounts = accounts;
        this.request = request;
        this.clock = clock;
    }

    @Override
    public ResponseEntity<TotpEnrollment> startTotpEnrollment() {
        var e = mfa.startEnrollment();
        return ResponseEntity.ok(new TotpEnrollment(e.secret(), e.otpauthUri(), e.issuer(), e.accountName()));
    }

    @Override
    public ResponseEntity<MfaRecoveryCodes> confirmTotpEnrollment(TotpCodeInput body) {
        var codes = mfa.confirmEnrollment(body.getCode());
        markVerified();
        return ResponseEntity.ok(new MfaRecoveryCodes(codes));
    }

    @Override
    public ResponseEntity<CurrentUser> verifyMfa(MfaVerifyRequest body) {
        mfa.verify(body.getCode(), body.getRecoveryCode());
        markVerified();
        var user = accounts.current();
        return ResponseEntity.ok(UserMapper.toCurrentUser(user, mfa.status(user.id(), user.roles(), true)));
    }

    @Override
    public ResponseEntity<Void> resetUserMfa(UUID userId) {
        mfa.reset(userId);
        return ResponseEntity.noContent().build();
    }

    /** Marks the session MFA-verified and rotates its id. */
    private void markVerified() {
        HttpSession session = request.getSession(true);
        session.setAttribute(SessionAttributes.MFA_VERIFIED_AT, Instant.now(clock));
        request.changeSessionId();
    }
}
