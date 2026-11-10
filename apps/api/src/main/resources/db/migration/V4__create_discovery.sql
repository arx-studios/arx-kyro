-- V4: discovery (swipes, donor preferences, public view)

CREATE TABLE swipes (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    donor_user_id  uuid        NOT NULL REFERENCES users (id),
    request_id     uuid        NOT NULL REFERENCES funding_requests (id),
    action         varchar(16) NOT NULL,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT swipes_action_chk CHECK (action IN ('PASS', 'INTERESTED', 'SAVED')),
    CONSTRAINT uq_swipes_donor_request UNIQUE (donor_user_id, request_id)
);
CREATE INDEX idx_swipes_donor_action_updated ON swipes (donor_user_id, action, updated_at DESC);
CREATE INDEX idx_swipes_request ON swipes (request_id);

CREATE TABLE donor_preferences (
    user_id          uuid PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    expense_types    varchar(32)[],
    max_amount_minor bigint,
    currency         varchar(3),
    updated_at       timestamptz NOT NULL DEFAULT now()
);

-- Privacy-safe interface from the education module to discovery.
-- Must never expose fee_reference, documents, email or similar private data.
CREATE VIEW discovery_public_requests AS
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
WHERE r.status = 'APPROVED';
