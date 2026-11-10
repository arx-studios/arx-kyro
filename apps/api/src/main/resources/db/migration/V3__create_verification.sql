-- V3: verification cases and decisions

CREATE TABLE verification_cases (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id        uuid        NOT NULL REFERENCES funding_requests (id),
    submission_number int         NOT NULL,
    status            varchar(16) NOT NULL DEFAULT 'OPEN',
    outcome           varchar(32),
    assigned_to       uuid REFERENCES users (id),
    assigned_at       timestamptz,
    opened_at         timestamptz NOT NULL DEFAULT now(),
    closed_at         timestamptz,
    version           bigint      NOT NULL DEFAULT 0,
    CONSTRAINT verification_cases_status_chk CHECK (status IN ('OPEN', 'ASSIGNED', 'CLOSED')),
    CONSTRAINT verification_cases_outcome_chk CHECK (outcome IS NULL OR outcome IN
        ('APPROVED', 'REJECTED', 'CHANGES_REQUESTED', 'WITHDRAWN')),
    CONSTRAINT uq_verification_cases_request_submission UNIQUE (request_id, submission_number)
);
CREATE UNIQUE INDEX uq_verification_cases_one_open ON verification_cases (request_id) WHERE status <> 'CLOSED';
CREATE INDEX idx_verification_cases_status_opened ON verification_cases (status, opened_at);
CREATE INDEX idx_verification_cases_assigned_to ON verification_cases (assigned_to);

CREATE TABLE verification_decisions (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    case_id              uuid         NOT NULL UNIQUE REFERENCES verification_cases (id),
    decided_by           uuid         NOT NULL REFERENCES users (id),
    decision             varchar(32)  NOT NULL,
    rationale            varchar(2000) NOT NULL,
    message_to_recipient varchar(2000),
    created_at           timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT verification_decisions_decision_chk CHECK (decision IN ('APPROVE', 'REJECT', 'REQUEST_CHANGES')),
    CONSTRAINT verification_decisions_rationale_chk CHECK (length(rationale) >= 10)
);
CREATE INDEX idx_verification_decisions_decided_by ON verification_decisions (decided_by);
