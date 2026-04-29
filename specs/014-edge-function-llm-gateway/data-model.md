# Phase 1 Data Model — Vercel AI Gateway Edge Function (Day 2)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-04-29

This document defines every type and schema introduced by spec 014: TypeScript types in the
Edge Function (mirroring Kotlin sealed classes from spec 013), the JWT claim shape, the audit
row `details_json` payload, and the `cost_per_user_daily` SQL view DDL.

The wire format (HTTP/JSON) is unchanged from spec 013. This document covers what the
**server side** sees and produces.

---

## 1. TypeScript types — request envelope

Package: `supabase/functions/llm_gateway/types.ts` (or inline in `index.ts` for Day 2 small
codebase). Validated at runtime via Zod (see §6).

### 1.1 `LlmGatewayRequest` discriminated union

Mirrors the Kotlin sealed `LlmGatewayRequest` from
[`specs/013-cloud-llm-routing/data-model.md`](../013-cloud-llm-routing/data-model.md) §1.1.
Wire discriminator: `type` (lowercase snake_case).

```typescript
type LlmGatewayRequest =
  | EmbedRequest
  | SummarizeRequest
  | ExtractActionsRequest
  | ClassifyIntentRequest
  | GenerateDayHeaderRequest
  | ScanSensitivityRequest;

interface EmbedRequest {
  type: "embed";
  requestId: string;             // UUIDv4
  payload: { text: string };
}

interface SummarizeRequest {
  type: "summarize";
  requestId: string;
  payload: { text: string; maxTokens: number };
}

interface ExtractActionsRequest {
  type: "extract_actions";
  requestId: string;
  payload: {
    text: string;
    contentType: string;
    state: StateSnapshotJson;
    registeredFunctions: AppFunctionSummaryJson[];
    maxCandidates: number;       // default 3
  };
}

interface ClassifyIntentRequest {
  type: "classify_intent";
  requestId: string;
  payload: { text: string; appCategory: string };
}

interface GenerateDayHeaderRequest {
  type: "generate_day_header";
  requestId: string;
  payload: { dayIsoDate: string; envelopeSummaries: string[] };  // dayIsoDate = YYYY-MM-DD
}

interface ScanSensitivityRequest {
  type: "scan_sensitivity";
  requestId: string;
  payload: { text: string };
}
```

### 1.2 Supporting JSON-mirror types

```typescript
// Mirrors com.capsule.app.data.entity.StateSnapshot (Day-1 Kotlin).
// Field-for-field copy. Field nullability matches the Kotlin nullable (?).
interface StateSnapshotJson {
  foregroundApp: string | null;
  appCategory: string | null;
  activityState: string | null;
  hourLocal: number | null;
  dayOfWeek: string | null;
  // ...additional optional fields as Spec 002 evolves; new fields land in this mirror first.
}

// Mirrors com.capsule.app.ai.model.AppFunctionSummary (Day-1 Kotlin).
interface AppFunctionSummaryJson {
  id: string;
  name: string;
  schema: Record<string, unknown>;  // arbitrary JSON Schema fragment
}

// Mirrors com.capsule.app.actions.ActionProposal (Day-1 Kotlin).
interface ActionProposalJson {
  functionId: string;
  args: Record<string, unknown>;
  confidence: number;          // 0..1
  rationale: string | null;
}
```

**Validation rule**: `requestId` MUST match `/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i`
(UUIDv4). Failure → `Error(code: "INTERNAL", message: "request body failed validation")`.

---

## 2. TypeScript types — response envelope

Mirrors the Kotlin sealed `LlmGatewayResponse` from
[`specs/013-cloud-llm-routing/data-model.md`](../013-cloud-llm-routing/data-model.md) §1.2.

### 2.1 `LlmGatewayResponse` discriminated union

```typescript
type LlmGatewayResponse =
  | EmbedResponse
  | SummarizeResponse
  | ExtractActionsResponse
  | ClassifyIntentResponse
  | GenerateDayHeaderResponse
  | ScanSensitivityResponse
  | ErrorResponse;

interface EmbedResponse {
  type: "embed_response";
  requestId: string;
  vector: number[];              // length === 1536, finite numbers
  modelLabel: string;            // e.g. "openai/text-embedding-3-small"
}

interface SummarizeResponse {
  type: "summarize_response";
  requestId: string;
  summary: string;
  modelLabel: string;
}

interface ExtractActionsResponse {
  type: "extract_actions_response";
  requestId: string;
  proposals: ActionProposalJson[];
  modelLabel: string;
}

interface ClassifyIntentResponse {
  type: "classify_intent_response";
  requestId: string;
  intent: string;
  confidence: number;            // 0..1
  modelLabel: string;
}

interface GenerateDayHeaderResponse {
  type: "generate_day_header_response";
  requestId: string;
  header: string;
  modelLabel: string;
}

interface ScanSensitivityResponse {
  type: "scan_sensitivity_response";
  requestId: string;
  tags: string[];
  modelLabel: string;
}

interface ErrorResponse {
  type: "error";
  requestId: string;
  code: ErrorCode;
  message: string;               // human-readable, never contains prompt or token text
}

type ErrorCode =
  | "NETWORK_UNAVAILABLE"
  | "GATEWAY_5XX"
  | "PROVIDER_5XX"
  | "TIMEOUT"
  | "MALFORMED_RESPONSE"
  | "UNAUTHORIZED"
  | "INTERNAL";
```

### 2.2 HTTP status code mapping (single rule)

- **HTTP 401**: ONLY when the auth gate fails (FR-014-007, FR-014-008). Body is
  `ErrorResponse(code: "UNAUTHORIZED", ...)`.
- **HTTP 200**: All other cases — successes AND upstream LLM failures (FR-014-003). Body is
  the appropriate response variant.

### 2.3 Error code mapping (FR-014-004)

| Trigger | `code` value |
|---------|-------------|
| Auth header missing / malformed / verification fails / `sub` missing or non-UUID | `UNAUTHORIZED` (HTTP 401) |
| Vercel AI Gateway returns 5xx | `GATEWAY_5XX` |
| Direct OpenAI returns 5xx | `PROVIDER_5XX` |
| Anthropic / OpenAI request times out (per-call timeout: 30s default, 60s for `summarize`) | `TIMEOUT` |
| Upstream returns malformed JSON or schema-invalid response | `MALFORMED_RESPONSE` |
| Inbound request body fails Zod parse | `INTERNAL` (with generic message; never echo Zod error) |
| Any uncaught exception in function body | `INTERNAL` |
| `NETWORK_UNAVAILABLE` is reserved for the **client** (`:net` cannot reach the function); the function itself never produces this code | n/a |

---

## 3. JWT claim shape (Supabase-issued, HS256)

Verified inbound at the auth gate. Reference: spec FR-014-007, FR-014-008.

```typescript
interface SupabaseJwtClaims {
  sub: string;                   // UUID — auth.users.id; stamped as audit user_id
  exp: number;                   // unix seconds; jose's jwtVerify enforces
  iat: number;                   // unix seconds, issued-at
  iss: string;                   // matches `${SUPABASE_URL}/auth/v1`
  role: "authenticated" | "anon" | "service_role";   // "authenticated" expected for user calls
  aud: string;                   // "authenticated" by Supabase default
  email?: string;                // present for email-based auth; ignored
  app_metadata?: Record<string, unknown>;
  user_metadata?: Record<string, unknown>;
}
```

**Required validation steps** (all must pass; any failure → `UNAUTHORIZED`):
1. Header parses as `Bearer <jwt>`.
2. `jose.jwtVerify(token, hmacKey, { issuer: <expected> })` succeeds — covers signature,
   `exp`, `iss`.
3. `sub` is present, non-empty, matches UUID regex.
4. (Defensive, optional Day 2) `role === "authenticated"` — reject service-role tokens at the
   gateway since service-role should never originate user-facing calls.

The function MUST NOT log the token, any claim other than `sub`, or any `app_metadata` /
`user_metadata` content.

---

## 4. Audit row payload (`audit_log_entries.details_json`)

Reference: spec FR-014-013, [audit-row-contract.md](contracts/audit-row-contract.md).

The `audit_log_entries` table shape itself is unchanged from spec 013 (`id`, `user_id`,
`created_at`, `event_type`, `actor`, `subject_id`, `details_json`). Spec 014 only specifies
the `details_json` payload shape for `event_type = 'cloud_llm_call'` rows.

### 4.1 Success row

```typescript
interface AuditDetailsSuccess {
  requestId: string;             // UUIDv4 from request envelope
  requestType: LlmGatewayRequest["type"];   // e.g. "embed", "classify_intent"
  model: string;                 // upstream model id, e.g. "claude-haiku-4-5"
  modelLabel: string;            // wire-format label, e.g. "anthropic/claude-haiku-4-5"
  latencyMs: number;             // upstream call duration only (excludes audit insert)
  tokensIn: number;              // from upstream usage (Anthropic input_tokens; OpenAI prompt_tokens)
  tokensOut: number;             // 0 for embeddings; from upstream usage otherwise
  cacheHit: boolean;             // ALWAYS false for non-cached request types
  success: true;
}
```

### 4.2 Error row

```typescript
interface AuditDetailsError {
  requestId: string;
  requestType: LlmGatewayRequest["type"];
  model: string;                 // best-effort; the configured model for this request type
  modelLabel: string;
  latencyMs: number;             // time spent before failure
  tokensIn: 0;                   // unknown on failure; explicitly zero, never null
  tokensOut: 0;
  cacheHit: false;
  success: false;
  errorCode: ErrorCode;          // exactly one of the seven codes from §2
}
```

### 4.3 Field-by-field constraints (Day-2 fully specified, no nulls)

| Field | Type | Constraint |
|-------|------|------------|
| `requestId` | string | UUIDv4. Always present. |
| `requestType` | string | Closed enum, six values. Always present. |
| `model` | string | Always present (configured per request type). |
| `modelLabel` | string | Always present, non-empty. |
| `latencyMs` | number | Integer ≥ 0, `Math.round(performance.now() - start)`. |
| `tokensIn` | number | Integer ≥ 0. On error rows: 0 (never null). |
| `tokensOut` | number | Integer ≥ 0. Embeddings: 0. Errors: 0. |
| `cacheHit` | boolean | True only for `classify_intent` / `scan_sensitivity` cache reads. |
| `success` | boolean | Strict — `true` ⇔ upstream returned a parseable success. |
| `errorCode` | string \| undefined | Present only on error rows. One of the seven `ErrorCode` values. |

**Bounded Observation invariants** (Constitution Principle XIV, FR-014-012):
- The `details_json` MUST NOT contain prompt text, response text, embedding vectors, JWT
  contents, or any user-derived content.
- The function MUST NOT include any field beyond those listed above. New fields require a
  spec amendment.

---

## 5. `cost_per_user_daily` SQL view

Reference: spec FR-014-024.
Migration file: `supabase/migrations/00000003_cost_per_user_daily.sql`.

### 5.1 View DDL

```sql
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
```

### 5.2 RLS on the view

Views inherit RLS from their underlying tables. `audit_log_entries` already has
`SELECT USING (auth.uid() = user_id)` (spec 013 RLS contract), so a user querying
`cost_per_user_daily` sees only their own rows. No additional grant or policy is required.

**Verification**: a `psql` smoke test under the spec-014 phase verifies that User A querying
the view returns zero rows for User B's `user_id`.

### 5.3 Day-2 limitations (intentional)

- **Cache reads are billed at full input rate.** Anthropic charges ~10% of input rate for
  cache reads, but the audit row carries `cacheHit: boolean`, not
  `cache_read_input_tokens`. The Day-2 cost is therefore an over-estimate by up to ~9% on
  the classifier path. Refining this is spec 005's responsibility.
- **No per-call cost stored.** The view computes cost on read. Materialization is deferred.
- **No alerting / threshold logic.** Pure observability.

---

## 6. Zod schemas (validation surface)

The function uses one root Zod schema for the inbound request body and per-handler schemas
for upstream response validation. This section documents the shape; concrete code lives in
the implementation.

```typescript
// Root request schema (discriminated union).
const LlmGatewayRequestSchema = z.discriminatedUnion("type", [
  z.object({ type: z.literal("embed"), requestId: z.string().uuid(), payload: EmbedPayload }),
  z.object({ type: z.literal("summarize"), requestId: z.string().uuid(), payload: SummarizePayload }),
  // ...four more
]);
```

A failed parse → return `ErrorResponse(code: "INTERNAL", message: "request body failed validation")`
HTTP 200. The function MUST NOT include the Zod issue array in the response body.

---

## 7. Cross-references

| Concept | File |
|---------|------|
| Wire envelope (HTTP) | [specs/013-cloud-llm-routing/contracts/llm-gateway-envelope-contract.md](../013-cloud-llm-routing/contracts/llm-gateway-envelope-contract.md) |
| Kotlin sealed `LlmGatewayRequest` / `LlmGatewayResponse` | [specs/013-cloud-llm-routing/data-model.md](../013-cloud-llm-routing/data-model.md) §1 |
| `audit_log_entries` table schema | [supabase/migrations/00000000_initial_schema.sql](../../supabase/migrations/00000000_initial_schema.sql) §5.8 |
| RLS contract on `audit_log_entries` | [specs/013-cloud-llm-routing/contracts/supabase-rls-contract.md](../013-cloud-llm-routing/contracts/supabase-rls-contract.md) |
| Encryption contract (audit log NOT uploaded from device) | [specs/contracts/envelope-content-encryption-contract.md](../contracts/envelope-content-encryption-contract.md) |
| Spec-014 contracts | [contracts/](contracts/) |
