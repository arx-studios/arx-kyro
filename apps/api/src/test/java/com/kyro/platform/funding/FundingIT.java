package com.kyro.platform.funding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyro.platform.common.security.Role;
import com.kyro.platform.support.Csrf;
import com.kyro.platform.support.DbFixtures;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import com.kyro.platform.support.TestUsers.TestUser;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class FundingIT extends IntegrationTestBase {

    private static final long TARGET = 4_500_000L;

    @Autowired
    TestUsers users;

    @Autowired
    DbFixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JsonMapper mapper;

    private static String body(long amount, String currency, boolean ack) {
        return "{\"amount\":{\"amountMinor\":" + amount + ",\"currency\":\"" + currency + "\"},\"acknowledgeNonBinding\":" + ack + "}";
    }

    private static String key() {
        return "key-" + UUID.randomUUID();
    }

    private MockHttpServletRequestBuilder pledgeReq(TestUser donor, UUID request, String idemKey, String json) {
        MockHttpServletRequestBuilder b = post("/api/v1/funding/requests/{id}/pledges", request)
                .with(Csrf.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
        if (donor != null) {
            b.session(donor.session());
        }
        if (idemKey != null) {
            b.header("Idempotency-Key", idemKey);
        }
        return b;
    }

    private UUID pledge(TestUser donor, UUID request, long amount) throws Exception {
        MvcResult r = mockMvc.perform(pledgeReq(donor, request, key(), body(amount, "INR", true)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(mapper.readTree(r.getResponse().getContentAsString()).get("id").asText());
    }

    private int audit(String action, UUID entity) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM audit_events WHERE action = ? AND entity_id = ?", Integer.class, action, entity);
    }

    private JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsString());
    }

    @Test
    void createReplayAndIdempotencyKeyReuse() throws Exception {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        TestUser donor = users.createActiveUser(Role.DONOR);
        UUID rid = fixtures.insertRequest(recipient.id(), "APPROVED");
        String k = key();

        MvcResult created = mockMvc.perform(pledgeReq(donor, rid, k, body(100_000, "INR", true)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.nonBinding").value(true))
                .andExpect(jsonPath("$.amount.amountMinor").value(100_000))
                .andReturn();
        String id = json(created).get("id").asText();
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/funding/pledges/" + id);
        assertThat(audit("PLEDGE_CREATED", UUID.fromString(id))).isEqualTo(1);

        mockMvc.perform(pledgeReq(donor, rid, k, body(100_000, "INR", true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id));
        assertThat(audit("PLEDGE_CREATED", UUID.fromString(id))).isEqualTo(1);

        mockMvc.perform(pledgeReq(donor, rid, k, body(200_000, "INR", true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        // same key on another request is also a reuse
        UUID other = fixtures.insertRequest(recipient.id(), "APPROVED");
        mockMvc.perform(pledgeReq(donor, other, k, body(100_000, "INR", true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));

        mockMvc.perform(pledgeReq(donor, rid, null, body(100_000, "INR", true)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        mockMvc.perform(pledgeReq(donor, rid, "short", body(100_000, "INR", true)))
                .andExpect(status().isUnprocessableEntity());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pledges WHERE donor_user_id = ?", Integer.class, donor.id()))
                .isEqualTo(1);
    }

    @Test
    void rejections() throws Exception {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        TestUser donor = users.createActiveUser(Role.DONOR);
        TestUser donor2 = users.createActiveUser(Role.DONOR);
        UUID rid = fixtures.insertRequest(recipient.id(), "APPROVED");

        pledge(donor, rid, 1_000_000);
        mockMvc.perform(pledgeReq(donor, rid, key(), body(100_000, "INR", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACTIVE_PLEDGE_EXISTS"));

        mockMvc.perform(pledgeReq(donor2, rid, key(), body(TARGET, "INR", true)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PLEDGE_EXCEEDS_REMAINING"))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("3500000")));

        mockMvc.perform(pledgeReq(donor2, rid, key(), body(100_000, "USD", true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_CURRENCY"));
        mockMvc.perform(pledgeReq(donor2, rid, key(), body(100_000, "INR", false)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(pledgeReq(donor2, rid, key(), body(99, "INR", true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        // dual-role recipient cannot pledge to their own request
        TestUser both = users.createActiveUser(Role.RECIPIENT, Role.DONOR);
        UUID own = fixtures.insertRequest(both.id(), "APPROVED");
        mockMvc.perform(pledgeReq(both, own, key(), body(100_000, "INR", true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PLEDGE_NOT_ALLOWED"));

        UUID pastDue = fixtures.insertRequest(recipient.id(), "APPROVED", "Past due", Instant.now(), LocalDate.now().minusDays(1));
        mockMvc.perform(pledgeReq(donor2, pastDue, key(), body(100_000, "INR", true)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PLEDGE_NOT_ALLOWED"));

        UUID submitted = fixtures.insertRequest(recipient.id(), "SUBMITTED");
        mockMvc.perform(pledgeReq(donor2, submitted, key(), body(100_000, "INR", true))).andExpect(status().isNotFound());
        mockMvc.perform(pledgeReq(donor2, UUID.randomUUID(), key(), body(100_000, "INR", true))).andExpect(status().isNotFound());

        UUID held = fixtures.insertRequest(recipient.id(), "APPROVED");
        jdbc.update("INSERT INTO request_moderation_holds (request_id, reason, placed_by) VALUES (?, 'test hold', ?)", held, recipient.id());
        mockMvc.perform(pledgeReq(donor2, held, key(), body(100_000, "INR", true))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/funding/requests/{id}/summary", held)).andExpect(status().isNotFound());
    }

    @Test
    void roles() throws Exception {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        UUID rid = fixtures.insertRequest(recipient.id(), "APPROVED");
        mockMvc.perform(pledgeReq(recipient, rid, key(), body(100_000, "INR", true))).andExpect(status().isForbidden());
        mockMvc.perform(pledgeReq(null, rid, key(), body(100_000, "INR", true))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/me/funding/pledges").session(recipient.session())).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/me/funding/pledges")).andExpect(status().isUnauthorized());
    }

    @Test
    void concurrentPledgesCannotExceedTheTarget() throws Exception {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        UUID rid = fixtures.insertRequest(recipient.id(), "APPROVED");
        List<TestUser> donors = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            donors.add(users.createActiveUser(Role.DONOR));
        }
        CountDownLatch ready = new CountDownLatch(donors.size());
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(donors.size());
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (TestUser d : donors) {
                results.add(pool.submit(() -> {
                    ready.countDown();
                    go.await();
                    return mockMvc.perform(pledgeReq(d, rid, key(), body(TARGET, "INR", true)))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            ready.await();
            go.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : results) {
                codes.add(f.get());
            }
            assertThat(codes.stream().filter(c -> c == 201).count()).isEqualTo(1);
            assertThat(codes.stream().filter(c -> c == 409).count()).isEqualTo(donors.size() - 1);
        } finally {
            pool.shutdownNow();
        }
        Long sum = jdbc.queryForObject(
                "SELECT COALESCE(sum(amount_minor),0) FROM pledges WHERE request_id = ? AND status = 'ACTIVE'", Long.class, rid);
        assertThat(sum).isLessThanOrEqualTo(TARGET).isEqualTo(TARGET);
    }

    @Test
    void concurrentIdempotentRetryCreatesOnePledge() throws Exception {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        TestUser donor = users.createActiveUser(Role.DONOR);
        UUID rid = fixtures.insertRequest(recipient.id(), "APPROVED");
        String k = key();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                results.add(pool.submit(() -> {
                    go.await();
                    return mockMvc.perform(pledgeReq(donor, rid, k, body(500_000, "INR", true)))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            go.countDown();
            List<Integer> codes = List.of(results.get(0).get(), results.get(1).get());
            assertThat(codes).containsExactlyInAnyOrder(201, 200);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pledges WHERE request_id = ?", Integer.class, rid)).isEqualTo(1);
    }

    @Test
    void cancelAndObjectAccess() throws Exception {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        TestUser donor = users.createActiveUser(Role.DONOR);
        TestUser other = users.createActiveUser(Role.DONOR);
        UUID rid = fixtures.insertRequest(recipient.id(), "APPROVED");
        UUID pid = pledge(donor, rid, 700_000);

        mockMvc.perform(get("/api/v1/funding/pledges/{id}", pid).session(other.session())).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/funding/pledges/{id}/cancel", pid).session(other.session()).with(Csrf.token()))
                .andExpect(status().isNotFound());
        assertThat(audit("ACCESS_DENIED", pid)).isEqualTo(2);
        mockMvc.perform(get("/api/v1/funding/pledges/{id}", UUID.randomUUID()).session(donor.session())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/funding/pledges/{id}", pid).session(donor.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/funding/pledges/{id}/cancel", pid).session(donor.session()).with(Csrf.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.cancelReason").value("DONOR_CANCELLED"))
                .andExpect(jsonPath("$.cancelledAt").exists());
        assertThat(audit("PLEDGE_CANCELLED", pid)).isEqualTo(1);
        mockMvc.perform(post("/api/v1/funding/pledges/{id}/cancel", pid).session(donor.session()).with(Csrf.token()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE_TRANSITION"));

        // capacity is released and the donor may pledge again
        UUID again = pledge(donor, rid, 800_000);
        assertThat(again).isNotEqualTo(pid);
    }

    @Test
    void myPledgesPagesNewestFirstAndHidesWithdrawnRequestDetails() throws Exception {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        TestUser donor = users.createActiveUser(Role.DONOR);
        List<UUID> pledges = new ArrayList<>();
        UUID heldRequest = null;
        for (int i = 0; i < 3; i++) {
            UUID rid = fixtures.insertRequest(recipient.id(), "APPROVED");
            pledges.add(pledge(donor, rid, 100_000 + i));
            heldRequest = rid;
        }
        jdbc.update("INSERT INTO request_moderation_holds (request_id, reason, placed_by) VALUES (?, 'hold', ?)", heldRequest, recipient.id());

        List<String> seen = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 5; guard++) {
            var req = get("/api/v1/me/funding/pledges").param("limit", "2").session(donor.session());
            if (cursor != null) {
                req.param("cursor", cursor);
            }
            MvcResult r = mockMvc.perform(req).andExpect(status().isOk()).andReturn();
            JsonNode b = json(r);
            for (JsonNode item : b.get("items")) {
                seen.add(item.get("pledge").get("id").asText());
                if (item.get("pledge").get("id").asText().equals(pledges.get(2).toString())) {
                    assertThat(item.get("request").isNull()).isTrue();
                } else {
                    assertThat(item.get("request").get("title").asText()).isNotBlank();
                }
            }
            if (b.get("nextCursor").isNull()) {
                break;
            }
            cursor = b.get("nextCursor").asText();
        }
        assertThat(seen).containsExactly(pledges.get(2).toString(), pledges.get(1).toString(), pledges.get(0).toString());
        mockMvc.perform(get("/api/v1/me/funding/pledges").param("cursor", "garbage").session(donor.session()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void withdrawingTheRequestCancelsActivePledges() throws Exception {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        TestUser d1 = users.createActiveUser(Role.DONOR);
        TestUser d2 = users.createActiveUser(Role.DONOR);
        UUID rid = fixtures.insertRequest(recipient.id(), "APPROVED");
        UUID p1 = pledge(d1, rid, 1_000_000);
        UUID p2 = pledge(d2, rid, 1_000_000);
        UUID cancelledEarly = pledge(users.createActiveUser(Role.DONOR), rid, 500_000);
        jdbc.update("UPDATE pledges SET status='CANCELLED', cancel_reason='DONOR_CANCELLED', cancelled_at=now() WHERE id = ?", cancelledEarly);

        mockMvc.perform(post("/api/v1/education/requests/{r}/withdraw", rid).session(recipient.session()).with(Csrf.token()))
                .andExpect(status().isOk());

        for (UUID p : List.of(p1, p2)) {
            assertThat(jdbc.queryForObject("SELECT status || ':' || cancel_reason FROM pledges WHERE id = ?", String.class, p))
                    .isEqualTo("CANCELLED:REQUEST_WITHDRAWN");
            assertThat(audit("PLEDGE_CANCELLED", p)).isEqualTo(1);
        }
        assertThat(jdbc.queryForObject("SELECT cancel_reason FROM pledges WHERE id = ?", String.class, cancelledEarly))
                .isEqualTo("DONOR_CANCELLED");
        mockMvc.perform(get("/api/v1/funding/pledges/{id}", p1).session(d1.session()))
                .andExpect(jsonPath("$.cancelReason").value("REQUEST_WITHDRAWN"));
    }

    @Test
    void pledgesNeverCountAsMoneyAndNothingLeaksDonors() throws Exception {
        TestUser recipient = users.createActiveUser(Role.RECIPIENT);
        TestUser donor = users.createActiveUser(Role.DONOR);
        TestUser viewer = users.createActiveUser(Role.DONOR);
        UUID rid = fixtures.insertRequest(recipient.id(), "APPROVED");
        String k = key();
        mockMvc.perform(pledgeReq(donor, rid, k, body(1_250_000, "INR", true))).andExpect(status().isCreated());

        MvcResult anon = mockMvc.perform(get("/api/v1/funding/requests/{id}/summary", rid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.amountMinor").value(TARGET))
                .andExpect(jsonPath("$.confirmedMinor").value(0))
                .andExpect(jsonPath("$.pledgedMinor").value(1_250_000))
                .andExpect(jsonPath("$.remainingMinor").value(TARGET))
                .andExpect(jsonPath("$.pledgeableMinor").value(TARGET - 1_250_000))
                .andExpect(jsonPath("$.activePledgeCount").value(1))
                .andExpect(jsonPath("$.pledgesAreNonBinding").value(true))
                .andExpect(jsonPath("$.viewerPledge").doesNotExist())
                .andReturn();
        mockMvc.perform(get("/api/v1/funding/requests/{id}/summary", rid).session(viewer.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerPledge").doesNotExist());
        mockMvc.perform(get("/api/v1/funding/requests/{id}/summary", rid).session(donor.session()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viewerPledge.status").value("ACTIVE"))
                .andExpect(jsonPath("$.viewerPledge.amount.amountMinor").value(1_250_000));

        MvcResult feed = mockMvc.perform(get("/api/v1/discovery/feed").param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode card = null;
        for (JsonNode n : json(feed).get("items")) {
            if (n.get("id").asText().equals(rid.toString())) {
                card = n;
            }
        }
        assertThat(card).isNotNull();
        assertThat(card.get("funding").get("pledgedMinor").asLong()).isEqualTo(1_250_000);
        assertThat(card.get("funding").get("confirmedMinor").asLong()).isZero();
        MvcResult detail = mockMvc.perform(get("/api/v1/discovery/feed/{id}", rid)).andExpect(status().isOk()).andReturn();
        assertThat(json(detail).get("funding").get("pledgedMinor").asLong()).isEqualTo(1_250_000);
        assertThat(json(detail).get("funding").get("confirmedMinor").asLong()).isZero();

        for (String raw : List.of(
                anon.getResponse().getContentAsString(), feed.getResponse().getContentAsString(), detail.getResponse().getContentAsString())) {
            assertThat(raw).doesNotContain(donor.id().toString()).doesNotContain(donor.email()).doesNotContain(k);
            assertThat(raw.toLowerCase()).doesNotContain("idempotency").doesNotContain("donoruserid");
        }
    }
}
