package com.kyro.platform.common.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code kyro.sessions.*}: how often an existing session is re-checked against the database. */
@ConfigurationProperties(prefix = "kyro.sessions")
public record SessionProperties(Duration revalidateInterval) {

    public SessionProperties {
        revalidateInterval = revalidateInterval == null ? Duration.ofSeconds(60) : revalidateInterval;
    }
}
