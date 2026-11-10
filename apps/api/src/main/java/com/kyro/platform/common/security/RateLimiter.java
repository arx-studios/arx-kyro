package com.kyro.platform.common.security;

import com.kyro.platform.common.config.KyroProperties;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** In-memory fixed-window (one minute) limiter keyed by client IP and path. */
@Component
public class RateLimiter {

    private record Window(long minute, int count) {}

    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final Clock clock;
    private final KyroProperties props;

    public RateLimiter(Clock clock, KyroProperties props) {
        this.clock = clock;
        this.props = props;
    }

    /** Configured limit per minute for the path, or -1 when the path is not rate limited. */
    public int limitFor(String method, String path) {
        if (!"POST".equals(method)) {
            return -1;
        }
        return switch (path) {
            case "/api/v1/auth/session" -> props.auth().loginRatePerMinute();
            case "/api/v1/auth/register" -> props.auth().registerRatePerMinute();
            case "/api/v1/auth/verify-email/resend" -> props.auth().resendRatePerMinute();
            default -> -1;
        };
    }

    /** Records one hit; returns true when the request is allowed. */
    public boolean tryAcquire(String clientKey, String path, int limit) {
        long minute = clock.millis() / 60_000L;
        String key = clientKey + "|" + path;
        Window w = windows.merge(
                key,
                new Window(minute, 1),
                (old, fresh) -> old.minute() == minute ? new Window(minute, old.count() + 1) : fresh);
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute() < minute);
        }
        return w.count() <= limit;
    }

    /** Test hook. */
    public void reset() {
        windows.clear();
    }
}
