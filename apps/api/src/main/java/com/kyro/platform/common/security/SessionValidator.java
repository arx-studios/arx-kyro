package com.kyro.platform.common.security;

import java.util.UUID;

/** Implemented by identity: decides whether an existing session may keep being used. */
public interface SessionValidator {

    /** False when the user is suspended or gone, or the credentials version changed since login. */
    boolean stillValid(UUID userId, long credentialsVersionAtLogin);

    /** Called (with the session's security context still loaded) right before the session is invalidated. */
    void sessionRevoked(UUID userId);
}
