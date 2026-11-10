package com.kyro.platform.common.security;

import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.common.security.RateLimitProperties.KeyType;
import com.kyro.platform.common.security.RateLimitProperties.Rule;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * In-memory fixed-window (one minute) limiter driven by rules: the three built-in auth rules (limits from
 * {@code kyro.auth.*}, always on) plus {@code kyro.rate-limits.rules} (switched off by {@code enabled=false}).
 * The bucket key is {@code ruleId|clientKey}; a USER rule falls back to the IP for anonymous callers.
 */
@Component
public class RateLimiter {

    /** @param retryAfterSeconds seconds left in the current window (meaningful when not allowed) */
    public record Decision(boolean allowed, int retryAfterSeconds) {
        static final Decision ALLOWED = new Decision(true, 0);
    }

    private record Window(long minute, int count) {}

    private record Compiled(String id, String method, PathPattern pattern, int limit, KeyType key) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final List<Compiled> authRules = new ArrayList<>();
    private final List<Compiled> configuredRules = new ArrayList<>();
    private final boolean configuredEnabled;

    public RateLimiter(Clock clock, KyroProperties props, RateLimitProperties limits) {
        this.clock = clock;
        PathPatternParser parser = PathPatternParser.defaultInstance;
        authRules.add(new Compiled("auth-login", "POST", parser.parse("/api/v1/auth/session"), props.auth().loginRatePerMinute(), KeyType.IP));
        authRules.add(new Compiled("auth-register", "POST", parser.parse("/api/v1/auth/register"), props.auth().registerRatePerMinute(), KeyType.IP));
        authRules.add(new Compiled(
                "auth-resend", "POST", parser.parse("/api/v1/auth/verify-email/resend"), props.auth().resendRatePerMinute(), KeyType.IP));
        for (Rule r : limits.rules()) {
            configuredRules.add(new Compiled(
                    r.id(), r.method() == null ? "*" : r.method().toUpperCase(), parser.parse(r.pattern()), r.perMinute(), r.key() == null ? KeyType.IP : r.key()));
        }
        this.configuredEnabled = limits.enabled();
    }

    /**
     * Records one hit against every matching rule and reports whether the request is allowed.
     *
     * @param userId the caller's user id, or null when anonymous
     */
    public Decision check(String method, String path, String clientIp, String userId) {
        PathContainer container = PathContainer.parsePath(path);
        Decision result = Decision.ALLOWED;
        for (Compiled rule : authRules) {
            result = evaluate(rule, method, container, clientIp, userId, result);
        }
        if (configuredEnabled) {
            for (Compiled rule : configuredRules) {
                result = evaluate(rule, method, container, clientIp, userId, result);
            }
        }
        return result;
    }

    private Decision evaluate(Compiled rule, String method, PathContainer path, String ip, String userId, Decision soFar) {
        if (!"*".equals(rule.method()) && !rule.method().equalsIgnoreCase(method)) {
            return soFar;
        }
        if (!rule.pattern().matches(path)) {
            return soFar;
        }
        String client = rule.key() == KeyType.USER && userId != null ? "u:" + userId : "ip:" + ip;
        long now = clock.millis();
        long minute = now / 60_000L;
        String key = rule.id() + "|" + client;
        Window w = windows.merge(
                key,
                new Window(minute, 1),
                (old, fresh) -> old.minute() == minute ? new Window(minute, old.count() + 1) : fresh);
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
        }
        if (w.count() <= rule.limit()) {
            return soFar;
        }
        int retry = (int) Math.max(1, 60 - (now / 1000L) % 60);
        return soFar.allowed() ? new Decision(false, retry) : new Decision(false, Math.max(soFar.retryAfterSeconds(), retry));
    }

    /** Test hook. */
    public void reset() {
        windows.clear();
    }
}
