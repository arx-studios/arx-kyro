package com.kyro.platform.identity.application.session;

import java.util.UUID;

/** Reads {@code users.credentials_version}, which a session records at login (bumped on password change/reset, MFA reset). */
public interface SessionCredentials {

    long currentVersion(UUID userId);
}
