-- KYRO schema verification. Everything is rolled back at the end.
-- Usage: docker compose exec -T postgres psql -U kyro -d kyro -v ON_ERROR_STOP=1 -f - < scripts/db/verify.sql
BEGIN;

-- Fixtures (fictional) ---------------------------------------------------
INSERT INTO users (id, email, email_normalized, password_hash, display_name, country_code, status)
VALUES ('00000000-0000-0000-0000-0000000000a1', 'r@kyro.local', 'r@kyro.local', 'x', 'Recipient One', 'IN', 'ACTIVE'),
       ('00000000-0000-0000-0000-0000000000a2', 'd@kyro.local', 'd@kyro.local', 'x', 'Donor One', 'IN', 'ACTIVE'),
       ('00000000-0000-0000-0000-0000000000a3', 'v@kyro.local', 'v@kyro.local', 'x', 'Verifier One', 'IN', 'ACTIVE');

INSERT INTO recipient_profiles (user_id, public_display_name)
VALUES ('00000000-0000-0000-0000-0000000000a1', 'Asha K.');

INSERT INTO funding_requests (id, recipient_user_id, status, title, fee_reference, amount_minor, currency)
VALUES ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1', 'DRAFT',     'Draft request one', 'SECRET-FEE-1', 100000, 'INR'),
       ('00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000a1', 'APPROVED',  'Approved request',  'SECRET-FEE-2', 250000, 'INR');

-- Roles seed -------------------------------------------------------------
DO $$
BEGIN
    IF (SELECT count(*) FROM roles WHERE code IN ('RECIPIENT','DONOR','VERIFIER','ADMIN')) <> 4 THEN
        RAISE EXCEPTION 'FAIL: roles seed incomplete';
    END IF;
END $$;

-- CHECK constraints reject bad values ------------------------------------
DO $$
DECLARE ok boolean;
BEGIN
    ok := false;
    BEGIN
        INSERT INTO funding_requests (recipient_user_id, status, title, amount_minor, currency)
        VALUES ('00000000-0000-0000-0000-0000000000a1', 'BOGUS', 'Bad status', 100, 'INR');
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: bad status accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO funding_requests (recipient_user_id, title, amount_minor, currency)
        VALUES ('00000000-0000-0000-0000-0000000000a1', 'Bad currency', 100, 'inr');
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: bad currency accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO funding_requests (recipient_user_id, title, amount_minor, currency)
        VALUES ('00000000-0000-0000-0000-0000000000a1', 'Zero amount', 0, 'INR');
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: zero amount accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO funding_requests (recipient_user_id, title, amount_minor, currency)
        VALUES ('00000000-0000-0000-0000-0000000000a1', 'Negative amount', -5, 'INR');
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: negative amount accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO users (email, email_normalized, password_hash, display_name, country_code, status)
        VALUES ('z@kyro.local', 'z@kyro.local', 'x', 'Zed', 'IN', 'WEIRD');
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: bad user status accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO users (email, email_normalized, password_hash, display_name, country_code)
        VALUES ('y@kyro.local', 'r@kyro.local', 'x', 'Dup', 'IN');
    EXCEPTION WHEN unique_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: duplicate email_normalized accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO request_documents (request_id, uploaded_by, document_type, storage_key, original_file_name, content_type, size_bytes)
        VALUES ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1',
                'TUITION_INVOICE', 'k1', 'a.exe', 'application/x-msdownload', 100);
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: bad document content type accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO request_documents (request_id, uploaded_by, document_type, storage_key, original_file_name, content_type, size_bytes)
        VALUES ('00000000-0000-0000-0000-0000000000b1', '00000000-0000-0000-0000-0000000000a1',
                'TUITION_INVOICE', 'k2', 'a.pdf', 'application/pdf', 10485761);
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: oversized document accepted'; END IF;
END $$;

-- Verification cases: one open case per request, one per submission --------
INSERT INTO verification_cases (id, request_id, submission_number, status)
VALUES ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000b1', 1, 'OPEN');

DO $$
DECLARE ok boolean;
BEGIN
    ok := false;
    BEGIN
        INSERT INTO verification_cases (request_id, submission_number, status)
        VALUES ('00000000-0000-0000-0000-0000000000b1', 2, 'ASSIGNED');
    EXCEPTION WHEN unique_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: second non-closed case accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO verification_cases (request_id, submission_number, status)
        VALUES ('00000000-0000-0000-0000-0000000000b1', 1, 'CLOSED');
    EXCEPTION WHEN unique_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: duplicate submission_number accepted'; END IF;

    -- A closed case alongside an open one is allowed
    INSERT INTO verification_cases (request_id, submission_number, status, outcome)
    VALUES ('00000000-0000-0000-0000-0000000000b1', 3, 'CLOSED', 'WITHDRAWN');

    ok := false;
    BEGIN
        INSERT INTO verification_decisions (case_id, decided_by, decision, rationale)
        VALUES ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000a3', 'APPROVE', 'short');
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: short rationale accepted'; END IF;

    INSERT INTO verification_decisions (case_id, decided_by, decision, rationale)
    VALUES ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000a3', 'APPROVE', 'Documents match the claim.');

    ok := false;
    BEGIN
        INSERT INTO verification_decisions (case_id, decided_by, decision, rationale)
        VALUES ('00000000-0000-0000-0000-0000000000c1', '00000000-0000-0000-0000-0000000000a3', 'REJECT', 'A second decision for the case.');
    EXCEPTION WHEN unique_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: second decision per case accepted'; END IF;
END $$;

-- Swipes: unique per donor+request, action check ---------------------------
INSERT INTO swipes (donor_user_id, request_id, action)
VALUES ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-0000000000b2', 'INTERESTED');

DO $$
DECLARE ok boolean;
BEGIN
    ok := false;
    BEGIN
        INSERT INTO swipes (donor_user_id, request_id, action)
        VALUES ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-0000000000b2', 'PASS');
    EXCEPTION WHEN unique_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: duplicate swipe accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO swipes (donor_user_id, request_id, action)
        VALUES ('00000000-0000-0000-0000-0000000000a3', '00000000-0000-0000-0000-0000000000b2', 'LIKE');
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: bad swipe action accepted'; END IF;
END $$;

-- Public view: only APPROVED, and no private columns -----------------------
DO $$
BEGIN
    IF (SELECT count(*) FROM discovery_public_requests) <> 1 THEN
        RAISE EXCEPTION 'FAIL: view should return exactly the 1 APPROVED request, got %',
            (SELECT count(*) FROM discovery_public_requests);
    END IF;
    IF EXISTS (SELECT 1 FROM discovery_public_requests WHERE id = '00000000-0000-0000-0000-0000000000b1') THEN
        RAISE EXCEPTION 'FAIL: view leaked a DRAFT request';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'discovery_public_requests'
                 AND (column_name LIKE 'fee%' OR column_name LIKE '%document%' OR column_name LIKE '%sha256%' OR column_name LIKE '%email%')) THEN
        RAISE EXCEPTION 'FAIL: view exposes a private column';
    END IF;
END $$;

-- Audit log is immutable ---------------------------------------------------
INSERT INTO audit_events (id, actor_user_id, action, entity_type, entity_id)
VALUES ('00000000-0000-0000-0000-0000000000d1', '00000000-0000-0000-0000-0000000000a3',
        'TEST_ACTION', 'funding_request', '00000000-0000-0000-0000-0000000000b1');

DO $$
DECLARE ok boolean;
BEGIN
    ok := false;
    BEGIN
        UPDATE audit_events SET action = 'TAMPERED';
    EXCEPTION WHEN raise_exception THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: audit_events UPDATE allowed'; END IF;

    ok := false;
    BEGIN
        DELETE FROM audit_events;
    EXCEPTION WHEN raise_exception THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: audit_events DELETE allowed'; END IF;
END $$;

DO $$ BEGIN RAISE NOTICE 'verify.sql: ALL CHECKS PASSED'; END $$;

ROLLBACK;
