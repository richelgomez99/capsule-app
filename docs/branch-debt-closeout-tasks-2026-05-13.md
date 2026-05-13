# Tasks: Branch Debt Closeout Before 004-012 Rebaseline

**Input**: [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md), existing Spec Kit artifacts under `specs/015-visual-refit`, `specs/016-intent-set-migration`, and `specs/017-capture-feedback-actions`

**Repository**: `/Users/richelgomez/dev/capsule-app`

**Current integration/reference worktree**: `/Users/richelgomez/dev/capsule-app` on `qa/015-017-stacked`

**Default branch**: `main`

**Purpose**: Finish or explicitly defer current branch debt before starting any rebaselined `004` through `012` Spec Kit work.

**Branch debt sequence**:

```text
016-intent-set-migration
  -> 017-capture-feedback-actions
  -> 015-phase1-cluster-surface
  -> docs/product-truth-reset
  -> first rebaselined 004-012 Spec Kit branch
```

## Stop Signs And Prerequisites

- Do not change app code from the `qa/015-017-stacked` integration/reference workspace.
- Do not discard, reset, or clean staged changes in sibling worktrees. The staged changes in `/Users/richelgomez/dev/capsule-app-015-phase1-split` and `/Users/richelgomez/dev/capsule-app-spec-017` are presumed to be intentional work until the owner says otherwise.
- Do not start a new `004` through `012` Spec Kit branch until every task in this checklist is complete or the incomplete track is explicitly deferred in writing.
- Do not treat stale GitHub draft PRs as the implementation source of truth. Local worktrees and staged diffs must be inspected before PR updates.
- Stop before any destructive command, including `git reset --hard`, `git clean`, force-push, deleting branches, dropping database objects, or rewriting migrations.
- Stop if any branch wants to change Room schema, Supabase migrations, Binder contracts, or external-write behavior outside its owning spec.
- Stop if `015` absorbs duplicate-capture behavior, `017` absorbs visual-only refit polish, or `016` absorbs ContactRef schema work.
- Stop if the docs reset tries to reorganize stale `004` through `012` specs before `016`, `017`, and `015` are landed or explicitly deferred.

## Known Worktrees And PR Reality

| Track | Worktree / branch | Current role |
| --- | --- | --- |
| Integration docs | `/Users/richelgomez/dev/capsule-app` on `qa/015-017-stacked` | Reference workspace with untracked planning docs; no app implementation work here. |
| 016 | `/Users/richelgomez/dev/capsule-app-spec-016` on `016-intent-set-migration` | Clean locally, but stale/diverged relative to PR #8 branch. |
| 017 | `/Users/richelgomez/dev/capsule-app-spec-017` on `017-capture-feedback-actions` | Actual implementation PR #20; review fixes pushed at `1d0d100`. |
| 015 | `/Users/richelgomez/dev/capsule-app-015-phase1-split` on `015-phase1-cluster-surface` | Actual implementation PR #21; closeout commit `275ead9` pushed. |
| Stale 015 PR | `/Users/richelgomez/dev/capsule-app-visual-refit` on `015-visual-refit` | Historical planning branch behind PR #5; closed as superseded by PR #21. |
| Docs reset | `docs/product-truth-reset` | New branch to create from clean base after branch debt is known. |

## Useful Read-Only Checks

These commands are safe as inventory checks. They do not mutate repository state.

```bash
git -C /Users/richelgomez/dev/capsule-app status --short
git -C /Users/richelgomez/dev/capsule-app worktree list
git -C /Users/richelgomez/dev/capsule-app branch -vv
git -C /Users/richelgomez/dev/capsule-app-015-phase1-split status --short
git -C /Users/richelgomez/dev/capsule-app-015-phase1-split diff --staged --stat
git -C /Users/richelgomez/dev/capsule-app-spec-016 status --short
git -C /Users/richelgomez/dev/capsule-app-spec-016 branch -vv
git -C /Users/richelgomez/dev/capsule-app-spec-017 status --short
git -C /Users/richelgomez/dev/capsule-app-spec-017 diff --staged --stat
```

Use `gh pr view 5`, `gh pr view 8`, and `gh pr list --state open --base main` only if the GitHub CLI is authenticated in the active shell.

## Mutating Command Rule

Future branch owners may need to commit, rebase, push, close PRs, or create PRs while completing this task list. Those commands are intentionally not executed during task generation. Run mutating commands only from the owning worktree named in the task, after the relevant stop signs have been checked.

---

## Phase 1: Inventory And Freeze

**Goal**: Freeze the current integration state, preserve all staged/untracked work, and make the branch debt queue explicit.

**Independent Test**: A reviewer can read this artifact plus [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md) and know which branch owns each piece of work before any app code changes happen.

### Phase 1 Snapshot - 2026-05-13

Recorded from read-only checks in the owning worktrees. No staged files were unstaged, discarded, committed, rebased, or pushed.

**Integration/reference worktree**: `/Users/richelgomez/dev/capsule-app` on `qa/015-017-stacked` at `450183f452e71aa70cc3ebf1e31c8bddd7760ef4`. The branch has no upstream in `git branch -vv`. Current dirty state is untracked docs only:

- `docs/agent-stack-landscape-research-2026-05-12.md`
- `docs/agent-stack-poc-research-2026-05-12.md`
- `docs/agent-stack-research-2026-05-12.md`
- `docs/android-architecture-verification-2026-05-13.md`
- `docs/branch-debt-closeout-tasks-2026-05-13.md`
- `docs/capture-understanding-stack-research-2026-05-12.md`
- `docs/orbit-agent-architecture-round-1-2026-05-12.md`
- `docs/orbit-agent-architecture-round-2-2026-05-12.md`
- `docs/orbit-agent-architecture-round-3-2026-05-12.md`
- `docs/orbit-agent-architecture-round-4-2026-05-12.md`
- `docs/orbit-agent-architecture-round-5-2026-05-12.md`
- `docs/orbit-agent-architecture-round-6-2026-05-12.md`
- `docs/product-roadmap-audit-2026-05-12.md`
- `docs/spec-branch-reorganization-plan-2026-05-13.md`

**Active worktrees**:

| Path | Branch | Head |
| --- | --- | --- |
| `/Users/richelgomez/dev/capsule-app` | `qa/015-017-stacked` | `450183f452e71aa70cc3ebf1e31c8bddd7760ef4` |
| `/Users/richelgomez/dev/capsule-app-015-p0c1` | `main` | `93be2d75758e5630ee402afee79176697f18dd62` |
| `/Users/richelgomez/dev/capsule-app-015-phase1-split` | `015-phase1-cluster-surface` | `275ead90c64a8fcaaa66d804774d3ba32a11abbe` |
| `/Users/richelgomez/dev/capsule-app-spec-016` | `016-intent-set-migration` | `96ac77dcfa74fbe5b0f86ec83bbdb6da44c5ad00` |
| `/Users/richelgomez/dev/capsule-app-spec-017` | `017-capture-feedback-actions` | `ceca6e65cb9ab4974e1cd1992e4f19ed37e03138` |
| `/Users/richelgomez/dev/capsule-app-visual-refit` | `015-visual-refit` | `d9d5db01957cdfc846734c3af8e9178284013317` |

**PR reality**:

| PR | State | Finding |
| --- | --- | --- |
| #1 `cloud-pivot` | Merged | Cloud pivot baseline already landed in `main`. |
| #5 `015-visual-refit` | Closed | Planning-only/stale; closed after replacement PR #21 was opened from the actual `015-phase1-cluster-surface` implementation branch state. |
| #8 `016-intent-set-migration` | Closed | Planning-only/stale; closed after replacement PR #19 was opened from the actual local implementation branch state. |
| #19 `016-intent-set-migration-closeout` | Open | Replacement implementation PR for `016`, including the 2026-05-13 closeout gate notes. |
| #21 `015-phase1-cluster-surface` | Open | Actual `015` implementation PR with closeout commit `275ead9`; land after PR #19 and preferably after PR #20 unless reviewers explicitly accept the dependency state. |
| #20 `017-capture-feedback-actions` | Open | Actual `017` implementation PR created from the sibling worktree; depends on PR #19 unless reviewers explicitly accept the dependency state. |

**016 worktree**: `/Users/richelgomez/dev/capsule-app-spec-016` is clean. It is ahead 11 / behind 3 relative to `origin/016-intent-set-migration`, and `git rev-list --left-right --count origin/main...HEAD` reports `0 5` relative to `origin/main`.

**017 staged files**: `/Users/richelgomez/dev/capsule-app-spec-017` has 6 staged modified files and no unstaged output from the read-only check:

- `app/src/androidTest/java/com/capsule/app/data/UrlHashDedupeContractTest.kt`
- `app/src/main/java/com/capsule/app/data/EnvelopeRepositoryImpl.kt`
- `app/src/main/java/com/capsule/app/data/EnvelopeStorageBackend.kt`
- `app/src/main/java/com/capsule/app/data/LocalRoomBackend.kt`
- `app/src/main/java/com/capsule/app/data/OrbitMigrations.kt`
- `app/src/main/java/com/capsule/app/data/dao/IntentEnvelopeDao.kt`

Staged diff stat: 6 files changed, 129 insertions, 41 deletions.

**015 staged files**: `/Users/richelgomez/dev/capsule-app-015-phase1-split` has 21 staged files and no unstaged output from the read-only check:

- `app/src/androidTest/java/com/capsule/app/service/ServiceHealthMonitorTest.kt`
- `app/src/main/java/com/capsule/app/overlay/BubbleUI.kt`
- `app/src/main/java/com/capsule/app/service/CapsuleOverlayService.kt`
- `app/src/main/java/com/capsule/app/service/ServiceHealthMonitor.kt`
- `app/src/main/java/com/capsule/app/ui/MainActivity.kt`
- `app/src/main/res/drawable/ic_capsule_notification.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- `app/src/main/res/drawable/ic_orbit_logo_mark.xml`
- deleted stale density launcher WebPs under `app/src/main/res/mipmap-*/`
- `docs/capture-overlay-followups.md`
- `specs/015-visual-refit/tasks.md`

Staged diff stat: 21 files changed, 316 insertions, 335 deletions.

**Routing note**: [docs/capture-overlay-followups.md](capture-overlay-followups.md) exists as the ownership/routing note. The integration worktree copy still describes the original dirty split. The staged `015` copy records that the former `qa/015-017-stacked` dirty worktree has been split into owning branches as of 2026-05-12. No staged sibling-worktree changes are routed into `qa/015-017-stacked`; the staged routing-note update itself belongs to the `015` worktree and must be preserved there.

**Freeze notes**: [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md) now states that `qa/015-017-stacked` is reference/docs-only until this checklist is complete, and that no rebaselined `004` through `012` Spec Kit branch starts until this checklist is complete or explicitly deferred.

- [x] T001 Record `qa/015-017-stacked` status, untracked docs, branch head, and upstream state in [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md) using `git -C /Users/richelgomez/dev/capsule-app status --short` and `git -C /Users/richelgomez/dev/capsule-app branch -vv`.
- [x] T002 [P] Record all active worktrees in [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md) using `git -C /Users/richelgomez/dev/capsule-app worktree list`.
- [x] T003 [P] Record PR reality for PR #1, PR #5, PR #8, and any open `015`/`016`/`017` PRs in [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md) using `gh pr view` or GitHub web review.
- [x] T004 [P] Capture the staged file list and diff stat for `/Users/richelgomez/dev/capsule-app-015-phase1-split` in [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md) without unstaging or discarding any file.
- [x] T005 [P] Capture the staged file list and diff stat for `/Users/richelgomez/dev/capsule-app-spec-017` in [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md) without unstaging or discarding any file.
- [x] T006 [P] Confirm `/Users/richelgomez/dev/capsule-app-spec-016` is clean and record its ahead/behind relationship to `origin/main` and its remote PR branch in [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md).
- [x] T007 Add a freeze note to [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md) stating that `qa/015-017-stacked` is reference/docs-only until this closeout checklist is complete.
- [x] T008 Add a freeze note to [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md) stating that no new `004` through `012` Spec Kit branch starts until this checklist is complete or explicitly deferred.
- [x] T009 Confirm no staged sibling-worktree changes are routed to `qa/015-017-stacked` in [docs/capture-overlay-followups.md](capture-overlay-followups.md) or a replacement routing note.
- [x] T010 Preserve the current untracked planning docs from `/Users/richelgomez/dev/capsule-app/docs` by listing their filenames in [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md); do not move them until Phase 5.

**Checkpoint**: Inventory is frozen. Branch owners can work from their own sibling worktrees without losing staged changes.

---

## Phase 2: 016 Intent Set Migration Reconciliation

**Goal**: Reconcile and land `016-intent-set-migration` first because it owns the durable intent enum/label set used by `017` and `015`.

**Independent Test**: `016` can be reviewed and landed from `/Users/richelgomez/dev/capsule-app-spec-016` with no ContactRef schema work, no stale rename behavior, and Android/cloud classifier gates passing.

### Phase 2 Snapshot - 2026-05-13

Recorded from `/Users/richelgomez/dev/capsule-app-spec-016`.

- Clean status confirmed before checks; `git status --short` returned no output.
- `origin/main...HEAD` diff stat: 22 files changed, 630 insertions, 37 deletions.
- `origin/016-intent-set-migration...HEAD` diff stat before replacement: 80 files changed, 3259 insertions, 141 deletions. PR #8 was not treated as the implementation source of truth.
- Android gate passed in 8s: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`.
- `npm test` is not defined in `supabase/functions/llm_gateway`; actual package gates are `npm run typecheck` and `npm run test:unit`.
- Gateway `typecheck` passed.
- Gateway `test:unit` passed: 6 test files, 53 tests.
- Stale-label search returned no implementation hits for `REMIND_ME`, `INSPIRATION`, or `intent-set rename`.
- READ_LATER coverage search found Android label/surface coverage and Supabase classifier prompt, allowlist, and tests.
- ContactRef/schema leakage check against `origin/main...HEAD` returned no `ContactRef`, `contact_ref`, `contactRef`, `OrbitMigrations`, `OrbitDatabase`, or `app/schemas` path hits.

- [x] T011 Confirm `/Users/richelgomez/dev/capsule-app-spec-016` is clean before reconciliation using `git -C /Users/richelgomez/dev/capsule-app-spec-016 status --short` and record the result in `specs/016-intent-set-migration/tasks.md`.
- [x] T012 [P] Compare local `016-intent-set-migration` against `origin/main` from `/Users/richelgomez/dev/capsule-app-spec-016` using `git diff --stat origin/main...HEAD` and record the summary in `specs/016-intent-set-migration/tasks.md`.
- [x] T013 [P] Compare local `016-intent-set-migration` against stale PR #8 branch from `/Users/richelgomez/dev/capsule-app-spec-016` and record whether PR #8 should be updated or replaced in [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md).
- [x] T014 Run the Android gate in `/Users/richelgomez/dev/capsule-app-spec-016`: `ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`, then record results in `specs/016-intent-set-migration/tasks.md`.
- [x] T015 Run the Supabase classifier gate in `/Users/richelgomez/dev/capsule-app-spec-016/supabase/functions/llm_gateway`: `npm run typecheck` and `npm run test:unit`, then record results in `specs/016-intent-set-migration/tasks.md`.
- [x] T016 Run stale-label search in `/Users/richelgomez/dev/capsule-app-spec-016`: `rg -n "REMIND_ME|INSPIRATION|intent-set rename" app/src/main supabase/functions/llm_gateway`, then record expected no-implementation-hit results in `specs/016-intent-set-migration/quickstart.md`.
- [x] T017 Run intent-label coverage search in `/Users/richelgomez/dev/capsule-app-spec-016`: `rg -n "Intent\.READ_LATER|READ_LATER" app/src/main supabase/functions/llm_gateway`, then record intended Android and cloud classifier coverage in `specs/016-intent-set-migration/quickstart.md`.
- [x] T018 Confirm no ContactRef entity, Room migration, exported schema, or historical intent rewrite leaked into `/Users/richelgomez/dev/capsule-app-spec-016`; record the confirmation in `specs/016-intent-set-migration/tasks.md`.
- [x] T019 Update or replace PR #8 from `/Users/richelgomez/dev/capsule-app-spec-016` only after T011 through T018 pass; include gate output and stale-PR reconciliation notes in the PR body linked from [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md).
- [x] T020 Land or explicitly defer `016-intent-set-migration`, then record the merge commit or defer reason in [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md).

T019/T020 result: committed the 016 closeout documentation updates as `c068770`, pushed replacement branch `016-intent-set-migration-closeout`, opened PR #19, and closed stale planning PR #8 with a superseded note. Landing remains explicitly deferred pending PR review/merge; treat `016` as PR-ready, not merged.

**Checkpoint**: `016` is landed or explicitly deferred. Do not start the `017` landing gate until this checkpoint is satisfied.

---

## Phase 3: 017 Capture Feedback Actions Staged Work Closeout

**Goal**: Preserve and close the actual `017-capture-feedback-actions` implementation work after `016` is reconciled.

**Independent Test**: The staged data/migration/repository changes in `/Users/richelgomez/dev/capsule-app-spec-017` are either committed into a reviewable PR or explicitly split/deferred without losing any staged file.

### Phase 3 Snapshot - 2026-05-13

Recorded from `/Users/richelgomez/dev/capsule-app-spec-017`. No staged files were unstaged, discarded, committed, rebased, or pushed.

Dependency state: T020 is explicitly deferred after replacement PR #19 was opened for `016`; `016` is PR-ready but not landed. Continue `017` only with non-mutating checks until branch owners approve commit/split/PR actions.

Staged file list:

- `app/src/androidTest/java/com/capsule/app/data/UrlHashDedupeContractTest.kt`
- `app/src/main/java/com/capsule/app/data/EnvelopeRepositoryImpl.kt`
- `app/src/main/java/com/capsule/app/data/EnvelopeStorageBackend.kt`
- `app/src/main/java/com/capsule/app/data/LocalRoomBackend.kt`
- `app/src/main/java/com/capsule/app/data/OrbitMigrations.kt`
- `app/src/main/java/com/capsule/app/data/dao/IntentEnvelopeDao.kt`

Staged diff stat: 6 files changed, 129 insertions, 41 deletions.

Ownership check: staged files match `017` duplicate capture/data ownership: URL hash duplicate matching, exact-text duplicate matching, active non-deleted/non-archived lookup, Room migration, storage backend/DAO routes, and URL-hash dedupe contract coverage. No staged visual-only `015`, ContactRef `016`, or future `004` through `012` files were found.

Schema verification result: the matching schema/entity/database files are already committed in the `017` branch history. Full `origin/main...HEAD` includes `app/schemas/com.capsule.app.data.OrbitDatabase/6.json`, `app/schemas/com.capsule.app.data.OrbitDatabase/7.json`, `OrbitDatabase.kt`, `IntentEnvelopeEntity.kt`, `EnvelopeNoteEntity.kt`, `EnvelopeNoteDao.kt`, `SealResultParcel`, storage/backend changes, and `OrbitMigrations.kt`. The current staged `OrbitMigrations.kt` edit is a follow-up migration adjustment, not an orphaned schema change.

Android gate: pre-commit staged-tree gate passed in 8s for `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `:app:lintDebug`.

Focused duplicate tests: `:app:testDebugUnitTest --tests com.capsule.app.overlay.PostCaptureOverlayBoundsRegressionTest` passed. Connected focused instrumentation also passed on SM-X710 with 14 tests using `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.capsule.app.data.UrlHashDedupeContractTest,com.capsule.app.overlay.OverlayDuplicateFeedbackTest,com.capsule.app.data.DuplicateLookupPerformanceContractTest`.

Duplicate audit metadata check: [EnvelopeRepositoryImpl.kt](../app/src/main/java/com/capsule/app/data/EnvelopeRepositoryImpl.kt) records duplicate attempts with `existingEnvelopeId` and `matchedBy` only in the duplicate audit `extraJson`; no raw text or full URL is added to that duplicate audit payload.

Commit/PR result: committed the staged `017` closeout work as `b872e38`, pushed `origin/017-capture-feedback-actions`, and opened PR #20. Self-review fixes were then committed as `1d0d100` and pushed to the same PR, adding database-backed active duplicate keys, exact-text active-key indexing, concurrent duplicate coverage, v5-to-v7 migration coverage, and androidTest schema assets. PR body includes S24 and Tab S9 physical QA evidence from `specs/017-capture-feedback-actions/tasks.md`. Landing remains deferred pending review and the `016` dependency state.

Review-fix validation passed with explicit local environment (`JAVA_HOME=/Applications/Android Studio.app/Contents/jbr/Contents/Home`, `ANDROID_HOME=/Users/richelgomez/Library/Android/sdk`): `:app:compileDebugKotlin`, `:app:compileDebugAndroidTestKotlin`, `:app:testDebugUnitTest --tests com.capsule.app.overlay.PostCaptureOverlayBoundsRegressionTest`, focused `:app:connectedDebugAndroidTest` for `UrlHashDedupeContractTest`, `DuplicateLookupPerformanceContractTest`, `OrbitDatabaseMigrationV5toV7Test`, and `OverlayDuplicateFeedbackTest` on SM-S928U1 and SM-X710, plus `:app:lintDebug`.

- [x] T021 Confirm T020 is complete or explicitly deferred before mutating `/Users/richelgomez/dev/capsule-app-spec-017`; record the dependency state in `specs/017-capture-feedback-actions/tasks.md`.
- [x] T022 Capture the staged file list in `/Users/richelgomez/dev/capsule-app-spec-017` with `git diff --staged --name-status` and copy the summary into [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md) before any commit or split.
- [x] T023 Capture the staged diff stat in `/Users/richelgomez/dev/capsule-app-spec-017` with `git diff --staged --stat` and copy the summary into [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md) before any commit or split.
- [x] T024 Confirm the staged `017` files belong to duplicate capture, `Already saved`, note persistence, Room migration, or URL-hash dedupe contract work by comparing them to `specs/017-capture-feedback-actions/data-model.md`.
- [x] T025 Stop and reroute any staged file in `/Users/richelgomez/dev/capsule-app-spec-017` that belongs to visual-only `015`, ContactRef `016` follow-up, or future `004` through `012` work; document reroutes in [docs/capture-overlay-followups.md](capture-overlay-followups.md).
- [x] T026 Commit or intentionally split the staged `017` files from `/Users/richelgomez/dev/capsule-app-spec-017` after T022 through T025 are recorded; do not use `git reset --hard`, `git clean`, or unstaged discard commands.
- [x] T027 Verify Room schema version, exported schema JSON, and migration naming in `/Users/richelgomez/dev/capsule-app-spec-017/app/schemas/com.capsule.app.data.OrbitDatabase` against `specs/017-capture-feedback-actions/data-model.md`.
- [x] T028 Run the Android gate in `/Users/richelgomez/dev/capsule-app-spec-017`: `ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`, then record results in `specs/017-capture-feedback-actions/tasks.md`.
- [x] T029 Run focused duplicate-capture tests in `/Users/richelgomez/dev/capsule-app-spec-017` for URL duplicate, exact-text duplicate, deleted/archived duplicate exclusions, note persistence, reclassify, open-existing, and URL hydration reuse; record command names and results in `specs/017-capture-feedback-actions/tasks.md`.
- [x] T030 Confirm duplicate audit metadata in `/Users/richelgomez/dev/capsule-app-spec-017` includes `existingEnvelopeId` and `matchedBy` but not raw text or full URLs; record confirmation in `specs/017-capture-feedback-actions/tasks.md`.
- [x] T031 Preserve S24 and Tab S9 physical QA evidence for duplicate URL, duplicate exact text, adjacent icon tap, portrait, and landscape in the `017` PR body and link the PR from [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md).
- [x] T032 Open, update, or land the `017-capture-feedback-actions` PR from `/Users/richelgomez/dev/capsule-app-spec-017` after T021 through T031 pass; record merge commit or defer reason in [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md).

**Checkpoint**: Duplicate/`Already saved` behavior is landed or explicitly deferred. Do not start the `015` landing gate until this checkpoint is satisfied.

---

## Phase 4: 015 Phase 1 Cluster Surface Staged Work Closeout

**Goal**: Close the actual `015-phase1-cluster-surface` implementation branch after behavior/data branches are settled.

**Independent Test**: The staged visual refit files in `/Users/richelgomez/dev/capsule-app-015-phase1-split` are committed or split safely, the remaining T015 verification gaps are either closed or deferred, and the stale PR #5 is closed or replaced.

### Phase 4 Snapshot - 2026-05-13

Recorded from `/Users/richelgomez/dev/capsule-app-015-phase1-split`. The worktree is on `015-phase1-cluster-surface`, ahead of `origin/main` by 17 commits, with no unstaged edits before closeout recording.

Dependency state: T020 (`016`) is explicitly PR-ready/deferred after replacement PR #19; T032 (`017`) is complete with PR #20 open and review-fix commit `1d0d100` pushed. Continue `015` as the third landing track; landing remains after `016` and preferably after `017` unless reviewers accept the dependency state.

Staged file list summary:

- Added `app/src/androidTest/java/com/capsule/app/service/ServiceHealthMonitorTest.kt`.
- Modified `BubbleUI`, `CapsuleOverlayService`, `ServiceHealthMonitor`, and `MainActivity` for Orbit mark visual refit, setup permission-row presentation, and service-running truth hardening.
- Modified notification/launcher vector drawables and added `app/src/main/res/drawable/ic_orbit_logo_mark.xml`.
- Deleted stale density launcher WebPs under `app/src/main/res/mipmap-*/`.
- Updated [docs/capture-overlay-followups.md](capture-overlay-followups.md) and `specs/015-visual-refit/tasks.md`.

Staged diff stat before closeout recording: 21 files changed, 316 insertions, 335 deletions.

Ownership check: staged files belong to the `015` visual/setup/logo lane, plus service-health hardening retained as internal diagnostics for the Settings QA issue found during `015` physical testing. No `016` intent-set migration files, `017` duplicate-capture data files, or future `004` through `012` work were found in the staged set.

Decision state: T015-501 through T015-506 are closed for this PR. Bubble size/transparency customization is deferred to follow-up overlay customization work. T015-901 is intentionally satisfied by user-confirmed physical S24/Tab S9 QA rather than a screenshot sweep. T015-904 keeps `RuntimeFlags.useNewVisualLanguage` default `false` until explicit alpha/release readiness. T015-905 physical QA is recorded complete in `specs/015-visual-refit/tasks.md`; no additional physical evidence was collected during this closeout run.

Validation: staged diff hygiene passed. Android gate passed with `ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`. Build-logic lint gate passed with `JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :build-logic:lint:test`. Focused connected regression `:app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.capsule.app.service.ServiceHealthMonitorTest` was attempted but not run because no Android devices were connected; do not claim new connected evidence for this run.

Commit/PR result: committed the staged `015` closeout work as `275ead9`, pushed `origin/015-phase1-cluster-surface`, and opened PR #21. PR body preserves the Orbit mark philosophy, packaged font-size result, contrast results, local-only copy removal, Samsung battery action QA, touch-bounds QA, flag default decision, and the no-device connected-test caveat. Stale planning PR #5 was closed as superseded by PR #21. Landing remains deferred pending review and the `016`/`017` dependency state.

- [x] T033 Confirm T020 and T032 are complete or explicitly deferred before mutating `/Users/richelgomez/dev/capsule-app-015-phase1-split`; record the dependency state in `specs/015-visual-refit/tasks.md`.
- [x] T034 Capture the staged file list in `/Users/richelgomez/dev/capsule-app-015-phase1-split` with `git diff --staged --name-status` and copy the summary into [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md) before any commit or split.
- [x] T035 Capture the staged diff stat in `/Users/richelgomez/dev/capsule-app-015-phase1-split` with `git diff --staged --stat` and copy the summary into [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md) before any commit or split.
- [x] T036 Confirm staged `015` files belong to visual refit, overlay touch-bounds presentation, service health presentation, launcher/notification mark assets, docs, or `specs/015-visual-refit/tasks.md`; reroute non-visual behavior changes to the owning branch before committing.
- [x] T037 Decide whether `T015-501` through `T015-506` in `specs/015-visual-refit/tasks.md` block the PR or become follow-up overlay customization work.
- [x] T038 Decide whether `T015-901`, `T015-904`, and `T015-905` in `specs/015-visual-refit/tasks.md` block the PR or become flag-flip follow-ups.
- [x] T039 Commit or intentionally split the staged `015` files from `/Users/richelgomez/dev/capsule-app-015-phase1-split` after T034 through T038 are recorded; do not use `git reset --hard`, `git clean`, or unstaged discard commands.
- [x] T040 Run the Android gate in `/Users/richelgomez/dev/capsule-app-015-phase1-split`: `ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug`, then record results in `specs/015-visual-refit/tasks.md`.
- [x] T041 Run the build-logic lint gate in `/Users/richelgomez/dev/capsule-app-015-phase1-split`: `./gradlew :build-logic:lint:test`, then record results in `specs/015-visual-refit/tasks.md`.
- [x] T042 Record the known connected Compose harness limitation for flag-off instrumented tests in `specs/015-visual-refit/tasks.md` without marking physical/manual evidence as complete unless it was actually collected.
- [x] T043 Capture or explicitly defer flag-on manual screenshots for Diary, Settings, Capture sheet, post-capture pills, and bubble drag/remove in `specs/015-visual-refit/tasks.md`.
- [x] T044 Preserve the corrected Orbit mark philosophy, packaged font-size result, contrast results, local-only copy removal, Samsung battery action QA, and touch-bounds QA in the `015` PR body linked from [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md).
- [x] T045 Close stale PR #5 or clearly label it as superseded by the `015-phase1-cluster-surface` implementation PR; record the decision in [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md).
- [x] T046 Open, update, or land the `015-phase1-cluster-surface` PR from `/Users/richelgomez/dev/capsule-app-015-phase1-split` after T033 through T045 pass; record merge commit or defer reason in [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md).

**Checkpoint**: Visual refit is landed behind flag or explicitly scoped to a remaining visual follow-up. Stale planning PR state no longer hides the actual implementation branch.

---

## Phase 5: Docs Product Truth Reset

**Goal**: Land the planning/truth-reset docs after branch debt is no longer ambiguous, so the repo stops promising local-only behavior and stale roadmap order.

**Independent Test**: README, planning docs, and spec statuses tell one current product story, and no app code is changed on this docs branch.

- [x] T047 Confirm T020, T032, and T046 are complete or explicitly deferred before creating `docs/product-truth-reset`; record the dependency state in [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md).
- [x] T048 Create `docs/product-truth-reset` from a clean `main` worktree in `/Users/richelgomez/dev/capsule-app` or a dedicated docs worktree; record the chosen worktree path in [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md). Used `/Users/richelgomez/dev/capsule-app-docs-product-truth-reset` from `origin/main`.
- [x] T049 Move or commit the untracked planning docs from `/Users/richelgomez/dev/capsule-app/docs` together on `docs/product-truth-reset`, including [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md) and [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md).
- [x] T050 Update [README.md](../README.md) so public product copy reflects local-first, cloud-augmented Orbit behavior without claiming local-only, no Orbit server, or BYOK/BYOC-first defaults.
- [x] T051 Update any `.specify/memory` product docs under `/Users/richelgomez/dev/capsule-app/.specify` that still claim local-only, no Orbit server, or BYOK/BYOC-first defaults.
- [x] T052 Archive stale spec-only folders [specs/004-ask-orbit](../specs/legacy/2026-05-13-roadmap-rebaseline/004-ask-orbit), [specs/005-cloud-boost-byok-llm](../specs/legacy/2026-05-13-roadmap-rebaseline/005-cloud-boost-byok-llm), [specs/006-orbit-cloud-storage](../specs/legacy/2026-05-13-roadmap-rebaseline/006-orbit-cloud-storage), [specs/007-knowledge-graph](../specs/legacy/2026-05-13-roadmap-rebaseline/007-knowledge-graph), [specs/008-orbit-agent](../specs/legacy/2026-05-13-roadmap-rebaseline/008-orbit-agent), [specs/009-byoc-sovereign-storage](../specs/legacy/2026-05-13-roadmap-rebaseline/009-byoc-sovereign-storage), [specs/010-visual-polish-pass](../specs/legacy/2026-05-13-roadmap-rebaseline/010-visual-polish-pass), [specs/011-manual-compose](../specs/legacy/2026-05-13-roadmap-rebaseline/011-manual-compose), and [specs/012-resolution-semantics](../specs/legacy/2026-05-13-roadmap-rebaseline/012-resolution-semantics) as legacy inputs, then recreate the active roadmap in those same numbers: `004-capture-understanding`, `005-retrieval-and-ask-citations`, `006-approval-action-runtime`, `007-memory-candidates-inspector`, `008-cloud-controls-storage-budgeting`, `009-kg-backend-poc`, `010-agent-coordinator`, `011-manual-compose`, and `012-resolution-semantics`.
- [x] T053 Reconcile stale task status in [specs/003-orbit-actions/tasks.md](../specs/003-orbit-actions/tasks.md) by verifying code reality before changing checkboxes; feed remaining action concepts into the future `006-approval-action-runtime` plan.
- [x] T054 Reconcile stale task status in [specs/013-cloud-llm-routing/tasks.md](../specs/013-cloud-llm-routing/tasks.md) by verifying Android/provider/router and Supabase migration reality after PR #1 cloud-pivot.
- [x] T055 Reconcile [specs/014-edge-function-llm-gateway/tasks.md](../specs/014-edge-function-llm-gateway/tasks.md) by adding status or follow-up notes for `supabase/functions/llm_gateway` without regenerating Day 2 from scratch.
- [x] T056 Confirm `docs/product-truth-reset` changes are markdown/docs/spec status only by running `git -C /Users/richelgomez/dev/capsule-app-docs-product-truth-reset diff --name-only origin/main...HEAD` and reviewing paths before PR.
- [x] T057 Open and land the `docs/product-truth-reset` PR, then record merge commit or defer reason in [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md). PR #22 is open; landing is deferred pending review of the branch-debt PR stack.

**Checkpoint**: Product truth and roadmap status are current. New Spec Kit prompts can cite the repo without re-litigating the local-first/cloud-augmented pivot.

---

## Phase 6: Final Readiness Gate

**Goal**: Prove the branch-debt queue is closed or intentionally deferred before any rebaselined `004` through `012` work starts.

**Independent Test**: A reviewer can approve the first rebaselined Spec Kit prompt knowing there are no hidden staged implementation leftovers, stale PR traps, or unresolved product-truth contradictions.

### Phase 6 Snapshot - 2026-05-13

Final gate verification was run after PR #22 opened and after the PR-state follow-up commit `050a9ab` was pushed.

Implementation worktree status:

- `/Users/richelgomez/dev/capsule-app-spec-016` on `016-intent-set-migration` is clean against `origin/016-intent-set-migration-closeout`.
- `/Users/richelgomez/dev/capsule-app-spec-017` on `017-capture-feedback-actions` is clean against `origin/017-capture-feedback-actions`.
- `/Users/richelgomez/dev/capsule-app-015-phase1-split` on `015-phase1-cluster-surface` is clean against `origin/015-phase1-cluster-surface`.
- `/Users/richelgomez/dev/capsule-app-docs-product-truth-reset` is clean with final pushed head `050a9ab` on `origin/docs/product-truth-reset`.
- `/Users/richelgomez/dev/capsule-app` on `qa/015-017-stacked` still has expected docs/spec-only residue from the planning session: untracked planning docs and modified legacy `004` through `012` status headers. Those files are represented by PR #22 and are not implementation work. Do not delete or discard them unless the integration/reference worktree is intentionally cleaned later.

GitHub PR state verified through the GitHub API:

| PR | State | Meaning |
| --- | --- | --- |
| #5 | Closed, not merged | Stale planning-only `015-visual-refit`, superseded by #21. |
| #8 | Closed, not merged | Stale planning-only `016-intent-set-migration`, superseded by #19. |
| #19 | Open | `016-intent-set-migration-closeout`, PR-ready/deferred pending review. |
| #20 | Open | `017-capture-feedback-actions`, PR-ready/deferred pending review. |
| #21 | Open | `015-phase1-cluster-surface`, PR-ready/deferred pending review. |
| #22 | Open | `docs/product-truth-reset`, PR-ready/deferred pending review. |

Roadmap branch check found no local branches matching `004-*` through `012-*` or remote branches matching `origin/004-*` through `origin/012-*`. Active top-level spec folders in PR #22 are `004-capture-understanding`, `005-retrieval-and-ask-citations`, `006-approval-action-runtime`, `007-memory-candidates-inspector`, `008-cloud-controls-storage-budgeting`, `009-kg-backend-poc`, `010-agent-coordinator`, `011-manual-compose`, and `012-resolution-semantics`; no `018+` exception is needed.

The first `004-capture-understanding` Speckit prompt in [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md) includes the required stop signs: stop if the spec implements Ask Orbit, adds KG tables before deletion/invalidation, adds generic browser automation, passes raw HTML/screenshots/embeddings/full evidence bundles over Binder, or adds network clients outside `com.capsule.app.net.*`.

- [x] T058 Verify `/Users/richelgomez/dev/capsule-app`, `/Users/richelgomez/dev/capsule-app-spec-016`, `/Users/richelgomez/dev/capsule-app-spec-017`, and `/Users/richelgomez/dev/capsule-app-015-phase1-split` have no unexpected staged or untracked implementation work; record results in [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md).
- [x] T059 Verify PR #5 and PR #8 are closed, replaced, landed, or explicitly labeled stale in [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md).
- [x] T060 Verify `016`, `017`, `015`, and `docs/product-truth-reset` each have a merge commit or explicit defer note in [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md).
- [x] T061 Run markdown diagnostics for [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md), [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md), and changed spec docs; fix markdown-only issues before PR.
- [x] T062 Confirm no new `004` through `012` branch exists locally or remotely before T057 is complete by checking branch lists from `/Users/richelgomez/dev/capsule-app`; record results in [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md).
- [x] T063 Confirm the first rebaselined branch remains `004-capture-understanding`, not `018+`, and confirm the full active roadmap occupies `004` through `012` after the stale folders are archived. Any exception requires a documented tooling failure in [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md) before Speckit generation.
- [x] T064 Confirm the first rebaselined `004-capture-understanding` prompt includes the required stop signs from [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md): no Ask Orbit, no KG tables before deletion/invalidation, no generic browser automation, no raw evidence over Binder, and no network clients outside `com.capsule.app.net.*`.
- [x] T065 Mark this closeout checklist complete or explicitly deferred in [docs/branch-debt-closeout-tasks-2026-05-13.md](branch-debt-closeout-tasks-2026-05-13.md) before running `/speckit.specify` for the first rebaselined `004` through `012` feature. The closeout checklist is complete with landing explicitly deferred for PRs #19, #20, #21, and #22 pending review.

**Checkpoint**: Branch debt is closed or intentionally deferred. The next Speckit session may start with `004-capture-understanding`.

---

## Dependencies And Execution Order

### Phase Dependencies

- **Phase 1: Inventory And Freeze** has no dependencies and must complete first.
- **Phase 2: 016 Intent Set Migration Reconciliation** depends on Phase 1.
- **Phase 3: 017 Capture Feedback Actions Staged Work Closeout** depends on Phase 2 because `017` consumes the durable intent set.
- **Phase 4: 015 Phase 1 Cluster Surface Staged Work Closeout** depends on Phases 2 and 3 because visual polish should adapt to landed behavior/data contracts.
- **Phase 5: Docs Product Truth Reset** depends on Phases 2, 3, and 4 being complete or explicitly deferred.
- **Phase 6: Final Readiness Gate** depends on Phases 1 through 5.

### Track Dependencies

- `016-intent-set-migration` is first because it owns `WANT_IT`, `REFERENCE`, `READ_LATER`, `FOR_SOMEONE`, `INTERESTING`, and `AMBIGUOUS` alignment.
- `017-capture-feedback-actions` is second because it owns duplicate capture, `Already saved`, note persistence, reclassify, open existing, and related schema/migration behavior.
- `015-phase1-cluster-surface` is third because it is presentation-led and should not hide data/behavior contract decisions.
- `docs/product-truth-reset` is fourth because stale product copy and rebaselined roadmap notes should land after the branch-debt reality is known.

### Parallel Opportunities

- T002 through T006 can run in parallel because they inspect different worktrees or PR records.
- T012 and T013 can run in parallel after T11 because they are read-only comparisons in the `016` worktree.
- T014 through T018 are verification tasks and can be split among reviewers after the branch state is stable.
- T022 through T025 can be split between reviewers as long as no one mutates the `017` index until T026.
- T034 through T038 can be split between reviewers as long as no one mutates the `015` index until T039.
- T050 through T055 can be worked in parallel on `docs/product-truth-reset` after T049, provided one reviewer owns final consistency.

## Parallel Examples

### Inventory Batch

```bash
# Terminal A
git -C /Users/richelgomez/dev/capsule-app worktree list

# Terminal B
git -C /Users/richelgomez/dev/capsule-app-015-phase1-split diff --staged --stat

# Terminal C
git -C /Users/richelgomez/dev/capsule-app-spec-017 diff --staged --stat

# Terminal D
git -C /Users/richelgomez/dev/capsule-app-spec-016 branch -vv
```

### 016 Verification Batch

```bash
# Terminal A in /Users/richelgomez/dev/capsule-app-spec-016
ANDROID_HOME="$HOME/Library/Android/sdk" \
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug

# Terminal B in /Users/richelgomez/dev/capsule-app-spec-016/supabase/functions/llm_gateway
npm test

# Terminal C in /Users/richelgomez/dev/capsule-app-spec-016
rg -n "REMIND_ME|INSPIRATION|intent-set rename" app/src/main supabase/functions/llm_gateway
```

### Docs Reset Batch

```bash
# Reviewer A
Update README.md product-truth copy.

# Reviewer B
Archive stale specs/004-* through specs/012-* as legacy inputs, then recreate the active roadmap in those same numeric slots.

# Reviewer C
Reconcile specs/003, specs/013, and specs/014 task/status notes.
```

## Implementation Strategy

### MVP First

1. Complete Phase 1 to freeze inventory and protect sibling staged changes.
2. Complete Phase 2 and land or defer `016`.
3. Stop and validate that the durable intent set is no longer ambiguous.

### Incremental Delivery

1. Land or defer `016`.
2. Land or defer PR #20 for `017`.
3. Land or defer PR #21 for `015`.
4. Land `docs/product-truth-reset`.
5. Run the final readiness gate.

### Start Condition For Rebaselined 004-012 Work

Only after T065 is complete should a new Speckit workflow start for the rebaselined roadmap. The expected first branch is `004-capture-understanding`. Starting `005` through `012`, appending `018+`, or reviving old `008-orbit-agent` first requires an explicit written exception in [docs/spec-branch-reorganization-plan-2026-05-13.md](spec-branch-reorganization-plan-2026-05-13.md).
