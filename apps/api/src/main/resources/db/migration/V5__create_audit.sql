-- V5: append-only audit log

CREATE TABLE audit_events (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at     timestamptz  NOT NULL DEFAULT now(),
    actor_user_id   uuid REFERENCES users (id),
    actor_roles     varchar(128),
    action          varchar(64)  NOT NULL,
    entity_type     varchar(64)  NOT NULL,
    entity_id       uuid,
    correlation_id  varchar(64),
    ip_address      varchar(45),
    details         jsonb        NOT NULL DEFAULT '{}'::jsonb
);
CREATE INDEX idx_audit_events_entity ON audit_events (entity_type, entity_id, occurred_at DESC);
CREATE INDEX idx_audit_events_actor ON audit_events (actor_user_id, occurred_at DESC);

CREATE FUNCTION audit_events_immutable() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'audit_events is append-only (% not allowed)', TG_OP;
END;
$$;

CREATE TRIGGER audit_events_no_update_delete
    BEFORE UPDATE OR DELETE ON audit_events
    FOR EACH ROW EXECUTE FUNCTION audit_events_immutable();
