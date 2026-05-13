# Tasks: Capture Feedback Actions

**Input**: [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md)
**Branch**: `017-capture-feedback-actions` (off fresh `origin/main`)
**Workflow**: Spec Kit sequence is specify -> plan -> tasks -> implement. This
task list has passed a `speckit.analyze` review pass on 2026-05-11; rerun the
analysis gate after Phase 1 schema details are finalized.

## Format: `[ID] [P?] [Phase] Description`

## Phase 0 - Branch hygiene and planning

- [x] **T017-001** Create/switch to branch `017-capture-feedback-actions` with
  the Spec Kit git extension once the current dirty worktree has been split or
  stashed. Do not develop this on `015-phase1-cluster-surface`.
- [x] **T017-002** Run `speckit.analyze` after this draft plan to confirm task
  order, contract artifacts, and migration details before implementation.
- [x] **T017-003** Confirm dependency state: spec 016 merged or explicitly
  rebased if intent picker/reclassify surfaces are touched.
- [x] **T017-004** Set `.specify/feature.json` to `specs/017-capture-feedback-actions`
  so Spec Kit commands operate on this feature.

## Phase 0A - Overlay feedback hardening

- [x] **T017-010** Update `PostCaptureOverlay.kt` so only visibly full-width chip
  rows use `fillMaxWidth`; compact states use wrapped content bounds.
- [x] **T017-011** Update `CapsuleOverlayService.kt` so chip rows use
  `MATCH_PARENT`, compact post-capture states use `WRAP_CONTENT`, and the overlay
  window includes `FLAG_NOT_TOUCH_MODAL` so outside taps pass through.
- [x] **T017-012** Update bubble drag/dismiss geometry to compute live bounds
  from `currentWindowMetrics` with display-metrics fallback during drag and drag
  end, instead of using stale setup-time portrait dimensions.
- [x] **T017-013** Verify Phase 0A gates: `:app:compileDebugKotlin`,
  `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`.
- [x] **T017-014** Install on S24 and Tab S9 and physically verify adjacent app
  icon taps outside the Undo pill, chip-row taps, and landscape drag/remove.
  - [x] Adjacent app/right-row taps outside the Undo pill confirmed on physical
    device feedback, 2026-05-11; centered compact pill is accepted.
  - [x] Landscape drag/remove physical pass confirmed on physical device
    feedback, 2026-05-11.
- [x] **T017-015** Commit Phase 0A as a small capture-hardening commit before the
  larger duplicate-capture schema work begins.

## Phase 1 - Duplicate keys and repository contract

- [x] **T017-101** Add envelope-level URL duplicate key support. Preferred shape:
  `primaryCanonicalUrlHash` on `IntentEnvelopeEntity` with Room migration and an
  index for non-deleted/non-archived lookup. For multi-URL captures, use the first
  URL returned by `ContinuationEngine.extractUrls(...)` as the v1 primary key.
- [x] **T017-102** Populate `textContentSha256` for exact non-URL text captures.
- [x] **T017-103** Add DAO queries for duplicate URL and exact-text matches,
  excluding deleted and archived envelopes for v1.
- [x] **T017-104** Replace `seal(): String` boundary with a typed seal result
  across repository, AIDL/Binder, orchestrator, and ViewModel layers.
- [x] **T017-105** Add audit action/event for duplicate capture attempts.
- [x] **T017-106** Preserve existing URL hydration result reuse: duplicate logic
  must not remove `sharedContinuationResultId` behavior or continuation-result
  canonical-hash cache hits.

## Phase 2 - Overlay feedback actions

- [x] **T017-201** Add `SealOutcome.AlreadySaved(existingEnvelopeId, matchedBy)`
  and migrate/adapt the existing `PostCaptureUi.AlreadyInDiary` naming so there
  is one duplicate-feedback state, not two competing concepts.
- [x] **T017-202** Implement compact `Already saved` feedback with actions:
  add note, reclassify intent, open existing capture.
- [x] **T017-203** Ensure compact feedback windows use visible-content touch
  bounds only; adjacent launcher/app icons must remain tappable.
- [x] **T017-204** Wire reclassify to update the existing envelope's intent
  history rather than creating a new envelope.
- [x] **T017-205** Specify and implement note persistence if notes are not
  already implemented. Use the `EnvelopeNoteEntity` shape in
  [data-model.md](data-model.md); do not add an ad-hoc note field outside spec
  017.
- [x] **T017-206** Add `envelope_note` persistence in the feedback-action
  migration and route `Already saved` note action to the existing envelope.

## Phase 3 - Tests and physical QA

- [x] **T017-301** Repository test: same canonical URL twice returns
  `AlreadySaved`, creates no second visible envelope, writes exactly one
  duplicate audit row with `existingEnvelopeId` + `matchedBy`, and does not log
  raw content or full URLs.
- [x] **T017-302** Repository test: same exact non-URL text twice returns
  `AlreadySaved`, creates no second visible envelope, writes exactly one
  duplicate audit row with `existingEnvelopeId` + `matchedBy=EXACT_TEXT`, and
  does not log raw text or full URLs.
- [x] **T017-303** UI/orchestrator test: `AlreadySaved` state exposes note,
  reclassify, and open actions; reclassify appends intent history on the existing
  envelope and creates no second envelope. Note tests must cover create/edit of
  the latest note on the existing envelope and confirm no ad-hoc note field is
  introduced. Open-existing tests must tap the action and assert navigation targets
  `existingEnvelopeId` without creating a new envelope.
- [x] **T017-304** Physical QA on S24 and Tab S9: duplicate URL, duplicate text,
  adjacent icon tap outside pill, portrait and landscape.
  - [x] S24 physical duplicate URL pass confirmed `Already saved`, 2026-05-11.
  - [x] S24 physical duplicate exact-text pass confirmed `Already saved`,
    2026-05-11.
  - [x] Tab S9 physical duplicate URL pass confirmed `Already saved`,
    2026-05-11.
  - [x] Tab S9 physical duplicate exact-text pass confirmed `Already saved`,
    2026-05-11.
  - [x] Adjacent tap pass-through outside compact feedback/undo pill confirmed
    on physical device feedback, 2026-05-11.
  - [x] Landscape drag/remove and bounds behavior confirmed on physical device
    feedback, 2026-05-11.
- [x] **T017-305** Verification gate: `:app:compileDebugKotlin`,
  `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`.
- [x] **T017-306** Regression test: existing URL hydration dedupe contract still
  reuses continuation results after envelope-level duplicate handling is added.
- [x] **T017-307** Repository tests: deleted duplicate candidates do not block;
  archived duplicate candidates do not block in v1.
- [x] **T017-308** Verify duplicate lookup performance/query shape: URL and text
  duplicate checks must use indexed lookup columns and avoid raw full-text scans;
  seed at least 1,000 visible envelopes plus archived/deleted controls in a Room
  fixture, run at least 100 repeated URL and text lookups, and keep p95 under
  50 ms or explicitly document any measured exception.

## Branch Debt Closeout Reconciliation - 2026-05-13

- Dependency state: spec 016 is PR-ready in replacement PR #19, while final landing remains deferred pending PR review/merge.
- Staged file list captured before any commit/split: `UrlHashDedupeContractTest.kt`, `EnvelopeRepositoryImpl.kt`, `EnvelopeStorageBackend.kt`, `LocalRoomBackend.kt`, `OrbitMigrations.kt`, and `IntentEnvelopeDao.kt`.
- Staged diff stat: 6 files changed, 129 insertions, 41 deletions.
- Ownership check: staged files align with duplicate capture/data ownership: URL hash duplicate matching, exact-text duplicate matching, active non-deleted/non-archived lookup, Room migration, storage backend/DAO routes, and URL-hash dedupe contract coverage.
- Reroute check: no staged visual-only `015`, ContactRef `016`, or future `004` through `012` files were found.
- Android gate passed: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `:app:lintDebug` succeeded.
- Duplicate audit metadata check: duplicate attempts record `existingEnvelopeId` and `matchedBy` in audit `extraJson`; no raw text or full URL is added to that duplicate audit payload.
- Schema verification result: matching schema/entity/database files are already committed in the `017` branch history. Full `origin/main...HEAD` includes `app/schemas/com.capsule.app.data.OrbitDatabase/6.json`, `app/schemas/com.capsule.app.data.OrbitDatabase/7.json`, `OrbitDatabase.kt`, `IntentEnvelopeEntity.kt`, `EnvelopeNoteEntity.kt`, `EnvelopeNoteDao.kt`, `SealResultParcel`, storage/backend changes, and `OrbitMigrations.kt`. The current staged `OrbitMigrations.kt` edit is a follow-up migration adjustment, not an orphaned schema change.
- Focused duplicate tests passed: `:app:testDebugUnitTest --tests com.capsule.app.overlay.PostCaptureOverlayBoundsRegressionTest` and connected `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.capsule.app.data.UrlHashDedupeContractTest,com.capsule.app.overlay.OverlayDuplicateFeedbackTest,com.capsule.app.data.DuplicateLookupPerformanceContractTest` on SM-X710, 14 connected tests.

## PR Review Fixes - 2026-05-13

- Self-review finding resolved: duplicate capture prevention now has database-backed active duplicate keys. `activePrimaryCanonicalUrlHash` and `activeTextContentSha256` are unique nullable columns, while durable hash columns remain available for display/backfill. Archive and soft-delete clear active keys so inactive rows do not block new saves.
- Race regression added: concurrent same-URL seal attempts return one `CREATED` result and the rest `ALREADY_SAVED`, with a single visible envelope and duplicate audit rows for the losing attempts.
- Exact-text lookup finding resolved: exact-text duplicate checks now query `activeTextContentSha256`, which is covered by a unique Room index in the exported v7 schema.
- Migration coverage added: `OrbitDatabaseMigrationV5toV7Test` validates v5-to-v7 upgrade behavior, URL hash backfill, text-key de-duplication during migration, unique active-key enforcement, and the `envelope_note` table. `androidTest` assets now include checked-in Room schemas for `MigrationTestHelper`.
- Updated gates passed with explicit local environment (`JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `ANDROID_HOME=/Users/richelgomez/Library/Android/sdk`): `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest --tests com.capsule.app.overlay.PostCaptureOverlayBoundsRegressionTest`, focused `:app:connectedDebugAndroidTest` for `UrlHashDedupeContractTest`, `DuplicateLookupPerformanceContractTest`, `OrbitDatabaseMigrationV5toV7Test`, `OverlayDuplicateFeedbackTest` on SM-S928U1 and SM-X710, and `:app:lintDebug`.

## Landing Map

- Spec 015: visual refit, shared source glyph resolver, settings subpage visual
  audit, chip/undo/bubble styling.
- Spec 016: durable intent enum/label migration and contact-ref schema.
- Spec 017: duplicate capture product behavior, already-saved actions, note or
  reclassify behavior, and compact feedback touch contract.
