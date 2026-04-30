# Quickstart: Visual Refit (Spec 015)

You're picking up the Quiet Almanac visual refit. This is the orientation.

## TL;DR

- **Branch**: `015-visual-refit` (off `origin/main`).
- **Spec**: [spec.md](spec.md) — read locked decisions LD-001…LD-005 first.
- **Plan**: [plan.md](plan.md) — 5 phases; Phase 5 deferred.
- **Tasks**: [tasks.md](tasks.md) — every task numbered T015-NNN, gated.
- **Design bundle**: `design/visual-refit-2026-04-29/` (checked into repo).
- **Review gate**: Claude reviews EVERY commit before the next lands.

## Branch checkout

```bash
cd /Users/richelgomez/dev/capsule-app
git fetch origin
git switch 015-visual-refit
# branch is tracking origin/main; rebase before resuming work
git pull --rebase
```

## Where things live

| What | Where |
|------|-------|
| Reference design (JSX prototypes) | `design/visual-refit-2026-04-29/project/` |
| Reference INDEX | `design/visual-refit-2026-04-29/INDEX.md` |
| Existing primitives (Phase 11 Block 7) | `app/src/main/java/com/capsule/app/ui/primitives/` |
| Existing tokens | `app/src/main/java/com/capsule/app/ui/tokens/Colors.kt` |
| Lint detector | `build-logic/lint/src/main/java/.../NoAgentVoiceMarkOutsideAgentSurfacesDetector.kt` |
| Lint detector test | `build-logic/lint/src/test/java/.../NoAgentVoiceMarkOutsideAgentSurfacesDetectorTest.kt` |
| Spec 010 (D4 amendment lands here) | `specs/010-visual-polish-pass/spec.md` |
| Runtime flag surface | `app/src/main/java/com/capsule/app/RuntimeFlags.kt` |

## Mental model

The refit is **presentation-only**. Every refit composable calls the same
upstream methods its predecessor calls. There are NO data model changes,
NO ViewModel signature changes, NO new permissions, NO new IPC.

The schema migration to drop "in orbit" / "archive" intents and add
"for someone" is a SEPARATE concern (DEP-001). Do not roll it into this
spec.

The refit BUILDS ON `AgentVoiceMark` / `ClusterActionRow` / `CapsulePalette`
(landed in PR #4 / Phase 11 Block 7). It does not replace them.

## Execution order

1. **Wait for green-light from user** — DEP-003 requires PR #3 + PR #4
   merged into `main` first; rebase `015-visual-refit` onto fresh main
   before starting Phase 0 commit 1.
2. Run Phase 0 commits 1 → 2 → 3, with a Claude review gate between each.
3. Phase 1 (cluster surface) before Phase 2 (diary) — lowest-risk first.
   Phase 1 will lean on Block 8's `ClusterSuggestionCard` if it has
   landed; otherwise Phase 1 builds the surface and Block 8 wires it later.
4. Phase 3 (settings) — flag constitutional copy review against Principles
   IX + X.
5. Phase 4 (capture sheet) — gated on **spec 016** (`016-intent-set-migration`)
   merging first. Spec 016 may be drafted in parallel.
6. Phase 5 (bubble) — DEFERRED to post-Demo Day (after 2026-05-22).

## Review gate protocol

1. Push commit.
2. Tell Claude (separate session): "Review commit `<hash>` on
   `015-visual-refit`, Phase 0 / Commit X / Tasks T015-XXX..XXX."
3. Claude returns approve / block / iterate.
4. On approve, next commit can start. Record the approval marker in the
   next commit body (e.g., `Reviewed-by: Claude (<session>) — approved`).

## Gates per commit (must pass before requesting review)

- `:app:compileDebugKotlin` clean.
- `:app:lintDebug` no NEW warnings (pre-existing `MissingClass` for
  `ActionsSettingsActivity` is allowed; predates this branch).
- `:build-logic:lint:test` 8/8 green (after Phase 0 c3, expectations updated).
- `RuntimeFlags.useNewVisualLanguage` = false in tree at all times.

## Stop conditions (when in doubt, stop and ask)

- Locked decision (LD-001 .. LD-005) appears to contradict existing code.
- Design bundle JSX renders something Compose can't reproduce 1:1.
- Spec 016 (`016-intent-set-migration`) not merged when starting Phase 4.
- PR #3 + PR #4 not yet merged into `main` when starting Phase 0 c1
  (i.e., Block 7 primitives not yet on main).
- Any change starts touching `app/src/main/java/com/capsule/app/bubble/`
  before Demo Day (LD-005).

## Useful commands

```bash
# Run lint suite
./gradlew :build-logic:lint:test

# Compile + lint
./gradlew :app:compileDebugKotlin :app:lintDebug

# Run unit tests for a phase's surface
./gradlew :app:testDebugUnitTest --tests "*Diary*"

# Build a debug APK to flip the flag manually on device
./gradlew :app:assembleDebug
```

## Status

- [x] Spec authored
- [x] Plan authored
- [x] Tasks authored
- [x] Research authored
- [x] Quickstart authored (this file)
- [x] Design bundle copied to repo
- [x] Branch `015-visual-refit` cut from `origin/main`
- [ ] Planning PR opened (next step — see deliverables in original task)
- [ ] DEP-001/002/003 confirmed with user
- [ ] Phase 0 commit 1 begun

**This is a planning-only spec.** As of 2026-04-29 all 3 dependencies
are resolved: DEP-001 → spec 016 (separate, parallel), DEP-002 → spec.md
confirmed, DEP-003 → gated on PR #3 + PR #4 merging into main. Phase 0
commit 1 fires only after the user green-lights it post-merge.
