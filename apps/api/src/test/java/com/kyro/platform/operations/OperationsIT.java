package com.kyro.platform.operations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyro.platform.common.security.Role;
import com.kyro.platform.support.Csrf;
import com.kyro.platform.support.DbFixtures;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import com.kyro.platform.support.TestUsers.TestUser;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Reports, moderation holds, the admin report queue and the dashboard through MockMvc. */
class OperationsIT extends IntegrationTestBase {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired TestUsers users;
    @Autowired DbFixtures fixtures;
    @Autowired JdbcTemplate jdbc;

    // ---- helpers ----

    private UUID publicRequest() {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        return fixtures.insertRequest(recipient.id(), "APPROVED");
    }

    private ResultActions report(TestUser u, UUID requestId, String reason, String details) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("requestId", requestId.toString());
        body.put("reason", reason);
        if (details != null) {
            body.put("details", details);
        }
        return mockMvc.perform(post("/api/v1/reports")
                .session(u.session())
                .with(Csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(JSON.writeValueAsString(body)));
    }

    private UUID createReport(TestUser u, UUID requestId) throws Exception {
        MvcResult r = report(u, requestId, "SUSPECTED_FRAUD", null).andExpect(status().isAccepted()).andReturn();
        return UUID.fromString(JSON.readTree(r.getResponse().getContentAsString()).get("id").asString());
    }

    private ResultActions getAs(TestUser u, String url) throws Exception {
        return mockMvc.perform(get(url).session(u.session()));
    }

    private ResultActions postAs(TestUser u, String url) throws Exception {
        return mockMvc.perform(post(url).session(u.session()).with(Csrf.token()));
    }

    private ResultActions resolve(TestUser u, UUID reportId, String resolution, String note, boolean hold) throws Exception {
        String body = JSON.writeValueAsString(Map.of("resolution", resolution, "note", note, "holdRequest", hold));
        return mockMvc.perform(post("/api/v1/admin/reports/" + reportId + "/resolve")
                .session(u.session())
                .with(Csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    private long audit(String action, UUID entityId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = ? AND entity_id = ?", Long.class, action, entityId);
    }

    private boolean inFeed(UUID requestId) throws Exception {
        MvcResult r = mockMvc.perform(get("/api/v1/discovery/feed").param("limit", "50")).andReturn();
        JsonNode node = JSON.readTree(r.getResponse().getContentAsString());
        for (JsonNode i : node.get("items")) {
            if (i.get("id").asString().equals(requestId.toString())) {
                return true;
            }
        }
        return false;
    }

    // ---- creating reports ----

    @Test
    void donorReportsPublicRequestAndDuplicateIsConflict() throws Exception {
        UUID requestId = publicRequest();
        TestUser donor = users.createActiveUser(Role.DONOR);
        TestUser other = users.createActiveUser(Role.DONOR);

        UUID id = createReport(donor, requestId);
        assertThat(audit("REPORT_CREATED", id)).isEqualTo(1);
        report(donor, requestId, "OTHER", "again please").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_OPEN"));
        createReport(other, requestId);
    }

    @Test
    void reasonOtherNeedsDetails() throws Exception {
        UUID requestId = publicRequest();
        TestUser donor = users.createActiveUser(Role.DONOR);
        report(donor, requestId, "OTHER", null).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        report(donor, requestId, "OTHER", "   ").andExpect(status().isUnprocessableEntity());
    }

    @Test
    void cannotReportOwnOrNonPublicRequestAndAnonymousIs401() throws Exception {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        UUID own = fixtures.insertRequest(recipient.id(), "APPROVED");
        UUID draft = fixtures.insertRequest(recipient.id(), "SUBMITTED");
        TestUser donor = users.createActiveUser(Role.DONOR);

        report(recipient, own, "SUSPECTED_FRAUD", null).andExpect(status().isUnprocessableEntity());
        report(donor, draft, "SUSPECTED_FRAUD", null).andExpect(status().isNotFound());
        report(donor, UUID.randomUUID(), "SUSPECTED_FRAUD", null).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/reports")
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requestId\":\"" + own + "\",\"reason\":\"SUSPECTED_FRAUD\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Disabled("needs SEC-2: config-driven rate limit rule report-create (5/min); remove when rebasing after SEC-2")
    void sixthReportInAMinuteIsRateLimited() throws Exception {
        TestUser donor = users.createActiveUser(Role.DONOR);
        for (int i = 0; i < 5; i++) {
            createReport(donor, publicRequest());
        }
        report(donor, publicRequest(), "SUSPECTED_FRAUD", null).andExpect(status().isTooManyRequests());
    }

    // ---- queue, resolve, hold ----

    @Test
    void verifierResolvesWithHoldRequestVanishesFromDiscoveryAndAdminReleases() throws Exception {
        UUID requestId = publicRequest();
        TestUser donor = users.createActiveUser(Role.DONOR);
        TestUser verifier = users.createActiveUser(Role.VERIFIER);
        TestUser admin = users.createActiveUser(Role.ADMIN);
        UUID reportId = createReport(donor, requestId);
        assertThat(inFeed(requestId)).isTrue();

        postAs(verifier, "/api/v1/admin/reports/" + reportId + "/assign").andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_REVIEW"))
                .andExpect(jsonPath("$.canResolve").value(true));
        assertThat(audit("REPORT_ASSIGNED", reportId)).isEqualTo(1);

        resolve(verifier, reportId, "ACTION_TAKEN", "Document looks altered; holding.", true)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.requestOnHold").value(true))
                .andExpect(jsonPath("$.hold.requestId").value(requestId.toString()))
                .andExpect(jsonPath("$.canResolve").value(false));
        assertThat(audit("REPORT_RESOLVED", reportId)).isEqualTo(1);
        assertThat(audit("REQUEST_HOLD_PLACED", requestId)).isEqualTo(1);

        assertThat(inFeed(requestId)).isFalse();
        mockMvc.perform(get("/api/v1/discovery/feed/{id}", requestId)).andExpect(status().isNotFound());
        // a held request is no longer reportable
        report(users.createActiveUser(Role.DONOR), requestId, "SUSPECTED_FRAUD", null).andExpect(status().isNotFound());

        postAs(verifier, "/api/v1/admin/requests/" + requestId + "/hold/release").andExpect(status().isForbidden());
        postAs(admin, "/api/v1/admin/requests/" + requestId + "/hold/release").andExpect(status().isNoContent());
        assertThat(audit("REQUEST_HOLD_RELEASED", requestId)).isEqualTo(1);
        assertThat(inFeed(requestId)).isTrue();
        mockMvc.perform(get("/api/v1/discovery/feed/{id}", requestId)).andExpect(status().isOk());
        postAs(admin, "/api/v1/admin/requests/" + requestId + "/hold/release").andExpect(status().isNotFound());
    }

    @Test
    void resolveValidationAndGuards() throws Exception {
        UUID requestId = publicRequest();
        TestUser donor = users.createActiveUser(Role.DONOR);
        TestUser v1 = users.createActiveUser(Role.VERIFIER);
        TestUser v2 = users.createActiveUser(Role.VERIFIER);
        UUID reportId = createReport(donor, requestId);

        resolve(v1, reportId, "DISMISSED", "Not assigned yet, should fail.", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_ASSIGNED_TO_YOU"));
        postAs(v1, "/api/v1/admin/reports/" + reportId + "/assign").andExpect(status().isOk());
        postAs(v1, "/api/v1/admin/reports/" + reportId + "/assign").andExpect(status().isOk());
        postAs(v2, "/api/v1/admin/reports/" + reportId + "/assign").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_ALREADY_ASSIGNED"));
        resolve(v2, reportId, "DISMISSED", "I am not the assignee at all.", false)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("REPORT_NOT_ASSIGNED_TO_YOU"));

        resolve(v1, reportId, "DISMISSED", "Dismissed but hold requested.", true).andExpect(status().isUnprocessableEntity());
        resolve(v1, reportId, "DISMISSED", "short", false).andExpect(status().isUnprocessableEntity());
        resolve(v1, reportId, "DISMISSED", "          x         ", false).andExpect(status().isUnprocessableEntity());

        resolve(v1, reportId, "DISMISSED", "Checked the documents; fine.", false).andExpect(status().isOk())
                .andExpect(jsonPath("$.resolution").value("DISMISSED"))
                .andExpect(jsonPath("$.requestOnHold").value(false));
        resolve(v1, reportId, "DISMISSED", "Checked the documents; fine.", false).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        postAs(v1, "/api/v1/admin/reports/" + reportId + "/assign").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(inFeed(requestId)).isTrue();
        // resolved report no longer blocks a new one from the same reporter
        createReport(donor, requestId);
    }

    @Test
    void secondHoldIsIdempotent() throws Exception {
        UUID requestId = publicRequest();
        TestUser d1 = users.createActiveUser(Role.DONOR);
        TestUser d2 = users.createActiveUser(Role.DONOR);
        TestUser v = users.createActiveUser(Role.VERIFIER);
        UUID r1 = createReport(d1, requestId);
        UUID r2 = createReport(d2, requestId);
        for (UUID id : List.of(r1, r2)) {
            postAs(v, "/api/v1/admin/reports/" + id + "/assign").andExpect(status().isOk());
            resolve(v, id, "ACTION_TAKEN", "Hold this request now.", true).andExpect(status().isOk());
        }
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM request_moderation_holds WHERE request_id = ? AND released_at IS NULL",
                        Long.class,
                        requestId))
                .isEqualTo(1);
        assertThat(audit("REQUEST_HOLD_PLACED", requestId)).isEqualTo(1);
    }

    @Test
    void selfReviewGuardIsForbiddenAndAudited() throws Exception {
        // verifier owns the reported request
        TestUser owner = users.createActiveUser(Role.RECIPIENT, Role.VERIFIER);
        UUID requestId = fixtures.insertRequest(owner.id(), "APPROVED");
        TestUser donor = users.createActiveUser(Role.DONOR);
        UUID reportId = createReport(donor, requestId);
        postAs(owner, "/api/v1/admin/reports/" + reportId + "/assign").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"));
        assertThat(audit("ACCESS_DENIED", reportId)).isEqualTo(1);

        // staff member filed the report
        TestUser staffReporter = users.createActiveUser(Role.DONOR, Role.VERIFIER);
        UUID other = publicRequest();
        UUID r2 = createReport(staffReporter, other);
        postAs(staffReporter, "/api/v1/admin/reports/" + r2 + "/assign").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"));
        assertThat(audit("ACCESS_DENIED", r2)).isEqualTo(1);
    }

    @Test
    void nonStaffCannotUseAdminEndpoints() throws Exception {
        TestUser donor = users.createActiveUser(Role.DONOR);
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        getAs(donor, "/api/v1/admin/reports").andExpect(status().isForbidden());
        getAs(recipient, "/api/v1/admin/reports").andExpect(status().isForbidden());
        getAs(donor, "/api/v1/admin/dashboard").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/admin/reports")).andExpect(status().isUnauthorized());
    }

    @Test
    void detailShowsOtherReportsAndNoRecipientExposure() throws Exception {
        UUID requestId = publicRequest();
        TestUser d1 = users.createActiveUser(Role.DONOR);
        TestUser d2 = users.createActiveUser(Role.DONOR);
        TestUser v = users.createActiveUser(Role.VERIFIER);
        UUID r1 = createReport(d1, requestId);
        createReport(d2, requestId);
        getAs(v, "/api/v1/admin/reports/" + r1).andExpect(status().isOk())
                .andExpect(jsonPath("$.otherReports.length()").value(1))
                .andExpect(jsonPath("$.canResolve").value(false))
                .andExpect(jsonPath("$.reporter.id").value(d1.id().toString()))
                .andExpect(jsonPath("$.requestStatus").value("APPROVED"));
        getAs(v, "/api/v1/admin/reports/" + UUID.randomUUID()).andExpect(status().isNotFound());
    }

    @Test
    void dashboardCountsMatchFixtures() throws Exception {
        jdbc.update("DELETE FROM request_moderation_holds");
        jdbc.update("DELETE FROM user_reports");
        long baseUnassigned = jdbc.queryForObject("SELECT count(*) FROM verification_cases WHERE status = 'OPEN'", Long.class);
        TestUser v = users.createActiveUser(Role.VERIFIER);
        TestUser donor = users.createActiveUser(Role.DONOR);
        TestUser donor2 = users.createActiveUser(Role.DONOR);
        UUID req1 = publicRequest();
        UUID req2 = publicRequest();
        UUID r1 = createReport(donor, req1);
        createReport(donor, req2);
        createReport(donor2, req2);
        postAs(v, "/api/v1/admin/reports/" + r1 + "/assign").andExpect(status().isOk());
        resolve(v, r1, "ACTION_TAKEN", "Taking action and holding.", true).andExpect(status().isOk());
        UUID caseReq = fixtures.insertRequest(users.createActiveUser(Role.RECIPIENT).id(), "SUBMITTED");
        jdbc.update("INSERT INTO verification_cases (request_id, submission_number, status) VALUES (?, 1, 'OPEN')", caseReq);
        UUID mineReq = fixtures.insertRequest(users.createActiveUser(Role.RECIPIENT).id(), "IN_REVIEW");
        jdbc.update(
                "INSERT INTO verification_cases (request_id, submission_number, status, assigned_to, assigned_at) VALUES (?, 1, 'ASSIGNED', ?, now())",
                mineReq,
                v.id());

        getAs(v, "/api/v1/admin/dashboard").andExpect(status().isOk())
                .andExpect(jsonPath("$.unassignedCases").value(baseUnassigned + 1))
                .andExpect(jsonPath("$.myOpenCases").value(1))
                .andExpect(jsonPath("$.openReports").value(2))
                .andExpect(jsonPath("$.unassignedReports").value(2))
                .andExpect(jsonPath("$.activeHolds").value(1))
                .andExpect(jsonPath("$.generatedAt").exists());
    }

    @Test
    void pagingHasNoDuplicatesAndRejectsBadCursor() throws Exception {
        jdbc.update("DELETE FROM request_moderation_holds");
        jdbc.update("DELETE FROM user_reports");
        TestUser v = users.createActiveUser(Role.VERIFIER);
        List<UUID> created = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            created.add(createReport(users.createActiveUser(Role.DONOR), publicRequest()));
        }
        // resolve two so RESOLVED / ALL queues are non-trivial
        for (int i = 0; i < 2; i++) {
            postAs(v, "/api/v1/admin/reports/" + created.get(i) + "/assign").andExpect(status().isOk());
            resolve(v, created.get(i), "DISMISSED", "Nothing wrong found here.", false).andExpect(status().isOk());
        }
        assertThat(pageIds(v, "OPEN", 2)).hasSize(5).doesNotHaveDuplicates();
        assertThat(pageIds(v, "ALL", 3)).hasSize(7).doesNotHaveDuplicates();
        assertThat(pageIds(v, "RESOLVED", 1)).hasSize(2).doesNotHaveDuplicates();
        postAs(v, "/api/v1/admin/reports/" + created.get(3) + "/assign").andExpect(status().isOk());
        assertThat(pageIds(v, "MINE", 1)).containsExactly(created.get(3).toString());
        // OPEN is oldest first
        assertThat(pageIds(v, "OPEN", 10).get(0)).isEqualTo(created.get(2).toString());
        getAs(v, "/api/v1/admin/reports?cursor=not-a-cursor").andExpect(status().isBadRequest());
    }

    private List<String> pageIds(TestUser u, String queue, int limit) throws Exception {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        do {
            String url = "/api/v1/admin/reports?queue=" + queue + "&limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor);
            MvcResult r = getAs(u, url).andExpect(status().isOk()).andReturn();
            JsonNode node = JSON.readTree(r.getResponse().getContentAsString());
            node.get("items").forEach(i -> ids.add(i.get("id").asString()));
            cursor = node.get("nextCursor").isNull() ? null : node.get("nextCursor").asString();
        } while (cursor != null);
        assertThat(new HashSet<>(ids)).hasSameSizeAs(ids);
        return ids;
    }
}
