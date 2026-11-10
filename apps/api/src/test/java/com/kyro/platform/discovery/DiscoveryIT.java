package com.kyro.platform.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import com.kyro.platform.common.security.Role;
import com.kyro.platform.support.Csrf;
import com.kyro.platform.support.DbFixtures;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import com.kyro.platform.support.TestUsers.TestUser;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

class DiscoveryIT extends IntegrationTestBase {

    private static final List<String> FORBIDDEN_KEYS =
            List.of("feeReference", "fee_reference", "documents", "email", "recipientUserId", "recipientId", "storageKey", "sha256", "reviewerFeedback");

    @Autowired
    TestUsers users;

    @Autowired
    DbFixtures fixtures;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    JsonMapper mapper;

    private UUID approved(TestUser recipient) {
        return fixtures.insertRequest(recipient.id(), "APPROVED");
    }

    private String swipeBody(String action) {
        return "{\"action\":\"" + action + "\"}";
    }

    private void swipe(TestUser donor, UUID request, String action) throws Exception {
        mockMvc.perform(post("/api/v1/discovery/requests/{id}/swipes", request)
                        .session(donor.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(swipeBody(action)))
                .andExpect(status().isOk());
    }

    private List<UUID> feedIds(TestUser viewer, int limit) throws Exception {
        List<UUID> ids = new ArrayList<>();
        String cursor = null;
        for (int guard = 0; guard < 100; guard++) {
            var req = get("/api/v1/discovery/feed").param("limit", String.valueOf(limit));
            if (cursor != null) {
                req.param("cursor", cursor);
            }
            if (viewer != null) {
                req.session(viewer.session());
            }
            JsonNode body = mapper.readTree(
                    mockMvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
            body.get("items").forEach(n -> ids.add(UUID.fromString(n.get("id").asText())));
            if (body.get("nextCursor").isNull()) {
                return ids;
            }
            cursor = body.get("nextCursor").asText();
        }
        throw new IllegalStateException("paging did not terminate");
    }

    @Test
    void anonymousFeedShowsOnlyApprovedRequests() throws Exception {
        TestUser r = users.createActiveUser(Role.RECIPIENT);
        UUID ok = approved(r);
        List<UUID> hidden = new ArrayList<>();
        for (String s : List.of("DRAFT", "SUBMITTED", "IN_REVIEW", "CHANGES_REQUESTED", "REJECTED", "WITHDRAWN")) {
            hidden.add(fixtures.insertRequest(r.id(), s));
        }
        List<UUID> ids = feedIds(null, 50);
        assertThat(ids).contains(ok).doesNotContainAnyElementsOf(hidden);
        for (UUID h : hidden) {
            mockMvc.perform(get("/api/v1/discovery/feed/{id}", h)).andExpect(status().isNotFound());
        }
    }

    @Test
    void expiredRequestsAreNotInFeed() throws Exception {
        TestUser r = users.createActiveUser(Role.RECIPIENT);
        UUID expired = fixtures.insertRequest(r.id(), "APPROVED", "Old", Instant.now(), LocalDate.now().minusDays(1));
        UUID noDue = fixtures.insertRequest(r.id(), "APPROVED", "No due date", Instant.now(), null);
        assertThat(feedIds(null, 50)).contains(noDue).doesNotContain(expired);
    }

    @Test
    void feedAndDetailAreFreeOfPrivateFields() throws Exception {
        TestUser r = users.createActiveUser(Role.RECIPIENT);
        UUID id = approved(r);
        fixtures.insertDocument(id, r.id(), "UPLOADED", "a".repeat(64));
        String feed = mockMvc.perform(get("/api/v1/discovery/feed").param("limit", "50"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String detail = mockMvc.perform(get("/api/v1/discovery/feed/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.recipientPublicName").value("Test Recipient"))
                .andExpect(jsonPath("$.story").isNotEmpty())
                .andExpect(jsonPath("$.funding.pledgedMinor").value(0))
                .andExpect(jsonPath("$.funding.confirmedMinor").value(0))
                .andExpect(jsonPath("$.amount.currency").value("INR"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        for (String json : List.of(feed, detail)) {
            Iterator<String> names = allFieldNames(mapper.readTree(json)).iterator();
            while (names.hasNext()) {
                assertThat(FORBIDDEN_KEYS).doesNotContain(names.next());
            }
            assertThat(json).doesNotContain("@").doesNotContain("FEE-").doesNotContain("requests/");
        }
        assertThat(mapper.readTree(feed).get("items").get(0).has("story")).isFalse();
    }

    private List<String> allFieldNames(JsonNode n) {
        List<String> out = new ArrayList<>();
        out.addAll(n.propertyNames());
        n.forEach(c -> out.addAll(allFieldNames(c)));
        return out;
    }

    @Test
    void storyExcerptIsTruncatedInCardButFullInDetail() throws Exception {
        TestUser r = users.createActiveUser(Role.RECIPIENT);
        UUID id = approved(r);
        String longStory = "x".repeat(500);
        jdbc.update("UPDATE funding_requests SET story = ? WHERE id = ?", longStory, id);
        mockMvc.perform(get("/api/v1/discovery/feed/{id}", id))
                .andExpect(jsonPath("$.story").value(longStory))
                .andExpect(jsonPath("$.storyExcerpt").value("x".repeat(240)));
    }

    @Test
    void pagingHasNoDuplicatesOrGapsAndHandlesTies() throws Exception {
        TestUser r = users.createActiveUser(Role.RECIPIENT);
        Instant base = Instant.now().plus(1, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MICROS);
        List<UUID> mine = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            // pairs share the same approved_at to exercise the id tiebreaker
            mine.add(fixtures.insertRequest(r.id(), "APPROVED", "Paged " + i, base.minusSeconds(i / 2), LocalDate.now().plusDays(30)));
        }
        List<UUID> ids = feedIds(null, 3);
        assertThat(ids).doesNotHaveDuplicates().containsAll(mine);
        // other tests also insert approved requests, so compare only my subsequence: approved_at DESC, id DESC
        List<UUID> expected = new ArrayList<>();
        for (int pair = 0; pair < 4; pair++) {
            List<UUID> group = new ArrayList<>();
            for (int i = pair * 2; i < Math.min(pair * 2 + 2, 7); i++) {
                group.add(mine.get(i));
            }
            group.sort(java.util.Comparator.comparing(UUID::toString).reversed());
            expected.addAll(group);
        }
        assertThat(ids.stream().filter(mine::contains).toList()).isEqualTo(expected);
        assertThat(ids).isEqualTo(feedIds(null, 50));
    }

    @Test
    void pagingIsStableWhenNewRequestsAreInserted() throws Exception {
        TestUser r = users.createActiveUser(Role.RECIPIENT);
        Instant base = Instant.now().plus(2, ChronoUnit.HOURS);
        List<UUID> mine = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            mine.add(fixtures.insertRequest(r.id(), "APPROVED", "Stable " + i, base.minusSeconds(i), LocalDate.now().plusDays(30)));
        }
        String first = mockMvc.perform(get("/api/v1/discovery/feed").param("limit", "2"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode page1 = mapper.readTree(first);
        // a brand-new (newest) request arrives between pages
        UUID inserted = fixtures.insertRequest(r.id(), "APPROVED", "Late", base.plusSeconds(60), LocalDate.now().plusDays(30));
        String second = mockMvc.perform(get("/api/v1/discovery/feed")
                        .param("limit", "2")
                        .param("cursor", page1.get("nextCursor").asText()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        List<String> seen = new ArrayList<>();
        page1.get("items").forEach(n -> seen.add(n.get("id").asText()));
        mapper.readTree(second).get("items").forEach(n -> seen.add(n.get("id").asText()));
        assertThat(seen).doesNotHaveDuplicates().doesNotContain(inserted.toString());
        assertThat(seen).containsExactly(mine.get(0).toString(), mine.get(1).toString(), mine.get(2).toString(), mine.get(3).toString());
    }

    @Test
    void malformedCursorIsBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/feed").param("cursor", "not-a-cursor!!"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void swipeIsIdempotentAndRemovesRequestFromFeed() throws Exception {
        TestUser r = users.createActiveUser(Role.RECIPIENT);
        TestUser donor = users.createActiveUser(Role.DONOR);
        UUID id = approved(r);
        assertThat(feedIds(donor, 50)).contains(id);
        swipe(donor, id, "SAVED");
        swipe(donor, id, "SAVED");
        Integer rows = jdbc.queryForObject(
                "SELECT count(*) FROM swipes WHERE donor_user_id = ? AND request_id = ?", Integer.class, donor.id(), id);
        assertThat(rows).isEqualTo(1);
        assertThat(feedIds(donor, 50)).doesNotContain(id);
        assertThat(feedIds(null, 50)).contains(id);
        mockMvc.perform(get("/api/v1/discovery/feed/{id}", id).session(donor.session()))
                .andExpect(jsonPath("$.viewerAction").value("SAVED"));
        // latest action wins
        swipe(donor, id, "INTERESTED");
        mockMvc.perform(get("/api/v1/discovery/feed/{id}", id).session(donor.session()))
                .andExpect(jsonPath("$.viewerAction").value("INTERESTED"));
        mockMvc.perform(get("/api/v1/discovery/feed/{id}", id)).andExpect(jsonPath("$.viewerAction").doesNotExist());
    }

    @Test
    void swipeRulesAndAccessControl() throws Exception {
        TestUser r = users.createActiveUser(Role.RECIPIENT);
        TestUser donor = users.createActiveUser(Role.DONOR);
        TestUser both = users.createActiveUser(Role.RECIPIENT, Role.DONOR);
        UUID id = approved(r);
        UUID draft = fixtures.insertRequest(r.id(), "DRAFT");
        UUID own = approved(both);

        mockMvc.perform(post("/api/v1/discovery/requests/{id}/swipes", id)
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(swipeBody("PASS")))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/discovery/requests/{id}/swipes", id)
                        .session(r.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(swipeBody("PASS")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/discovery/requests/{id}/swipes", id)
                        .session(donor.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(swipeBody("PASS")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/discovery/requests/{id}/swipes", draft)
                        .session(donor.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(swipeBody("PASS")))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/discovery/requests/{id}/swipes", own)
                        .session(both.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(swipeBody("SAVED")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mockMvc.perform(post("/api/v1/discovery/requests/{id}/swipes", id)
                        .session(donor.session())
                        .with(Csrf.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(swipeBody("LIKE")))
                .andExpect(status().is4xxClientError());
        // own request never appears in own feed
        assertThat(feedIds(both, 50)).doesNotContain(own);
    }

    @Test
    void savedListShowsSavedAndInterestedNewestFirstAndSupportsRemoval() throws Exception {
        TestUser r = users.createActiveUser(Role.RECIPIENT);
        TestUser donor = users.createActiveUser(Role.DONOR);
        UUID a = approved(r);
        UUID b = approved(r);
        UUID c = approved(r);
        UUID passed = approved(r);
        swipe(donor, a, "SAVED");
        swipe(donor, b, "INTERESTED");
        swipe(donor, passed, "PASS");
        swipe(donor, c, "SAVED");

        JsonNode p1 = mapper.readTree(mockMvc.perform(get("/api/v1/discovery/saved").param("limit", "2").session(donor.session()))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(p1.get("items")).hasSize(2);
        assertThat(p1.get("items").get(0).get("request").get("id").asText()).isEqualTo(c.toString());
        assertThat(p1.get("items").get(1).get("request").get("id").asText()).isEqualTo(b.toString());
        assertThat(p1.get("items").get(1).get("action").asText()).isEqualTo("INTERESTED");
        JsonNode p2 = mapper.readTree(mockMvc.perform(get("/api/v1/discovery/saved")
                        .param("limit", "2")
                        .param("cursor", p1.get("nextCursor").asText())
                        .session(donor.session()))
                .andReturn()
                .getResponse()
                .getContentAsString());
        assertThat(p2.get("items")).hasSize(1);
        assertThat(p2.get("items").get(0).get("request").get("id").asText()).isEqualTo(a.toString());
        assertThat(p2.get("nextCursor").isNull()).isTrue();
        assertThat(p1.toString()).doesNotContain(passed.toString()).doesNotContain("feeReference");

        mockMvc.perform(delete("/api/v1/discovery/saved/{id}", a).session(donor.session()).with(Csrf.token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/discovery/saved/{id}", a).session(donor.session()).with(Csrf.token()))
                .andExpect(status().isNoContent());
        // a PASS is not removable via saved and stays hidden
        mockMvc.perform(delete("/api/v1/discovery/saved/{id}", passed).session(donor.session()).with(Csrf.token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/discovery/saved").session(donor.session()))
                .andExpect(jsonPath("$.items.length()").value(2));
        // removed items can return to the feed
        assertThat(feedIds(donor, 50)).contains(a).doesNotContain(passed);
    }

    @Test
    void savedRequiresDonorRole() throws Exception {
        TestUser r = users.createActiveUser(Role.RECIPIENT);
        mockMvc.perform(get("/api/v1/discovery/saved")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/discovery/saved").session(r.session())).andExpect(status().isForbidden());
    }

    @Test
    void unapprovedSavedRequestDisappearsFromSavedList() throws Exception {
        TestUser r = users.createActiveUser(Role.RECIPIENT);
        TestUser donor = users.createActiveUser(Role.DONOR);
        UUID id = approved(r);
        swipe(donor, id, "SAVED");
        jdbc.update("UPDATE funding_requests SET status = 'WITHDRAWN' WHERE id = ?", id);
        mockMvc.perform(get("/api/v1/discovery/saved").session(donor.session()))
                .andExpect(jsonPath("$.items.length()").value(0));
        mockMvc.perform(get("/api/v1/discovery/feed/{id}", id)).andExpect(status().isNotFound());
    }
}
