# Tasks: 014-edge-function-llm-gateway (Day 2 — Vercel AI Gateway Edge Function)

**Input**: Design documents in `/Users/richelgomez/dev/capsule-app/specs/014-edge-function-llm-gateway/`
**Prerequisites**: spec.md, plan.md, research.md, data-model.md, quickstart.md, contracts/{gateway-request-response,auth-jwt-contract,audit-row-contract}.md
**Branch**: `cloud-pivot` (no new feature branch — NFR-014-003).
**Tests**: Unit-test tasks are included where the spec/plan calls for them (Phases B/C/D/E/F/H). E2E in Phase I.

## Format

`- [ ] T014-NNN [P?] [Phase X] [Tag] Description (file paths)`

- **`T014-NNN`**: sequential atomic-commit ID. One ID = one commit (NFR-014-002, sized 30 min – 2 hr).
- **`[P]`**: parallel-safe — touches independent files **and** has no ordering dependency on an earlier incomplete task.
- **`[Phase X]`**: maps to plan.md Phase A–I.
- **`[Tag]`**: `[US-014-001]`…`[US-014-005]` map to spec.md user stories; `[FOUND]` = foundational/infra (no user-story tag).
- Each task has 1–3 acceptance criteria bullets, a Constitution check, and (where applicable) a `Depends on:` line.

## User-Story → Tag Map

| Tag | Source | Priority |
|-----|--------|----------|
| `[US-014-001]` | spec.md User Story 1 — real cloud LLM round-trip | P1 |
| `[US-014-002]` | spec.md User Story 2 — auth gate | P1 |
| `[US-014-003]` | spec.md User Story 3 — audit, no prompt leak | P1 |
| `[US-014-004]` | spec.md User Story 4 — prompt-cache hit rate ≥ 80% | P2 |
| `[US-014-005]` | spec.md User Story 5 — latency budgets | P2 |
| `[FOUND]`     | foundational / shared infrastructure          | n/a  |

---

## Phase A — Vercel project bootstrap

Goal: a Vercel-deployable skeleton at `supabase/functions/llm_gateway/` that returns `501 Not Implemented` for `POST /llm`. No auth, no upstream calls, no audit. Just enough scaffolding for Phases B–F to land code under.

### T014-001 [Phase A] [FOUND] Create function package skeleton

Files (create):
- `supabase/functions/llm_gateway/package.json`
- `supabase/functions/llm_gateway/tsconfig.json`
- `supabase/functions/llm_gateway/vercel.json`

Contents:
- `package.json`: name `orbit-llm-gateway`, ESM-only, deps `@anthropic-ai/sdk`, `openai`, `@supabase/supabase-js`, `jose`, `zod`. devDeps `typescript`, `vitest`, `@types/node`.
- `tsconfig.json`: target `ES2022`, module `ESNext`, moduleResolution `Bundler`, strict.
- `vercel.json`: runtime `edge`, region `iad1`, single function `index.ts` mapped to path `/llm`.

Acceptance:
- `cd supabase/functions/llm_gateway && npm install` completes with no errors.
- `npx tsc --noEmit` passes (no source files yet, so vacuously).
- `vercel.json` schema validates against Vercel's CLI (`vercel deploy --dry-run` exits 0 once token wired, but is NOT run in this task).

Constitution check: ✓ (no principle implications — pure scaffolding).

### T014-002 [P] [Phase A] [FOUND] Add local-dev env template

Files (create):
- `supabase/functions/llm_gateway/.env.local.example`

Contents: comment-only template listing the five required env vars (`OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_URL`, `SUPABASE_JWT_SECRET`) with empty values and a header line `# DO NOT COMMIT REAL VALUES — see .gitignore`.

Files (modify):
- `.gitignore` (root) — append `supabase/functions/llm_gateway/.env.local` (the real local file, not the template).

Acceptance:
- `git status --ignored` shows `.env.local` ignored if created.
- `.env.local.example` contains exactly five `KEY=` lines with empty RHS.

Constitution check: Principle XV (Local-First Execution) + FR-014-021 secrets discipline — example file MUST contain no real secrets. Manual review confirms zero non-placeholder values committed.

Depends on: T014-001.

### T014-003 [Phase A] [FOUND] index.ts skeleton returning 501

Files (create):
- `supabase/functions/llm_gateway/index.ts`

Contents: edge-runtime `export default async function handler(req: Request): Promise<Response>` that returns HTTP 501 `{ "type":"error", "code":"INTERNAL", "message":"not yet implemented" }` with `Content-Type: application/json`. Method-not-allowed → 405. Adds `export const config = { runtime: "edge" }`.

Acceptance:
- `npx tsc --noEmit` passes.
- `vercel dev` (locally, smoke only) → `curl -X POST http://localhost:3000/llm` returns HTTP 501 with the documented body shape.
- File is < 60 lines (skeleton — real handlers land in C/D/E/F).

Constitution check: ✓ (placeholder; no egress, no storage).

Depends on: T014-001.

### T014-004 [P] [Phase A] [FOUND] Deploy runbook + script

Files (create):
- `supabase/functions/llm_gateway/deploy.sh` — one-liner runbook script: `vercel deploy --prod` after `vercel env ls` confirms all five env vars set in `production`. Exits non-zero on missing var.
- `supabase/functions/llm_gateway/README.md` — operator runbook per FR-014-020: env-var inventory, `vercel link` flow, `vercel dev` flow, deploy flow, smoke-curl examples (mirrors quickstart.md §3–4 but as the runbook of record).

Acceptance:
- `bash supabase/functions/llm_gateway/deploy.sh --dry-run` (or equivalent guard before `vercel deploy`) exits 0 when env is fake-populated and non-zero when any var is missing.
- README documents all five env vars from FR-014-021 and references the audit-row contract for `details_json` shape.
- No real secrets in either file.

Constitution check: FR-014-021 (secrets discipline) — verified by grep; FR-014-020 (deployment artifact present) — satisfied.

Depends on: T014-001.

---

## Phase B — JWT auth gate (`jose` + Supabase HS256)

Goal: every request past Phase A's 501 stub now flows through a real auth gate. Missing/invalid auth → HTTP 401 + `UNAUTHORIZED` body. Valid auth → handler reaches Phase C dispatch (still stubbed for now).

### T014-005 [Phase B] [US-014-002] lib/auth.ts + lib/errors.ts

Files (create):
- `supabase/functions/llm_gateway/lib/auth.ts` — `verifyJwt(rawHeader)` per auth-jwt-contract.md §2. Uses `jose.jwtVerify` over `SUPABASE_JWT_SECRET` (HS256), checks issuer = `${SUPABASE_URL}/auth/v1`, asserts `sub` is UUID-shaped, asserts `role === "authenticated"`. Throws `UnauthorizedError`.
- `supabase/functions/llm_gateway/lib/errors.ts` — `ErrorCode` enum (seven values from data-model.md §2.1) + `UnauthorizedError` class + closed-enum `UNAUTHORIZED_MESSAGES` map per auth-jwt-contract.md §3 + helper `errorResponse(code, message, requestId, status)` returning a `Response` with the standard envelope.

Acceptance:
- `npx tsc --noEmit` passes.
- All seven `ErrorCode` literals exported.
- `verifyJwt` does NOT log the token, claims, or stack traces — only throws. (Manual grep for `console.log` / `console.error` in `auth.ts` returns zero.)

Constitution check: Principle XIV (Bounded Observation) load-bearing — verified no token/claim ever leaves `verifyJwt` via stdout.

Depends on: T014-003.

### T014-006 [Phase B] [US-014-002] Wire auth gate into index.ts + unit tests

Files (modify):
- `supabase/functions/llm_gateway/index.ts` — top-of-handler call to `verifyJwt(req.headers.get("Authorization"))`; `UnauthorizedError` caught and mapped to HTTP 401 + `errorResponse("UNAUTHORIZED", message, requestId, 401)`. On success, attaches `userId = sub` to a request-scoped context object passed to (still-stubbed) dispatch.

Files (create):
- `supabase/functions/llm_gateway/test/auth.test.ts` — vitest unit tests covering all six failure modes from auth-jwt-contract.md §1 (missing header, scheme mismatch, empty token, jose verification failure, missing `sub`, non-`authenticated` role) + happy path. Uses an HMAC-signed JWT minted in-test against a fake `SUPABASE_JWT_SECRET` env.

Acceptance:
- `npx vitest run test/auth.test.ts` passes 7/7 cases.
- `curl -X POST http://localhost:3000/llm` (no auth) returns HTTP 401 with body `{"type":"error","code":"UNAUTHORIZED","message":"Missing or invalid Authorization header"}`.
- `curl` with a valid Supabase JWT no longer returns 401 (still 501 from Phase A stub — proves gate passes).

Constitution check: FR-014-007/008/009; Principle XIV — verified message strings come from the closed enum, no internals leaked.

Depends on: T014-005.

---

## Phase C — Request router (Zod-validated discriminated union)

Goal: parsed/validated requests reach a per-`type` handler dispatch table; six stub handlers exist; malformed bodies return `INTERNAL`.

### T014-007 [Phase C] [US-014-001] Zod schemas + TS discriminated-union types

Files (create):
- `supabase/functions/llm_gateway/types.ts` — TypeScript types from data-model.md §1 (LlmGatewayRequest, six variants, supporting JSON-mirror types) and §2 (LlmGatewayResponse, seven variants).
- `supabase/functions/llm_gateway/lib/schemas.ts` — Zod schemas mirroring the TS types, including UUIDv4 regex on `requestId`. Exports `LlmGatewayRequestSchema` (root discriminated union) + per-payload schemas.

Acceptance:
- `npx tsc --noEmit` passes.
- Round-trip test (added in T014-008): every example body in contracts/gateway-request-response.md §8 parses successfully.
- `requestId` regex rejects non-UUIDv4 strings.

Constitution check: Principle V (Stable Data Contracts) — TS types are exact mirrors of Kotlin sealed classes from spec 013 data-model §1.1/§1.2.

Depends on: T014-003.

### T014-008 [Phase C] [US-014-001] Router dispatch + 6 stub handlers

Files (create):
- `supabase/functions/llm_gateway/handlers/embed.ts`
- `supabase/functions/llm_gateway/handlers/summarize.ts`
- `supabase/functions/llm_gateway/handlers/extract_actions.ts`
- `supabase/functions/llm_gateway/handlers/classify_intent.ts`
- `supabase/functions/llm_gateway/handlers/generate_day_header.ts`
- `supabase/functions/llm_gateway/handlers/scan_sensitivity.ts`

Each stub: `export async function handle(req, ctx): Promise<LlmGatewayResponse>` returning `Error(code:"INTERNAL", message:"not yet implemented", requestId:req.requestId)`.

Files (modify):
- `supabase/functions/llm_gateway/index.ts` — after auth gate: parse JSON body, run `LlmGatewayRequestSchema.safeParse`. On failure → HTTP 200 + `Error(code:"INTERNAL", message:"request body failed validation", requestId:"")` (does NOT echo Zod issues per gateway-request-response.md §2). On success → dispatch to the right handler by `type`. Wraps result in `Response` with HTTP 200.

Files (create):
- `supabase/functions/llm_gateway/test/router.test.ts` — vitest cases: malformed body → INTERNAL; each of six valid bodies dispatches to the matching stub handler.

Acceptance:
- `npx vitest run test/router.test.ts` passes 7/7 cases (one per type + malformed).
- `curl` of any of the six valid example bodies (with valid JWT) returns HTTP 200 + `{"type":"error","code":"INTERNAL","message":"not yet implemented",...}`.
- Malformed body returns HTTP 200 + `INTERNAL` with the static message (no Zod issue echoed — manual inspection of response).

Constitution check: FR-014-001/006 (single endpoint, JSON only, no streaming); Principle XIV (no Zod issue leaked).

Depends on: T014-006, T014-007.

---

## Phase D — Anthropic handlers (Sonnet × 3, Haiku × 2 with prompt cache)

Goal: five Anthropic handlers fully implemented. Sonnet trio is uncached. Haiku pair uses `prompt-caching-2024-07-31` beta with `cache_control: {type:"ephemeral"}` on the stable system-prompt prefix. `cacheHit` detection from response usage.

### T014-009 [Phase D] [US-014-001] Anthropic SDK wrapper + Sonnet handlers

Files (create):
- `supabase/functions/llm_gateway/lib/anthropic.ts` — Anthropic SDK client configured with `baseURL` set to the Vercel AI Gateway endpoint (`process.env.ANTHROPIC_API_KEY` doubles as the gateway key per plan.md Open Clarification #2). Helpers: `callAnthropic(model, messages, opts)` returning `{ text, usage }`; `cachedSystemPrompt(prefix)` returning the `[{type:"text", text:prefix, cache_control:{type:"ephemeral"}}]` content-block shape.

Files (modify):
- `supabase/functions/llm_gateway/handlers/summarize.ts` — Sonnet (`anthropic/claude-sonnet-4-6`), 60s timeout, no cache. Returns `SummarizeResponse`. Maps upstream errors per gateway-request-response.md §6: gateway 5xx → `GATEWAY_5XX`, timeout → `TIMEOUT`, malformed → `MALFORMED_RESPONSE`.
- `supabase/functions/llm_gateway/handlers/extract_actions.ts` — Sonnet, 30s timeout, no cache. Validates upstream JSON array against `ActionProposalJson[]` Zod schema (added to `lib/schemas.ts`); validation failure → `MALFORMED_RESPONSE`.
- `supabase/functions/llm_gateway/handlers/generate_day_header.ts` — Sonnet, 30s timeout, no cache.

Files (create):
- `supabase/functions/llm_gateway/test/anthropic_handlers.test.ts` — vitest cases per handler: happy path (mocked), 5xx → `GATEWAY_5XX`, timeout → `TIMEOUT`, non-JSON body → `MALFORMED_RESPONSE`. `extract_actions` schema-invalid → `MALFORMED_RESPONSE`.

Acceptance:
- All three handlers return the documented success variants when given mock Anthropic responses.
- `cacheHit` is hardcoded `false` for these three handlers (verified in test).
- Error mapping table from gateway-request-response.md §6 fully exercised by mocks.

Constitution check: Principle XIV — handlers MUST NOT log prompt or response text. Manual grep for `console.log` in handler files returns only the per-request operator log line (added in Phase F).

Depends on: T014-008.

### T014-010 [Phase D] [US-014-004] Haiku handlers with cached prefix

Files (modify):
- `supabase/functions/llm_gateway/handlers/classify_intent.ts` — Haiku (`anthropic/claude-haiku-4-5`), 30s timeout. Sends request with `prompt-caching-2024-07-31` beta header. System prompt prefix wrapped via `cachedSystemPrompt(...)`. User content (variable) appended as a non-cached user-message turn. Inspects `usage.cache_read_input_tokens` to set `cacheHit = (cache_read_input_tokens > 0)` on the result context. Returns `ClassifyIntentResponse` with confidence ∈ [0,1].
- `supabase/functions/llm_gateway/handlers/scan_sensitivity.ts` — same shape; returns `ScanSensitivityResponse` with `tags: string[]`.

Files (modify):
- `supabase/functions/llm_gateway/lib/anthropic.ts` — extend `callAnthropic` return type to surface `cacheHit: boolean` derived from `usage.cache_read_input_tokens`.
- `supabase/functions/llm_gateway/test/anthropic_handlers.test.ts` — add cases: classify_intent first call `cacheHit=false`, second call (mocked with `cache_read_input_tokens=512`) `cacheHit=true`. Same for scan_sensitivity.

Acceptance:
- Classifier handler request body sent to Anthropic includes `cache_control:{type:"ephemeral"}` on the system prefix (verified by mock spy).
- Beta header `anthropic-beta: prompt-caching-2024-07-31` set on the upstream request.
- `cacheHit` correctly populated from mocked `usage.cache_read_input_tokens` values.

Constitution check: FR-014-010/011; Principle XIV — `intent` string never written to operator log or audit (audit wiring lands in Phase F but the contract is tested here that handlers do not log it).

Depends on: T014-009.

### T014-011 [Phase D] [US-014-004] Prompt-cache hit-rate verification harness

Files (create):
- `supabase/functions/llm_gateway/test/cache_hit_rate.harness.ts` — script (NOT a unit test; gated by `RUN_LIVE=1` env) that issues 100 sequential `classify_intent` requests with identical system prompt against a deployed gateway (or `vercel dev`), then queries Supabase `audit_log_entries` and computes `cacheHit` ratio. Prints `PASS` if ratio ≥ 0.80 (SC-014-002), `FAIL` otherwise. Exit code reflects pass/fail.
- `supabase/functions/llm_gateway/test/README.md` — explains the harness, when to run it (Phase I E2E), and the `RUN_LIVE` env-var contract.

Acceptance:
- Harness compiles (`npx tsc --noEmit`).
- Harness skipped under default `npx vitest run` (no `RUN_LIVE`).
- README documents the harness contract and links to SC-014-002.

Constitution check: ✓ (test harness; observability-only, no enforcement).

Depends on: T014-010. Note: actual run executes in T014-021 (Phase I); this task only commits the harness.

---

## Phase E — OpenAI embeddings handler

Goal: `embed` handler complete. OpenAI direct (no AI Gateway), `text-embedding-3-small`, `dimensions: 1536`, length assert.

### T014-012 [Phase E] [US-014-001] OpenAI client + embed handler

Files (create):
- `supabase/functions/llm_gateway/lib/openai.ts` — OpenAI SDK client with `process.env.OPENAI_API_KEY`. Helper `embedText(text)` returning `{ vector: number[], usage }`.

Files (modify):
- `supabase/functions/llm_gateway/handlers/embed.ts` — calls `embedText`, validates `vector.length === 1536` and all elements are finite numbers (FR-014-005, gateway-request-response.md §4.1). Length mismatch → `MALFORMED_RESPONSE`. Returns `EmbedResponse(modelLabel:"openai/text-embedding-3-small")`.

Files (create):
- `supabase/functions/llm_gateway/test/embed_handler.test.ts` — happy path (mocked 1536 floats), wrong-dimension → `MALFORMED_RESPONSE`, OpenAI 5xx → `PROVIDER_5XX`, timeout → `TIMEOUT`.

Acceptance:
- 4/4 vitest cases pass.
- `curl` (with valid JWT) `embed` request returns 1536-element vector against mocked OpenAI.
- `modelLabel === "openai/text-embedding-3-small"` exactly.

Constitution check: Principle XIV — vector content never logged.

Depends on: T014-008.

---

## Phase F — Audit insert (service-role + bounded log line)

Goal: every authenticated request — success or upstream failure — writes exactly one row into `audit_log_entries` with the documented `details_json` shape. Audit failure does NOT degrade user-facing response. Operator log line emitted per request, bounded fields only.

### T014-013 [Phase F] [US-014-003] Service-role Supabase client + recordAuditRow

Files (create):
- `supabase/functions/llm_gateway/lib/audit.ts` — module-level Supabase service-role client (`@supabase/supabase-js` with `SUPABASE_URL` + `SUPABASE_SERVICE_ROLE_KEY`). Function `recordAuditRow(input: AuditInput): Promise<void>` that inserts per audit-row-contract.md §1: `event_type='cloud_llm_call'`, `actor='edge_function'`, `subject_id=null`, `user_id` stamped from JWT `sub`, `details_json` per §2.1/§2.2. Wraps in try/catch; on failure logs `{level:"error",requestId,audit_insert_failed:true}` (FR-014-014 / contract §4) and resolves (does NOT throw).
- `supabase/functions/llm_gateway/test/audit.test.ts` — vitest cases: success-row shape match, error-row shape includes `errorCode`, audit failure does NOT throw, log shape on failure carries only `{requestId, audit_insert_failed}`.

Acceptance:
- 4/4 vitest cases pass against a mocked Supabase client.
- Manual schema check: emitted `details_json` keys are exactly the closed enum from audit-row-contract.md §2 — no extras.
- Audit-failure log line contains exactly two fields plus `level` (enforced by test).

Constitution check: Principle XIV (Bounded Observation) **load-bearing** — verified `details_json` never includes prompt, response, vector, JWT, or `intent` string. Principle VIII (User-Owned Data) — service-role bypasses RLS but explicit `user_id` stamp keeps `SELECT` RLS consistent (SC-014-007).

Depends on: T014-005.

### T014-014 [Phase F] [US-014-003] Wire audit + operator log into all 6 handlers

Files (modify):
- `supabase/functions/llm_gateway/index.ts` — wrap each handler dispatch in `performance.now()` measurement. After handler returns (success or error), build `AuditInput` with `requestId, requestType, model, modelLabel, latencyMs, tokensIn, tokensOut, cacheHit, success` (+ `errorCode` on error per audit-row-contract.md §2.2; 0 for tokens on errors). Call `recordAuditRow` (fire-and-forget — audit failure does not change response). Emit one structured `console.log` per request matching the operator-log shape from audit-row-contract.md §6 (adds `userId` field versus the audit `details_json`; never includes prompt/response).
- `supabase/functions/llm_gateway/handlers/*.ts` — extend each handler return shape to include `model`, `modelLabel`, `tokensIn`, `tokensOut`, `cacheHit` so `index.ts` can build the audit row without a second upstream call.

Files (create):
- `supabase/functions/llm_gateway/test/index_audit_integration.test.ts` — full-flow vitest: mock all six handlers + Supabase client, assert (a) 1 audit row per request, (b) row shape matches contract, (c) operator log line shape matches contract, (d) audit-insert failure → response still success.

Acceptance:
- Integration test 4/4 cases pass.
- Bounded-observation invariant verified by test: a deliberately recognizable UUID embedded in the prompt does NOT appear in the operator log line or `details_json` (mirrors SC-014-006 at unit-test scale).
- Per gateway-request-response.md §6: error rows carry `errorCode` from the seven-value enum.

Constitution check: FR-014-012/013/014; Principle XIV load-bearing — automated test enforces no-prompt-leak.

Depends on: T014-009, T014-010, T014-012, T014-013.

---

## Phase G — `cost_per_user_daily` view migration

Goal: a forward-only Postgres migration creates the cost-observability view. View inherits `audit_log_entries` SELECT RLS (each user sees only their own rows). No enforcement.

### T014-015 [Phase G] [FOUND] Cost view migration + RLS smoke addition

Files (create):
- `supabase/migrations/00000003_cost_per_user_daily.sql` — exact DDL from data-model.md §5.1 (rate table CTE + LEFT JOIN aggregate view + `COMMENT ON VIEW`). Uses `CREATE OR REPLACE VIEW`; RLS inherited from underlying table.

Files (modify):
- `supabase/tests/multi_user_smoke.sql` — add a block: as User A, insert one fake `audit_log_entries` row with `event_type='cloud_llm_call'`. Switch to User B's role. `SELECT * FROM cost_per_user_daily WHERE user_id = '<A>'` MUST return 0 rows. Assert + raise NOTICE on PASS/FAIL.

Acceptance:
- `supabase db push` (or `psql -f` against staging) applies the migration cleanly.
- Querying the view as a real user returns sensible aggregate columns (`request_count`, `tokens_in_total`, `tokens_out_total`, `cost_usd_estimate`).
- Updated multi-user smoke test prints PASS for the cross-user view check (SC-014-007).

Constitution check: Principle XI (Forward-Only Migrations) — `CREATE OR REPLACE VIEW` is forward-only; no DROP. Principle VIII (User-Owned Data) — RLS inheritance verified by smoke.

Depends on: T014-013 (audit shape stable before the view depends on `details_json` keys). Can be done in parallel with Phase E/F implementation since the migration text only references the contract, not running code; but per plan.md phases run sequentially, so we keep the dependency.

---

## Phase H — Android wiring (BuildConfig URL + AuthStateBinder + JWT header)

Goal: replace the hardcoded placeholder URL with `BuildConfig.CLOUD_GATEWAY_URL` from `local.properties`; introduce `AuthStateBinder` interface + Supabase implementation; `LlmGatewayClient` stamps `Authorization: Bearer <jwt>` on every call; null JWT → `Error(UNAUTHORIZED)` without network.

### T014-016 [Phase H] [US-014-001] Gradle BuildConfig.CLOUD_GATEWAY_URL

Files (modify):
- `app/build.gradle.kts` — read `cloud.gateway.url` from `local.properties` at build configuration time. Emit as `buildConfigField("String", "CLOUD_GATEWAY_URL", "\"<value>\"")`. If the property is missing, log a Gradle warning and default to `"https://gateway.example.invalid/llm"` (Day-1 placeholder per FR-014-016). Ensure `buildFeatures.buildConfig = true` if not already.

Acceptance:
- `./gradlew :app:compileDebugKotlin` passes.
- Generated `BuildConfig.java` (under `app/build/generated/`) contains `public static final String CLOUD_GATEWAY_URL = "...";` with the value from `local.properties` (or placeholder if missing).
- Building with `cloud.gateway.url` absent prints a Gradle WARN line referencing FR-014-016 — does NOT fail the build.

Constitution check: FR-014-021 (secrets discipline) — URL is non-secret config; explicitly distinct from API keys (which never enter `local.properties`).

Depends on: T014-001 (so `cloud.gateway.url` template exists; document the key in `supabase/functions/llm_gateway/README.md`).

### T014-017 [Phase H] [US-014-001] LlmGatewayClient reads BuildConfig.CLOUD_GATEWAY_URL

Files (modify):
- `app/src/main/java/com/capsule/app/ai/gateway/LlmGatewayClient.kt` — replace hardcoded `"https://gateway.example.invalid/llm"` with `BuildConfig.CLOUD_GATEWAY_URL`. No other behavior change in this task (auth header + binder land in T014-019).

Files (create / modify):
- Adjust `LlmGatewayClient`'s existing unit test (location per Day-1 layout, e.g. `app/src/test/java/com/capsule/app/ai/gateway/LlmGatewayClientTest.kt`) to assert the request URL matches `BuildConfig.CLOUD_GATEWAY_URL` rather than a hardcoded literal.

Acceptance:
- `./gradlew :app:testDebugUnitTest --tests '*LlmGatewayClient*'` is green.
- Manual: with `cloud.gateway.url=https://example.test/llm` in `local.properties`, request URL captured by the test's MockWebServer wiring is `https://example.test/llm`.

Constitution check: ✓ (refactor; no new principle implications).

Depends on: T014-016.

### T014-018 [P] [Phase H] [US-014-002] AuthStateBinder interface + SupabaseAuthStateBinder

Files (create):
- `app/src/main/java/com/capsule/app/auth/AuthStateBinder.kt` — `interface AuthStateBinder { suspend fun currentJwt(): String? }`.
- `app/src/main/java/com/capsule/app/data/auth/SupabaseAuthStateBinder.kt` — production impl reading the current Supabase Auth session from the existing Supabase Kotlin SDK singleton; returns the access token (`session.accessToken`) if present and non-expired (Supabase SDK handles refresh internally per FR-014-017), else null.
- `app/src/main/java/com/capsule/app/di/AuthModule.kt` (new file OR additive to existing DI module — pick whichever matches Day-1 layout) — Hilt `@Binds` `AuthStateBinder` to `SupabaseAuthStateBinder` in the appropriate component scope.

Files (create):
- `app/src/test/java/com/capsule/app/auth/SupabaseAuthStateBinderTest.kt` — unit test with a fake session source: returns token when session present, null when absent.

Acceptance:
- `./gradlew :app:compileDebugKotlin :app:testDebugUnitTest` is green.
- `AuthStateBinder` has no Supabase SDK references (interface in `auth/`, impl in `data/auth/`) — verified by import inspection.
- DI binding verified by an instrumentation-free test: a Hilt rule resolves `AuthStateBinder` to `SupabaseAuthStateBinder`.

Constitution check: FR-014-017 — `:net` stays decoupled from Supabase SDK; testability via fakes preserved (the interface is in `:app` for now, but its signature is small enough to relocate to a `:auth-api` module in a later spec without breaking `:net`).

Depends on: T014-001 (independent of T014-016/T014-017 — different file set, can land in parallel — hence `[P]`).

### T014-019 [Phase H] [US-014-002] LlmGatewayClient integrates AuthStateBinder

Files (modify):
- `app/src/main/java/com/capsule/app/ai/gateway/LlmGatewayClient.kt` — accept `AuthStateBinder` via constructor (Hilt-injected). Before each request, call `authStateBinder.currentJwt()`. If null → return `LlmGatewayResponse.Error(code = "UNAUTHORIZED", message = "no active Supabase session", requestId = req.requestId)` immediately (no OkHttp call). If non-null → set request header `Authorization: Bearer <jwt>`.

Files (modify):
- DI wiring (the same module touched in T014-018) — provide `AuthStateBinder` to `LlmGatewayClient`.

Files (modify / create):
- `app/src/test/java/com/capsule/app/ai/gateway/LlmGatewayClientTest.kt` — extend existing tests:
  - `null JWT → Error(UNAUTHORIZED) + no network call` (verified by zero MockWebServer requests).
  - `valid JWT → Authorization: Bearer <jwt>` header present on the recorded request (MockWebServer `takeRequest()` assertion).
  - Existing Day-1 retry-once direct-provider fallback unchanged (FR-014-018) — verified by an existing test still passing.

Acceptance:
- `./gradlew :app:testDebugUnitTest --tests '*LlmGatewayClient*'` is green.
- Null-JWT test confirms zero network requests issued by MockWebServer (`mockWebServer.requestCount == 0`).
- Valid-JWT test confirms the header is exactly `Authorization: Bearer <token>`.

Constitution check: FR-014-007/017/018; Principle I (egress boundaries) — null-auth returns without crossing the network.

Depends on: T014-017, T014-018.

---

## Phase I — End-to-end smoke against deployed function

Goal: prove SC-014-001 through SC-014-007 against a real Vercel deployment. Final closeout commit updates `tasks.md` status.

### T014-020 [Phase I] [US-014-001] Deploy gateway to Vercel staging

Operational task (no source-code changes; this commit captures the deployment record only).

Files (modify):
- `local.properties` (developer machine only, gitignored) — set `cloud.gateway.url=https://<project>.vercel.app/llm`.
- `supabase/functions/llm_gateway/README.md` — append a deploy log entry: timestamp, deployed Git SHA, Vercel project URL, output of `vercel env ls` (with values redacted to `***`).

Steps:
1. Confirm all 5 env vars set in Vercel `production` (`vercel env ls`).
2. Run `bash supabase/functions/llm_gateway/deploy.sh` (or equivalent — `vercel deploy --prod` from the function directory).
3. Capture stable production URL.
4. Smoke `curl -i -X POST https://<url>/llm` with no auth → expect 401.
5. Smoke `curl` with a real Supabase JWT + `embed` body → expect 200 + 1536-element vector.

Acceptance:
- Deployed URL is reachable; 401 path returns the contract-shaped body.
- Embed smoke returns valid `embed_response`.
- README updated; `git diff supabase/functions/llm_gateway/README.md` shows only the deploy-log append.

Constitution check: NFR-014-001 (no `git push` until user-approved) — deploy itself is to Vercel only; **no `git push` is executed**. The commit lands locally; user explicit approval gates a separate `git push`. Principle XIV — verified Vercel logs UI shows bounded log lines.

Depends on: T014-014, T014-015. **Requires explicit user approval before running per NFR-014-001.**

### T014-021 [Phase I] [US-014-001] [US-014-004] [US-014-005] Android E2E smoke against deployed URL

Files (create):
- `app/src/androidTest/java/com/capsule/app/ai/gateway/EdgeFunctionEndToEndTest.kt` — instrumented Android test running on a Pixel 9 Pro emulator (or device). Six sequential requests (one per type) against the deployed URL with a real Supabase JWT minted in the test setup. Asserts non-Error responses for each. Records latencies for SC-014-003.

Steps (manual, captured in test or runbook):
1. `./gradlew connectedDebugAndroidTest --tests '*EdgeFunctionEndToEndTest*'` — six requests pass (SC-014-001).
2. Run `npx tsx supabase/functions/llm_gateway/test/cache_hit_rate.harness.ts` (with `RUN_LIVE=1`) — confirm ratio ≥ 0.80 (SC-014-002).
3. Latency aggregation from test output: p50 ≤ 800 ms, p95 ≤ 2500 ms over 200 1-KB embed calls (SC-014-003).
4. `psql -c "SELECT ... FROM audit_log_entries WHERE created_at > now()-interval '1 hour'"` — confirm 1:1 row count vs requests (SC-014-005), every row's `details_json` matches the contract.
5. `vercel logs <project> --output=raw --since=1h | grep <unique-prompt-uuid>` — confirm zero matches (SC-014-006).
6. `psql -f supabase/tests/multi_user_smoke.sql` — confirm PASS (SC-014-007).
7. `curl` no-auth and `curl` forged-JWT — confirm 401 + UNAUTHORIZED body (SC-014-004).

Acceptance:
- All seven success criteria measurably met. Each criterion has a recorded measurement (test output, SQL output, log grep, or curl status).
- A `Phase-I-evidence.md` (committed under `specs/014-edge-function-llm-gateway/`) records the measurements with timestamps. Optional but strongly recommended for audit trail.

Constitution check: Principle XIV — SC-014-006 explicitly verifies no prompt content in logs; this task is the load-bearing test for the audit trail. NFR-014-001 — no `git push`.

Depends on: T014-019, T014-020.

### T014-022 [Phase I] [FOUND] Update tasks.md status log + close out spec 014

Files (modify):
- `specs/014-edge-function-llm-gateway/tasks.md` (this file) — append a `## Status Log` section (or a `Status` column in this table) marking each `T014-NNN` task as `[x]` complete with the commit SHA. Mark every Phase I success criterion as PASS with evidence pointer (commit SHA or `Phase-I-evidence.md` reference).
- `specs/014-edge-function-llm-gateway/plan.md` — flip "Phase 2 (Tasks)" checkbox to `[x]`.
- `.specify/state` (if it exists) or equivalent — update spec-014 status to `complete`.

Acceptance:
- `git log --oneline` from the start of Phase A shows exactly 22 commits, one per `T014-NNN` task ID.
- `tasks.md` has all checkboxes ticked.
- `plan.md` Phase 2 checkbox ticked.
- A summary PR-description-style block added at the bottom of this file naming all SCs that passed and any deferred/follow-up items.

Constitution check: ✓ (bookkeeping; no principle implications).

Depends on: T014-021.

---

## Dependency graph (high-level)

```
A (T014-001) ──┬── A (T014-002 [P]) ──┐
               ├── A (T014-003) ──────┼── A (T014-004 [P])
               │                      │
               └──────────────────────┴─→ B (T014-005) → B (T014-006)
                                                            │
                                                            ↓
                                                          C (T014-007) → C (T014-008)
                                                                            │
                              ┌─────────────────────────────────────────────┤
                              ↓                                             ↓
                            D (T014-009) → D (T014-010) → D (T014-011)    E (T014-012)
                              │                                             │
                              └──────────────────┬──────────────────────────┘
                                                 ↓
                                               F (T014-013) → F (T014-014)
                                                 │
                                                 ↓
                                               G (T014-015)
                                                 │
                                                 ↓
              H (T014-016) → H (T014-017) ─┐  H (T014-018 [P])
                                           ↓        ↓
                                          H (T014-019)
                                                 │
                                                 ↓
                                               I (T014-020) → I (T014-021) → I (T014-022)
```

## Parallel-safe tasks `[P]`

- **T014-002** (env template — independent file from T014-003).
- **T014-004** (deploy.sh + README — independent file set from T014-003).
- **T014-018** (`AuthStateBinder` interface + impl + DI — independent of the URL-wiring chain T014-016/T014-017; can land in parallel with either).

All other tasks are strictly sequential per the dependency graph. Specifically: Phases B, C, D-then-E, F, G, the Android URL chain (016→017→019), and Phase I are sequential. Phase D's three tasks are sequential among themselves (handlers depend on `lib/anthropic.ts` from T014-009, cache harness depends on Haiku handlers from T014-010).

---

## Per-phase task breakdown

| Phase | Tasks | IDs |
|-------|-------|-----|
| A — Vercel bootstrap | 4 | T014-001 .. T014-004 |
| B — JWT auth | 2 | T014-005, T014-006 |
| C — Request router | 2 | T014-007, T014-008 |
| D — Anthropic handlers | 3 | T014-009, T014-010, T014-011 |
| E — OpenAI embed | 1 | T014-012 |
| F — Audit mirror | 2 | T014-013, T014-014 |
| G — Cost view migration | 1 | T014-015 |
| H — Android wiring | 4 | T014-016 .. T014-019 |
| I — E2E smoke + closeout | 3 | T014-020, T014-021, T014-022 |
| **Total** | **22** | |

## MVP suggestion

Per spec.md priorities, the **P1 MVP** is User Stories 1–3 satisfied end-to-end:

- Phases A → B → C → D (T014-009 only — Sonnet trio) → E → F → H → I.
- **Skip** for MVP: T014-010, T014-011 (US4 cache hit rate), T014-015 (US-orthogonal cost view).

That MVP slice is ~17 tasks and unblocks every dependent feature (specs 002 cluster suggestions, 003 actions, 004 Ask Orbit). US4 (cache hit rate) and Phase G (cost view) can land in a follow-up sub-PR — they are observability/cost-shaping, not user-facing capability gates.

In practice the user has asked for the full Day-2 sweep, so the recommended order is the full 22 tasks A → I sequentially, with the three `[P]` parallel-safes opportunistically interleaved.

---

## Format Validation

All tasks above conform to the required format:

- Every task starts with `- [ ] T014-NNN`.
- Every task has a `[Phase X]` tag (A–I).
- Every task has either a `[US-014-NNN]` or `[FOUND]` tag.
- `[P]` appears only on tasks with no ordering dependency on an incomplete task and an independent file set.
- Every task lists exact file paths it creates or modifies.
- Every task has 1–3 acceptance bullets and a Constitution check line.
- `Depends on:` lines added wherever ordering is not implicit.

Verified by manual scan 2026-04-29.

---

## Status Log

### 2026-04-29 — Phases A–H complete, Phase I pending user credentials

Local implementation lands all 19 Android-and-server-code tasks. Deployment + live E2E (T014-020/021) and final closeout (T014-022) require user-supplied secrets and Vercel project access — paused at the T014-019/T014-020 boundary per NFR-014-001 ("no `git push`, no live deploy without explicit user approval").

| Task | Status | Commit |
|------|--------|--------|
| T014-001 | [x] | `c50932a` |
| T014-002 | [x] | `857c422` |
| T014-003 | [x] | `4b0e22b` |
| T014-004 | [x] | `8130425` |
| T014-005 | [x] | `bc99e9e` |
| T014-006 | [x] | `d4a65c4` |
| T014-007 | [x] | `48a9eda` |
| T014-008 | [x] | `2da4671` |
| T014-009 | [x] | `1367fc3` |
| T014-010 | [x] | `21c59a3` |
| T014-011 | [x] | `9be8f2c` |
| T014-012 | [x] | `e6360a4` |
| T014-013 | [x] | `2f9df1d` |
| T014-014 | [x] | `91bdc24` |
| T014-015 | [x] | `98e708e` |
| T014-016 | [x] | `3dcd8e3` |
| T014-017 | [x] | `9581f1f` |
| T014-018 | [x] | `6f1bfc5` |
| T014-019 | [x] | `f9dc273` |
| T014-020 | [ ] | (deploy — needs user credentials) |
| T014-021 | [ ] | (live E2E — depends on T014-020) |
| T014-022 | [ ] | (closeout — depends on T014-021) |

### Verification matrix at handoff (2026-04-29)

- `npx tsc --noEmit` (Edge Function): PASS
- `npx vitest run` (Edge Function unit tests): PASS
- `./gradlew :app:compileDebugKotlin :app:compileDebugUnitTestKotlin --no-daemon`: PASS
- `./gradlew :app:testDebugUnitTest --tests "*LlmGatewayClientAuth*"`: PASS (5/5)
- `grep -r "gateway.example.invalid" app/src`: 0 matches (placeholder lives only in `app/build.gradle.kts` as fallback)
- `git status`: clean
- `git log --oneline 3534960..f9dc273` shows 19 atomic commits, one per `T014-NNN`

### Deviations recorded

1. **Supabase Kotlin SDK not yet wired** — `SupabaseAuthStateBinder` accepts a `() -> String?` lambda shim. Adding `gotrue-kt` and an `Application`-scoped `SupabaseClient` is a follow-up unblock; until then production calls return `Error(UNAUTHORIZED)` because `NoSessionAuthStateBinder` is the default. Suggested follow-up task ID: T014-019b or fold into next spec.
2. **No Hilt in repo** — task descriptions mentioning `@Binds` / `AuthModule.kt` were satisfied via the existing manual-DI pattern (constructor-injected defaults).
3. **`LlmGatewayClient` lives at `com.capsule.app.net.LlmGatewayClient`**, not the `com.capsule.app.ai.gateway.LlmGatewayClient` path quoted in tasks.md. Used the actual location.
4. **W1 spec drift fixed** during analyze pass — `requestId` added to `details_json` field listing in spec.md FR-014-013, US-3 Independent Test, and SC-014-005 (audit-row-contract.md and data-model.md were already correct).

### What user must provide to unblock T014-020

1. `vercel login` (or `VERCEL_TOKEN` env var) on this machine.
2. New Vercel project (suggested name: `orbit-llm-gateway`) and team/scope decision.
3. Five production env vars on Vercel: `OPENAI_API_KEY`, `ANTHROPIC_API_KEY`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_URL`, `SUPABASE_JWT_SECRET`.
4. Confirm or change Vercel deploy region (currently `iad1` in `vercel.json`).
5. Apply migration `00000003_cost_per_user_daily.sql` to prod Supabase (`omohxxhsjrqpkwfxbkau`) before audit rows start landing.
6. Decision on when Supabase Kotlin SDK lands in Android (deviation #1 unblocker).

### Operational confirmation

- `cloud-pivot` branch: 19 new commits since T014-001 (38 since spec/003 fork), local-only. Not pushed.
- No deploys executed. No external API calls made (all Anthropic/OpenAI clients mocked in tests).
