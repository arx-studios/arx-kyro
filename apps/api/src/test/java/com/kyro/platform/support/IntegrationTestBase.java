package com.kyro.platform.support;

import com.kyro.platform.common.security.RateLimiter;
import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

// FROZEN CONTRACT (test support): API-2/3/4 extend this class.
/**
 * Full-stack test base: Spring Boot + MockMvc against singleton Testcontainers (Postgres 17, MinIO) that are started
 * once per JVM and shared by every test class. Profile {@code test} (no dev seed, no real SMTP: see
 * {@link TestMailConfig}). Rate limiter windows are reset before each test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestMailConfig.class})
public abstract class IntegrationTestBase {

    /** Same image as docker-compose (see DECISIONS.md D-004). */
    public static final String MINIO_IMAGE = "bitnamilegacy/minio:2025.5.24-debian-12-r5";

    public static final String MINIO_USER = "kyro-minio";
    public static final String MINIO_PASSWORD = "kyro-minio-secret";
    public static final String BUCKET = "kyro-documents-test";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    static final GenericContainer<?> MINIO = new GenericContainer<>(MINIO_IMAGE)
            .withEnv("MINIO_ROOT_USER", MINIO_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
            .withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    static {
        POSTGRES.start();
        MINIO.start();
        createBucket();
    }

    private static String minioEndpoint() {
        return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000);
    }

    private static void createBucket() {
        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(minioEndpoint()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(MINIO_USER, MINIO_PASSWORD)))
                .forcePathStyle(true)
                .build()) {
            s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
        } catch (BucketAlreadyOwnedByYouException ignored) {
            // already there
        }
    }

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("kyro.storage.endpoint", IntegrationTestBase::minioEndpoint);
        registry.add("kyro.storage.public-endpoint", IntegrationTestBase::minioEndpoint);
        registry.add("kyro.storage.access-key", () -> MINIO_USER);
        registry.add("kyro.storage.secret-key", () -> MINIO_PASSWORD);
        registry.add("kyro.storage.documents-bucket", () -> BUCKET);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    private RateLimiter rateLimiter;

    @BeforeEach
    void resetRateLimiter() {
        rateLimiter.reset();
    }
}
