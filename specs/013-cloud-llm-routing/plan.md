# Implementation Plan: Cloud LLM Routing + Supabase Backbone (Day 1)

**Branch**: `cloud-pivot` (spec dir `013-cloud-llm-routing`) | **Date**: 2026-04-28 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification at `specs/013-cloud-llm-routing/spec.md`

> Branch name `cloud-pivot` does not match the speckit `NNN-name` convention. The plan was generated with `SPECIFY_FEATURE=013-cloud-llm-routing` to align tooling with the spec dir. This is intentional and tracked in [research.md](research.md) under "Tooling deviation".

## Summary

Land the keystone strangler-fig refactor that unblocks every Phase 11 Block 4+ task:

1. Introduce `LlmProviderRouter` over the existing `LlmProvider` interface so cloud-mode AI inference (default) routes through a new `CloudLlmProvider` while local-mode (`useLocalAi=true`) keeps `NanoLlmProvider`.
2. Extend the existing `:capture`↔`:net` AIDL channel with a single new method `callLlmGateway(LlmGatewayRequestParcel) → LlmGatewayResponseParcel`. No new cross-process surface, no socket from `:capture`.
3. Implement `LlmGatewayClient` in `:net` over the existing OkHttp dependency, addressing a placeholder Vercel AI Gateway URL (`https://gateway.example.invalid/llm`) with retry-once direct-provider fallback per ADR-003.
4. Provision the Supabase backbone: v1 schema mirroring Room (hybrid plaintext + reserved nullable `*_ct bytea` ciphertext columns), RLS policies on every table per ADR-007, Postgres CHECK constraint enforcing FR-032 cluster-citation invariant per ADR-002, and a multi-user smoke test as the alpha release gate.

The Edge Function, supabase-kt client, write-through sync, auth flow, and `ClusterDetectionWorker` cloud migration are all explicitly **out of scope** and deferred to follow-up specs.

## Technical Context

**Language/Version**: Kotlin 2.x (Android, target SDK aligned with existing app module); Postgres 15 + pgvector 0.9 (Supabase, server-side); SQL for migrations and tests.
**Primary Dependencies** (Android, all already on the classpath):
- `okhttp3` (existing in `:net`) — reused for `LlmGatewayClient`; no new HTTP client.
- `kotlinx.serialization` — `LlmGatewayRequest`/`LlmGatewayResponse` sealed classes serialized to JSON UTF-8 bytes for the parcel wire format and the AI Gateway envelope body.
- AIDL (`com.capsule.app.net.ipc`) — extends [INetworkGateway.aidl](../../app/src/main/aidl/com/capsule/app/net/ipc/INetworkGateway.aidl).
- `Parcelable` (Android framework) — manual `writeToParcel`/`CREATOR` mirroring `FetchResultParcel`.

**Server-side dependencies** (Supabase project, no Android impact):
- Postgres 15, pgvector 0.9, Supabase Auth (`auth.users`, `auth.uid()`).

**Storage**:
- Device: existing Room DB (SQLCipher) — unchanged. `RuntimeFlags` gains two new keys in the existing `SharedPreferences` store.
- Cloud (new, Day-1 schema only — no Android writes yet): Supabase Postgres v1 schema with hybrid plaintext + reserved nullable `*_ct bytea` columns per [`specs/contracts/envelope-content-encryption-contract.md`](../contracts/envelope-content-encryption-contract.md).

**Testing**:
- JVM unit tests under `app/src/test/` via `./gradlew testDebugUnitTest --tests '*Llm*' --tests '*Cluster*'`.
- AIDL parcel round-trip tests via `Parcel.obtain()` in JVM unit tests with Robolectric or via instrumented test if needed.
- Supabase: `psql -f supabase/tests/multi_user_smoke.sql` against the production project.

**Target Platform**:
- Android (existing `:default` / `:capture` / `:ml` / `:net` four-process app — no manifest changes).
- Supabase free tier project for Day 1.

**Project Type**: Mobile app (Android, Kotlin) + cloud data plane (SQL migrations + smoke test). No web or backend application code in this spec.

**Performance Goals** (per spec NFR-013-001):
- Capture-path AI calls (`classifyIntent`, `scanSensitivity`) p95 ≤ 2s, measured at the `CloudLlmProvider` boundary.
- Stage-path AI calls (`summarize`, `generateDayHeader`) p95 ≤ 3s.
- `LlmGatewayClient` timeouts: 30s default, 60s for `summarize`.

**Constraints**:
- `:capture` MUST NOT open any network socket (NFR-013-004; existing process-isolation invariant from constitution Principle VI).
- No Anthropic / OpenAI / Vercel AI Gateway API keys may ship in the Android binary (NFR-013-003) — provider auth lives server-side at the Edge Function (separate spec).
- All tenant isolation in cloud is enforced by RLS on `auth.uid() = user_id` (NFR-013-005); no application-layer `user_id` filter relied upon.
- No `git push` until user explicitly approves (NFR-013-008).
- Each of ~10 sub-tasks lands as its own commit on `cloud-pivot` (NFR-013-007).

**Scale/Scope** (Day 1):
- Android: ~10 new/modified files, ~600 lines of new Kotlin (router, cloud provider, gateway client, parcels, sealed classes, runtime-flag extensions, AIDL).
- Server: 3 SQL migrations, 1 SQL smoke test, ~9 tables. Free tier (small project, used by smoke test only on Day 1).
- Inference budget projection (NFR-013-002): ≤ $1/user/month at 100 captures/day with Haiku 4.5 + prompt caching for hot path, Sonnet 4.6 for stage path, embedding-3-small for embeddings. Telemetry validation deferred.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

The Orbit Constitution (`.specify/memory/constitution.md`, version 3.2.0, amended 2026-04-28 to ratify this spec) is the binding gate. Each principle is evaluated against this spec's design.

| Principle | Status | Notes |
|-----------|--------|-------|
| **I. Local-First Supremacy** | ✅ Pass (post-amendment v3.2.0, 2026-04-28) | Principle I was scoped on 2026-04-28 to permit AI-inference egress via `:net` as a structurally-bounded path, with `RuntimeFlags.useLocalAi` as a non-negotiable kill switch and `NanoLlmProvider` preserved permanently as the local-mode option. This spec satisfies all three structural invariants: (a) cloud-mode routes through `:net` via `callLlmGateway` AIDL; (b) local-mode resolves to `NanoLlmProvider` with no network egress (verifiable in airplane mode); (c) `LlmProviderRouter` is the single resolution point. |
| **II. Effortless Capture, Any Path** | ✅ Pass | Capture flow unchanged. No new friction. |
| **III. Intent Before Artifact** | ✅ Pass | Envelope shape unchanged; cloud schema mirrors Room columns 1:1. |
| **IV. Continuations Grow Captures** | ✅ Pass | Continuations remain background. The cloud round-trip happens off the foreground critical path (or within the 2s p95 budget when on it). |
| **V. Under-Deliver on Noise** | ✅ Pass | No notification surface change. |
| **VI. Privilege Separation By Design** | ✅ Pass (post-amendment v3.2.0) — **structurally preserved**. The new method `callLlmGateway` lives on the existing `:net` AIDL surface; `:capture` still has zero network permission. The `LlmGatewayClient` lives in `:net` only. NFR-013-004 makes this explicit and `lsof`/`netstat`-verifiable. The 2026-04-28 amendment to Principle VI confirms `:net` carries AI inference traffic in addition to URL hydration; the lint rule banning OkHttp/HTTP clients outside `:net` survives unchanged. |
| **VII. Context Beyond Content** | ✅ Pass | Context signals still computed on-device. Only the prompt and inference cross the boundary. |
| **VIII. Collect Only What You Use** | ✅ Pass | The ciphertext columns added in this spec are reserved (NULL on Day 1) — no data collected, just schema shape future-proofed for spec 006. Justified per Round 2 clarification: cheaper one-time wider schema than ALTER TABLE later. |
| **IX. User-Sovereign Cloud Escape Hatch (LLM)** | ✅ Pass (post-amendment v3.2.0), with two deferred-with-deadline items | The 2026-04-28 amendment to Principle IX permits an Orbit-operated managed proxy (Vercel AI Gateway + Supabase Auth JWT) as the default cloud-mode path. Day-1 spec 013 ships the `LlmProvider` abstraction + cloud routing skeleton. **Deferred-with-deadline (alpha-install gate)**: (1) BYOK provisioning UI + per-capability cloud opt-out toggles — absorbed by spec `005-cloud-boost-byok-llm`; (2) audit log entry per cloud LLM call (provider, model, capability, prompt digest, token count) — lands in the Edge Function spec. Both MUST land before the first external alpha install per the constitution amendment log. |
| **X. Sovereign Cloud Storage** | ✅ Pass for Day 1 (no application-layer cloud writes yet — only schema provisioning and smoke test). The non-negotiables (audit log never uploaded, consent ledger never uploaded, per-user DEK encryption) are reserved for spec 006 and the schema reserves the ciphertext columns. |
| **XI. Consent-Aware Prompt Assembly** | ⚠️ Deferred. Day 1 ships no prompt assembly path that crosses the `:agent` consent filter (because Day 1 has no real prompts — just placeholder gateway calls). The consent filter must be wired before the first real cloud LLM call ships in a downstream spec. Flagged in `tasks.md` (next step) as a precondition for the Edge Function spec. |
| **XII. Provenance Or It Didn't Happen** | ✅ Pass — no derived facts written by this spec. |
| **XIV. Bounded Observation** (FR-032 server-side) | ✅ Pass — enforced server-side via [`supabase-cluster-membership-check-contract.md`](contracts/supabase-cluster-membership-check-contract.md). |
| **XV. Latency Budgets** | ✅ Pass — capture <2s p95, stage <3s p95 codified in NFR-013-001. CloudLlmProvider 30s/60s timeouts cover the worst case; typical p50 hits the budget. |

**Gate verdict**: **PASS** (post-amendment v3.2.0, 2026-04-28). The constitution amendment ratifying this spec landed at `.specify/memory/constitution.md` v3.2.0 on 2026-04-28; Principles I, VI, and IX were updated to scope cloud LLM routing as a permitted process under structurally-bounded conditions (kill switch, process boundary, alpha-install gate for audit log + BYOK). Implementation may proceed to `/speckit.tasks`.

The two deferred-with-deadline items above (Principle IX BYOK + per-capability toggles + audit log; Principle XI consent filter) are listed in **Complexity Tracking** below as known scope deferrals with the alpha-install gate as their hard deadline.

## Project Structure

### Documentation (this feature)

```text
specs/013-cloud-llm-routing/
├── plan.md                                          # this file
├── spec.md
├── research.md                                      # Phase 0 output
├── data-model.md                                    # Phase 1 output
├── quickstart.md                                    # Phase 1 output
├── contracts/                                       # Phase 1 output
│   ├── llm-gateway-envelope-contract.md
│   ├── aidl-callllmgateway-contract.md
│   ├── supabase-rls-contract.md
│   └── supabase-cluster-membership-check-contract.md
└── tasks.md                                         # Phase 2 — generated by /speckit.tasks (NOT this command)
```

### Source Code (repository root)

```text
app/src/main/
├── aidl/com/capsule/app/net/ipc/
│   └── INetworkGateway.aidl                         # MODIFIED: add callLlmGateway
├── java/com/capsule/app/
│   ├── RuntimeFlags.kt                              # MODIFIED: add useLocalAi, clusterEmitEnabled
│   ├── ai/
│   │   ├── LlmProvider.kt                           # MODIFIED: doc-comment scope (network ban → local-mode only)
│   │   ├── NanoLlmProvider.kt                       # UNCHANGED (TODOs preserved)
│   │   ├── LlmProviderRouter.kt                     # NEW
│   │   └── CloudLlmProvider.kt                      # NEW
│   ├── ai/gateway/                                  # NEW package
│   │   ├── LlmGatewayRequest.kt                     # NEW (sealed, @Serializable)
│   │   └── LlmGatewayResponse.kt                    # NEW (sealed, @Serializable, Embed overrides equals/hashCode)
│   ├── net/
│   │   ├── NetworkGatewayImpl.kt                    # MODIFIED: add callLlmGateway handler
│   │   └── LlmGatewayClient.kt                      # NEW
│   ├── net/ipc/
│   │   ├── FetchResultParcel.kt                     # UNCHANGED
│   │   ├── LlmGatewayRequestParcel.kt               # NEW (Parcelable, JSON UTF-8 inside)
│   │   └── LlmGatewayResponseParcel.kt              # NEW (Parcelable, JSON UTF-8 inside)
│   └── cluster/
│       └── ClusterDetectionWorker.kt                # MODIFIED: add CLUSTER-LOCAL-PIN comment only

app/src/test/java/com/capsule/app/
├── ai/
│   ├── LlmProviderRouterTest.kt                     # NEW — covers SC-004 (flag flip)
│   └── CloudLlmProviderTest.kt                      # NEW — embed-null contract, error mapping
└── ai/gateway/
    └── LlmGatewayParcelRoundTripTest.kt             # NEW — Parcelable round-trip via Robolectric

supabase/                                            # NEW directory (repo root)
├── migrations/
│   ├── 00000000_initial_schema.sql                  # NEW — v1 schema, hybrid plaintext + *_ct
│   ├── 00000001_rls_policies.sql                    # NEW — auth.uid()=user_id on all CRUD
│   └── 00000002_cluster_membership_check.sql        # NEW — FR-032 enforcement
├── functions/                                       # NEW — empty in this spec (reserved)
└── tests/
    └── multi_user_smoke.sql                         # NEW — ADR-007 isolation proof
```

**Structure Decision**: Single Android app module + sibling `supabase/` directory at repo root. No new Gradle modules. No process-model change. The new Kotlin files slot into existing packages (`com.capsule.app.ai`, `com.capsule.app.net`, `com.capsule.app.net.ipc`); a new sub-package `com.capsule.app.ai.gateway` holds the wire-format sealed classes so they can be shared between `:capture` (Cloud provider) and `:net` (NetworkGatewayImpl handler) without circular dependencies.

## Phase 0 — Outline & Research

See [research.md](research.md). All NEEDS CLARIFICATION items from spec Round 1 + Round 2 are recorded as decisions there with rationale. Validated facts (model availability, pricing, doc dates, AIDL JSON-string parcel pattern, RFC-2606 placeholder hostname) are captured for the implementer so they don't have to re-research.

## Phase 1 — Design & Contracts

### Data model
See [data-model.md](data-model.md). Covers:
- Kotlin sealed `LlmGatewayRequest` / `LlmGatewayResponse` hierarchies (six request/response pairs + `Error`).
- AIDL parcels (`LlmGatewayRequestParcel`, `LlmGatewayResponseParcel`) and their JSON-string wire format.
- Supabase tables (9 tables) with full column lists, types, RLS policy shape, and reserved `*_ct bytea` ciphertext columns per the encryption contract.
- `RuntimeFlags` extensions (`useLocalAi`, `clusterEmitEnabled`).

### Contracts
Four contracts are split out under [contracts/](contracts/):

1. **[llm-gateway-envelope-contract.md](contracts/llm-gateway-envelope-contract.md)** — JSON wire format for the provider-agnostic envelope. Documents every `type` value, request payload schema, and response schema. Explicitly states that provider-specific shaping (Anthropic Messages, OpenAI Embeddings) is server-side only.
2. **[aidl-callllmgateway-contract.md](contracts/aidl-callllmgateway-contract.md)** — AIDL signature, parcel binary format, binder thread-safety rules, UID gate.
3. **[supabase-rls-contract.md](contracts/supabase-rls-contract.md)** — `auth.uid() = user_id` policy on all four CRUD ops on every table; multi-user smoke test acceptance criteria; ADR-007 reference.
4. **[supabase-cluster-membership-check-contract.md](contracts/supabase-cluster-membership-check-contract.md)** — Postgres CHECK/trigger enforcing FR-032 server-side; ADR-002 reference.

### Why four contracts (structural decision)
The original instruction allowed bundling the LLM gateway envelope and the AIDL contract into one file. They are split because:
- The envelope is the boundary between **Android `:net`** and the **Edge Function** (HTTP/JSON, server-side concern).
- The AIDL contract is the boundary between **`:capture`** and **`:net`** (Android-internal IPC, parcel binary format).

The two have different consumers, different review audiences (Edge Function team vs. Android team), and different change cadences (the envelope can evolve when models change; the AIDL parcel is binary-stable across releases). Splitting them reflects the actual system boundaries and is also what `tasks.md` will use for traceability.

### Quickstart
See [quickstart.md](quickstart.md) — 5-minute walkthrough for the next implementer.

### Agent context update
Run `.specify/scripts/bash/update-agent-context.sh copilot` after this plan lands. Adds the new technologies (kotlinx.serialization for AIDL parcel JSON, OkHttp for LlmGatewayClient, Supabase Postgres + pgvector + RLS) to the Copilot agent context file.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| Constitution Principle I/II amendment required | Cloud LLM routing is the keystone unblocking Phase 11 Block 4+ (cluster engine cloud migration, sync, Ask Orbit, Cloud Boost, Knowledge Graph, Agent — five downstream specs). Local-only Nano on Pixel 9 Pro alone cannot ship the alpha install timeline. | Keeping Principle I literal would require all six specs to ship Nano-only first, blocked behind Pixel 9 Pro arrival May 1 and AICore stability — pushing alpha by ≥ 2 months and forcing every downstream spec to have a "now do it again for cloud" follow-up. |
| Constitution Principle IX partial conformance (managed proxy before BYOK + audit + per-capability toggles) | Day 1 ships the abstraction (`LlmProvider` routing + AIDL channel). The audit-log entries and per-capability toggles require the auth flow (Day 2-3 spec) and the audit log writer extension (later spec) which do not exist on Day 1. | Folding BYOK + audit + toggles into Day 1 would require shipping `AuthSessionStore`, `AuthOnboardingActivity`, and audit-log writer extension this week, all of which already have follow-up specs scheduled. The atomic-commits-on-cloud-pivot constraint (NFR-013-007) would be violated. |
| Hybrid plaintext + reserved nullable `*_ct bytea` columns on Day 1 | Spec 006 will need ciphertext columns for every plaintext content column; pre-reserving them as nullable in the Day 1 migration costs ~20 extra column declarations and zero runtime cost (they stay NULL until spec 006 lands). | Adding ciphertext columns later via `ALTER TABLE` would require either a downtime window or zero-downtime migration tooling that doesn't exist for the Supabase project on Day 1. Round 2 clarification (Option C, Hybrid) chose this trade-off explicitly. |

---

## Output Summary

Generated artifacts:
- [plan.md](plan.md) (this file)
- [research.md](research.md)
- [data-model.md](data-model.md)
- [quickstart.md](quickstart.md)
- [contracts/llm-gateway-envelope-contract.md](contracts/llm-gateway-envelope-contract.md)
- [contracts/aidl-callllmgateway-contract.md](contracts/aidl-callllmgateway-contract.md)
- [contracts/supabase-rls-contract.md](contracts/supabase-rls-contract.md)
- [contracts/supabase-cluster-membership-check-contract.md](contracts/supabase-cluster-membership-check-contract.md)

Branch: `cloud-pivot` (HEAD-of-spec for `013-cloud-llm-routing`).

**Next step**: After ratifying the Principle I/II amendment via `/speckit.constitution`, run `/speckit.tasks` to generate `tasks.md`. Do NOT implement before that.
