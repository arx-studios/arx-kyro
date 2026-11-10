package com.kyro.platform.identity.api;

import com.kyro.platform.common.errors.ApiException;
import com.kyro.platform.common.security.KyroPrincipal;
import com.kyro.platform.contract.api.AuthApi;
import com.kyro.platform.contract.model.AcceptedMessage;
import com.kyro.platform.contract.model.CsrfToken;
import com.kyro.platform.contract.model.CurrentUser;
import com.kyro.platform.contract.model.LoginRequest;
import com.kyro.platform.contract.model.RegisterRequest;
import com.kyro.platform.contract.model.ResendVerificationRequest;
import com.kyro.platform.contract.model.VerifyEmailRequest;
import com.kyro.platform.identity.application.AuthService;
import com.kyro.platform.identity.application.RegisterCommand;
import com.kyro.platform.identity.application.mfa.MfaService;
import com.kyro.platform.identity.application.session.SessionCredentials;
import com.kyro.platform.common.security.SessionAttributes;
import java.time.Clock;
import java.time.Instant;
import com.kyro.platform.identity.domain.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.DeferredCsrfToken;
import org.springframework.web.bind.annotation.RestController;
import com.kyro.platform.common.security.CurrentActor;
import com.kyro.platform.common.security.Role;

@RestController
class AuthController implements AuthApi {

    private final AuthService auth;
    private final SecurityContextRepository contextRepository;
    private final CurrentActor actor;
    private final HttpServletRequest request;
    private final HttpServletResponse response;
    private final SessionCredentials credentials;
    private final MfaService mfa;
    private final Clock clock;

    AuthController(
            AuthService auth,
            SecurityContextRepository contextRepository,
            CurrentActor actor,
            HttpServletRequest request,
            HttpServletResponse response,
            SessionCredentials credentials,
            MfaService mfa,
            Clock clock) {
        this.credentials = credentials;
        this.mfa = mfa;
        this.clock = clock;
        this.auth = auth;
        this.contextRepository = contextRepository;
        this.actor = actor;
        this.request = request;
        this.response = response;
    }

    @Override
    public ResponseEntity<CsrfToken> getCsrfToken() {
        // The request attribute CsrfToken is BREACH-masked (XOR); the SPA handler expects the plain value (= cookie
        // value) in the X-XSRF-TOKEN header, so hand out the plain token from the deferred holder. Resolving it also
        // writes the XSRF-TOKEN cookie.
        DeferredCsrfToken deferred = (DeferredCsrfToken) request.getAttribute(DeferredCsrfToken.class.getName());
        if (deferred == null) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "CSRF protection is not active.", null);
        }
        org.springframework.security.web.csrf.CsrfToken token = deferred.get();
        return ResponseEntity.ok(new CsrfToken(token.getHeaderName(), token.getToken()));
    }

    @Override
    public ResponseEntity<AcceptedMessage> register(RegisterRequest body) {
        auth.register(new RegisterCommand(
                body.getEmail(),
                body.getPassword(),
                body.getDisplayName(),
                body.getPublicDisplayName(),
                body.getCountryCode(),
                body.getRoles().stream().map(r -> UserMapper.role(r.getValue())).collect(Collectors.toSet()),
                body.getConsents().getTermsVersion(),
                body.getConsents().getPrivacyVersion(),
                request.getRemoteAddr(),
                request.getHeader("User-Agent")));
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AcceptedMessage("If the details are valid, a verification email has been sent."));
    }

    @Override
    public ResponseEntity<Void> verifyEmail(VerifyEmailRequest body) {
        auth.verifyEmail(body.getToken());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<AcceptedMessage> resendVerificationEmail(ResendVerificationRequest body) {
        auth.resendVerification(body.getEmail());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(new AcceptedMessage("If the account exists and is unverified, a new verification email has been sent."));
    }

    @Override
    public ResponseEntity<CurrentUser> login(LoginRequest body) {
        UserAccount user = auth.authenticate(body.getEmail(), body.getPassword());

        // Session fixation protection: always move to a fresh session id.
        HttpSession existing = request.getSession(false);
        if (existing != null) {
            request.changeSessionId();
        }
        HttpSession session = request.getSession(true);
        session.removeAttribute(SessionAttributes.MFA_VERIFIED_AT); // a new login never inherits MFA state
        session.setAttribute(SessionAttributes.CREDENTIALS_VERSION, credentials.currentVersion(user.id()));
        session.setAttribute(SessionAttributes.LAST_REVALIDATED_AT, Instant.now(clock));

        KyroPrincipal principal = new KyroPrincipal(user.id(), user.email(), user.roles());
        var authorities = user.roles().stream()
                .map((Role r) -> new SimpleGrantedAuthority("ROLE_" + r.name()))
                .toList();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        SecurityContextHolder.setContext(context);
        contextRepository.saveContext(context, request, response);

        auth.recordLoginSucceeded(user.id());
        return ResponseEntity.ok(UserMapper.toCurrentUser(user, mfa.status(user.id(), user.roles(), false)));
    }

    @Override
    public ResponseEntity<Void> logout() {
        KyroPrincipal principal = actor.require();
        auth.recordLogout(principal.userId());
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
