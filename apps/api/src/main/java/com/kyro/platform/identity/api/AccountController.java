package com.kyro.platform.identity.api;

import com.kyro.platform.common.security.SessionAttributes;
import com.kyro.platform.contract.api.AccountApi;
import com.kyro.platform.contract.model.AcceptedMessage;
import com.kyro.platform.contract.model.ChangePasswordRequest;
import com.kyro.platform.contract.model.PasswordResetConfirm;
import com.kyro.platform.contract.model.PasswordResetRequest;
import com.kyro.platform.identity.application.PasswordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

@RestController
class AccountController implements AccountApi {

    private final PasswordService passwords;
    private final HttpServletRequest request;

    AccountController(PasswordService passwords, HttpServletRequest request) {
        this.passwords = passwords;
        this.request = request;
    }

    @Override
    public ResponseEntity<AcceptedMessage> requestPasswordReset(PasswordResetRequest body) {
        passwords.requestReset(body.getEmail(), request.getRemoteAddr());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AcceptedMessage("If an account exists for that email, a password reset link has been sent."));
    }

    @Override
    public ResponseEntity<Void> confirmPasswordReset(PasswordResetConfirm body) {
        passwords.confirmReset(body.getToken(), body.getNewPassword());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<Void> changePassword(ChangePasswordRequest body) {
        long version = passwords.changePassword(body.getCurrentPassword(), body.getNewPassword());
        // Keep the caller signed in: adopt the new credentials version and rotate the session id. Every other
        // session of this user fails revalidation (credentials_version mismatch) and is signed out.
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.setAttribute(SessionAttributes.CREDENTIALS_VERSION, version);
            request.changeSessionId();
        }
        return ResponseEntity.noContent().build();
    }
}
