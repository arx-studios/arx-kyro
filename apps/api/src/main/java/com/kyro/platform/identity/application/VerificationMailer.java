package com.kyro.platform.identity.application;

/** Outbound identity emails (Mailpit locally). Implementations must not throw on delivery failure. */
public interface VerificationMailer {

    void sendVerification(String toEmail, String displayName, String rawToken);

    /** Sent instead of a verification mail when someone registers with an already-registered address. */
    void sendExistingAccountNotice(String toEmail);
}
