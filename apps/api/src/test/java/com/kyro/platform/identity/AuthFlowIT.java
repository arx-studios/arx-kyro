package com.kyro.platform.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyro.platform.common.security.Role;
import com.kyro.platform.support.Csrf;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestMailConfig.CapturingMailer;
import com.kyro.platform.support.TestUsers;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MvcResult;

class AuthFlowIT extends IntegrationTestBase {

    private static final String PASSWORD = "CorrectHorse!2026";

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    TestUsers testUsers;

    @Autowired
    CapturingMailer mailer;

    private static String registerBody(String email, String roles, String terms, String privacy) {
        return """
                {"email":"%s","password":"%s","displayName":"Asha Verma","publicDisplayName":"Asha V.",
                 "countryCode":"IN","roles":%s,"consents":{"termsVersion":"%s","privacyVersion":"%s"}}
                """.formatted(email, PASSWORD, roles, terms, privacy);
    }

    private void register(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(email, "[\"RECIPIENT\"]", "2026-11-01", "2026-11-01")))
                .andExpect(status().isAccepted());
    }

    private static String uniqueEmail() {
        return "new-" + UUID.randomUUID() + "@kyro.local";
    }

    @Test
    void registerAlwaysReturns202EvenForExistingEmail() throws Exception {
        String email = uniqueEmail();
        register(email);
        assertThat(mailer.countFor(email, "VERIFICATION")).isEqualTo(1);
        // same email again: still 202, but an "existing account" notice is sent and no second user is created
        register(email);
        assertThat(mailer.countFor(email, "EXISTING_ACCOUNT")).isEqualTo(1);
        Integer users = jdbc.queryForObject("SELECT count(*) FROM users WHERE email_normalized = ?", Integer.class, email);
        assertThat(users).isEqualTo(1);
        Integer consents = jdbc.queryForObject(
                "SELECT count(*) FROM consent_records c JOIN users u ON u.id = c.user_id WHERE u.email_normalized = ?",
                Integer.class,
                email);
        assertThat(consents).isEqualTo(2);
        String status = jdbc.queryForObject("SELECT status FROM users WHERE email_normalized = ?", String.class, email);
        assertThat(status).isEqualTo("PENDING_VERIFICATION");
    }

    @Test
    void registerRejectsVerifierAndAdminRoles() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), "[\"VERIFIER\"]", "2026-11-01", "2026-11-01")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), "[\"DONOR\",\"ADMIN\"]", "2026-11-01", "2026-11-01")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ROLE_NOT_ALLOWED"));
    }

    @Test
    void registerRejectsStaleConsentVersions() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(uniqueEmail(), "[\"DONOR\"]", "1999-01-01", "2026-11-01")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_VERSION_MISMATCH"));
    }

    @Test
    void registerValidationFailureIs422WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("not-an-email", "[\"DONOR\"]", "2026-11-01", "2026-11-01")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void unparseableJsonIs400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/session")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void loginBeforeVerificationIs403ThenVerifyThenLogin() throws Exception {
        String email = uniqueEmail();
        register(email);

        mockMvc.perform(post("/api/v1/auth/session")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"));

        String token = mailer.lastTokenFor(email).orElseThrow();
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(token)))
                .andExpect(status().isNoContent());

        // single use
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(token)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        MockHttpSession session = new MockHttpSession();
        String oldId = session.getId();
        mockMvc.perform(post("/api/v1/auth/session")
                        .session(session)
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.emailVerified").value(true))
                .andExpect(jsonPath("$.roles[0]").value("RECIPIENT"))
                .andExpect(jsonPath("$.recipientProfile.publicDisplayName").value("Asha V."));
        // session id rotated (fixation protection) and context stored
        assertThat(session.getId()).isNotEqualTo(oldId);

        mockMvc.perform(get("/api/v1/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));

        Integer succeeded = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = 'LOGIN_SUCCEEDED' AND actor_user_id = (SELECT id FROM users WHERE email_normalized = ?)",
                Integer.class,
                email);
        assertThat(succeeded).isEqualTo(1);
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        String email = uniqueEmail();
        register(email);
        jdbc.update(
                "UPDATE email_verification_tokens SET expires_at = ? WHERE user_id = (SELECT id FROM users WHERE email_normalized = ?)",
                Timestamp.from(Instant.now().minusSeconds(60)),
                email);
        mockMvc.perform(post("/api/v1/auth/verify-email")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(mailer.lastTokenFor(email).orElseThrow())))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void resendOnlySendsToUnverifiedAccountsButAlwaysReturns202() throws Exception {
        String email = uniqueEmail();
        register(email);
        mockMvc.perform(post("/api/v1/auth/verify-email/resend")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isAccepted());
        assertThat(mailer.countFor(email, "VERIFICATION")).isEqualTo(2);
        mockMvc.perform(post("/api/v1/auth/verify-email/resend")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-%s@kyro.local\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isAccepted());
    }

    @Test
    void wrongPasswordIs401AndWritesLoginFailedAudit() throws Exception {
        var user = testUsers.createActiveUser(Role.DONOR);
        mockMvc.perform(post("/api/v1/auth/session")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"WrongPassword!2026\"}".formatted(user.email())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        Integer failed = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = 'LOGIN_FAILED' AND entity_id = ?", Integer.class, user.id());
        assertThat(failed).isEqualTo(1);
    }

    @Test
    void postWithoutCsrfIs403() throws Exception {
        mockMvc.perform(post("/api/v1/auth/session")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@kyro.local\",\"password\":\"whatever-pass-1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        var user = testUsers.createActiveUser(Role.RECIPIENT);
        mockMvc.perform(patch("/api/v1/me/profile")
                        .session(user.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Changed Name\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void csrfEndpointIssuesTokenAndCookie() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
        String cookieValue = result.getResponse().getCookie("XSRF-TOKEN").getValue();
        assertThat(cookieValue).isNotBlank();
        // the body token is what the client echoes in X-XSRF-TOKEN: it must equal the cookie value
        assertThat(result.getResponse().getContentAsString()).contains("\"" + cookieValue + "\"");
    }

    @Test
    void meIs401WhenAnonymousWithProblemBody() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.type").value("urn:kyro:problem:UNAUTHENTICATED"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void correlationIdIsEchoedOrGenerated() throws Exception {
        mockMvc.perform(get("/api/v1/me").header("X-Correlation-Id", "abc-123"))
                .andExpect(header().string("X-Correlation-Id", "abc-123"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
        MvcResult generated = mockMvc.perform(get("/api/v1/me").header("X-Correlation-Id", "bad id with spaces!"))
                .andReturn();
        String id = generated.getResponse().getHeader("X-Correlation-Id");
        assertThat(id).isNotEqualTo("bad id with spaces!");
        assertThat(UUID.fromString(id)).isNotNull();
    }

    @Test
    void policiesArePublic() throws Exception {
        mockMvc.perform(get("/api/v1/policies/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.termsVersion").value("2026-11-01"))
                .andExpect(jsonPath("$.privacyVersion").value("2026-11-01"));
    }

    @Test
    void profileUpdateAndLogout() throws Exception {
        var user = testUsers.createActiveUser(Role.RECIPIENT);
        mockMvc.perform(patch("/api/v1/me/profile")
                        .session(user.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"displayName\":\"Renamed User\",\"recipientPublicDisplayName\":\"Renamed R.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Renamed User"))
                .andExpect(jsonPath("$.recipientProfile.publicDisplayName").value("Renamed R."));
        // donor profile does not exist for this user
        mockMvc.perform(patch("/api/v1/me/profile")
                        .session(user.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"donorPublicDisplayName\":\"Nope\"}"))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(delete("/api/v1/auth/session").session(user.session()).with(Csrf.token()))
                .andExpect(status().isNoContent());
        assertThat(user.session().isInvalid()).isTrue();
    }

    @Test
    void eleventhLoginAttemptWithinAMinuteIs429() throws Exception {
        String body = "{\"email\":\"nobody@kyro.local\",\"password\":\"WrongPassword!2026\"}";
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(post("/api/v1/auth/session")
                            .with(Csrf.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(post("/api/v1/auth/session")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
