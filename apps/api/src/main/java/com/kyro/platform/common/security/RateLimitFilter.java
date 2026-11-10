package com.kyro.platform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Not a Spring bean on purpose: it is added to the security filter chain only (see SecurityConfig). The client IP is
 * {@code getRemoteAddr()}, which is the real client when {@code server.forward-headers-strategy=native} and the
 * request comes from a trusted proxy (Tomcat RemoteIpValve).
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;
    private final ProblemResponseWriter writer;

    public RateLimitFilter(RateLimiter limiter, ProblemResponseWriter writer) {
        this.limiter = limiter;
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userId = auth != null && auth.getPrincipal() instanceof KyroPrincipal p ? p.userId().toString() : null;
        RateLimiter.Decision decision =
                limiter.check(request.getMethod(), request.getRequestURI(), request.getRemoteAddr(), userId);
        if (!decision.allowed()) {
            response.setHeader("Retry-After", Integer.toString(decision.retryAfterSeconds()));
            writer.write(request, response, 429, "RATE_LIMITED", "Too many requests. Try again later.");
            return;
        }
        chain.doFilter(request, response);
    }
}
