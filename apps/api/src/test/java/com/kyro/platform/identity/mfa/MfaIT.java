package com.kyro.platform.identity.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyro.platform.common.security.Role;
import com.kyro.platform.support.Csrf;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import com.kyro.platform.support.TestUsers.TestUser;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class MfaIT extends IntegrationTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String ADMIN_PATH = "/api/v1/admin/verification/cases";

    @Autowired TestUsers users;
    @Autowired JdbcTemplate jdbc;

    private ResultActions postJson(MockHttpSession s, String path, String body) throws Exception {
        return mockMvc.perform(post(path).session(s).with(Csrf.token()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions cases(MockHttpSession s) throws Exception {
        return mockMvc.perform(get(ADMIN_PATH).session(s));
    }

    private MockHttpSession freshUnverified(TestUser u, Role... roles) {
        return TestUsers.sessionWithoutMfa(u.id(), u.email(), java.util.EnumSet.copyOf(List.of(roles)));
    }

    @Test
    void enrollConfirmThenAdminEndpointsOpen() throws Exception {
        TestUser v = users.createActiveUserWithoutMfa(Role.VERIFIER);
        cases(v.session()).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MFA_ENROLLMENT_REQUIRED"));
        mockMvc.perform(get("/api/v1/me").session(v.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfa.required").value(true))
                .andExpect(jsonPath("$.mfa.enrolled").value(false))
                .andExpect(jsonPath("$.mfa.verified").value(false));

        String body = postJson(v.session(), "/api/v1/auth/mfa/totp/enrollment", "").andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value("KYRO Sandbox"))
                .andReturn().getResponse().getContentAsString();
        String secret = JSON.readTree(body).get("secret").asText();
        assertThat(JSON.readTree(body).get("otpauthUri").asText()).startsWith("otpauth://totp/").contains("secret=" + secret);

        // the secret is encrypted at rest
        String stored = jdbc.queryForObject("SELECT secret_ciphertext FROM user_mfa_credentials WHERE user_id = ?", String.class, v.id());
        assertThat(stored).doesNotContain(secret);

        postJson(v.session(), "/api/v1/auth/mfa/totp/enrollment/confirm", "{\"code\":\"000000\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        String confirm = postJson(v.session(), "/api/v1/auth/mfa/totp/enrollment/confirm", "{\"code\":\"" + TestUsers.totpCode(secret) + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codes.length()").value(10))
                .andReturn().getResponse().getContentAsString();
        for (JsonNode c : JSON.readTree(confirm).get("codes")) {
            assertThat(c.asText()).matches("[A-Z0-9]{5}-[A-Z0-9]{5}");
        }
        // recovery codes are stored hashed
        Integer plain = jdbc.queryForObject(
                "SELECT count(*) FROM user_mfa_recovery_codes WHERE user_id = ? AND code_hash ~ '-'", Integer.class, v.id());
        assertThat(plain).isZero();

        cases(v.session()).andExpect(status().isOk());

        // enrolling again is refused
        postJson(v.session(), "/api/v1/auth/mfa/totp/enrollment", "").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MFA_ALREADY_ENROLLED"));
    }

    @Test
    void verifyWithTotpRejectsReplayAndAcceptsTheNextStep() throws Exception {
        TestUser v = users.createActiveUserWithoutMfa(Role.VERIFIER);
        String secret = users.enrollTotp(v.id());
        MockHttpSession s = freshUnverified(v, Role.VERIFIER);
        cases(s).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MFA_REQUIRED"));

        String code = TestUsers.totpCode(secret);
        postJson(s, "/api/v1/auth/mfa/verify", "{\"code\":\"" + code + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfa.verified").value(true));
        cases(s).andExpect(status().isOk());

        // same code on a new (unverified) session: replay
        MockHttpSession s2 = freshUnverified(v, Role.VERIFIER);
        postJson(s2, "/api/v1/auth/mfa/verify", "{\"code\":\"" + code + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        cases(s2).andExpect(status().isForbidden());
        // a code from a later step (inside the skew window) still works
        postJson(s2, "/api/v1/auth/mfa/verify", "{\"code\":\"" + TestUsers.totpCode(secret, 1) + "\"}").andExpect(status().isOk());

        Integer failed = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = 'MFA_FAILED' AND entity_id = ?", Integer.class, v.id());
        assertThat(failed).isEqualTo(1);
    }

    @Test
    void recoveryCodeWorksExactlyOnce() throws Exception {
        TestUser v = users.createActiveUserWithoutMfa(Role.VERIFIER);
        MockHttpSession enrollSession = v.session();
        String secret = JSON.readTree(postJson(enrollSession, "/api/v1/auth/mfa/totp/enrollment", "").andReturn().getResponse().getContentAsString())
                .get("secret").asText();
        List<String> codes = new ArrayList<>();
        JSON.readTree(postJson(enrollSession, "/api/v1/auth/mfa/totp/enrollment/confirm", "{\"code\":\"" + TestUsers.totpCode(secret) + "\"}")
                        .andReturn().getResponse().getContentAsString())
                .get("codes")
                .forEach(n -> codes.add(n.asText()));

        MockHttpSession s1 = freshUnverified(v, Role.VERIFIER);
        postJson(s1, "/api/v1/auth/mfa/verify", "{\"recoveryCode\":\"" + codes.get(0) + "\"}").andExpect(status().isOk());
        cases(s1).andExpect(status().isOk());

        MockHttpSession s2 = freshUnverified(v, Role.VERIFIER);
        postJson(s2, "/api/v1/auth/mfa/verify", "{\"recoveryCode\":\"" + codes.get(0) + "\"}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MFA_CODE_INVALID"));
        // another unused code is fine
        postJson(s2, "/api/v1/auth/mfa/verify", "{\"recoveryCode\":\"" + codes.get(1) + "\"}").andExpect(status().isOk());
        // both code and recovery code, or neither, is a validation error
        postJson(s2, "/api/v1/auth/mfa/verify", "{}").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void adminResetsAnotherUsersMfaAndKillsTheirSession() throws Exception {
        TestUser admin = users.createActiveUser(Role.ADMIN);
        TestUser target = users.createActiveUser(Role.VERIFIER);
        users.enrollTotp(target.id());
        cases(target.session()).andExpect(status().isOk());

        postJson(admin.session(), "/api/v1/admin/users/" + target.id() + "/mfa/reset", "").andExpect(status().isNoContent());

        cases(target.session()).andExpect(status().isUnauthorized());
        Integer creds = jdbc.queryForObject("SELECT count(*) FROM user_mfa_credentials WHERE user_id = ?", Integer.class, target.id());
        assertThat(creds).isZero();
        Integer audited = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = 'MFA_RESET' AND entity_id = ?", Integer.class, target.id());
        assertThat(audited).isEqualTo(1);
    }

    @Test
    void resetOnSelfOrByNonAdminIsForbidden() throws Exception {
        TestUser admin = users.createActiveUser(Role.ADMIN);
        postJson(admin.session(), "/api/v1/admin/users/" + admin.id() + "/mfa/reset", "")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        TestUser verifier = users.createActiveUser(Role.VERIFIER);
        postJson(verifier.session(), "/api/v1/admin/users/" + UUID.randomUUID() + "/mfa/reset", "").andExpect(status().isForbidden());
    }

    @Test
    void donorCannotUseMfaEndpoints() throws Exception {
        TestUser donor = users.createActiveUser(Role.DONOR);
        postJson(donor.session(), "/api/v1/auth/mfa/verify", "{\"code\":\"123456\"}").andExpect(status().isForbidden());
        postJson(donor.session(), "/api/v1/auth/mfa/totp/enrollment", "").andExpect(status().isForbidden());
    }

    @Test
    void mfaDeniedAdminAccessIsAudited() throws Exception {
        TestUser v = users.createActiveUserWithoutMfa(Role.VERIFIER);
        users.enrollTotp(v.id());
        cases(v.session()).andExpect(status().isForbidden());
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = 'ACCESS_DENIED' AND actor_user_id = ? AND details->>'reason' = 'MFA_REQUIRED'",
                Integer.class,
                v.id());
        assertThat(n).isEqualTo(1);
    }
}
