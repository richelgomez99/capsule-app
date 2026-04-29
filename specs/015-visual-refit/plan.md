# Implementation Plan: Visual Refit (Quiet Almanac)

**Branch**: `015-visual-refit` | **Date**: 2026-04-29 | **Spec**: [spec.md](spec.md)

## Summary

Phased, feature-flagged adoption of the "Quiet Almanac" visual language. Phase 0
lands tokens + primitives + AgentVoiceMark color consolidation across 3
gated commits. Phases 1–4 refit one screen surface each. Phase 5 (bubble) is
deferred to post-Demo Day. Every commit gated by Claude review.

## Technical Context

- **Language/Version**: Kotlin 2.x (matches existing app module).
- **Primary Dependencies**: Jetpack Compose, existing `CapsulePalette`,
  Material 3 (Compose-side only — refit does not depend on Material You /
  dynamic color).
- **Storage**: N/A (no schema changes).
- **Testing**: Existing JVM unit tests (`:app:test`), instrumented tests
  (`:app:connectedDebugAndroidTest`) where present, lint suite
  (`:build-logic:lint:test`).
- **Target Platform**: Android, target SDK aligned with existing app module.
- **Project Type**: Mobile app (Android).
- **Performance Goals**: No measurable regression at 60 fps on Pixel 8 Pro
  for Diary scroll; serif italic accent renders without layout flicker.
- **Constraints**: Presentation-only — no ViewModel signature changes, no
  schema changes, no bubble overlay changes pre-Demo Day. Flag-gated.
- **Scale/Scope**: 4 user-facing screens refitted (Diary, Cluster detail,
  Settings, Capture sheet). 5 new primitives. 3 new tokens.

## Constitution Check

The 16 constitutional principles are honored. Specifically:

- **Principle III (Intent Before Artifact)**: data-layer append-only
  `intentHistoryJson` is **untouched**. Only user-visible copy changes
  (LD-003). PASS.
- **Principle IX (Sovereign Cloud LLM)** + **Principle X (Sovereign Cloud
  Storage)**: settings danger-row copy must scope honestly to what Orbit
  controls (FR-015-011). PASS.
- **Principle XII (Provenance)**: cluster detail screen continues to render
  citations on every claim. The refit's reference render
  (`design/visual-refit-2026-04-29/project/orbit-screen-cluster.jsx`) shows
  citation chips on every bullet — refit must preserve. PASS.
- All other 14 principles: untouched by presentation-only refit. PASS.

No constitutional violations to justify in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/015-visual-refit/
├── spec.md            # what + why + locked decisions + scope
├── plan.md            # this file — phase plan + risk + gates
├── research.md        # JSX → Compose token map, type scale, screen-by-screen comp
├── quickstart.md      # how a future agent picks this up
└── tasks.md           # numbered checkbox tasks grouped by phase
```

No `data-model.md`: no schema changes (per FR-015-008, DEP-001).
No `contracts/`: presentation refit, no API surface.

### Source Code (touched paths)

```text
app/src/main/java/com/capsule/app/
├── RuntimeFlags.kt                        # add useNewVisualLanguage
├── ui/
│   ├── tokens/
│   │   ├── Colors.kt                      # add BrandAccent + dim + ink
│   │   └── Type.kt                        # NEW — Cormorant / Inter / JetBrains Mono
│   └── primitives/
│       ├── AgentVoiceMark.kt              # consolidate to brandAccent (Phase 0 c3)
│       ├── ClusterActionRow.kt            # untouched
│       ├── OrbitMark.kt                   # NEW (Phase 0 c2)
│       ├── OrbitWordmark.kt               # NEW (Phase 0 c2)
│       ├── MonoLabel.kt                   # NEW (Phase 0 c2)
│       ├── IntentChip.kt                  # NEW (Phase 0 c2)
│       └── SourceGlyph.kt                 # NEW (Phase 0 c2)
├── diary/ui/                              # Phase 2 refit
├── cluster/ui/                            # Phase 1 refit (screen) + Block 8 card
├── settings/                              # Phase 3 refit
├── capture/                               # Phase 4 refit
└── bubble/                                # ← UNTOUCHED (LD-005)

app/src/main/res/font/                     # Cormorant + Inter + JetBrains Mono
build-logic/lint/src/...                   # NoAgentVoiceMark detector test update
specs/010-visual-polish-pass/spec.md       # D4 amendment entry
```

**Structure Decision**: Single Android module (`:app`) plus existing
`:build-logic:lint`. Refit does not introduce new modules.

## Phase Plan

### Phase 0 — Foundation (3 commits, gated by Claude review after each) — risk: LOW

**Goal**: tokens + primitives in tree, no screens touched, flag defaulted OFF.

**Commits** (each one a separate review gate):

1. `feat(015): brand accent + display + serif tokens` —
   - extend `tokens/Colors.kt` with `BrandAccent` (#e8b06a), `BrandAccentDim`
     (16% alpha amber), `BrandAccentInk` (#1a1206)
   - add `tokens/Type.kt` (display = Cormorant Garamond, body = Inter,
     caption = JetBrains Mono)
   - wire fonts to `app/src/main/res/font/`
   - add `RuntimeFlags.useNewVisualLanguage = false`
   - **NO SCREEN CHANGES**
   - gates: `:app:compileDebugKotlin` clean, `:app:lintDebug` no new
     warnings, `:build-logic:lint:test` green
2. `feat(015): OrbitMark + OrbitWordmark + MonoLabel + IntentChip + SourceGlyph` —
   - 5 primitives in `ui/primitives/`
   - mechanism mirrors existing `AgentVoiceMark` (Canvas + Path, no font
     dep for the logo glyphs)
   - `IntentChip` consumes the new 5-intent palette (LD-002)
   - own-file Compose previews for each
   - gates: same as commit 1 + previews render
3. `feat(015): consolidate AgentVoiceMark to brand amber + spec 010 D4 amendment` —
   - `AgentVoiceMark.kt` color one-line change: `inkAccentCluster` →
     `brandAccent`
   - update `NoAgentVoiceMarkOutsideAgentSurfacesDetectorTest` to reflect
     the consolidated color (allow-list logic unchanged)
   - append D4 amendment entry to `specs/010-visual-polish-pass/spec.md`
   - gates: `:build-logic:lint:test` green (8/8), compile + lintDebug clean

**Phase 0 rollback path**: revert commits in reverse order. Each commit is
self-contained.

**Phase 0 exit criteria**: Claude approves all 3 commits; primitives
previewable in IDE; flag is OFF; no behavior change observable in app.

---

### Phase 1 — Cluster surface (greenfield) — risk: LOW

**Goal**: Build `ClusterSuggestionCard` and `ClusterDetailScreen` from scratch
on the new tokens. Phase 11 Block 8 work flows through this.

**Why lowest risk after Phase 0**: greenfield composables; no existing UI
to dismantle. The `ClusterSuggestionCard` is anticipated by spec 002 Phase 11
Block 8 (`tasks.md` T145–T147) and was unblocked by the Block 7 primitives.

**Inputs**: `design/visual-refit-2026-04-29/project/orbit-screen-cluster.jsx`,
existing cluster ViewModel surface from spec 002.

**Gate criteria**: flag-ON path renders the card + detail screen with all
ViewModel calls unchanged from spec 002 expectations; flag-OFF path
unchanged.

**Rollback**: revert phase commits; cluster surface returns to whatever
spec 002 Phase 11 Block 8 ships independently.

---

### Phase 2 — Diary refit — risk: MEDIUM

**Goal**: Header gets `OrbitWordmark` + `MonoLabel` date stamp. Day groups
adopt hairline + mono date pattern. Source glyphs replace existing
app-icon dots. Navigation/data contracts unchanged.

**Risk**: Diary is the most-trafficked screen and has existing instrumented
tests. Refit must not break test selectors. Mitigate by keeping
content-description / test tags identical and only swapping visual
composables.

**Inputs**: `design/visual-refit-2026-04-29/project/orbit-screen-diary.jsx`,
`app/src/main/java/com/capsule/app/diary/ui/DiaryScreen.kt`.

**Gate criteria**: flag-OFF Diary unchanged; flag-ON Diary matches design
reference; existing instrumented tests green on flag-OFF; new
flag-ON screenshot test (or manual sweep) recorded.

**Rollback**: revert phase commits; flag still gates exposure.

---

### Phase 3 — Settings refit — risk: MEDIUM

**Goal**: New editorial section pattern, amber toggle pill, "Forget everything"
danger row with refined wording per FR-015-011 (LD-004 hardware phrasing).

**Risk**: Settings has many discrete rows; danger-row copy is a constitutional
sensitivity surface (Principles IX, X). Mitigate by writing copy review
into the gate.

**Inputs**: `design/visual-refit-2026-04-29/project/orbit-screen-settings.jsx`,
`app/src/main/java/com/capsule/app/settings/SettingsScreen.kt`.

**Gate criteria**: flag-OFF unchanged; flag-ON renders new pattern; danger-row
copy reviewed against constitution Principles IX + X; LD-004 hardware
phrasing present verbatim.

**Rollback**: revert phase commits.

---

### Phase 4 — Capture sheet refit — risk: HIGH

**Goal**: New chrome, intent chips adopt the 5-intent set, "Save" / "Cancel"
CTA styling. Strip "sealed at save" wording (LD-003).

**Risk**: Highest because (a) capture sheet is the primary save path and
(b) the LD-002 intent set's persistent enforcement depends on **spec 016
(`016-intent-set-migration`)** — a separate, parallel-drafted data-layer
migration spec. Phase 4 must wait for spec 016 to merge before landing.
Do not invent presentation-only widening of the intent field as a
shortcut.

**Inputs**: `design/visual-refit-2026-04-29/project/orbit-screen-capture.jsx`,
`app/src/main/java/com/capsule/app/capture/...`.

**Gate criteria**: spec 016 merged into main; flag-OFF unchanged; flag-ON
capture flow saves an envelope with the new intent set; no "sealed at
save" wording anywhere in the refit copy.

**Rollback**: revert phase commits; if DEP-001 already shipped, that
migration stays.

---

### Phase 5 — Bubble overlay refit — DEFERRED (post-Demo Day) — risk: N/A

Reference only. No actionable tasks in this spec. Reopen post-2026-05-22.

## Risk Register

| ID | Risk | Phase | Likelihood | Impact | Mitigation |
|----|------|-------|------------|--------|------------|
| R1 | PR #3 + PR #4 not yet on main when Phase 0 c1 fires | 0 | Resolved-gated | Blocks branch start | User green-lights Phase 0 c1 only after PR #3 → PR #4 merge + rebase |
| R2 | Cormorant Garamond licensing / size | 0 c1 | Low | APK size bloat | Subset font; monitor APK size delta |
| R3 | Spec 016 (intent-set migration) not ready by Phase 4 | 4 | Medium | Phase 4 cannot land | Drafted in parallel; Phase 4 simply waits |
| R4 | Instrumented test selectors break | 2, 3 | Medium | CI red | Preserve content-descriptions + test tags |
| R5 | Designer/Claude block on a Phase 0 commit | 0 | Medium | Branch stalls | Each commit small + self-contained; iterate fast |
| R6 | LD-005 violated by Phase 1 cluster work touching bubble | 1 | Low | Demo bubble breaks | Lint/grep for `bubble/` in pre-merge review |
| R7 | Material You expectation from product | any | Low | Re-litigation of locked decision | LD-001 closes this; reject re-open |
| R8 | Concurrent agent worktree collision | any | Medium | Cross-branch commit drift | Use `git worktree add` per agent OR serialize agents |

## Complexity Tracking

No constitutional violations to justify.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| (none)    | —          | —                                   |
