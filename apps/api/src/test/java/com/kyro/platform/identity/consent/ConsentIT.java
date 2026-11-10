package com.kyro.platform.identity.consent;

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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

/** Policy versions are bumped to 2027-01-01 for this class; users created here start on the old (2026-11-01) version. */
@TestPropertySource(properties = {"kyro.policy.terms-version=2027-01-01"})
class ConsentIT extends IntegrationTestBase {

    @Autowired TestUsers users;
    @Autowired JdbcTemplate jdbc;

    private TestUser userWithOldConsents() {
        TestUser u = users.createActiveUser(Role.DONOR);
        jdbc.update("INSERT INTO consent_records (user_id, policy_type, policy_version) VALUES (?, 'TERMS', '2026-11-01')", u.id());
        jdbc.update("INSERT INTO consent_records (user_id, policy_type, policy_version) VALUES (?, 'PRIVACY', '2026-11-01')", u.id());
        return u;
    }

    private org.springframework.test.web.servlet.ResultActions accept(TestUser u, String terms, String privacy) throws Exception {
        return mockMvc.perform(post("/api/v1/me/consents")
                .session(u.session())
                .with(Csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"termsVersion\":\"" + terms + "\",\"privacyVersion\":\"" + privacy + "\"}"));
    }

    @Test
    void historyShowsOldAcceptancesAndReacceptIsRequired() throws Exception {
        TestUser u = userWithOldConsents();
        mockMvc.perform(get("/api/v1/me/consents").session(u.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.current.termsVersion").value("2027-01-01"))
                .andExpect(jsonPath("$.acceptedTermsVersion").value("2026-11-01"))
                .andExpect(jsonPath("$.acceptedPrivacyVersion").value("2026-11-01"))
                .andExpect(jsonPath("$.reacceptRequired").value(true))
                .andExpect(jsonPath("$.history.length()").value(2));
    }

    @Test
    void acceptingCurrentVersionsAppendsRowsAndClearsTheFlag() throws Exception {
        TestUser u = userWithOldConsents();
        accept(u, "2027-01-01", "2026-11-01")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reacceptRequired").value(false))
                .andExpect(jsonPath("$.acceptedTermsVersion").value("2027-01-01"))
                .andExpect(jsonPath("$.history.length()").value(3))
                .andExpect(jsonPath("$.history[0].policyType").value("TERMS"))
                .andExpect(jsonPath("$.history[0].policyVersion").value("2027-01-01"));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM audit_events WHERE action = 'CONSENT_ACCEPTED' AND entity_id = ?", Long.class, u.id()))
                .isEqualTo(1);
        // older rows are untouched (append-only)
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consent_records WHERE user_id = ?", Long.class, u.id())).isEqualTo(3);
        // accepting again appends nothing
        accept(u, "2027-01-01", "2026-11-01").andExpect(status().isOk()).andExpect(jsonPath("$.history.length()").value(3));
    }

    @Test
    void wrongVersionIs422() throws Exception {
        TestUser u = userWithOldConsents();
        accept(u, "2026-11-01", "2026-11-01")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("CONSENT_VERSION_MISMATCH"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM consent_records WHERE user_id = ?", Long.class, u.id())).isEqualTo(2);
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/me/consents")).andExpect(status().isUnauthorized());
    }
}
