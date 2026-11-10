package com.kyro.platform.identity.session;

import static org.assertj.core.api.Assertions.assertThat;

import com.kyro.platform.common.security.KyroPrincipal;
import com.kyro.platform.common.security.Role;
import com.kyro.platform.common.security.SessionAttributes;
import com.kyro.platform.common.security.SessionGuardFilter;
import com.kyro.platform.common.security.SessionProperties;
import com.kyro.platform.common.security.SessionValidator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class SessionGuardFilterTest {

    private static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-10T10:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class CountingValidator implements SessionValidator {
        int checks;
        int revoked;
        boolean valid = true;

        @Override
        public boolean stillValid(UUID userId, long credentialsVersionAtLogin) {
            checks++;
            return valid;
        }

        @Override
        public void sessionRevoked(UUID userId) {
            revoked++;
        }
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private static void authenticate(UUID id) {
        var principal = new KyroPrincipal(id, "u@kyro.local", Set.of(Role.DONOR));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, java.util.List.of()));
        SecurityContextHolder.setContext(context);
    }

    private static void call(SessionGuardFilter filter, MockHttpSession session, UUID id) throws Exception {
        authenticate(id);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/me");
        req.setSession(session);
        filter.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
    }

    @Test
    void revalidatesOnlyOncePerInterval() throws Exception {
        MutableClock clock = new MutableClock();
        CountingValidator validator = new CountingValidator();
        var filter = new SessionGuardFilter(validator, new SessionProperties(Duration.ofSeconds(60)), clock);
        UUID id = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();

        call(filter, session, id);
        assertThat(validator.checks).isEqualTo(1);
        clock.now = clock.now.plusSeconds(59);
        call(filter, session, id);
        assertThat(validator.checks).isEqualTo(1);
        clock.now = clock.now.plusSeconds(1);
        call(filter, session, id);
        assertThat(validator.checks).isEqualTo(2);
    }

    @Test
    void zeroIntervalChecksEveryRequest() throws Exception {
        CountingValidator validator = new CountingValidator();
        var filter = new SessionGuardFilter(validator, new SessionProperties(Duration.ZERO), new MutableClock());
        UUID id = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();
        call(filter, session, id);
        call(filter, session, id);
        assertThat(validator.checks).isEqualTo(2);
    }

    @Test
    void invalidSessionIsInvalidatedAndContextCleared() throws Exception {
        CountingValidator validator = new CountingValidator();
        validator.valid = false;
        var filter = new SessionGuardFilter(validator, new SessionProperties(Duration.ofSeconds(60)), new MutableClock());
        UUID id = UUID.randomUUID();
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAttributes.CREDENTIALS_VERSION, 3L);
        call(filter, session, id);
        assertThat(validator.revoked).isEqualTo(1);
        assertThat(session.isInvalid()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
