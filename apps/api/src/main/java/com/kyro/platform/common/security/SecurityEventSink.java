package com.kyro.platform.common.security;

import jakarta.servlet.http.HttpServletRequest;

/** Implemented by audit: records security-relevant denials raised by filters and handlers. */
public interface SecurityEventSink {

    /** @param reason ROLE_MISSING or MFA_REQUIRED */
    void accessDenied(HttpServletRequest request, String reason);
}
