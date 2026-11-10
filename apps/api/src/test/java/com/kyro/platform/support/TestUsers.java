package com.kyro.platform.support;

import com.kyro.platform.common.security.KyroPrincipal;
import com.kyro.platform.common.security.Role;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

// FROZEN CONTRACT (test support)
/**
 * Creates ACTIVE users directly in the database and hands back a ready-to-use logged-in {@link MockHttpSession}.
 * The session is built without calling the login endpoint, so it never touches the login rate limiter.
 */
@Component
public class TestUsers {

    public static final String PASSWORD = "CorrectHorse!2026";

    /** {@code session} carries the authenticated SecurityContext; pass it with {@code .session(user.session())}. */
    public record TestUser(UUID id, String email, String password, MockHttpSession session) {}

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public TestUsers(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    public TestUser createActiveUser(Role... roles) {
        Set<Role> roleSet = roles.length == 0 ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(Arrays.asList(roles));
        String email = "user-" + UUID.randomUUID() + "@kyro.local";
        UUID id = jdbc.queryForObject(
                """
                INSERT INTO users (email, email_normalized, password_hash, display_name, country_code, status, email_verified_at)
                VALUES (?, ?, ?, ?, 'IN', 'ACTIVE', now()) RETURNING id
                """,
                UUID.class,
                email,
                email,
                encoder.encode(PASSWORD),
                "Test User");
        for (Role r : roleSet) {
            jdbc.update("INSERT INTO user_roles (user_id, role_code) VALUES (?, ?)", id, r.name());
        }
        if (roleSet.contains(Role.RECIPIENT)) {
            jdbc.update("INSERT INTO recipient_profiles (user_id, public_display_name) VALUES (?, ?)", id, "Test Recipient");
        }
        if (roleSet.contains(Role.DONOR)) {
            jdbc.update("INSERT INTO donor_profiles (user_id, public_display_name) VALUES (?, ?)", id, "Test Donor");
        }
        return new TestUser(id, email, PASSWORD, sessionFor(id, email, roleSet));
    }

    /** A fresh logged-in session for an existing user. */
    public static MockHttpSession sessionFor(UUID id, String email, Set<Role> roles) {
        KyroPrincipal principal = new KyroPrincipal(id, email, roles);
        List<SimpleGrantedAuthority> authorities =
                roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r.name())).toList();
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);
        return session;
    }
}
