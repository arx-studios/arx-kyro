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

-- Funding: campaigns and pledges (V6) --------------------------------------
INSERT INTO campaigns (id, request_id, target_minor, currency)
VALUES ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000b2', 250000, 'INR');

INSERT INTO pledges (id, campaign_id, request_id, donor_user_id, amount_minor, currency, idempotency_key)
VALUES ('00000000-0000-0000-0000-0000000000f1', '00000000-0000-0000-0000-0000000000e1',
        '00000000-0000-0000-0000-0000000000b2', '00000000-0000-0000-0000-0000000000a2', 50000, 'INR', 'idem-key-0001');

DO $$
DECLARE ok boolean;
BEGIN
    ok := false;
    BEGIN
        INSERT INTO pledges (campaign_id, request_id, donor_user_id, amount_minor, currency, idempotency_key)
        VALUES ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000b2',
                '00000000-0000-0000-0000-0000000000a3', 0, 'INR', 'idem-key-0002');
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: zero pledge amount accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO pledges (campaign_id, request_id, donor_user_id, amount_minor, currency, idempotency_key)
        VALUES ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000b2',
                '00000000-0000-0000-0000-0000000000a3', -5, 'INR', 'idem-key-0003');
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: negative pledge amount accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO pledges (campaign_id, request_id, donor_user_id, amount_minor, currency, idempotency_key)
        VALUES ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000b2',
                '00000000-0000-0000-0000-0000000000a2', 1000, 'INR', 'idem-key-0004');
    EXCEPTION WHEN unique_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: second ACTIVE pledge for same donor and request accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO pledges (campaign_id, request_id, donor_user_id, amount_minor, currency, status, cancel_reason, idempotency_key)
        VALUES ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000b2',
                '00000000-0000-0000-0000-0000000000a2', 1000, 'INR', 'CANCELLED', 'DONOR_CANCELLED', 'idem-key-0001');
    EXCEPTION WHEN unique_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: duplicate (donor, idempotency_key) accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO pledges (campaign_id, request_id, donor_user_id, amount_minor, currency, status, idempotency_key)
        VALUES ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000b2',
                '00000000-0000-0000-0000-0000000000a2', 1000, 'INR', 'CANCELLED', 'idem-key-0005');
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: CANCELLED pledge without reason accepted'; END IF;

    -- A CANCELLED pledge alongside the ACTIVE one is allowed, and the progress view ignores it
    INSERT INTO pledges (campaign_id, request_id, donor_user_id, amount_minor, currency, status, cancel_reason, cancelled_at, idempotency_key)
    VALUES ('00000000-0000-0000-0000-0000000000e1', '00000000-0000-0000-0000-0000000000b2',
            '00000000-0000-0000-0000-0000000000a2', 7777, 'INR', 'CANCELLED', 'DONOR_CANCELLED', now(), 'idem-key-0006');

    IF (SELECT pledged_minor FROM funding_request_progress WHERE request_id = '00000000-0000-0000-0000-0000000000b2') <> 50000 THEN
        RAISE EXCEPTION 'FAIL: funding_request_progress counted a CANCELLED pledge';
    END IF;
    IF (SELECT active_pledge_count FROM funding_request_progress WHERE request_id = '00000000-0000-0000-0000-0000000000b2') <> 1 THEN
        RAISE EXCEPTION 'FAIL: funding_request_progress active_pledge_count wrong';
    END IF;
END $$;

-- Operations: reports and holds (V7) ----------------------------------------
INSERT INTO user_reports (id, reporter_user_id, request_id, reason)
VALUES ('00000000-0000-0000-0000-000000000aa1', '00000000-0000-0000-0000-0000000000a2',
        '00000000-0000-0000-0000-0000000000b2', 'SUSPECTED_FRAUD');

DO $$
DECLARE ok boolean;
BEGIN
    ok := false;
    BEGIN
        INSERT INTO user_reports (reporter_user_id, request_id, reason)
        VALUES ('00000000-0000-0000-0000-0000000000a2', '00000000-0000-0000-0000-0000000000b2', 'OTHER');
    EXCEPTION WHEN unique_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: second open report by same reporter on same request accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO user_reports (reporter_user_id, request_id, reason, status)
        VALUES ('00000000-0000-0000-0000-0000000000a3', '00000000-0000-0000-0000-0000000000b2', 'OTHER', 'RESOLVED');
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: RESOLVED report without resolution accepted'; END IF;

    ok := false;
    BEGIN
        INSERT INTO user_reports (reporter_user_id, request_id, reason)
        VALUES ('00000000-0000-0000-0000-0000000000a3', '00000000-0000-0000-0000-0000000000b2', 'BOGUS');
    EXCEPTION WHEN check_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: bad report reason accepted'; END IF;
END $$;

INSERT INTO request_moderation_holds (id, request_id, report_id, reason, placed_by)
VALUES ('00000000-0000-0000-0000-000000000bb1', '00000000-0000-0000-0000-0000000000b2',
        '00000000-0000-0000-0000-000000000aa1', 'Held for review', '00000000-0000-0000-0000-0000000000a3');

DO $$
DECLARE ok boolean;
BEGIN
    ok := false;
    BEGIN
        INSERT INTO request_moderation_holds (request_id, reason, placed_by)
        VALUES ('00000000-0000-0000-0000-0000000000b2', 'Second hold', '00000000-0000-0000-0000-0000000000a3');
    EXCEPTION WHEN unique_violation THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: second active hold on same request accepted'; END IF;

    IF EXISTS (SELECT 1 FROM discovery_public_requests WHERE id = '00000000-0000-0000-0000-0000000000b2') THEN
        RAISE EXCEPTION 'FAIL: held APPROVED request still in discovery_public_requests';
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.columns
               WHERE table_name = 'discovery_public_requests'
                 AND (column_name LIKE 'fee%' OR column_name LIKE '%document%' OR column_name LIKE '%sha256%' OR column_name LIKE '%email%')) THEN
        RAISE EXCEPTION 'FAIL: view exposes a private column after V7';
    END IF;

    -- Releasing the hold makes the request public again, and a new hold is then allowed
    UPDATE request_moderation_holds SET released_at = now(), released_by = '00000000-0000-0000-0000-0000000000a3'
    WHERE id = '00000000-0000-0000-0000-000000000bb1';
    IF NOT EXISTS (SELECT 1 FROM discovery_public_requests WHERE id = '00000000-0000-0000-0000-0000000000b2') THEN
        RAISE EXCEPTION 'FAIL: released hold still hides the request';
    END IF;
    INSERT INTO request_moderation_holds (request_id, reason, placed_by)
    VALUES ('00000000-0000-0000-0000-0000000000b2', 'Hold again', '00000000-0000-0000-0000-0000000000a3');
END $$;

-- Identity security (V8) -----------------------------------------------------
DO $$
BEGIN
    IF (SELECT credentials_version FROM users WHERE id = '00000000-0000-0000-0000-0000000000a1') <> 0 THEN
        RAISE EXCEPTION 'FAIL: credentials_version should default to 0';
    END IF;
END $$;

-- Append-only history (V9) ---------------------------------------------------
INSERT INTO consent_records (id, user_id, policy_type, policy_version)
VALUES ('00000000-0000-0000-0000-000000000cc1', '00000000-0000-0000-0000-0000000000a1', 'TERMS', '2026-11-01');

INSERT INTO funding_request_status_history (id, request_id, to_status)
VALUES ('00000000-0000-0000-0000-000000000dd1', '00000000-0000-0000-0000-0000000000b1', 'DRAFT');

DO $$
DECLARE ok boolean;
BEGIN
    ok := false;
    BEGIN UPDATE verification_decisions SET rationale = 'Tampered rationale text.';
    EXCEPTION WHEN raise_exception THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: verification_decisions UPDATE allowed'; END IF;

    ok := false;
    BEGIN DELETE FROM verification_decisions;
    EXCEPTION WHEN raise_exception THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: verification_decisions DELETE allowed'; END IF;

    ok := false;
    BEGIN UPDATE funding_request_status_history SET to_status = 'APPROVED';
    EXCEPTION WHEN raise_exception THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: funding_request_status_history UPDATE allowed'; END IF;

    ok := false;
    BEGIN DELETE FROM funding_request_status_history;
    EXCEPTION WHEN raise_exception THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: funding_request_status_history DELETE allowed'; END IF;

    ok := false;
    BEGIN UPDATE consent_records SET policy_version = 'tampered';
    EXCEPTION WHEN raise_exception THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: consent_records UPDATE allowed'; END IF;

    ok := false;
    BEGIN DELETE FROM consent_records;
    EXCEPTION WHEN raise_exception THEN ok := true; END;
    IF NOT ok THEN RAISE EXCEPTION 'FAIL: consent_records DELETE allowed'; END IF;
END $$;

DO $$ BEGIN RAISE NOTICE 'verify.sql: ALL CHECKS PASSED'; END $$;


ROLLBACK;
