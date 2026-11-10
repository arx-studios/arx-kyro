-- V7: operations (user reports, moderation holds) and a hold-aware public view

CREATE TABLE user_reports (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    reporter_user_id uuid          NOT NULL REFERENCES users (id),
    target_type      varchar(16)   NOT NULL DEFAULT 'REQUEST',
    request_id       uuid          NOT NULL REFERENCES funding_requests (id),
    reason           varchar(32)   NOT NULL,
    details          varchar(2000),
    status           varchar(16)   NOT NULL DEFAULT 'OPEN',
    assigned_to      uuid REFERENCES users (id),
    assigned_at      timestamptz,
    resolution       varchar(32),
    resolution_note  varchar(2000),
    resolved_by      uuid REFERENCES users (id),
    resolved_at      timestamptz,
    created_at       timestamptz   NOT NULL DEFAULT now(),
    version          bigint        NOT NULL DEFAULT 0,
    CONSTRAINT user_reports_target_type_chk CHECK (target_type IN ('REQUEST')),
    CONSTRAINT user_reports_reason_chk CHECK (reason IN ('SUSPECTED_FRAUD', 'ALTERED_DOCUMENT', 'DUPLICATE_REQUEST', 'INAPPROPRIATE_CONTENT', 'PRIVACY_CONCERN', 'OTHER')),
    CONSTRAINT user_reports_status_chk CHECK (status IN ('OPEN', 'IN_REVIEW', 'RESOLVED')),
    CONSTRAINT user_reports_resolution_chk CHECK (resolution IS NULL OR resolution IN ('DISMISSED', 'ACTION_TAKEN')),
    CONSTRAINT user_reports_resolved_has_resolution_chk CHECK ((status = 'RESOLVED') = (resolution IS NOT NULL))
);
CREATE UNIQUE INDEX uq_user_reports_open_per_reporter ON user_reports (reporter_user_id, request_id) WHERE status <> 'RESOLVED';
CREATE INDEX idx_user_reports_status_created ON user_reports (status, created_at, id);
CREATE INDEX idx_user_reports_request ON user_reports (request_id);
CREATE INDEX idx_user_reports_assigned_to ON user_reports (assigned_to);
CREATE INDEX idx_user_reports_reporter ON user_reports (reporter_user_id);

CREATE TABLE request_moderation_holds (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id  uuid         NOT NULL REFERENCES funding_requests (id),
    report_id   uuid REFERENCES user_reports (id),
    reason      varchar(500) NOT NULL,
    placed_by   uuid         NOT NULL REFERENCES users (id),
    placed_at   timestamptz  NOT NULL DEFAULT now(),
    released_by uuid REFERENCES users (id),
    released_at timestamptz
);
CREATE UNIQUE INDEX uq_request_holds_active ON request_moderation_holds (request_id) WHERE released_at IS NULL;
CREATE INDEX idx_request_holds_report ON request_moderation_holds (report_id);

-- Same column list and order as V4, plus the hold exclusion.
CREATE OR REPLACE VIEW discovery_public_requests AS
SELECT r.id,
       r.recipient_user_id,
       r.title,
       r.story,
       r.expense_type,
       r.institution_name,
       r.institution_city,
       r.institution_country,
       r.course_name,
       r.amount_minor,
       r.currency,
       r.due_date,
       r.approved_at,
       p.public_display_name AS recipient_public_name
FROM funding_requests r
JOIN recipient_profiles p ON p.user_id = r.recipient_user_id
WHERE r.status = 'APPROVED'
  AND NOT EXISTS (SELECT 1 FROM request_moderation_holds h
                  WHERE h.request_id = r.id AND h.released_at IS NULL);
