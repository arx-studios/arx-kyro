package com.kyro.platform.education;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyro.platform.common.security.Role;
import com.kyro.platform.support.Csrf;
import com.kyro.platform.support.DbFixtures;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import com.kyro.platform.support.TestUsers.TestUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

/** PATCH {@code clearFields} (D-010 / D-015). */
class ClearFieldsIT extends IntegrationTestBase {

    @Autowired TestUsers users;
    @Autowired DbFixtures fixtures;
    @Autowired JdbcTemplate jdbc;

    private ResultActions patchBody(TestUser u, UUID rid, String json) throws Exception {
        return mockMvc.perform(patch("/api/v1/education/requests/{r}", rid)
                .session(u.session())
                .with(Csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void clearsOptionalFieldsAndSubmitThenListsThem() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        UUID rid = fixtures.insertRequest(u.id(), "DRAFT");
        patchBody(u, rid, "{\"version\":0,\"clearFields\":[\"courseName\",\"dueDate\",\"feeReference\"]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseName").doesNotExist())
                .andExpect(jsonPath("$.dueDate").doesNotExist())
                .andExpect(jsonPath("$.institutionCity").value("Pune"))
                .andExpect(jsonPath("$.version").value(1));
        mockMvc.perform(get("/api/v1/education/requests/{r}", rid).session(u.session()))
                .andExpect(jsonPath("$.courseName").doesNotExist())
                .andExpect(jsonPath("$.dueDate").doesNotExist());
        String normalized = jdbc.queryForObject("SELECT fee_reference_normalized FROM funding_requests WHERE id = ?", String.class, rid);
        assertThat(normalized).isNull();
        String details = jdbc.queryForObject(
                "SELECT details::text FROM audit_events WHERE action = 'REQUEST_UPDATED' AND entity_id = ?", String.class, rid);
        assertThat(details).contains("clearedFields").contains("courseName").contains("dueDate");

        mockMvc.perform(post("/api/v1/education/requests/{r}/submit", rid).session(u.session()).with(Csrf.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_INCOMPLETE"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("courseName")));
    }

    @Test
    void clearingAndSettingTheSameFieldIs422() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        UUID rid = fixtures.insertRequest(u.id(), "DRAFT");
        patchBody(u, rid, "{\"version\":0,\"courseName\":\"New course\",\"clearFields\":[\"courseName\"]}")
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void clearingANonClearableFieldIsRejected() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        UUID rid = fixtures.insertRequest(u.id(), "DRAFT");
        ResultActions r = patchBody(u, rid, "{\"version\":0,\"clearFields\":[\"title\"]}");
        int s = r.andReturn().getResponse().getStatus();
        assertThat(s).isIn(400, 422);
        assertThat(jdbc.queryForObject("SELECT title IS NOT NULL FROM funding_requests WHERE id = ?", Boolean.class, rid)).isTrue();
    }
}
