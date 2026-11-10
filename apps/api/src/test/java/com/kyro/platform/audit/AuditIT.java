package com.kyro.platform.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyro.platform.audit.application.AuditActions;
import com.kyro.platform.audit.application.AuditEntry;
import com.kyro.platform.audit.application.AuditService;
import com.kyro.platform.common.security.Role;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class AuditIT extends IntegrationTestBase {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuditService audit;

    @Autowired
    TestUsers testUsers;

    @Autowired
    TransactionTemplate tx;

    @Test
    void auditEventsAreImmutable() {
        UUID entity = UUID.randomUUID();
        audit.record(new AuditEntry(AuditActions.REQUEST_CREATED, "FUNDING_REQUEST", entity, Map.of("k", "v")));
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_events SET action = 'X' WHERE entity_id = ?", entity))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_events WHERE entity_id = ?", entity))
                .hasMessageContaining("append-only");
    }

    @Test
    void auditParticipatesInCallersTransaction() {
        UUID entity = UUID.randomUUID();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
                    audit.record(new AuditEntry(AuditActions.REQUEST_CREATED, "FUNDING_REQUEST", entity, Map.of()));
                    throw new IllegalStateException("rollback");
                }))
                .isInstanceOf(IllegalStateException.class);
        Integer n = jdbc.queryForObject("SELECT count(*) FROM audit_events WHERE entity_id = ?", Integer.class, entity);
        assertThat(n).isZero();
    }

    @Test
    void verifierCanListAndFilterAuditEventsWithPaging() throws Exception {
        var verifier = testUsers.createActiveUser(Role.VERIFIER);
        UUID entity = UUID.randomUUID();
        for (int i = 0; i < 5; i++) {
            audit.record(new AuditEntry(AuditActions.REQUEST_UPDATED, "FUNDING_REQUEST", entity, Map.of("i", i)));
        }
        String page1 = mockMvc.perform(get("/api/v1/admin/audit-events")
                        .session(verifier.session())
                        .param("entityId", entity.toString())
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].action").value("REQUEST_UPDATED"))
                .andExpect(jsonPath("$.items[0].entityId").value(entity.toString()))
                .andExpect(jsonPath("$.items[0].details.i").value(4))
                .andExpect(jsonPath("$.nextCursor").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String cursor = new tools.jackson.databind.json.JsonMapper().readTree(page1).get("nextCursor").asString();
        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .session(verifier.session())
                        .param("entityId", entity.toString())
                        .param("limit", "2")
                        .param("cursor", cursor))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].details.i").value(2));
    }

    @Test
    void auditEventIncludesActorSummary() throws Exception {
        var admin = testUsers.createActiveUser(Role.ADMIN, Role.VERIFIER);
        // a failed login by the admin's account creates an event with no actor; use the verifier's own list call instead:
        UUID entity = admin.id();
        jdbc.update("INSERT INTO audit_events (actor_user_id, action, entity_type, entity_id) VALUES (?, 'PROFILE_UPDATED', 'USER', ?)", admin.id(), entity);
        mockMvc.perform(get("/api/v1/admin/audit-events")
                        .session(admin.session())
                        .param("actorUserId", admin.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].actor.id").value(admin.id().toString()))
                .andExpect(jsonPath("$.items[0].actor.displayName").value("Test User"));
    }

    @Test
    void nonStaffCannotReadAuditEvents() throws Exception {
        for (Role role : new Role[] {Role.RECIPIENT, Role.DONOR}) {
            var user = testUsers.createActiveUser(role);
            mockMvc.perform(get("/api/v1/admin/audit-events").session(user.session()))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        }
        mockMvc.perform(get("/api/v1/admin/audit-events")).andExpect(status().isUnauthorized());
    }

    @Test
    void routeRulesForRolesAreEnforced() throws Exception {
        var donor = testUsers.createActiveUser(Role.DONOR);
        mockMvc.perform(get("/api/v1/me/education/requests").session(donor.session())).andExpect(status().isForbidden());
        var recipient = testUsers.createActiveUser(Role.RECIPIENT);
        mockMvc.perform(get("/api/v1/discovery/saved").session(recipient.session())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/discovery/saved")).andExpect(status().isUnauthorized());
    }
}
