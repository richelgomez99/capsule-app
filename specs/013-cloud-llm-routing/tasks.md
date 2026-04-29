# Tasks: Cloud LLM Routing + Supabase Backbone (Day 1)

**Spec dir**: `specs/013-cloud-llm-routing/` | **Branch**: `cloud-pivot` | **Generated**: 2026-04-28
**Inputs**: [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md), [research.md](research.md), [quickstart.md](quickstart.md), [contracts/](contracts/)

> **Hard rules (non-negotiable)**
> 1. Each task = a single atomic commit on `cloud-pivot` (NFR-013-007). Sized for ~30 min – 2 hr.
> 2. Tasks are strictly sequenced by dependency. `[P]` is applied **only** where files are genuinely independent.
> 3. The FR-013-016 migration sweep **excludes** [`ClusterDetectionWorker.kt`](../../app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt) per the Round 2 clarification (carve-out). The `CLUSTER-LOCAL-PIN` comment is added by a separate task.
> 4. **No `git push` tasks.** All work stays local until the user explicitly approves a push (NFR-013-008).
> 5. Multi-user smoke test (Phase I) is a **release gate** per ADR-007 / SC-008 — no alpha install ships until it prints `PASS`.

## Format

`- [ ] T013-NNN [P?] [Phase X] Description`

- **`[P]`**: parallel-safe (independent files, no dependency on incomplete tasks).
- **Phase X**: A through I, matching the dependency-ordered phase plan.
- Every task block below contains: **Files**, **Acceptance**, **Commit**, **Depends on**.

---

## Phase A — Non-breaking foundation

**Goal**: Land doc updates, RuntimeFlags additions, sealed classes, and parcels. Every commit compiles green; no existing call site is rewired yet.

**Independent test**: `./gradlew compileDebugKotlin` continues to pass after each commit; no behavior change.

- [X] **T013-001** [Phase A] Relax `LlmProvider` interface doc-comment to scope the network-ban to local-mode implementations only (FR-013-001).
  - **Files**: [`app/src/main/java/com/capsule/app/ai/LlmProvider.kt`](../../app/src/main/java/com/capsule/app/ai/LlmProvider.kt)
  - **Acceptance**: Doc comment on `interface LlmProvider` no longer reads "MUST NOT touch the network" unconditionally; instead scopes the ban to local-mode implementations and documents that cloud-mode implementations route through `:net`. Method signatures and method count unchanged. `./gradlew compileDebugKotlin` exits 0.
  - **Commit**: `docs(ai): scope LlmProvider network ban to local-mode impls (FR-013-001)`
  - **Depends on**: —

- [X] **T013-002** [Phase A] Extend `RuntimeFlags` with `useLocalAi` (default `false`) and `clusterEmitEnabled` (default `true`); preserve `clusterModelLabelLock` (FR-013-002).
  - **Files**: [`app/src/main/java/com/capsule/app/RuntimeFlags.kt`](../../app/src/main/java/com/capsule/app/RuntimeFlags.kt)
  - **Acceptance**: Two new `@Volatile @JvmStatic var` fields persisted via the existing `SharedPreferences` (keys `cloud.use_local_ai` and `cluster.emit_enabled`). Default for `useLocalAi` is `false` (cloud is default). `clusterModelLabelLock` and its default (`NanoLlmProvider.MODEL_LABEL`) are unchanged. `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` exits 0. `grep -n "useLocalAi" app/src/main/java/com/capsule/app/RuntimeFlags.kt` returns ≥ 1 line.
  - **Commit**: `feat(runtime): add useLocalAi + clusterEmitEnabled flags (FR-013-002)`
  - **Depends on**: —

- [X] **T013-003** [P] [Phase A] Create `LlmGatewayRequest` sealed class with the six subtypes per data-model §1.1 (FR-013-003).
  - **Files**: `app/src/main/java/com/capsule/app/ai/gateway/LlmGatewayRequest.kt` (NEW; new sub-package `com.capsule.app.ai.gateway`)
  - **Acceptance**: `@Serializable sealed class LlmGatewayRequest` with subtypes `Embed`, `Summarize`, `ExtractActions`, `ClassifyIntent`, `GenerateDayHeader`, `ScanSensitivity`. Every subtype carries `requestId: String` plus method-specific payload fields per [data-model.md §1.1](data-model.md). Discriminator `"type"` matches the values in [contracts/llm-gateway-envelope-contract.md](contracts/llm-gateway-envelope-contract.md). `./gradlew compileDebugKotlin` exits 0.
  - **Commit**: `feat(ai/gateway): add LlmGatewayRequest sealed hierarchy (FR-013-003)`
  - **Depends on**: —

- [X] **T013-004** [P] [Phase A] Create `LlmGatewayResponse` sealed class with seven variants and `Embed` `equals`/`hashCode` override (FR-013-004).
  - **Files**: `app/src/main/java/com/capsule/app/ai/gateway/LlmGatewayResponse.kt` (NEW)
  - **Acceptance**: `@Serializable sealed class LlmGatewayResponse` with `EmbedResponse`, `SummarizeResponse`, `ExtractActionsResponse`, `ClassifyIntentResponse`, `GenerateDayHeaderResponse`, `ScanSensitivityResponse`, `Error(code, message)`. Every variant carries `requestId: String`. `EmbedResponse` overrides `equals` using `vector.contentEquals` and `hashCode` using `vector.contentHashCode`. `./gradlew compileDebugKotlin` exits 0.
  - **Commit**: `feat(ai/gateway): add LlmGatewayResponse sealed hierarchy with Embed equals override (FR-013-004)`
  - **Depends on**: —

- [X] **T013-005** [P] [Phase A] Add `@Serializable` mirror DTOs `StateSnapshotJson`, `AppFunctionSummaryJson`, `ActionProposalJson` per data-model §1.4.
  - **Files**: `app/src/main/java/com/capsule/app/ai/gateway/GatewayDtos.kt` (NEW)
  - **Acceptance**: Three field-for-field `@Serializable` mirrors of `StateSnapshot`, `AppFunctionSummary`, and `ActionProposal`. They are referenced by `LlmGatewayRequest.ExtractActions` and `LlmGatewayResponse.ExtractActionsResponse`. `./gradlew compileDebugKotlin` exits 0.
  - **Commit**: `feat(ai/gateway): add @Serializable DTO mirrors for AIDL wire format`
  - **Depends on**: T013-003, T013-004

- [X] **T013-006** [P] [Phase A] Create `LlmGatewayRequestParcel` + sibling AIDL parcelable declaration.
  - **Files**: `app/src/main/java/com/capsule/app/net/ipc/LlmGatewayRequestParcel.kt` (NEW); `app/src/main/aidl/com/capsule/app/net/ipc/LlmGatewayRequestParcel.aidl` (NEW)
  - **Acceptance**: Kotlin data class implements `android.os.Parcelable` with single `payloadJson: String` field, manual `writeToParcel`/`CREATOR` mirroring [`FetchResultParcel.kt`](../../app/src/main/java/com/capsule/app/net/ipc/FetchResultParcel.kt). AIDL stub contains exactly `parcelable LlmGatewayRequestParcel;` in package `com.capsule.app.net.ipc`. `./gradlew compileDebugKotlin` exits 0.
  - **Commit**: `feat(net/ipc): add LlmGatewayRequestParcel (Parcelable + AIDL stub)`
  - **Depends on**: T013-003

- [X] **T013-007** [P] [Phase A] Create `LlmGatewayResponseParcel` + sibling AIDL parcelable declaration.
  - **Files**: `app/src/main/java/com/capsule/app/net/ipc/LlmGatewayResponseParcel.kt` (NEW); `app/src/main/aidl/com/capsule/app/net/ipc/LlmGatewayResponseParcel.aidl` (NEW)
  - **Acceptance**: Same shape as T013-006 (single `payloadJson: String`, manual `Parcelable`). AIDL stub contains `parcelable LlmGatewayResponseParcel;`. `./gradlew compileDebugKotlin` exits 0.
  - **Commit**: `feat(net/ipc): add LlmGatewayResponseParcel (Parcelable + AIDL stub)`
  - **Depends on**: T013-004

---

## Phase B — AIDL extension + `LlmGatewayClient` skeleton

**Goal**: Extend the AIDL surface and add the OkHttp-based gateway client in `:net`. Adds new code paths without rewiring existing ones.

**Independent test**: `./gradlew :app:generateDebugAidl` produces stubs containing `callLlmGateway`; no production call site is yet using them.

- [X] **T013-008** [Phase B] Extend `INetworkGateway.aidl` with `callLlmGateway(in LlmGatewayRequestParcel) → LlmGatewayResponseParcel` (FR-013-005).
  - **Files**: [`app/src/main/aidl/com/capsule/app/net/ipc/INetworkGateway.aidl`](../../app/src/main/aidl/com/capsule/app/net/ipc/INetworkGateway.aidl)
  - **Acceptance**: AIDL adds the new method exactly as in [data-model.md §3](data-model.md). `fetchPublicUrl` signature is unchanged. Imports for the two new parcels are added. `./gradlew :app:generateDebugAidl` produces stubs; `find app/build/generated/aidl_source_output_dir -name "INetworkGateway*.java" | xargs grep -l "callLlmGateway"` returns ≥ 1 file. `NetworkGatewayImpl.kt` will fail to compile until T013-013; that is expected.
  - **Commit**: `feat(net/ipc): extend INetworkGateway AIDL with callLlmGateway (FR-013-005)`
  - **Depends on**: T013-006, T013-007

- [X] **T013-009** [Phase B] Create `LlmGatewayClient` skeleton with model routing, request envelope shape, and timeout matrix (FR-013-006, FR-013-007, FR-013-010).
  - **Files**: `app/src/main/java/com/capsule/app/net/LlmGatewayClient.kt` (NEW)
  - **Acceptance**: Class uses the existing OkHttp dependency (no new HTTP client added to `:net` Gradle config). It exposes a single suspending entry `suspend fun call(request: LlmGatewayRequest): LlmGatewayResponse` that selects the model per-type (`Embed`→`openai/text-embedding-3-small`, `Summarize`/`ExtractActions`/`GenerateDayHeader`→`anthropic/claude-sonnet-4-6`, `ClassifyIntent`/`ScanSensitivity`→`anthropic/claude-haiku-4-5`), serializes to the provider-agnostic `{type, payload}` JSON envelope per [contracts/llm-gateway-envelope-contract.md](contracts/llm-gateway-envelope-contract.md), and applies 30s default timeout / 60s for `Summarize`. Day-1 placeholder URL `https://gateway.example.invalid/llm`. `./gradlew compileDebugKotlin` exits 0.
  - `LlmGatewayClient.send(request)` wraps the flat sealed-class JSON `{type, requestId, …fields}` into the nested HTTP envelope `{type, requestId, payload: {…fields}}` before POST. On response, it unwraps the nested HTTP envelope `{type, requestId, ok, data: {…fields} | error: {code, message}}` into either the flat sealed-class JSON for `LlmGatewayResponse` (success) or `LlmGatewayResponse.Error(code, message)` (failure). Wrap/unwrap is a private helper inside `LlmGatewayClient`; the parcel layer (T013-006/T013-007) only ever sees flat sealed-class JSON.
  - **Commit**: `feat(net): add LlmGatewayClient skeleton with model routing + envelope (FR-013-006/007/010)`
  - **Depends on**: T013-003, T013-004, T013-005

- [ ] **T013-010** [Phase B] Add retry-once direct-provider fallback to `LlmGatewayClient` per ADR-003 (FR-013-008).
  - **Files**: `app/src/main/java/com/capsule/app/net/LlmGatewayClient.kt`
  - **Acceptance**: On Gateway 5xx response, the client retries the same request once against the direct provider endpoint (Anthropic Messages or OpenAI Embeddings, placeholder URLs). Retry-once is per-request, no exponential backoff. Two consecutive 5xx surface a single `LlmGatewayResponse.Error(code = "PROVIDER_5XX", ...)`. `IOException` and timeouts surface `Error(code = "NETWORK_UNAVAILABLE" | "TIMEOUT", ...)` rather than throwing across the AIDL boundary. `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` exits 0.
  - **Commit**: `feat(net): add retry-once direct-provider fallback to LlmGatewayClient (FR-013-008)`
  - **Depends on**: T013-009

- [ ] **T013-011** [Phase B] Add bearer-token graceful-null handling to `LlmGatewayClient` (FR-013-009).
  - **Files**: `app/src/main/java/com/capsule/app/net/LlmGatewayClient.kt`
  - **Acceptance**: Client attempts to read a bearer token from a `tokenProvider: () -> String?` constructor parameter (Day-1 stub returns `null` because `AuthSessionStore` does not yet exist). When the result is `null` or blank, no `Authorization` header is sent and the request proceeds. No `NullPointerException` is thrown on the no-auth path. `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` exits 0.
  - **Commit**: `feat(net): graceful-null bearer-token handling in LlmGatewayClient (FR-013-009)`
  - **Depends on**: T013-009

---

## Phase C — `NetworkGatewayImpl` integration + `CloudLlmProvider`

**Goal**: Wire the new code paths. After Phase C, cloud mode is functionally available behind the router; existing call sites still construct `NanoLlmProvider()` directly.

**Independent test**: Unit tests covering the embed-null contract and parcel round-trip pass.

- [ ] **T013-012** [Phase C] Wire `callLlmGateway` handler into `NetworkGatewayImpl` (FR-013-011).
  - **Files**: [`app/src/main/java/com/capsule/app/net/NetworkGatewayImpl.kt`](../../app/src/main/java/com/capsule/app/net/NetworkGatewayImpl.kt)
  - **Acceptance**: New override `override fun callLlmGateway(request: LlmGatewayRequestParcel): LlmGatewayResponseParcel` parses `request.payloadJson` into `LlmGatewayRequest` via kotlinx.serialization, delegates to a held `LlmGatewayClient` instance (constructor-injected), encodes the returned `LlmGatewayResponse` to JSON, wraps in `LlmGatewayResponseParcel`. `fetchPublicUrl` handler is unchanged. No locks taken across the network call (per FR-013-011). `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` exits 0.
  - **Commit**: `feat(net): wire callLlmGateway handler into NetworkGatewayImpl (FR-013-011)`
  - **Depends on**: T013-008, T013-011

- [ ] **T013-013** [Phase C] Create `CloudLlmProvider` implementing all six `LlmProvider` methods with asymmetric error mapping (FR-013-012, FR-013-013).
  - **Files**: `app/src/main/java/com/capsule/app/ai/CloudLlmProvider.kt` (NEW)
  - **Acceptance**: Class implements [`LlmProvider`](../../app/src/main/java/com/capsule/app/ai/LlmProvider.kt). Constructor takes `INetworkGateway`. Each method generates a UUIDv4 `requestId`, builds the appropriate `LlmGatewayRequest` subtype, JSON-encodes it into `LlmGatewayRequestParcel`, calls the AIDL method, decodes the response, and returns. Error mapping per [data-model.md §1.3](data-model.md): `embed()` returns `null` on any `Error`; `summarize` and `extractActions` throw `IOException`; remaining methods throw `IOException`. No direct OkHttp/Retrofit/HttpURLConnection import. `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` exits 0.
  - **Commit**: `feat(ai): add CloudLlmProvider with asymmetric error contract (FR-013-012/013)`
  - **Depends on**: T013-008, T013-012

- [ ] **T013-014** [Phase C] Unit test `CloudLlmProviderTest` — embed-null contract and per-method error mapping.
  - **Files**: `app/src/test/java/com/capsule/app/ai/CloudLlmProviderTest.kt` (NEW)
  - **Acceptance**: Test uses a fake `INetworkGateway` that returns canned `LlmGatewayResponseParcel`s wrapping success and `Error` variants. Cases: (a) `embed()` returns `null` on every `Error.code`; (b) `summarize()` and `extractActions()` throw `IOException` on `Error`; (c) remaining four methods throw on `Error`; (d) success path returns the expected typed result for each method. `./gradlew testDebugUnitTest --tests '*CloudLlmProviderTest*'` exits 0.
  - **Commit**: `test(ai): cover CloudLlmProvider error contract`
  - **Depends on**: T013-013

- [ ] **T013-015** [Phase C] Unit test `LlmGatewayParcelRoundTripTest` — Parcel write/read survives kotlinx.serialization round-trip.
  - **Files**: `app/src/test/java/com/capsule/app/ai/gateway/LlmGatewayParcelRoundTripTest.kt` (NEW)
  - **Acceptance**: Test (Robolectric) constructs each `LlmGatewayRequest` subtype, encodes to parcel, writes to `Parcel.obtain()`, rewinds, reads back, decodes, asserts equality. Special-case for `EmbedResponse`: assert `contentEquals` on the `FloatArray` and that two `EmbedResponse` instances with the same vector hash to the same value. `./gradlew testDebugUnitTest --tests '*LlmGatewayParcelRoundTripTest*'` exits 0.
  - **Commit**: `test(ai/gateway): parcel round-trip + Embed equality contract`
  - **Depends on**: T013-006, T013-007

---

## Phase D — `LlmProviderRouter` + call-site migration

**Goal**: Land the router and complete the FR-013-016 migration sweep with the cluster-worker carve-out.

**Independent test**: SC-001 grep returns zero outside the two excluded files; SC-004 router unit test passes.

- [ ] **T013-016** [Phase D] Create `LlmProviderRouter` with `hasNanoCapableHardware()` stub returning `false` (FR-013-014, FR-013-015).
  - **Files**: `app/src/main/java/com/capsule/app/ai/LlmProviderRouter.kt` (NEW)
  - **Acceptance**: `object LlmProviderRouter { fun create(context: Context, networkGateway: INetworkGateway?): LlmProvider }`. Resolution: if `RuntimeFlags.useLocalAi == true && hasNanoCapableHardware() == true` → return `NanoLlmProvider()`; else return `CloudLlmProvider(checkNotNull(networkGateway) { "networkGateway required for cloud mode" })`. `hasNanoCapableHardware()` is a private `fun` returning `false` unconditionally on Day 1 with a `// TODO: real Pixel 9 Pro / S24 detection — separate spec` comment. `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` exits 0.
  - **Commit**: `feat(ai): add LlmProviderRouter with hardware-stub (FR-013-014/015)`
  - **Depends on**: T013-002, T013-013

- [ ] **T013-017** [Phase D] Unit test `LlmProviderRouterTest` proving SC-004 (flag flip changes returned impl).
  - **Files**: `app/src/test/java/com/capsule/app/ai/LlmProviderRouterTest.kt` (NEW)
  - **Acceptance**: Test cases: (a) default (`useLocalAi=false`) returns `CloudLlmProvider`; (b) `useLocalAi=true` + Nano-capable=`false` (the unconditional Day-1 stub) still returns `CloudLlmProvider` (per acceptance scenario 3); (c) `useLocalAi=false` + `networkGateway=null` throws `IllegalStateException` whose message names the missing dependency. `./gradlew testDebugUnitTest --tests '*LlmProviderRouterTest*'` exits 0.
  - **Commit**: `test(ai): cover LlmProviderRouter resolution rules (SC-004)`
  - **Depends on**: T013-016

- [ ] **T013-018** [Phase D] Migrate every production `NanoLlmProvider()` call site under `app/src/main/` to `LlmProviderRouter.create(context, networkGateway)` — **except** `cluster/ClusterDetectionWorker.kt` (FR-013-016 sweep with carve-out).
  - **Files**: every file under `app/src/main/` matching `grep -rln "NanoLlmProvider()" app/src/main/` at the time of the task **excluding** [`app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt`](../../app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt), [`app/src/main/java/com/capsule/app/ai/LlmProviderRouter.kt`](../../app/src/main/java/com/capsule/app/ai/LlmProviderRouter.kt) (local-mode branch), and [`app/src/main/java/com/capsule/app/ai/NanoLlmProvider.kt`](../../app/src/main/java/com/capsule/app/ai/NanoLlmProvider.kt) itself.
  - **Acceptance**: After this commit, the SC-001 verification command returns zero results:
    ```sh
    grep -rn "NanoLlmProvider()" app/src/main/ \
      | grep -v "cluster/ClusterDetectionWorker.kt" \
      | grep -v "ai/LlmProviderRouter.kt"
    ```
    Test fakes under `app/src/test/` and `app/src/androidTest/` are NOT touched (FR-013-016 explicitly permits them). `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` exits 0. `NanoLlmProvider.kt` method bodies are unchanged (FR-013-017).
  - **Commit**: `refactor(ai): route production sites through LlmProviderRouter (FR-013-016)`
  - **Depends on**: T013-016

- [ ] **T013-019** [Phase D] Add `// CLUSTER-LOCAL-PIN: migrated in Phase 11 Block 4` comment in `ClusterDetectionWorker` immediately above the `NanoLlmProvider()` constructor (FR-013-028).
  - **Files**: [`app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt`](../../app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt)
  - **Acceptance**: Exactly one line `// CLUSTER-LOCAL-PIN: migrated in Phase 11 Block 4` appears immediately above the `NanoLlmProvider()` construction call. `grep -n "CLUSTER-LOCAL-PIN" app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt | wc -l` returns exactly `1`. The constructor itself and the `clusterModelLabelLock` usage are unchanged (FR-013-018). `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` exits 0.
  - **Commit**: `chore(cluster): pin ClusterDetectionWorker to local mode with self-doc comment (FR-013-028)`
  - **Depends on**: T013-018

---

## Phase E — Smoke test + verification

**Goal**: Prove SC-001, SC-002, SC-003 all pass against the merged Phase A–D state. This task is verification-only — no production code changes.

- [ ] **T013-020** [Phase E] Run the full Day-1 verification matrix and capture the output in the commit body.
  - **Files**: none modified — verification only.
  - **Acceptance**: All four commands MUST exit 0 (or empty for the grep) **in this exact order**:
    1. `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` — exits 0 (SC-002 compile gate).
    2. `./gradlew :app:generateDebugAidl` — exits 0; `find app/build/generated/aidl_source_output_dir -name "INetworkGateway*.java" -exec grep -l "callLlmGateway" {} +` returns ≥ 1 file (SC-003).
    3. `./gradlew testDebugUnitTest --tests '*Llm*' --tests '*Cluster*'` — exits 0 with zero failures (SC-002 test gate, SC-005 cluster preserve).
    4. `grep -rn "NanoLlmProvider()" app/src/main/ | grep -v "cluster/ClusterDetectionWorker.kt" | grep -v "ai/LlmProviderRouter.kt"` — returns zero lines (SC-001).
    Capture each command's output in the commit message body. If any command fails, do NOT advance to Phase F; open a follow-up commit on the failing task ID.
  - **Commit**: `chore(verify): Day-1 Phase A–D verification matrix passes (SC-001/002/003/005)`
  - **Depends on**: T013-019

---

## Phase F — Supabase project + initial schema migration

**Goal**: Stand up the Supabase backbone and write the v1 hybrid-shape schema. Server-side only — no Android changes.

**Independent test**: Migration `00000000` applies cleanly on a fresh Supabase project; `select tablename from pg_tables where schemaname='public'` lists all nine tables.

- [ ] **T013-021** [Phase F] Provision Supabase project (free tier) and record secrets out of repo (FR-013-021).
  - **Files**: none in repo — secrets land in 1Password / shared vault. Add a single line to local-only `local.properties` referencing where the secrets live (do NOT commit the secrets themselves).
  - **Acceptance**: A Supabase project exists with project ref, anon key, service role key, and DB URL recorded outside the repo. `git status` shows no new tracked secrets. `local.properties` is in `.gitignore` (verify before commit).
  - **Commit**: `chore(supabase): record Day-1 project provisioning (no secrets in repo)`
  - **Depends on**: —

- [ ] **T013-022** [Phase F] Create `supabase/` directory scaffold with `migrations/`, `functions/` (empty placeholder), `tests/` (FR-013-022).
  - **Files**: `supabase/migrations/.gitkeep` (NEW), `supabase/functions/.gitkeep` (NEW), `supabase/tests/.gitkeep` (NEW), `supabase/README.md` (NEW — short note pointing at this spec)
  - **Acceptance**: `ls supabase/migrations supabase/functions supabase/tests` lists each as an empty directory (or with `.gitkeep`). `supabase/README.md` references this spec by ID and warns future migrations adding `SECURITY DEFINER` functions to re-check `auth.uid()` (per spec edge case).
  - **Commit**: `chore(supabase): scaffold migrations/functions/tests directories (FR-013-022)`
  - **Depends on**: T013-021

- [ ] **T013-023** [Phase F] Write `00000000_initial_schema.sql` mirroring v1 Room schema with hybrid plaintext + reserved nullable `*_ct bytea` columns (FR-013-023).
  - **Files**: `supabase/migrations/00000000_initial_schema.sql` (NEW)
  - **Acceptance**: Migration enables `pgcrypto` and `vector` extensions; creates the nine tables defined in [data-model.md §5](data-model.md) (`envelopes`, `continuations`, `continuation_results`, `clusters`, `cluster_members`, `action_proposals`, `action_executions`, `audit_log_entries`, `user_profiles`); every table includes `user_id uuid not null references auth.users(id) on delete cascade`; `clusters.embedding` and `cluster_members.embedding` are `vector(1536)`; nullable `*_ct bytea` ciphertext columns named per [`specs/contracts/envelope-content-encryption-contract.md`](../contracts/envelope-content-encryption-contract.md) are present (`body_ct`, `ocr_ct`, `transcript_ct`, `media_ref_ct` on `envelopes`; `result_ct` on `continuation_results`; `summary_ct` on `clusters`; `excerpt_ct` on `cluster_members`; `payload_ct` on `action_proposals`; `result_ct` on `action_executions`); `cluster_members` has unique constraint on `(cluster_id, envelope_id)`. `audit_log_entries` table has `created_at` only (no `updated_at` — see FR-013-023 audit exception). Migration applies cleanly on a fresh project: `psql "$DB_URL" -f supabase/migrations/00000000_initial_schema.sql` exits 0; `select count(*) from pg_tables where schemaname='public'` returns ≥ 9.
  - **Commit**: `feat(supabase): initial v1 schema with hybrid plaintext + reserved *_ct columns (FR-013-023)`
  - **Depends on**: T013-022

---

## Phase G — RLS migration

**Goal**: Enable RLS and install per-table per-CRUD-verb policies binding `auth.uid() = user_id` (ADR-007).

**Independent test**: After applying, `select tablename, rowsecurity from pg_tables where schemaname='public'` shows `rowsecurity = t` for every table from Phase F.

- [ ] **T013-024** [Phase G] Write `00000001_rls_policies.sql` per [contracts/supabase-rls-contract.md](contracts/supabase-rls-contract.md) (FR-013-024).
  - **Files**: `supabase/migrations/00000001_rls_policies.sql` (NEW)
  - **Acceptance**: For each of the nine tables: `ALTER TABLE <t> ENABLE ROW LEVEL SECURITY;` plus four policies (one per CRUD verb) of shape `USING (auth.uid() = user_id) WITH CHECK (auth.uid() = user_id)`. Special case `audit_log_entries`: SELECT and INSERT permitted; UPDATE and DELETE policies evaluate to `false` (audit is append-only per Constitution Principle XII). Migration applies cleanly: `psql "$DB_URL" -f supabase/migrations/00000001_rls_policies.sql` exits 0. `select count(*) from pg_tables where schemaname='public' and rowsecurity = false` returns 0.
  - **Commit**: `feat(supabase): RLS auth.uid()=user_id policies on every table (FR-013-024)`
  - **Depends on**: T013-023

---

## Phase H — FR-032 cluster-membership trigger

**Goal**: Server-side enforcement of spec 002's FR-032 cluster-citation invariant per ADR-002.

**Independent test**: Inserting a `cluster_summary` whose cited envelope IDs are not all members of the cited cluster fails with the trigger's error.

- [ ] **T013-025** [Phase H] Write `00000002_cluster_membership_check.sql` implementing the FR-032 invariant per [contracts/supabase-cluster-membership-check-contract.md](contracts/supabase-cluster-membership-check-contract.md) (FR-013-025).
  - **Files**: `supabase/migrations/00000002_cluster_membership_check.sql` (NEW)
  - **Acceptance**: Migration installs a `BEFORE INSERT OR UPDATE` trigger on `clusters` (and/or the relevant cited-envelope-ID column per the contract) that validates every cited envelope ID has a row in `cluster_members` for the same `cluster_id`. Migration applies cleanly: `psql "$DB_URL" -f supabase/migrations/00000002_cluster_membership_check.sql` exits 0. A negative test (insert `clusters.summary` citing a non-member envelope ID) raises a Postgres exception; a positive test (cite only members) succeeds.
  - **Commit**: `feat(supabase): cluster-membership trigger enforces FR-032 server-side (FR-013-025)`
  - **Depends on**: T013-024

---

## Phase I — Multi-user smoke test SQL + execution (RELEASE GATE)

**Goal**: Prove RLS isolation between two users on the production project. Per ADR-007 / SC-008, no alpha install ships until T013-028 prints `PASS`.

**Independent test**: `psql "$DB_URL" -f supabase/tests/multi_user_smoke.sql` outputs `PASS` and exits 0.

- [ ] **T013-026** [Phase I] Write `multi_user_smoke.sql` per FR-013-026 / [contracts/supabase-rls-contract.md](contracts/supabase-rls-contract.md).
  - **Files**: `supabase/tests/multi_user_smoke.sql` (NEW)
  - **Acceptance**: Script (a) creates users A and B via `auth.admin.createUser`; (b) inserts envelopes, a cluster + members, and an action proposal owned by each user; (c) sets the session to user B and runs `SELECT * FROM envelopes` — asserts only B's rows return; (d) attempts `UPDATE` and `DELETE` against user A's primary keys from B's session — asserts zero rows affected and no error leaks row existence; (e) prints a single `PASS` line on success or `FAIL <reason>` on failure; (f) exits 0 on PASS, non-zero on FAIL.
  - **Commit**: `test(supabase): multi-user RLS smoke test SQL (FR-013-026)`
  - **Depends on**: T013-025

- [ ] **T013-027** [Phase I] Apply the three migrations to the production Supabase project in order.
  - **Files**: none — operational task. Note migration timestamps in commit body.
  - **Acceptance**: `psql "$DB_URL" -f supabase/migrations/00000000_initial_schema.sql && psql "$DB_URL" -f supabase/migrations/00000001_rls_policies.sql && psql "$DB_URL" -f supabase/migrations/00000002_cluster_membership_check.sql` each exit 0 in order against the project provisioned in T013-021. From the SQL editor: `select count(*) from pg_tables where schemaname='public' and rowsecurity = false` returns 0 (SC-006).
  - **Commit**: `chore(supabase): apply migrations 00000000–00000002 to Day-1 project (SC-006)`
  - **Depends on**: T013-023, T013-024, T013-025

- [ ] **T013-028** [Phase I] **RELEASE GATE** — Execute the multi-user smoke test against the production project; capture the `PASS` output (SC-007, SC-008, ADR-007).
  - **Files**: none — operational task. Capture full smoke-test stdout/stderr in the commit body.
  - **Acceptance**: `psql "$DB_URL" -f supabase/tests/multi_user_smoke.sql` exits 0 and stdout contains the line `PASS`. If the run prints `FAIL <reason>`, do NOT mark this task done — open a follow-up task to fix RLS policies, re-run T013-027 if needed, then re-run T013-028. **No alpha install may be created until this task is complete and PASSes (SC-008 hard gate per ADR-007).**
  - **Commit**: `chore(supabase): multi-user RLS smoke test PASS — alpha gate cleared (SC-007/008)`
  - **Depends on**: T013-026, T013-027

---

## Dependencies & Execution Order

### Phase ordering (strict)

```
A → B → C → D → E (Android smoke gate) → F → G → H → I (release gate)
```

Phases A–E (Android) and Phases F–I (Supabase) can be executed by different people once Phase A is merged, but the alpha-install gate (T013-028) blocks regardless of Android-side completion.

### Parallel opportunities (within a phase)

- **Phase A**: T013-003, T013-004, T013-005 (sealed classes + DTOs) and T013-006, T013-007 (parcels) are `[P]` — different files, no shared edits. T013-001 and T013-002 are independent of each other and of the sealed-class work.
- **Phase B**: T013-010 and T013-011 both edit `LlmGatewayClient.kt`, so they are NOT parallel — order by dependency on T013-009.
- **Phase C–I**: largely sequential (each task either depends on the previous file's compilation or on a server-side migration order).

### Critical-path lengths

- Android critical path: T013-001 → T013-002 → T013-003 → T013-006 → T013-008 → T013-012 → T013-013 → T013-016 → T013-018 → T013-019 → T013-020 (11 tasks).
- Supabase critical path: T013-021 → T013-022 → T013-023 → T013-024 → T013-025 → T013-026 → T013-027 → T013-028 (8 tasks).

---

## Release Gates

| Success criterion | Task ID | Gate type |
|---|---|---|
| **SC-001** — `grep` returns zero outside the two excluded files | T013-018 (sweep) and T013-020 (verification) | Phase E gate |
| **SC-002** — `compileDebug*` and `testDebug*` pass | T013-020 | Phase E gate |
| **SC-003** — AIDL stub generated and consumed by both `:capture` and `:net` | T013-020 | Phase E gate |
| **SC-004** — `useLocalAi` flip changes router output (unit-tested) | T013-017 | Phase D internal |
| **SC-005** — Cluster engine continues to operate in local mode with the `CLUSTER-LOCAL-PIN` comment | T013-019, T013-020 | Phase D / E |
| **SC-006** — All three migrations apply cleanly on a fresh project | T013-027 | Phase I |
| **SC-007** — Multi-user smoke test prints `PASS` | T013-028 | **Release gate** |
| **SC-008** — No alpha install ships until SC-007 passes (ADR-007) | T013-028 | **Release gate** |
| **SC-009** — Latency attribution possible via `requestId` | T013-003, T013-013 | Built-in (no separate task) |

---

## Implementation Strategy

1. **Day-1 morning**: Phase A (T013-001 → T013-007) and Phase B (T013-008 → T013-011). Each commit is small, compiles, and ships behind no flag (the new code is unreachable until Phase D's migration).
2. **Day-1 midday**: Phase C (T013-012 → T013-015) wires the new path. After T013-015 the cloud path is functional but not yet routed-to.
3. **Day-1 afternoon (Android)**: Phase D (T013-016 → T013-019) flips the default and finishes the sweep with the carve-out. Phase E (T013-020) is the verification commit.
4. **Day-1 afternoon (Supabase, in parallel with Phase D if staffed)**: Phase F → G → H (T013-021 → T013-025).
5. **Day-1 end**: Phase I (T013-026 → T013-028). T013-028 is the release gate; if it FAILs, debug RLS, re-run, do not push.
6. **No `git push`** until the user explicitly approves (NFR-013-008).

---

## Generation Notes

- **Total task count**: 28.
- **Phase breakdown**: A=7, B=4, C=4, D=4, E=1, F=3, G=1, H=1, I=3.
- **`[P]` parallel tasks**: 5 (T013-003, T013-004, T013-005, T013-006, T013-007). All other tasks are sequential by file or by migration order.
- **Tasks with multi-file scope**: T013-006, T013-007 (Kotlin parcel + AIDL stub each), T013-018 (sweep across all production `NanoLlmProvider()` sites except the carve-out).
- **Carve-out enforcement**: T013-018 (excludes `ClusterDetectionWorker.kt` from the sweep) + T013-019 (adds the self-documenting comment) jointly satisfy FR-013-016 and FR-013-028.
- **Verification commands embedded verbatim** in T013-020 acceptance per the user prompt's Phase E spec.
- **No `git push` task generated** per the user's hard rule (NFR-013-008).
- **Ambiguities surfaced during decomposition**: zero. The spec + plan + data-model + contracts gave unambiguous file paths, field shapes, and acceptance commands for every task.
