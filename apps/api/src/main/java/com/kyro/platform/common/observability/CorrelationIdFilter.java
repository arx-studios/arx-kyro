package com.kyro.platform.common.observability;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Accepts a valid inbound X-Correlation-Id (max 64 chars, [A-Za-z0-9-]) or generates one; echoes it on the response. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9-]{1,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(CorrelationContext.HEADER);
        String id = incoming != null && VALID.matcher(incoming).matches() ? incoming : UUID.randomUUID().toString();
        MDC.put(CorrelationContext.MDC_KEY, id);
        response.setHeader(CorrelationContext.HEADER, id);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(CorrelationContext.MDC_KEY);
        }
    }
}
