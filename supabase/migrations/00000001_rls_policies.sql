-- supabase/migrations/00000001_rls_policies.sql
--
-- Orbit Cloud — Row-Level Security policies.
--
-- Spec:     specs/013-cloud-llm-routing/spec.md
-- Contract: specs/013-cloud-llm-routing/contracts/supabase-rls-contract.md
-- Task:     T013-024 (FR-013-024)
-- ADR:      ADR-007 (RLS is the ONLY isolation mechanism; NFR-013-005)
--
-- Pattern: every table gets RLS enabled + four policies (SELECT/INSERT/
-- UPDATE/DELETE) bound to `auth.uid() = user_id`. The audit_log_entries
-- table is append-only — UPDATE and DELETE policies evaluate to false.
--
-- ⚠️  WARNING — `SECURITY DEFINER` future-proofing
-- Any function added by a downstream spec with SECURITY DEFINER MUST
-- explicitly re-check auth.uid() inside the function body. Failing to
-- do so silently bypasses every policy here. See contract §4.

BEGIN;

-- ─── envelopes ────────────────────────────────────────────────────────────
ALTER TABLE envelopes ENABLE ROW LEVEL SECURITY;
CREATE POLICY select_own_rows ON envelopes
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY insert_own_rows ON envelopes
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY update_own_rows ON envelopes
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY delete_own_rows ON envelopes
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- ─── continuations ────────────────────────────────────────────────────────
ALTER TABLE continuations ENABLE ROW LEVEL SECURITY;
CREATE POLICY select_own_rows ON continuations
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY insert_own_rows ON continuations
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY update_own_rows ON continuations
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY delete_own_rows ON continuations
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- ─── continuation_results ─────────────────────────────────────────────────
ALTER TABLE continuation_results ENABLE ROW LEVEL SECURITY;
CREATE POLICY select_own_rows ON continuation_results
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY insert_own_rows ON continuation_results
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY update_own_rows ON continuation_results
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY delete_own_rows ON continuation_results
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- ─── clusters ─────────────────────────────────────────────────────────────
ALTER TABLE clusters ENABLE ROW LEVEL SECURITY;
CREATE POLICY select_own_rows ON clusters
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY insert_own_rows ON clusters
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY update_own_rows ON clusters
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY delete_own_rows ON clusters
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- ─── cluster_members ──────────────────────────────────────────────────────
ALTER TABLE cluster_members ENABLE ROW LEVEL SECURITY;
CREATE POLICY select_own_rows ON cluster_members
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY insert_own_rows ON cluster_members
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY update_own_rows ON cluster_members
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY delete_own_rows ON cluster_members
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- ─── action_proposals ─────────────────────────────────────────────────────
ALTER TABLE action_proposals ENABLE ROW LEVEL SECURITY;
CREATE POLICY select_own_rows ON action_proposals
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY insert_own_rows ON action_proposals
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY update_own_rows ON action_proposals
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY delete_own_rows ON action_proposals
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- ─── action_executions ────────────────────────────────────────────────────
ALTER TABLE action_executions ENABLE ROW LEVEL SECURITY;
CREATE POLICY select_own_rows ON action_executions
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY insert_own_rows ON action_executions
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY update_own_rows ON action_executions
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY delete_own_rows ON action_executions
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

-- ─── audit_log_entries (append-only) ──────────────────────────────────────
-- SELECT and INSERT permitted (own rows only); UPDATE and DELETE explicitly
-- denied — audit is append-only per Constitution Principle XII.
ALTER TABLE audit_log_entries ENABLE ROW LEVEL SECURITY;
CREATE POLICY select_own_rows ON audit_log_entries
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY insert_own_rows ON audit_log_entries
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY no_update_audit ON audit_log_entries
    FOR UPDATE TO authenticated USING (false);
CREATE POLICY no_delete_audit ON audit_log_entries
    FOR DELETE TO authenticated USING (false);

-- ─── user_profiles ────────────────────────────────────────────────────────
ALTER TABLE user_profiles ENABLE ROW LEVEL SECURITY;
CREATE POLICY select_own_rows ON user_profiles
    FOR SELECT TO authenticated USING (auth.uid() = user_id);
CREATE POLICY insert_own_rows ON user_profiles
    FOR INSERT TO authenticated WITH CHECK (auth.uid() = user_id);
CREATE POLICY update_own_rows ON user_profiles
    FOR UPDATE TO authenticated USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id);
CREATE POLICY delete_own_rows ON user_profiles
    FOR DELETE TO authenticated USING (auth.uid() = user_id);

COMMIT;
