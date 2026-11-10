package com.kyro.platform.common.security;

import java.util.Optional;

// FROZEN CONTRACT: change signatures only with a DECISIONS.md entry.
/** Access to the authenticated caller. {@link #require()} throws 401 UNAUTHENTICATED when anonymous. */
public interface CurrentActor {

    Optional<KyroPrincipal> current();

    KyroPrincipal require();
}
