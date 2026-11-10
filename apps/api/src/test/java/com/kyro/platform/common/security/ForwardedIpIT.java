package com.kyro.platform.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.kyro.platform.support.IntegrationTestBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

/**
 * Real HTTP (Tomcat) because X-Forwarded-For handling is done by Tomcat's RemoteIpValve. The login limit is lowered to 2;
 * POSTs without CSRF still count against it (403 until the limit trips to 429).
 */
class ForwardedIpIT {

    private static int post(int port, String forwardedFor) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/auth/session"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"email\":\"x@kyro.local\",\"password\":\"WrongPassword!2026\"}"));
        if (forwardedFor != null) {
            b.header("X-Forwarded-For", forwardedFor);
        }
        return HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"server.forward-headers-strategy=native", "kyro.auth.login-rate-per-minute=2"})
    static class TrustedProxy extends IntegrationTestBase {
        @LocalServerPort int port;

        @Test
        void eachForwardedClientGetsItsOwnBucket() throws Exception {
            assertThat(post(port, "203.0.113.10")).isEqualTo(403);
            assertThat(post(port, "203.0.113.10")).isEqualTo(403);
            assertThat(post(port, "203.0.113.10")).isEqualTo(429);
            assertThat(post(port, "203.0.113.11")).isEqualTo(403);
        }
    }

    @SpringBootTest(
            webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
            properties = {"server.forward-headers-strategy=none", "kyro.auth.login-rate-per-minute=2"})
    static class NoForwardedHeaderTrust extends IntegrationTestBase {
        @LocalServerPort int port;

        @Test
        void spoofedForwardedForIsIgnored() throws Exception {
            assertThat(post(port, "203.0.113.10")).isEqualTo(403);
            assertThat(post(port, "203.0.113.11")).isEqualTo(403);
            assertThat(post(port, "203.0.113.12")).isEqualTo(429);
        }
    }
}
