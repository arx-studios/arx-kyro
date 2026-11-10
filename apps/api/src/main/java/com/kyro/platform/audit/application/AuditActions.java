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
}
