package com.kyro.platform.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.kyro.platform.support.IntegrationTestBase;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ObjectStorageIT extends IntegrationTestBase {

    @Autowired
    ObjectStorage storage;

    @Test
    void presignedPutThenHeadOpenAndPresignedGetRoundTrip() throws Exception {
        String key = "requests/" + UUID.randomUUID() + "/documents/" + UUID.randomUUID();
        byte[] content = "%PDF-1.4 fictional test document".getBytes(StandardCharsets.UTF_8);

        assertThat(storage.head(key)).isEmpty();

        PresignedUrl put = storage.presignPut(key, "application/pdf", content.length, Duration.ofMinutes(5));
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create(put.url()))
                .PUT(HttpRequest.BodyPublishers.ofByteArray(content));
        // java.net.http forbids setting some headers; the ones signed here (content-type) are allowed.
        put.requiredHeaders().forEach((name, value) -> req.header(name, value));
        HttpResponse<String> putResponse = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build().send(req.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(putResponse.statusCode()).as(putResponse.body()).isEqualTo(200);

        var meta = storage.head(key).orElseThrow();
        assertThat(meta.sizeBytes()).isEqualTo(content.length);
        assertThat(meta.contentType()).isEqualTo("application/pdf");

        try (var in = storage.open(key)) {
            assertThat(in.readAllBytes()).isEqualTo(content);
        }

        PresignedUrl get = storage.presignGet(key, Duration.ofMinutes(5));
        HttpResponse<byte[]> got = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                .send(HttpRequest.newBuilder(URI.create(get.url())).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        assertThat(got.statusCode()).isEqualTo(200);
        assertThat(got.body()).isEqualTo(content);

        storage.delete(key);
        assertThat(storage.head(key)).isEmpty();
        storage.delete(key); // idempotent
    }

    @Test
    void presignedPutWithWrongContentTypeIsRejected() throws Exception {
        String key = "requests/" + UUID.randomUUID() + "/documents/" + UUID.randomUUID();
        byte[] content = "hello".getBytes(StandardCharsets.UTF_8);
        PresignedUrl put = storage.presignPut(key, "application/pdf", content.length, Duration.ofMinutes(5));
        HttpResponse<String> response = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build()
                .send(
                        HttpRequest.newBuilder(URI.create(put.url()))
                                .header("Content-Type", "text/html")
                                .PUT(HttpRequest.BodyPublishers.ofByteArray(content))
                                .build(),
                        HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(403);
    }
}
