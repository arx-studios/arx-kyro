package com.kyro.platform.identity.application;

/** Outbound account-security emails. Implementations must not throw on delivery failure. */
public interface AccountMailer {

    void sendPasswordReset(String toEmail, String displayName, String rawToken);

    /** Notice after a password was reset or changed. Never contains credentials. */
    void sendPasswordChanged(String toEmail, String displayName);
}
