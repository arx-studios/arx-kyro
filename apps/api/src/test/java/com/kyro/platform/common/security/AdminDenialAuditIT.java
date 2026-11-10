package com.kyro.platform.common.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import com.kyro.platform.support.TestUsers.TestUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AdminDenialAuditIT extends IntegrationTestBase {

    @Autowired TestUsers users;
    @Autowired JdbcTemplate jdbc;

    @Test
    void donorHittingAdminPathWritesAccessDeniedRoleMissing() throws Exception {
        TestUser donor = users.createActiveUser(Role.DONOR);
        mockMvc.perform(get("/api/v1/admin/verification/cases").session(donor.session())).andExpect(status().isForbidden());
        Integer n = jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = 'ACCESS_DENIED' AND actor_user_id = ? AND details->>'reason' = 'ROLE_MISSING'",
                Integer.class,
                donor.id());
        assertThat(n).isEqualTo(1);
    }

    @Test
    void anonymous401sAndNonAdminPathDenialsAreNotAudited() throws Exception {
        long before = jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'ACCESS_DENIED'", Long.class);
        mockMvc.perform(get("/api/v1/admin/verification/cases")).andExpect(status().isUnauthorized());
        TestUser donor = users.createActiveUser(Role.DONOR);
        mockMvc.perform(get("/api/v1/education/requests").session(donor.session())).andExpect(status().isForbidden());
        long after = jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE action = 'ACCESS_DENIED'", Long.class);
        assertThat(after).isEqualTo(before);
    }
}
