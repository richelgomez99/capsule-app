-- Spec 014 — T014-015 (Phase G): cost_per_user_daily observability view.
-- See specs/014-edge-function-llm-gateway/data-model.md §5.1.
--
-- Day-2 cost observability view. Aggregates per-user daily token usage into a
-- USD cost estimate using a static rate table embedded in the view. NO ENFORCEMENT —
-- this is observability only (FR-014-023). Hard quota cutoff is spec 005's responsibility.
--
-- Rates as of 2026-04-29 (USD per 1,000,000 tokens). Updates land in a new migration.

CREATE OR REPLACE VIEW cost_per_user_daily AS
WITH rates(model_label, input_per_million_usd, output_per_million_usd) AS (
  VALUES
    ('anthropic/claude-sonnet-4-6'::text, 3.00::numeric, 15.00::numeric),
    ('anthropic/claude-haiku-4-5'::text,  0.25::numeric,  1.25::numeric),
    ('openai/text-embedding-3-small'::text, 0.02::numeric, 0.00::numeric)
),
calls AS (
  SELECT
    a.user_id,
    (a.created_at AT TIME ZONE 'UTC')::date AS date_utc,
    (a.details_json ->> 'modelLabel')                     AS model_label,
    COALESCE((a.details_json ->> 'tokensIn')::int,  0)    AS tokens_in,
    COALESCE((a.details_json ->> 'tokensOut')::int, 0)    AS tokens_out,
    COALESCE((a.details_json ->> 'success')::boolean, false) AS success
  FROM audit_log_entries a
  WHERE a.event_type = 'cloud_llm_call'
)
SELECT
  c.user_id,
  c.date_utc,
  COUNT(*)                                           AS request_count,
  COUNT(*) FILTER (WHERE c.success)                  AS success_count,
  SUM(c.tokens_in)                                   AS tokens_in_total,
  SUM(c.tokens_out)                                  AS tokens_out_total,
  ROUND(
    SUM(
      (c.tokens_in::numeric / 1000000) * COALESCE(r.input_per_million_usd, 0)
      + (c.tokens_out::numeric / 1000000) * COALESCE(r.output_per_million_usd, 0)
    )::numeric,
    4
  ) AS cost_usd_estimate
FROM calls c
LEFT JOIN rates r ON r.model_label = c.model_label
GROUP BY c.user_id, c.date_utc;

COMMENT ON VIEW cost_per_user_daily IS
  'Day-2 cost observability (FR-014-024). Estimates USD cost per user per UTC date from '
  'audit_log_entries.cloud_llm_call rows using a static rate table. No enforcement; '
  'spec 005 owns hard quota.';
