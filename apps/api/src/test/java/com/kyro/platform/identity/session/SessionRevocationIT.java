package com.kyro.platform.identity.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyro.platform.common.security.Role;
import com.kyro.platform.support.Csrf;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import com.kyro.platform.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;

class SessionRevocationIT extends IntegrationTestBase {

    @Autowired TestUsers users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void suspendedUsersSessionIsRejectedAtTheNextRequest() throws Exception {
        TestUser donor = users.createActiveUser(Role.DONOR);
        mockMvc.perform(get("/api/v1/me").session(donor.session())).andExpect(status().isOk());

        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE id = ?", donor.id());

        mockMvc.perform(get("/api/v1/me").session(donor.session())).andExpect(status().isUnauthorized());
        Integer revoked = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = 'SESSION_REVOKED' AND entity_id = ?", Integer.class, donor.id());
        assertThat(revoked).isEqualTo(1);
    }

    @Test
    void bumpedCredentialsVersionKillsTheSession() throws Exception {
        TestUser donor = users.createActiveUser(Role.DONOR);
        mockMvc.perform(get("/api/v1/me").session(donor.session())).andExpect(status().isOk());
        jdbc.update("UPDATE users SET credentials_version = credentials_version + 1 WHERE id = ?", donor.id());
        mockMvc.perform(get("/api/v1/me").session(donor.session())).andExpect(status().isUnauthorized());
    }

    private MockHttpSession login(TestUser user) throws Exception {
        var result = mockMvc.perform(post("/api/v1/auth/session")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + user.email() + "\",\"password\":\"" + user.password() + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    @Test
    void passwordChangeKillsOtherSessionsButKeepsTheChangingOne() throws Exception {
        TestUser donor = users.createActiveUser(Role.DONOR);
        MockHttpSession first = login(donor);
        MockHttpSession second = login(donor);
        mockMvc.perform(get("/api/v1/me").session(first)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").session(second)).andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/me/password")
                        .session(first)
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"" + donor.password() + "\",\"newPassword\":\"BrandNewHorse!2027x\"}"))
                .andExpect(status().is2xxSuccessful());

        mockMvc.perform(get("/api/v1/me").session(first)).andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/me").session(second)).andExpect(status().isUnauthorized());
    }
}
