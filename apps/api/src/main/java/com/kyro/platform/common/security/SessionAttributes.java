package com.kyro.platform.common.security;

/** HTTP session attribute names shared by the identity (credentials version) and security (MFA, revalidation) code. */
public final class SessionAttributes {

    private SessionAttributes() {}

    /** The user's {@code users.credentials_version} when the session was created (Long). */
    public static final String CREDENTIALS_VERSION = "KYRO_CREDENTIALS_VERSION";

    /** When this session passed MFA (Instant); absent when not verified. */
    public static final String MFA_VERIFIED_AT = "KYRO_MFA_VERIFIED_AT";

    /** When the session was last checked against the database (Instant). */
    public static final String LAST_REVALIDATED_AT = "KYRO_LAST_REVALIDATED_AT";
}
