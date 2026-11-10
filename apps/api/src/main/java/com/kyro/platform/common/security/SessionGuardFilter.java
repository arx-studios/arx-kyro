package com.kyro.platform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Re-checks an existing session against the database at most once per {@code kyro.sessions.revalidate-interval}.
 * A suspended user or a changed credentials version invalidates the session; the request then proceeds anonymously
 * (protected endpoints answer 401). Not a Spring bean: added to the security chain only.
 */
public class SessionGuardFilter extends OncePerRequestFilter {

    private final SessionValidator validator;
    private final SessionProperties props;
    private final Clock clock;

    public SessionGuardFilter(SessionValidator validator, SessionProperties props, Clock clock) {
        this.validator = validator;
        this.props = props;
        this.clock = clock;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        HttpSession session = request.getSession(false);
        if (session != null && auth != null && auth.getPrincipal() instanceof KyroPrincipal principal) {
            Instant now = Instant.now(clock);
            Object last = session.getAttribute(SessionAttributes.LAST_REVALIDATED_AT);
            boolean due = !(last instanceof Instant l) || !now.isBefore(l.plus(props.revalidateInterval()));
            if (due) {
                Object v = session.getAttribute(SessionAttributes.CREDENTIALS_VERSION);
                long version = v instanceof Number n ? n.longValue() : 0L;
                if (validator.stillValid(principal.userId(), version)) {
                    session.setAttribute(SessionAttributes.LAST_REVALIDATED_AT, now);
                } else {
                    validator.sessionRevoked(principal.userId());
                    session.invalidate();
                    SecurityContextHolder.clearContext();
                }
            }
        }
        chain.doFilter(request, response);
    }
}
