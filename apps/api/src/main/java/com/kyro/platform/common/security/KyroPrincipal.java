package com.kyro.platform.common.security;

import java.io.Serializable;
import java.util.Set;
import java.util.UUID;

// FROZEN CONTRACT: change signatures only with a DECISIONS.md entry.
/** The authenticated principal stored in the session. Roles are loaded at login. */
public record KyroPrincipal(UUID userId, String email, Set<Role> roles) implements Serializable {

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }
}
