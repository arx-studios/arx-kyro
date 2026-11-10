package com.kyro.platform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;

/** Not a Spring bean on purpose: it is added to the security filter chain only (see SecurityConfig). */
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
        String path = request.getRequestURI();
        int limit = limiter.limitFor(request.getMethod(), path);
        if (limit >= 0 && !limiter.tryAcquire(request.getRemoteAddr(), path, limit)) {
            response.setHeader("Retry-After", "60");
            writer.write(request, response, 429, "RATE_LIMITED", "Too many requests. Try again later.");
            return;
        }
        chain.doFilter(request, response);
    }
}
