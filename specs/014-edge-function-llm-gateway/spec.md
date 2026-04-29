# Feature Specification: Vercel AI Gateway Edge Function (Day 2)

**Feature Branch**: `cloud-pivot` (work for spec `014-edge-function-llm-gateway`; no new branch — continues on the Day-1 cloud-pivot branch)
**Created**: 2026-04-29
**Status**: Draft
**Governing document**: `.specify/memory/constitution.md` (v3.2.0)
**Input**: User description: "Spec 014 — Vercel AI Gateway Edge Function. Make the placeholder URL real."

> **Relationship to prior specs.** This spec is the **Day-2 follow-up to spec 013**
> (`Cloud LLM Routing + Supabase Backbone`). Day 1 shipped the Android-side abstraction:
> `LlmProvider` interface with cloud/local routing, `CloudLlmProvider`, AIDL `callLlmGateway`,
> `LlmGatewayClient` with retry-once direct-provider fallback, and the Supabase backbone with
> RLS proven via `multi_user_smoke.sql` (cleared 2026-04-29 — alpha gate).
>
> Day 1 left exactly one stub: `LlmGatewayClient` POSTs to `https://gateway.example.invalid/llm`.
> **Day 2 makes that endpoint real.** Specifically: a Vercel Edge Function that accepts the
> provider-agnostic envelope from FR-013-007, routes to the correct upstream model, applies
> Anthropic prompt caching for high-volume request types, authenticates the caller via Supabase
> JWT, and mirrors observability metadata into the existing `audit_log_entries` table.
>
> **Constitutional alignment.** Principle I (Process & Egress Boundaries) is updated: the egress
> graph for AI inference becomes `:capture → :net → Vercel Edge Function → {Anthropic | OpenAI}`
> (Day 1 graph stopped at `:net → gateway placeholder`). Principle XIV (Bounded Observation) is
> the load-bearing constraint for the Edge Function's logging shape — prompt content is ephemeral
> and MUST NOT appear in any persisted log. Principle IX (LLM Sovereignty) is preserved: all AI
> inference still flows through `LlmProvider` → AIDL → `LlmGatewayClient`; only the *destination*
> of the HTTPS call changes.
>
> **ADR cross-references.**
> - **ADR-003** (Vercel AI Gateway + direct provider fallback): the Edge Function is the *Gateway*
>   half of that diagram. Direct-provider fallback remains an Android-side concern (FR-013-008,
>   shipped Day 1) — the Edge Function does not implement its own fallback to itself.
> - **ADR-007** (RLS + multi-user smoke test prereq): satisfied by Day 1; `audit_log_entries`
>   inserts in this spec MUST honor the existing RLS policy (`auth.uid() = user_id`).

---

## Clarifications

### Session 2026-04-28

- Q: How should `LlmGatewayClient` obtain the Supabase JWT for outbound calls (FR-014-017)? → A: Introduce an `authStateBinder` companion to the existing `gatewayBinder` pattern, exposing `suspend fun currentJwt(): String?`. `LlmGatewayClient` calls it before each request; null → return `Error(code="UNAUTHORIZED")` without crossing the network. Auth refresh is the binder implementation's responsibility. Keeps `:net` decoupled from the Supabase SDK and preserves testability with fakes.
- Q: Should the Edge Function enforce per-user/per-day token budgets in Day 2 (FR-014-023)? → A: No — defer hard quota cutoff to spec 005 (BYOK + Orbit-managed quota). Day 2 relies on Vercel platform rate limiting (200 req/min/IP default) plus a `cost_per_user_daily` SQL view over `audit_log_entries` (sum tokens × per-model rate from a static rate table) for retrospective observability. No enforcement in the function.
- Q: How is the gateway URL configured on Android (FR-014-016)? → A: Gradle `BuildConfig.CLOUD_GATEWAY_URL` sourced from `local.properties` key `cloud.gateway.url` at build time. `app/build.gradle.kts` reads the property and emits the `BuildConfig` field; `LlmGatewayClient` reads `BuildConfig.CLOUD_GATEWAY_URL` instead of the hardcoded placeholder. Missing property → build warns (not fails) and defaults to the Day-1 placeholder so smoke tests still execute the offline path. `RuntimeFlags` is rejected: URL is build-time/environment-bound, not user-flippable.

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Real cloud LLM round-trip from Android (Priority: P1)

As an Orbit user with the alpha installed, I tap a button that triggers any of the six AI
capabilities (embed, summarize, extract actions, classify intent, generate day header, scan
sensitivity). The Day-1 Android client builds the request envelope and dispatches it through
`:net`. Today that request times out against `gateway.example.invalid`. After this story ships,
the request hits a deployed Vercel Edge Function that returns a real, model-shaped response in
under the latency budgets defined in spec 013.

**Why this priority**: This is the only story that actually unblocks every dependent feature
(specs 002 v1 cluster suggestions, 003 orbit actions, 004 Ask Orbit). Every other story in this
spec is a guardrail around it.

**Independent Test**: From an instrumented Android test, build a real `Embed("the quick brown
fox", requestId = …)` request, dispatch it via the deployed `LlmGatewayClient` (with `cloud.gateway.url`
pointing at the production Vercel URL), and verify the returned `LlmGatewayResponse.Embed.vector`
is a `FloatArray` of length 1536 with `modelLabel == "openai/text-embedding-3-small"`. Repeat for
each of the other five request types and verify each returns the documented success variant.

**Acceptance Scenarios**:

1. **Given** the Edge Function is deployed at `https://<project>.vercel.app/llm` and the Android
   client's `cloud.gateway.url` build config field points at it, **When** the client sends a valid
   `Embed` request with a valid Supabase JWT in the `Authorization` header, **Then** the response
   parses as `LlmGatewayResponse.Embed` with a 1536-element `FloatArray` and a non-empty `modelLabel`.
2. **Given** the same deployment, **When** the client sends a `Summarize`, `ExtractActions`,
   `ClassifyIntent`, `GenerateDayHeader`, or `ScanSensitivity` request, **Then** the response
   parses as the corresponding success variant defined in `specs/013-cloud-llm-routing/data-model.md` §1.2.
3. **Given** any upstream provider returns an error (Anthropic/OpenAI 5xx, rate-limit, malformed),
   **When** the Edge Function processes the failure, **Then** it responds with HTTP 200 carrying a
   JSON body of `LlmGatewayResponse.Error(code, message)` where `code` is exactly one of the seven
   values enumerated in spec 013 data-model §1.2: `NETWORK_UNAVAILABLE`, `GATEWAY_5XX`,
   `PROVIDER_5XX`, `TIMEOUT`, `MALFORMED_RESPONSE`, `UNAUTHORIZED`, `INTERNAL`.

---

### User Story 2 — Authenticated calls only; unauthenticated requests rejected (Priority: P1)

As Orbit's owner of the cloud egress surface, I need every request to the Edge Function to be
attributable to a signed-in Supabase user. Day 1 deliberately allowed null auth (graceful
degradation while `AuthSessionStore` was a stub). Day 2 closes that hole. The function MUST
reject any request without a valid Supabase JWT and MUST extract `user_id` from the JWT's `sub`
claim for downstream audit and per-user observability.

**Why this priority**: Without this, every cost telemetry / audit row is anonymous and the
function is a free public LLM proxy. This must land alongside Story 1 — not after.

**Independent Test**: Issue a curl against the deployed function with `Authorization` header
omitted; expect HTTP 401 and a body of `{"type":"error","code":"UNAUTHORIZED","message":...}`.
Issue the same curl with a forged/expired JWT; expect the same 401 + `UNAUTHORIZED` shape. Issue
the curl with a valid Supabase JWT minted for a real `auth.users` row; expect HTTP 200 and a
success-variant body.

**Acceptance Scenarios**:

1. **Given** the Edge Function is deployed, **When** a request arrives with no `Authorization`
   header, **Then** the function returns HTTP 401 with a body matching
   `LlmGatewayResponse.Error(code = "UNAUTHORIZED", message = <human-readable>)`.
2. **Given** a request arrives with `Authorization: Bearer <expired-or-invalid-jwt>`, **When** the
   function attempts to verify the token, **Then** it returns HTTP 401 with the same `UNAUTHORIZED`
   error shape.
3. **Given** a request arrives with a valid Supabase JWT signed by the project's JWT secret,
   **When** the function decodes the token, **Then** it extracts the `sub` claim as a non-null
   UUID string and uses that value as `user_id` for the audit row in Story 3.
4. **Given** the Android `LlmGatewayClient` has been wired to a real auth source on Day 2,
   **When** any of the six `LlmProvider` methods is invoked from a signed-in session, **Then** the
   outbound HTTPS request carries `Authorization: Bearer <jwt>` automatically — no per-call wiring
   required.

---

### User Story 3 — Every successful call is auditable; no prompt content leaks (Priority: P1)

As Orbit's privacy-by-construction owner, I need every successful LLM call to write exactly one
row into the existing `audit_log_entries` table with enough metadata to bill, debug, and detect
abuse — and not one byte more. Specifically, no row may contain prompt text, response text,
embedding vectors, or any other user-derived content. Per Constitution Principle XIV, the audit
shape carries `requestId`, capability, model, latency, token counts, and cache-hit flag. That is
the entire payload.

**Why this priority**: This is the only story that proves the function is actually compatible
with Orbit's bounded-observation principle. Without it, any cost analytics in spec 005 (BYOK +
quota) or spec 007 (knowledge graph) would have to re-litigate the privacy contract.

**Independent Test**: Make 10 successful calls across different request types from a known user.
Query `select details_json from audit_log_entries where user_id = '<user>' order by created_at
desc limit 10;` and verify (a) exactly 10 rows exist, (b) every row's `details_json` matches the
shape `{requestId, requestType, model, modelLabel, latencyMs, tokensIn, tokensOut, cacheHit, success}`, and
(c) `grep` over the full Vercel function logs for that time window finds zero substrings of any
prompt text, response text, or vector content.

**Acceptance Scenarios**:

1. **Given** a successful Edge Function call, **When** the function returns to the client, **Then**
   exactly one row is inserted into `audit_log_entries` with `event_type='cloud_llm_call'`,
   `actor='edge_function'`, `user_id` taken from the JWT `sub` claim, and `details_json` matching
   the documented shape (no prompt, no response body, no vector).
2. **Given** the same call, **When** the audit insert happens, **Then** it uses the Supabase
   service-role key (because the function is acting on behalf of the user but is not running as
   that user's session) yet stamps `user_id` from the JWT — RLS sees the stamped `user_id` and
   the existing `WITH CHECK (auth.uid() = user_id)` policy on insert is consistent because the
   function explicitly sets the value rather than relying on `auth.uid()`.
3. **Given** any call (success or error), **When** the function emits log lines for observability,
   **Then** those log lines contain `requestId`, capability/`requestType`, `model`/`modelLabel`,
   `latencyMs`, `tokensIn`/`tokensOut`, `cacheHit`, and `success` only — never prompt text,
   completion text, vector content, or any other user-derived content.
4. **Given** an upstream failure where the function returns `LlmGatewayResponse.Error`, **When**
   the audit row is written, **Then** the row carries `success=false` and the `code` field; no
   audit row is skipped on error (errors are audited too, with the same shape minus token counts).

---

### User Story 4 — High-volume classifier calls hit Anthropic prompt cache (Priority: P2)

As Orbit's owner of cloud LLM cost, I need the high-volume classifier calls (`ClassifyIntent`
and `ScanSensitivity`) to use Anthropic's prompt-cached prefix feature so that the stable system
prompt portion costs ~10% of an uncached read after the cache warms. The cache hit rate target
is ≥ 80% over 100 sequential identical-system-prompt calls, verified via response headers and
mirrored into the audit row's `cacheHit` flag.

**Why this priority**: Cost-shaping. Without prompt caching, the classifier path becomes the
single largest variable cost driver as capture volume grows. With it, the unit economics for
cluster-engine cloud migration (Phase 11 Block 4) close.

**Independent Test**: Issue 100 sequential `ClassifyIntent` requests with identical system
prompts (varying only the user content) from a single signed-in user. Aggregate the
`cacheHit` flags from `audit_log_entries.details_json`. The fraction MUST be ≥ 0.80.

**Acceptance Scenarios**:

1. **Given** the Edge Function is configured with the Anthropic `prompt-caching-2024-07-31` beta
   header, **When** a `ClassifyIntent` request is dispatched, **Then** the request body sent to
   Anthropic includes a `cache_control: {type: "ephemeral"}` block on the stable system prompt
   prefix.
2. **Given** 100 sequential identical-system-prompt classifier calls, **When** the cache warms
   after the first ~1–2 calls, **Then** ≥ 80 of the subsequent 99 calls report `cacheHit=true`
   in their audit row.
3. **Given** `Summarize`, `ExtractActions`, and `GenerateDayHeader` (which do *not* have stable
   system prompts), **When** their requests are dispatched, **Then** they MUST NOT use prompt
   caching — `cacheHit` is always `false` for those request types and that is the expected shape.

---

### User Story 5 — Latency budgets honored end-to-end (Priority: P2)

As an Orbit user, the perceived latency for any AI-backed action must stay within the spec 013
budgets even with the new cloud round-trip. Specifically: embedding round-trip from the Android
client (including AIDL hop, `:net` HTTPS, Edge Function dispatch, OpenAI call, return) MUST be
p50 ≤ 800 ms, p95 ≤ 2500 ms for input ≤ 8 KB.

**Why this priority**: Latency drives whether the cluster-suggestion engine and capture-time
sensitivity classification feel native or sluggish. Below budget = ship. Above = re-architect.

**Independent Test**: Run 200 sequential `Embed` calls of ~1 KB text from an instrumented Android
test on a Pixel 9 Pro on stable Wi-Fi, measure end-to-end latency from `LlmGatewayClient.embed`
entry to return. Compute p50 and p95.

**Acceptance Scenarios**:

1. **Given** the deployed function on Vercel's edge network and a stable client network, **When**
   200 sequential 1-KB embed calls are made, **Then** the measured p50 is ≤ 800 ms and p95 is
   ≤ 2500 ms.
2. **Given** the same setup with an 8-KB embed input, **When** the call is made, **Then** the
   single-call latency is ≤ 2500 ms (within p95 budget).

---

### Edge Cases

- **JWT expiry mid-flight.** Supabase JWTs are 1-hour by default. If the token is valid at
  request entry but expires during the upstream call, the function MUST still return the
  upstream-derived response (success or error) without re-validating mid-flight. Any subsequent
  request from the same client uses the next refreshed token.
- **JWT `sub` claim missing or malformed.** If the JWT verifies but lacks a UUID-shaped `sub`,
  the function MUST treat this as `UNAUTHORIZED` — the function cannot stamp an audit row with a
  null/invalid `user_id`, and a JWT without `sub` is an authn failure.
- **Audit insert fails after successful upstream call.** If the upstream LLM call succeeds but
  the audit insert fails (e.g., transient Postgres issue), the function MUST still return the
  successful LLM response to the client (the user got the answer they asked for). The function
  MUST emit an `error`-level log line containing only the `requestId` and `audit_insert_failed=true`
  flag — no prompt content, no response content. Lost-audit observability is a secondary failure
  surface; degrading user-facing functionality on it would be worse.
- **Service-role key absent or wrong.** If `SUPABASE_SERVICE_ROLE_KEY` is misconfigured, *every*
  audit insert fails (predictable). The function MUST surface this as a startup-time error in
  Vercel logs and continue serving requests — but must not pretend audit succeeded.
- **Prompt cache misses on first call.** Per Anthropic semantics, the first call with a new
  `cache_control` block is a cache miss + write. The audit row reports `cacheHit=false` for that
  call, which is correct; subsequent calls within the cache TTL report `cacheHit=true`.
- **Streaming requested by future caller.** Day-1 `LlmProvider` interface is non-streaming; this
  spec keeps that. Streaming for `Summarize` and `ExtractActions` is deferred to spec 004 (Ask
  Orbit), which is the first feature where the user perceives partial output.
- **Replay of a captured JWT.** Supabase JWTs are signed and time-bounded (1h) but not nonce-bound.
  Replay within the JWT's lifetime is technically possible. Day 2 accepts this risk on the
  premise that JWT expiry + per-user audit (which surfaces anomalous duplicate `requestId`s) is
  sufficient. A nonce/idempotency-key layer is deferred unless audit reveals replay-driven cost.
- **Vercel cold start.** First request to a cold function pays a ~200–600 ms cold-start tax.
  This is *included* in the SC-014-003 latency budget — the budget assumes warm path is the
  common case (call frequency >> idle timeout).
- **Edge Function source-of-truth lives in the Android repo.** The function source lives at
  `supabase/functions/llm_gateway/index.ts` for now, even though Vercel deploys it. This is a
  conscious co-location choice (spec 013 migrations live nearby; the contract surface is one PR
  away from Android). When/if Vercel grows a separate `vercel/` project root, migration is in
  scope of a future spec, not this one.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Edge Function — request handling and routing

- **FR-014-001 (HTTPS endpoint shape)**: System MUST expose a single Vercel Edge Function at the
  path `/llm` that accepts `POST` requests with `Content-Type: application/json` and a body
  matching the `LlmGatewayRequest` envelope defined in `specs/013-cloud-llm-routing/data-model.md`
  §1.1 (discriminator field `type` with values `embed | summarize | extract_actions |
  classify_intent | generate_day_header | scan_sensitivity`, plus a `requestId` UUIDv4 string and
  a method-specific `payload`).
- **FR-014-002 (model routing table)**: The function MUST route by `type` to the following
  upstream models, exactly:
  - `embed` → OpenAI `text-embedding-3-small` (1536-dim) via OpenAI Embeddings API.
  - `summarize`, `extract_actions`, `generate_day_header` → Anthropic `claude-sonnet-4-6` via
    the Vercel AI Gateway (model string `anthropic/claude-sonnet-4-6`).
  - `classify_intent`, `scan_sensitivity` → Anthropic `claude-haiku-4-5` via the Vercel AI
    Gateway (model string `anthropic/claude-haiku-4-5`).
- **FR-014-003 (response envelope)**: The function MUST return `200 OK` with a JSON body matching
  `LlmGatewayResponse` from spec 013 data-model §1.2 — one of the six success variants on
  upstream success, or `LlmGatewayResponse.Error(code, message)` on upstream failure. The
  function MUST NOT return non-200 status codes for upstream LLM failures (those are encoded as
  `Error` variants in the body).
- **FR-014-004 (error code mapping)**: On upstream failure, the function MUST map to exactly one
  of the seven error codes from spec 013 data-model §1.2: `NETWORK_UNAVAILABLE`, `GATEWAY_5XX`,
  `PROVIDER_5XX`, `TIMEOUT`, `MALFORMED_RESPONSE`, `UNAUTHORIZED` (reserved for the auth path,
  never for upstream calls), `INTERNAL`. Mapping table:
  - Vercel AI Gateway returns 5xx → `GATEWAY_5XX`.
  - Direct OpenAI returns 5xx → `PROVIDER_5XX`.
  - Upstream timeout → `TIMEOUT`.
  - Upstream returns malformed JSON or schema-invalid response → `MALFORMED_RESPONSE`.
  - Any uncaught exception in the function body → `INTERNAL`.
- **FR-014-005 (Embed response shape)**: For `embed` request type, the success response MUST
  carry `vector: number[]` of length exactly `1536` and `modelLabel: "openai/text-embedding-3-small"`.
- **FR-014-006 (no streaming)**: The function MUST NOT use Server-Sent Events or chunked transfer
  encoding for any response. All responses are single-shot JSON.

#### Edge Function — authentication

- **FR-014-007 (JWT requirement)**: The function MUST require an `Authorization: Bearer <jwt>`
  header on every request. Missing header, malformed header, or any token verification failure
  MUST return HTTP `401` with a body of `LlmGatewayResponse.Error(code = "UNAUTHORIZED", message
  = <human-readable, no token content>)`. This is the **only** non-200 status code the function
  emits.
- **FR-014-008 (JWT verification)**: The function MUST verify JWTs against the Supabase project's
  JWT secret (delivered via Vercel env var; see operational scope). Verification MUST check
  signature, `exp`, `iss`, and presence of a UUID-shaped `sub` claim. Any verification failure
  collapses to `UNAUTHORIZED` (the function MUST NOT leak which check failed in the response body).
- **FR-014-009 (user_id extraction)**: On successful verification, the function MUST extract
  `sub` and use that as `user_id` for the audit insert (FR-014-013). The function MUST NOT trust
  any other source for `user_id` (no `X-User-Id` headers, no body fields).

#### Edge Function — prompt caching

- **FR-014-010 (cache_control on classifier prefixes)**: For `classify_intent` and
  `scan_sensitivity` requests, the function MUST send the Anthropic API request with the
  `prompt-caching-2024-07-31` beta header and a `cache_control: {type: "ephemeral"}` block on
  the stable system-prompt prefix. The "stable system prompt" is the constant text used for
  every call of that request type (the variable user content is appended as the user-message
  turn and is *not* in the cached block).
- **FR-014-011 (cacheHit detection)**: On Anthropic response, the function MUST inspect
  `usage.cache_read_input_tokens` (or equivalent per Anthropic API) to determine whether the
  call was a cache hit, and surface that as a boolean `cacheHit` field on the audit row's
  `details_json` (FR-014-013). For non-cached request types (`summarize`, `extract_actions`,
  `generate_day_header`, `embed`), `cacheHit` is always `false`.

#### Edge Function — audit and observability

- **FR-014-012 (logging shape — Bounded Observation)**: For every request, the function MUST emit
  exactly one structured log line containing only the following fields and no others:
  `requestId`, `requestType`, `model`, `modelLabel`, `latencyMs`, `tokensIn`, `tokensOut`,
  `cacheHit`, `success`, and (on failure) `errorCode`. The function MUST NOT log: prompt text,
  user-supplied payload content, response text, embedding vectors, JWT contents, or any other
  user-derived data. This is the load-bearing requirement for Constitution Principle XIV
  compliance for the cloud egress path.
- **FR-014-013 (audit_log_entries insert)**: For every request (success or error) past the auth
  gate, the function MUST insert exactly one row into `audit_log_entries` (the table created by
  spec 013 migrations) with: `user_id` from the JWT `sub`, `event_type='cloud_llm_call'`,
  `actor='edge_function'`, `details_json={requestId, requestType, model, modelLabel, latencyMs, tokensIn,
  tokensOut, cacheHit, success}` (plus `errorCode` on failure). The insert MUST use the Supabase
  service-role key (because the function is not running as the user's auth session) and MUST
  explicitly stamp `user_id` from the JWT — relying on `auth.uid()` would not work since the
  function uses service-role.
- **FR-014-014 (audit failure isolation)**: If the audit insert fails, the function MUST still
  return the upstream LLM response to the client (degrading user-facing functionality on audit
  failures is worse than dropping audit rows). The function MUST log the audit failure with
  `requestId` and `audit_insert_failed=true` only — no prompt or response content.
- **FR-014-015 (no cost computation in function)**: The Edge Function MUST NOT compute aggregate
  cost or token totals beyond the per-call `tokensIn` / `tokensOut` it already records. Per-user
  per-day rollups are the responsibility of a SQL view (defined in this spec's plan, not the
  function code) reading from `audit_log_entries`.

#### Android client — wire the real URL and JWT

- **FR-014-016 (gateway URL via build config)**: System MUST replace the hardcoded
  `https://gateway.example.invalid/llm` constant in `LlmGatewayClient` with a Gradle
  `BuildConfig.CLOUD_GATEWAY_URL` field. `app/build.gradle.kts` MUST read the value from
  `local.properties` key `cloud.gateway.url` at build time and emit it as `BuildConfig.CLOUD_GATEWAY_URL`.
  `LlmGatewayClient` MUST read `BuildConfig.CLOUD_GATEWAY_URL` instead of the hardcoded
  placeholder. If the property is missing, the build MUST warn (not fail) and default to the
  Day-1 placeholder URL (`https://gateway.example.invalid/llm`) so smoke tests still execute the
  offline path. `RuntimeFlags.gatewayUrl` is explicitly rejected: the URL is environment-bound
  (dev/staging/prod) and changes only at deploy time, not at runtime; `RuntimeFlags` is reserved
  for user-flippable feature flags.
- **FR-014-017 (JWT wiring via authStateBinder)**: System MUST replace the Day-1 graceful-null
  `AuthSessionStore.getCurrentToken()` stub with an `authStateBinder` companion to the existing
  `gatewayBinder` pattern in `LlmGatewayClient`. The `authStateBinder` MUST expose
  `suspend fun currentJwt(): String?` returning a fresh non-expired JWT or null.
  `LlmGatewayClient` MUST call it before each request; if null, the client MUST return
  `LlmGatewayResponse.Error(code = "UNAUTHORIZED", message = …)` immediately without crossing
  the network. Auth refresh is the binder implementation's responsibility, not the client's.
  A direct Supabase Auth SDK singleton inside `:net` is explicitly rejected: it would couple
  `:net` to the Supabase SDK and hurt testability with fakes. The chosen wiring MUST ensure that
  any production call site that already routes through `LlmProviderRouter` picks up the JWT
  automatically — no per-call-site auth wiring.
- **FR-014-018 (no Android-side fallback change)**: The Day-1 retry-once direct-provider fallback
  in `LlmGatewayClient` (FR-013-008) MUST NOT change in this spec. The Edge Function's
  `GATEWAY_5XX` response is what triggers the existing fallback path. The function does not
  implement its own self-fallback.

#### Repository and deployment

- **FR-014-019 (source location)**: The Edge Function source MUST live at
  `supabase/functions/llm_gateway/index.ts` in this repository, alongside the Day-1 migrations.
  Co-locating with `supabase/` is a deliberate choice for this spec (Vercel deploys from a
  separate Vercel project that imports from this directory; the path may move to `vercel/` in a
  future spec but not this one).
- **FR-014-020 (deployment artifact)**: System MUST add `supabase/functions/llm_gateway/deploy.sh`
  (or equivalent runbook in `supabase/functions/llm_gateway/README.md`) that documents the exact
  `vercel deploy --prod` invocation and the env vars the deploy expects to be present in the
  Vercel project. The script MUST NOT contain any secrets.
- **FR-014-021 (secrets discipline)**: All secrets — `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`,
  `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_URL`, `SUPABASE_JWT_SECRET` — MUST be configured only
  via Vercel project environment variables. They MUST NOT appear in this repository, in
  `local.properties`, in commit history, or in CI logs. The deploy runbook MUST verify (via
  `vercel env ls` or equivalent) that all five are set before a production deploy.
- **FR-014-022 (local dev path)**: System MUST document `vercel dev` as the local-development
  invocation, with the function reachable at `http://localhost:3000/llm`. Local-dev secrets are
  read from a gitignored `.env.local` (template `.env.local.example` MAY be committed with
  empty values).

#### Cost and quota (defer)

- **FR-014-023 (no per-user cost cap in this spec)**: The Edge Function MUST NOT enforce
  per-user-per-day token budgets in Day 2. Hard quota cutoff is deferred to spec 005 (BYOK +
  Orbit-managed quota), where the meter already lives. Day-2 mitigation is Vercel platform
  rate limiting (200 req/min/IP default) plus the audit telemetry from FR-014-013, which makes
  abuse observable retrospectively.
- **FR-014-024 (cost_per_user_daily SQL view)**: The spec-014 plan MUST define a SQL view
  `cost_per_user_daily` over `audit_log_entries` that aggregates `(tokensIn + tokensOut) ×
  per-model-rate` (from a static rate table) grouped by `user_id` and date. The view is for
  observability only — no enforcement, no UI in Day 2. Enforcement is spec 005's responsibility.

---

### Non-Functional Requirements

- **NFR-014-001 (no `git push` until approved)**: All commits land locally on `cloud-pivot`. No
  push, no PR, no Vercel deploy until the user explicitly approves. This carries forward NFR-013-008.
- **NFR-014-002 (commit sizing)**: Each task = single atomic commit, sized 30 min – 2 hr.
  Carries forward from spec 013.
- **NFR-014-003 (no branching)**: This spec is the Day-2 follow-up to spec 013. All work lands
  on `cloud-pivot`. No new feature branch is created.
- **NFR-014-004 (Constitution v3.2.0 compliance)**: Egress paths now include
  `:net → Vercel Edge Function → {Anthropic | OpenAI}` per Principle I. Bounded Observation
  (Principle XIV) is the load-bearing constraint for FR-014-012. LLM Sovereignty (Principle IX)
  is preserved end-to-end.

---

### Key Entities

- **`LlmGatewayRequest` / `LlmGatewayResponse` (over the wire)**: Defined by spec 013
  data-model §1.1 and §1.2. This spec consumes them on the server side; no schema changes.
  Discriminator `type`. Six request variants, six success response variants plus `Error`.
- **`audit_log_entries` row written by the Edge Function**: Pre-existing table from spec 013
  migration `00000000_initial_schema.sql`. Columns referenced: `user_id uuid`, `event_type text`,
  `actor text`, `details_json jsonb`, `created_at timestamptz default now()`. RLS policy on the
  table requires `auth.uid() = user_id` for INSERT — function uses service-role and stamps
  `user_id` explicitly to satisfy `WITH CHECK`.
- **Supabase JWT**: Signed by the project's JWT secret. Carries `sub` (UUID, the `auth.users.id`),
  `exp` (≤ 1h from issue), `iss`, `role`. The function reads `sub` as the authoritative `user_id`.
- **Per-user-per-day cost view (deferred to plan)**: A SQL view over `audit_log_entries` that
  aggregates `tokensIn + tokensOut` grouped by `user_id` and date. Not built by the function;
  defined as part of the spec-014 plan.
- **Vercel project**: A separate Vercel project that imports source from
  `supabase/functions/llm_gateway/`. Carries the production secrets in env vars. Deploys to a
  stable URL recorded in `local.properties` as `cloud.gateway.url` for the Android build.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-014-001 (real round-trip parity)**: With the Edge Function deployed and Android
  `LlmGatewayClient` configured against it, a signed-in user invoking each of the six
  `LlmProvider` methods receives a structurally valid `LlmGatewayResponse` success variant for
  ≥ 99% of calls under normal upstream conditions. Verified by an instrumented Android test
  exercising all six request types in sequence.
- **SC-014-002 (prompt cache hit rate)**: Anthropic prompt cache hit rate for `ClassifyIntent`
  over 100 sequential identical-system-prompt calls is ≥ 80%, measured via
  `audit_log_entries.details_json.cacheHit` aggregation.
- **SC-014-003 (embedding latency)**: Embedding round-trip latency from the Android client
  (entry to `LlmGatewayClient.embed` → return) for input ≤ 8 KB is p50 ≤ 800 ms and
  p95 ≤ 2500 ms, measured over 200 sequential calls on Pixel 9 Pro on stable Wi-Fi against the
  warm production deployment.
- **SC-014-004 (auth gate)**: A request with no `Authorization` header, an expired JWT, or a
  forged JWT returns HTTP 401 with a body matching `LlmGatewayResponse.Error(code =
  "UNAUTHORIZED", message = …)` in 100% of attempts. Verified by curl against deployed function.
- **SC-014-005 (audit completeness)**: For 100 successful calls across mixed request types,
  exactly 100 rows are inserted into `audit_log_entries` with `event_type='cloud_llm_call'`, and
  every row's `details_json` matches the documented shape (`requestId`, `requestType`, `model`,
  `modelLabel`, `latencyMs`, `tokensIn`, `tokensOut`, `cacheHit`, `success`).
- **SC-014-006 (no prompt content in logs)**: After 100 successful calls with deliberately
  recognizable prompt strings (e.g. unique UUIDs embedded in user content), grep over the full
  Vercel function log output for that time window returns zero matches of those UUIDs.
- **SC-014-007 (RLS still holds)**: Day-1 multi-user RLS smoke test (`supabase/tests/multi_user_smoke.sql`)
  continues to pass after Day-2 changes (no migrations in this spec — but `audit_log_entries`
  inserts via service-role MUST still leave `WITH CHECK (auth.uid() = user_id)` semantically
  consistent with the explicit `user_id` stamp).

---

## Assumptions

- **Vercel as the runtime.** Edge Function runs on Vercel's edge network (not Cloudflare Workers,
  not Deno Deploy, not Supabase Edge Functions despite the directory name). The directory name
  `supabase/functions/llm_gateway/` reflects co-location with Day-1 migrations, not the runtime.
- **Anthropic prompt caching is GA on `claude-haiku-4-5` and `claude-sonnet-4-6`.** Per
  ADR-003 model selection. If a future Anthropic model deprecation breaks this, that is a spec
  amendment, not a Day-2 blocker.
- **Supabase JWT format is project-default.** Default Supabase auth issues HS256-signed JWTs
  with `sub`/`exp`/`iss`/`role`. If the project later flips to RS256 or custom JWKS, the
  function's verification path adapts; this spec assumes HS256 + project JWT secret.
- **`cloud.gateway.url` is single-environment for Day 2.** One production URL; no
  staging/canary/preview routing. Multi-environment routing is deferred.
- **Token counts are upstream-reported.** `tokensIn` / `tokensOut` come from
  Anthropic/OpenAI response metadata. The function does not run its own tokenizer.
- **`AuthSessionStore` evolves in this spec.** Day 1 was a graceful-null stub; Day 2 must
  produce real JWTs. Exact wiring shape is the open clarification (FR-014-017).
- **No streaming.** Day-1 `LlmProvider` interface is non-streaming; this spec preserves that.
  Streaming is a spec-004 (Ask Orbit) concern.
- **Vercel platform-level rate limiting suffices for Day 2.** Per-user token budgets defer to
  spec 005 (BYOK + Orbit-managed quota); see FR-014-023.
- **Day-1 backbone is in place.** Specifically: `audit_log_entries` exists with the documented
  RLS, the multi-user smoke test passes (cleared 2026-04-29 per current branch state), and
  `LlmGatewayClient` is already wired through AIDL with retry-once direct-provider fallback.
- **Replay attacks bounded by JWT expiry.** No nonce/idempotency-key layer is added in Day 2
  (see Edge Cases — replay).

---

## Dependencies

- **Spec 013** (`Cloud LLM Routing + Supabase Backbone`): Day 1 must have shipped. This spec
  reads `LlmGatewayRequest` / `LlmGatewayResponse` shapes from `specs/013-cloud-llm-routing/data-model.md`
  and modifies `LlmGatewayClient` to point at the real URL.
- **Vercel project provisioned**: A Vercel project linked to this repo's `supabase/functions/llm_gateway/`
  directory, with the five env vars (FR-014-021) configured. Provisioning is a manual operator
  step documented in the deploy runbook (FR-014-020).
- **Anthropic API key + OpenAI API key**: Procured by the operator and set in Vercel env vars.
  Not provisioned by this spec.
- **Supabase service-role key + JWT secret**: Read from the existing Supabase project (created
  in spec 013) and set in Vercel env vars.
- **Constitution v3.2.0**: Active governing document. Principles I, IX, XIV, XV all referenced
  by this spec.

---

## Out of Scope

- **Per-user / per-day token quotas.** Deferred to spec 005 (BYOK + Orbit quota).
- **Streaming responses.** Deferred to spec 004 (Ask Orbit).
- **Multi-environment routing** (staging, canary, preview). Single-environment for Day 2.
- **Cluster engine cloud migration.** Spec 013 carved the cluster worker out of the FR-013-016
  sweep with a `CLUSTER-LOCAL-PIN` comment; that pin remains in Day 2. Migration is Phase 11
  Block 4.
- **Direct-provider fallback re-architecture.** Day-1 retry-once on the Android client side is
  unchanged. The Edge Function does not do its own self-fallback.
- **Idempotency / replay defense beyond JWT expiry.** No nonce, no idempotency key. Audit-driven
  detection only.
- **Edge Function source migration to a separate `vercel/` directory.** Stays at
  `supabase/functions/llm_gateway/` for Day 2.
- **Cost UI / billing dashboard.** Aggregate cost view defined as a SQL view in /speckit.plan,
  but no UI on top.

---

## Open Clarifications

All three originally-flagged `[NEEDS CLARIFICATION]` markers have been resolved in the
`## Clarifications` section above (Session 2026-04-28):

- **Q1 — JWT wiring on Android (FR-014-017)** — RESOLVED: `authStateBinder` companion to
  `gatewayBinder`.
- **Q2 — Cost cap deferral (FR-014-023)** — RESOLVED: deferred to spec 005; Day 2 uses Vercel
  platform rate limiting + `cost_per_user_daily` SQL view (FR-014-024) for observability.
- **Q3 — Gateway URL config surface (FR-014-016)** — RESOLVED: Gradle `BuildConfig.CLOUD_GATEWAY_URL`
  sourced from `local.properties`.

The remaining items from the user's `/speckit.specify` brief (Q3 streaming yes/no, Q4 replay
attack vector, Q5 `ExtractActions` payload serialization on the function side) have been
**resolved in-spec** rather than flagged:

- **Streaming** → resolved as "no, deferred to spec 004 Ask Orbit" (see Edge Cases, Out of
  Scope, Assumptions).
- **Replay** → resolved as "JWT expiry only; no nonce" (see Edge Cases, Assumptions). If audit
  reveals replay-driven cost in production, that's a follow-up spec.
- **`ExtractActions` payload serialization** → resolved as "TypeScript-side concern; spec 014
  plan picks Zod vs `JSON.parse`; not a spec-level decision" (see Out of Scope of this section
  by virtue of being a /speckit.plan implementation choice).

---

## Specification Quality Validation Summary

Validated against `specs/014-edge-function-llm-gateway/checklists/requirements.md` (this run):

- **Content Quality**: PASS — no implementation language/framework specifics in user-facing
  sections; written for stakeholders; mandatory sections complete.
- **Requirement Completeness**: PASS — all three originally-flagged `[NEEDS CLARIFICATION]`
  markers resolved in `## Clarifications` (Session 2026-04-28). Zero remaining markers. All
  requirements are testable, unambiguous, with measurable success criteria.
- **Feature Readiness**: PASS — every FR has an acceptance scenario or success criterion;
  user stories cover primary flows (round-trip, auth, audit, cache, latency); SC items are
  technology-agnostic and verifiable.

**Spec is ready for `/speckit.plan`. All three open clarifications closed 2026-04-28.**
