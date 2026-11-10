package com.kyro.platform.common.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code kyro.rate-limits.*}: config-driven fixed one-minute window rules (D-013). */
@ConfigurationProperties(prefix = "kyro.rate-limits")
public record RateLimitProperties(Boolean enabled, List<Rule> rules) {

    public enum KeyType {
        IP,
        USER
    }

    /** {@code perMinute} is bound from a quoted YAML string (D-017). {@code method} may be {@code *}. */
    public record Rule(String id, String method, String pattern, int perMinute, KeyType key) {}

    public RateLimitProperties {
        enabled = enabled == null || enabled;
        rules = rules == null ? List.of() : List.copyOf(rules);
    }
}
