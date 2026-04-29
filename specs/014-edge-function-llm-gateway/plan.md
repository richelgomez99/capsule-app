# Implementation Plan: Edge Function LLM Gateway (Day 2)

**Branch**: `cloud-pivot` (no new feature branch — same deviation as spec 013, see §Tooling
Notes) | **Date**: 2026-04-29 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/014-edge-function-llm-gateway/spec.md`

## Summary

Spec 014 turns the placeholder `cloud.gateway.url` (`https://gateway.example.invalid/llm`)
shipped with spec 013 into a real, deployed Vercel Edge Function. The function authenticates
every request against a Supabase JWT, routes the six `LlmGatewayRequest` types to the
hardcoded providers per ADR-003 (Anthropic Sonnet for summarize/extract/header, Anthropic
Haiku with prompt caching for classify/scan, OpenAI direct for embed), mirrors a structured
audit row to `audit_log_entries` for every call (success or upstream failure), and exposes a
read-only `cost_per_user_daily` SQL view for Day-2 cost observability. The Android side gets
the gateway URL via `BuildConfig` from `local.properties` and pulls the JWT through a new
`AuthStateBinder` interface so `LlmGatewayClient` can stamp the `Authorization` header on
every call.

The technical approach is fully specified by the user — no scope discovery in this plan.
Phase 0 research validates the locked decisions; Phase 1 documents shapes; tasks land
sequentially under nine atomic phases A–I.

## Technical Context

**Language/Version**: TypeScript 5.x (Vercel Edge Function runtime — Web standard
`Request`/`Response`, ESM-only imports). Android side: Kotlin 2.x (matching Day-1; only
trivial wiring changes).
**Primary Dependencies**:
- Server: `@anthropic-ai/sdk`, `openai`, `@supabase/supabase-js`, `jose` (JWT verification),
  `zod` (request body validation). All edge-runtime compatible.
- Android (additive): no new libraries; `LlmGatewayClient` already uses OkHttp + kotlinx.serialization.
**Storage**: Supabase Postgres 15 — existing `audit_log_entries` table (no new columns); one
new migration `00000003_cost_per_user_daily.sql` for the read-side view.
**Testing**:
- Server: `vitest` (or `node:test` if vitest adds friction in edge mode) for unit tests;
  `vercel dev` + `curl` for local integration; instrumented Android E2E for Phase I.
- Android: existing `./gradlew testDebugUnitTest` + new `connectedDebugAndroidTest`
  smoke for E2E.
**Target Platform**: Production deployment on Vercel (region: `iad1` default, configurable).
Android target SDK unchanged from Day-1.
**Project Type**: Hybrid — server-side TypeScript Edge Function (single endpoint) + Android
client wiring + one Supabase migration. Source for the function lives co-located at
`supabase/functions/llm_gateway/index.ts` (NOT Supabase Edge Functions despite the path —
see [research.md §9](research.md#9-tooling-deviation--branch-name-audit-row-co-location)).
**Performance Goals** (per [spec.md §SC](spec.md)):
- SC-014-002: Anthropic prompt cache hit rate ≥ 80% over 100 sequential identical-system-prompt calls.
- SC-014-003: p50 ≤ 800ms / p95 ≤ 2500ms for `embed` round-trips from emulator over good network.
- SC-014-001: 99% of requests in steady state return success or structured `Error`.
**Constraints**:
- FR-014-006: non-streaming responses only.
- FR-014-012: structured logs MUST exclude prompt content, response content, vectors, JWT
  contents (Constitution Principle XIV — Bounded Observation, load-bearing).
- FR-014-021: secrets ONLY via Vercel env vars; never `local.properties`, never committed.
- NFR-014-001: do not push, do not deploy without explicit user approval.
- NFR-014-002: every task lands as one atomic commit on `cloud-pivot`.
**Scale/Scope**: Single deployed Vercel project. Single endpoint POST `/llm`. One Postgres
view. Six request types. Day-2 user count: single-digit (alpha gate). Audit table grows ~1
row per cloud call (~10–100 rows/user/day expected).

## Constitution Check

*Gate: Must pass before Phase 0 research. Re-checked after Phase 1 design.*

Constitution v3.2.0 governs this plan. Audit:

| Principle | Status | Justification |
|-----------|--------|---------------|
| **I — Composition over Code Generation** | PASS | Function composes Anthropic SDK + OpenAI SDK + Supabase client + jose; no code generation. Constitution v3.2.0 amendment (cloud LLM exemption) explicitly permits LLM use behind the Edge Function as a foundation/composition primitive when capability cannot be met locally. |
| **II — Spec-Driven Development** | PASS | Spec 014 exists, 24 FRs, clarifications all resolved. `/speckit.plan` is being run before implementation. |
| **III — Local-First, Privacy-First** | N/A→PASS | Spec 005 introduces the user-facing privacy gate. Spec 014 is infrastructure to make spec 005 possible; in isolation, no user-facing capability is enabled (placeholder URL was the gate). The function itself enforces FR-014-012 / FR-014-013 to prevent prompt leakage to operator surface. |
| **IV — Composable Agent Surface** | N/A | No agent surface change in spec 014. |
| **V — Stable Data Contracts** | PASS | Wire envelope shape (spec 013) unchanged. Audit row shape fully specified ([audit-row-contract.md](contracts/audit-row-contract.md)). View DDL versioned via migration. |
| **VI — Observable Boundaries** | PASS | One log line per request with bounded fields (FR-014-012). Audit row mirrors operator log. View provides cost observability. |
| **VII — Reversible Operations** | PASS | Audit insert is fire-and-forget; failure does not degrade user-facing response (FR-014-014). Migration is purely additive (CREATE VIEW). |
| **VIII — User-Owned Data** | PASS | Existing RLS on `audit_log_entries` preserved (SC-014-007). View inherits RLS. |
| **IX — Append-Only Mutation Trail** | PASS | `audit_log_entries` is append-only (`no_update_audit`, `no_delete_audit` policies from Day 1 untouched). Day-2 adds only new INSERTs. |
| **X — Cluster-First Curation** | N/A | No cluster surface change. |
| **XI — Forward-Only Migrations** | PASS | One new forward-only migration `00000003_cost_per_user_daily.sql` (CREATE OR REPLACE VIEW). |
| **XII — Bounded Inference Surface** | PASS | The function does not infer; it routes. The classifier `intent` string is forbidden from `details_json` per [audit-row-contract.md §3](contracts/audit-row-contract.md#3-bounded-observation-invariants-constitution-principle-xiv-fr-014-012). |
| **XIII — Reversible Capture** | N/A | No capture surface change. |
| **XIV — Bounded Observation Surface** | **LOAD-BEARING — PASS** | FR-014-012 closed enum of audit fields; `details_json` shape is fully specified with no nulls and no extra fields. Adding any field requires spec amendment. Operator log line shape is identical to audit row plus `userId`. Test SC-014-006 verifies no prompt content in logs. |
| **XV — Local-First Execution** | PASS w/ amendment | Cloud LLM use is permitted by the v3.2.0 amendment, conditional on (a) BYOK or quota enforcement (deferred to spec 005), (b) ADR-003 model pinning (this spec), (c) audit telemetry (this spec FR-014-013). All three conditions are present in the v3.2.0-compliant path. |

**Gate result**: PASS. No violations. No entries needed in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/014-edge-function-llm-gateway/
├── spec.md                                  # authoritative requirements (24 FRs, 7 SCs)
├── plan.md                                  # this file
├── research.md                              # Phase 0: validated decisions
├── data-model.md                            # Phase 1: TypeScript types, JWT claims, view DDL
├── quickstart.md                            # Phase 1: 5-minute walkthrough for next implementer
├── contracts/
│   ├── gateway-request-response.md          # server-side wire-format obligations
│   ├── auth-jwt-contract.md                 # JWT verification details
│   └── audit-row-contract.md                # details_json shape, fully specified
├── checklists/                              # (existing — pre-spec acceptance)
└── tasks.md                                 # Phase 2 output of /speckit.tasks (NOT created by /speckit.plan)
```

### Source Code (repository root)

New / modified files for spec 014. The function source is co-located with Supabase
migrations to keep contract and infrastructure one PR away from each other (deviation noted
in FR-014-019).

```text
supabase/
├── functions/
│   └── llm_gateway/                        # NEW — Vercel Edge Function (despite the supabase/ path)
│       ├── index.ts                        # entry point: POST /llm handler
│       ├── handlers/
│       │   ├── embed.ts                    # OpenAI direct
│       │   ├── summarize.ts                # Anthropic Sonnet
│       │   ├── extract_actions.ts          # Anthropic Sonnet
│       │   ├── classify_intent.ts          # Anthropic Haiku + prompt caching
│       │   ├── generate_day_header.ts      # Anthropic Sonnet
│       │   └── scan_sensitivity.ts         # Anthropic Haiku + prompt caching
│       ├── lib/
│       │   ├── auth.ts                     # JWT verification (jose)
│       │   ├── audit.ts                    # service-role insert into audit_log_entries
│       │   ├── anthropic.ts                # Anthropic client + prompt-cache helpers
│       │   ├── openai.ts                   # OpenAI client (embeddings only)
│       │   ├── schemas.ts                  # Zod request schemas
│       │   └── errors.ts                   # ErrorCode → response envelope mapping
│       ├── types.ts                        # TypeScript mirrors of Kotlin sealed classes
│       ├── package.json                    # @anthropic-ai/sdk, openai, @supabase/supabase-js, jose, zod
│       ├── tsconfig.json
│       ├── vercel.json                     # runtime: edge, region: iad1
│       ├── deploy.sh                       # one-liner: vercel deploy --prod
│       ├── README.md                       # operator runbook (env vars, smoke test)
│       └── .env.local.example              # template for local dev (gitignored .env.local)
└── migrations/
    └── 00000003_cost_per_user_daily.sql    # NEW — read-side observability view

app/
├── build.gradle.kts                        # MODIFIED — read cloud.gateway.url from local.properties → BuildConfig
└── src/main/java/com/capsule/app/
    ├── auth/
    │   └── AuthStateBinder.kt              # NEW — interface { suspend fun currentJwt(): String? }
    ├── di/
    │   └── AuthModule.kt                   # NEW or MODIFIED — Hilt binding for SupabaseAuthStateBinder
    ├── data/
    │   └── auth/
    │       └── SupabaseAuthStateBinder.kt  # NEW — production impl pulling from Supabase Auth
    └── ai/gateway/
        └── LlmGatewayClient.kt             # MODIFIED — inject AuthStateBinder, stamp Authorization header

local.properties                            # gitignored; new key: cloud.gateway.url=https://...
```

**Structure Decision**: Co-locate the Edge Function with Day-1 migrations under
`supabase/functions/llm_gateway/` despite deploying to Vercel. Rationale documented in
FR-014-019 and [research.md §9](research.md#9-tooling-deviation--branch-name-audit-row-co-location):
keeps the contract surface (Day-1 migrations + Day-2 function) one PR away. Migration to a
separate `vercel/` directory is a future spec.

## Phase Plan (Phases A–I)

Each phase is one or more atomic commits on `cloud-pivot`. Phases are sequential — do not
parallelize. `/speckit.tasks` will expand each into concrete tasks (~2–4 tasks per phase,
~18–22 total tasks expected).

### Phase A — Vercel project bootstrap
**Deliverables**:
- `supabase/functions/llm_gateway/{package.json, tsconfig.json, vercel.json, .env.local.example, deploy.sh, README.md}`.
- Vercel project linked locally (`vercel link`), all five env vars set in Production /
  Preview / Development per [auth-jwt-contract.md §4](contracts/auth-jwt-contract.md#4-required-vercel-environment-variables)
  and [research.md §8](research.md#8-secret-management-on-vercel).
- First "hello world" deploy returning HTTP 200 from `/llm` (placeholder body).
- `local.properties` updated with `cloud.gateway.url=https://<project>.vercel.app/llm` (dev
  machine only, gitignored).

**Acceptance**: `curl -i https://<deploy-url>/llm` returns HTTP 200 (no auth gate yet — Phase
B closes it). Vercel project URL is stable and ready for Phase B–F to land code under it.

### Phase B — Auth gate (`jose` + JWT verification)
**Deliverables**:
- `lib/auth.ts` implementing the verification steps from
  [auth-jwt-contract.md §2](contracts/auth-jwt-contract.md#2-jwt-verification--algorithm-and-steps).
- `lib/errors.ts` with the `ErrorCode` enum and the closed-enum UNAUTHORIZED message strings
  from [auth-jwt-contract.md §3](contracts/auth-jwt-contract.md#3-unauthorized-message-strings-closed-enum).
- Unit tests covering the six failure modes + happy path (auth-jwt-contract.md §7).
- `index.ts` skeleton that runs the auth gate on every request and rejects with HTTP 401 +
  `UNAUTHORIZED` body for any failure.

**Acceptance**: All six unit-test cases pass. `curl` without auth header → 401. `curl` with
forged JWT → 401. `curl` with a real Supabase-issued JWT → passes through to the next stage
(which is currently a stub).

### Phase C — Request router (Zod-validated discriminated union)
**Deliverables**:
- `lib/schemas.ts` with the root `LlmGatewayRequestSchema` and per-type payload schemas per
  [data-model.md §1](data-model.md#1-typescript-types--request-envelope) and
  [data-model.md §6](data-model.md#6-zod-schemas-validation-surface).
- `types.ts` with the discriminated-union TypeScript types.
- `index.ts` request flow: parse body → Zod validate → dispatch on `type` → call handler →
  build response envelope.
- Six placeholder handler stubs returning `Error(code: "INTERNAL", message: "not yet
  implemented")` so the dispatch table compiles end-to-end.
- Unit tests: malformed body → `INTERNAL`; each `type` dispatches to the right handler.

**Acceptance**: All six request types reach their (stub) handlers. Malformed body returns
`Error(INTERNAL)` HTTP 200 — no Zod issue echoed.

### Phase D — Anthropic handlers (Sonnet × 3, Haiku × 2 with prompt caching)
**Deliverables**:
- `lib/anthropic.ts` — Anthropic SDK client configured with `baseURL` = Vercel AI Gateway
  + gateway key. Helper `cachedSystemPrompt(prefix)` that returns the
  `cache_control: {type:"ephemeral"}` content-block shape.
- `handlers/{summarize,extract_actions,generate_day_header}.ts` — Sonnet, no caching.
  Per-handler timeouts (60s for summarize, 30s for the other two — see
  [gateway-request-response.md §3](contracts/gateway-request-response.md#3-model-routing-table-fr-014-002)).
- `handlers/{classify_intent,scan_sensitivity}.ts` — Haiku with cached system prefix +
  `prompt-caching-2024-07-31` beta header. Cache-hit detection per
  [research.md §2](research.md#2-anthropic-prompt-caching--mechanics-hit-rate-audit-surface).
- Error mapping per [gateway-request-response.md §6](contracts/gateway-request-response.md#6-upstream-error--errorcode-mapping-fr-014-004).
- For `extract_actions`: response is a JSON array of `ActionProposalJson`; validate via Zod
  before returning (malformed → `MALFORMED_RESPONSE`).
- Unit tests with mocked Anthropic responses for happy path, 5xx, timeout, malformed JSON.

**Acceptance**: All five Anthropic handlers return the right response envelope shape against
the production Anthropic API (live integration test gated by env-var presence). `cacheHit`
populates correctly on the second sequential ClassifyIntent call.

### Phase E — OpenAI embeddings handler
**Deliverables**:
- `lib/openai.ts` — OpenAI SDK client with direct API key (no AI Gateway).
- `handlers/embed.ts` — `text-embedding-3-small`, `dimensions: 1536`. Vector length
  validation (length !== 1536 → `MALFORMED_RESPONSE`).
- Unit tests with mocked OpenAI response.

**Acceptance**: Embed handler returns 1536-element vector for any input string. Direct
network failure surfaces as `PROVIDER_5XX` or `TIMEOUT`.

### Phase F — Audit insert (service-role client)
**Deliverables**:
- `lib/audit.ts` — Supabase service-role client (module-level singleton). Function
  `recordAuditRow(input)` that inserts the row per
  [audit-row-contract.md §1](contracts/audit-row-contract.md#1-insert-shape).
- `index.ts` integration: wrap each handler call in measurement (latencyMs), capture token
  usage, build `details_json`, call `recordAuditRow`. Wrap in try/catch — failure logs
  `audit_insert_failed=true` and returns the upstream response anyway (FR-014-014).
- Operator log line per [audit-row-contract.md §6](contracts/audit-row-contract.md#6-observability-log-line-separate-from-the-audit-row)
  emitted via `console.log` once per request.
- Unit tests: success row shape, error row shape, audit failure does not affect response
  body.

**Acceptance**: Every authenticated request results in exactly one row in `audit_log_entries`.
SC-014-005 verification SQL returns `well_formed = total`. SC-014-006 grep on Vercel logs
returns zero matches for prompt UUIDs.

### Phase G — `cost_per_user_daily` view migration
**Deliverables**:
- `supabase/migrations/00000003_cost_per_user_daily.sql` per
  [data-model.md §5](data-model.md#5-cost_per_user_daily-sql-view).
- Apply against production Supabase (`supabase db push` or psql against the production DB
  URL).
- Add a row to the existing multi-user RLS smoke (`supabase/tests/multi_user_smoke.sql`)
  asserting User B sees zero rows in `cost_per_user_daily` for User A.

**Acceptance**: View is queryable; RLS smoke still passes; sample query for the test user
returns sensible cost values.

### Phase H — Android wiring
**Deliverables**:
- `app/build.gradle.kts` — read `cloud.gateway.url` from `local.properties` and stamp
  `BuildConfig.CLOUD_GATEWAY_URL` (FR-014-016).
- `app/src/main/java/com/capsule/app/auth/AuthStateBinder.kt` — interface
  `interface AuthStateBinder { suspend fun currentJwt(): String? }`.
- `app/src/main/java/com/capsule/app/data/auth/SupabaseAuthStateBinder.kt` — production
  implementation pulling from the Supabase Auth Kotlin SDK session.
- `LlmGatewayClient.kt` — accept `AuthStateBinder` in the constructor; on each request,
  call `currentJwt()`. If null → return `LlmGatewayResponse.Error(code = UNAUTHORIZED, ...)`
  immediately without HTTP (FR-014-017). Otherwise stamp `Authorization: Bearer <jwt>`.
- `AuthModule.kt` (Hilt) — bind `AuthStateBinder` to `SupabaseAuthStateBinder`.
- Unit tests for `LlmGatewayClient`: null token → UNAUTHORIZED without network; valid token
  → header is stamped.

**Acceptance**: Existing `./gradlew testDebugUnitTest` is green. Manual: from a signed-out
emulator, every gateway call returns UNAUTHORIZED without making a network call.

### Phase I — End-to-end smoke
**Deliverables**:
- `app/src/androidTest/.../EdgeFunctionEndToEndTest.kt` — instrumented test against the
  deployed gateway. Six requests (one per type), assert non-Error responses, log latency.
- Manual SC-014-002 verification: 100 sequential ClassifyIntent calls, then run the SQL in
  [quickstart.md §7](quickstart.md#7-end-to-end-verification-sc-014-001--sc-014-007)
  → confirm cache hit rate ≥ 80%.
- Manual SC-014-003 verification: latency p50 / p95 from emulator.
- Manual SC-014-006 verification: grep Vercel logs for prompt UUIDs.
- Manual SC-014-007 verification: re-run `supabase/tests/multi_user_smoke.sql`.

**Acceptance**: All seven success criteria measurably pass. Gate to ship spec 005.

## Open Clarifications (carry to `/speckit.tasks`)

These four questions were identified during planning. They do NOT block `/speckit.plan` —
they need resolution before or during `/speckit.tasks` execution.

1. **Vercel project provisioning timing.** Phase A assumes the Vercel project is
   created during task execution (not pre-provisioned). Confirm this is fine, or pre-create
   so the URL is known earlier.
2. **Anthropic / Vercel AI Gateway key.** The spec hardcodes "Anthropic SDK via Vercel AI
   Gateway", which means `ANTHROPIC_API_KEY` is actually the Vercel AI Gateway key (not a
   raw Anthropic key). Confirm at task time. The function shape is identical either way (it
   is a `baseURL` swap).
3. **Single deploy script vs runbook README.** FR-014-020 allows either. Plan ships both —
   a one-liner `deploy.sh` + a README with env-var prerequisites. Confirm both are wanted.
4. **Cache TTL.** Plan uses Anthropic's default 5-min ephemeral cache. The newer beta
   supports 1-hour TTL. SC-014-002's 100-call test runs in <5 min so the default is
   sufficient. If real workloads have longer gaps, revisit.

## Tooling Notes

- **Branch deviation**: still on `cloud-pivot` (no feature branch). Setup script run with
  `SPECIFY_FEATURE=014-edge-function-llm-gateway`. Same workaround spec 013 used.
- **Agent context update**: `.specify/scripts/bash/update-agent-context.sh copilot` will be
  run after this plan lands to register the new TypeScript / Vercel surface area in the
  Copilot context file.
- **Pushing**: `git push` is held until user explicitly approves (NFR-014-001). All work
  lands locally on `cloud-pivot`.

## Complexity Tracking

No constitution gate violations identified. Table intentionally empty.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| _none_    |            |                                     |

## Phase Status

- [x] Phase 0 (Research) — see [research.md](research.md)
- [x] Phase 1 (Design & Contracts) — see [data-model.md](data-model.md), [quickstart.md](quickstart.md), [contracts/](contracts/)
- [ ] Phase 2 (Tasks) — produced by `/speckit.tasks`, NOT this command
