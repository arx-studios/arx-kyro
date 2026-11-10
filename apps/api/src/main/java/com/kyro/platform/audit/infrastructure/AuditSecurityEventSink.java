package com.kyro.platform.audit.infrastructure;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.security.SecurityEventSink;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Writes ACCESS_DENIED audit rows for route-level denials (the actor is taken from the security context). */
@Component
class AuditSecurityEventSink implements SecurityEventSink {

    private final AuditService audit;

    AuditSecurityEventSink(AuditService audit) {
        this.audit = audit;
    }

    @Override
    public void accessDenied(HttpServletRequest request, String reason) {
        String resource = request.getMethod() + " " + request.getRequestURI();
        audit.record(new AuditEntry(
                AuditActions.ACCESS_DENIED, "ENDPOINT", null, Map.of("resource", resource, "reason", reason)));
    }
}
