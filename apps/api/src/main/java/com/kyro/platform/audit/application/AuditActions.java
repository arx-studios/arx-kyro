package com.kyro.platform.audit.application;

// FROZEN CONTRACT: add constants freely, never rename existing ones (values are stored in audit_events.action).
public final class AuditActions {

    private AuditActions() {}

    // Identity
    public static final String USER_REGISTERED = "USER_REGISTERED";
    public static final String REGISTRATION_ATTEMPT_EXISTING_EMAIL = "REGISTRATION_ATTEMPT_EXISTING_EMAIL";
    public static final String EMAIL_VERIFIED = "EMAIL_VERIFIED";
    public static final String LOGIN_SUCCEEDED = "LOGIN_SUCCEEDED";
    public static final String LOGIN_FAILED = "LOGIN_FAILED";
    public static final String LOGOUT = "LOGOUT";
    public static final String PROFILE_UPDATED = "PROFILE_UPDATED";

    // Requests
    public static final String REQUEST_CREATED = "REQUEST_CREATED";
    public static final String REQUEST_UPDATED = "REQUEST_UPDATED";
    public static final String REQUEST_STATUS_CHANGED = "REQUEST_STATUS_CHANGED";

    // Documents
    public static final String DOCUMENT_UPLOAD_AUTHORIZED = "DOCUMENT_UPLOAD_AUTHORIZED";
    public static final String DOCUMENT_UPLOADED = "DOCUMENT_UPLOADED";
    public static final String DOCUMENT_REJECTED = "DOCUMENT_REJECTED";
    public static final String DOCUMENT_DELETED = "DOCUMENT_DELETED";
    public static final String DOCUMENT_VIEW_URL_ISSUED = "DOCUMENT_VIEW_URL_ISSUED";

    // Access (details: resource, reason)
    public static final String ACCESS_DENIED = "ACCESS_DENIED";

    // Verification
    public static final String CASE_OPENED = "CASE_OPENED";
    public static final String CASE_ASSIGNED = "CASE_ASSIGNED";
    public static final String CASE_CLOSED = "CASE_CLOSED";
    public static final String VERIFICATION_DECIDED = "VERIFICATION_DECIDED";

    // Funding (entity PLEDGE)
    public static final String PLEDGE_CREATED = "PLEDGE_CREATED";
    public static final String PLEDGE_CANCELLED = "PLEDGE_CANCELLED";

    // Operations (entities USER_REPORT, FUNDING_REQUEST for holds)
    public static final String REPORT_CREATED = "REPORT_CREATED";
    public static final String REPORT_ASSIGNED = "REPORT_ASSIGNED";
    public static final String REPORT_RESOLVED = "REPORT_RESOLVED";
    public static final String REQUEST_HOLD_PLACED = "REQUEST_HOLD_PLACED";
    public static final String REQUEST_HOLD_RELEASED = "REQUEST_HOLD_RELEASED";

    // Account (entity USER)
    public static final String PASSWORD_RESET_REQUESTED = "PASSWORD_RESET_REQUESTED";
    public static final String PASSWORD_RESET_COMPLETED = "PASSWORD_RESET_COMPLETED";
    public static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";
    public static final String CONSENT_ACCEPTED = "CONSENT_ACCEPTED";

    // MFA and sessions (entity USER). Details never contain secrets, tokens, codes or passwords.
    public static final String MFA_ENROLLMENT_STARTED = "MFA_ENROLLMENT_STARTED";
    public static final String MFA_ENROLLED = "MFA_ENROLLED";
    public static final String MFA_VERIFIED = "MFA_VERIFIED";
    public static final String MFA_FAILED = "MFA_FAILED";
    public static final String MFA_RECOVERY_CODE_USED = "MFA_RECOVERY_CODE_USED";
    public static final String MFA_RESET = "MFA_RESET";
    public static final String SESSION_REVOKED = "SESSION_REVOKED";
}
