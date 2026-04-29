# Feature Specification: Cloud LLM Routing + Supabase Backbone (Day 1)

**Feature Branch**: `cloud-pivot` (work for spec `013-cloud-llm-routing`)
**Created**: 2026-04-28
**Status**: Draft
**Governing document**: `.specify/memory/constitution.md`
**Input**: User description: "Cloud LLM Routing + Supabase Backbone Day 1 — keystone refactor that unblocks every Phase 11 Block 4+ task."

> **Relationship to prior specs.** This spec is the **execution unit for Day 1** of the cloud pivot described in
> `~/.gstack/projects/richelgomez99-capsule-app/orbit-pivot-plan-2026-04-28.md` and the design doc
> `~/.gstack/projects/richelgomez99-capsule-app/richelgomez-spec-003-orbit-actions-design-20260428-161116.md`. It introduces a
> **strangler-fig** abstraction (`LlmProvider` routing) so cloud-mode AI inference can land in
> Phase 11 Block 4 without ripping out `NanoLlmProvider`. It also stands up the **Supabase backbone**
> (project, schema, RLS policies, multi-user smoke test) that all subsequent specs (sync, auth,
> cluster engine cloud migration, write-through) will build on. Specs 004–008 (Ask Orbit, Cloud
> Boost, Cloud Storage, Knowledge Graph, Agent) and the v1 cluster-suggestion engine in spec 002
> all depend on the abstraction this spec delivers.
>
> **Constitutional alignment.** Principle II (Local by Default) is **amended in this spec** so that
> *local-mode* `LlmProvider` implementations MUST NOT touch the network, while *cloud-mode*
> implementations MUST route every call through the existing `:net` process via AIDL. Principle IX
> (LLM Sovereignty) is preserved end-to-end: all AI inference still flows through the
> `LlmProvider` interface; only the *binding* changes. Principle XV (latency budgets) is enforced
> in NFRs below.
>
> **ADR cross-references.**
> - **ADR-003** (Vercel AI Gateway + direct provider fallback): drives FR-013-007, FR-013-008, FR-013-014.
> - **ADR-006** (cluster engine cloud migration as gating work): drives FR-013-018, SC-005.
> - **ADR-007** (RLS + multi-user smoke test prereq): drives FR-013-024, FR-013-025, SC-008.

---

## Clarifications

### Session 2026-04-28

- Q: Cloud-mode default vs. local-mode default? → A: **Cloud is the default** (`useLocalAi = false`). Local mode is a kill switch for cloud rollback during incidents and a fallback for users on Nano-capable hardware (Pixel 9 Pro / S24-class) once Nano integration completes.
- Q: Real Vercel AI Gateway URL or placeholder for Day 1? → A: **Placeholder** (`https://gateway.example.invalid/llm`). Real URL lands when the Edge Function deploys (Day 4–7, separate spec).
- Q: Which provider models for which method? → A: Embed → `openai/text-embedding-3-small` (1536d). Summarize / ExtractActions / GenerateDayHeader → `anthropic/claude-sonnet-4-6`. ClassifyIntent / ScanSensitivity → `anthropic/claude-haiku-4-5` with prompt-cached prefix.
- Q: How does the Android client shape provider-specific JSON? → A: It does not. The Android client sends a **provider-agnostic envelope** `{type, payload}`; the Edge Function does provider-specific shaping. This keeps the mobile binary stable when models change.
- Q: Is multi-user RLS smoke test optional for Day 1 or mandatory? → A: **Mandatory before alpha install** per ADR-007. Cannot ship without it.
- Q: Asymmetric error contract on `LlmProvider`? → A: Preserved exactly. `embed()` returns `null` on any error; `summarize` and `extractActions` throw; the rest throw. `LlmProviderEmbedTest` enforces the embed-null contract for both implementations.

### Session 2026-04-28 (Round 2, post-/specify)

- Q: Cloud schema column shape — Day-1 plaintext-only (rename later when spec 006 lands), Day-1 ciphertext-only with `*_ct` bytea (block alpha until spec 006 ships DEK/KEK), or **hybrid** (plaintext columns mirroring Room **plus** reserved nullable `*_ct` ciphertext columns alongside)? → A: **Hybrid (Option C).** Rationale: future-proofs the schema with minimal Day-1 cost. When spec 006 (Orbit Cloud Storage) ships DEK/KEK provisioning + client-side AES-GCM, the migration to encrypted-at-rest only has to populate the ciphertext columns and drop the plaintext ones — never `ALTER TABLE` to add new columns. Slightly wider Day-1 schema in exchange for a smoother future migration. Day-1 writes plaintext only; ciphertext columns stay `NULL` until spec 006 lands. The ciphertext column set is the one defined in `specs/contracts/envelope-content-encryption-contract.md` (e.g. `body_ct`, `ocr_ct`, `transcript_ct`, `result_ct`, `object_ct`, …) typed as nullable `bytea`. This affects FR-013-023 and the Supabase-tables Key Entity below.
- Q: `ClusterDetectionWorker` migration scope for Day 1 — fold it into the FR-013-016 sweep (every `NanoLlmProvider()` site routes through `LlmProviderRouter`), or carve it out and pin it to local-mode until the Phase 11 Block 4 cluster-engine cloud migration lands? → A: **Option B — carve `ClusterDetectionWorker` out of the FR-013-016 migration.** Rationale: ADR-006 explicitly gates cluster-engine cloud migration to Phase 11 Block 4 (separate spec). Day-1 spec preserves the existing direct `NanoLlmProvider()` construction in `app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt` only, with a `// CLUSTER-LOCAL-PIN: migrated in Phase 11 Block 4` comment immediately above the constructor that references the future Phase 11 Block 4 spec by ID. This affects FR-013-016, SC-001, SC-005, and adds FR-013-028 (carve-out self-documenting).

**Round 2 (post-/specify) closed: 2 questions surfaced (schema shape, cluster worker), 2 questions resolved. Spec is ready for `/speckit.plan`.**

---

## User Scenarios & Testing *(mandatory)*

### User Story 1 — LlmProvider routes to cloud or local based on a runtime flag (Priority: P1)

As an Orbit engineer working on Phase 11 Block 4 (cluster engine cloud migration), I need a single
`LlmProvider` interface that I can resolve at runtime to either the existing on-device
`NanoLlmProvider` or a new `CloudLlmProvider` that proxies through the `:net` process. Today every
production call site directly constructs `NanoLlmProvider()`, which means cloud rollout would
require touching every call site individually. After this story ships, every production call site
goes through `LlmProviderRouter.create(context, networkGateway)` and the routing is controlled by
a single persisted runtime flag `RuntimeFlags.useLocalAi`. Cloud is the default; local is a kill
switch.

**Why this priority**: This is the keystone refactor. Every downstream Phase 11 Block 4+ task
(cluster cloud migration, sync, write-through, Ask Orbit) is blocked until this abstraction lands.
It must come first.

**Independent Test**: With `useLocalAi = false` (default), construct an `LlmProvider` via
`LlmProviderRouter` from a unit test with a fake `INetworkGateway`. Verify the returned instance
is a `CloudLlmProvider` and that calling each of the six interface methods produces an AIDL call
to `callLlmGateway` with the corresponding `LlmGatewayRequest` subtype. Flip `useLocalAi = true`,
re-resolve, and verify the returned instance is a `NanoLlmProvider`.

**Acceptance Scenarios**:

1. **Given** the app is running with `RuntimeFlags.useLocalAi = false` (default), **When** any
   production call site invokes `LlmProviderRouter.create(context, networkGateway)`, **Then** the
   router returns a `CloudLlmProvider` bound to the supplied `INetworkGateway`.
2. **Given** the app is running with `RuntimeFlags.useLocalAi = true` and `hasNanoCapableHardware()`
   returns `true`, **When** any production call site invokes
   `LlmProviderRouter.create(context, networkGateway)`, **Then** the router returns a
   `NanoLlmProvider`.
3. **Given** `RuntimeFlags.useLocalAi = true` but `hasNanoCapableHardware()` returns `false`,
   **When** the router is asked to create a provider, **Then** it falls back to `CloudLlmProvider`
   so users without Nano-capable hardware never get a stub-only experience.
4. **Given** `useLocalAi = false` and a non-null `networkGateway` is required, **When** `null` is
   passed for the gateway, **Then** the router throws an `IllegalStateException` with a message
   that names the missing dependency.
5. **Given** an existing call site previously wrote `NanoLlmProvider()`, **When** the migration is
   complete, **Then** `grep -rn "NanoLlmProvider()" app/src/main/` returns zero results outside of
   `NanoLlmProvider.kt` itself and the `LlmProviderRouter` local-branch construction site.

---

### User Story 2 — Cloud-mode AI inference rides the existing `:net` AIDL channel (Priority: P1)

As Orbit's process model owner, I need cloud LLM calls to ride the existing `:capture` → `:net`
AIDL channel rather than introduce a second cross-process surface. Today `:net` already implements
`fetchPublicUrl` over OkHttp; I need a sibling method `callLlmGateway` that takes a parcelled
`LlmGatewayRequest`, dispatches it to the Vercel AI Gateway (with direct-provider fallback on 5xx),
and returns a parcelled `LlmGatewayResponse`. The `:capture` process must never open an outbound
socket.

**Why this priority**: Process isolation between capture and network is a core Orbit security
property (per existing constitution). Adding a second egress point would weaken it. Reusing the
AIDL channel preserves the property.

**Independent Test**: From an instrumented test inside the `:capture` process, build an
`LlmGatewayRequest.Embed("hello world", requestId = UUID.random())`, call the AIDL method through
the existing service binding pattern (mirroring `UrlHydrateWorker.kt`), and verify a non-null
`LlmGatewayResponse.Embed` parcel comes back. Verify via `lsof`/`netstat` (or a no-network mock at
the OkHttp level) that the `:capture` process did not open a socket.

**Acceptance Scenarios**:

1. **Given** the AIDL surface is regenerated, **When** the project builds, **Then**
   `INetworkGateway.aidl` exposes `LlmGatewayResponseParcel callLlmGateway(in LlmGatewayRequestParcel request)`
   and the generated stubs compile cleanly into both `:capture` and `:net`.
2. **Given** a `CloudLlmProvider` running in `:capture`, **When** any of its six interface methods
   is called, **Then** the only IPC the call performs is a single `callLlmGateway` round-trip; no
   direct OkHttp / Retrofit / HttpURLConnection usage exists in `:capture`.
3. **Given** the Vercel AI Gateway returns a 5xx response, **When** `LlmGatewayClient` handles the
   error, **Then** it retries the request **once** by addressing the corresponding direct provider
   endpoint (Anthropic Messages or OpenAI Embeddings) before surfacing failure to the AIDL caller
   (per ADR-003).
4. **Given** an authenticated user session exists, **When** `LlmGatewayClient` builds the outbound
   request, **Then** it reads the bearer token from `AuthSessionStore.getCurrentToken()` if
   available; if the session store does not yet exist (Day 1 reality), the client proceeds with no
   `Authorization` header rather than crashing.

---

### User Story 3 — Multi-tenant Supabase backbone is provisioned and proven (Priority: P1)

As Orbit's owner of the cloud data plane, I need a Supabase project with the v1 schema mirrored
from Room, Row Level Security policies on every table, a Postgres CHECK constraint that enforces
spec 002's FR-032 cluster-citation invariant server-side, and a multi-user smoke test that proves
user B cannot read or mutate user A's data. None of the application-layer wiring (supabase-kt
client, write-through, sync) ships in this spec — only the backbone and proof of isolation.

**Why this priority**: ADR-007 makes the multi-user smoke test a hard gate before any alpha
install. Without RLS proven on a real project, the alpha cannot ship. With it, every subsequent
spec (sync, auth, write-through) plugs in without re-proving the data model.

**Independent Test**: From the Supabase SQL editor, run `supabase/tests/multi_user_smoke.sql`. The
script creates two `auth.users` rows, inserts envelopes, clusters, and action proposals owned by
each user, then attempts cross-user `SELECT`/`UPDATE`/`DELETE` from a client session set to user B
on user A's rows. The script MUST output a single `PASS` line and zero rows visible across the
boundary in either direction.

**Acceptance Scenarios**:

1. **Given** a fresh Supabase project, **When** the migrations in `supabase/migrations/` are
   applied in order (`00000000`, `00000001`, `00000002`), **Then** the database contains the v1
   schema with `user_id uuid references auth.users(id)` on every table, RLS policies enabled and
   matching `auth.uid() = user_id` on every CRUD operation, and a CHECK constraint that prevents
   inserting a `cluster_summary` whose cited envelope IDs are not members of the cited cluster.
2. **Given** users A and B both authenticated, **When** user B issues `SELECT * FROM envelopes`,
   **Then** the result contains only user B's rows; user A's rows are invisible.
3. **Given** users A and B both authenticated, **When** user B attempts to `UPDATE` or `DELETE` a
   row owned by user A by primary key, **Then** the operation affects zero rows and returns no
   error that leaks the existence of the row.
4. **Given** pgvector is enabled, **When** the migration creates `embedding vector(1536)` columns
   on `clusters` and `cluster_members`, **Then** the columns exist with the correct dimension and
   accept inserts of `text-embedding-3-small`-shaped vectors.
5. **Given** the multi-user smoke test, **When** it runs end-to-end, **Then** it prints `PASS`,
   exits with status 0, and is suitable to wire into the alpha-release gate.

---

### Edge Cases

- **Network unavailable in cloud mode.** `LlmGatewayClient` MUST surface a typed
  `LlmGatewayResponse.Error(code = "NETWORK_UNAVAILABLE", message)` rather than throw an
  `IOException` across the AIDL boundary. `CloudLlmProvider` then maps that to the appropriate
  per-method error contract (`embed()` → `null`; `summarize`/`extractActions` → throw; rest → throw).
- **`embed()` failure parity.** Both `NanoLlmProvider.embed` and `CloudLlmProvider.embed` MUST
  return `null` on any error and when `forceNanoUnavailable` is set; the existing
  `LlmProviderEmbedTest` is extended (not replaced) to cover the cloud impl.
- **Missing auth token at Day 1.** `AuthSessionStore` does not exist yet; `getCurrentToken()` is a
  stub that returns `null`. `LlmGatewayClient` MUST proceed with no `Authorization` header rather
  than blocking the entire AI surface on auth (which lands Day 2–3).
- **`useLocalAi = true` on a phone without Nano hardware.** The router MUST fall back to
  `CloudLlmProvider` so the user never gets a stub-only experience; the kill-switch is for cloud
  outages, not for forcing Nano on devices that cannot run it.
- **AIDL schema drift between `:capture` and `:net`.** The two parcel classes
  (`LlmGatewayRequestParcel`, `LlmGatewayResponseParcel`) MUST live under a single source of truth
  in `app/src/main/java/com/capsule/app/net/ipc/` (matching the AIDL package) so both processes
  link the same Kotlin types. JSON wire format uses kotlinx.serialization for forward compatibility.
- **`Embed` response equality.** `LlmGatewayResponse.Embed` carries a `FloatArray`, which has
  reference-equality `equals` by default. The data class MUST override `equals`/`hashCode` to use
  `contentEquals`/`contentHashCode` so test assertions and downstream `Set`/`Map` usage work.
- **5xx storm at the Gateway.** Retry-once policy applies *per request*, not per session; there is
  no exponential backoff at this layer (callers can re-issue). Two consecutive 5xx (Gateway then
  direct provider) surface a single typed error to the AIDL caller.
- **Cluster engine still on local label.** The v1 cluster engine continues to write
  `clusterModelLabelLock = NanoLlmProvider.MODEL_LABEL` until Phase 11 Block 4 cuts it over to
  cloud. This spec MUST NOT change the cluster engine's model label; only the routing for AI
  inference changes.
- **RLS bypass via SQL functions.** Any `SECURITY DEFINER` function added later MUST explicitly
  re-check `auth.uid()`; this spec adds none, but the migration files include a comment block
  warning future migrations.

---

## Requirements *(mandatory)*

### Functional Requirements

#### Morning block — `LlmProvider` abstraction layer

- **FR-013-001 (interface doc-comment relaxation)**: System MUST update the doc comments on
  `LlmProvider.kt` so the "MUST NOT touch the network" constraint is scoped to local-mode
  implementations only, and the interface contract permits cloud-mode implementations that route
  through `:net`. The interface signature and method count MUST NOT change.
- **FR-013-002 (RuntimeFlags extension)**: System MUST extend the existing `RuntimeFlags.kt` at
  `app/src/main/java/com/capsule/app/RuntimeFlags.kt` (package `com.capsule.app`) with two new
  persisted boolean flags: `useLocalAi` (default `false`) and `clusterEmitEnabled` (default `true`).
  The existing `clusterModelLabelLock` field MUST NOT be removed or have its Day-1 default changed
  (it stays pinned to `NanoLlmProvider.MODEL_LABEL` until Phase 11 Block 4 cuts the cluster engine
  over to cloud). Both new flags MUST persist via the same `SharedPreferences` mechanism the
  existing flag uses.
- **FR-013-003 (`LlmGatewayRequest` sealed hierarchy)**: System MUST define a
  `kotlinx.serialization.Serializable` sealed class `LlmGatewayRequest` with one subtype per
  `LlmProvider` method: `Embed`, `Summarize`, `ExtractActions`, `ClassifyIntent`, `GenerateDayHeader`,
  `ScanSensitivity`. Every subtype MUST carry a `requestId: String` (UUIDv4 string) for tracing and
  the method-specific payload fields needed by the corresponding `LlmProvider` method.
- **FR-013-004 (`LlmGatewayResponse` sealed hierarchy)**: System MUST define a
  `kotlinx.serialization.Serializable` sealed class `LlmGatewayResponse` with one success subtype
  per request type plus an `Error(code: String, message: String)` variant. The `Embed` success
  subtype carries `FloatArray` and MUST override `equals`/`hashCode` using `contentEquals` /
  `contentHashCode`.
- **FR-013-005 (AIDL surface extension)**: System MUST extend the existing
  `app/src/main/aidl/com/capsule/app/net/ipc/INetworkGateway.aidl` (package `com.capsule.app.net.ipc`)
  with `LlmGatewayResponseParcel callLlmGateway(in LlmGatewayRequestParcel request)`. The two
  parcelable wrappers MUST live in `app/src/main/java/com/capsule/app/net/ipc/` and use
  kotlinx.serialization JSON for the wire format inside the parcel. Existing AIDL methods MUST NOT
  change.
- **FR-013-006 (`LlmGatewayClient`)**: System MUST add a new `LlmGatewayClient.kt` in the `:net`
  package that uses the existing OkHttp dependency (no new HTTP client introduced). It MUST route
  per-request-type to the correct model:
  - `Embed` → `openai/text-embedding-3-small`
  - `Summarize`, `ExtractActions`, `GenerateDayHeader` → `anthropic/claude-sonnet-4-6`
  - `ClassifyIntent`, `ScanSensitivity` → `anthropic/claude-haiku-4-5` (with the prompt-cached
    prefix pattern from the tech-stack research doc).
- **FR-013-007 (provider-agnostic envelope)**: `LlmGatewayClient` MUST send a provider-agnostic
  JSON body of the form `{ "type": "embed" | "summarize" | "extract_actions" | "classify_intent"
  | "generate_day_header" | "scan_sensitivity", "payload": { ... } }`. The Android client MUST NOT
  contain provider-specific request shaping; that responsibility lives in the Edge Function which
  is **out of scope** for this spec.
- **FR-013-008 (Gateway URL + fallback per ADR-003)**: `LlmGatewayClient` MUST address the
  placeholder URL `https://gateway.example.invalid/llm` for Day 1. On 5xx response from the
  Gateway, the client MUST retry **once** against the corresponding direct provider endpoint
  (Anthropic Messages or OpenAI Embeddings, placeholder URLs acceptable for Day 1). Retry-once is
  per-request, not per-session.
- **FR-013-009 (auth header)**: `LlmGatewayClient` MUST attempt to read a bearer token from
  `AuthSessionStore.getCurrentToken()`. If the session store does not yet exist or returns null,
  the client MUST proceed without an `Authorization` header rather than fail.
- **FR-013-010 (timeouts)**: `LlmGatewayClient` MUST apply a 30-second default request timeout for
  all request types except `Summarize`, which uses 60 seconds.
- **FR-013-011 (NetworkGatewayImpl extension)**: System MUST extend the existing
  `NetworkGatewayImpl.kt` with a `callLlmGateway` AIDL handler that parses the incoming parcel into
  `LlmGatewayRequest`, delegates to `LlmGatewayClient`, and wraps the response into
  `LlmGatewayResponseParcel`. Existing handlers (`fetchPublicUrl`) MUST NOT change. Binder threads
  may block on IO inside the handler; the handler MUST NOT take any locks across the network call.
- **FR-013-012 (`CloudLlmProvider`)**: System MUST add a new `CloudLlmProvider.kt` that implements
  `LlmProvider`. Its constructor MUST take `INetworkGateway` (binding pattern mirrors
  `UrlHydrateWorker.kt`). Each interface method MUST: build the corresponding
  `LlmGatewayRequest`, wrap it in the parcel, call AIDL, unwrap the response, and return.
- **FR-013-013 (asymmetric error contract preserved)**: `CloudLlmProvider` MUST preserve the
  existing `LlmProvider` error contract: `embed()` returns `null` on any error and on
  `forceNanoUnavailable`; `summarize` and `extractActions` throw on error; the remaining methods
  throw on error.
- **FR-013-014 (`LlmProviderRouter`)**: System MUST add a new `LlmProviderRouter.kt` exposing
  `object LlmProviderRouter { fun create(context: Context, networkGateway: INetworkGateway?): LlmProvider }`.
  Resolution rule: if `RuntimeFlags.useLocalAi == true` AND `hasNanoCapableHardware() == true`,
  return `NanoLlmProvider()`; otherwise return `CloudLlmProvider(checkNotNull(networkGateway))`.
- **FR-013-015 (hardware check stub)**: `hasNanoCapableHardware()` MUST return `false`
  unconditionally in v1. Real Pixel 9 Pro / Galaxy S24 detection is deferred to a later spec; for
  Day 1 this means the router defaults to cloud everywhere.
- **FR-013-016 (call-site migration)**: System MUST replace every direct `NanoLlmProvider()`
  construction in production code (everything under `app/src/main/`) with
  `LlmProviderRouter.create(context, networkGateway)`, **except**
  `app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt`, which remains pinned to
  local-mode via direct `NanoLlmProvider()` construction with a
  `// CLUSTER-LOCAL-PIN: migrated in Phase 11 Block 4` comment immediately above the constructor
  (per ADR-006 and the Round 2 clarification). Migration of this file is owned by the Phase 11
  Block 4 spec, not Day 1. Test fakes that construct `NanoLlmProvider()` directly under
  `app/src/test/` and `app/src/androidTest/` MAY remain unchanged.
- **FR-013-017 (Nano untouched)**: System MUST NOT modify any method body in `NanoLlmProvider.kt`.
  Every existing `TODO("AICore integration — US2")` stays as-is. The Nano provider remains the
  local-mode option once US2 (AICore integration) lands in a separate spec.
- **FR-013-018 (cluster engine label preservation per ADR-006)**: System MUST NOT change
  `clusterModelLabelLock`'s default (still `NanoLlmProvider.MODEL_LABEL`) and MUST NOT modify
  `ClusterDetectionWorker`'s usage of `LlmProvider`. Cluster engine cloud migration is gated work
  and lives in a later spec; this spec only delivers the abstraction it will consume.
- **FR-013-019 (kill-switch surface)**: `clusterEmitEnabled` (default `true`) MUST be readable by
  `ClusterDetectionWorker` so cluster card surfacing can be disabled at runtime in case of a
  cloud-summarisation regression. This spec adds the flag; the consuming worker logic lands later.
- **FR-013-028 (cluster worker carve-out is self-documenting)**: The source file
  `app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt` MUST contain a single-line
  comment exactly matching `// CLUSTER-LOCAL-PIN: migrated in Phase 11 Block 4` immediately above
  the `NanoLlmProvider()` construction call. The comment is the durable signal that this
  call-site is intentionally exempt from the FR-013-016 sweep and is owned by the Phase 11 Block 4
  spec. The Phase 11 Block 4 spec's `tasks.md` MUST include a task that removes this comment when
  it migrates the constructor to `LlmProviderRouter.create(...)`. Verifiable by
  `grep -n "CLUSTER-LOCAL-PIN" app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt`
  returning exactly one line on Day 1 and zero lines after Phase 11 Block 4 lands.
- **FR-013-020 (smoke compile + tests)**: After all the above land, the command
  `./gradlew compileDebugKotlin compileDebugUnitTestKotlin testDebugUnitTest --tests '*Llm*' --tests '*Cluster*'`
  MUST exit with status 0 and zero failed tests.

#### Evening block — Supabase backbone

- **FR-013-021 (Supabase project provisioning)**: A Supabase project MUST be provisioned (free
  tier acceptable for Day 1; upgrade to Pro deferred until cost telemetry warrants it). Project
  reference, anon key, service role key, and database URL MUST be recorded outside the repo
  (1Password / shared secrets vault) and not committed.
- **FR-013-022 (`supabase/` directory)**: System MUST create a `supabase/` directory at repo root
  with subdirectories `migrations/`, `functions/`, and `tests/`. The `functions/` directory is
  reserved for the Edge Function (separate spec) and ships empty in this spec.
- **FR-013-023 (initial schema migration)**: `supabase/migrations/00000000_initial_schema.sql`
  MUST mirror the v1 Room schema for: `envelopes`, `continuations`, `continuation_results`,
  `clusters`, `cluster_members`, `action_proposals`, `action_executions`,
  `audit_log_entries`, and `user_profiles` (per-user preferences, declared focus topics, work
  hours, energy windows; out-of-scope for population by Day 1 — the table exists so spec 014's
  first-run onboarding flow has somewhere to write; `user_profiles` does NOT carry `*_ct`
  ciphertext columns because it stores no encrypted body content). Every table MUST include
  `user_id uuid not null references auth.users(id) on delete cascade`. The `pgvector` extension
  MUST be enabled and `clusters.embedding` and `cluster_members.embedding` MUST be `vector(1536)`
  (matching `text-embedding-3-small`'s dimensionality).

  **Common columns + audit-log exception.** Every table carries the common columns `id`,
  `user_id`, `created_at`, `updated_at`. Exception: `audit_log_entries` is append-only
  (Constitution Principle XII) and MUST NOT include `updated_at`. Its only timestamp is
  `created_at`.

  **Hybrid plaintext + reserved-ciphertext column shape (per Round 2 clarification).** Each
  table MUST mirror the Room schema's user-content columns as **plaintext** columns AND, in the
  same migration, add the corresponding **nullable `*_ct bytea` ciphertext columns** named per
  `specs/contracts/envelope-content-encryption-contract.md`. Concretely:
  - `envelopes`: plaintext `body`, `ocr_text`, `transcript`, `media_ref` (and any other
    Room-mirrored content columns) AND nullable `body_ct bytea`, `ocr_ct bytea`,
    `transcript_ct bytea`, `media_ref_ct bytea`.
  - `continuation_results`: plaintext `result_json` (or equivalent Room column) AND nullable
    `result_ct bytea`.
  - `clusters` / `cluster_members`: plaintext `summary` / `member_excerpt` (or equivalent Room
    columns) AND nullable `summary_ct bytea` / `excerpt_ct bytea` where the contract requires
    them.
  - `action_proposals` / `action_executions`: plaintext content columns from Room AND nullable
    `payload_ct bytea` / `result_ct bytea` per the contract.
  - `audit_log_entries`: plaintext `event_type`, `actor`, `created_at`, etc., per Room; no
    ciphertext column unless the contract specifies one.

  Day-1 application writes go into the plaintext columns only; ciphertext columns stay `NULL`
  until spec 006 (Orbit Cloud Storage) ships DEK/KEK provisioning and client-side AES-GCM. This
  shape is explicitly chosen so spec 006's migration can populate `*_ct` and drop plaintext —
  never `ALTER TABLE` to add ciphertext columns. RLS policies (FR-013-024) MUST cover both
  plaintext and ciphertext columns since both live on the same row. The `embedding` columns on
  `clusters` and `cluster_members` remain plaintext (vectors are one-way projections per the
  encryption contract).
- **FR-013-024 (RLS policies per ADR-007)**: `supabase/migrations/00000001_rls_policies.sql` MUST
  enable RLS on every table from FR-013-023 and define policies that gate `SELECT`, `INSERT`,
  `UPDATE`, and `DELETE` on `auth.uid() = user_id`. No table may be left with RLS disabled.
- **FR-013-025 (cluster-membership CHECK constraint per ADR-002 / FR-032)**:
  `supabase/migrations/00000002_cluster_membership_check.sql` MUST add a Postgres CHECK
  constraint (or trigger if a CHECK is structurally insufficient) that enforces server-side: a
  `cluster_summary`'s cited envelope IDs MUST all be members of the cited cluster. This is the
  cloud-side enforcement of spec 002's FR-032 invariant.
- **FR-013-026 (multi-user smoke test)**: `supabase/tests/multi_user_smoke.sql` MUST script the
  ADR-007 isolation proof:
  1. Create users A and B via `auth.admin.createUser`.
  2. As user A, insert envelopes, a cluster with members, and an action proposal.
  3. As user B, do the same.
  4. As user B, attempt `SELECT * FROM envelopes` and verify only B's rows return.
  5. As user B, attempt `UPDATE` and `DELETE` against user A's row primary keys; verify zero rows
     affected.
  6. Print `PASS` and exit 0 on success; `FAIL <reason>` and non-zero on any failure.
- **FR-013-027 (alpha gate)**: The multi-user smoke test MUST be executable as a one-command
  invocation (`psql ... -f supabase/tests/multi_user_smoke.sql`) and MUST be wired into the
  alpha-release gate before the first external install. No alpha install ships until this passes
  on the production Supabase project.

#### Out of scope (explicitly deferred)

- Supabase Edge Function `llm-gateway/index.ts` (Day 4–7, separate spec).
- supabase-kt Android client integration (`SupabaseClient.kt`, Day 2–3 spec).
- WriteThroughSync (Day 2–3 spec).
- Auth flow: `SignInWithGoogle`, `AuthSessionStore`, `AuthOnboardingActivity` (Day 2–3 spec).
- `ClusterDetectionWorker` rewrite to consume `LlmProviderRouter` (Phase 11 Block 4 spec).
- `ClusterSummariser` (Phase 11 Block 6 spec).
- First-run conversational onboarding, calendar integration, web app v1.1, real Vercel AI Gateway
  URL, real Anthropic / OpenAI provider integration (skeleton/stub responses suffice for Day 1).

### Non-Functional Requirements

- **NFR-013-001 (latency budget per Principle XV)**: Capture-path AI calls (`classifyIntent`,
  `scanSensitivity`) routed through cloud MUST complete at p95 ≤ 2s under normal network conditions.
  Stage-path AI calls (`summarize`, `generateDayHeader`) MUST complete at p95 ≤ 3s. These budgets
  are measured at the `CloudLlmProvider` boundary (round-trip through AIDL + Gateway).
- **NFR-013-002 (cost ceiling per ADR-003)**: Day 1 deployment MUST stay within the projected
  $1 / user / month inference budget at 100 captures/day/user. Telemetry to validate this lands in
  a later spec; this spec encodes the assumption and the model selection that makes it achievable
  (Haiku 4.5 with prompt cache for hot-path classification, Sonnet 4.6 only for stage-path
  summarisation, embedding-3-small for embeddings).
- **NFR-013-003 (security: zero-trust between mobile and provider keys)**: The Android binary MUST
  NOT contain Anthropic, OpenAI, or Vercel AI Gateway API keys. All provider authentication
  happens server-side at the Edge Function (separate spec). The mobile client only carries a
  Supabase user JWT.
- **NFR-013-004 (security: process isolation preserved)**: `:capture` MUST NOT open any network
  socket as a result of this spec. All cloud calls go through `:net` via AIDL. Verifiable by
  `lsof`/`netstat` on a running build.
- **NFR-013-005 (security: RLS is the only authorisation mechanism)**: All tenant isolation in the
  cloud data plane MUST be enforced by RLS on Supabase Postgres. No application-layer filter on
  `user_id` may be relied upon as the sole isolation mechanism.
- **NFR-013-006 (build hygiene)**: Existing tests under `app/src/test/` and `app/src/androidTest/`
  MUST remain green after the refactor. New tests for `LlmProviderRouter` and `CloudLlmProvider`
  are tracked in the planning phase.
- **NFR-013-007 (atomic commits)**: Each of the ~10 sub-tasks identified in the plan MUST land as
  its own commit on the `cloud-pivot` branch. No squash before review.
- **NFR-013-008 (no push)**: All work for this spec MUST remain local-only until the user
  explicitly approves a push.

### Key Entities

- **`LlmGatewayRequest` (sealed)**: Provider-agnostic request envelope crossing the AIDL boundary.
  Six subtypes mirroring `LlmProvider`'s six methods. Each carries `requestId: String` for tracing.
- **`LlmGatewayResponse` (sealed)**: Provider-agnostic response envelope. Six success subtypes plus
  `Error(code, message)`. `Embed` overrides equality on its `FloatArray`.
- **`LlmGatewayRequestParcel` / `LlmGatewayResponseParcel`**: Android `Parcelable` wrappers around
  the sealed types, using kotlinx.serialization JSON inside the parcel for forward compatibility.
- **`RuntimeFlags` (extended)**: Existing object at `com.capsule.app.RuntimeFlags`. Adds
  `useLocalAi: Boolean = false` and `clusterEmitEnabled: Boolean = true`. Persists via the
  existing `SharedPreferences` mechanism.
- **`LlmProviderRouter` (new)**: Object that resolves a `LlmProvider` instance based on
  `RuntimeFlags.useLocalAi` and `hasNanoCapableHardware()`. Single source of truth for routing.
- **`CloudLlmProvider` (new)**: `LlmProvider` implementation that builds `LlmGatewayRequest`s and
  delegates to the AIDL `callLlmGateway` method on a supplied `INetworkGateway`. Preserves the
  `LlmProvider` asymmetric error contract.
- **Supabase tables** (mirroring v1 Room schema, **hybrid plaintext + reserved-ciphertext** per
  Round 2 clarification): `envelopes`, `continuations`, `continuation_results`, `clusters`,
  `cluster_members`, `action_proposals`, `action_executions`, `audit_log_entries`,
  `user_profiles` (per-user preferences, declared focus topics, work hours, energy windows;
  out-of-scope for Day-1 population — exists so spec 014's first-run onboarding flow has
  somewhere to write). Each owns a `user_id uuid` column with RLS policy `auth.uid() = user_id`
  on all CRUD operations. Each content-bearing table also carries the nullable `*_ct bytea`
  ciphertext columns named in `specs/contracts/envelope-content-encryption-contract.md` (e.g.
  `body_ct`, `ocr_ct`, `transcript_ct`, `result_ct`, …) reserved for spec 006; Day-1 writes
  leave them `NULL`. `user_profiles` does NOT carry `*_ct` columns (no encrypted body content);
  `audit_log_entries` is append-only and omits `updated_at` per Constitution Principle XII.
- **Cluster-citation CHECK** (server-side enforcement of FR-032): `cluster_summary` cited envelope
  IDs MUST be members of the cited cluster.

---

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: After this spec lands, the verification command

  ```sh
  grep -rn "NanoLlmProvider()" app/src/main/ \
    | grep -v "cluster/ClusterDetectionWorker.kt" \
    | grep -v "ai/LlmProviderRouter.kt"
  ```

  returns zero results. The two excluded files are: (a) `LlmProviderRouter.kt` itself, which
  constructs the local impl on the local-mode branch, and (b) `ClusterDetectionWorker.kt`, carved
  out per the FR-013-016 amendment and self-documented via FR-013-028. Verifiable by a single
  shell pipeline.
- **SC-002**: `./gradlew compileDebugKotlin compileDebugUnitTestKotlin testDebugUnitTest --tests
  '*Llm*' --tests '*Cluster*'` exits with status 0 and zero failed tests on the `cloud-pivot`
  branch.
- **SC-003**: The AIDL stub for `callLlmGateway` is generated by the Android build (visible under
  `app/build/generated/aidl_source_output_dir/`) and is consumed by both `:capture` (via
  `CloudLlmProvider`) and `:net` (via `NetworkGatewayImpl`). Verifiable by `find` + `grep` on the
  generated sources directory.
- **SC-004**: Flipping `RuntimeFlags.useLocalAi` from `false` (default) to `true` and back at
  runtime changes which `LlmProvider` implementation `LlmProviderRouter.create` returns, verified
  by a unit test that does not require any device hardware.
- **SC-005**: The existing v1 cluster-suggestion engine continues to operate against
  `clusterModelLabelLock = NanoLlmProvider.MODEL_LABEL` after the refactor. "Continues to operate"
  means Day 1 preserves the existing **local-mode** behaviour of `ClusterDetectionWorker`: it
  still constructs `NanoLlmProvider()` directly (now annotated with the `CLUSTER-LOCAL-PIN`
  comment per FR-013-028), it still writes the local model label, and no cluster card appearance
  changes. Cloud migration of the cluster worker is **Phase 11 Block 4 work**, not Day 1, per
  ADR-006.
- **SC-006**: All three Supabase migrations apply cleanly in order on a fresh project, producing
  the v1 schema with RLS enabled on every table and the cluster-citation CHECK constraint
  installed. Verifiable from the Supabase SQL editor: `select tablename from pg_tables where
  schemaname='public'` and `select tablename, rowsecurity from pg_tables where schemaname='public'`.
- **SC-007**: `supabase/tests/multi_user_smoke.sql` exits 0 with output containing the line `PASS`
  on the production Supabase project.
- **SC-008** (alpha gate per ADR-007): No alpha install is created until SC-007 passes. The check
  is a manual gate on the release checklist.
- **SC-009 (latency)**: A subsequent telemetry spec MUST be able to attribute end-to-end latency
  to the `CloudLlmProvider` boundary because every outbound request carries a `requestId` (UUIDv4)
  set at request construction time.

## Assumptions

- The Vercel AI Gateway product, model availability (Sonnet 4.6, Haiku 4.5, embedding-3-small),
  and pricing/caching behaviour described in the tech-stack research doc dated 2026-04-28 remain
  valid through May 22, 2026. Validated against Anthropic and OpenAI public docs as of the spec
  date.
- The Supabase free tier is sufficient to run the multi-user smoke test and host the v1 schema for
  alpha. Upgrade to Pro is deferred until cost telemetry from a later spec warrants it.
- Existing OkHttp dependency in `:net` is sufficient for `LlmGatewayClient`; no new HTTP client
  needed.
- kotlinx.serialization is already on the classpath (used by existing JSON serialization
  elsewhere); if not, the planning phase will add it.
- The `:capture` ↔ `:net` AIDL channel and the existing `INetworkGateway` binding pattern
  (mirrored in `UrlHydrateWorker.kt`) is the correct shape to extend; no process re-architecture is
  required.
- Pixel 9 Pro hardware arriving May 1, 2026 is needed only to *verify* cluster-detection-on-cloud
  end-to-end; the Day 1 work in this spec does not require Nano-capable hardware because cloud is
  the default and `hasNanoCapableHardware()` returns `false` unconditionally in v1.
- AuthSessionStore lands in a Day 2–3 spec; for Day 1, `LlmGatewayClient` proceeds without an
  `Authorization` header. The Edge Function (also out of scope) will treat unauthenticated
  requests appropriately when it ships.

## Risks

- **R-013-001 (Gateway URL unknown)**: The real Vercel AI Gateway URL is not yet known. Day 1
  ships a placeholder URL; the URL becomes real when the Edge Function deploys (separate spec).
  Mitigation: provider-agnostic envelope + retry-once direct fallback (ADR-003) means the mobile
  binary does not need to ship again to swap URLs — the swap is configuration.
- **R-013-002 (real provider integration deferred)**: The Edge Function and real provider calls
  are out of scope for Day 1. The Day-1 deliverable ships skeleton/stub responses from
  `CloudLlmProvider`'s perspective (Gateway returns canned responses or 5xx → direct fallback also
  canned). Mitigation: this is intentional; the abstraction is what unblocks Phase 11 Block 4+.
  Real integration lands in the Edge Function spec.
- **R-013-003 (Pixel 9 Pro arrival May 1 gates cluster-detection-on-cloud verification)**: Without
  Pixel 9 Pro hardware, the cluster engine cannot be exercised end-to-end on cloud. Day-1 work in
  this spec does not require it; verification is needed in the Phase 11 Block 4 spec.
- **R-013-004 (RLS misconfiguration)**: A wrong RLS policy could leak data between users.
  Mitigation: the multi-user smoke test (FR-013-026) is the gate; nothing ships without a passing
  run.
- **R-013-005 (parcel size limits)**: Large embedding payloads (1536 floats ≈ 6 KB binary) cross
  the AIDL boundary. Well within the ~1 MB Binder limit, but worth noting as the abstraction
  scales (e.g., batch embeddings later).
- **R-013-006 (constitution amendment)**: Principle II's "MUST NOT touch the network" interpreted
  literally would block this spec. The intended reading (local-mode only) requires a constitution
  amendment, which is out-of-band coordination tracked separately.
