package com.kyro.platform.identity.application;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code kyro.password-reset.*}: lifetime of password reset tokens. */
@ConfigurationProperties(prefix = "kyro.password-reset")
public record PasswordResetProperties(Duration tokenTtl) {

    public PasswordResetProperties {
        tokenTtl = tokenTtl == null ? Duration.ofMinutes(30) : tokenTtl;
    }
}
