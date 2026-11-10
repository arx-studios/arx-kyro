-- V9: append-only history tables (verification decisions, request status history, consents)
-- No code path updates or deletes these rows. Future account deletion must ANONYMISE the user,
-- not delete it: the users -> consent_records ON DELETE CASCADE will now raise.

CREATE FUNCTION forbid_update_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only (% not allowed)', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_verification_decisions_append_only
    BEFORE UPDATE OR DELETE ON verification_decisions
    FOR EACH ROW EXECUTE FUNCTION forbid_update_delete();

CREATE TRIGGER trg_funding_request_status_history_append_only
    BEFORE UPDATE OR DELETE ON funding_request_status_history
    FOR EACH ROW EXECUTE FUNCTION forbid_update_delete();

CREATE TRIGGER trg_consent_records_append_only
    BEFORE UPDATE OR DELETE ON consent_records
    FOR EACH ROW EXECUTE FUNCTION forbid_update_delete();
