package com.kyro.platform.common.security;

import com.kyro.platform.common.errors.ApiException;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextCurrentActor implements CurrentActor {

    @Override
    public Optional<KyroPrincipal> current() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof KyroPrincipal p) {
            return Optional.of(p);
        }
        return Optional.empty();
    }

    @Override
    public KyroPrincipal require() {
        return current()
                .orElseThrow(() -> new ApiException(
                        HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication is required.", null));
    }
}
