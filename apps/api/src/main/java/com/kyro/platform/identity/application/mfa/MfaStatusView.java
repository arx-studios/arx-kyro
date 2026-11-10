package com.kyro.platform.identity.application.mfa;

/** {@code required}: staff role and enforcement on; {@code verified}: this session passed MFA. */
public record MfaStatusView(boolean required, boolean enrolled, boolean verified) {}
