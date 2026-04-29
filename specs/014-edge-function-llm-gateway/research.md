# Phase 0 Research — Vercel AI Gateway Edge Function (Day 2)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-04-29

This document records the validated facts and decisions made before Phase 1 design. Every
hardcoded technical choice in the spec was already locked by the user (`anthropic/claude-sonnet-4-6`,
`anthropic/claude-haiku-4-5`, `openai/text-embedding-3-small`, Vercel Edge Functions
TypeScript, Supabase JWT). The research below validates those choices against current vendor
documentation and resolves the remaining ambiguities (`ExtractActions` validation library,
JWT verification approach, batching policy).

All NEEDS CLARIFICATION items from the spec brief are resolved here or explicitly deferred to
the open-questions list at the bottom.

---

## 1. Vercel AI Gateway — model strings and Anthropic SDK shape

**Decision**: Call Anthropic via the official `@anthropic-ai/sdk` (TypeScript) configured with
`baseURL` pointing at the Vercel AI Gateway and `apiKey` set to the Vercel AI Gateway key.
Model strings are passed exactly as `anthropic/claude-sonnet-4-6` and
`anthropic/claude-haiku-4-5` — the AI Gateway prefix-matches the provider/model and forwards
the call to Anthropic with prompt-caching headers preserved.

**Rationale**:
- The AI Gateway is a transparent reverse proxy; the SDK does not need a Vercel-specific
  client. Setting `baseURL` to the gateway URL and stamping the gateway key as `Authorization`
  is sufficient.
- Using the Anthropic SDK (rather than raw `fetch`) gives us typed `Messages.create` calls,
  built-in usage extraction (`usage.cache_read_input_tokens`, `usage.cache_creation_input_tokens`),
  and beta-header plumbing for `prompt-caching-2024-07-31`.
- Model strings are pinned to ADR-003 (`claude-sonnet-4-6` for Sonnet path,
  `claude-haiku-4-5` for Haiku path). Validated against the Anthropic public model list as of
  2026-04-29.

**Alternatives considered**:
- Vercel AI SDK (`ai` package + `@ai-sdk/anthropic` provider) — adds another abstraction layer
  on top of the Anthropic SDK without giving us anything we need (we don't stream, we don't
  use `generateObject`, and the cache-read token surface is more directly accessible from the
  Anthropic SDK). Rejected for additional dependency surface.
- Raw `fetch` to `https://gateway.ai.vercel.app/v1/anthropic/messages` — works, but loses the
  typed surface and forces us to maintain our own usage-extraction code. Rejected.

**Open variability**: If Anthropic deprecates `claude-haiku-4-5` mid-life, model swap is a
spec amendment, not a code refactor — the model string lives in one place per request type.

---

## 2. Anthropic prompt caching — mechanics, hit rate, audit surface

**Decision**: For `ClassifyIntent` and `ScanSensitivity` request types only, pass the
`prompt-caching-2024-07-31` beta header on the Anthropic API call and attach
`cache_control: { type: "ephemeral" }` to the **system-message content block** carrying the
stable classifier prefix. The variable user content is appended as a separate user-message
turn and is intentionally NOT in the cached block.

**Cache hit detection**: Anthropic's response carries `usage.cache_read_input_tokens` (number
of tokens served from cache) and `usage.cache_creation_input_tokens` (tokens written to cache
on a miss + write). The function maps:

```
cacheHit = (response.usage.cache_read_input_tokens ?? 0) > 0
```

This boolean is mirrored into the audit row's `details_json.cacheHit` field per FR-014-011.
For request types that do not opt into caching (`Embed`, `Summarize`, `ExtractActions`,
`GenerateDayHeader`), `cacheHit` is hardcoded to `false`.

**Hit rate target**: SC-014-002 requires ≥ 80% over 100 sequential identical-system-prompt
calls. Anthropic's documented cache TTL is 5 minutes (rolling on read). 100 sequential calls
within ~5 minutes from the same client comfortably exceed this; the only realistic miss is
the first 1–2 calls while the cache warms.

**Rationale**:
- The system prompt is the only stable prefix across calls for these two capabilities. The
  user content varies every call. Putting `cache_control` on the system block is the correct
  Anthropic pattern.
- Mirroring `cacheHit` into the audit row gives spec 005 (BYOK + quota) a direct cost-model
  input without the Edge Function having to compute aggregate cost.

**Alternatives considered**:
- Full prompt caching (system + few-shot examples in same cached block) — supported, but
  Day-2 prefixes are short enough that single-block caching captures the win. If we later
  add longer few-shot prefixes, we extend the same block.
- 1-hour cache TTL (Anthropic supports `cache_control: { type: "ephemeral", ttl: "1h" }` on
  newer beta) — rejected for Day 2; 5-min default is fine for sequential classifier loads
  and reduces blast radius if a prompt change is needed.

---

## 3. OpenAI embeddings — direct call, no AI Gateway

**Decision**: Call OpenAI directly via the official `openai` SDK (TypeScript) with
`apiKey: process.env.OPENAI_API_KEY`. Model: `text-embedding-3-small` with explicit
`dimensions: 1536`. **No batching in Day 2** — every `Embed` request is a single
`embeddings.create` call with one input string.

**Rationale**:
- The spec's hardcoded decision is "OpenAI SDK direct" (not via Vercel AI Gateway). This is a
  cost decision: the AI Gateway adds a small per-call markup, and embedding latency is the
  dominant cost driver on the embedding path — direct keeps the round-trip tighter.
- Day-1 `LlmProvider.embed(text: String): FloatArray?` is single-input; the Edge Function
  preserves that contract. Batching multiple texts into one OpenAI call would require an API
  shape change on Android (`embedBatch(texts: List<String>)`) that is out of scope for Day 2.
- `text-embedding-3-small` defaults to 1536 dimensions; passing `dimensions: 1536` explicitly
  is defensive (in case the default changes in a future SDK version).

**Alternatives considered**:
- Routing OpenAI embeddings through Vercel AI Gateway — possible (gateway supports OpenAI
  passthrough), but adds latency and cost without adding reliability for embeddings (which
  are stateless and cheap to retry).
- Batching N requests into one OpenAI call inside the Edge Function — would require holding
  inbound requests open while batching, defeating the per-request latency budget. Rejected.

**Future**: If embedding cost ever becomes a meaningful line item, the right move is to add
`embedBatch` on the Android side (spec amendment) and fan out batch calls to OpenAI. Day 2
keeps the 1:1 mapping.

---

## 4. JWT verification — Supabase HS256 with project JWT secret

**Decision**: Verify the inbound `Authorization: Bearer <jwt>` header using the `jose` library
(TypeScript, MIT-licensed, edge-runtime compatible) with `jwtVerify` and the project's HS256
JWT secret read from `process.env.SUPABASE_JWT_SECRET`. **Do not** use `@supabase/supabase-js`
for verification — that SDK calls back to the Auth REST endpoint, which adds a network hop on
every gateway call.

**Algorithm**: Supabase issues HS256-signed JWTs by default. The project's JWT secret is a
shared symmetric key; verification is a constant-time HMAC-SHA256 check against the secret.

**Required claim checks**:
- `exp` — must be in the future (jose's `jwtVerify` does this automatically).
- `iss` — must match `${SUPABASE_URL}/auth/v1` (jose's `jwtVerify` accepts an `issuer` option).
- `sub` — must be present, non-empty, UUID-shaped. Custom check after `jwtVerify` returns.

Any failure (signature mismatch, expired, malformed, missing/non-UUID `sub`) collapses to a
single `UNAUTHORIZED` response shape per FR-014-008. The function MUST NOT leak which check
failed in the response body.

**Rationale**:
- `jose` is the standard JOSE implementation for the Web Crypto API edge runtimes
  (Vercel Edge, Cloudflare Workers, Deno). `@supabase/supabase-js` server client adds a REST
  hop per request, which violates the latency budget and adds an unnecessary failure mode.
- HS256 + project JWT secret is the default Supabase auth shape (validated against current
  Supabase docs as of 2026-04-29). If the project later flips to RS256 + JWKS, the migration
  is `jwtVerify(token, jose.createRemoteJWKSet(jwksUri))` — a one-line change. Spec assumes
  HS256 per the current project state.

**Alternatives considered**:
- `jsonwebtoken` (Node.js) — not edge-runtime compatible (uses Node crypto). Rejected.
- `@supabase/supabase-js` server client + `auth.getUser()` — adds one network hop per call.
  Rejected.
- RS256 + JWKS (`createRemoteJWKSet`) — premature; requires flipping Supabase auth settings.
  Defer to a future spec amendment if ever needed.

---

## 5. Audit insert — service-role key, explicit user_id stamp

**Decision**: Use `@supabase/supabase-js` (server client, edge-runtime compatible) initialized
with the service-role key for the audit insert only. The function MUST explicitly stamp
`user_id` from the verified JWT's `sub` claim — never rely on `auth.uid()` because the
service-role client bypasses RLS.

Insert shape (per spec FR-014-013):

```typescript
await supabase.from("audit_log_entries").insert({
  user_id: subClaim,           // UUID from JWT sub
  event_type: "cloud_llm_call",
  actor: "edge_function",
  subject_id: null,            // not used in Day 2
  details_json: {
    requestId, requestType, model, modelLabel,
    latencyMs, tokensIn, tokensOut, cacheHit, success,
    // on failure: errorCode (string)
  },
});
```

**Rationale**:
- Day-1 RLS contract requires `WITH CHECK (auth.uid() = user_id)` for INSERT on every
  user-owned table. `audit_log_entries` per [supabase-rls-contract.md] follows the same
  shape. Service-role bypasses RLS, so the function explicitly stamps `user_id` to remain
  semantically consistent with `WITH CHECK` (SC-014-007).
- The service-role client is initialized once per cold-start (module-level singleton) and
  reused across requests — no per-request initialization cost.
- `insert` is fire-and-forget for client UX (FR-014-014: audit failure does NOT degrade the
  user-facing response).

**Failure handling** (FR-014-014): wrap the insert in try/catch; on failure log
`audit_insert_failed=true` with `requestId` only (no prompt content) and continue returning
the upstream LLM response. The function MUST NOT swallow audit failures silently — the log
line is required so operators can detect Postgres-side issues.

**Alternatives considered**:
- Postgres direct connection (`postgres-js` or similar) — doesn't work in Vercel Edge
  Runtime (no TCP). `@supabase/supabase-js` uses HTTP/PostgREST, which is edge-compatible.
- Async write (returning to client first, audit insert in `waitUntil`) — Vercel Edge does
  support `ctx.waitUntil`, but for Day 2 we want the audit insert in the request lifecycle so
  failures are observable in the per-request log line. Defer optimization.

---

## 6. ExtractActions payload validation — Zod

**Decision**: Use `zod` (TypeScript schema validation library) to parse the inbound request
body. Define one root discriminated-union schema mirroring the Kotlin sealed `LlmGatewayRequest`,
with per-`type` payload schemas. Failed parse → return `LlmGatewayResponse.Error(code:
"MALFORMED_RESPONSE", message: ...)` with HTTP 200.

Wait — `MALFORMED_RESPONSE` is the wrong code for an inbound malformed request. Re-read
FR-014-004: `MALFORMED_RESPONSE` covers upstream returning malformed JSON. For inbound
request body that fails Zod parse, the correct code is `INTERNAL` (the function caught an
invariant violation it cannot recover from). The function returns HTTP 200 with
`Error(code: "INTERNAL", message: "request body failed validation")` — never echoing the
specific Zod error to avoid leaking schema internals.

**Rationale**:
- Zod gives us runtime type validation that mirrors the Kotlin sealed class shape. Without
  it, the function would either trust the body blindly (security hole) or hand-roll
  validators per request type (drift risk).
- `zod` is edge-runtime compatible (zero deps, pure TypeScript).
- Parsing errors stay inside the function — the client gets a generic error code so we do
  not leak schema details to a potentially-untrusted caller.

**Alternatives considered**:
- `JSON.parse` + manual `if (typeof x !== "string") throw` — bug-prone, no exhaustive
  discriminated-union check.
- `valibot` — smaller bundle than Zod but smaller community; Zod is the safer Day-2 default.
- `io-ts` — heavier, fp-style API, overkill for our shape.

---

## 7. Cold-start mitigation — accept the tax

**Decision**: Vercel Edge Functions cold-start in ~50–200ms (validated against Vercel's
public benchmarks). First-token latency from Anthropic dominates total request time
(typically 300–1500ms for short Haiku prompts, 800–3000ms for Sonnet). The cold-start tax is
included in the SC-014-003 budget (p50 ≤ 800ms, p95 ≤ 2500ms for embed) and does not need
its own mitigation.

**Steady-state assumption**: Once captures begin flowing in production, request frequency
(~10/min/user under normal use) easily exceeds Vercel's idle-shutdown threshold; the warm
path is the common case. Worst-case cold start happens after ≥ 15 minutes of zero traffic,
which is rare in practice.

**Rationale**:
- Provisioned execution / always-warm is a paid Vercel feature; not needed for Day 2.
- The function body has minimal startup cost: import `jose`, `@anthropic-ai/sdk`, `openai`,
  `@supabase/supabase-js`, `zod`. All are edge-compatible and tree-shaken.
- No global state to warm beyond the SDK clients (which are lazily initialized on first call
  anyway).

**Alternatives considered**:
- Always-warm via cron-pinging — adds operational complexity without measurable user-facing
  benefit at Day-2 scale (single-digit users).
- Vercel Pro provisioned execution — paid feature, deferred until production traffic
  justifies it.

---

## 8. Secret management on Vercel

**Decision**: All five secrets — `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`,
`SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_URL`, `SUPABASE_JWT_SECRET` — are configured **only**
via Vercel project environment variables (Production, Preview, Development environments
separately). They MUST NOT appear in this repository, `local.properties`, commit history, or
CI logs. The deploy runbook (FR-014-020) verifies all five are set via `vercel env ls` before
production deploy.

**Local development**: Secrets are sourced from a gitignored `.env.local` at the function
root (`supabase/functions/llm_gateway/.env.local`). A `.env.local.example` MAY be committed
with empty values as a template (FR-014-022).

**Rotation**: The function reads env vars at module init. Rotation requires either a new
deploy or Vercel's hot env-var update (which triggers a new deploy automatically).

**Rationale**:
- Vercel env vars are encrypted at rest, per-environment, and never appear in build artifacts
  or logs.
- Sixth env var note: the **Vercel AI Gateway key** (separate from Anthropic API key) is also
  required if we route Anthropic via the gateway. Locked in as a sixth env var:
  `VERCEL_AI_GATEWAY_KEY` (or whatever the Vercel-side name is — confirmed at task time when
  the project is provisioned).

**Alternatives considered**:
- HashiCorp Vault / AWS Secrets Manager — over-engineered for Day 2 scale; Vercel's built-in
  env vars are the appropriate primitive.
- `.env` checked into the repo (encrypted with `dotenv-vault` or similar) — adds tooling
  surface; rejected in favor of platform-native env vars.

---

## 9. Tooling deviation — branch name, audit-row co-location

**Decision**: Continue working on the `cloud-pivot` branch (no new feature branch). This
mirrors the spec-013 deviation; speckit tooling is invoked with
`SPECIFY_FEATURE=014-edge-function-llm-gateway` to align paths with the spec directory.

**Decision**: Edge Function source lives at `supabase/functions/llm_gateway/index.ts` despite
deploying to Vercel. This co-locates the function with the Day-1 migrations and keeps the
contract surface one PR away from Android. Migration to a separate `vercel/` directory is
explicitly deferred (FR-014-019).

---

## 10. cost_per_user_daily SQL view — design

**Decision**: A SQL view defined in a new migration
`supabase/migrations/00000003_cost_per_user_daily.sql` aggregates `audit_log_entries` rows
where `event_type = 'cloud_llm_call'` and joins against a static rate table to produce
per-user-per-day cost. **No enforcement** — observability only (FR-014-023, FR-014-024).

Rate table approach: a small SQL `VALUES`-backed CTE inside the view definition, keyed on
`details_json->>'modelLabel'`. Day-2 rates (per 1M input/output tokens, USD):

| Model label | Input $/M | Output $/M |
|-------------|-----------|------------|
| `anthropic/claude-sonnet-4-6` | 3.00 | 15.00 |
| `anthropic/claude-haiku-4-5` | 0.25 | 1.25 |
| `openai/text-embedding-3-small` | 0.02 | 0.00 (embeddings have no output tokens) |

Rates are static at view-creation time; updating them is a follow-up migration. The view
returns `(user_id, date_local, cost_usd_estimate, request_count)`.

**Rationale**:
- Hardcoding rates inside the view (rather than a `model_rates` table) keeps Day 2 simple and
  avoids an admin surface for rate management. Rate changes are infrequent and a migration
  is the appropriate change vehicle.
- `details_json.cacheHit` is **NOT** factored into the cost computation in Day 2 — Anthropic
  cache reads are billed at ~10% of normal input, but the audit row carries cacheHit, not
  cache_read_input_tokens directly. This is an intentional Day-2 simplification; refining
  the cost model is a spec 005 concern.

**Alternatives considered**:
- Materialized view refreshed nightly — premature optimization at Day-2 scale.
- Postgres `RAISE NOTICE` to surface daily cost per user — wrong layer; cost surfacing is a
  spec 005 UI concern.

---

## 11. Validated facts (vendor doc snapshots, 2026-04-29)

- **Anthropic models**: `claude-sonnet-4-6` and `claude-haiku-4-5` are GA per Anthropic's
  public model list. `prompt-caching-2024-07-31` beta is GA on both.
- **OpenAI**: `text-embedding-3-small` defaults to 1536 dimensions; configurable down to 256
  via the `dimensions` parameter.
- **Vercel Edge Runtime**: Web standard `Request`/`Response` API; no Node.js APIs except
  those polyfilled (`crypto.subtle` for JOSE works). Module imports limited to ESM.
  `@anthropic-ai/sdk`, `openai`, `@supabase/supabase-js`, `jose`, `zod` all confirmed
  edge-compatible per their published docs.
- **Vercel AI Gateway**: model strings `anthropic/<model>`, `openai/<model>` route to the
  named provider. Authentication via the gateway key in `Authorization`. The gateway is a
  drop-in `baseURL` replacement for the Anthropic SDK.
- **Supabase JWT**: HS256 by default; secret available as the project's "JWT Secret" in the
  Supabase dashboard. JWTs carry `sub`, `exp`, `iss`, `role`, `aud` claims.
- **`audit_log_entries` schema**: validated against `supabase/migrations/00000000_initial_schema.sql`
  — columns `id`, `user_id`, `created_at`, `event_type`, `actor`, `subject_id`, `details_json`.
  RLS append-only (SELECT/INSERT only).

---

## 12. Open questions (carry into plan.md)

- **Q1 — Vercel project provisioning timing.** Is the Vercel project created NOW (before
  `/speckit.tasks`) so the deploy URL is known, or is provisioning a task itself?
  *Default assumption*: provisioning is a Phase A task (Vercel project + env vars + first
  empty deploy land in one task before code lands). The `cloud.gateway.url` placeholder in
  `local.properties` is updated at the end of Phase A.
- **Q2 — Anthropic API key procurement.** Does the user already hold an Anthropic API key
  for personal use, or do we provision a new dev key? *Default assumption*: user supplies
  the key at task-execution time; spec 014 does not block on it.
- **Q3 — Vercel AI Gateway key vs direct Anthropic API key.** Spec hardcodes "Anthropic SDK
  via Vercel AI Gateway", which means the SDK is configured with the **gateway** key, not a
  raw Anthropic key. Confirm at task time which key the user intends to use; the function
  shape is identical either way (it's a `baseURL` swap).
- **Q4 — Edge Function deploy script vs runbook README.** FR-014-020 allows either a
  `deploy.sh` script or a runbook README. *Decision*: ship both — a one-liner `deploy.sh`
  that delegates to `vercel deploy --prod` and a README documenting env-var prerequisites
  and smoke-test steps.

These four are tracked in [plan.md](plan.md) §Open Clarifications and surface to the user at
the end of `/speckit.plan` so they can be resolved before `/speckit.tasks`.
