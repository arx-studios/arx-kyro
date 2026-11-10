-- V6: funding (campaigns, non-binding pledges, progress view)
-- A pledge is a non-binding statement of intent. It is never money raised.

CREATE TABLE campaigns (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id    uuid        NOT NULL UNIQUE REFERENCES funding_requests (id),
    mode          varchar(16) NOT NULL DEFAULT 'GROUP',
    target_minor  bigint      NOT NULL,
    currency      varchar(3)  NOT NULL,
    status        varchar(16) NOT NULL DEFAULT 'OPEN',
    created_at    timestamptz NOT NULL DEFAULT now(),
    closed_at     timestamptz,
    version       bigint      NOT NULL DEFAULT 0,
    CONSTRAINT campaigns_mode_chk CHECK (mode IN ('GROUP', 'DIRECT')),
    CONSTRAINT campaigns_target_chk CHECK (target_minor > 0),
    CONSTRAINT campaigns_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT campaigns_status_chk CHECK (status IN ('OPEN', 'CLOSED'))
);

CREATE TABLE pledges (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    campaign_id      uuid        NOT NULL REFERENCES campaigns (id),
    request_id       uuid        NOT NULL REFERENCES funding_requests (id),
    donor_user_id    uuid        NOT NULL REFERENCES users (id),
    amount_minor     bigint      NOT NULL,
    currency         varchar(3)  NOT NULL,
    status           varchar(16) NOT NULL DEFAULT 'ACTIVE',
    cancel_reason    varchar(32),
    idempotency_key  varchar(64) NOT NULL,
    created_at       timestamptz NOT NULL DEFAULT now(),
    cancelled_at     timestamptz,
    updated_at       timestamptz NOT NULL DEFAULT now(),
    version          bigint      NOT NULL DEFAULT 0,
    CONSTRAINT pledges_amount_chk CHECK (amount_minor > 0),
    CONSTRAINT pledges_currency_chk CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT pledges_status_chk CHECK (status IN ('ACTIVE', 'CANCELLED', 'LAPSED')),
    CONSTRAINT pledges_cancel_reason_chk CHECK (cancel_reason IS NULL OR cancel_reason IN ('DONOR_CANCELLED', 'REQUEST_WITHDRAWN')),
    CONSTRAINT pledges_cancelled_has_reason_chk CHECK ((status = 'CANCELLED') = (cancel_reason IS NOT NULL)),
    CONSTRAINT uq_pledges_donor_idempotency UNIQUE (donor_user_id, idempotency_key)
);
CREATE UNIQUE INDEX uq_pledges_active_donor_request ON pledges (donor_user_id, request_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_pledges_request_status ON pledges (request_id, status);
CREATE INDEX idx_pledges_donor_created ON pledges (donor_user_id, created_at DESC, id DESC);
CREATE INDEX idx_pledges_campaign ON pledges (campaign_id);

-- Pledged totals per request. One row per request with at least one pledge row; consumers LEFT JOIN it.
CREATE VIEW funding_request_progress AS
SELECT p.request_id,
       p.currency,
       COALESCE(sum(p.amount_minor) FILTER (WHERE p.status = 'ACTIVE'), 0)::bigint AS pledged_minor,
       (count(*) FILTER (WHERE p.status = 'ACTIVE'))::bigint AS active_pledge_count
FROM pledges p
GROUP BY p.request_id, p.currency;
