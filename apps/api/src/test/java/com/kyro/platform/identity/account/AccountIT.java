package com.kyro.platform.identity.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyro.platform.common.security.Role;
import com.kyro.platform.common.security.SessionAttributes;
import com.kyro.platform.identity.account.CapturingMailSenderConfig.CapturingMailSender;
import com.kyro.platform.support.Csrf;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import com.kyro.platform.support.TestUsers.TestUser;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;

@Import(CapturingMailSenderConfig.class)
class AccountIT extends IntegrationTestBase {

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_-]+)");
    private static final String NEW_PASSWORD = "BrandNewPassw0rd!";

    @Autowired TestUsers users;
    @Autowired JdbcTemplate jdbc;
    @Autowired CapturingMailSender mail;

    private void requestReset(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists());
    }

    private String tokenFromLatestMail(String email) {
        List<SimpleMailMessage> mails = mail.to(email);
        assertThat(mails).isNotEmpty();
        Matcher m = TOKEN.matcher(mails.get(mails.size() - 1).getText());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private org.springframework.test.web.servlet.ResultActions confirm(String token, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                .with(Csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"newPassword\":\"" + password + "\"}"));
    }

    private int loginStatus(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/session")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn()
                .getResponse()
                .getStatus();
    }

    private long credentialsVersion(UUID id) {
        return jdbc.queryForObject("SELECT credentials_version FROM users WHERE id = ?", Long.class, id);
    }

    private long audits(String action, UUID entityId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = ? AND entity_id = ?", Long.class, action, entityId);
    }

    @Test
    void unknownEmailGets202AndNoMail() throws Exception {
        String email = "nobody-" + UUID.randomUUID() + "@kyro.local";
        requestReset(email);
        assertThat(mail.to(email)).isEmpty();
    }

    @Test
    void nonActiveAccountGets202AndNoMail() throws Exception {
        TestUser u = users.createActiveUser(Role.DONOR);
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", u.id());
        requestReset(u.email());
        assertThat(mail.to(u.email())).isEmpty();
        assertThat(audits("PASSWORD_RESET_REQUESTED", u.id())).isZero();
    }

    @Test
    void resetFlowChangesPasswordOnceAndBumpsCredentialsVersion() throws Exception {
        TestUser u = users.createActiveUser(Role.DONOR);
        long before = credentialsVersion(u.id());
        requestReset(u.email());
        assertThat(audits("PASSWORD_RESET_REQUESTED", u.id())).isEqualTo(1);
        String token = tokenFromLatestMail(u.email());
        String stored = jdbc.queryForObject("SELECT token_hash FROM password_reset_tokens WHERE user_id = ?", String.class, u.id());
        assertThat(stored).isNotEqualTo(token).hasSize(64);

        confirm(token, NEW_PASSWORD).andExpect(status().isNoContent());

        assertThat(credentialsVersion(u.id())).isEqualTo(before + 1);
        assertThat(audits("PASSWORD_RESET_COMPLETED", u.id())).isEqualTo(1);
        assertThat(loginStatus(u.email(), u.password())).isEqualTo(401);
        assertThat(loginStatus(u.email(), NEW_PASSWORD)).isEqualTo(200);
        // "your password was changed" notice, without the token
        List<SimpleMailMessage> mails = mail.to(u.email());
        assertThat(mails.get(mails.size() - 1).getText()).doesNotContain("token=");

        // single use
        confirm(token, "AnotherPassw0rd!!").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
    }

    @Test
    void expiredAndUnknownTokensAndShortPasswordsAreRejected() throws Exception {
        TestUser u = users.createActiveUser(Role.DONOR);
        requestReset(u.email());
        String token = tokenFromLatestMail(u.email());
        confirm(token, "short").andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        jdbc.update("UPDATE password_reset_tokens SET expires_at = now() - interval '1 minute' WHERE user_id = ?", u.id());
        confirm(token, NEW_PASSWORD).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        confirm("not-a-real-token", NEW_PASSWORD).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("TOKEN_INVALID"));
        assertThat(loginStatus(u.email(), u.password())).isEqualTo(200);
    }

    @Test
    void completingResetConsumesOtherOutstandingTokens() throws Exception {
        TestUser u = users.createActiveUser(Role.DONOR);
        requestReset(u.email());
        String first = tokenFromLatestMail(u.email());
        requestReset(u.email());
        String second = tokenFromLatestMail(u.email());
        assertThat(second).isNotEqualTo(first);
        confirm(second, NEW_PASSWORD).andExpect(status().isNoContent());
        confirm(first, "AnotherPassw0rd!!").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void changePasswordKeepsCallerSessionAndBumpsCredentialsVersion() throws Exception {
        TestUser u = users.createActiveUser(Role.DONOR);
        long before = credentialsVersion(u.id());
        mockMvc.perform(post("/api/v1/me/password")
                        .session(u.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + u.password() + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isNoContent());
        assertThat(credentialsVersion(u.id())).isEqualTo(before + 1);
        assertThat(u.session().getAttribute(SessionAttributes.CREDENTIALS_VERSION)).isEqualTo(before + 1);
        assertThat(audits("PASSWORD_CHANGED", u.id())).isEqualTo(1);
        assertThat(loginStatus(u.email(), NEW_PASSWORD)).isEqualTo(200);
        assertThat(loginStatus(u.email(), u.password())).isEqualTo(401);
        // the calling session still works
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/me").session(u.session()))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordWithWrongCurrentPasswordIs422AndChangesNothing() throws Exception {
        TestUser u = users.createActiveUser(Role.DONOR);
        long before = credentialsVersion(u.id());
        mockMvc.perform(post("/api/v1/me/password")
                        .session(u.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong-password-123\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CURRENT_PASSWORD_INCORRECT"));
        assertThat(credentialsVersion(u.id())).isEqualTo(before);
        assertThat(audits("LOGIN_FAILED", u.id())).isEqualTo(1);
        assertThat(loginStatus(u.email(), u.password())).isEqualTo(200);
    }

    @Test
    void registeredUserSeesTwoConsentRowsAndNoReacceptRequired() throws Exception {
        String email = "reg-" + UUID.randomUUID() + "@kyro.local";
        mockMvc.perform(post("/api/v1/auth/register")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"CorrectHorse!2026","displayName":"Asha Verma","publicDisplayName":"Asha V.",
                                 "countryCode":"IN","roles":["DONOR"],"consents":{"termsVersion":"2026-11-01","privacyVersion":"2026-11-01"}}
                                """.formatted(email)))
                .andExpect(status().isAccepted());
        UUID id = jdbc.queryForObject("SELECT id FROM users WHERE email_normalized = ?", UUID.class, email);
        var session = TestUsers.sessionFor(id, email, java.util.Set.of(Role.DONOR));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/me/consents").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.history.length()").value(2))
                .andExpect(jsonPath("$.reacceptRequired").value(false));
    }

    @Test
    void changePasswordRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/me/password")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"x\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }
}
