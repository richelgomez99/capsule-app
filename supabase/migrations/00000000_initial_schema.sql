-- supabase/migrations/00000000_initial_schema.sql
--
-- Orbit Cloud — v1 initial schema.
--
-- Spec:        specs/013-cloud-llm-routing/spec.md
-- Data model:  specs/013-cloud-llm-routing/data-model.md  (§5)
-- Encryption:  specs/contracts/envelope-content-encryption-contract.md
-- Task:        T013-023 (FR-013-023)
--
-- Mirrors v1 of the on-device Room schema. Day-1 stores plaintext columns
-- (body, ocr_text, transcript, etc.). Reserved nullable `*_ct bytea`
-- columns are pre-allocated for spec 006 (Orbit Cloud Storage) which will
-- migrate writes to ciphertext. RLS policies are installed by the next
-- migration (00000001_rls_policies.sql).
--
-- ⚠️  WARNING — `SECURITY DEFINER` future-proofing
-- If a downstream migration adds a function with SECURITY DEFINER, that
-- function MUST explicitly re-check auth.uid(). Failure to do so silently
-- bypasses RLS for every caller. See supabase-rls-contract.md §4.

BEGIN;

-- ─── Extensions ───────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
CREATE EXTENSION IF NOT EXISTS vector;     -- pgvector for cluster embeddings

-- ─── 5.1  envelopes ───────────────────────────────────────────────────────
CREATE TABLE envelopes (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    device_id       text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    day_local       date NOT NULL,
    content_type    text NOT NULL,
    intent          text,
    intent_source   text,
    state_snapshot  jsonb,
    intent_history  jsonb,
    -- Day-1 plaintext (spec 006 migrates to *_ct):
    body            text,
    ocr_text        text,
    transcript      text,
    media_ref       text,
    -- Reserved ciphertext columns (spec 006 — Orbit Cloud Storage):
    body_ct         bytea,
    ocr_ct          bytea,
    transcript_ct   bytea,
    media_ref_ct    bytea
);
CREATE INDEX idx_envelopes_user_id        ON envelopes(user_id);
CREATE INDEX idx_envelopes_user_day       ON envelopes(user_id, day_local);
CREATE INDEX idx_envelopes_user_created   ON envelopes(user_id, created_at DESC);

-- ─── 5.2  continuations ───────────────────────────────────────────────────
CREATE TABLE continuations (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    envelope_id       uuid NOT NULL REFERENCES envelopes(id) ON DELETE CASCADE,
    continuation_type text NOT NULL,
    status            text NOT NULL,
    attempt_count     int  NOT NULL DEFAULT 0
);
CREATE INDEX idx_continuations_user_id     ON continuations(user_id);
CREATE INDEX idx_continuations_envelope_id ON continuations(envelope_id);

-- ─── 5.3  continuation_results ────────────────────────────────────────────
CREATE TABLE continuation_results (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    continuation_id  uuid NOT NULL REFERENCES continuations(id) ON DELETE CASCADE,
    envelope_id      uuid NOT NULL REFERENCES envelopes(id) ON DELETE CASCADE,
    model_provenance text,
    result_json      jsonb,
    -- Reserved ciphertext (spec 006):
    result_ct        bytea
);
CREATE INDEX idx_continuation_results_user_id        ON continuation_results(user_id);
CREATE INDEX idx_continuation_results_continuation   ON continuation_results(continuation_id);

-- ─── 5.4  clusters ────────────────────────────────────────────────────────
CREATE TABLE clusters (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    summary       text,
    embedding     vector(1536),
    model_label   text NOT NULL,
    member_count  int  NOT NULL DEFAULT 0,
    -- Reserved ciphertext (spec 006):
    summary_ct    bytea
);
CREATE INDEX idx_clusters_user_id ON clusters(user_id);

-- ─── 5.5  cluster_members ─────────────────────────────────────────────────
CREATE TABLE cluster_members (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    cluster_id   uuid NOT NULL REFERENCES clusters(id) ON DELETE CASCADE,
    envelope_id  uuid NOT NULL REFERENCES envelopes(id) ON DELETE CASCADE,
    excerpt      text,
    embedding    vector(1536),
    -- Reserved ciphertext (spec 006):
    excerpt_ct   bytea,
    CONSTRAINT cluster_members_cluster_envelope_unique
      UNIQUE (cluster_id, envelope_id)
);
CREATE INDEX idx_cluster_members_user_id    ON cluster_members(user_id);
CREATE INDEX idx_cluster_members_cluster_id ON cluster_members(cluster_id);
CREATE INDEX idx_cluster_members_envelope   ON cluster_members(envelope_id);

-- ─── 5.6  action_proposals ────────────────────────────────────────────────
CREATE TABLE action_proposals (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    envelope_id  uuid NOT NULL REFERENCES envelopes(id) ON DELETE CASCADE,
    function_id  text NOT NULL,
    args_json    jsonb NOT NULL,
    confidence   real NOT NULL,
    state        text NOT NULL,
    -- Reserved ciphertext (spec 006):
    payload_ct   bytea
);
CREATE INDEX idx_action_proposals_user_id     ON action_proposals(user_id);
CREATE INDEX idx_action_proposals_envelope_id ON action_proposals(envelope_id);

-- ─── 5.7  action_executions ───────────────────────────────────────────────
CREATE TABLE action_executions (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    proposal_id   uuid NOT NULL REFERENCES action_proposals(id) ON DELETE CASCADE,
    outcome       text NOT NULL,
    result_json   jsonb,
    error_message text,
    -- Reserved ciphertext (spec 006):
    result_ct     bytea
);
CREATE INDEX idx_action_executions_user_id     ON action_executions(user_id);
CREATE INDEX idx_action_executions_proposal_id ON action_executions(proposal_id);

-- ─── 5.8  audit_log_entries ───────────────────────────────────────────────
-- Append-only. NO updated_at column (Constitution Principle XII).
-- The on-device audit log is NEVER uploaded (Constitution Principle X);
-- this table is reserved for cloud-action mirrors that downstream specs
-- will populate (e.g., Edge Function recording cloud LLM call metadata).
CREATE TABLE audit_log_entries (
    id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id       uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at    timestamptz NOT NULL DEFAULT now(),
    event_type    text NOT NULL,
    actor         text NOT NULL,
    subject_id    uuid,
    details_json  jsonb
);
CREATE INDEX idx_audit_log_user_id    ON audit_log_entries(user_id);
CREATE INDEX idx_audit_log_user_time  ON audit_log_entries(user_id, created_at DESC);

-- ─── 5.9  user_profiles ───────────────────────────────────────────────────
CREATE TABLE user_profiles (
    id            uuid PRIMARY KEY,
    user_id       uuid NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    display_name  text,
    settings_json jsonb,
    CONSTRAINT user_profiles_id_matches_user_id CHECK (id = user_id)
);
CREATE INDEX idx_user_profiles_user_id ON user_profiles(user_id);

-- ─── updated_at maintenance trigger ───────────────────────────────────────
-- Single shared trigger function bumps updated_at on every UPDATE.
-- audit_log_entries is intentionally excluded (no updated_at column).
CREATE OR REPLACE FUNCTION orbit_set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at := now();
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_envelopes_updated_at
  BEFORE UPDATE ON envelopes FOR EACH ROW EXECUTE FUNCTION orbit_set_updated_at();
CREATE TRIGGER trg_continuations_updated_at
  BEFORE UPDATE ON continuations FOR EACH ROW EXECUTE FUNCTION orbit_set_updated_at();
CREATE TRIGGER trg_continuation_results_updated_at
  BEFORE UPDATE ON continuation_results FOR EACH ROW EXECUTE FUNCTION orbit_set_updated_at();
CREATE TRIGGER trg_clusters_updated_at
  BEFORE UPDATE ON clusters FOR EACH ROW EXECUTE FUNCTION orbit_set_updated_at();
CREATE TRIGGER trg_cluster_members_updated_at
  BEFORE UPDATE ON cluster_members FOR EACH ROW EXECUTE FUNCTION orbit_set_updated_at();
CREATE TRIGGER trg_action_proposals_updated_at
  BEFORE UPDATE ON action_proposals FOR EACH ROW EXECUTE FUNCTION orbit_set_updated_at();
CREATE TRIGGER trg_action_executions_updated_at
  BEFORE UPDATE ON action_executions FOR EACH ROW EXECUTE FUNCTION orbit_set_updated_at();
CREATE TRIGGER trg_user_profiles_updated_at
  BEFORE UPDATE ON user_profiles FOR EACH ROW EXECUTE FUNCTION orbit_set_updated_at();

COMMIT;
