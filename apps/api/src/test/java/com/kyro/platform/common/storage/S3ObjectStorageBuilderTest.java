package com.kyro.platform.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.kyro.platform.common.config.KyroProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3ObjectStorageBuilderTest {

    private static final Region REGION = Region.of("eu-west-1");

    private static KyroProperties.Storage storage(String endpoint, String publicEndpoint, String key, String secret) {
        return new KyroProperties.Storage(endpoint, publicEndpoint, "eu-west-1", key, secret, "bucket");
    }

    private static String presignedGet(S3Presigner p) {
        return p.presignGetObject(r -> r.signatureDuration(Duration.ofMinutes(1))
                        .getObjectRequest(g -> g.bucket("bucket").key("a/b")))
                .url()
                .toString();
    }

    @Test
    void staticKeysUseStaticProvider() {
        var s = storage("http://localhost:9000", "http://localhost:9000", "k", "sec");
        assertThat(S3ObjectStorage.credentialsProvider(s)).isInstanceOf(StaticCredentialsProvider.class);
    }

    @Test
    void blankKeysUseDefaultChain() {
        assertThat(S3ObjectStorage.credentialsProvider(storage("", "", "", "")))
                .isInstanceOf(DefaultCredentialsProvider.class);
        assertThat(S3ObjectStorage.credentialsProvider(storage(null, null, null, null)))
                .isInstanceOf(DefaultCredentialsProvider.class);
    }

    @Test
    void localEndpointIsOverriddenWithPathStyle() {
        var s = storage("http://localhost:9000", "http://localhost:9000", "k", "sec");
        AwsCredentialsProvider creds = S3ObjectStorage.credentialsProvider(s);
        try (S3Client c = S3ObjectStorage.clientBuilder(s, creds, REGION).build()) {
            assertThat(c.serviceClientConfiguration().endpointOverride())
                    .hasValueSatisfying(u -> assertThat(u.toString()).isEqualTo("http://localhost:9000"));
        }
        try (S3Presigner p = S3ObjectStorage.presignerBuilder(s, creds, REGION).build()) {
            assertThat(presignedGet(p)).startsWith("http://localhost:9000/bucket/a/b");
        }
    }

    @Test
    void blankEndpointsUseDefaultAwsEndpointVirtualHost() {
        var s = storage("", "", "", "");
        AwsCredentialsProvider creds =
                StaticCredentialsProvider.create(AwsBasicCredentials.create("AKIATEST", "secret"));
        try (S3Client c = S3ObjectStorage.clientBuilder(s, creds, REGION).build()) {
            assertThat(c.serviceClientConfiguration().endpointOverride()).isEmpty();
        }
        try (S3Presigner p = S3ObjectStorage.presignerBuilder(s, creds, REGION).build()) {
            assertThat(presignedGet(p)).startsWith("https://bucket.s3.eu-west-1.amazonaws.com/a/b");
        }
    }
}
