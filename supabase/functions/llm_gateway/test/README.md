# `llm_gateway` test harnesses

This directory holds two kinds of files:

1. **Unit tests** (`*.test.ts`): run automatically by `npx vitest run`. Network calls are mocked.
2. **Live harnesses** (`*.harness.ts`): manual scripts gated by an env var (`RUN_LIVE=1`). Skipped under default `vitest run`. Used during Phase I deploy verification.

## `cache_hit_rate.harness.ts` — SC-014-002 verification

Verifies that prompt caching is functioning end-to-end on a live deployment by issuing 100 identical `classify_intent` requests, then querying `audit_log_entries` for the resulting `cacheHit` ratio.

**Pass condition:** ratio ≥ 0.80 (matches Success Criterion SC-014-002 in the spec).

### Run

```bash
RUN_LIVE=1 \
GATEWAY_URL=https://<your-vercel-deploy>/llm_gateway \
USER_JWT=<authenticated supabase session JWT> \
SUPABASE_URL=https://<project>.supabase.co \
SUPABASE_SERVICE_ROLE_KEY=<service role> \
npx tsx supabase/functions/llm_gateway/test/cache_hit_rate.harness.ts
```

### Behavior contract

| Env var               | Required | Purpose                                                    |
| --------------------- | -------- | ---------------------------------------------------------- |
| `RUN_LIVE`            | yes      | If unset, the script prints a skip notice and exits 0.     |
| `GATEWAY_URL`         | yes      | Full URL of the deployed `llm_gateway` Edge Function.      |
| `USER_JWT`            | yes      | Authenticated Supabase session JWT (any test user).        |
| `SUPABASE_URL`        | yes      | Project URL.                                               |
| `SUPABASE_SERVICE_ROLE_KEY` | yes | Service-role key (bypasses RLS to read all audit rows).   |

Exit codes:

- `0` — PASS (ratio ≥ 0.80) **or** skipped because `RUN_LIVE` was unset.
- `1` — FAIL (ratio < 0.80).
- `2` — Operational error (missing env, audit query failed, no rows).

### Notes

- The harness sends the **same** `text` and `appCategory` on every call so that the cached system block dominates the input. Anthropic ephemeral cache is keyed on the cached content blocks, not on the user message, so cacheHit should still be true even if the user message varies — but we keep it constant to keep the signal clean.
- The first request is expected to miss; the remaining 99 should hit. So the "ideal" ratio is 0.99. The 0.80 threshold gives slack for occasional cache evictions and upstream failures.
- Audit insert failures are non-blocking on the request path (T014-014), so missing audit rows do **not** fail individual requests — but they will reduce the denominator. The harness counts only `success: true` audit rows.
