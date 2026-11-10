package com.kyro.platform.common.storage;

import com.kyro.platform.common.config.KyroProperties;
import java.io.InputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * S3 implementation using two endpoints: an internal client for server-side calls and a presigner bound to the
 * public endpoint (the host the browser uses). Path-style addressing for MinIO.
 */
@Component
public class S3ObjectStorage implements ObjectStorage {

    private final S3Client client;
    private final S3Presigner presigner;
    private final String bucket;
    private final Clock clock;

    public S3ObjectStorage(KyroProperties props, Clock clock) {
        KyroProperties.Storage s = props.storage();
        this.bucket = s.documentsBucket();
        this.clock = clock;
        AwsCredentialsProvider creds = credentialsProvider(s);
        Region region = Region.of(s.region());
        this.client = clientBuilder(s, creds, region).build();
        this.presigner = presignerBuilder(s, creds, region).build();
    }

    private static boolean blank(String v) {
        return v == null || v.isBlank();
    }

    /** Blank access key means "use the default chain" (ECS task role, env, profile); otherwise static keys (MinIO). */
    static AwsCredentialsProvider credentialsProvider(KyroProperties.Storage s) {
        if (blank(s.accessKey())) {
            return DefaultCredentialsProvider.builder().build();
        }
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(s.accessKey(), s.secretKey()));
    }

    /** Blank endpoint means the default AWS endpoint with virtual-host addressing; otherwise override + path-style. */
    static S3ClientBuilder clientBuilder(KyroProperties.Storage s, AwsCredentialsProvider creds, Region region) {
        S3ClientBuilder b = S3Client.builder().region(region).credentialsProvider(creds);
        if (!blank(s.endpoint())) {
            b.endpointOverride(URI.create(s.endpoint())).forcePathStyle(true);
            b.requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                    .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED);
        } else {
            b.forcePathStyle(false);
        }
        return b;
    }

    /** Blank public endpoint means the default presigner endpoint; otherwise the browser-facing override. */
    static S3Presigner.Builder presignerBuilder(KyroProperties.Storage s, AwsCredentialsProvider creds, Region region) {
        S3Presigner.Builder b = S3Presigner.builder().region(region).credentialsProvider(creds);
        boolean override = !blank(s.publicEndpoint());
        if (override) {
            b.endpointOverride(URI.create(s.publicEndpoint()));
        }
        b.serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(override)
                .checksumValidationEnabled(false)
                .build());
        return b;
    }

    @Override
    public PresignedUrl presignPut(String key, String contentType, long contentLength, Duration ttl) {
        PutObjectRequest put = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(contentLength)
                .build();
        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(put)
                .build());
        Map<String, String> headers = new LinkedHashMap<>();
        presigned.signedHeaders().forEach((name, values) -> {
            String lower = name.toLowerCase();
            // host is implied by the URL; content-length is set by the HTTP client from the body.
            if (!lower.equals("host") && !lower.equals("content-length")) {
                headers.put(name, String.join(",", values));
            }
        });
        return new PresignedUrl(presigned.url().toString(), Map.copyOf(headers), Instant.now(clock).plus(ttl));
    }

    @Override
    public PresignedUrl presignGet(String key, Duration ttl) {
        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build());
        return new PresignedUrl(presigned.url().toString(), Map.of(), Instant.now(clock).plus(ttl));
    }

    @Override
    public Optional<StoredObjectMetadata> head(String key) {
        try {
            var r = client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return Optional.of(new StoredObjectMetadata(r.contentLength(), r.contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public InputStream open(String key) {
        return client.getObject(GetObjectRequest.builder().bucket(bucket).key(key).build());
    }

    @Override
    public void delete(String key) {
        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }
}
