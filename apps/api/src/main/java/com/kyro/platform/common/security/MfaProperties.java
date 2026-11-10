package com.kyro.platform.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** {@code kyro.mfa.*}. The encryption key is a base64 encoded 32 byte AES key and is never logged. */
@ConfigurationProperties(prefix = "kyro.mfa")
public record MfaProperties(
        boolean adminEnforced, String issuer, String encryptionKey, int recoveryCodeCount, int allowedSkewSteps) {

    public MfaProperties {
        issuer = issuer == null || issuer.isBlank() ? "KYRO Sandbox" : issuer;
        recoveryCodeCount = recoveryCodeCount <= 0 ? 10 : recoveryCodeCount;
        allowedSkewSteps = Math.max(allowedSkewSteps, 0);
    }

    @Override
    public String toString() {
        return "MfaProperties[adminEnforced=" + adminEnforced + ", issuer=" + issuer + ", encryptionKey=***]";
    }
}
