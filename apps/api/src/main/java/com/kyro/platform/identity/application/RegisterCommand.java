package com.kyro.platform.identity.application;

import com.kyro.platform.common.security.Role;
import java.util.Set;

public record RegisterCommand(
        String email,
        String password,
        String displayName,
        String publicDisplayName,
        String countryCode,
        Set<Role> roles,
        String termsVersion,
        String privacyVersion,
        String ipAddress,
        String userAgent) {}
