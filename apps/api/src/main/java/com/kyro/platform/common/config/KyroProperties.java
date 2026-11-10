package com.kyro.platform.common.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Typed view of the {@code kyro.*} configuration tree (see application.yml). */
@ConfigurationProperties(prefix = "kyro")
public record KyroProperties(
        String webBaseUrl,
        String mailFrom,
        Policy policy,
        Auth auth,
        Storage storage,
        Documents documents,
        Discovery discovery,
        Seed seed) {

    public record Policy(String termsVersion, String privacyVersion) {}

    public record Auth(
            Duration verificationTokenTtl,
            int loginRatePerMinute,
            int registerRatePerMinute,
            int resendRatePerMinute) {}

    public record Storage(
            String endpoint,
            String publicEndpoint,
            String region,
            String accessKey,
            String secretKey,
            String documentsBucket) {}

    public record Documents(
            long maxSizeBytes,
            int maxPerRequest,
            List<String> allowedContentTypes,
            Duration uploadUrlTtl,
            Duration viewUrlTtl) {}

    public record Discovery(int defaultPageSize, int maxPageSize, int storyExcerptLength) {}

    public record Seed(boolean enabled, String password) {}
}
