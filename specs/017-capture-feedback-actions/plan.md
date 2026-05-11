# Implementation Plan: Capture Feedback Actions

**Branch**: `017-capture-feedback-actions` | **Date**: 2026-05-11 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/017-capture-feedback-actions/spec.md`

## Summary

Add envelope-level duplicate detection and a typed post-capture feedback result.
The existing URL hydration cache remains intact, but it is no longer mistaken
for user-visible dedupe. A second capture of the same canonical URL or exact
text returns `AlreadySaved(existingEnvelopeId, matchedBy)` and opens a compact
action surface for note, reclassify, or open existing capture.

## Technical Context

- **Language/Version**: Kotlin 2.x Android app.
- **Primary Dependencies**: Room/SQLCipher, Jetpack Compose, existing AIDL/Binder
  repository boundary, existing `ContinuationEngine` and `CanonicalUrlHasher`.
- **Storage**: SQLCipher Room database (`OrbitDatabase`). Expected schema changes
  for `primaryCanonicalUrlHash` and note persistence; `textContentSha256` already
  exists but is not currently populated in the seal path.
- **Testing**: JVM unit tests, Room migration/instrumented tests as needed,
  physical QA on S24 and Tab S9.
- **Target Platform**: Android 13+.
- **Project Type**: Mobile app.
- **Performance Goals**: Indexed duplicate lookup p95 stays under 50 ms in
  repository tests or physical-device QA on S24/Tab S9; no full-text scans in
  the seal path.
- **Constraints**: Preserve undo semantics; do not remove hydration-result reuse;
  typed seal result must cross process boundaries without stringly encoding.
- **Scale/Scope**: Capture seal path, repository/AIDL boundary, overlay feedback
  UI, duplicate audit row, and tests.

## Constitution Check

- **Principle II (Effortless Capture, Any Path)**: duplicate recognition and
  compact correction actions reduce save uncertainty without adding a modal flow.
  PASS.
- **Principle III (Intent Before Artifact)**: `Already saved` gives the user a
  correction path before creating redundant artifacts; reclassify appends a new
  intent layer to the existing envelope. PASS.
- **Principle VI (Privilege Separation By Design)**: typed seal results still
  cross the existing `:capture` -> `:ml` Binder boundary; no network access is
  added to capture or ML processes. PASS.
- **Principle VIII (Collect Only What You Use)**: duplicate keys are hashes used
  directly for user-visible duplicate prevention; no raw content or full URLs are
  added to audit metadata. PASS.

No constitutional violations to justify in Complexity Tracking.

## Project Structure

### Documentation

```text
specs/017-capture-feedback-actions/
├── spec.md
├── plan.md
├── data-model.md
└── tasks.md
```

Contracts should be added under `contracts/` if the AIDL shape becomes larger
than a simple parcel addition.

### Source Code

```text
app/src/main/java/com/capsule/app/
├── data/
│   ├── EnvelopeRepositoryImpl.kt
│   ├── dao/IntentEnvelopeDao.kt
│   ├── entity/IntentEnvelopeEntity.kt
│   └── model/AuditAction.kt
├── data/ipc/                         # AIDL/Binder typed seal result
├── overlay/                          # SealOutcome/PostCaptureUi + UI actions
└── service/CapsuleSealOrchestrator.kt

app/src/androidTest/java/com/capsule/app/data/   # migration/repository tests
app/src/test/java/com/capsule/app/               # pure unit tests where possible
```

## Phase Plan

### Phase 0 - Overlay feedback hardening

Land the low-risk physical-device fix first: compact post-capture windows use
visible-content bounds, chip rows remain visibly full-width, and bubble drag uses
live window metrics after rotation. This preserves existing visuals and prepares
the same touch contract the future `Already saved` state will use.

### Phase 1 - Duplicate keys, migration, and typed result

Add durable duplicate keys and DAO lookups. Use `primaryCanonicalUrlHash` for the
first extracted URL and populated `textContentSha256` for exact non-URL text
captures. Author Room migrations and indexes, then carry `Created` vs
`AlreadySaved` across repository, Binder, orchestrator, and ViewModel layers.

### Phase 2 - Feedback state and compact UI

Adapt the compact post-capture UI to present duplicate captures as `Already
saved` instead of undoable new saves. Keep visible-content touch bounds for all
compact states.

### Phase 3 - Feedback actions and notes

Add `Already saved` UI state with note, reclassify, and open-existing actions.
Note persistence is modeled in [data-model.md](data-model.md) and lands in the
feedback-action migration after the duplicate-key migration.

### Phase 4 - Verification and physical QA

Run unit, migration, lint, and physical-device QA. Confirm adjacent app icons are
tappable outside compact feedback pills in portrait and landscape.

## Risk And Rollback

- **Highest risk**: AIDL/Binder result changes. Mitigation: additive parcel
  shape, a legacy `seal()` wrapper during migration, and compile all processes
  together.
- **Schema risk**: duplicate key migration. Mitigation: forward-only migration,
  exported schema, migration tests.
- **UX risk**: duplicate action copy implying a new save. Mitigation: separate
  `Already saved` state and no undo wording for duplicate attempts.

Rollback requires reverting schema/code together before release; once a schema
version ships to alpha devices, rollback must be a forward migration.