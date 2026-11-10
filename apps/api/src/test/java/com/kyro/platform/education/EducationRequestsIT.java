package com.kyro.platform.education;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.kyro.platform.common.security.Role;
import com.kyro.platform.common.storage.ObjectStorage;
import com.kyro.platform.common.storage.PresignedUrl;
import com.kyro.platform.education.application.port.EducationRequestSubmitted;
import com.kyro.platform.education.application.port.EducationRequestWithdrawn;
import com.kyro.platform.education.application.port.EducationReviewFacade;
import com.kyro.platform.education.application.port.ReviewSnapshot;
import com.kyro.platform.education.application.port.VerificationOutcome;
import com.kyro.platform.support.Csrf;
import com.kyro.platform.support.DbFixtures;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import com.kyro.platform.support.TestUsers.TestUser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.MvcResult;

@RecordApplicationEvents
class EducationRequestsIT extends IntegrationTestBase {

    private static final byte[] PDF = "%PDF-1.4 fictional invoice for tests".getBytes(StandardCharsets.UTF_8);
    private static final byte[] PNG = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};

    @Autowired TestUsers users;
    @Autowired DbFixtures fixtures;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectStorage storage;
    @Autowired EducationReviewFacade facade;
    @Autowired ApplicationEvents events;

    // ---- helpers ----

    private static String createBody(String feeRef, String institution) {
        return """
                {"title":"Semester 3 tuition fee","expenseType":"TUITION","amount":{"amountMinor":4500000,"currency":"INR"},
                 "story":"A fictional story that is comfortably longer than fifty characters for the completeness rule.",
                 "institutionName":"%s","institutionCity":"Pune","courseName":"B.Tech Computer Science",
                 "feeReference":"%s","dueDate":"%s"}
                """.formatted(institution, feeRef, LocalDate.now().plusDays(30));
    }

    private UUID create(TestUser u, String feeRef, String institution) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/education/requests")
                        .session(u.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(feeRef, institution)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = JsonPath.read(res.getResponse().getContentAsString(), "$.id");
        assertThat(res.getResponse().getHeader("Location")).endsWith("/api/v1/education/requests/" + id);
        return UUID.fromString(id);
    }

    private UUID create(TestUser u) throws Exception {
        return create(u, "INV-" + UUID.randomUUID(), "Institute " + UUID.randomUUID());
    }

    private UUID authorize(TestUser u, UUID rid, String declaredType, long size) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/education/requests/{r}/documents/upload-url", rid)
                        .session(u.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"TUITION_INVOICE\",\"fileName\":\"invoice.bin\",\"contentType\":\"" + declaredType
                                + "\",\"sizeBytes\":" + size + "}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("/api/v1/education/requests/" + rid + "/documents/")))
                .andExpect(jsonPath("$.method").value("PUT"))
                .andExpect(jsonPath("$.document.status").value("PENDING_UPLOAD"))
                .andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(body).doesNotContain("storageKey").doesNotContain("requests/" + rid + "/documents/\"");
        UUID did = UUID.fromString(JsonPath.read(body, "$.document.id"));
        String url = JsonPath.read(body, "$.uploadUrl");
        java.util.Map<String, String> headers = JsonPath.read(body, "$.requiredHeaders");
        putObject(url, headers, declaredType, size);
        return did;
    }

    private static void putObject(String url, java.util.Map<String, String> headers, String type, long size) throws Exception {
        // the object body is supplied by the caller through lastBytes
        byte[] bytes = lastBytes.get();
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(url)).PUT(HttpRequest.BodyPublishers.ofByteArray(bytes));
        headers.forEach(req::header);
        HttpResponse<String> r = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                .send(req.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
    }

    private static final ThreadLocal<byte[]> lastBytes = new ThreadLocal<>();

    /** authorize + PUT + complete; returns the complete() result. */
    private MvcResult upload(TestUser u, UUID rid, byte[] bytes, String declaredType) throws Exception {
        lastBytes.set(bytes);
        UUID did = authorize(u, rid, declaredType, bytes.length);
        return mockMvc.perform(post("/api/v1/education/requests/{r}/documents/{d}/complete", rid, did)
                        .session(u.session())
                        .with(Csrf.token()))
                .andReturn();
    }

    private UUID uploadOk(TestUser u, UUID rid, byte[] bytes) throws Exception {
        MvcResult r = upload(u, rid, bytes, "application/pdf");
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return UUID.fromString(JsonPath.read(r.getResponse().getContentAsString(), "$.id"));
    }

    private long version(UUID rid) {
        return jdbc.queryForObject("SELECT version FROM funding_requests WHERE id = ?", Long.class, rid);
    }

    private int auditCount(String action, UUID entityId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = ? AND entity_id = ?", Integer.class, action, entityId);
    }

    private String statusOf(UUID rid) {
        return jdbc.queryForObject("SELECT status FROM funding_requests WHERE id = ?", String.class, rid);
    }

    // ---- create / read / update ----

    @Test
    void createReadListAndPatchWithOptimisticLocking() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        UUID rid = create(u);

        mockMvc.perform(get("/api/v1/education/requests/{r}", rid).session(u.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.publicDisplayName").value("Test Recipient"))
                .andExpect(jsonPath("$.statusHistory[0].toStatus").value("DRAFT"))
                .andExpect(jsonPath("$.version").value(0));

        mockMvc.perform(get("/api/v1/me/education/requests").session(u.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").value(rid.toString()))
                .andExpect(jsonPath("$.items[0].documentCount").value(0));

        mockMvc.perform(patch("/api/v1/education/requests/{r}", rid)
                        .session(u.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"title\":\"Updated title\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated title"))
                .andExpect(jsonPath("$.institutionCity").value("Pune"))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(auditCount("REQUEST_UPDATED", rid)).isEqualTo(1);
        assertThat(auditCount("REQUEST_CREATED", rid)).isEqualTo(1);

        mockMvc.perform(patch("/api/v1/education/requests/{r}", rid)
                        .session(u.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"title\":\"Stale title\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("STALE_VERSION"));
    }

    @Test
    void createRejectsNonInrCurrency() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        mockMvc.perform(post("/api/v1/education/requests")
                        .session(u.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("X-1", "Inst").replace("INR", "USD")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("amount.currency")));
    }

    // ---- submit / documents ----

    @Test
    void submitWithoutDocumentIsIncomplete() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        UUID rid = create(u);
        mockMvc.perform(post("/api/v1/education/requests/{r}/submit", rid).session(u.session()).with(Csrf.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("REQUEST_INCOMPLETE"))
                .andExpect(jsonPath("$.errors[*].field").value(hasItem("documents")));
        assertThat(statusOf(rid)).isEqualTo("DRAFT");
    }

    @Test
    void uploadRoundTripSubmitAndEventAndLock() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        UUID rid = create(u);
        UUID did = uploadOk(u, rid, PDF);

        String expectedHash = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(PDF));
        assertThat(jdbc.queryForObject("SELECT sha256 FROM request_documents WHERE id = ?", String.class, did)).isEqualTo(expectedHash);
        assertThat(auditCount("DOCUMENT_UPLOADED", did)).isEqualTo(1);
        assertThat(auditCount("DOCUMENT_UPLOAD_AUTHORIZED", did)).isEqualTo(1);

        mockMvc.perform(get("/api/v1/education/requests/{r}/documents", rid).session(u.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].status").value("UPLOADED"));

        mockMvc.perform(post("/api/v1/education/requests/{r}/submit", rid).session(u.session()).with(Csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.submissionCount").value(1))
                .andExpect(jsonPath("$.editable").value(false))
                .andExpect(jsonPath("$.submittedAt").isNotEmpty());

        List<EducationRequestSubmitted> submitted = events.stream(EducationRequestSubmitted.class)
                .filter(e -> e.requestId().equals(rid))
                .toList();
        assertThat(submitted).hasSize(1);
        assertThat(submitted.get(0).submissionNumber()).isEqualTo(1);
        assertThat(submitted.get(0).recipientUserId()).isEqualTo(u.id());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM funding_request_status_history WHERE request_id = ?", Integer.class, rid))
                .isEqualTo(2);
        assertThat(auditCount("REQUEST_STATUS_CHANGED", rid)).isEqualTo(1);

        // no edits, uploads or deletes once submitted
        mockMvc.perform(patch("/api/v1/education/requests/{r}", rid)
                        .session(u.session()).with(Csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version(rid) + ",\"title\":\"Sneaky change\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));
        mockMvc.perform(delete("/api/v1/education/requests/{r}/documents/{d}", rid, did).session(u.session()).with(Csrf.token()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/education/requests/{r}/documents/upload-url", rid)
                        .session(u.session()).with(Csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"OTHER\",\"fileName\":\"a.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":10}"))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/api/v1/education/requests/{r}/submit", rid).session(u.session()).with(Csrf.token()))
                .andExpect(status().isConflict());
    }

    @Test
    void fakePdfWithPngBytesIsRejectedAndObjectRemoved() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        UUID rid = create(u);
        MvcResult res = upload(u, rid, PNG, "application/pdf");
        assertThat(res.getResponse().getStatus()).isEqualTo(422);
        assertThat(res.getResponse().getContentAsString()).contains("DOCUMENT_INVALID");
        String key = jdbc.queryForObject("SELECT storage_key FROM request_documents WHERE request_id = ?", String.class, rid);
        assertThat(jdbc.queryForObject("SELECT status FROM request_documents WHERE request_id = ?", String.class, rid))
                .isEqualTo("REJECTED");
        assertThat(storage.head(key)).isEmpty();
        UUID did = jdbc.queryForObject("SELECT id FROM request_documents WHERE request_id = ?", UUID.class, rid);
        assertThat(auditCount("DOCUMENT_REJECTED", did)).isEqualTo(1);
    }

    @Test
    void completeWithoutUploadIsRejected() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        UUID rid = create(u);
        MvcResult auth = mockMvc.perform(post("/api/v1/education/requests/{r}/documents/upload-url", rid)
                        .session(u.session()).with(Csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"OTHER\",\"fileName\":\"a.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":10}"))
                .andExpect(status().isCreated())
                .andReturn();
        String did = JsonPath.read(auth.getResponse().getContentAsString(), "$.document.id");
        mockMvc.perform(post("/api/v1/education/requests/{r}/documents/{d}/complete", rid, did).session(u.session()).with(Csrf.token()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOCUMENT_INVALID"));
    }

    @Test
    void uploadAuthorizationValidatesTypeSizeAndLimit() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        UUID rid = create(u);
        mockMvc.perform(post("/api/v1/education/requests/{r}/documents/upload-url", rid)
                        .session(u.session()).with(Csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"OTHER\",\"fileName\":\"a.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":10485761}"))
                .andExpect(status().isUnprocessableEntity());
        for (int i = 0; i < 10; i++) {
            fixtures.insertDocument(rid, u.id(), "UPLOADED", "h" + i + UUID.randomUUID());
        }
        mockMvc.perform(post("/api/v1/education/requests/{r}/documents/upload-url", rid)
                        .session(u.session()).with(Csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"documentType\":\"OTHER\",\"fileName\":\"a.pdf\",\"contentType\":\"application/pdf\",\"sizeBytes\":10}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("DOCUMENT_LIMIT_EXCEEDED"));
    }

    @Test
    void deleteDocumentSoftDeletesAndRemovesObject() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        UUID rid = create(u);
        UUID did = uploadOk(u, rid, PDF);
        String key = jdbc.queryForObject("SELECT storage_key FROM request_documents WHERE id = ?", String.class, did);
        assertThat(storage.head(key)).isPresent();
        mockMvc.perform(delete("/api/v1/education/requests/{r}/documents/{d}", rid, did).session(u.session()).with(Csrf.token()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT status FROM request_documents WHERE id = ?", String.class, did)).isEqualTo("DELETED");
        assertThat(storage.head(key)).isEmpty();
        assertThat(auditCount("DOCUMENT_DELETED", did)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/education/requests/{r}/documents", rid).session(u.session()))
                .andExpect(jsonPath("$.items").isEmpty());
        mockMvc.perform(delete("/api/v1/education/requests/{r}/documents/{d}", rid, did).session(u.session()).with(Csrf.token()))
                .andExpect(status().isNotFound());
    }

    // ---- authorization ----

    @Test
    void otherRecipientGets404AndAccessDeniedAuditAndDonorGets403() throws Exception {
        TestUser owner = users.createActiveUser(Role.RECIPIENT);
        TestUser other = users.createActiveUser(Role.RECIPIENT);
        TestUser donor = users.createActiveUser(Role.DONOR);
        UUID rid = create(owner);
        UUID did = uploadOk(owner, rid, PDF);

        mockMvc.perform(get("/api/v1/education/requests/{r}", rid).session(other.session())).andExpect(status().isNotFound());
        assertThat(auditCount("ACCESS_DENIED", rid)).isEqualTo(1);
        mockMvc.perform(get("/api/v1/education/requests/{r}/documents", rid).session(other.session())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/education/requests/{r}/documents/{d}/complete", rid, did).session(other.session()).with(Csrf.token()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/v1/education/requests/{r}/documents/{d}", rid, did).session(other.session()).with(Csrf.token()))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/api/v1/education/requests/{r}", rid)
                        .session(other.session()).with(Csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"title\":\"Hijacked title\"}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/education/requests/{r}/withdraw", rid).session(other.session()).with(Csrf.token()))
                .andExpect(status().isNotFound());
        assertThat(auditCount("ACCESS_DENIED", rid)).isEqualTo(6);
        assertThat(statusOf(rid)).isEqualTo("DRAFT");
        // unknown id: also 404, no audit
        mockMvc.perform(get("/api/v1/education/requests/{r}", UUID.randomUUID()).session(other.session())).andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/education/requests/{r}", rid).session(donor.session())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/education/requests").session(donor.session())).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/education/requests").session(donor.session()).with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON).content(createBody("A", "B")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/education/requests/{r}", rid)).andExpect(status().isUnauthorized());
    }

    // ---- lifecycle ----

    @Test
    void approvedRequestCannotBeEditedAndWithdrawRemovesItFromDiscoveryView() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        UUID rid = fixtures.insertRequest(u.id(), "APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM discovery_public_requests WHERE id = ?", Integer.class, rid)).isEqualTo(1);

        mockMvc.perform(patch("/api/v1/education/requests/{r}", rid)
                        .session(u.session()).with(Csrf.token()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":0,\"amount\":{\"amountMinor\":9999999,\"currency\":\"INR\"}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        mockMvc.perform(post("/api/v1/education/requests/{r}/withdraw", rid).session(u.session()).with(Csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("WITHDRAWN"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM discovery_public_requests WHERE id = ?", Integer.class, rid)).isEqualTo(0);
        assertThat(events.stream(EducationRequestWithdrawn.class).filter(e -> e.requestId().equals(rid)).count()).isEqualTo(1);

        mockMvc.perform(post("/api/v1/education/requests/{r}/withdraw", rid).session(u.session()).with(Csrf.token()))
                .andExpect(status().isConflict());
    }

    @Test
    void facadeDecisionsDriveTheLifecycleWithHistoryAndResubmission() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        TestUser verifier = users.createActiveUser(Role.VERIFIER);
        UUID rid = create(u);
        uploadOk(u, rid, PDF);
        mockMvc.perform(post("/api/v1/education/requests/{r}/submit", rid).session(u.session()).with(Csrf.token()))
                .andExpect(status().isOk());

        // decisions need IN_REVIEW first
        org.junit.jupiter.api.Assertions.assertThrows(
                com.kyro.platform.common.errors.ConflictException.class,
                () -> facade.applyDecision(rid, VerificationOutcome.APPROVED, verifier.id(), null));

        facade.markInReview(rid, verifier.id());
        assertThat(statusOf(rid)).isEqualTo("IN_REVIEW");
        org.junit.jupiter.api.Assertions.assertThrows(
                com.kyro.platform.common.errors.ConflictException.class, () -> facade.markInReview(rid, verifier.id()));

        facade.applyDecision(rid, VerificationOutcome.CHANGES_REQUESTED, verifier.id(), "Please upload a clearer invoice.");
        mockMvc.perform(get("/api/v1/education/requests/{r}", rid).session(u.session()))
                .andExpect(jsonPath("$.status").value("CHANGES_REQUESTED"))
                .andExpect(jsonPath("$.editable").value(true))
                .andExpect(jsonPath("$.reviewerFeedback").value("Please upload a clearer invoice."));

        mockMvc.perform(post("/api/v1/education/requests/{r}/submit", rid).session(u.session()).with(Csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.submissionCount").value(2));
        assertThat(events.stream(EducationRequestSubmitted.class).filter(e -> e.requestId().equals(rid)).map(e -> e.submissionNumber()))
                .containsExactly(1, 2);

        facade.markInReview(rid, verifier.id());
        facade.applyDecision(rid, VerificationOutcome.APPROVED, verifier.id(), null);
        assertThat(statusOf(rid)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT approved_at IS NOT NULL FROM funding_requests WHERE id = ?", Boolean.class, rid)).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM discovery_public_requests WHERE id = ?", Integer.class, rid)).isEqualTo(1);

        ReviewSnapshot snap = facade.getReviewSnapshot(rid);
        assertThat(snap.status()).isEqualTo("APPROVED");
        assertThat(snap.editable()).isFalse();
        assertThat(snap.documents()).hasSize(1);
        assertThat(snap.feeReference()).isNotBlank();
        assertThat(snap.statusHistory().stream().map(h -> h.toStatus()))
                .containsExactly("DRAFT", "SUBMITTED", "IN_REVIEW", "CHANGES_REQUESTED", "SUBMITTED", "IN_REVIEW", "APPROVED");
        assertThat(jdbc.queryForObject(
                        "SELECT changed_by FROM funding_request_status_history WHERE request_id = ? AND to_status = 'APPROVED'",
                        UUID.class, rid))
                .isEqualTo(verifier.id());
        assertThat(auditCount("REQUEST_STATUS_CHANGED", rid)).isEqualTo(7 - 1);

        var summaries = facade.getReviewSummaries(List.of(rid, UUID.randomUUID()));
        assertThat(summaries).containsOnlyKeys(rid);
        assertThat(summaries.get(rid).recipientPublicName()).isEqualTo("Test Recipient");
        assertThat(summaries.get(rid).amountMinor()).isEqualTo(4_500_000L);

        org.junit.jupiter.api.Assertions.assertThrows(
                com.kyro.platform.common.errors.NotFoundException.class, () -> facade.getReviewSnapshot(UUID.randomUUID()));
    }

    @Test
    void facadeIssuesDocumentViewUrlAndAudits() throws Exception {
        TestUser u = users.createActiveUser(Role.RECIPIENT);
        TestUser verifier = users.createActiveUser(Role.VERIFIER);
        UUID rid = create(u);
        UUID did = uploadOk(u, rid, PDF);

        PresignedUrl url = facade.issueDocumentViewUrl(rid, did, verifier.id());
        HttpResponse<byte[]> got = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(HttpRequest.newBuilder(URI.create(url.url())).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(got.statusCode()).isEqualTo(200);
        assertThat(got.body()).isEqualTo(PDF);
        assertThat(auditCount("DOCUMENT_VIEW_URL_ISSUED", did)).isEqualTo(1);

        UUID otherRequest = create(users.createActiveUser(Role.RECIPIENT));
        org.junit.jupiter.api.Assertions.assertThrows(
                com.kyro.platform.common.errors.NotFoundException.class,
                () -> facade.issueDocumentViewUrl(otherRequest, did, verifier.id()));
    }

    @Test
    void duplicateIndicatorsFlagSharedDocumentHashAndFeeReference() throws Exception {
        TestUser a = users.createActiveUser(Role.RECIPIENT);
        TestUser b = users.createActiveUser(Role.RECIPIENT);
        String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        UUID ra = create(a, "INV-" + suffix, "Shared Institute " + suffix);
        UUID rb = create(b, "inv " + suffix.toLowerCase(), "SHARED institute " + suffix);
        byte[] shared = ("%PDF-1.4 shared " + suffix).getBytes(StandardCharsets.UTF_8);
        uploadOk(a, ra, shared);
        uploadOk(b, rb, shared);

        ReviewSnapshot snap = facade.getReviewSnapshot(rb);
        assertThat(snap.duplicateIndicators()).anySatisfy(d -> {
            assertThat(d.kind()).isEqualTo("DOCUMENT_HASH_MATCH");
            assertThat(d.otherRequestId()).isEqualTo(ra);
        });
        assertThat(snap.duplicateIndicators()).anySatisfy(d -> {
            assertThat(d.kind()).isEqualTo("FEE_REFERENCE_MATCH");
            assertThat(d.otherRequestId()).isEqualTo(ra);
            assertThat(d.otherRequestStatus()).isEqualTo("DRAFT");
        });

        // a withdrawn request no longer counts for the fee-reference rule
        mockMvc.perform(post("/api/v1/education/requests/{r}/withdraw", ra).session(a.session()).with(Csrf.token())).andExpect(status().isOk());
        assertThat(facade.getReviewSnapshot(rb).duplicateIndicators().stream().map(d -> d.kind()))
                .doesNotContain("FEE_REFERENCE_MATCH");

        // a request with unique data has none
        UUID rc = create(users.createActiveUser(Role.RECIPIENT));
        assertThat(facade.getReviewSnapshot(rc).duplicateIndicators()).isEmpty();
    }
}
