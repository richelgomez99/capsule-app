# Audit Row Contract (Day 2)

**Boundary**: Edge Function `→` Supabase `audit_log_entries` table (service-role insert).
**Status**: STABLE for Day 2 — `details_json` shape is **fully specified, no nullable
fields, no optional fields except `errorCode` on success rows**.

This contract specifies the exact `audit_log_entries` row written by the Edge Function for
every request that passes the auth gate (success or upstream failure). The table schema
itself is unchanged from spec 013 — only the `details_json` payload is defined here.

---

## 1. Insert shape

For every request past the auth gate, exactly one row:

```sql
INSERT INTO audit_log_entries
  (user_id, event_type, actor, subject_id, details_json, created_at)
VALUES
  (
    '<sub claim from verified JWT>'::uuid,
    'cloud_llm_call',
    'edge_function',
    NULL,                         -- subject_id unused in Day 2
    '<json described below>'::jsonb,
    now()                         -- table default; explicitly stated for clarity
  );
```

### Column constraints

| Column | Day-2 value | Notes |
|--------|-------------|-------|
| `id` | `gen_random_uuid()` (table default) | Function does not provide. |
| `user_id` | JWT `sub` claim, UUID | Stamped by function (service-role bypasses RLS, FR-014-013). |
| `created_at` | `now()` (table default) | Function does not provide; UTC timestamp from Postgres. |
| `event_type` | literal `'cloud_llm_call'` | New event type for spec 014. Future specs may add others. |
| `actor` | literal `'edge_function'` | Distinguishes server-mirrored audits from Android-mirrored ones (future specs). |
| `subject_id` | `NULL` | Reserved for future use (e.g., capsule-id linkage). |
| `details_json` | per §2 below | Validated against the documented shape. |

---

## 2. `details_json` payload — fully specified

### 2.1 Success row (every field present, no nulls)

```json
{
  "requestId": "<UUIDv4>",
  "requestType": "<one of the six types from gateway-request-response.md §3>",
  "model": "<upstream model id, e.g. 'claude-haiku-4-5'>",
  "modelLabel": "<wire-format label, e.g. 'anthropic/claude-haiku-4-5'>",
  "latencyMs": <integer ≥ 0>,
  "tokensIn": <integer ≥ 0>,
  "tokensOut": <integer ≥ 0>,
  "cacheHit": <boolean>,
  "success": true
}
```

### 2.2 Error row (every field present, no nulls; adds `errorCode`)

```json
{
  "requestId": "<UUIDv4>",
  "requestType": "<one of the six types>",
  "model": "<configured upstream model for this request type>",
  "modelLabel": "<wire-format label>",
  "latencyMs": <integer ≥ 0, time spent before failure>,
  "tokensIn": 0,
  "tokensOut": 0,
  "cacheHit": false,
  "success": false,
  "errorCode": "<one of the seven ErrorCode values from gateway-request-response.md §6>"
}
```

### 2.3 Field-by-field rules (Day 2, no exceptions)

| Field | Type | Required | Rules |
|-------|------|----------|-------|
| `requestId` | string | yes | UUIDv4 from inbound request envelope. NEVER null, NEVER empty. |
| `requestType` | string | yes | Closed enum: `embed`, `summarize`, `extract_actions`, `classify_intent`, `generate_day_header`, `scan_sensitivity`. |
| `model` | string | yes | Upstream model id (the bare model name, e.g. `"claude-haiku-4-5"`, `"text-embedding-3-small"`). |
| `modelLabel` | string | yes | Provider-prefixed wire label. Stable across Day 2 (see [gateway-request-response.md §3](gateway-request-response.md#3-model-routing-table-fr-014-002)). |
| `latencyMs` | number (integer) | yes | `Math.round(performance.now() - upstreamStart)`. Excludes audit insert time. ≥ 0. |
| `tokensIn` | number (integer) | yes | From upstream usage (Anthropic `input_tokens`, OpenAI `prompt_tokens`). On error rows: **0**, never null. |
| `tokensOut` | number (integer) | yes | From upstream usage (Anthropic `output_tokens`, OpenAI `completion_tokens`). Embeddings: **0**. Errors: **0**. |
| `cacheHit` | boolean | yes | True iff Anthropic returned `cache_read_input_tokens > 0`. Always `false` for non-cached request types (`embed`, `summarize`, `extract_actions`, `generate_day_header`). Always `false` on error rows. |
| `success` | boolean | yes | Strict — `true` ⇔ upstream returned a parseable success. Anything else is `false`. |
| `errorCode` | string | only when `success: false` | One of the seven `ErrorCode` values. Absent on success rows. |

**No nullable fields, no optional fields beyond `errorCode`.** This is intentional — the
`cost_per_user_daily` view depends on stable types when extracting via `details_json->>'tokensIn'`.

---

## 3. Bounded Observation invariants (Constitution Principle XIV, FR-014-012)

`details_json` MUST NOT contain:

- Prompt text or any portion of the user-supplied request payload (`payload.text`,
  `payload.envelopeSummaries`, etc.).
- Response text from any LLM (`summary`, `header`, classified `intent` *string content*,
  extracted action proposals, sensitivity tags).
- Embedding vectors (any element of `vector`).
- JWT contents (header, payload claims, signature).
- IP addresses, user-agent strings, or any client-fingerprint data.
- Any field beyond those listed in §2.1 / §2.2.

**The classified `intent` string is forbidden** — even though it is short and looks innocuous,
it is a derived fact about the user's content (Constitution Principle XII) and inclusion in
audit telemetry would leak intent classifications through any operator with audit-log read
access. The `cacheHit` flag is the cost-shaping signal; the actual classification result
goes only to the calling client.

Adding any field requires a spec amendment and a corresponding update to this contract,
[data-model.md](../data-model.md), and the operator log shape (FR-014-012).

---

## 4. Audit insert failure handling (FR-014-014)

The audit insert MUST NOT degrade user-facing functionality. If the insert throws:

1. The function STILL returns the upstream LLM response to the client (success or error).
2. The function emits a single error-level log line:
   ```
   { "level": "error", "requestId": "<id>", "audit_insert_failed": true }
   ```
   with no other fields, no prompt content, no response content.
3. The function does NOT retry the insert and does NOT queue it for later. Day-2
   acceptance: lost audit rows are observable via the missing-row pattern in the daily cost
   view (a user with active capture but zero audit rows for a day is anomalous).

Hardening (durable audit queue, retry-with-backoff) is deferred to a future spec.

---

## 5. RLS interaction (SC-014-007)

`audit_log_entries` has `WITH CHECK (auth.uid() = user_id)` for INSERT under spec 013's RLS
contract. The function uses the **service-role** client which bypasses RLS, so the policy is
not evaluated at insert time. However, the function MUST stamp `user_id` from the JWT `sub`
explicitly so that:

1. Users querying their own audit rows (subject to `SELECT` RLS `auth.uid() = user_id`) see
   their data correctly.
2. The Day-1 multi-user smoke test (`supabase/tests/multi_user_smoke.sql`) continues to
   pass — User B cannot see User A's audit rows because `auth.uid() = user_id` filters them
   out on read.
3. Spec 005 (BYOK + quota) can later add `SELECT` queries on `audit_log_entries` directly
   from the Android client without trust-boundary concern.

The function MUST NOT use the anon-key client to insert audit rows. Anon-key would require
the user's JWT in the request header (which we have) but would also require the
`auth.uid() = user_id` policy to evaluate true — which it would, redundantly. We use
service-role for two reasons: (a) no per-request user-scoped client construction overhead,
(b) the function is acting on behalf of the user but is not running *as* the user in the
PostgREST sense.

---

## 6. Observability log line (separate from the audit row)

The function emits exactly **one** structured log line per request via `console.log` (Vercel
captures stdout). Shape per FR-014-012:

```json
{
  "requestId": "...",
  "userId": "<sub claim>",
  "requestType": "...",
  "model": "...",
  "modelLabel": "...",
  "latencyMs": ...,
  "tokensIn": ...,
  "tokensOut": ...,
  "cacheHit": ...,
  "success": ...,
  "errorCode": "..."        // only on failure
}
```

This is the **operator-visible** log (Vercel logs UI, accessible to project owners). The
audit row in `audit_log_entries` is the **user-visible** record (queryable from the Android
client via Supabase auth). They mirror each other deliberately — operators can correlate
log lines to audit rows by `requestId`.

The log line MUST NOT contain any field outside the list above. `userId` is included in the
log line (operator-only) but NOT in the audit row's `details_json` (the row's own
`user_id` column already carries it).

---

## 7. Migration footprint

Spec 014 does NOT add any column to `audit_log_entries` or any new table. The
`event_type = 'cloud_llm_call'` rows land in the existing table.

The only new migration in spec 014 is `00000003_cost_per_user_daily.sql` (the read-side
view, see [data-model.md §5](../data-model.md#5-cost_per_user_daily-sql-view)).

---

## 8. Verification (SC-014-005, SC-014-006)

Acceptance check, runnable against the deployed function:

```sql
-- Pick a known signed-in user; make 100 calls across mixed types.
-- Then:
SELECT
  COUNT(*) FILTER (WHERE event_type = 'cloud_llm_call') AS rows,
  COUNT(*) FILTER (WHERE event_type = 'cloud_llm_call' AND details_json ? 'requestId'
                   AND details_json ? 'requestType' AND details_json ? 'model'
                   AND details_json ? 'modelLabel' AND details_json ? 'latencyMs'
                   AND details_json ? 'tokensIn' AND details_json ? 'tokensOut'
                   AND details_json ? 'cacheHit' AND details_json ? 'success') AS well_formed
FROM audit_log_entries
WHERE user_id = '<known user_id>'
  AND created_at > now() - interval '1 hour';
-- Expected: rows = 100, well_formed = 100.
```

For SC-014-006 (no prompt content in logs), the operator runs (Vercel CLI):

```sh
vercel logs <deployment-id> --since 1h \
  | grep -E '<UUID embedded in test prompts>' \
  || echo "PASS: no prompt content in logs"
```

Expected: zero matches → PASS.

---

## 9. Cross-references

- [gateway-request-response.md](gateway-request-response.md) — request/response wire format
- [auth-jwt-contract.md](auth-jwt-contract.md) — how `sub` flows to `user_id`
- [data-model.md §4](../data-model.md#4-audit-row-payload-audit_log_entriesdetails_json) —
  TypeScript types
- [data-model.md §5](../data-model.md#5-cost_per_user_daily-sql-view) —
  `cost_per_user_daily` view DDL
- [supabase/migrations/00000000_initial_schema.sql](../../../supabase/migrations/00000000_initial_schema.sql)
  §5.8 — table schema
- [supabase/migrations/00000001_rls_policies.sql](../../../supabase/migrations/00000001_rls_policies.sql)
  — RLS policy on `audit_log_entries`
- Spec FR-014-009, FR-014-012, FR-014-013, FR-014-014, FR-014-015
