-- V1: identity (users, roles, profiles, email verification tokens, consents)

CREATE TABLE users (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    email             varchar(254) NOT NULL,
    email_normalized  varchar(254) NOT NULL UNIQUE,
    password_hash     varchar(255) NOT NULL,
    display_name      varchar(80)  NOT NULL,
    country_code      varchar(2)   NOT NULL,
    status            varchar(32)  NOT NULL DEFAULT 'PENDING_VERIFICATION',
    email_verified_at timestamptz,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    version           bigint       NOT NULL DEFAULT 0,
    CONSTRAINT users_country_code_chk CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT users_status_chk CHECK (status IN ('PENDING_VERIFICATION', 'ACTIVE', 'SUSPENDED'))
);

CREATE TABLE roles (
    code        varchar(32) PRIMARY KEY,
    description varchar(200) NOT NULL
);

INSERT INTO roles (code, description) VALUES
    ('RECIPIENT', 'Person raising a verified education funding request'),
    ('DONOR', 'Person browsing and supporting verified requests'),
    ('VERIFIER', 'Staff member who reviews verification cases'),
    ('ADMIN', 'Platform administrator');

CREATE TABLE user_roles (
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role_code  varchar(32) NOT NULL REFERENCES roles (code),
    granted_at timestamptz NOT NULL DEFAULT now(),
    granted_by uuid REFERENCES users (id),
    PRIMARY KEY (user_id, role_code)
);
CREATE INDEX idx_user_roles_role_code ON user_roles (role_code);
CREATE INDEX idx_user_roles_granted_by ON user_roles (granted_by);

CREATE TABLE recipient_profiles (
    user_id             uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    public_display_name varchar(40) NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0
);

CREATE TABLE donor_profiles (
    user_id             uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    public_display_name varchar(40) NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    version             bigint      NOT NULL DEFAULT 0
);

CREATE TABLE email_verification_tokens (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    consumed_at timestamptz,
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_verification_tokens_user_id ON email_verification_tokens (user_id);

CREATE TABLE consent_records (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id        uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    policy_type    varchar(16) NOT NULL,
    policy_version varchar(32) NOT NULL,
    accepted_at    timestamptz NOT NULL DEFAULT now(),
    ip_address     varchar(45),
    user_agent     varchar(512),
    CONSTRAINT consent_records_policy_type_chk CHECK (policy_type IN ('TERMS', 'PRIVACY'))
);
CREATE INDEX idx_consent_records_user_id ON consent_records (user_id);
