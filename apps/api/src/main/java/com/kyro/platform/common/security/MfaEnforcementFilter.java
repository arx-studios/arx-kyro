package com.kyro.platform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * When {@code kyro.mfa.admin-enforced} is on, VERIFIER and ADMIN sessions without {@code MFA_VERIFIED_AT} cannot use
 * {@code /api/v1/admin/**}. Not a Spring bean: added to the security chain only.
 */
public class MfaEnforcementFilter extends OncePerRequestFilter {

    private static final String ADMIN_PREFIX = "/api/v1/admin";

    private final MfaProperties props;
    private final MfaEnrollmentLookup enrollment;
    private final SecurityEventSink events;
    private final ProblemResponseWriter writer;

    public MfaEnforcementFilter(
            MfaProperties props, MfaEnrollmentLookup enrollment, SecurityEventSink events, ProblemResponseWriter writer) {
        this.props = props;
        this.enrollment = enrollment;
        this.events = events;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (props.adminEnforced() && isAdminPath(request.getRequestURI())) {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null
                    && auth.getPrincipal() instanceof KyroPrincipal p
                    && (p.hasRole(Role.VERIFIER) || p.hasRole(Role.ADMIN))) {
                HttpSession session = request.getSession(false);
                boolean verified = session != null && session.getAttribute(SessionAttributes.MFA_VERIFIED_AT) != null;
                if (!verified) {
                    boolean enrolled = enrollment.isEnrolled(p.userId());
                    safeAudit(request);
                    if (enrolled) {
                        writer.write(request, response, 403, "MFA_REQUIRED", "Multi-factor verification is required.");
                    } else {
                        writer.write(
                                request, response, 403, "MFA_ENROLLMENT_REQUIRED", "Set up multi-factor authentication first.");
                    }
                    return;
                }
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean isAdminPath(String path) {
        return path.equals(ADMIN_PREFIX) || path.startsWith(ADMIN_PREFIX + "/");
    }

    private void safeAudit(HttpServletRequest request) {
        try {
            events.accessDenied(request, "MFA_REQUIRED");
        } catch (RuntimeException ignored) {
            // auditing must never change the response
        }
    }
}
