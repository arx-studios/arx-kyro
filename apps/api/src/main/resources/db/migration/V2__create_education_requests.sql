-- V2: education funding requests, documents, status history

CREATE TABLE institutions (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name         varchar(200) NOT NULL,
    city         varchar(100),
    country_code varchar(2)   NOT NULL,
    website      varchar(300),
    verified     boolean      NOT NULL DEFAULT false,
    created_at   timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT institutions_country_code_chk CHECK (country_code ~ '^[A-Z]{2}$')
);
CREATE UNIQUE INDEX uq_institutions_name_country ON institutions (lower(name), country_code);

CREATE TABLE funding_requests (
    id                        uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    recipient_user_id         uuid         NOT NULL REFERENCES users (id),
    institution_id            uuid REFERENCES institutions (id),
    status                    varchar(32)  NOT NULL DEFAULT 'DRAFT',
    title                     varchar(120) NOT NULL,
    story                     varchar(2000),
    expense_type              varchar(32),
    institution_name          varchar(200),
    institution_city          varchar(100),
    institution_country       varchar(2) DEFAULT 'IN',
    course_name               varchar(200),
    fee_reference             varchar(100),
    fee_reference_normalized  varchar(100),
    amount_minor              bigint       NOT NULL,
    currency                  varchar(3)   NOT NULL,
    due_date                  date,
    submission_count          int          NOT NULL DEFAULT 0,
    submitted_at              timestamptz,
    approved_at               timestamptz,
    created_at                timestamptz  NOT NULL DEFAULT now(),
    updated_at                timestamptz  NOT NULL DEFAULT now(),
    version                   bigint       NOT NULL DEFAULT 0,
    CONSTRAINT funding_requests_status_chk CHECK (status IN
        ('DRAFT', 'SUBMITTED', 'IN_REVIEW', 'CHANGES_REQUESTED', 'APPROVED', 'REJECTED', 'WITHDRAWN', 'FUNDED', 'CLOSED')),
    CONSTRAINT funding_requests_expense_type_chk CHECK (expense_type IS NULL OR expense_type IN
        ('TUITION', 'EXAMINATION_FEE', 'COURSE_MATERIALS', 'OTHER_ACADEMIC')),
    CONSTRAINT funding_requests_institution_country_chk CHECK (institution_country IS NULL OR institution_country ~ '^[A-Z]{2}$'),
    CONSTRAINT funding_requests_amount_chk CHECK (amount_minor > 0),
    CONSTRAINT funding_requests_currency_chk CHECK (currency ~ '^[A-Z]{3}$')
);
CREATE INDEX idx_funding_requests_recipient ON funding_requests (recipient_user_id, created_at DESC);
CREATE INDEX idx_funding_requests_status_approved ON funding_requests (status, approved_at DESC, id DESC);
CREATE INDEX idx_funding_requests_fee_ref ON funding_requests (fee_reference_normalized);
CREATE INDEX idx_funding_requests_institution ON funding_requests (institution_id);

CREATE TABLE request_documents (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id         uuid         NOT NULL REFERENCES funding_requests (id),
    uploaded_by        uuid         NOT NULL REFERENCES users (id),
    document_type      varchar(32)  NOT NULL,
    storage_key        varchar(300) NOT NULL UNIQUE,
    original_file_name varchar(200) NOT NULL,
    content_type       varchar(64)  NOT NULL,
    size_bytes         bigint       NOT NULL,
    sha256             varchar(64),
    status             varchar(32)  NOT NULL DEFAULT 'PENDING_UPLOAD',
    scan_status        varchar(32)  NOT NULL DEFAULT 'NOT_SCANNED',
    created_at         timestamptz  NOT NULL DEFAULT now(),
    uploaded_at        timestamptz,
    deleted_at         timestamptz,
    CONSTRAINT request_documents_type_chk CHECK (document_type IN
        ('TUITION_INVOICE', 'FEE_STATEMENT', 'ENROLLMENT_PROOF', 'OTHER')),
    CONSTRAINT request_documents_content_type_chk CHECK (content_type IN
        ('application/pdf', 'image/jpeg', 'image/png')),
    CONSTRAINT request_documents_size_chk CHECK (size_bytes BETWEEN 1 AND 10485760),
    CONSTRAINT request_documents_status_chk CHECK (status IN
        ('PENDING_UPLOAD', 'UPLOADED', 'REJECTED', 'DELETED'))
);
CREATE INDEX idx_request_documents_request ON request_documents (request_id);
CREATE INDEX idx_request_documents_sha256 ON request_documents (sha256);
CREATE INDEX idx_request_documents_uploaded_by ON request_documents (uploaded_by);

CREATE TABLE funding_request_status_history (
    id         uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id uuid        NOT NULL REFERENCES funding_requests (id),
    from_status varchar(32),
    to_status  varchar(32) NOT NULL,
    changed_by uuid REFERENCES users (id),
    note       varchar(2000),
    changed_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_status_history_request ON funding_request_status_history (request_id, changed_at);
CREATE INDEX idx_status_history_changed_by ON funding_request_status_history (changed_by);
