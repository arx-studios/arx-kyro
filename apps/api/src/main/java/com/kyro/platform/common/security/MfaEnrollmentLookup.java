package com.kyro.platform.common.security;

import java.util.UUID;

/** Implemented by identity: whether the user has a confirmed MFA credential. */
public interface MfaEnrollmentLookup {

    boolean isEnrolled(UUID userId);
}
