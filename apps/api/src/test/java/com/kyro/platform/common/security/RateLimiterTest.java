package com.kyro.platform.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.kyro.platform.common.config.KyroProperties;
import com.kyro.platform.common.security.RateLimitProperties.KeyType;
import com.kyro.platform.common.security.RateLimitProperties.Rule;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-10T10:00:10Z");

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static KyroProperties props() {
        return new KyroProperties(
                null, null, null, new KyroProperties.Auth(Duration.ofHours(1), 10, 5, 3), null, null, null, null);
    }

    private static RateLimiter limiter(MutableClock clock, boolean enabled, Rule... rules) {
        return new RateLimiter(clock, props(), new RateLimitProperties(enabled, List.of(rules)));
    }

    @Test
    void feedAllowsTheLimitThenReturnsRetryAfterWithinTheWindow() {
        MutableClock clock = new MutableClock();
        RateLimiter l = limiter(clock, true, new Rule("feed", "GET", "/api/v1/discovery/feed/**", 3, KeyType.IP));
        for (int i = 0; i < 3; i++) {
            assertThat(l.check("GET", "/api/v1/discovery/feed", "1.1.1.1", null).allowed()).isTrue();
        }
        RateLimiter.Decision d = l.check("GET", "/api/v1/discovery/feed/abc", "1.1.1.1", null);
        assertThat(d.allowed()).isFalse();
        assertThat(d.retryAfterSeconds()).isEqualTo(50); // 10s into the minute
        // another IP is independent, another method is not limited
        assertThat(l.check("GET", "/api/v1/discovery/feed", "2.2.2.2", null).allowed()).isTrue();
        assertThat(l.check("POST", "/api/v1/discovery/feed", "1.1.1.1", null).allowed()).isTrue();
        // next window resets
        clock.now = clock.now.plusSeconds(60);
        assertThat(l.check("GET", "/api/v1/discovery/feed", "1.1.1.1", null).allowed()).isTrue();
    }

    @Test
    void userKeyedRulesAreIndependentPerUserAndFallBackToIpWhenAnonymous() {
        RateLimiter l = limiter(new MutableClock(), true, new Rule("swipe", "POST", "/api/v1/discovery/requests/*/swipes", 1, KeyType.USER));
        String path = "/api/v1/discovery/requests/abc/swipes";
        assertThat(l.check("POST", path, "9.9.9.9", "user-a").allowed()).isTrue();
        assertThat(l.check("POST", path, "9.9.9.9", "user-a").allowed()).isFalse();
        assertThat(l.check("POST", path, "9.9.9.9", "user-b").allowed()).isTrue();
        assertThat(l.check("POST", path, "9.9.9.9", null).allowed()).isTrue();
        assertThat(l.check("POST", path, "9.9.9.9", null).allowed()).isFalse();
    }

    @Test
    void disablingConfiguredRulesKeepsTheBuiltInAuthLimits() {
        RateLimiter l = limiter(new MutableClock(), false, new Rule("feed", "GET", "/api/v1/discovery/feed/**", 1, KeyType.IP));
        for (int i = 0; i < 5; i++) {
            assertThat(l.check("GET", "/api/v1/discovery/feed", "1.1.1.1", null).allowed()).isTrue();
        }
        for (int i = 0; i < 3; i++) {
            assertThat(l.check("POST", "/api/v1/auth/verify-email/resend", "1.1.1.1", null).allowed()).isTrue();
        }
        assertThat(l.check("POST", "/api/v1/auth/verify-email/resend", "1.1.1.1", null).allowed()).isFalse();
    }
}
