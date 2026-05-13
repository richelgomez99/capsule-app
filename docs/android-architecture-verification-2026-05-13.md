# Android Architecture Verification - 2026-05-13

This verifies whether the Round 1-6 architecture can realistically work on Android devices. It is not a new product plan. It is an Android feasibility check against the current app code, build, tests, and platform constraints.

Related docs:

- [Orbit Agent Architecture Round 2 - 2026-05-12](orbit-agent-architecture-round-2-2026-05-12.md)
- [Orbit Agent Architecture Round 3 - 2026-05-12](orbit-agent-architecture-round-3-2026-05-12.md)
- [Orbit Agent Architecture Round 4 - 2026-05-12](orbit-agent-architecture-round-4-2026-05-12.md)
- [Orbit Agent Architecture Round 5 - 2026-05-12](orbit-agent-architecture-round-5-2026-05-12.md)
- [Orbit Agent Architecture Round 6 - 2026-05-12](orbit-agent-architecture-round-6-2026-05-12.md)
- [Capture Understanding Stack Research - 2026-05-12](capture-understanding-stack-research-2026-05-12.md)

## Verdict

Yes: the planned architecture can work on Android, but the wording needs one important correction.

The safe Android phrasing is:

> Orbit enforces `:net` as the only app code path allowed to perform network I/O, using package boundaries, AIDL, custom lint, tests, and review gates.

The unsafe phrasing is:

> Only the `:net` process holds the `INTERNET` permission.

Android permissions are granted to the app UID, not to individual processes. The current app uses multiple processes, but they share the same package UID. The OS does not make `INTERNET` process-scoped. Orbit can still enforce the intended boundary in practice, but it is a static/runtime architecture rule, not a manifest-level OS permission split.

With that correction, the architecture is Android-feasible.

## Online research addendum

This section was added after the initial repo/device verification pass to validate the Android-specific assumptions against current Android documentation. The conclusion still holds, but the online research makes the boundary wording and implementation gates sharper.

Sources checked:

- Android application fundamentals: <https://developer.android.com/guide/components/fundamentals>
- Android processes and threads: <https://developer.android.com/guide/components/processes-and-threads>
- Android permissions overview: <https://developer.android.com/guide/topics/permissions/overview>
- Android AIDL guide: <https://developer.android.com/develop/background-work/services/aidl>
- Android Binder API reference: <https://developer.android.com/reference/android/os/Binder>
- Android `TransactionTooLargeException`: <https://developer.android.com/reference/android/os/TransactionTooLargeException>
- Android WorkManager/task scheduling: <https://developer.android.com/develop/background-work/background-tasks/persistent>
- Android background task restrictions: <https://developer.android.com/develop/background-work/background-tasks/bg-work-restrictions>
- Android foreground services: <https://developer.android.com/develop/background-work/services/fgs>
- Android network security config: <https://developer.android.com/privacy-and-security/security-config>
- Android network operations: <https://developer.android.com/develop/connectivity/network-ops/connecting>
- Android Room migration testing: <https://developer.android.com/training/data-storage/room/migrating-db-versions>

### Research findings

1. Android sandboxing is app/UID-based, while `android:process` is component placement.
   Android's application fundamentals describe each app as running in its own security sandbox with a unique Linux user ID by default. The processes-and-threads docs confirm that app components run in one process by default, but manifest entries can set `android:process` so selected components run in separate processes. This supports Orbit's multiprocess topology for isolation and lifecycle separation, but it does not turn permissions into per-process grants.

2. Network permission is an app capability, so the `:net` guarantee must be architectural.
   Android's network docs state that network operations require manifest permissions such as `android.permission.INTERNET`, and the permissions docs describe install-time and runtime permission behavior at the app level. That confirms the correction above: `:net` can be the only allowed network code path, but not the only process with OS-level `INTERNET`.

3. AIDL/Binder is the right IPC mechanism, but it imposes thread-safety and payload constraints.
   The AIDL docs say remote-process calls are dispatched from a Binder thread pool and implementations must be thread-safe. They also warn that marshalling is expensive. The `TransactionTooLargeException` reference states that the Binder transaction buffer is currently 1MB shared by all in-flight transactions for a process and recommends keeping transactions small, avoiding huge arrays/bitmaps, paging results, and returning essential data first. This strongly supports passing IDs, compact request JSON, page tokens, and summaries over AIDL instead of raw HTML, screenshots, embeddings, or full evidence bundles.

4. WorkManager supports bounded cloud sync/enrichment, not unconstrained background agents.
   Android recommends WorkManager for work that should persist if the app leaves the visible state, including server sync. It supports constraints, retries, chaining, expedited work, and long-running work with foreground notifications. Background restriction docs also make clear that excessive background activity can be restricted by users/OEMs, and foreground services should only be used for user-noticeable tasks. This supports the plan's user-invoked agent flows and bounded enrichment, and argues against autonomous background agents as an early milestone.

5. Cloud-heavy extraction is the correct Android shape.
   Android network docs recommend secure network communication, minimizing sensitive data sent over the network, encapsulating network operations behind repositories, and avoiding network work on the UI thread. For Orbit, this maps to `INetworkGateway` plus typed cloud calls for dynamic extraction, browser fallback, heavy VLM, KG updates, and long-running LLM workflows. Android should orchestrate, display, approve, and store compact state; the cloud should do heavyweight fetching/reasoning when user policy allows it.

6. The connected-test failures match official platform behavior.
   Network security config docs say cleartext traffic is disabled by default for apps targeting Android 9/API 28 and higher unless explicitly opted in. They also describe domain-specific and debug-only trust/cleartext configuration. That explains the localhost/cleartext connected-test failures and turns them into a test harness fix, not an architecture blocker. Room docs say migration testing requires exported schema JSON files and adding the schema location as an androidTest asset folder, which explains the missing `1.json`/`3.json` migration-test failures.

### Research-backed refinements

The Android plan should carry these gates into Speckit:

- State the network boundary as: `:net` is the only allowed network code path, enforced by package boundaries, AIDL, lint, tests, and review.
- Add Binder contract limits to every new AIDL surface: no raw page bodies, screenshots, embeddings, or large result sets; use IDs, compact summaries, and pagination.
- Treat AIDL service implementations as concurrent APIs; require thread-safety, cancellation, timeouts, and bounded work.
- Use WorkManager for reliable bounded enrichment/sync, with network/battery constraints where appropriate.
- Use foreground services only for user-visible capture/overlay/execution work, not invisible agent loops.
- Keep dynamic extraction, browser rendering, KG updates, heavy VLM, and expensive LLM workflows cloud-side behind explicit Basic/Smart/Deep policy.
- Fix connected-test harnesses by adding debug/test network security config for localhost or using HTTPS test servers, and by packaging Room schemas into androidTest assets.

Net: the online research reinforces the report's final assessment. The plan can work on Android if Orbit treats Android as a Binder/Room/client-orchestrator platform and keeps cloud-heavy work behind explicit network gateway and user policy controls.

## Verification commands run

### Build gate

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug
```

Result: **PASS**.

Gradle completed successfully in 1m31s. This verifies the current Android app compiles, JVM tests pass, Android test sources compile, and lint passes, including the custom no-network-outside-net lint rule.

### Connected device check

```bash
adb devices -l
```

Result: **Tab S9 attached**.

Device: `SM-X710` / `gts9wifi`.

### Targeted connected Android test attempt

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.capsule.app.net.NetworkGatewayContractTest,com.capsule.app.net.UidCheckTest,com.capsule.app.action.NoNetworkDuringActionExecutionTest,com.capsule.app.action.ExecutionIpcContractTest,com.capsule.app.data.OrbitDatabaseTest,com.capsule.app.data.OrbitDatabaseMigrationV1toV2Test,com.capsule.app.data.OrbitDatabaseMigrationV2toV3Test,com.capsule.app.data.OrbitDatabaseMigrationV3toV4Test
```

Result: **PARTIAL**.

31 targeted tests started on the Tab S9. 22 passed and 9 failed. The failures are test-harness/config issues rather than evidence that the architecture cannot run on Android:

- `NetworkGatewayContractTest` localhost cases failed because localhost is blocked by gateway URL validation and cleartext localhost is blocked by Android network security policy.
- Migration tests failed because Room schema JSON files such as `com.capsule.app.data.OrbitDatabase/1.json` and `3.json` were not available in androidTest assets.

Implication: future architecture work should fix the connected-test harness before relying on those connected tests as release gates. The compile/unit/lint gate is green.

## Current Android anchors

The current app already has the structural pieces the plan needs.

### Process topology

[app/src/main/AndroidManifest.xml](../app/src/main/AndroidManifest.xml) declares:

- default process for UI activities;
- `CapsuleOverlayService` in `:capture`;
- `EnvelopeRepositoryService` in `:ml`;
- `NetworkGatewayService` in `:net`;
- `ActionExecutorService` in `:capture`.

This supports the Round 4/Round 5 boundary model:

```text
:ui       displays diary/detail/settings/approvals
:capture  owns overlay and local external-write execution
:ml       owns encrypted Room and audit/repository writes
:net      owns network client code path
```

Android feasibility: **high**.

Required correction: describe `:net` as the only allowed code path for network I/O, not the only process with OS-level `INTERNET` permission.

### AIDL IPC surfaces

The app already has AIDL surfaces for the planned boundaries:

- [app/src/main/aidl/com/capsule/app/net/ipc/INetworkGateway.aidl](../app/src/main/aidl/com/capsule/app/net/ipc/INetworkGateway.aidl): `fetchPublicUrl` and `callLlmGateway`.
- [app/src/main/aidl/com/capsule/app/data/ipc/IEnvelopeRepository.aidl](../app/src/main/aidl/com/capsule/app/data/ipc/IEnvelopeRepository.aidl): seal/read/mutate/hydration/action/cluster repository surface.
- [app/src/main/aidl/com/capsule/app/action/ipc/IActionExecutor.aidl](../app/src/main/aidl/com/capsule/app/action/ipc/IActionExecutor.aidl): local action execution.

Android feasibility: **high**.

Constraint: Binder has practical transaction-size limits. Future `EvidenceBundle`, citations, retrieval chunks, memory candidates, and agent plans should pass IDs and compact summaries over Binder, not raw page text, screenshots, embeddings, or large evidence payloads.

### Network gateway

[app/src/main/java/com/capsule/app/net/NetworkGatewayService.kt](../app/src/main/java/com/capsule/app/net/NetworkGatewayService.kt) is a bound service in `:net` and exposes `fetchPublicUrl` plus `callLlmGateway`.

[app/src/main/java/com/capsule/app/ai/CloudLlmProvider.kt](../app/src/main/java/com/capsule/app/ai/CloudLlmProvider.kt) already routes cloud LLM calls through `INetworkGateway` rather than importing HTTP clients.

[build-logic/lint/src/main/java/com/capsule/lint/NoHttpClientOutsideNetDetector.kt](../build-logic/lint/src/main/java/com/capsule/lint/NoHttpClientOutsideNetDetector.kt) enforces an error when HTTP clients are instantiated outside `com.capsule.app.net.*`.

Android feasibility: **high**, with an enforcement caveat.

Constraint: Android cannot process-scope `INTERNET`, so the lint rule and code review are load-bearing. Future specs should keep a no-network-outside-net lint/test gate.

### Local action execution

[app/src/main/java/com/capsule/app/action/ActionExecutorService.kt](../app/src/main/java/com/capsule/app/action/ActionExecutorService.kt) already runs in `:capture`, validates function/schema/args, invokes local handlers, records action/audit rows through `:ml`, and supports an undo window.

Android feasibility: **high**.

This is exactly the right Android boundary for Round 4/Round 5 approval-first execution. The future `ApprovalRequest` layer can wrap the existing flow without moving execution to cloud.

### Encrypted Room storage

[app/src/main/java/com/capsule/app/data/OrbitDatabase.kt](../app/src/main/java/com/capsule/app/data/OrbitDatabase.kt) uses Room v7 with SQLCipher and Android Keystore-backed passphrase management. Existing entities already include captures, continuations, continuation results, audit rows, actions, skills, clusters, cluster members, and notes.

Android feasibility: **high** for additive sidecar tables.

Round 2/Round 5 were correct to prefer sidecars over destructive rewrites. `SourceIdentity`, `EvidenceBundle`, `CaptureUnderstanding`, `RetrievalChunk`, `MemoryCandidate`, and `ApprovalRequest` fit Room well if they are additive and indexed carefully.

Constraint: migration tests need androidTest asset packaging fixed before future Room migration gates can be trusted on connected devices.

### Cloud/local LLM routing

[app/src/main/java/com/capsule/app/ai/LlmProviderRouter.kt](../app/src/main/java/com/capsule/app/ai/LlmProviderRouter.kt) already centralizes local-vs-cloud provider choice and currently falls through to cloud because hardware detection is a stub.

Android feasibility: **medium-high**.

The shape is right, but a real local/Nano kill-switch story requires:

- actual AICore/Nano availability detection;
- user-visible local-only/cloud mode setting;
- graceful local fallback when cloud is off;
- clear UI when a feature is unavailable locally.

### Source identity and YouTube handling

[app/src/main/java/com/capsule/app/ui/primitives/SourceIdentityResolver.kt](../app/src/main/java/com/capsule/app/ui/primitives/SourceIdentityResolver.kt) and [app/src/main/java/com/capsule/app/net/ProviderMetadataResolver.kt](../app/src/main/java/com/capsule/app/net/ProviderMetadataResolver.kt) already cover YouTube URL recognition and oEmbed metadata.

Android feasibility: **high**.

Required cleanup: the current UI resolver still falls back from app category `video` to YouTube. Round 2/Round 5 correctly require provider/app evidence before showing YouTube identity.

## Feature feasibility matrix

| Planned feature | Android fit | Why |
| --- | --- | --- |
| Capture understanding | High | Additive Room sidecars, existing hydration worker, capture detail UI, `INetworkGateway` path. |
| Basic/Smart/Deep controls | High | Settings + persisted policy are normal Android work; use repository-backed state if controls become multi-process/transactional. |
| Evidence bundles | High | Store in Room/Supabase; pass IDs over Binder. Avoid raw large payloads in AIDL. |
| Retrieval chunks | High | Local FTS/Room is feasible; cloud pgvector stays server-side. Embeddings should not move over Binder in bulk. |
| Ask Orbit citations | High | UI + retrieval + gateway fit. Keep answers citation-first and compact. |
| Candidate actions v2 | High | Existing action proposal/executor stack already matches this. |
| Approval runtime | High | Android UI confirmation + local Intent execution is the native path. |
| Memory candidates/inspector | High | Settings/detail UI + Room sidecars fit well. |
| Cloud gateway/budget controls | High | Mostly server/gateway work; Android only needs typed request parcels and displayable receipts/fallbacks. |
| KG/backend POC | High if cloud-side | Android should not host the KG. Use cloud adapter and sync compact references/results. |
| Agent coordinator | Medium-high | Feasible if user-invoked and approval-first. Avoid autonomous background execution and heavy local orchestration. |
| Full agent runtime | Medium | Feasible later, but only after Binder payload, background work, consent, cancellation, and battery constraints are explicit. |

## Android-specific architecture rules

These should be added to future specs/tasks as hard gates.

### Rule 1: network boundary is code-enforced, not permission-enforced

Because `INTERNET` is app UID-wide, every future feature must keep:

- no HTTP clients outside `com.capsule.app.net.*`;
- lint gate enabled;
- no direct provider SDK clients outside `:net` unless explicitly reviewed;
- workers and providers using `INetworkGateway` for network operations.

### Rule 2: Binder payloads must stay small

Pass these over AIDL:

- IDs;
- short status strings;
- compact display summaries;
- pagination tokens;
- small JSON requests/responses.

Do not pass these over AIDL in bulk:

- screenshots;
- raw HTML;
- full extracted page text;
- embedding vectors in large lists;
- large evidence bundles;
- entire retrieval result corpora.

### Rule 3: sidecar tables are the right migration path

Future tables should be additive:

- `capture_source_identity`;
- `evidence_bundle`;
- `capture_understanding`;
- `retrieval_chunk_local`;
- `user_feedback_episode`;
- `approval_request`;
- `memory_candidate`;
- `promoted_memory_fact`;
- `agent_run` only later.

This avoids rewriting existing capture rows and keeps Android migration risk bounded.

### Rule 4: deep extraction should mostly be cloud-side

Android can do local capture, OCR, URL hydration coordination, and display. It should not be expected to run a reliable background browser renderer. Browser fallback, dynamic-page extraction, transcripts, and heavy VLM should be cloud/server capabilities with explicit user controls.

### Rule 5: user-invoked agent flows fit Android better than autonomous background agents

Android background restrictions, battery policy, foreground service rules, and notification requirements all favor user-invoked flows:

- capture detail -> ask/draft;
- Ask Orbit -> plan;
- approval sheet -> local execution;
- WorkManager for bounded enrichment.

Autonomous scheduled agents should remain out of scope until the coordinator has proven value.

## Verification gaps to fix before implementation

1. **Connected network gateway tests**: localhost is blocked by URL validation and cleartext policy. Use a test-specific HTTPS mock, a loopback allowlist only in androidTest, or move these to JVM tests with fake OkHttp where appropriate.
2. **Connected migration tests**: package exported Room schemas into androidTest assets so `MigrationTestHelper` can load `1.json`, `3.json`, etc.
3. **Local/Nano capability detection**: `LlmProviderRouter` still stubs hardware detection to `false`.
4. **Source identity fallback**: remove category-only `video -> youtube` once provider-first identity lands.
5. **Future multiprocess settings**: one boolean in SharedPreferences is acceptable today; Basic/Smart/Deep and cloud controls should use a repository-backed or otherwise coherent cross-process source of truth.
6. **Binder contract design**: new Round 2 objects need AIDL/Room boundaries that pass references, not large raw payloads.

## Final assessment

The planned architecture is compatible with Android if it is implemented as an Android-native, Binder-and-Room architecture rather than as a web/cloud agent copied onto a phone.

The strongest existing proof points are:

- current Android build/test/lint gate passes;
- process split already exists;
- AIDL boundaries already exist;
- SQLCipher Room is already in place;
- cloud LLM routing already goes through `INetworkGateway`;
- local action execution already runs in `:capture`;
- custom lint already blocks HTTP clients outside `app/net`;
- source identity and provider metadata hooks already exist.

The biggest correction is conceptual, not structural: do not claim Android grants `INTERNET` only to `:net`. Claim that Orbit enforces `:net` as the only network code path. With that wording and the gates above, the Round 1-6 plan can work on real Android devices.
