package com.kyro.platform.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyro.platform.common.security.Role;
import com.kyro.platform.education.application.port.EducationRequestSubmitted;
import com.kyro.platform.education.application.port.EducationRequestWithdrawn;
import com.kyro.platform.support.Csrf;
import com.kyro.platform.support.DbFixtures;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import com.kyro.platform.support.TestUsers.TestUser;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * End-to-end verification flows through MockMvc. Requests are created with {@link DbFixtures}; the education module's
 * submit/withdraw events are published directly. The facade is the real API-2 bean when present, otherwise the JDBC
 * stand-in from {@link FallbackEducationFacadeConfig}.
 */
class VerificationIT extends IntegrationTestBase {

    private static final String BASE = "/api/v1/admin/verification/cases";
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired TestUsers users;
    @Autowired DbFixtures fixtures;
    @Autowired JdbcTemplate jdbc;
    @Autowired ApplicationEventPublisher events;

    // ---- helpers ----

    record Submitted(TestUser recipient, UUID requestId, UUID caseId) {}

    private Submitted submit() {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        UUID requestId = fixtures.insertRequest(recipient.id(), "SUBMITTED");
        UUID caseId = publishSubmitted(recipient.id(), requestId, 1);
        return new Submitted(recipient, requestId, caseId);
    }

    private UUID publishSubmitted(UUID recipientId, UUID requestId, int number) {
        events.publishEvent(new EducationRequestSubmitted(requestId, recipientId, number, Instant.now()));
        return jdbc.queryForObject(
                "SELECT id FROM verification_cases WHERE request_id = ? AND submission_number = ?",
                UUID.class,
                requestId,
                number);
    }

    private String requestStatus(UUID requestId) {
        return jdbc.queryForObject("SELECT status FROM funding_requests WHERE id = ?", String.class, requestId);
    }

    private long auditCount(String action, UUID entityId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = ? AND entity_id = ?", Long.class, action, entityId);
    }

    private ResultActions getAs(TestUser u, String url) throws Exception {
        return mockMvc.perform(get(url).session(u.session()));
    }

    private ResultActions postAs(TestUser u, String url) throws Exception {
        return mockMvc.perform(post(url).session(u.session()).with(Csrf.token()));
    }

    private ResultActions decide(TestUser u, UUID caseId, String decision, String rationale, String message) throws Exception {
        String body = JSON.writeValueAsString(
                message == null
                        ? java.util.Map.of("decision", decision, "rationale", rationale)
                        : java.util.Map.of("decision", decision, "rationale", rationale, "messageToRecipient", message));
        MockHttpServletRequestBuilder req = post(BASE + "/" + caseId + "/decision")
                .session(u.session())
                .with(Csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
        return mockMvc.perform(req);
    }

    private List<String> pageIds(TestUser u, String queue, int limit) throws Exception {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        do {
            String url = BASE + "?queue=" + queue + "&limit=" + limit + (cursor == null ? "" : "&cursor=" + cursor);
            MvcResult r = getAs(u, url).andExpect(status().isOk()).andReturn();
            JsonNode node = JSON.readTree(r.getResponse().getContentAsString());
            node.get("items").forEach(i -> ids.add(i.get("id").asString()));
            cursor = node.get("nextCursor").isNull() ? null : node.get("nextCursor").asString();
        } while (cursor != null);
        return ids;
    }

    // ---- tests ----

    @Test
    void submittedEventOpensCaseVisibleInUnassignedQueue() throws Exception {
        TestUser verifier = users.createActiveUser(Role.VERIFIER);
        Submitted s = submit();

        assertThat(pageIds(verifier, "UNASSIGNED", 50)).contains(s.caseId().toString());
        getAs(verifier, BASE + "/" + s.caseId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.submissionNumber").value(1))
                .andExpect(jsonPath("$.canDecide").value(false))
                .andExpect(jsonPath("$.assignedTo").doesNotExist())
                .andExpect(jsonPath("$.request.id").value(s.requestId().toString()))
                .andExpect(jsonPath("$.request.recipient.email").value(s.recipient().email()))
                .andExpect(jsonPath("$.decisions.length()").value(0));
        assertThat(auditCount("CASE_OPENED", s.caseId())).isEqualTo(1);

        // replaying the same event does not create a second case
        publishSubmitted(s.recipient().id(), s.requestId(), 1);
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM verification_cases WHERE request_id = ?", Long.class, s.requestId()))
                .isEqualTo(1);
    }

    @Test
    void claimMovesRequestToInReviewAndSecondVerifierIsRejected() throws Exception {
        TestUser v1 = users.createActiveUser(Role.VERIFIER);
        TestUser v2 = users.createActiveUser(Role.VERIFIER, Role.ADMIN);
        Submitted s = submit();

        postAs(v1, BASE + "/" + s.caseId() + "/assign")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.assignedTo.id").value(v1.id().toString()))
                .andExpect(jsonPath("$.canDecide").value(true))
                .andExpect(jsonPath("$.request.status").value("IN_REVIEW"));
        assertThat(requestStatus(s.requestId())).isEqualTo("IN_REVIEW");
        assertThat(auditCount("CASE_ASSIGNED", s.caseId())).isEqualTo(1);

        postAs(v2, BASE + "/" + s.caseId() + "/assign")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_ALREADY_ASSIGNED"));
        // the holder re-claiming is a harmless no-op
        postAs(v1, BASE + "/" + s.caseId() + "/assign").andExpect(status().isOk());
        assertThat(auditCount("CASE_ASSIGNED", s.caseId())).isEqualTo(1);

        assertThat(pageIds(v1, "UNASSIGNED", 50)).doesNotContain(s.caseId().toString());
        assertThat(pageIds(v1, "MINE", 50)).contains(s.caseId().toString());
        assertThat(pageIds(v2, "MINE", 50)).doesNotContain(s.caseId().toString());
        // the other verifier sees it as read-only
        getAs(v2, BASE + "/" + s.caseId()).andExpect(status().isOk()).andExpect(jsonPath("$.canDecide").value(false));
    }

    @Test
    void concurrentClaimsHaveExactlyOneWinner() throws Exception {
        TestUser v1 = users.createActiveUser(Role.VERIFIER);
        TestUser v2 = users.createActiveUser(Role.VERIFIER);
        Submitted s = submit();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Integer>> calls = List.of(
                    () -> postAs(v1, BASE + "/" + s.caseId() + "/assign").andReturn().getResponse().getStatus(),
                    () -> postAs(v2, BASE + "/" + s.caseId() + "/assign").andReturn().getResponse().getStatus());
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : pool.invokeAll(calls)) {
                codes.add(f.get());
            }
            assertThat(codes).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(requestStatus(s.requestId())).isEqualTo("IN_REVIEW");
    }

    @Test
    void approveClosesCaseApprovesRequestAndPublishesToDiscoveryView() throws Exception {
        TestUser v = users.createActiveUser(Role.VERIFIER);
        TestUser other = users.createActiveUser(Role.VERIFIER);
        Submitted s = submit();
        postAs(v, BASE + "/" + s.caseId() + "/assign").andExpect(status().isOk());

        decide(other, s.caseId(), "APPROVE", "Documents match the fee reference.", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CASE_NOT_ASSIGNED_TO_YOU"));

        decide(v, s.caseId(), "APPROVE", "Documents match the fee reference.", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.outcome").value("APPROVED"))
                .andExpect(jsonPath("$.canDecide").value(false))
                .andExpect(jsonPath("$.decisions.length()").value(1))
                .andExpect(jsonPath("$.decisions[0].decision").value("APPROVE"))
                .andExpect(jsonPath("$.decisions[0].decidedBy.id").value(v.id().toString()))
                .andExpect(jsonPath("$.request.status").value("APPROVED"));

        assertThat(requestStatus(s.requestId())).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM discovery_public_requests WHERE id = ?", Long.class, s.requestId()))
                .isEqualTo(1);
        assertThat(auditCount("VERIFICATION_DECIDED", s.caseId())).isEqualTo(1);
        assertThat(auditCount("CASE_CLOSED", s.caseId())).isEqualTo(1);
        assertThat(pageIds(v, "CLOSED", 50)).contains(s.caseId().toString());

        // one decision per case: deciding again is refused and nothing changes
        decide(v, s.caseId(), "REJECT", "Changed my mind about this one.", "No.")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        assertThat(jdbc.queryForObject(
                        "SELECT count(*) FROM verification_decisions WHERE case_id = ?", Long.class, s.caseId()))
                .isEqualTo(1);
    }

    @Test
    void decisionValidationRequiresRationaleAndMessage() throws Exception {
        TestUser v = users.createActiveUser(Role.VERIFIER);
        Submitted s = submit();
        postAs(v, BASE + "/" + s.caseId() + "/assign").andExpect(status().isOk());

        decide(v, s.caseId(), "REJECT", "Fee reference does not match.", null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("messageToRecipient"));
        decide(v, s.caseId(), "REQUEST_CHANGES", "Fee reference does not match.", "   ")
                .andExpect(status().isUnprocessableEntity());
        decide(v, s.caseId(), "APPROVE", "short", null).andExpect(status().isUnprocessableEntity());

        // nothing was recorded or changed
        assertThat(requestStatus(s.requestId())).isEqualTo("IN_REVIEW");
        getAs(v, BASE + "/" + s.caseId()).andExpect(jsonPath("$.status").value("ASSIGNED"));
    }

    @Test
    void requestChangesThenResubmitOpensSecondCaseWithBothDecisions() throws Exception {
        TestUser v = users.createActiveUser(Role.VERIFIER);
        Submitted s = submit();
        postAs(v, BASE + "/" + s.caseId() + "/assign").andExpect(status().isOk());
        decide(v, s.caseId(), "REQUEST_CHANGES", "Invoice is illegible, please upload again.", "Please upload a clearer invoice.")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.request.status").value("CHANGES_REQUESTED"));

        // the recipient edits and resubmits (education module's job; simulated with the fixture + event)
        jdbc.update("UPDATE funding_requests SET status = 'SUBMITTED' WHERE id = ?", s.requestId());
        UUID second = publishSubmitted(s.recipient().id(), s.requestId(), 2);
        assertThat(second).isNotEqualTo(s.caseId());

        postAs(v, BASE + "/" + second + "/assign").andExpect(status().isOk());
        decide(v, second, "APPROVE", "New invoice is clear and matches.", null).andExpect(status().isOk());

        getAs(v, BASE + "/" + second)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionNumber").value(2))
                .andExpect(jsonPath("$.decisions.length()").value(2))
                .andExpect(jsonPath("$.decisions[0].decision").value("APPROVE"))
                .andExpect(jsonPath("$.decisions[1].decision").value("REQUEST_CHANGES"))
                .andExpect(jsonPath("$.decisions[1].messageToRecipient").value("Please upload a clearer invoice."));
        assertThat(requestStatus(s.requestId())).isEqualTo("APPROVED");
    }

    @Test
    void withdrawalClosesOpenAndAssignedCases() throws Exception {
        TestUser v = users.createActiveUser(Role.VERIFIER);
        Submitted open = submit();
        Submitted assigned = submit();
        postAs(v, BASE + "/" + assigned.caseId() + "/assign").andExpect(status().isOk());

        events.publishEvent(new EducationRequestWithdrawn(open.requestId(), open.recipient().id(), Instant.now()));
        events.publishEvent(new EducationRequestWithdrawn(assigned.requestId(), assigned.recipient().id(), Instant.now()));

        for (Submitted s : List.of(open, assigned)) {
            getAs(v, BASE + "/" + s.caseId())
                    .andExpect(jsonPath("$.status").value("CLOSED"))
                    .andExpect(jsonPath("$.outcome").value("WITHDRAWN"));
            assertThat(auditCount("CASE_CLOSED", s.caseId())).isEqualTo(1);
        }
        assertThat(pageIds(v, "UNASSIGNED", 50)).doesNotContain(open.caseId().toString());
        assertThat(pageIds(v, "CLOSED", 50)).contains(open.caseId().toString(), assigned.caseId().toString());
    }

    @Test
    void nonStaffCannotUseAdminEndpoints() throws Exception {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        TestUser donor = users.createActiveUser(Role.DONOR);
        Submitted s = submit();
        for (TestUser u : List.of(recipient, donor)) {
            getAs(u, BASE).andExpect(status().isForbidden());
            getAs(u, BASE + "/" + s.caseId()).andExpect(status().isForbidden());
            postAs(u, BASE + "/" + s.caseId() + "/assign").andExpect(status().isForbidden());
        }
        mockMvc.perform(get(BASE)).andExpect(status().isUnauthorized());
    }

    @Test
    void viewUrlIsIssuedAndAuditedPerDocumentAndForeignDocumentsAreNotFound() throws Exception {
        TestUser v = users.createActiveUser(Role.VERIFIER);
        Submitted s = submit();
        Submitted other = submit();
        UUID doc = fixtures.insertDocument(s.requestId(), s.recipient().id(), "UPLOADED", "a".repeat(64));
        UUID foreign = fixtures.insertDocument(other.requestId(), other.recipient().id(), "UPLOADED", "b".repeat(64));

        getAs(v, BASE + "/" + s.caseId())
                .andExpect(jsonPath("$.request.documents.length()").value(1))
                .andExpect(jsonPath("$.request.documents[0].id").value(doc.toString()))
                .andExpect(jsonPath("$.request.documents[0].storageKey").doesNotExist());

        for (int i = 1; i <= 2; i++) {
            postAs(v, BASE + "/" + s.caseId() + "/documents/" + doc + "/view-url")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.url").isNotEmpty())
                    .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                            .string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
            assertThat(auditCount("DOCUMENT_VIEW_URL_ISSUED", doc)).isEqualTo(i);
        }
        postAs(v, BASE + "/" + s.caseId() + "/documents/" + foreign + "/view-url").andExpect(status().isNotFound());
        postAs(v, BASE + "/" + UUID.randomUUID() + "/documents/" + doc + "/view-url").andExpect(status().isNotFound());
        assertThat(auditCount("DOCUMENT_VIEW_URL_ISSUED", foreign)).isZero();
    }

    @Test
    void verifierCannotReviewOwnRequest() throws Exception {
        TestUser both = users.createActiveUser(Role.RECIPIENT, Role.VERIFIER);
        UUID requestId = fixtures.insertRequest(both.id(), "SUBMITTED");
        UUID caseId = publishSubmitted(both.id(), requestId, 1);

        postAs(both, BASE + "/" + caseId + "/assign")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"));
        assertThat(requestStatus(requestId)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT status FROM verification_cases WHERE id = ?", String.class, caseId))
                .isEqualTo("OPEN");
        // the refusal is audited and survives the failed request
        assertThat(auditCount("ACCESS_DENIED", caseId)).isEqualTo(1);

        // even if the case were somehow assigned to them, a decision is refused
        jdbc.update("UPDATE verification_cases SET status = 'ASSIGNED', assigned_to = ? WHERE id = ?", both.id(), caseId);
        jdbc.update("UPDATE funding_requests SET status = 'IN_REVIEW' WHERE id = ?", requestId);
        decide(both, caseId, "APPROVE", "I would like to approve my own.", null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SELF_REVIEW_FORBIDDEN"));
        assertThat(requestStatus(requestId)).isEqualTo("IN_REVIEW");
        getAs(both, BASE + "/" + caseId).andExpect(jsonPath("$.canDecide").value(false));
    }

    @Test
    void queuePagesWithCursorOldestFirstAndRejectsBadInput() throws Exception {
        TestUser v = users.createActiveUser(Role.VERIFIER);
        List<String> mine = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            mine.add(submit().caseId().toString());
        }
        List<String> ids = pageIds(v, "UNASSIGNED", 2);
        assertThat(ids).doesNotHaveDuplicates().containsAll(mine);
        assertThat(ids.stream().filter(mine::contains).toList()).containsExactlyElementsOf(mine);

        MvcResult first = getAs(v, BASE + "?queue=UNASSIGNED&limit=2")
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].requestTitle").isNotEmpty())
                .andExpect(jsonPath("$.items[0].amount.currency").value("INR"))
                .andReturn();
        assertThat(JSON.readTree(first.getResponse().getContentAsString()).get("nextCursor").isNull())
                .isFalse();

        getAs(v, BASE + "?queue=NOPE").andExpect(status().isBadRequest());
        getAs(v, BASE + "?cursor=garbage").andExpect(status().isBadRequest());
        getAs(v, BASE + "?limit=0").andExpect(status().isUnprocessableEntity());
    }
}
