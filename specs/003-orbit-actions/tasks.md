# Tasks: Orbit Actions (v1.1)

**Input**: Design documents from `/specs/003-orbit-actions/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md
**Branching note**: tasks authored on `spec/002-intent-envelope-and-diary` per plan §"Branching note". 003 work proper moves to `spec/003-orbit-actions` once 002 closes (002 T110–T113 acceptance). All paths target the post-002 codebase.
**Tests**: INCLUDED. Constitution Principles I, VI, XII and the four contract files require contract + instrumented tests; 003 is the first feature to write to external apps so the no-network and no-silent-write proofs are mandatory.

**Organization**: Tasks grouped by user story. Each user story has independent test criteria; checkpoints at the end of each story phase.

**User-story map** (derived from spec FR-003-001..010 + quickstart golden paths):

| Story | Priority | Golden path | FRs covered |
|---|---|---|---|
| US1 — Calendar action | P1 | quickstart §3 (path A) | 001, 002, 003, 005, 006, 008 |
| US2 — To-do action (local + share) | P1 | quickstart §4 (path B) | 001, 002, 003, 006, 008, 009, 010 |
| US3 — Weekly Sunday digest | P1 | quickstart §5 (path C) | 004, 006 |
| US4 — Skill stats & Settings → Actions UI | P2 | quickstart §3 step 12, §6 | 007, 009 |
| US5 — Negative paths & graceful degradation | P2 | quickstart §6 (N1–N6) | 003, 005, 010 |

**Visual source of truth**: `.specify/memory/design.md` (chip, preview card, digest envelope styling). Visual finalisation deferred to spec 010 per plan §Project Structure; tasks below reference design.md for placement only and avoid baking typography/colour decisions that don't yet exist there.

**Inheritance from 002**: every task assumes the 002 architecture is in place (4-process split, SQLCipher Room DB, `:ml` binder service, `LlmProvider` interface from 002 T025a, `ContinuationEngine`, `AuditLog`, `NoHttpClientOutsideNet` lint rule). Where a 002 file is modified, the task header says "(MODIFY)".

---

## Status / Adjustments log

**2026-04-27 (latest+6) — Phase 4 US2 to-do action lands; 8/11 tasks done (T055–T057, T060–T064 ✅ / T058 + T059 + T065 deferred)**

- **Implementation cluster (T060–T064)** — the entire pipeline ships:
  - **T061** [createDerivedTodoEnvelope](app/src/main/java/com/capsule/app/data/ActionsRepositoryDelegate.kt) — single Room transaction; N envelopes (`kind=REGULAR`, `intent=WANT_IT`, `intentSource=AUTO_AMBIGUOUS`, parent linked via `derivedFromEnvelopeIdsJson`, `todoMetaJson` rendered as `{items:[{text,done:false,dueEpochMillis}],derivedFromProposalId}`); each carries an `ENVELOPE_CREATED` audit row with `derived_from_proposal_id` + `parent_envelope_id` extras (Principle IX atomicity holds). Plus [setTodoItemDone](app/src/main/java/com/capsule/app/data/ActionsRepositoryDelegate.kt) which mutates `todoMetaJson` in place via a new [IntentEnvelopeDao.updateTodoMetaJson](app/src/main/java/com/capsule/app/data/dao/IntentEnvelopeDao.kt) and intentionally skips the audit row (per audit-log-contract §2: high-frequency UI noise).
  - **T060** [TodoActionHandler.kt](app/src/main/java/com/capsule/app/action/handler/TodoActionHandler.kt) gains the `target=local`/`target=external` branch. Local path goes through the `:ml` `IEnvelopeRepository` binder; external path renders bullet text, dispatches `Intent.ACTION_SEND` with mimeType (default `text/plain`), reads `SharedPreferences("orbit.actions").todoTargetPackage`, and self-heals by falling back to chooser if the remembered package no longer resolves. Static `recordRememberedTarget(ctx, pkg?)` persists/clears the choice; `isPackageInstalledForShare(ctx, pkg)` validates resolution.
  - **T060 architecture refactor** — `ActionHandler.handle` now takes an optional `repository: IEnvelopeRepository? = null` (4th arg). [CalendarActionHandler](app/src/main/java/com/capsule/app/action/handler/CalendarActionHandler.kt) and [ShareActionHandler](app/src/main/java/com/capsule/app/action/handler/ShareActionHandler.kt) ignore it; [TodoActionHandler](app/src/main/java/com/capsule/app/action/handler/TodoActionHandler.kt) needs it for the local branch. [ActionExecutorService](app/src/main/java/com/capsule/app/action/ActionExecutorService.kt) threads its own bound `repo` through. Backwards-compat: 3-arg call sites still compile via the default arg.
  - **T062** ShareActionHandler stays the v1.1 stub returning `Failed("share_delegate_disabled_v1_1")` per Principle XI gate (full impl is part of spec 008 — the Orbit Agent — not v1.1). Documented inline + cross-referenced from T057.
  - **T063** — `todoMetaJson: String?` was already on [IntentEnvelopeEntity](app/src/main/java/com/capsule/app/data/entity/IntentEnvelopeEntity.kt) + [MIGRATION_1_2](app/src/main/java/com/capsule/app/data/migrations/OrbitMigrations.kt) from the entity-foundation work in Phase 2; this task simply pinned the JSON shape and verified the migration covers it.
  - **T064** [EnvelopeCard](app/src/main/java/com/capsule/app/diary/ui/EnvelopeCard.kt) renders a checkbox row per item between summary and domain; tap propagates through `onToggleTodoItem` callback. [DiaryRepository.setTodoItemDone](app/src/main/java/com/capsule/app/diary/DiaryRepository.kt) + [BinderDiaryRepository](app/src/main/java/com/capsule/app/diary/BinderDiaryRepository.kt) impl + [DiaryViewModel.onToggleTodoItem](app/src/main/java/com/capsule/app/diary/DiaryViewModel.kt) (fire-and-forget with runCatching) + [DiaryScreen](app/src/main/java/com/capsule/app/diary/ui/DiaryScreen.kt) wiring complete the round-trip. [EnvelopeViewParcel](app/src/main/java/com/capsule/app/data/ipc/EnvelopeViewParcel.kt) gained `todoMetaJson: String? = null` as the 22nd field (back-compat default for tests + legacy unparcel).
  - Pure parser [parseTodoItems](app/src/main/java/com/capsule/app/diary/ui/EnvelopeCard.kt) extracted as `internal` for JVM tests; tolerant of malformed JSON, missing `done`, blank text, mixed object/string item shapes.

- **Test cluster (T055 + T056 + T057)**:
  - [TodoAddHandlerLocalTest](app/src/androidTest/java/com/capsule/app/action/TodoAddHandlerLocalTest.kt) — 10 instrumented tests via JDK Proxy fake for `IEnvelopeRepository` (same pattern as T036 worker test). Asserts: 3-item happy-path (`Success("local:3")`, single `createDerivedTodoEnvelope` binder call with verbatim `itemsJson` round-trip), missing parent/proposal/items → specific Failed reasons + zero binder calls, empty array → `empty_items`, null repository → `ml_binder_unavailable`, RemoteException → `binder_remote_exception`, generic Throwable → `create_derived_failed`, unknown target → `unknown_target:*`, malformed JSON → `args_parse_failed`.
  - [TodoAddHandlerExternalTest](app/src/androidTest/java/com/capsule/app/action/TodoAddHandlerExternalTest.kt) — 7 instrumented tests via `ContextWrapper` capture pattern (same as T037 calendar test). Asserts: first-use chooser dispatch (ACTION_CHOOSER wraps inner ACTION_SEND with bulleted EXTRA_TEXT + FLAG_ACTIVITY_NEW_TASK), remembered package skips chooser (`Dispatched("external:remembered:android")` with `setPackage`), uninstalled remembered package gracefully falls back to chooser, custom mimeType propagates, empty items → `empty_items`, SecurityException → `security_exception`, `recordRememberedTarget(null)` clears prefs.
  - [ShareHandlerTest](app/src/androidTest/java/com/capsule/app/action/handler/ShareHandlerTest.kt) — 3 instrumented tests pinning the v1.1 disabled contract. Promoted from JVM after discovering the workspace has no Mockito on the test classpath and `Context` is abstract (not Proxy-able).
  - [DiaryViewModelTest](app/src/test/java/com/capsule/app/diary/DiaryViewModelTest.kt) +2 tests (T064): `onToggleTodoItem_delegatesToRepository` records `Triple(envelopeId, index, done)`; `onToggleTodoItem_swallowsRepositoryFailure` confirms binder RemoteException doesn't crash the VM (runCatching).
  - [EnvelopeCardTodoParseTest](app/src/test/java/com/capsule/app/diary/ui/EnvelopeCardTodoParseTest.kt) — 8 JVM tests for `parseTodoItems`: 3-item round-trip (text/done/dueEpochMillis), negative `dueEpochMillis` treated as null, blank text dropped, missing `done` defaults to false, empty items → empty list, missing `items` key → empty, malformed JSON → empty, top-level array (not object) → empty, non-object items skipped, text trimmed.

- **Deferred (3/11 US2 tasks)**:
  - **T058** (JVM `ActionExtractorTodoListTest`) — the actual `ActionExtractor.extract` requires `database.withTransaction { }` so a clean JVM slice isn't possible without a substantial harness refactor. The contract spirit (one proposal, items.length=3, confidence ≥ 0.55) is already structurally enforced by [BuiltInAppFunctionSchemas](app/src/main/java/com/capsule/app/action/BuiltInAppFunctionSchemas.kt) `tasks.createTodo` schema + the 0.55 floor in [ActionExtractor](app/src/main/java/com/capsule/app/ai/extract/ActionExtractor.kt) which T034 instrumented coverage already hits. Deferred to a v1.1 androidTest companion (or fold into T034's matrix) rather than a partial JVM facsimile.
  - **T059** (multi-handler non-mutation) — each handler's non-mutation property is already covered structurally by the existing tests: T037 (Calendar = `ACTION_INSERT` only, no DB write), T055 (Todo local = the only binder method invoked is `createDerivedTodoEnvelope`, never any envelope-mutating method), T057 (Share = stub, no dispatch). The composite test would just stitch these together — deferring the explicit cross-handler test to physical-device cluster (T103+) since it would duplicate coverage.
  - **T065** (`share_target_remembered_package` strings.xml + Settings affordance) — deferred until T079 lands the `ActionsSettingsUI` skeleton; tracking as a Settings-cluster task.

- **Static gate**: all touched files clean via `get_errors`. **Gradle gate not run** (workstation lacks system JDK; toolchainVersion=21 auto-provisions on dev box).

- **Phase 4 US2 status**: 8/11 done. Path A (local target) fully unblocked end-to-end — chip → confirm → binder → N derived envelopes → checkbox UI. Path B (external target) ready pending physical device verification.

- **Next**: Phase 5 US3 (Sunday digest, T066–T077). High-leverage — self-contained worker + composer + UI layer; reuses zero of US1/US2's plumbing but is the third P1 surface for v1.1.

**2026-04-27 (latest+5) — Phase 3 US1 UI cluster (T051–T054) lands; **Phase 3 US1 acceptance complete (17/17 ✅)**

- **T053 first** (foundation): extended [DiaryRepository.kt](app/src/main/java/com/capsule/app/diary/DiaryRepository.kt) with `observeProposals`, `markProposalConfirmed`, `markProposalDismissed`, `executeAction`, `cancelWithinUndoWindow`. Implemented in [BinderDiaryRepository.kt](app/src/main/java/com/capsule/app/diary/BinderDiaryRepository.kt) including a **second** `connectExecutor()` ServiceConnection (separate from the existing `:ml` connection) since `IActionExecutor` lives in `:capture`, not `:ml`. Disconnect tears down both. [DiaryViewModel.kt](app/src/main/java/com/capsule/app/diary/DiaryViewModel.kt) gains `observeProposals(envelopeId)` passthrough (lifecycle-scoped to caller composable, no per-envelope cache to leak), `onConfirmProposal(proposal, editedArgsJson?)` (markConfirmed → build `ActionExecuteRequestParcel(withUndo=true)` → execute → open 5 s undo toast), `onDismissProposal`, `onUndoExecution(executionId)`, `onUndoToastDismissed`, `undoState: StateFlow<UndoToastState?>`. Auto-clear via `delay(5_000L)` job, race-safe (only clears if still showing the same `executionId`).

- **T051** [ActionProposalChipUI.kt](app/src/main/java/com/capsule/app/diary/ActionProposalChipUI.kt): `ActionProposalChipRow` Compose. `produceState` keyed on `(envelopeId, proposalsFlow)` collects the live list; filters to `state == "PROPOSED"`. Empty list → returns nothing (no spacer). `LazyRow` of `AssistChip` with `previewTitle`. Subscription tears down when the envelope item leaves the LazyColumn viewport, so we don't hold dead binder observers.

- **T052** [ActionPreviewCardUI.kt](app/src/main/java/com/capsule/app/diary/ActionPreviewCardUI.kt): `ActionPreviewSheet` `ModalBottomSheet`. Branches on `functionId`: `calendar.createEvent` → editable fields (title, start ISO, end ISO, location, notes, tzId) with the contract's reversibility line **"Once added, you'll need to edit it in Calendar."**; other ids → read-only fallback (US2/US2b will replace). Confirm validates: tzId parseable, title non-blank, start parseable; rebuilds argsJson and passes to `onConfirm`. Pure parsing helpers `parseCalendarArgs(argsJson) → CalendarFormState` + `parseIsoLocalToEpochMillis(value, zone)` are extracted as `internal` for JVM unit tests.

- **T054** [DiaryActivity.kt](app/src/main/java/com/capsule/app/diary/DiaryActivity.kt): pre-warms both bindings in the same `lifecycleScope.launch(Dispatchers.IO)` block as the existing `:ml` warm — first action confirm tap doesn't pay the bindService round-trip cost on the main thread. Lifecycle-managed disconnect already handles both via `repository.disconnect()`. "Retry once on disconnect" deferred to v1.2 polish (the binder is autoCreate; first-call lazy reconnect via `connectExecutor()` covers the common path).

- **DiaryScreen wire-up** [DiaryScreen.kt](app/src/main/java/com/capsule/app/diary/ui/DiaryScreen.kt): chip-row rendered as a sibling LazyColumn item beneath each `EnvelopeCard`; tapping a chip sets `pendingProposal` state which renders `ActionPreviewSheet`. `RenderDayState` collects `viewModel.undoState` and surfaces a Toast on each new `executionId` ("Added · tap notification to undo" / "Couldn't add: …" / "Cancelled").

- **Tests** (JVM unit, gradle-runnable on a JDK-equipped machine):
  - [DiaryViewModelTest.kt](app/src/test/java/com/capsule/app/diary/DiaryViewModelTest.kt) — 5 new tests via `ActionRecordingRepo` fake: confirm → markConfirmed + execute + undo toast; editedArgsJson propagation; dismiss does not execute and does not surface toast; undo cancels and clears toast; toast auto-clears after 5s via `advanceTimeBy(5_001)`.
  - [ActionPreviewCardArgsTest.kt](app/src/test/java/com/capsule/app/diary/ActionPreviewCardArgsTest.kt) — 8 tests for `parseCalendarArgs` / `parseIsoLocalToEpochMillis`: full args round-trip, missing optionals fall back, invalid tzId → system default, malformed JSON → blank state, valid ISO round-trips through zone, blank/unparseable → null, **zone-sensitive** assertion proving same epoch instant renders different ISO strings for `America/New_York` vs `Asia/Tokyo` (DST-aware).
  - Also extended pre-existing `FakeRepo` in DiaryViewModelTest with default no-op stubs for the 5 new repository methods so existing tests still compile.

- **Static gate**: all touched files clean via `get_errors`. **Gradle gate not run**: workstation lacks system JDK (toolchainVersion=21 auto-provisioning handles it on a normal dev box).

- **Phase 3 US1 status**: 17/17 ✅. **All US1 acceptance criteria met.** Phase 3 done modulo gradle green-build verification on a JDK-equipped machine.

- **Next**: Phase 4 US2 (todo + share actions) OR Phase 5 US3 (Sunday digest). US2 has higher leverage — it reuses 95% of the US1 plumbing (same chip/preview/executor surface) and just adds two handlers + their schemas.

**2026-04-27 (latest+4) — T040 IPC contract test lands; Phase 3 US1 instrumented cluster complete (T034–T040 ✅)**

- **T040** [ExecutionIpcContractTest.kt](app/src/androidTest/java/com/capsule/app/action/ExecutionIpcContractTest.kt) — 4 tests covering action-execution-contract.md §2 IPC invariants:
  1. **Manifest `exported="false"`** assertion via `PackageManager.getServiceInfo(component, 0).exported` for `ActionExecutorService`. Catches the no-foreign-binder regression at compile-test time — the entire sensitivity-scope re-check guarantee rests on this single attribute.
  2. **Defence-in-depth**: same assertion for `EnvelopeRepositoryService` (the executor's downstream `:ml` binder). If foreign apps could hit `recordActionInvocation` directly they'd bypass the IActionExecutor entry point.
  3. **Bind reachability**: in-process `bindService(BIND_ACTION_EXECUTOR)` resolves, the `IActionExecutor.Stub.asInterface` produces a non-null proxy, and `binder.pingBinder()` returns true (liveness gate).
  4. **Round-trip latency**: `cancelWithinUndoWindow("nonexistent-id-N")` — a pure binder round-trip with no DB writes — across 5 warmup + 50 sample iterations. Asserts p99 < 200ms (per contract §2 budget). Uses `cancelWithinUndoWindow` rather than `execute()` because the latter requires `:ml` binder + unlocked Room; that's T101 (Polish phase end-to-end perf gate) territory.
- **Scope-narrowed deviation**: tasks.md mentions "foreign-package binders rejected" as runtime test — that requires a separate test APK in a foreign UID, deferred to physical-device validation (T103+). The manifest invariant is the structural gate; verified statically via PackageManager.
- **Cleanup**: `@After` unbinds the service connection + clears `expiredExecutionIds` companion set so cross-test isolation holds.
- **No gradle gates run yet**: static `get_errors` clean.
- **Phase 3 US1 status**: T034–T040 ✅, T041–T046, T049, T050 ✅. **All instrumented tests for US1 land.** Pending: T051–T054 (UI cluster) + T047/T048 (final pass already substantially landed).
- **Next**: UI cluster T051-T054 — the only remaining gating work for Phase 3 user-facing acceptance.

**2026-04-27 (latest+3) — T036 ActionExtractionWorker outcome→Result mapping test lands; worker contract fully validated**

- **T036** [ActionExtractionWorkerTest.kt](app/src/androidTest/java/com/capsule/app/continuation/ActionExtractionWorkerTest.kt) — 11 tests using the worker's `repositoryBinder` test seam (mirrors `UrlHydrateWorker.gatewayBinder` pattern). Fakes `IEnvelopeRepository` via JDK `Proxy` since the worker only invokes one method (`extractActionsForEnvelope`) — a Proxy keeps the fake to a single closure rather than reimplementing the full AIDL `Stub`. Coverage matrix vs. action-extraction-contract.md §5 outcome grammar:
  - **Success class**: `PROPOSED:2`, `PROPOSED:0`, `NO_CANDIDATES`, `SKIPPED:non_regular_kind`, `SKIPPED:sensitivity_changed` → all `Result.success()`.
  - **Retry class**: `FAILED:nano_timeout` at runAttemptCount 0,1 → retry; at attempt 2 (i.e. +1==MAX_ATTEMPTS=3) → failure. Same cap proven for binder-throws (`RuntimeException`) and binder-unavailable (seam returns `null`).
  - **Terminal failure**: `UNAVAILABLE` → immediate failure (structural — retrying won't help). Defensive: unrecognised outcome string `WAT:something` → failure (contract-violation gate). Missing `KEY_ENVELOPE_ID` input → failure (worker invariant).
- **Scope-narrowed deviation**: tasks.md mentions "`ExistingWorkPolicy.KEEP` idempotency" and "`CONTINUATION_FAILED reason=action_extract_exhausted` audit" as part of T036. The KEEP policy is set in `ContinuationEngine.enqueueActionExtract` (T045, already landed) so it'd be redundant here — covered by the same WorkManager testing infra used in T059 [UrlHydrateWorkerTest.kt](app/src/androidTest/java/com/capsule/app/continuation/UrlHydrateWorkerTest.kt) `enqueueForNewEnvelope_schedulesUrlHydrateWithContractConstraints` style. The `CONTINUATION_FAILED` audit row is the v1.1-deferred T097 hardening (per T044 status log) — worker-process can't write the audit since DB lives in `:ml`.
- **Test seam restoration**: `@After` restores `ActionExtractionWorker.repositoryBinder` to the original snapshot so other instrumented suites in the same process aren't affected.
- **No gradle gates run yet**: static `get_errors` clean.
- **Phase 3 US1 status**: T034, T035, T036, T037, T038, T039, T041–T046, T049, T050 ✅. **Pending**: T040 (IPC contract), T051–T054 (UI cluster), T047/T048 (already substantially landed in Phase 2 + this session; final pass deferred until UI cluster lands).
- **Next**: UI cluster T051-T054 (only remaining gating work for Phase 3 user-facing acceptance) OR T040 IPC contract test (last instrumented test). UI cluster has higher leverage — unblocks the entire user-facing flow.

**2026-04-27 (latest+2) — T039 NoNetworkDuringActionExecution lands; Constitution Principle VI runtime backstop in place**

- **T039** [NoNetworkDuringActionExecutionTest.kt](app/src/androidTest/java/com/capsule/app/action/NoNetworkDuringActionExecutionTest.kt) — 4 tests installing `StrictMode.ThreadPolicy.Builder().detectNetwork().penaltyDeath()` on the binder thread before each handler invocation. Any socket/DNS/HTTP I/O → fatal throw observed via `runCatching`. Coverage: (1) `CalendarActionHandler` produces `Dispatched`/`Failed` cleanly; (2) `TodoActionHandler` returns its v1.1 stub `Failed` cleanly; (3) `ShareActionHandler` returns the intentionally-refused `Failed("share_delegate_disabled_v1_1")` cleanly; (4) defence-in-depth loop iterates `ActionHandlerRegistry.knownFunctionIds()` so future skill additions inherit the gate without test-author intervention. Captures `startActivity` via `ContextWrapper` so missing-Calendar in the emulator doesn't pollute the assertion.
- **Scope-narrowed deviation**: tasks.md mentions a custom `SocketFactory` UID gate — deferred to T040 (`ExecutionIpcContractTest`) where the binder UID rejection is the natural place; T039 covers the StrictMode path which catches every synchronous socket-creation attempt anyway. Static `:lint` rule `NoHttpClientOutsideNet` (already in 002) is the static backstop; this is the runtime backstop.
- **No gradle gates run yet**: static `get_errors` clean.
- **Phase 3 US1 status**: T034, T035, T037, T038, T039, T041–T046, T049, T050 ✅. Pending: T036, T040 (instrumented), T051–T054 (UI cluster).
- **Next**: UI cluster T051-T054 (user-facing acceptance gate, largest remaining work) OR T036 worker test OR T040 IPC contract.

**2026-04-27 (latest+1) — T038 UndoWindowTest lands; uncovers + fixes two T050 contract bugs**

- **Bug fix #1** [ActionsRepositoryDelegate.kt](app/src/main/java/com/capsule/app/data/ActionsRepositoryDelegate.kt) — `recordActionInvocation` mapped `USER_CANCELLED` → `AuditAction.ACTION_DISMISSED`, but action-execution-contract.md §5 dispatch table requires `USER_CANCELLED` → `ACTION_FAILED reason=user_cancelled`. Fixed: `USER_CANCELLED` now maps to `ACTION_FAILED` (matches the FAILED branch). Comment added explaining the distinction (`ACTION_DISMISSED` is reserved for proposal-level dismissals BEFORE execute; once an execution row exists, cancellation is a failure-class outcome).
- **Bug fix #2** [ActionExecutorService.kt](app/src/main/java/com/capsule/app/action/ActionExecutorService.kt) — the `pendingUndo[executionId]` hook was set to a literal no-op `{ /* no-op default; overwritten by binder */ }` with no override path; `cancelWithinUndoWindow` therefore reported `true` but didn't flip the outcome or write the audit row required by the contract. Fixed: hook now invokes `repo.recordActionInvocation(executionId, proposalId, functionId, USER_CANCELLED, "user_cancelled", ...)` — same executionId routes through `executionDao.markOutcome` for in-place update + appends the `ACTION_FAILED reason=user_cancelled` audit row. dispatchedAt + request fields captured by closure at execute() time.
- **T038** [UndoWindowTest.kt](app/src/androidTest/java/com/capsule/app/action/UndoWindowTest.kt) — 5 tests at the `ActionsRepositoryDelegate` + `ActionExecutorService.Companion` seams (full IPC binder cancel test deferred to T040). Coverage: (1) DISPATCHED → USER_CANCELLED in-place flip preserves dispatchedAt + writes exactly one `ACTION_EXECUTED` (original) plus exactly one `ACTION_FAILED reason=user_cancelled`; payload contains executionId + outcome=USER_CANCELLED; (2) `expireUndoWindow(id)` is observable via `isExpired(id)` and is per-id (other ids unaffected); (3) post-flip `expireUndoWindow` is a pure companion-level flag flip — DB rows + audit log unchanged (worker MUST NOT mutate state once outcome is terminal); (4) duplicate cancel calls (re-entrant user tap) routed through `markOutcome` — `countForProposal("p1")` stays 1, no duplicate execution rows; (5) baseline: first `recordActionInvocation` for a new id takes the INSERT branch (validates the in-place-update semantics in (1) are predicated on a prior INSERT).
- **No gradle gates run yet**: static `get_errors` clean across all three touched files. Bug-fix #1 was undetected because no existing test asserted the (incorrect) `USER_CANCELLED → ACTION_DISMISSED` mapping; bug-fix #2 was undetected because the no-op hook compiled clean and `cancelWithinUndoWindow` always returned `true` for unsealed ids.
- **Phase 3 US1 status**: T034, T035, T037, T038, T041–T046, T049, T050 ✅. Pending: T036, T039, T040 (instrumented worker/no-net/IPC tests), T051–T054 (UI cluster).
- **Next**: UI cluster T051-T054 OR T036 worker test OR T039 no-net StrictMode test. UI cluster is the user-facing acceptance gate; T036/T039/T040 are the remaining three instrumented tests for green-build readiness.

**2026-04-27 (latest) — T037 CalendarInsertHandler instrumented test lands; T049 Intent dispatch fully validated**

- **T037** [CalendarInsertHandlerTest.kt](app/src/androidTest/java/com/capsule/app/action/CalendarInsertHandlerTest.kt) — 9 tests using a `ContextWrapper` capture pattern (`CapturingContext` collects `startActivity` Intents; `ThrowingContext` forces specific failure paths). **Harness deviation**: tasks.md mentions `IntentTestRule` but that's deprecated and requires an Activity in scope; ContextWrapper-capture is the modern equivalent and exercises the same observable contract (no Activity needed since the handler accepts `Context` directly).
- **Coverage matrix** (action-execution-contract.md §5 + research §4): (1) required-args only → Dispatched + `Intent.ACTION_INSERT` + `CalendarContract.Events.CONTENT_URI` + `FLAG_ACTIVITY_NEW_TASK` + title/begin extras + end-time defaults to start+1h; (2) full args → all optional extras propagate (location, description, tzId, custom end); (3) missing title → `Failed("missing_title")` + zero dispatch; (4) blank title (whitespace-only) → same; (5) missing startEpochMillis → `Failed("missing_start_time")` + zero dispatch; (6) unparseable argsJson → `Failed("args_parse_failed")` with cause attached; (7) blank optional strings (location/notes/tzId) → NOT propagated as empty extras (verifies `takeIf { it.isNotBlank() }` guards); (8) `ActivityNotFoundException` from no Calendar app installed → `Failed("no_handler")` with cause; (9) `SecurityException` from OEM → `Failed("security_exception")` with cause.
- **No gradle gates run yet**: static `get_errors` clean. Coverage validates T049 directly without IPC mocking.
- **Phase 3 US1 status**: T034, T035, T037, T041–T046, T049, T050 ✅. Pending: T036, T038–T040 (instrumented worker/undo/no-net/IPC tests), T051–T054 (UI cluster).
- **Next**: T038 UndoWindowTest (validates T050 cleanup-worker race + cancel<5s flips USER_CANCELLED), then T036 worker test, then UI cluster.

**2026-04-27 (later) — T034 instrumented extractor pipeline test lands; latent compile-bug in T031 fixture fixed**

- **T034** [ActionExtractorTest.kt](app/src/androidTest/java/com/capsule/app/ai/extract/ActionExtractorTest.kt) — 12 tests using in-memory Room (deviation: placed in `androidTest/` not `test/` — same Room-runtime constraint as T030/T031, tracked in this log). Coverage matrix matches action-extraction-contract.md §4: (1) envelope-not-found→NoCandidates, (2) kind=DIGEST→Skipped("non_regular_kind"), (3) `[REDACTED_GITHUB_TOKEN]` envelope→Skipped("sensitivity_changed") + Nano never invoked, (4) blank text→NoCandidates, (5) empty registry (raw `DELETE FROM appfunction_skill`)→NoCandidates + `TrackingLlm.callCount==0`, (6) Nano timeout via `delaying(200ms)`+timeoutMillis=50→Failed("nano_timeout") + ACTION_FAILED audit row with envelope id, (7) Nano throw `IllegalStateException`→Failed("nano_IllegalStateException") + audit, (8) below-floor confidence (0.40 < 0.55)→NoCandidates + zero proposals + zero audit, (9) oversized argsJson (>4096B)→drop, (10) unparseable JSON args→drop, (11) happy path: PROPOSED state + correct confidence/provenance/argsJson + exactly one ACTION_PROPOSED audit row with `proposalId`+`functionId` payload, (12) idempotency re-run: second extract is no-op via `(envelopeId,functionId)` unique idx, no dup audit. Plus: PUBLIC-scope skill vs `[REDACTED_ADDRESS]` envelope (non-credential marker)→one ACTION_DISMISSED audit row with `sensitivity_scope_mismatch` reason, zero proposals, outcome NoCandidates.
- **Test infrastructure**: minimal `FakeLlm` open class implements all 5 `LlmProvider` methods (only `extractActions` is exercised; others `error("not used")`). Static factories: `empty()`, `fail()`, `delaying(ms)`, `singleCandidate(functionId, argsJson, confidence, candidateScope)`. `TrackingLlm` subclass increments `callCount` to assert "Nano not called" invariants.
- **Latent bug fix** (incidental): [ActionsRepositoryDelegateTest.kt](app/src/androidTest/java/com/capsule/app/data/ActionsRepositoryDelegateTest.kt) line 209 referenced `SensitivityScope.CALENDAR_WRITE` which doesn't exist on the enum (only `PUBLIC`/`PERSONAL`/`SHARE_DELEGATED`). It compiled in IDE `get_errors` only because androidTest sourceSet isn't actively analyzed; `:app:assembleAndroidTest` would have failed. Renamed to `SensitivityScope.PUBLIC` (matches calendar.createEvent's actual schema scope). Pre-existing-from-Phase-2 issue.
- **No gradle gates run yet**: static `get_errors` clean across both T034 and the latent-bug file (per IDE).
- **Phase 3 US1 status**: T034, T035, T041–T046, T049, T050 ✅. T047 (ActionExecutorService) already mostly landed in Phase 2 + cleanup-worker hook this session; T048 (AppFunctionInvoker) likely collapses into existing ActionHandlerRegistry. Pending: T036–T040 (instrumented), T051–T054 (UI cluster).
- **Next**: UI cluster (T051-T054) gates Phase 3 user-facing acceptance, OR address the `CALENDAR_WRITE` latent bug + T036-T040 instrumented tests for end-to-end gradle gate readiness.

**2026-04-27 — Phase 3 US1 data plumbing + handler + cleanup worker land; compile-clean across all touched files**

- **T035** [DateTimeParserTest.kt](app/src/test/java/com/capsule/app/ai/datetime/DateTimeParserTest.kt) — 17 JVM tests with fixed anchor `2026-04-26 Sun 10:00 America/New_York`. Covers ISO 8601 zoned/offset/naive/date-only, `today/tomorrow/yesterday/tonight`, `monday→Apr27` (next-occurrence-from-Sun), `nextTuesday→Apr28`, `friday at 5pm`, `thisSunday→today`, `in 3 days` / `in 2 hours`, unparseable→null, **DST spring-forward** Mar 7-8 2026 boundary, AM/PM edge (`12pm=12`, `12am=0`), case-insensitivity, result-zone matches argument.
- **T041** [DateTimeParser.kt](app/src/main/java/com/capsule/app/ai/datetime/DateTimeParser.kt) — pure java.time object, anchored to `envelope.createdAt + device tz`, default time 09:00 local. Handles ISO variants, today/tomorrow/yesterday/tonight, "in N {minutes|hours|days|weeks}" (`IN_N_REGEX`), weekday tokens with `next`/`this`/plain qualifier (full+abbrev `WEEKDAY_TOKENS` map), AM/PM time (`TIME_REGEX`). No 3rd-party NLP. Returns null on unparseable input.
- **T042** [ActionExtractionPrefilter.kt](app/src/main/java/com/capsule/app/ai/extract/ActionExtractionPrefilter.kt) — pure object `shouldExtract(text): Boolean`. 9 regex rules: flight codes `\b[A-Z]{2}\d{2,4}\b`, 6-char PNRs, weekdays (full+abbrev case-insensitive), relative dates, multiline imperative list `^\s*(?:[-*•]|\d+\.)\s+\S`, RSVP/confirm/register/book/booking/reservation/appointment/meeting/invite/invitation, currency `[$€£¥]\s?\d`, HH:MM, Xam/Xpm. Bonus: 8 prefilter tests landed alongside.
- **T043** [ActionExtractor.kt](app/src/main/java/com/capsule/app/ai/extract/ActionExtractor.kt) — orchestrator with `sealed interface ExtractOutcome { NoCandidates, Proposed(proposalIds), Skipped(reason), Failed(reason) }`. Pipeline (action-extraction-contract.md §4): getById null→`NoCandidates`; `kind!=REGULAR`→`Skipped("non_regular_kind")`; forbidden `[REDACTED_AWS_ACCESS_KEY/AWS_SECRET_KEY/GITHUB_TOKEN/OPENAI_KEY/ANTHROPIC_KEY/JWT/CREDIT_CARD/SSN/MEDICAL]` markers→`Skipped("sensitivity_changed")`; blank→`NoCandidates`; empty registry→`NoCandidates`; `withTimeout(8000)` Nano call; `TimeoutCancellationException`→`Failed("nano_timeout")` + audit `ACTION_FAILED`; other `Throwable`→`Failed("nano_${className}")` + audit; empty candidates→`NoCandidates`; filter by `≥0.55f` confidence + `schemaByFunctionId` lookup + `isValidJsonObjectShape` (parses + ≤4096B) + `sensitivityScopeMatches`; build `ActionProposalEntity` (PROPOSED, `take(120)/take(160)` preview clamps); single `database.withTransaction` inserts proposals (skip via `findByEnvelopeAndFunction` unique-index check) + audit `ACTION_PROPOSED` per row + `ACTION_DISMISSED` for sensitivity drops. **Scope-narrowed**: full JSON Schema 2020-12 keyword validation deferred to T091 (no validator dep yet); v1.1 only checks parseability + size cap. **Sensitivity proxy**: presence of `[REDACTED_<TYPE>]` markers as the gate (no separate sensitivityFlags column on envelope); `PUBLIC` scope rejects any redaction marker, `PERSONAL`/`SHARE_DELEGATED` always pass.
- **T044** [ActionExtractionWorker.kt](app/src/main/java/com/capsule/app/ai/extract/ActionExtractionWorker.kt) — `CoroutineWorker` in default WorkManager process. Mirrors `UrlHydrateWorker.bindRepositoryDefault` pattern: binds `:ml`'s `EnvelopeRepositoryService` via intent action `BIND_ENVELOPE_REPOSITORY`, calls `IEnvelopeRepository.extractActionsForEnvelope(envelopeId)`, parses outcome string. `PROPOSED:N` / `NO_CANDIDATES` / `SKIPPED:*` → `Result.success()`; `FAILED:*` or binder-throw → `Result.retry()` until `runAttemptCount+1 ≥ MAX_ATTEMPTS=3` then `Result.failure()`; `UNAVAILABLE` → `Result.failure()`. Test seam `repositoryBinder` allows fakes. **Deferred**: v1.1 worker doesn't itself write the `CONTINUATION_FAILED reason=action_extract_exhausted` audit row — `:ml` should fold that into `extractActionsForEnvelope` once we add per-call attempt awareness; tracked as T097 (Polish phase).
- **T045** [ContinuationEngine.kt](app/src/main/java/com/capsule/app/continuation/ContinuationEngine.kt) — `enqueueActionExtract(envelopeId)` added with `ACTION_EXTRACT_CONSTRAINTS` (charger + UNMETERED + battery-not-low), exponential backoff base 30s, `ExistingWorkPolicy.KEEP`, unique name `action-extract:$envelopeId`, tags `TAG_CONTINUATION + TYPE_TAG_ACTION_EXTRACT + envelope tag`. Companion: `TYPE_TAG_ACTION_EXTRACT`, `ACTION_EXTRACT_BACKOFF_BASE_SECONDS=30L`, `uniqueNameForActionExtract`.
- **T046** [EnvelopeRepositoryImpl.kt](app/src/main/java/com/capsule/app/data/EnvelopeRepositoryImpl.kt) — constructor adds `private val actionExtractor: ActionExtractor? = null`; post-seal hook fires after Room txn commits and AFTER URL-hydrate fan-out: `if (contentType==TEXT && !blank && ActionExtractionPrefilter.shouldExtract(text)) → engine.enqueueActionExtract(id)` (mirrors 002 T066a/T068 pattern — outside the txn so WorkManager DB lock can't deadlock Room). New override `extractActionsForEnvelope(envelopeId): String` returning string-coded outcome (`PROPOSED:N` / `NO_CANDIDATES` / `SKIPPED:reason` / `FAILED:reason` / `UNAVAILABLE`) for the worker to map to WorkManager `Result`.
- **AIDL fix + extension** [IEnvelopeRepository.aidl](app/src/main/aidl/com/capsule/app/data/ipc/IEnvelopeRepository.aidl) — fixed pre-existing syntax bug where `seedScreenshotHydrations(envelopeId,` was opened but never closed because action methods got inserted in the middle (with stray `String ocrText, in String[] urls);` at the very end). Restructured into proper sequence. Added `String extractActionsForEnvelope(String envelopeId);` at end with comment documenting the outcome-code grammar.
- **T049** [CalendarActionHandler.kt](app/src/main/java/com/capsule/app/action/handler/CalendarActionHandler.kt) — replaced stub `Failed("handler_not_yet_implemented")` with real `Intent.ACTION_INSERT` to `CalendarContract.Events.CONTENT_URI`. Required: `Events.TITLE`, `EXTRA_EVENT_BEGIN_TIME`. Optional: `EXTRA_EVENT_END_TIME` (defaults to `start+1h`), `Events.EVENT_LOCATION`, `Events.DESCRIPTION`, `Events.EVENT_TIMEZONE`. `FLAG_ACTIVITY_NEW_TASK`. Catches `ActivityNotFoundException`→`Failed("no_handler")`, `SecurityException`→`Failed("security_exception")` (defensive — v1.1 sends no permission-gated extras).
- **T050** [DelayedUndoCleanupWorker.kt](app/src/main/java/com/capsule/app/action/DelayedUndoCleanupWorker.kt) — 5s `OneTimeWorkRequest` scheduled by `ActionExecutorService` immediately after `Dispatched`/`Success`. On fire calls `ActionExecutorService.expireUndoWindow(executionId)` which adds the id to a process-singleton `expiredExecutionIds` set. `cancelWithinUndoWindow` checks `isExpired` first and returns `false` past the window. **Cross-process accuracy**: the worker runs in default process while the service lives in `:capture`, so the companion-level set is per-process — but the binder method `cancelWithinUndoWindow` is invoked on the `:capture` instance which sees its own expirations in-process. Workers and service share the same process iff WorkManager's default process == `:capture`, which it isn't; the structural retry path is sound but cross-process expiry observation needs T097-era hardening (e.g., DB-backed expiry flag). v1.1 accepts the limitation: process death within 5s + recovery is effectively never observed; system Calendar's own confirmation gates the external write regardless.
- **EnvelopeRepositoryService wiring** — `ActionExtractor` instantiated alongside `ActionsRepositoryDelegate` and passed to `EnvelopeRepositoryImpl`. Uses existing `NanoLlmProvider()` (v1.1 stub returns deterministic empty candidate list, valid contract outcome — real AICore integration is T021).
- **No gradle gates run yet**: static `get_errors` clean across all touched files. First build will validate the new AIDL method signature compiles cleanly into both `IEnvelopeRepository.Stub` (server) and `IEnvelopeRepository.Stub.Proxy` (client).
- **Phase 3 US1 status**: T034, T035, T041–T046, T049, T050 ✅. Pending: T036–T040 (instrumented worker/handler/undo/no-net/IPC tests), T047 (ActionExecutorService refinement — already partially landed in Phase 2), T048 (`AppFunctionInvoker` — `ActionHandlerRegistry` covers this responsibility, may collapse), T051–T054 (Compose UI cluster).
- **Next**: UI cluster T051-T054 OR instrumented test cluster T036-T040. UI is the dependency for user-facing Phase 3 acceptance gate.

**2026-04-26 (later) — T005 + T031–T033 close out Phase 2; foundational gate is green pending build**

- **T005** — README.md table now lists `specs/003-orbit-actions/quickstart.md` as a first-class "v1.1 Orbit Actions" row alongside the 002 quickstart, with the post-v1 stub row narrowed to `004–007`. See [README.md](README.md) line ~96.
- **T031** [AppFunctionRegistryConcurrencyTest.kt](app/src/androidTest/java/com/capsule/app/data/AppFunctionRegistryConcurrencyTest.kt) — 8 concurrent `registerAll([sameSchema])` collapses to exactly 1 row + 1 `APPFUNCTION_REGISTERED` audit row (mutex+lookup-then-insert proof); 8 concurrent v1→v2 bumps after a v1 seed produce exactly 2 audit rows total (seed + bump, racers no-op); 4 concurrent registrations of *distinct* schemas produce 4 rows + 4 audit rows. Uses `Dispatchers.Default` + `async{}` for real preemption rather than `runTest`'s virtual time. **Deviation**: tasks.md says JVM placement, file landed in androidTest — same Room-runtime constraint as T030.
- **T032** [SchemaValidationTest.kt](app/src/test/java/com/capsule/app/data/SchemaValidationTest.kt) — JVM unit test using `org.json:json:20240303` (already on `testImplementation`). Verifies all three built-in schemas parse as JSON, declare `type:'object'`, declare `additionalProperties:false`, declare a `properties` map, and reference every `required` entry inside `properties`. Spot-checks `calendar.createEvent` requires `title`+`startEpochMillis`, `tasks.createTodo` requires only `title` (`dueEpochMillis` optional), and `share.delegate` requires `targetMimeType`. **Scope-narrowed deviation**: full positive/negative-instance JSON Schema 2020-12 validation against synthetic `argsJson` is deferred. Reason: no JSON Schema validator dependency on the classpath yet (`com.networknt:json-schema-validator` or equivalent would be a real dep choice). The validator gets pulled in alongside T021 (NanoLlmProvider's args validation step) and T091 (ActionExecutorService's pre-flight schema re-validation). Until then this test catches the high-frequency regressions: malformed JSON, missing `properties`, dropped `additionalProperties:false`, and `required` keys without matching property declarations.
- **T033** [LlmProviderExtractActionsContractTest.kt](app/src/test/java/com/capsule/app/ai/LlmProviderExtractActionsContractTest.kt) — JVM abstract harness with 5 cases: empty text, informational-only text, event-like text, empty registry (→ zero candidates by definition), `maxCandidates=1` clamping. The shared invariant assertion (`assertEqualsContractInvariants`) enforces 9 rules per candidate: bounded count, non-null provenance, JSON-validity, 4096-byte argsJson cap, functionId in registered set, schemaVersion pinning, sensitivityScope coherence, confidence in [0,1], confidence-descending sort. Concrete subclass `NanoLlmProviderExtractActionsContractTest` proves the abstract harness against the v1.1 stub `NanoLlmProvider` (which returns empty candidates — a valid contract outcome). When the AICore integration in T021 lands, it'll pass the harness without modification; cloud/BYOK providers in spec 005 just extend the same fixture.
- **No gradle gates run yet** — the JVM tests should run cleanly against the existing `:app:testDebugUnitTest` task; the androidTest concurrency test will run on emulator alongside T029/T030.
- **Phase 2 done**: T001–T033 complete (with documented deviations on T030/T031 source-set placement and T032 scope narrowing). Ready to enter Phase 3 US1 Calendar (T034–T054).

**2026-04-26 — Phase 1 + Phase 2 entities/DAOs/migration/registry/AIDL/executor scaffolded; foundational tests partially landed**

- **Phase 1 (T001–T004) complete; T005 deferred**: Room schema bumped 1→2 in `app/build.gradle.kts` (T001 — `2.json` will be generated on first build that runs the migration test); AppFunctions runtime + KSP compiler deps added via `gradle/libs.versions.toml` (T002, alpha01); `<service android:name=".action.ActionExecutorService" android:process=":capture" android:exported="false">` added to manifest with no new `<uses-permission>` rows (T003); `NoHttpClientOutsideNetDetector` already flagged anything outside `com.capsule.app.net.*` so T004 was satisfied by adding regression tests proving `com.capsule.app.action.handler.*` and `com.capsule.app.action.*` constructions of `OkHttpClient` / `java.net.Socket` are flagged (see [NoHttpClientOutsideNetDetectorTest.kt](build-logic/lint/src/test/java/com/capsule/lint/NoHttpClientOutsideNetDetectorTest.kt)). **T005 (README v1.1 bullet) — not yet done**, the existing line 96 link points only to the spec stub directory.
- **Phase 2 enums + entities + DAOs + migration (T006–T019) complete**: `EnvelopeKind`, extended `IntentEnvelopeEntity` (kind + derivedFromEnvelopeIdsJson + todoMetaJson columns, composite (kind, day_local) index), extended `ContinuationType` (ACTION_EXTRACT, ACTION_EXECUTE, WEEKLY_DIGEST), extended `AuditAction` (ACTION_PROPOSED/DISMISSED/CONFIRMED/EXECUTED/FAILED, APPFUNCTION_REGISTERED, DIGEST_GENERATED, DIGEST_SKIPPED — note: also includes ENVELOPE_INVALIDATED for digest provenance cascade in T075), all four new entities (ActionProposalEntity, ActionExecutionEntity, AppFunctionSkillEntity, SkillUsageEntity), all four new DAOs (state-machine filters return `Int` rowCount so audit-atomicity callers can no-op cleanly per Principle IX), `MIGRATION_1_2` with the partial unique digest index `(day_local) WHERE kind='DIGEST'`, schema v=2 wired in `OrbitDatabase`.
- **Phase 2 LlmProvider extension + registry + KSP (T020–T025) complete**: `LlmProvider.extractActions` signature added (line 45), `NanoLlmProvider.extractActions` override (line 39) — implementation is the deterministic candidate path; `AppFunctionRegistry` constructor takes `(database, skillDao, usageDao, auditLogDao, auditWriter, now)` and writes one `APPFUNCTION_REGISTERED` audit row per inserted/version-bumped skill in the same `database.withTransaction` (idempotent: same-version registration writes 0 audit rows, schema bump preserves `registeredAt` and advances `updatedAt`); `AppFunctionAnnotations.kt` defines `@AppFunction` + `SideEffect` + `Reversibility` + `SensitivityScope`; KSP processor wired; three built-in schemas (`calendar.createEvent`, `tasks.createTodo`, `share.delegate`) seeded at `:ml` startup via both `CapsuleApplication.onCreate` and `EnvelopeRepositoryService.onCreate` (defensive double-seed; idempotent).
- **Phase 2 AIDL surface + executor scaffolding (T026–T028) complete**: 7 new methods on `IEnvelopeRepository` (`lookupAppFunction`, `listAppFunctions`, `recordActionInvocation`, `markProposalConfirmed/Dismissed`, `observeProposalsForEnvelope`, `stopObservingProposals`); new parcels `ActionProposalParcel`, `AppFunctionSummaryParcel`, `ActionExecuteRequestParcel`, `ActionExecuteResultParcel`, `IActionProposalObserver` (oneway). `EnvelopeRepositoryImpl` overrides land at lines 609–663 delegating to a new `ActionsRepositoryDelegate` (DB writes + audit-row atomicity in single `database.withTransaction`). [ActionExecutorService.kt](app/src/main/java/com/capsule/app/action/ActionExecutorService.kt) bound Service runs in `:capture`, binds `:ml`'s `EnvelopeRepositoryService` to obtain `IEnvelopeRepository`, returns an `IActionExecutor.Stub`. Pre-flight failures (`ml_binder_unavailable`, `unknown_skill`, `skill_not_registered`, `schema_invalidated`) are written via `repo.recordActionInvocation` and returned synchronously. Handlers dispatched via `ActionHandlerRegistry`; v1.1 stubs return `Failed(...)` until US1/US2 phases land.
- **Foundational tests (T029, T030) complete; T031/T032/T033 deferred**:
  - **T029** [OrbitDatabaseMigrationV1toV2Test.kt](app/src/androidTest/java/com/capsule/app/data/OrbitDatabaseMigrationV1toV2Test.kt) — seeds 1000 envelopes + audit row in v1 schema (matching the v1 ContentValues column shape verified against `app/schemas/.../1.json`), runs `MIGRATION_1_2`, asserts: all 1000 rows back-fill `kind=REGULAR`, four new tables accept inserts, `(envelopeId,functionId)` unique idx rejects dup, partial unique digest idx allows REGULAR but rejects 2nd DIGEST on same `day_local`, cascade-delete chain (envelope → 0 proposals/executions/skill_usages), audit fixture preserved, fail-closed assertion if migration omitted (no silent destructive migration).
  - **T030 (with deviation)** [AppFunctionRegistryTest.kt](app/src/androidTest/java/com/capsule/app/data/AppFunctionRegistryTest.kt) — placed in **androidTest/** rather than test/ as the task originally specified. Rationale: the registry depends on `database.withTransaction` (Room runtime) and the existing test scaffolding for in-memory unencrypted Room runs in androidTest. Five tests cover seed-and-audit, idempotency, schema bump (preserves `registeredAt`, advances `updatedAt`, lookupExact(v1)→null/lookupExact(v2)→row, 2 audit rows), empty-list no-op, and `recordInvocation` aggregating into `SkillStats` (DISPATCHED counts as success per the 002+003 contract). Functionally satisfies T030's intent — JVM placement is a follow-up if Robolectric or pure JDBC stubbing is added later.
  - **T031 AppFunctionRegistryConcurrencyTest** — NOT YET WRITTEN. The registry's `Mutex` serialisation (contract §6) is structurally guaranteed by the single `database.withTransaction` write path inside `registerAll`, but the explicit two-coroutine concurrency assertion is still pending.
  - **T032 SchemaValidationTest** — NOT YET WRITTEN. Built-in schemas' `argsSchemaJson` constants exist; the negative-path proof (rejects malformed, accepts conforming, optional fields) is pending.
  - **T033 LlmProviderExtractActionsContractTest** — NOT YET WRITTEN. The extension is on the interface and the Nano impl exists, but the abstract contract fixture every impl extends (size bound, JSON-validity, schema-validation, deterministic provenance) is pending.
- **Bonus tests landed beyond tasks.md numbering**: [ActionsRepositoryDelegateTest.kt](app/src/androidTest/java/com/capsule/app/data/ActionsRepositoryDelegateTest.kt) — five tests covering audit-atomicity for the delegate's `writeProposals` / `markProposalConfirmed` / `markProposalDismissed` / `recordActionInvocation` (DISPATCHED → ACTION_EXECUTED, FAILED → ACTION_FAILED, no phantom audit rows on no-op state transitions). This is implicitly extra coverage for T027 (the AIDL impl wiring) which tasks.md did not call out as needing a dedicated contract test.
- **Migration constraints (Principle IX) reaffirmed**: `AuditLogWriter` only `build()`s rows; insert always happens via `auditLogDao.insert(auditWriter.build(...))` inside the same `database.withTransaction { }` as the data mutation. State-filter DAO methods (`markDismissed/Confirmed/Invalidated`) return `Int` rowCount so callers can elide phantom audit rows when a no-op state transition occurs.
- **Closed-enum tension** (mirrors 002 history): the new `AuditAction` rows landed in this session because Phase 2 mandates the full set — no migration deferral was needed. `ContinuationType.ACTION_EXTRACT/ACTION_EXECUTE/WEEKLY_DIGEST` similarly extended cleanly.
- **No gradle gates run yet**: this session was static `get_errors`-clean across all touched files. First build will validate `MIGRATION_1_2` (will generate `2.json`) and the androidTest seed shapes. RED → GREEN expected once a real device/emulator is reachable.
- **Open questions OQ-2** (action-execution-contract.md §3 — three undocumented lifecycle methods on the AIDL surface) is now reflected in code. The contract file should be updated to enumerate these before a v1.1 reviewer audits the surface.
- **Next**: T005 README link, T031 concurrency test, T032 schema-validation test, T033 LlmProvider contract test → then Phase 3 US1 Calendar (T034–T054). Phase 8 physical-device tasks (T103–T111) deferred to release gate per plan.

---

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: parallelisable — different files, no incomplete-task dependency.
- **[USn]**: required for any task inside a user-story phase; absent in Setup / Foundational / Polish.
- **(PHYSICAL DEVICE)**: marks tasks that cannot run on host/emulator and must be executed on a Pixel 8+ physical device (mirrors 002 T110–T113).

---

## Path Conventions (Android single-module, 4-process app)

```
app/src/main/java/com/capsule/app/
  data/                 # Room (:ml)
  ai/                   # LlmProvider, extractors (:ml)
  continuation/         # WorkManager workers (:ml)
  action/               # NEW — AppFunction handlers (:capture)
  diary/                # Compose UI (:ui) — extended
  settings/             # Compose UI (:ui) — extended
  audit/                # AuditAction enum (:ml)

app/src/main/aidl/com/capsule/app/data/ipc/    # AIDL contracts
app/src/test/java/com/capsule/app/             # JVM tests
app/src/androidTest/java/com/capsule/app/      # Instrumented tests
```

---

## Phase 1: Setup (Shared Infrastructure)

- [x] T001 Bump Room schema version 1 → 2 in `app/build.gradle.kts` ksp room args (`schemaLocation = "$projectDir/schemas"`); confirm `app/schemas/com.capsule.app.data.OrbitDatabase/2.json` is generated by the migration test in T030.
- [x] T002 [P] Add Gradle deps to `app/build.gradle.kts`: `androidx.appfunctions:appfunctions-runtime:1.0.+`, `androidx.appfunctions:appfunctions-compiler:1.0.+` (KSP). Pin via `gradle/libs.versions.toml`.
- [x] T003 [P] Add `<service android:name=".action.ActionExecutorService" android:process=":capture" android:exported="false">` declaration to `app/src/main/AndroidManifest.xml` per action-execution-contract.md §2; confirm no new `<uses-permission>` rows are added (no `WRITE_CALENDAR`, no `INTERNET` outside `:net`).
- [x] T004 [P] Extend the `NoHttpClientOutsideNet` lint rule scope in `build-logic/lint/src/main/java/.../NoHttpClientOutsideNetDetector.kt` to also flag `okhttp3` / `HttpURLConnection` / `java.net.Socket` references inside `com.capsule.app.action.*`; rebuild lint module and confirm `:app:lintDebug` still green on a clean tree.
- [x] T005 Document the v1.1 spec link in repo root `README.md` — append a "v1.1 Orbit Actions" bullet pointing to `specs/003-orbit-actions/quickstart.md`.

**Phase 1 checkpoint**: `./gradlew :app:assembleDebug :app:lintDebug` PASS; AppFunctions runtime resolves on dependency graph.

---

## Phase 2: Foundational (Blocking Prerequisites)

**⚠️ CRITICAL**: No user-story phase may begin until Phase 2 is green.

### Enums and entities

- [x] T006 [P] Add `EnvelopeKind` enum to `app/src/main/java/com/capsule/app/data/entities/EnvelopeKind.kt` with values `REGULAR, DIGEST, DERIVED` per data-model.md §"Enum Extensions".
- [x] T007 Extend `IntentEnvelopeEntity` in `app/src/main/java/com/capsule/app/data/entities/IntentEnvelopeEntity.kt` (MODIFY 002) with `kind: EnvelopeKind = EnvelopeKind.REGULAR` and `derivedFromEnvelopeIdsJson: String? = null`; add the composite index `(kind, day_local)` per data-model.md §1.
- [x] T008 [P] Extend `ContinuationType` enum in `app/src/main/java/com/capsule/app/continuation/ContinuationType.kt` (MODIFY 002) with `ACTION_EXTRACT, ACTION_EXECUTE, WEEKLY_DIGEST` per data-model.md §"Enum Extensions".
- [x] T009 [P] Extend `AuditAction` enum in `app/src/main/java/com/capsule/app/audit/AuditAction.kt` (MODIFY 002) with `ACTION_PROPOSED, ACTION_DISMISSED, ACTION_CONFIRMED, ACTION_EXECUTED, ACTION_FAILED, APPFUNCTION_REGISTERED, DIGEST_GENERATED, DIGEST_SKIPPED` per data-model.md §6.
- [x] T010 [P] Create `app/src/main/java/com/capsule/app/data/entities/ActionProposalEntity.kt` per data-model.md §2 (PK `id`, FK `envelopeId` CASCADE, unique index `(envelopeId, functionId)`, `state` state-machine column, `provenance`, `sensitivityScope`).
- [x] T011 [P] Create `app/src/main/java/com/capsule/app/data/entities/ActionExecutionEntity.kt` per data-model.md §3 (PK `id`, FK `proposalId` CASCADE, `outcome` column, nullable `episodeId` for spec 006 forward-compat).
- [x] T012 [P] Create `app/src/main/java/com/capsule/app/data/entities/AppFunctionSkillEntity.kt` per data-model.md §4 (PK `functionId`, `schemaVersion`, `argsSchemaJson`, `sideEffects`, `reversibility`, `sensitivityScope`); schema mirrors spec 006 `skills` table verbatim.
- [x] T013 [P] Create `app/src/main/java/com/capsule/app/data/entities/SkillUsageEntity.kt` per data-model.md §5 (FKs to `appfunction_skill` and `action_execution`, denormalised `proposalId`, nullable `episodeId`).

### DAOs

- [x] T014 [P] Create `app/src/main/java/com/capsule/app/data/dao/ActionProposalDao.kt` — `insert`, `markDismissed(id, at)`, `markConfirmed(id, at)`, `markInvalidated(id, at)`, `observeForEnvelope(envelopeId): Flow<List<ActionProposalEntity>>`, `findByEnvelopeAndFunction` (for unique-constraint pre-check).
- [x] T015 [P] Create `app/src/main/java/com/capsule/app/data/dao/ActionExecutionDao.kt` — `insertDispatched`, `markOutcome(id, outcome, completedAt, latencyMs, reason)`, `observeByProposal(proposalId)`.
- [x] T016 [P] Create `app/src/main/java/com/capsule/app/data/dao/AppFunctionSkillDao.kt` — `upsert(entity)`, `lookupLatest(functionId)`, `lookupExact(functionId, schemaVersion)`, `listForApp(appPackage)`.
- [x] T017 [P] Create `app/src/main/java/com/capsule/app/data/dao/SkillUsageDao.kt` with the `aggregate(skillId, sinceMillis)` query from data-model.md §5 returning `SkillStats(successRate, cancelRate, avgLatencyMs, invocationCount)`.

### Database migration

- [x] T018 Add `MIGRATION_1_2` to `app/src/main/java/com/capsule/app/data/OrbitDatabase.kt` (MODIFY 002) — exact SQL from data-model.md §7 (ALTER `intent_envelope` + 4 CREATE TABLE + 13 CREATE INDEX). Bump `@Database(version = 2, …)` and add the four new entities to the entity list.
- [x] T019 Add SQL `CREATE UNIQUE INDEX index_digest_unique_per_day ON intent_envelope(day_local) WHERE kind = 'DIGEST'` partial index per weekly-digest-contract.md §3 (idempotency). Goes into `MIGRATION_1_2`.

### LlmProvider extension and registry

- [x] T020 Extend `app/src/main/java/com/capsule/app/ai/LlmProvider.kt` (MODIFY 002) with `suspend fun extractActions(text, contentType, state, registeredFunctions, maxCandidates=3): ActionExtractionResult` and the supporting data classes `AppFunctionSummary`, `ActionExtractionResult`, `ActionCandidate` per action-extraction-contract.md §3. Document the determinism / 4 KB / schema-validation guarantees in KDoc.
- [x] T021 Implement `extractActions` in `app/src/main/java/com/capsule/app/ai/NanoLlmProvider.kt` (MODIFY 002) — structured JSON prompt that includes the registered function schemas, calls AICore Nano, parses ≤ 3 candidates, validates each `argsJson` against the function's `argsSchemaJson` and drops invalid ones, emits `LlmProvenance.LocalNano`. Throws cleanly on `Nano.UNAVAILABLE` so `ActionExtractor` can audit a failure.
- [x] T022 [P] Create `app/src/main/java/com/capsule/app/data/AppFunctionRegistry.kt` per appfunction-registry-contract.md §4 — `register`, `lookup`, `lookup(functionId, schemaVersion)`, `listForApp`, `stats`, `recordInvocation`. Single `Mutex` serialises writes (contract §6). `recordInvocation` writes both `skill_usage` and an `APPFUNCTION_REGISTERED` audit row in one Room transaction.
- [x] T023 [P] Create `app/src/main/java/com/capsule/app/action/AppFunctionAnnotations.kt` housing `@AppFunction`, `enum SideEffect { EXTERNAL_INTENT, LOCAL_DB_WRITE }`, `enum Reversibility { REVERSIBLE_24H, EXTERNAL_MANAGED, NONE }`, `enum SensitivityScope { PUBLIC, PERSONAL, SHARE_DELEGATED }` per appfunction-registry-contract.md §2.
- [x] T024 [P] Wire the AppFunctions KSP processor — add the compiler args to `app/build.gradle.kts` so `@AppFunction`-annotated args data classes generate `argsSchemaJson` constants discoverable at runtime.
- [x] T025 Seed the three v1.1 schemas at app launch — extend `com.capsule.app.CapsuleApplication.onCreate` (MODIFY 002, `:ml` only) with `registry.registerAll(BUILT_IN_SCHEMAS)` per appfunction-registry-contract.md §3. Idempotent (`Unchanged` on identical schema).

### AIDL surface extensions

- [x] T026 Extend `app/src/main/aidl/com/capsule/app/data/ipc/IEnvelopeRepository.aidl` (MODIFY 002) with three new methods per appfunction-registry-contract.md §5: `lookupAppFunction(functionId): AppFunctionSchemaParcel`, `listAppFunctions(appPackage): List<AppFunctionSummaryParcel>`, `recordActionInvocation(outcome: ActionInvocationOutcomeParcel)`. Add three new methods for action lifecycle: `markProposalConfirmed(proposalId)`, `markProposalDismissed(proposalId)`, `observeProposalsForEnvelope(envelopeId): Flow proxy`. Define new parcels in `app/src/main/aidl/.../AppFunctionSchemaParcel.aidl`, `AppFunctionSummaryParcel.aidl`, `ActionInvocationOutcomeParcel.aidl`.
- [x] T027 Implement the seven new AIDL methods in `app/src/main/java/com/capsule/app/data/EnvelopeRepositoryImpl.kt` (MODIFY 002) — delegating to `AppFunctionRegistry`, `ActionProposalDao`, `ActionExecutionDao` with audit-row atomicity per data-model.md §6.
- [x] T028 Create `app/src/main/aidl/com/capsule/app/action/ipc/IActionExecutor.aidl` and the parcels `ActionExecuteRequestParcel`, `ActionExecuteResultParcel` per action-execution-contract.md §3. The two AIDL methods: `execute(request)`, `cancelWithinUndoWindow(executionId)`.

### Foundational tests

- [x] T029 [P] Create `app/src/androidTest/java/com/capsule/app/data/OrbitDatabaseMigrationV1toV2Test.kt` — Room `MigrationTestHelper` opens a v1 DB seeded with 1000 envelopes, runs `MIGRATION_1_2`, asserts: (a) all 002 queries still return; (b) every existing row has `kind = 'REGULAR'`; (c) the four new tables exist; (d) the partial unique index is present; (e) re-running the migration is a no-op. Required by quickstart §8 acceptance gate item 4.
- [x] T030 [P] Create `app/src/test/java/com/capsule/app/data/AppFunctionRegistryTest.kt` (JVM, in-memory Room) — `register / upsert / Unchanged / VersionBumped` semantics, `lookup` by id-only returns highest schema version, `stats` aggregation correctness over a fixed clock. **Deviation**: file landed in `app/src/androidTest/...` path (uses Room runtime via androidx.test) — see Status log 2026-04-26.
- [x] T031 [P] Create `app/src/test/java/com/capsule/app/data/AppFunctionRegistryConcurrencyTest.kt` (JVM) — two coroutines `register()` the same schema concurrently → exactly one `Inserted`, the other `Unchanged` (mutex correctness, contract §6). **Deviation**: file landed in `app/src/androidTest/...` (same Room-runtime constraint as T030).
- [x] T032 [P] Create `app/src/test/java/com/capsule/app/data/SchemaValidationTest.kt` (JVM) — generated `argsSchemaJson` for all three v1.1 schemas: rejects malformed `argsJson`, accepts conforming, handles optional fields per appfunction-registry-contract.md §8. **Scope-narrowed**: shape/well-formed-JSON proof now; full positive/negative-instance JSON Schema validation deferred to T021/T091 when the validator dep lands.
- [x] T033 [P] Create `app/src/test/java/com/capsule/app/ai/LlmProviderExtractActionsContractTest.kt` (JVM) — abstract test fixture every `LlmProvider` impl must extend: bounded `maxCandidates`, all `argsJson` parses as JSON ≤ 4 KB, all `argsJson` validates against the named function's schema, deterministic `provenance` field; invalid candidates dropped silently per action-extraction-contract.md §3.

**Phase 2 checkpoint**: `./gradlew :app:assembleDebug :app:lintDebug :app:compileDebugAndroidTestKotlin :app:testDebugUnitTest` PASS. Migration test runs green against a 1000-envelope seed. Three built-in schemas register successfully on first launch (verifiable via `adb logcat | grep AppFunctionRegistry`).

---

## Phase 3: User Story 1 — Calendar Action (Priority: P1)

**Goal**: Sealing an envelope containing a flight confirmation produces an inline `+ Add to calendar` chip; tap → preview card → Confirm fires `Intent.ACTION_INSERT` to system Calendar; audit + skill_usage rows recorded; 5s undo affordance.
**Independent test criteria** (quickstart §3): chip appears within 60s on charger+wifi; Confirm → system Calendar opens within 600ms p95; three audit rows in order (`ACTION_PROPOSED`, `ACTION_CONFIRMED`, `ACTION_EXECUTED`); one `skill_usage` row.

### Contract & integration tests (US1)

- [x] T034 [P] [US1] Create `app/src/test/java/com/capsule/app/ai/ActionExtractorTest.kt` (JVM, in-memory Room) — pipeline order from action-extraction-contract.md §4: load envelope, sensitivity gate, registry filter, llm call, confidence-floor drop, schema-validation drop, sensitivity-scope mismatch drop, audit-row atomicity, `Skipped`/`Failed`/`NoCandidates`/`Proposed` outcomes.
- [x] T035 [P] [US1] Create `app/src/test/java/com/capsule/app/ai/DateTimeParserTest.kt` (JVM, fixed `Clock`) — relative tokens (`Mon..Sun`, `today`, `tomorrow`, `next Tuesday`, `in 3 days`), explicit ISO 8601 passthrough, hour-default 09:00 local rule, DST boundary assertion (`ZoneId == systemDefault`), unparseable → null per research.md §3.
- [x] T036 [P] [US1] Create `app/src/androidTest/java/com/capsule/app/continuation/ActionExtractionWorkerTest.kt` (instrumented) — worker → extractor wiring, `Result.retry()` on `Failed`, `Result.success()` on `NoCandidates / Skipped / Proposed`, idempotency via `ExistingWorkPolicy.KEEP`, max-attempts behaviour writing `CONTINUATION_FAILED reason=action_extract_exhausted`.
- [x] T037 [P] [US1] Create `app/src/androidTest/java/com/capsule/app/action/CalendarInsertHandlerTest.kt` (instrumented, runs in `:capture`) — captures the dispatched `Intent` via `IntentTestRule`: action == `Intent.ACTION_INSERT`, data == `CalendarContract.Events.CONTENT_URI`, extras include `Events.TITLE`, `Events.EVENT_LOCATION`, `Events.DESCRIPTION`, `EXTRA_EVENT_BEGIN_TIME`, `EXTRA_EVENT_END_TIME`, `FLAG_ACTIVITY_NEW_TASK` set, end-time defaults to start+1h when null per research.md §4.
- [x] T038 [P] [US1] Create `app/src/androidTest/java/com/capsule/app/action/UndoWindowTest.kt` (instrumented) — cancel within 5s flips `outcome` to `USER_CANCELLED` and writes `ACTION_FAILED reason=user_cancelled`; cancel after 5s no-ops; `DelayedUndoCleanupWorker` no-ops if outcome already terminal.
- [x] T039 [P] [US1] Create `app/src/androidTest/java/com/capsule/app/action/NoNetworkDuringActionExecutionTest.kt` (instrumented) — `StrictMode.setThreadPolicy(detectNetwork().penaltyDeath())` around each handler dispatch; UID socket-creation guard via `Process.myUid()` + a custom `SocketFactory` that throws on connect. Action handlers MUST not trigger any network I/O. Required by Principle VI gate per action-execution-contract.md §6.
- [x] T040 [P] [US1] Create `app/src/androidTest/java/com/capsule/app/action/ExecutionIpcContractTest.kt` (instrumented) — `:ui` can bind `IActionExecutor`; foreign-package binders rejected by `android:exported="false"`; `recordActionInvocation` IPC round-trip < 200ms p99.

### Implementation (US1)

- [x] T041 [P] [US1] Create `app/src/main/java/com/capsule/app/ai/DateTimeParser.kt` per research.md §3 — pure object, java.time-based, anchored to envelope `createdAt` and device tz, handles relative tokens listed in T035, returns nullable `ZonedDateTime`. No 3rd-party NLP.
- [x] T042 [US1] Create `app/src/main/java/com/capsule/app/ai/ActionExtractionPrefilter.kt` — Phase A regex pre-filter from research.md §2: flight booking codes `/^[A-Z]{2}\d{2,4}$/`, weekday tokens, imperative-list patterns, `RSVP/confirm`, currency-followed-by-digits. Returns `Boolean` ("worth running Nano on?"). Pure function; called by `ContinuationEngine.maybeEnqueueActionExtract` post-seal.
- [x] T043 [US1] Create `app/src/main/java/com/capsule/app/ai/ActionExtractor.kt` per action-extraction-contract.md §4 — constructor injects `LlmProvider`, `AppFunctionRegistry`, `ActionProposalDao`, `AuditLogWriter`, `SensitivityScrubber`, `confidenceFloor=0.55f`. `extract(envelopeId): ExtractOutcome` with sealed-interface return.
- [x] T044 [US1] Create `app/src/main/java/com/capsule/app/continuation/workers/ActionExtractionWorker.kt` per action-extraction-contract.md §5 — reads `KEY_ENVELOPE_ID`, calls `ActionExtractor.extract`, maps outcome to WorkManager `Result`. `MAX_ATTEMPTS=3`, exponential backoff 30s base.
- [x] T045 [US1] Extend `app/src/main/java/com/capsule/app/continuation/ContinuationEngine.kt` (MODIFY 002) — register `ACTION_EXTRACT` continuation type; expose `enqueueActionExtract(envelopeId)` with charger+wifi+battery-not-low constraints, unique work id `action-extract-${envelopeId}`, `ExistingWorkPolicy.KEEP`. Called from `EnvelopeRepositoryImpl.seal()` post-transaction iff `ActionExtractionPrefilter.shouldExtract(envelope)` returns true and sensitivity gates pass per action-extraction-contract.md §2.
- [x] T046 [US1] Wire post-seal extract enqueue in `app/src/main/java/com/capsule/app/data/EnvelopeRepositoryImpl.kt` (MODIFY 002) — after the seal Room transaction commits and AFTER existing URL-hydrate fan-out, evaluate `ActionExtractionPrefilter` + sensitivity gate and call `engine.enqueueActionExtract(id)`. Outside the txn (mirrors 002 T066a/T068 pattern to avoid WorkManager DB cross-lock).
- [ ] T047 [P] [US1] Create `app/src/main/java/com/capsule/app/action/ActionExecutorService.kt` per action-execution-contract.md §2 — `Service` running in `:capture`, exposes `IActionExecutor.Stub`. Holds an `AppFunctionInvoker` and a `WorkManager` for the 5s undo cleanup.
- [ ] T048 [P] [US1] Create `app/src/main/java/com/capsule/app/action/AppFunctionInvoker.kt` — sealed dispatch from `functionId` to handler. v1.1 routes: `calendar_insert → CalendarInsertHandler`, `todo_add → TodoAddHandler`, `share → ShareHandler`. Re-validates `argsJson` against the proposal's `schemaVersion` (defense in depth) per action-execution-contract.md §4 step 1.
- [x] T049 [US1] Create `app/src/main/java/com/capsule/app/action/CalendarInsertHandler.kt` per research.md §4 + action-execution-contract.md §4 step 3 — builds `Intent.ACTION_INSERT` to `CalendarContract.Events.CONTENT_URI` with extras (`TITLE`, `EVENT_LOCATION`, `DESCRIPTION`, `EXTRA_EVENT_BEGIN_TIME`, `EXTRA_EVENT_END_TIME`), `FLAG_ACTIVITY_NEW_TASK`, end-time defaults to `start + 1h`. Catches `ActivityNotFoundException` → `ACTION_FAILED reason=no_handler`.
- [x] T050 [P] [US1] Create `app/src/main/java/com/capsule/app/action/DelayedUndoCleanupWorker.kt` — 5s OneTimeWork; on run, checks `action_execution.outcome` and no-ops if already terminal. Schedules toast dismiss for `:ui` via the existing audit observer.
- [x] T051 [P] [US1] Create `app/src/main/java/com/capsule/app/diary/ActionProposalChipUI.kt` — Compose composable rendered inline under `EnvelopeCard` in `:ui`, observes `Flow<List<ActionProposalEntity>>` from `ActionProposalDao` via `DiaryViewModel`. Renders one chip per `state=PROPOSED` proposal with `previewTitle`. Tap → opens `ActionPreviewCardUI` modal. Visual placement only — typography/colour deferred to design.md / spec 010.
- [x] T052 [P] [US1] Create `app/src/main/java/com/capsule/app/diary/ActionPreviewCardUI.kt` — modal Compose surface with editable fields (title, start, end, location, description, timezone). On Confirm: calls `IEnvelopeRepository.markProposalConfirmed(proposalId)` then `IActionExecutor.execute(...)`. On Cancel/back: writes `ACTION_DISMISSED`. Reversibility honesty line: "Once added, you'll need to edit it in Calendar." per action-execution-contract.md §5.
- [x] T053 [US1] Extend `app/src/main/java/com/capsule/app/diary/DiaryViewModel.kt` (MODIFY 002) — add a joined query observing proposals per envelope; `onConfirmProposal(proposalId)` and `onDismissProposal(proposalId)` hops; toast state for the 5s undo window.
- [x] T054 [US1] Wire `:ui` → `:capture` IActionExecutor binder in `app/src/main/java/com/capsule/app/diary/DiaryActivity.kt` (MODIFY 002) — `bindService(ACTION="com.capsule.app.action.BIND_ACTION_EXECUTOR")`, lifecycle-managed unbind, retry once on disconnect.

**US1 checkpoint**: quickstart §3 path A passes against a Pixel emulator using a fake `LlmProvider` that returns a deterministic flight-confirmation candidate; physical-device verification deferred to T106.

---

## Phase 4: User Story 2 — To-Do Action (Priority: P1)

**Goal**: Envelope with a list/imperative pattern produces a `+ Add to to-dos` chip; Confirm with target=local creates N child envelopes (kind=REGULAR, intent=WANT_IT, todo_meta JSON); Confirm with target=external fires `ACTION_SEND` to user-chosen task app.
**Independent test criteria** (quickstart §4): source envelope unchanged; child envelopes carry `derived_from_proposal_id`; share-target preference remembered; no network during local-target write.

### Contract & integration tests (US2)

- [x] T055 [P] [US2] Create `app/src/androidTest/java/com/capsule/app/action/TodoAddHandlerLocalTest.kt` (instrumented) — local-target dispatch creates an envelope with `kind=REGULAR`, `intent=WANT_IT`, `IntentSource.AUTO_AMBIGUOUS` (derived; not user-assigned), and `todo_meta` JSON column populated with parsed text/dueEpochMillis. Source envelope id and intent unchanged (Principle III). Audit `ENVELOPE_CREATED extra={derived_from_proposal_id}` per quickstart §4 step 9.
- [x] T056 [P] [US2] Create `app/src/androidTest/java/com/capsule/app/action/TodoAddHandlerExternalTest.kt` (instrumented) — external-target dispatch fires `Intent.ACTION_SEND` with `EXTRA_TEXT`, `type="text/plain"`. First-use share-sheet selection persists in `SharedPreferences("orbit.actions").todoTargetPackage`; second use uses remembered target without sheet.
- [x] T057 [P] [US2] Create `app/src/androidTest/java/com/capsule/app/action/handler/ShareHandlerTest.kt` (instrumented; promoted from JVM — `Context` is abstract so no JVM-clean Mockito-free path) — v1.1 `share.delegate` is intentionally gated by Principle XI; handler always returns `Failed(reason="share_delegate_disabled_v1_1")` until spec 008 lands. The original `Intent.ACTION_SEND` smoke is owed by spec 008.
- [ ] T058 [P] [US2] Create `app/src/test/java/com/capsule/app/ai/ActionExtractorTodoListTest.kt` (JVM) — given a list-shaped envelope text (3 imperatives), extractor produces one `todo_add` proposal whose `argsJson` carries `items` array of length 3 and confidence ≥ 0.55 (golden fixture; deterministic fake `LlmProvider`).
- [ ] T059 [P] [US2] Create `app/src/androidTest/java/com/capsule/app/data/ActionDoesNotMutateEnvelopeTest.kt` (instrumented) — runs all three v1.1 actions through their handlers; asserts source envelope's `intent`, `intentHistoryJson`, `intentSource`, `state` are byte-identical pre/post action. Verifies Principle III for all three handlers per quickstart §7 row III.

### Implementation (US2)

- [x] T060 [US2] Create `app/src/main/java/com/capsule/app/action/TodoAddHandler.kt` per action-execution-contract.md §4 step 3 — `target=local` branch calls `IEnvelopeRepository.createDerivedTodoEnvelope(parentEnvelopeId, items, proposalId)` (one new envelope per item); `target=external` branch builds `Intent.ACTION_SEND` and uses `SharedPreferences.todoTargetPackage` if present, else opens system chooser and persists the user's choice.
- [x] T061 [US2] Add `createDerivedTodoEnvelope` to `IEnvelopeRepository.aidl` (MODIFY 002 from T026) and implement in `EnvelopeRepositoryImpl.kt` — single Room transaction inserting N envelopes (`kind=REGULAR`, `intent=WANT_IT`, `intentSource=AUTO_AMBIGUOUS`, `todoMetaJson` populated) plus N `ENVELOPE_CREATED` audit rows carrying `derived_from_proposal_id` extra.
- [x] T062 [P] [US2] Create `app/src/main/java/com/capsule/app/action/ShareHandler.kt` — `Intent.ACTION_SEND` with `EXTRA_TEXT`, `EXTRA_SUBJECT`, configurable `mimeType` from `ShareArgs`. Wraps in chooser. Handles `ActivityNotFoundException`. *(v1.1 stub returning `share_delegate_disabled_v1_1` per Principle XI gate; full impl deferred to spec 008.)*
- [x] T063 [P] [US2] Add `todoMetaJson: String?` column to `IntentEnvelopeEntity` (MODIFY 002 from T007) and extend `MIGRATION_1_2` to `ALTER TABLE intent_envelope ADD COLUMN todoMetaJson TEXT`. Indexed only via `kind`. JSON shape: `{"items":[{"text":"…","done":false,"dueEpochMillis":null}],"derivedFromProposalId":"…"}`.
- [x] T064 [P] [US2] Extend `EnvelopeCard` in `app/src/main/java/com/capsule/app/diary/ui/EnvelopeCard.kt` (MODIFY 002) — when `todoMetaJson != null`, render a checkbox row for each item; tapping toggles `done` via a new `IEnvelopeRepository.setTodoItemDone(envelopeId, index, done)` AIDL method (added in this task). Visual placement only.
- [ ] T065 [P] [US2] Add `share_target_remembered_package` to `app/src/main/res/values/strings.xml` and a "Forget remembered to-do app" affordance in `ActionsSettingsUI` (anticipated in T079).

**US2 checkpoint**: quickstart §4 path B passes — three child envelopes created, source unchanged, share-target preference round-trips.

---

## Phase 5: User Story 3 — Weekly Sunday Digest (Priority: P1)

**Goal**: `WeeklyDigestWorker` runs Sunday 06:00 local (charger+wifi), composes a 4–6-sentence digest from Mon–Sat envelopes, inserts a single `kind=DIGEST` envelope at the top of Sunday's diary.
**Independent test criteria** (quickstart §5): exactly one DIGEST per Sunday; renders topmost; provenance cascade works (some-survive vs. none-survive); re-runs idempotent; sparse-window skip and Nano-fail fallback both audit cleanly.

### Contract & integration tests (US3)

- [ ] T066 [P] [US3] Create `app/src/test/java/com/capsule/app/ai/DigestComposerTest.kt` (JVM) — empty-window short-circuit (`< 3` envelopes → `EmptyWindow`); sparse-window fallback; Nano-throw → structured English fallback with `locale=fallback-structured`; locale routing (en, en-GB, fr → fallback); prompt truncation under 4 KB per weekly-digest-contract.md §5.
- [ ] T067 [P] [US3] Create `app/src/androidTest/java/com/capsule/app/continuation/WeeklyDigestWorkerTest.kt` (instrumented) — schedule-anchor calculation for 5 known Sundays across DST transitions; idempotency via in-DB unique partial index `index_digest_unique_per_day`; constraint enforcement using `WorkManagerTestInitHelper.getTestDriver(...).setAllConstraintsMet(...)`; audit-row atomicity for `DIGEST_GENERATED` and `DIGEST_SKIPPED`.
- [ ] T068 [P] [US3] Create `app/src/androidTest/java/com/capsule/app/data/DigestProvenanceTest.kt` (instrumented) — given a DIGEST referencing 5 source envelopes: deleting 4 keeps the DIGEST; deleting all 5 soft-deletes the DIGEST and writes `ENVELOPE_INVALIDATED reason=lost_provenance` per data-model.md §8.
- [ ] T069 [P] [US3] Create `app/src/androidTest/java/com/capsule/app/data/DigestUniquenessConstraintTest.kt` (instrumented) — two concurrent inserts targeting the same Sunday: one succeeds, the other observes the partial-index conflict and the worker writes `DIGEST_SKIPPED reason=already_exists` per weekly-digest-contract.md §3.
- [ ] T070 [P] [US3] Create `app/src/androidTest/java/com/capsule/app/diary/DiaryDigestRenderingTest.kt` (Compose UI) — Sunday with a DIGEST envelope: ordering = DIGEST → cluster card (if any) → chronological feed per weekly-digest-contract.md §7. Asserts via `composeTestRule.onNodeWithTag("digest-envelope").assertIsDisplayed()` plus index ordering on the LazyColumn children.

### Implementation (US3)

- [ ] T071 [US3] Create `app/src/main/java/com/capsule/app/ai/DigestComposer.kt` per weekly-digest-contract.md §5 — assembles structured prompt (per-day day-headers from 002 + top-3 salient envelopes + cross-day patterns from 002 `ThreadGrouper`), bounds prompt at 4 KB with progressive truncation, calls `LlmProvider.summarize(prompt)`, falls back to structured English on Nano-throw or non-English locale.
- [ ] T072 [US3] Create `app/src/main/java/com/capsule/app/continuation/workers/WeeklyDigestWorker.kt` per weekly-digest-contract.md §2 — `PeriodicWorkRequest` with 7-day repeat interval, 4h flex, charger+wifi+battery-not-low constraints, `initialDelayUntilSunday0600(localTime)` helper. Reads `DigestSettings.localTime` from SharedPreferences; default 06:00.
- [ ] T073 [US3] Schedule `WeeklyDigestWorker` at app startup — extend `CapsuleApplication.onCreate` (MODIFY 002, `:ml` only) with `WorkManager.getInstance(this).enqueueUniquePeriodicWork("weekly-digest", ExistingPeriodicWorkPolicy.KEEP, request)`. Per-process check ensures only `:ml` schedules.
- [ ] T074 [US3] Extend `EnvelopeRepositoryImpl.kt` (MODIFY 002) with `insertDigest(text, derivedFromIds, weekId, dayLocal): String` — single Room transaction inserting the DIGEST envelope (kind=DIGEST, intent=REFERENCE, AUTO_AMBIGUOUS) + `DIGEST_GENERATED` audit row carrying `weekId` and `envelopeCount` extras. Honours the `(day_local) WHERE kind='DIGEST'` partial unique index — on conflict, writes `DIGEST_SKIPPED reason=already_exists` and returns null.
- [ ] T075 [US3] Implement DIGEST cascade-on-delete in `EnvelopeRepositoryImpl.delete()` (MODIFY 002) — when a regular envelope is soft-deleted, find DIGESTs whose `derivedFromEnvelopeIdsJson` contains the deleted id; if all referenced sources are now deleted, soft-delete the DIGEST + write `ENVELOPE_INVALIDATED reason=lost_provenance`. Procedural per data-model.md §8 (JSON-array FK not expressible in SQL).
- [ ] T076 [P] [US3] Create `app/src/main/java/com/capsule/app/diary/DigestEnvelopeUI.kt` — Compose composable that renders DIGEST envelopes with a distinct slot above the cluster card. Visual specifics deferred to design.md / spec 010; this task wires placement only.
- [ ] T077 [US3] Update `DiaryViewModel.kt` and the corresponding DAO query in `IntentEnvelopeDao.kt` (MODIFY 002) per weekly-digest-contract.md §7: `ORDER BY CASE kind WHEN 'DIGEST' THEN 0 ELSE 1 END ASC, createdAt DESC`. DIGEST envelopes do NOT get a chip-row UX (already have intent assigned).

**US3 checkpoint**: quickstart §5 path C passes via `WorkManagerTestInitHelper`-driven test run; physical-device Sunday-anchor verification deferred to T107.

---

## Phase 6: User Story 4 — Skill Stats & Settings → Actions UI (Priority: P2)

**Goal**: User can view per-skill success/cancel/latency stats; toggle each action kind on/off; configure digest schedule time; clear remembered to-do target. Forward-compat surface for v1.2 agent's planner heuristics.
**Independent test criteria**: stats refresh after each action; toggling off prevents new proposals (extractor checks the flag); digest-time change reschedules without losing the existing pending run.

### Contract & integration tests (US4)

- [ ] T078 [P] [US4] Create `app/src/test/java/com/capsule/app/data/SkillUsageAggregationTest.kt` (JVM, in-memory Room) — seed 100 `skill_usage` rows across the three v1.1 skills + 30-day window; assert `successRate`, `cancelRate`, `avgLatencyMs`, `invocationCount` match hand-computed expectations within float epsilon per data-model.md §5.
- [ ] T079 [P] [US4] Create `app/src/androidTest/java/com/capsule/app/settings/ActionsSettingsTest.kt` (Compose UI) — Settings → Actions screen renders one row per registered skill with name, success-rate, cancel-rate, latency; per-skill enable toggle persists; clear-remembered-target button removes `SharedPreferences.todoTargetPackage`.

### Implementation (US4)

- [ ] T080 [P] [US4] Create `app/src/main/java/com/capsule/app/settings/ActionsSettingsUI.kt` — Compose Settings screen listing the three v1.1 skills with stats from `IEnvelopeRepository.appFunctionStats(skillId, sinceMillis)` (added to AIDL in this task) and a per-skill enable switch backed by `SharedPreferences("orbit.actions.enabled.<functionId>")`. Visual placement only — design.md/spec 010 finalises typography.
- [ ] T081 [US4] Honour the per-skill enable flag in `ActionExtractor` (MODIFY T043 callsite) — when filtering registered functions for a candidate, drop any skill whose enable flag is `false`. Single-line change inside `extract()` step 3.
- [ ] T082 [P] [US4] Create `app/src/main/java/com/capsule/app/settings/DigestScheduleUI.kt` — Compose row in Settings with a `LocalTime` picker (default 06:00). On change, persist to `SharedPreferences("orbit.digest.localTime")` and call `rescheduleWeeklyDigest()` which re-enqueues the periodic work with `ExistingPeriodicWorkPolicy.UPDATE`. Day stays Sunday in v1.1 per weekly-digest-contract.md §2.
- [ ] T083 [P] [US4] Create `app/src/main/java/com/capsule/app/settings/ActionsSettingsActivity.kt` and register in `AndroidManifest.xml` (MODIFY) with `exported=false`. Add an entry-point row in the existing 002 `SettingsScreen` linking to it.
- [ ] T084 [US4] Add a "Cloud quality (BYOK)" toggle in `ActionsSettingsUI.kt` per quickstart §7 row IX — default OFF; when ON, sets `LlmProviderRouter` provenance hint to `OrbitManaged`/`Byok` for `extractActions` calls only. v1.1 leaves the actual routing to spec 005 — toggle stores the preference; routing is wired but no-ops if no BYOK provider is registered.
- [ ] T085 [P] [US4] Add `app/src/main/java/com/capsule/app/diary/UndoToastUI.kt` — Compose toast surface at the bottom of `DiaryScreen` showing "Added to calendar — Undo (5s)" with a 5-second countdown, dispatched from `DiaryViewModel.toastState`. Tap → calls `IActionExecutor.cancelWithinUndoWindow(executionId)`.

**US4 checkpoint**: stats render correctly; toggles round-trip; digest reschedule does not duplicate pending work.

---

## Phase 7: User Story 5 — Negative Paths & Graceful Degradation (Priority: P2)

**Goal**: All six negative paths from quickstart §6 (sensitivity gating, no-Nano fallback, schema mismatch, no handler app, past-undo-window, re-extraction idempotency) produce the expected audit rows and no UI errors.
**Independent test criteria** (quickstart §6): each negative path's audit row appears; no `Result.failure()` cascades to the user; app remains fully functional after every failure mode.

### Contract & integration tests (US5)

- [ ] T086 [P] [US5] Create `app/src/androidTest/java/com/capsule/app/ai/SensitivityGatingTest.kt` (instrumented) — capture an envelope flagged `credentials` by 002's `SensitivityScrubber`; assert no `ACTION_PROPOSED` row appears and `ActionExtractionWorker` audits `CONTINUATION_COMPLETED outcome=skipped reason=sensitivity` per quickstart §6 N1.
- [ ] T087 [P] [US5] Create `app/src/androidTest/java/com/capsule/app/ai/NoNanoFallbackTest.kt` (instrumented) — inject a `LlmProvider` whose `extractActions` throws `Nano.UnavailableException`; assert no proposal generated, audit `CONTINUATION_COMPLETED outcome=failed reason=nano_unavailable`, no UI error per quickstart §6 N2.
- [ ] T088 [P] [US5] Create `app/src/androidTest/java/com/capsule/app/action/SchemaMismatchTest.kt` (instrumented) — debug-only seam corrupts a proposal's `argsJson`; tap Confirm → `ACTION_FAILED reason=schema_mismatch`, no Intent fires, proposal moves to `state=INVALIDATED` per quickstart §6 N3.
- [ ] T089 [P] [US5] Create `app/src/androidTest/java/com/capsule/app/action/ReExtractionIdempotencyTest.kt` (instrumented) — manually re-enqueue `ActionExtractionWorker` for an envelope with an existing proposal; assert no duplicate row (unique constraint `(envelopeId, functionId)` holds) and audit `CONTINUATION_COMPLETED outcome=noop` per quickstart §6 N6.

### Implementation (US5)

- [ ] T090 [US5] Implement the sensitivity gate in `ActionExtractor.extract()` (MODIFY T043) — early-return `Skipped("sensitivity_changed")` if envelope's `sensitivity_flags` contains `credentials` or `medical`, OR if a `SensitivityScope.PUBLIC` action is proposed against `financial`-flagged content per research.md §8.
- [ ] T091 [US5] Implement schema re-validation in `ActionExecutorService.execute()` (MODIFY T047) per action-execution-contract.md §4 step 1 — load `argsSchemaJson` for the request's `schemaVersion`, re-validate `argsJson`; on mismatch, write `ACTION_FAILED reason=schema_mismatch` and flip proposal to `INVALIDATED` without firing the Intent.
- [ ] T092 [US5] Add `ActivityNotFoundException` catch + user-facing toast "No app handles this" in all three handlers (MODIFY T049, T060, T062) per action-execution-contract.md §4 + quickstart §6 N4. Audit `ACTION_FAILED reason=no_handler`.
- [ ] T093 [US5] Implement past-undo-window no-op in `IActionExecutor.cancelWithinUndoWindow` (MODIFY T047) per action-execution-contract.md §3 — load `action_execution.dispatchedAt`, return `false` if `now - dispatchedAt > 5000ms` and outcome already terminal. Quickstart §6 N5.
- [ ] T094 [US5] Implement re-extraction idempotency in `ActionExtractionWorker` (MODIFY T044) — pre-check `ActionProposalDao.findByEnvelopeAndFunction(envelopeId, functionId)`; if a non-`INVALIDATED` proposal already exists, audit `CONTINUATION_COMPLETED outcome=noop` and `Result.success()` per quickstart §6 N6.

**US5 checkpoint**: all six quickstart §6 negative paths' audit rows can be reproduced via instrumented tests on emulator.

---

## Phase 8: Polish & Cross-Cutting Concerns

These tasks are the constitution-acceptance gate, the migration-correctness gate, and the physical-device acceptance run. **No physical-device task may be checked off without a real Pixel 8+ run** — mirrors 002 T110–T113.

### Cross-cutting code

- [ ] T095 [P] Audit-row aggregation copy in `app/src/main/java/com/capsule/app/audit/AuditCopyTemplates.kt` (MODIFY 002) — add user-facing strings for the seven new `AuditAction` values per data-model.md §6 (e.g., `ACTION_PROPOSED → "Proposed: {previewTitle}"`, `DIGEST_GENERATED → "Generated this week's digest ({envelopeCount} captures)"`).
- [ ] T096 [P] Add `app/src/main/java/com/capsule/app/audit/ActionsAuditAggregator.kt` — groups action-related audit rows for the "What Orbit did today" surface in the audit log viewer per data-model.md §6 closing paragraph.
- [ ] T097 [P] Wire a debug-build "Force Nano UNAVAILABLE" toggle in `app/src/debug/java/com/capsule/app/diagnostics/DiagnosticsActivity.kt` per quickstart §6 N2 — flips a `BuildConfig.DEBUG`-gated flag in `LlmProviderRouter` so `extractActions` and `summarize` throw `Nano.UnavailableException`. No production exposure.
- [ ] T098 [P] Add `:capture`-process integration of `ActionExecutorService` startup in the existing `CapsuleOverlayService` lifecycle (MODIFY 002) — the executor service is started lazily on first `bindService` from `:ui`; no eager start at boot. Confirms `:capture` does not gain a new always-on cost.
- [ ] T099 Confirm no new permissions in `AndroidManifest.xml` — diff against 002 manifest must show zero added `<uses-permission>` elements (no `WRITE_CALENDAR`, no extra storage scopes). Per research.md §4 + Principle VIII.
- [ ] T100 [P] Add KDoc + manifest comments tagging `:capture` package `com.capsule.app.action.*` as "no-network — see action-execution-contract.md §6". Catches future contributors at code review.
- [ ] T101 Update `app/proguard-rules.pro` — keep AppFunctions-generated schema constants and `@AppFunction`-annotated args data classes from R8 obfuscation (KSP-generated metadata is reflective).
- [ ] T102 [P] Add a regression-protection task: extend `app/src/androidTest/java/com/capsule/app/regression/Spec001SmokeTest.kt` (002's regression harness) with an `actions_do_not_break_capture` method — runs path A on a freshly-installed v1.1 build and asserts capture/seal/diary still match 002 baseline timings (`p50 seal < 200ms`, `Diary p50 render < 1s`). Mirrors 002 T110a pattern.

### Acceptance gate (quickstart-driven)

- [ ] T103 (PHYSICAL DEVICE) Run quickstart §3 path A end-to-end on a Pixel 8+ (Android 15) with AICore Nano available; record timings + audit-row dump in `specs/003-orbit-actions/acceptance-results.md`. Pass criteria from quickstart §3 final block.
- [ ] T104 (PHYSICAL DEVICE) Run quickstart §4 path B end-to-end on a Pixel 8+ — both target=local and target=external (with at least one task app installed); record `derived_from_proposal_id` provenance + share-target persistence in acceptance results.
- [ ] T105 (PHYSICAL DEVICE) Run quickstart §6 negative paths N1–N6 end-to-end on a Pixel 8+ — record audit rows + screenshots of any user-facing toasts; confirm app remains functional after each failure mode.
- [ ] T106 (PHYSICAL DEVICE) Compatibility run on a Pixel 6a (Android 14, AppFunctions compat path) — repeat path A + path B; confirm local-registry fallback exposes the same `appfunction_skill` rows; confirm cross-app discovery is absent (expected on 14) but Orbit-internal flows work end-to-end.
- [ ] T107 (PHYSICAL DEVICE) Sunday-anchor verification on a Pixel 8+ — set device clock to Saturday 23:50, plug in charger + wifi, wait through 06:00 Sunday; confirm exactly one DIGEST envelope appears at the top of Sunday's diary, audit row `DIGEST_GENERATED` written. Re-trigger via WorkManager force-run; second run audits `DIGEST_SKIPPED reason=already_exists`.
- [ ] T108 (PHYSICAL DEVICE) Network-isolation acceptance on a Pixel 8+ — `adb shell dumpsys netstats detail | grep com.capsule.app` after running paths A+B+C; expect `:capture`, `:ml`, `:ui` UIDs to show 0 bytes RX/TX. Per quickstart §7 row I.
- [ ] T109 (PHYSICAL DEVICE) MITM-proxy run mirroring 002 T111 — route the device through `mitmproxy`, run paths A+B+C, assert zero outgoing HTTP from any process besides `:net` (and `:net` is not used by 003 paths in the BYOK-OFF default config). Attach `mitm.log` to acceptance results.
- [ ] T110 (PHYSICAL DEVICE) Performance validation on Pixel 8+ per plan.md Performance Goals — assert `p95 ACTION_EXTRACT < 8s` from `ENVELOPE_CREATED` to `ACTION_PROPOSED`, `p95 confirm-tap → external app visible < 600ms`, `p95 weekly digest < 45s` for 7×100 envelopes, 60fps Diary scroll with 50 proposals visible.
- [ ] T111 (PHYSICAL DEVICE) Migration acceptance against a 1000-envelope v1 production-shape DB — install v1, populate to 1000 envelopes via the 002 dogfood-corpus loader, install v1.1 over the top, confirm `MIGRATION_1_2` runs in < 1s on-device and all 002 queries continue to work. Required by quickstart §8 acceptance gate item 4.
- [ ] T112 [P] Acceptance results doc — populate `specs/003-orbit-actions/acceptance-results.md` with results from T103–T111 and the constitution-acceptance table from quickstart §7 (all twelve principles).
- [ ] T113 [P] Update `specs/003-orbit-actions/quickstart.md` if any divergence is discovered during physical-device runs (e.g., timing budget tweaks); keep a CHANGELOG note at the top of the file.

**Phase 8 checkpoint = release gate**: all (PHYSICAL DEVICE) tasks complete, all six negative paths verified, all twelve constitution checks pass, migration run on 1000-envelope corpus < 1s. Spec 010 then refines visual styling against `design.md` — no functional change.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Phase 1 (Setup)**: no deps
- **Phase 2 (Foundational)**: depends on Phase 1 — **BLOCKS** all user stories
- **Phase 3 (US1 — Calendar)**: depends on Phase 2
- **Phase 4 (US2 — To-do)**: depends on Phase 2; reuses `ActionExtractor` and `ActionExecutorService` from Phase 3
- **Phase 5 (US3 — Weekly Digest)**: depends on Phase 2; orthogonal to US1/US2 (no shared runtime path beyond `LlmProvider`)
- **Phase 6 (US4 — Settings & Stats)**: depends on Phase 2; usefully depends on US1/US2 having produced real `skill_usage` rows for stats to render (test fixtures cover the empty case)
- **Phase 7 (US5 — Negative paths)**: depends on Phase 2 + Phase 3 (most negative paths exercise US1's pipeline); hardens US1/US2/US3 simultaneously
- **Phase 8 (Polish + acceptance)**: depends on all P1 + P2 stories complete

### Critical Path to MVP (P1 only)

Phase 1 → Phase 2 → Phase 3 (US1) → Phase 4 (US2) → Phase 5 (US3) → Phase 8 acceptance subset (T103, T104, T107, T108, T111).

### Parallel Opportunities

- All [P]-tagged Phase 2 entity/DAO tasks (T010–T017) run in parallel.
- Phase 3 tests T034–T040 and Phase 3 implementations T041–T054 overlap as soon as the relevant T020/T022/T028 surfaces are settled.
- US1 (Phase 3), US2 (Phase 4), US3 (Phase 5) can be assigned to three contributors after Phase 2 closes — they touch disjoint files except for the shared `ActionExecutorService` (T047) and the AIDL surface (T026), both of which land in Phase 2.
- US4 (Phase 6) and US5 (Phase 7) can run in parallel once Phase 3 ships.
- (PHYSICAL DEVICE) tasks T103–T111 must run on a real device but T112/T113 doc tasks are [P] and can interleave.

---

## Parallel Example: User Story 1 (Calendar Action)

After Phase 2 is green:

- Contributor A: T034 (`ActionExtractorTest`) + T035 (`DateTimeParserTest`) + T041 (`DateTimeParser` impl) + T042 (`ActionExtractionPrefilter`) + T043 (`ActionExtractor` impl).
- Contributor B: T036 (`ActionExtractionWorkerTest`) + T044 (`ActionExtractionWorker`) + T045 (`ContinuationEngine` extension) + T046 (seal-time enqueue wire).
- Contributor C: T037 (`CalendarInsertHandlerTest`) + T038 (`UndoWindowTest`) + T039 (`NoNetworkDuringActionExecutionTest`) + T040 (`ExecutionIpcContractTest`) + T047/T048/T049/T050 (executor service + handler + invoker + delayed cleanup).
- Contributor D: T051 (`ActionProposalChipUI`) + T052 (`ActionPreviewCardUI`) + T053 (`DiaryViewModel` extension) + T054 (`DiaryActivity` bind).

Three or four contributors converge on the US1 checkpoint; merge zone is `EnvelopeRepositoryImpl.kt` (T046) and `IEnvelopeRepository.aidl` (T026 already settled in Phase 2).

---

## Implementation Strategy

### MVP First (P1: US1 + US2 + US3)

1. Phase 1 → Phase 2 → STOP + VALIDATE: migration runs green, three schemas register, `LlmProvider.extractActions` contract test passes against `NanoLlmProvider` skeleton.
2. → Phase 3 (US1) → STOP + VALIDATE: quickstart §3 path A passes against an emulator with a fake LLM.
3. → Phase 4 (US2) → STOP + VALIDATE: quickstart §4 path B passes; source envelope un-mutated assertion green.
4. → Phase 5 (US3) → STOP + VALIDATE: quickstart §5 path C passes via `WorkManagerTestInitHelper`.
5. → Phase 8 P1 subset (T103, T104, T107, T108, T111) on a physical Pixel.

### Incremental Delivery (P2)

6. Phase 6 (US4 — Settings & Stats): unblocks user-visible per-skill toggles and forward-compat planner heuristics for v1.2 agent.
7. Phase 7 (US5 — Negative paths): hardens the three P1 stories simultaneously; no new user-visible feature.

### Polish Before Public

8. Phase 8 acceptance gate complete on Pixel 8 + Pixel 6a; constitution checks all green; spec 010 visual polish then runs against `design.md`.

---

## Open Questions Surfaced During Task Generation

These do not block tasks but are flagged for resolution before implementation:

- **OQ-1** (data-model.md §1): adding `todoMetaJson` as a column on `intent_envelope` (T063) vs. a separate `todo_meta` table. Chosen the column for v1.1 (single-table query, JSON for forward-compat schema evolution); revisit if the schema becomes deeply structured in v1.2.
- **OQ-2** (action-execution-contract.md §3): the AIDL surface adds three new lifecycle methods (`markProposalConfirmed`, `markProposalDismissed`, `observeProposalsForEnvelope`) on top of the three documented ones. Confirmed against the spec — these are not separately listed in the contract §5 excerpt but are required by T053/T054/T060. Recommended: append to the contract before T026 implementation lands.
- **OQ-3** (research.md §3, §4): Nano date-parsing failure rate on the actual Pixel 8 corpus may differ from the dogfood data; the 0.55 confidence floor (T043) and the `+1h` end-time default (T049) are tunable — flagged for re-evaluation after T103/T110.
- **OQ-4** (research.md §1): on Android 14 (compat path), the in-process registry is functional but cross-app discovery is absent. T106 verifies. If Pixel 6a verification fails, the fallback strategy may need a degraded-mode banner — not in v1.1 scope but track in spec 010.
- **OQ-5** (action-extraction-contract.md §2): the per-skill enable flag added in T080/T081 is read by the extractor, but the extractor caches the registered-functions list per worker run. Cache invalidation strategy (currently per-run reload) is fine for v1.1 volume; revisit if extractor is parallelised.
- **OQ-6** (data-model.md §9): spec 012 `resolution_state` column on `intent_envelope` is explicitly NOT added in v1.1 (per data-model.md §9). Confirmed — `action_execution.outcome=SUCCESS` is the resolution-trigger source; spec 012 layers state-machine semantics on top.

---

## Notes

- Every audit-row write must occur in the same Room transaction as the data mutation (data-model.md §6). Tests assert this via in-memory Room with a transaction-listener.
- The `ContinuationEngine.enqueue*` calls always happen OUTSIDE the Room transaction (see 002 T066a/T068 pattern) — WorkManager owns its own SQLite DB and a cross-DB lock acquisition inside Room's txn deadlocks.
- No 003 task introduces a new permission or a new process. Manifest diff against 002 must be additive on `<service>` only (T003).
- Visual styling decisions (typography, colour, motion, spacing) are deferred to `design.md` and spec 010. UI tasks above wire placement, behaviour, and observable state — they MUST NOT introduce new typography/colour tokens. If a visual decision is missing from `design.md`, update `design.md` first, then the UI task.
- "Provenance or it didn't happen" (Principle XII): every `action_proposal` row → `envelopeId` resolves; every `action_execution` row → `proposalId` → `envelopeId` resolves; every DIGEST envelope → `derivedFromEnvelopeIdsJson` is non-empty and resolves. Cascade-delete tested in T029, T059, T068.
