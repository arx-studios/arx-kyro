-- V8: identity security (credentials version, password reset tokens, TOTP MFA)

ALTER TABLE users ADD COLUMN credentials_version bigint NOT NULL DEFAULT 0;

CREATE TABLE password_reset_tokens (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash    varchar(64) NOT NULL UNIQUE,
    expires_at    timestamptz NOT NULL,
    consumed_at   timestamptz,
    requested_ip  varchar(45),
    created_at    timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens (user_id);

CREATE TABLE user_mfa_credentials (
    user_id           uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    method            varchar(16)  NOT NULL DEFAULT 'TOTP',
    secret_ciphertext varchar(512) NOT NULL,
    key_id            varchar(32)  NOT NULL,
    confirmed_at      timestamptz,
    last_used_step    bigint,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    version           bigint       NOT NULL DEFAULT 0,
    CONSTRAINT user_mfa_credentials_method_chk CHECK (method IN ('TOTP'))
);

CREATE TABLE user_mfa_recovery_codes (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  varchar(64) NOT NULL,
    used_at    timestamptz,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_user_mfa_recovery_codes_user ON user_mfa_recovery_codes (user_id);
