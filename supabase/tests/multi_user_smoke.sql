-- supabase/tests/multi_user_smoke.sql
--
-- Orbit Cloud — Multi-user RLS isolation smoke test (RELEASE GATE).
--
-- Spec:     specs/013-cloud-llm-routing/spec.md
-- Contract: specs/013-cloud-llm-routing/contracts/supabase-rls-contract.md  §3
-- Task:     T013-026 (FR-013-026); executed by T013-028 (alpha gate)
-- ADR:      ADR-007 (RLS is the ONLY isolation mechanism; alpha gate)
-- SC:       SC-007 / SC-008
--
-- Invocation:
--   psql "$SUPABASE_DB_URL" -v ON_ERROR_STOP=1 -f supabase/tests/multi_user_smoke.sql
--
-- Exit 0 + stdout contains "PASS"  → release gate cleared.
-- Otherwise (exit non-zero or stdout contains "FAIL")  → DO NOT SHIP.
--
-- ─── How RLS is exercised here ───────────────────────────────────────────
-- Supabase RLS reads the current identity from auth.uid(), which itself
-- reads the 'sub' claim from request.jwt.claims (a session GUC). To
-- simulate two authenticated users from a single psql session:
--   1. SET ROLE authenticated         — engages the policies bound to
--                                       the `authenticated` Postgres role
--   2. SET LOCAL request.jwt.claims   — sets the JWT 'sub' that auth.uid()
--                                       reads inside that transaction
-- A RESET ROLE between probes returns to the bootstrap superuser context
-- (used to provision and tear down auth.users + own rows).
--
-- ─── Expected output shape (success) ─────────────────────────────────────
-- Every "ASSERT" check that passes prints `ok` (psql \echo on a true expr).
-- A single `PASS` line at the end confirms every probe passed.

\set ON_ERROR_STOP 1
\set QUIET 1
\pset pager off
\pset format unaligned

\echo === multi_user_smoke.sql start ===

-- ─── Bootstrap: two auth.users (A and B) ──────────────────────────────────
BEGIN;
RESET ROLE;

DELETE FROM auth.users WHERE id IN (
  '00000000-0000-0000-0000-00000000000a',
  '00000000-0000-0000-0000-00000000000b'
);

INSERT INTO auth.users (id, instance_id, aud, role, email, encrypted_password,
  email_confirmed_at, created_at, updated_at, raw_app_meta_data, raw_user_meta_data)
VALUES
  ('00000000-0000-0000-0000-00000000000a',
   '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated',
   'smoke-a@example.invalid', '',
   now(), now(), now(), '{}'::jsonb, '{}'::jsonb),
  ('00000000-0000-0000-0000-00000000000b',
   '00000000-0000-0000-0000-000000000000',
   'authenticated', 'authenticated',
   'smoke-b@example.invalid', '',
   now(), now(), now(), '{}'::jsonb, '{}'::jsonb);
COMMIT;

-- ─── User A: insert own envelope + cluster + member + proposal ────────────
BEGIN;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}',
  true);

INSERT INTO envelopes (id, user_id, device_id, day_local, content_type, body)
VALUES ('aaaaaaaa-0000-0000-0000-00000000000a',
        '00000000-0000-0000-0000-00000000000a',
        'smoke-A', current_date, 'note', 'A-envelope-1');

INSERT INTO clusters (id, user_id, model_label)
VALUES ('cccccccc-0000-0000-0000-00000000000a',
        '00000000-0000-0000-0000-00000000000a',
        'smoke-label');

INSERT INTO cluster_members (id, user_id, cluster_id, envelope_id, excerpt)
VALUES ('bbbbbbbb-0000-0000-0000-00000000000a',
        '00000000-0000-0000-0000-00000000000a',
        'cccccccc-0000-0000-0000-00000000000a',
        'aaaaaaaa-0000-0000-0000-00000000000a',
        'A-cluster-member');

INSERT INTO action_proposals (id, user_id, envelope_id, function_id, args_json, confidence, state)
VALUES ('dddddddd-0000-0000-0000-00000000000a',
        '00000000-0000-0000-0000-00000000000a',
        'aaaaaaaa-0000-0000-0000-00000000000a',
        'noop.fn', '{}'::jsonb, 0.9, 'pending');
COMMIT;

-- ─── User B: insert own analogous rows ────────────────────────────────────
BEGIN;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}',
  true);

INSERT INTO envelopes (id, user_id, device_id, day_local, content_type, body)
VALUES ('aaaaaaaa-0000-0000-0000-00000000000b',
        '00000000-0000-0000-0000-00000000000b',
        'smoke-B', current_date, 'note', 'B-envelope-1');

INSERT INTO clusters (id, user_id, model_label)
VALUES ('cccccccc-0000-0000-0000-00000000000b',
        '00000000-0000-0000-0000-00000000000b',
        'smoke-label');

INSERT INTO cluster_members (id, user_id, cluster_id, envelope_id, excerpt)
VALUES ('bbbbbbbb-0000-0000-0000-00000000000b',
        '00000000-0000-0000-0000-00000000000b',
        'cccccccc-0000-0000-0000-00000000000b',
        'aaaaaaaa-0000-0000-0000-00000000000b',
        'B-cluster-member');

INSERT INTO action_proposals (id, user_id, envelope_id, function_id, args_json, confidence, state)
VALUES ('dddddddd-0000-0000-0000-00000000000b',
        '00000000-0000-0000-0000-00000000000b',
        'aaaaaaaa-0000-0000-0000-00000000000b',
        'noop.fn', '{}'::jsonb, 0.9, 'pending');
COMMIT;

-- ─── Probes as user B ─────────────────────────────────────────────────────
-- Each probe is wrapped in a DO block that RAISEs on assertion failure
-- (causing ON_ERROR_STOP to abort with non-zero exit). Every assertion
-- that passes prints `ok`.

BEGIN;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}',
  true);

DO $$
DECLARE
    n int;
BEGIN
    -- B can see own envelope (sanity).
    SELECT count(*) INTO n FROM envelopes
      WHERE id = 'aaaaaaaa-0000-0000-0000-00000000000b';
    IF n <> 1 THEN
        RAISE EXCEPTION 'FAIL B cannot see own envelope (count=%)', n;
    END IF;

    -- B cannot see A's envelope.
    SELECT count(*) INTO n FROM envelopes
      WHERE id = 'aaaaaaaa-0000-0000-0000-00000000000a';
    IF n <> 0 THEN
        RAISE EXCEPTION 'FAIL B saw A''s envelope (count=%) — RLS LEAK', n;
    END IF;

    -- B's full envelope SELECT returns only B's row.
    SELECT count(*) INTO n FROM envelopes;
    IF n <> 1 THEN
        RAISE EXCEPTION 'FAIL B sees % envelopes total (expected 1)', n;
    END IF;

    -- B's UPDATE against A's PK affects 0 rows.
    UPDATE envelopes SET body = 'pwn'
      WHERE id = 'aaaaaaaa-0000-0000-0000-00000000000a';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN
        RAISE EXCEPTION 'FAIL B updated A''s envelope (% rows) — RLS LEAK', n;
    END IF;

    -- B's DELETE against A's PK affects 0 rows.
    DELETE FROM envelopes
      WHERE id = 'aaaaaaaa-0000-0000-0000-00000000000a';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN
        RAISE EXCEPTION 'FAIL B deleted A''s envelope (% rows) — RLS LEAK', n;
    END IF;

    -- Same probes for clusters.
    SELECT count(*) INTO n FROM clusters
      WHERE id = 'cccccccc-0000-0000-0000-00000000000a';
    IF n <> 0 THEN
        RAISE EXCEPTION 'FAIL B saw A''s cluster — RLS LEAK';
    END IF;

    UPDATE clusters SET model_label = 'pwn'
      WHERE id = 'cccccccc-0000-0000-0000-00000000000a';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN
        RAISE EXCEPTION 'FAIL B updated A''s cluster — RLS LEAK';
    END IF;

    DELETE FROM clusters
      WHERE id = 'cccccccc-0000-0000-0000-00000000000a';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN
        RAISE EXCEPTION 'FAIL B deleted A''s cluster — RLS LEAK';
    END IF;

    -- Same probes for cluster_members.
    SELECT count(*) INTO n FROM cluster_members
      WHERE id = 'bbbbbbbb-0000-0000-0000-00000000000a';
    IF n <> 0 THEN
        RAISE EXCEPTION 'FAIL B saw A''s cluster_member — RLS LEAK';
    END IF;

    UPDATE cluster_members SET excerpt = 'pwn'
      WHERE id = 'bbbbbbbb-0000-0000-0000-00000000000a';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN
        RAISE EXCEPTION 'FAIL B updated A''s cluster_member — RLS LEAK';
    END IF;

    DELETE FROM cluster_members
      WHERE id = 'bbbbbbbb-0000-0000-0000-00000000000a';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN
        RAISE EXCEPTION 'FAIL B deleted A''s cluster_member — RLS LEAK';
    END IF;

    -- Same probes for action_proposals.
    SELECT count(*) INTO n FROM action_proposals
      WHERE id = 'dddddddd-0000-0000-0000-00000000000a';
    IF n <> 0 THEN
        RAISE EXCEPTION 'FAIL B saw A''s action_proposal — RLS LEAK';
    END IF;

    UPDATE action_proposals SET state = 'pwn'
      WHERE id = 'dddddddd-0000-0000-0000-00000000000a';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN
        RAISE EXCEPTION 'FAIL B updated A''s action_proposal — RLS LEAK';
    END IF;

    DELETE FROM action_proposals
      WHERE id = 'dddddddd-0000-0000-0000-00000000000a';
    GET DIAGNOSTICS n = ROW_COUNT;
    IF n <> 0 THEN
        RAISE EXCEPTION 'FAIL B deleted A''s action_proposal — RLS LEAK';
    END IF;

    -- pgvector + dimensionality probe (1536d, matches text-embedding-3-small).
    INSERT INTO clusters (id, user_id, model_label, embedding)
    VALUES ('eeeeeeee-0000-0000-0000-00000000000b',
            '00000000-0000-0000-0000-00000000000b',
            'pgvector-probe',
            (SELECT array_fill(0.0::real, ARRAY[1536])::vector));

    SELECT count(*) INTO n FROM clusters
      WHERE id = 'eeeeeeee-0000-0000-0000-00000000000b'
        AND vector_dims(embedding) = 1536;
    IF n <> 1 THEN
        RAISE EXCEPTION 'FAIL pgvector(1536) probe failed (count=%)', n;
    END IF;
END $$;
COMMIT;

-- ─── Re-confirm A's data still intact (rules out over-restrictive RLS) ────
BEGIN;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}',
  true);

DO $$
DECLARE
    n int;
BEGIN
    SELECT count(*) INTO n FROM envelopes
      WHERE id = 'aaaaaaaa-0000-0000-0000-00000000000a';
    IF n <> 1 THEN
        RAISE EXCEPTION 'FAIL A cannot see own envelope (count=%)', n;
    END IF;

    SELECT count(*) INTO n FROM clusters
      WHERE id = 'cccccccc-0000-0000-0000-00000000000a';
    IF n <> 1 THEN
        RAISE EXCEPTION 'FAIL A cannot see own cluster (count=%)', n;
    END IF;

    SELECT count(*) INTO n FROM cluster_members
      WHERE id = 'bbbbbbbb-0000-0000-0000-00000000000a';
    IF n <> 1 THEN
        RAISE EXCEPTION 'FAIL A cannot see own cluster_member (count=%)', n;
    END IF;

    SELECT count(*) INTO n FROM action_proposals
      WHERE id = 'dddddddd-0000-0000-0000-00000000000a';
    IF n <> 1 THEN
        RAISE EXCEPTION 'FAIL A cannot see own action_proposal (count=%)', n;
    END IF;
END $$;
COMMIT;

-- ─── Spec 014: cost_per_user_daily view RLS isolation (SC-014-007) ────────
-- A inserts one cloud_llm_call audit row. B then queries cost_per_user_daily
-- filtered to A's user_id and MUST see 0 rows (the view inherits RLS from
-- audit_log_entries).

BEGIN;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}',
  true);

INSERT INTO audit_log_entries (user_id, event_type, actor, subject_id, details_json)
VALUES (
  '00000000-0000-0000-0000-00000000000a',
  'cloud_llm_call',
  'edge_function',
  NULL,
  jsonb_build_object(
    'requestId',  '550e8400-e29b-41d4-a716-446655440777',
    'requestType','classify_intent',
    'model',      'claude-haiku-4-5',
    'modelLabel', 'anthropic/claude-haiku-4-5',
    'latencyMs',  120,
    'tokensIn',   800,
    'tokensOut',  20,
    'cacheHit',   true,
    'success',    true
  )
);
COMMIT;

BEGIN;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-00000000000b","role":"authenticated"}',
  true);

DO $$
DECLARE
    n int;
BEGIN
    -- B querying for A's cost rows must see 0 (RLS inheritance).
    SELECT count(*) INTO n FROM cost_per_user_daily
      WHERE user_id = '00000000-0000-0000-0000-00000000000a';
    IF n <> 0 THEN
        RAISE EXCEPTION
          'FAIL B saw A''s cost_per_user_daily rows (count=%) — RLS LEAK', n;
    END IF;

    -- B's view of cost_per_user_daily must be empty for B (B has no cloud_llm_call rows).
    SELECT count(*) INTO n FROM cost_per_user_daily
      WHERE user_id = '00000000-0000-0000-0000-00000000000b';
    IF n <> 0 THEN
        RAISE EXCEPTION
          'FAIL B''s cost view contains unexpected rows (count=%)', n;
    END IF;

    RAISE NOTICE 'cost_per_user_daily RLS isolation: ok';
END $$;
COMMIT;

-- A re-confirms its own row IS visible to itself via the view.
BEGIN;
SET LOCAL ROLE authenticated;
SELECT set_config('request.jwt.claims',
  '{"sub":"00000000-0000-0000-0000-00000000000a","role":"authenticated"}',
  true);

DO $$
DECLARE
    n int;
BEGIN
    SELECT count(*) INTO n FROM cost_per_user_daily
      WHERE user_id = '00000000-0000-0000-0000-00000000000a';
    IF n <> 1 THEN
        RAISE EXCEPTION
          'FAIL A cannot see own cost_per_user_daily row (count=%)', n;
    END IF;
END $$;
COMMIT;

-- ─── Cleanup ──────────────────────────────────────────────────────────────
BEGIN;
RESET ROLE;
DELETE FROM auth.users WHERE id IN (
  '00000000-0000-0000-0000-00000000000a',
  '00000000-0000-0000-0000-00000000000b'
);
COMMIT;

\echo PASS
\echo === multi_user_smoke.sql end ===
