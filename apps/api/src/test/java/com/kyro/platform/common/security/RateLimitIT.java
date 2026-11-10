package com.kyro.platform.common.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.kyro.platform.support.Csrf;
import com.kyro.platform.support.IntegrationTestBase;
import com.kyro.platform.support.TestUsers;
import com.kyro.platform.support.TestUsers.TestUser;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class RateLimitIT extends IntegrationTestBase {

    @Autowired TestUsers users;

    @Test
    void the121stFeedRequestFromOneIpIs429WithRetryAfter() throws Exception {
        for (int i = 0; i < 120; i++) {
            mockMvc.perform(get("/api/v1/discovery/feed").with(r -> {
                        r.setRemoteAddr("198.51.100.7");
                        return r;
                    }))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/discovery/feed").with(r -> {
                    r.setRemoteAddr("198.51.100.7");
                    return r;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // another client is unaffected
        mockMvc.perform(get("/api/v1/discovery/feed").with(r -> {
                    r.setRemoteAddr("198.51.100.8");
                    return r;
                }))
                .andExpect(status().isOk());
    }

    private void swipe(TestUser user, String ip, int expected) throws Exception {
        mockMvc.perform(post("/api/v1/discovery/requests/" + UUID.randomUUID() + "/swipes")
                        .session(user.session())
                        .with(Csrf.token())
                        .with(r -> {
                            r.setRemoteAddr(ip);
                            return r;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"decision\":\"SAVED\"}"))
                .andExpect(r -> {
                    int actual = r.getResponse().getStatus();
                    if ((expected == 429) != (actual == 429)) {
                        throw new AssertionError("expected " + (expected == 429 ? "429" : "not 429") + " but was " + actual);
                    }
                });
    }

    @Test
    void swipeLimitIsPerUserNotPerIp() throws Exception {
        TestUser a = users.createActiveUser(Role.DONOR);
        TestUser b = users.createActiveUser(Role.DONOR);
        for (int i = 0; i < 60; i++) {
            swipe(a, "203.0.113.9", 200);
        }
        swipe(a, "203.0.113.9", 429);
        swipe(b, "203.0.113.9", 200); // same IP, different user
    }

    @Test
    void pledgeCreationIsRateLimitedPerUser() throws Exception {
        TestUser donor = users.createActiveUser(Role.DONOR);
        for (int i = 0; i < 10; i++) {
            pledge(donor, false);
        }
        pledge(donor, true);
    }

    private void pledge(TestUser donor, boolean limited) throws Exception {
        mockMvc.perform(post("/api/v1/funding/requests/" + UUID.randomUUID() + "/pledges")
                        .session(donor.session())
                        .with(Csrf.token())
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":{\"amountMinor\":500,\"currency\":\"INR\"}}"))
                .andExpect(r -> {
                    boolean is429 = r.getResponse().getStatus() == 429;
                    if (is429 != limited) {
                        throw new AssertionError("pledge expected limited=" + limited + " status=" + r.getResponse().getStatus());
                    }
                });
    }
}
