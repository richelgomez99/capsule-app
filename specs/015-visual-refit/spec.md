# Feature Specification: Visual Refit (Quiet Almanac)

**Feature Branch**: `015-visual-refit`
**Created**: 2026-04-29
**Status**: Draft (planning only — no implementation)
**Input**: Off-platform design exploration "Quiet Almanac" (deep navy ground,
cream type, amber accent, Cormorant Garamond serif italic for emphasis,
JetBrains Mono captions). Bundle checked into
`design/visual-refit-2026-04-29/`.

## Summary

Adopt a new visual language across the Android Compose codebase as a presentation-only
refit. The refit is feature-flagged (`RuntimeFlags.useNewVisualLanguage`, default
OFF), shipped behind 5 phases with a Claude review gate after every commit, and
**builds on** the Phase 11 Block 7 primitives (`AgentVoiceMark`,
`ClusterActionRow`, `CapsulePalette`) — it does not replace them.

No data model changes. No ViewModel signature changes. Every refit composable
calls the same upstream methods its predecessor calls.

## Locked Decisions (Non-Negotiable)

These were resolved before spec authoring. Do not relitigate during implementation.

- **LD-001 — Single brand accent: amber (#e8b06a).** A new `BrandAccent` token
  consolidates with `AgentVoiceMark`'s existing `--ink-accent-cluster`
  (#3D4A6B / #8FA3CC). The ✦ glyph keeps its symbolic role; only its color
  changes. Spec 010 D4 receives an amendment entry recording this consolidation.
- **LD-002 — Intent set is `[remind me, inspiration, reference, read later, for someone]`.**
  Drop "in orbit" (vague) and "archive" (not a save-time intent). The
  "for someone" intent enables a contact-picker layer.
  *Schema migration to enforce this at the data layer is a SEPARATE concern —
  see Dependencies below.*
- **LD-003 — No "sealed at save" wording.** Public framing is
  "reclassify adds a layer, latest visible." `intentHistoryJson` remains
  append-only at the data layer; only user-facing copy changes.
- **LD-004 — Local-AI hardware phrasing is**
  "for local-model-capable phones (Pixel 8 Pro+, Galaxy S24+, capable hardware)".
  Not "Pixel 8 and up."
- **LD-005 — Bubble overlay refit (Phase 5) deferred to post-Demo Day (May 22, 2026).**
  The bubble in the working tree is the demo bubble; do not touch it pre-Demo Day.

## Clarifications

### Session 2026-04-29

- Q: What objective pass/fail criterion gates SC-005 against the design-bundle reference renders? → A: Per-commit Claude review against a verbatim property checklist materialized in `research.md` §8 (mechanical grep/test gates 1–6 per phase + one structural-composition screenshot review item). No pixel-diff tooling — Paparazzi/Roborazzi adoption deferred to a post-Demo decision; pixel-diffing against off-platform JSX prototypes (different engine/fonts/scale) is failure-noise.
- Q: Cormorant Garamond font subsetting policy for v1? → A: Subset to Latin + Latin Extended-A only; drop Cyrillic + Vietnamese. Inter and JetBrains Mono ship unsubsetted. If/when Vietnamese or Cyrillic localization lands, swap in a fuller subset behind a locale-qualified font resource or flavor.
- Q: WCAG AA contrast threshold — what counts as "body size" for the cream-on-bgDeep + brand-accent-italic-on-bgDeep edge case? → A: Pin body = 14sp regular (matches `bodyLarge`). Require 4.5:1 minimum for both cream and brand-accent on bgDeep at body size. If brand-accent italic fails 4.5:1 at 14sp, accent-italic spans MUST only render at ≥18sp (display tier — `displaySmall` 17sp bumped to 18sp, or `displayMedium`/`displayLarge`); accent italic is forbidden at body sizes that don't qualify for WCAG AA "large text" 3:1 relaxation.
- Q: SC-002 lint baseline scope after rebase onto fresh main? → A: Expand baseline to enumerate both pre-existing warnings explicitly: `MissingClass ActionsSettingsActivity` AND `RemoveWorkManagerInitializer`. Any third warning fails the gate. No `lint-baseline.xml` introduced in this branch (avoids scope creep); fixing `RemoveWorkManagerInitializer` is non-refit work and is NOT a Phase 0 prerequisite.
- Q: How is `RuntimeFlags.useNewVisualLanguage` observed across an Activity lifecycle when flipped mid-session? → A: Read once at Activity create and pass via `LocalRuntimeFlags` Composition Local. No reactive observer. Mid-session flip has no visible effect until the next Activity recreate (background+foreground or manual restart). Flag is process-scoped at v1.

## User Scenarios & Testing

This refit is presentation-only. There are no new user-facing capabilities; the
"users" of this feature are the team's design + engineering review loop and,
eventually, alpha testers behind the runtime flag.

### User Story 1 — Engineering can ship the refit incrementally without breaking alpha (Priority: P1)

A developer toggles `RuntimeFlags.useNewVisualLanguage` true on a debug build
and sees the new visual language across Diary, Cluster detail, Settings, and
Capture sheet. Toggling false returns to the existing visual language. No
crash, no missing strings, no broken navigation, no data corruption.

**Why this priority**: Without the flag gate, the refit either (a) has to land
all-at-once or (b) commits visible churn to alpha users mid-flight. The flag
makes Phase 0–4 landable independently.

**Independent Test**: Build debug APK on `015-visual-refit` after Phase 0
completes. Flip the flag at runtime; observe Phase 0 changes are token-only
and invisible (no screens touched yet). After each subsequent phase, the
corresponding screen visibly switches.

**Acceptance Scenarios**:

1. **Given** flag is OFF, **When** user opens Diary, **Then** the existing
   visual language renders and `:app:lintDebug` is clean.
2. **Given** flag is ON after Phase 1, **When** user opens a cluster card,
   **Then** the new `ClusterSuggestionCard` and `ClusterDetailScreen` render
   on the new tokens; ViewModel calls are unchanged.
3. **Given** flag is ON, **When** user reclassifies an envelope's intent,
   **Then** the UI shows "latest visible" framing (LD-003); the
   `intentHistoryJson` write is byte-identical to the flag-OFF path.

---

### User Story 2 — Designer/Claude can review each commit against the design bundle (Priority: P1)

After every commit on `015-visual-refit`, Claude (separate session) reviews
the diff against `design/visual-refit-2026-04-29/` reference JSX and either
approves or blocks the next commit.

**Why this priority**: This is the explicit gating mechanism the user
requested. Without it, the refit drifts.

**Independent Test**: Each Phase 0 commit ships independently; the next commit
does not start until Claude posts an approve.

**Acceptance Scenarios**:

1. **Given** Phase 0 commit 1 (tokens) lands, **When** Claude reviews,
   **Then** spec.md / commit body explicitly call out which JSX file the
   tokens map from, and the lint/compile gates are recorded in the commit body.
2. **Given** Claude blocks a commit, **When** the developer iterates,
   **Then** they amend or follow up with a fix commit on the same branch
   before proceeding.

---

### User Story 3 — Alpha tester sees a coherent refit when the flag flips on (Priority: P2)

When the team is satisfied the refit is complete (post-Phase 4), the flag
default flips to `true` (or alpha-only build wires `useNewVisualLanguage = true`)
and the four refitted surfaces (Diary, Cluster, Settings, Capture sheet)
render cohesively.

**Why this priority**: The actual user-visible payoff. Lower priority than
P1 stories because it gates on Phase 4 completion.

**Independent Test**: Manual screenshot sweep of the four screens with
flag = true vs the design bundle reference renders.

**Acceptance Scenarios**:

1. **Given** all phases complete, **When** flag flips on, **Then** Diary,
   Cluster detail, Settings, and Capture sheet render in the new language
   and the bubble overlay still renders in the existing visual language
   (LD-005).

---

### Edge Cases
`RuntimeFlags.useNewVisualLanguage` is read
  **once at Activity create** and passed down via a `LocalRuntimeFlags`
  Composition Local. No reactive observer. A mid-session flip has no
  visible effect until the next Activity recreate (background+foreground
  or manual restart). The flag is process-scoped atrecomposition;
  acceptable to require Activity restart for the boundary case in v1.
- **Font load failure**: `Cormorant Garamond` / `JetBrains Mono` fail to load
  → fall back to system serif / monospace; UI remains legible.
- **Dynamic color (Material You)**: refit uses fixed `BrandAccent`, not
  Material You. This is intentional — Quiet Almanac is a fixed palette.
- **Accessibility****4.5:1 minimum at body size = 14sp regular** (matching
  `bodyLarge` in the type scale). Brand-accent italic spans on `bgDeep` MUST
  ALSO hit 4.5:1 at body size; if they fail at 14sp, accent italic is
  permitted only at ≥18sp (display tier — `displaySmall` 17sp must be bumped
  to 18sp for italic spans, or italic is restricted to `displayMedium` /
  `displayLarge`). Accent italic at body sizes that rely on WCAG AA
  "large text" 3:1 relaxation is **forbidden**d8)
  must hit WCAG AA at body size; serif italic accent at WCAG AA min for body.
- **Existing screens during partial rollout**: when only Phase 1 has landed,
  flag-ON Diary still renders existing diary; only Cluster screens use new
  tokens. UX checks this is acceptable per phase.

## Requirements

### Functional Requirements

- **FR-015-001**: System MUST add a `RuntimeFlag The flag MUST be read **once
  at Activity create** and propagated via a `LocalRuntimeFlags` Composition
  Local; refit composables MUST NOT observe the flag reactively. (See
  Edge Cases — flag flips mid-session.)s.useNewVisualLanguage: Boolean`
  flag, default `false`, in `app/src/main/java/com/capsule/app/RuntimeFlags.kt`.
  All refit composables MUST read this flag (or accept it as a parameter from
  a flag-aware host) before adopting new tokens.
- **FR-015-002**: System MUST add a `BrandAccent` token (`#e8b06a`),
  `BrandAccentDim` (16% alpha amber), and `BrandAccentInk` (`#1a1206`) to
  `app/src/main/java/com/capsule/app/ui/tokens/Colors.kt` as part of
  `CapsulePalette.Tokens`. (Per LD-001.)
- **FR-015-003**: System MUST consolidate `AgentVoiceMark`'s rendering color
  from `inkAccentCluster` to `brandAccent`. The lint detector
  `NoAgentVoiceMarkOutsideAgentSurfaces` and its test MUST be updated to
  reflect the consolidated color (test expectations only — detector logic
  unchanged). (Per LD-001.)
- **FR-015-004**: System MUST append a D4 amendment entry to
  `specs/010-visual-polish-pass/spec.md` (NOTE: tasks.md does not exist for
  spec 010 — see Dependencies for the contradiction surface) recording the
  consolidation: `inkAccentCluster` retired, `brandAccent` adopted.
- **FR-015-005**: System MUST add `app/src/main/java/com/capsule/app/ui/tokens/Type.kt`
  exposing typography stacks: display (`Cormorant Garamond`), body (`Inter`),
  caption (`JetBrains Mono`). Fonts MUST be wired via `app/src/main/res/font/`.
- **FR-015-006**: System MUST add the following Compose primitives to
  `app/src/main/java/com/capsule/app/ui/primitives/`:
  - `OrbitMark` — `Canvas` + `Path`, no font dependency. Mechanism mirrors
    `AgentVoiceMark`. Renders self-dot + tilted ellipse + accent dot.
  - `OrbitWordmark` — `OrbitMark` + serif "Orbit." text.
  - `MonoLabel` — uppercase tracked mono caption.
  - `IntentChip` — pill chip consuming the LD-002 5-intent palette.
  - `SourceGlyph` — round chip per app source (Twitter, Safari, Notes, …).
- **FR-015-007**: Refitted composables MUST NOT change the upstream method
  signatures or call paths into ViewModels. Every refit is a presentation-only
  swap.
- **FR-015-008**: Refit MUST NOT introduce migrations against the
  `IntentEnvelope` schema. The "drop in orbit / archive, add for someone"
  schema migration is referred out (see Dependencies).
- **FR-015-009**: Refit MUST NOT introduce the Slate / philosophy page or
  launch video into the Android app. Both belong on `orbitassistant.com` /
  press kit material.
- **FR-015-010**: Capture sheet refit (Phase 4) MUST NOT use "sealed at save"
  wording. Public framing is "reclassify adds a layer, latest visible."
  (Per LD-003.)
- **FR-015-011**: Settings danger-row copy MUST scope honestly: distinguish
  what Orbit controls (local data, on-device caches) vs what depends on
  third-party LLM-provider SLAs. (Per spec.md context for LD-004.)
- **FR-015-012**: Bubble overlay (`com.capsule.app.bubble.*`) MUST NOT be
  modified by this spec before Demo Day (2026-05-22). (Per LD-005.)
- **FR-015-013**: A Claude-review gate MUST be enforced after every commit
  on `015-visual-refit` before the next commit lands. The gate is not
  automated; it is a manual checkpoint enumerated in `tasks.md` Phase 0.

### Key Entities

This feature has no new data entities. It exclusively touches the presentation
layer.

## Success Criteria

### Measurable Outcomes

- **SC-001**: `:app:compileDebugKotlin` is clean after every commit on
  `015-visual-refit`.two
  pre-existing inherited warnings: `MissingClass ActionsSettingsActivity`
  AND `RemoveWorkManagerInitializer`. Any third warning fails the gate.
  No `lint-baseline.xml` is introduced on this branch; fixing
  `RemoveWorkManagerInitializer` is out of scope (non-refit work)rnings beyond the pre-existing
  `MissingClass` for `ActionsSettingsActivity`.every commit passes the
  per-phase property checklist enumerated verbatim in `research.md` §8.
  Phase 0 commits MUST satisfy all 5 Phase-0 properties (token scope,
  flag presence, tokens/primitives/font-only diff, lint allow-list intact,
  `:app:compileDebugKotlin` clean). Phase 1+ commits MUST satisfy all 7
  per-screen properties (zero hex literals in screen files, zero inline
  `FontFamily(Font(R.font.x))` in screen files, no Material You /
  `dynamicColor = true`, logic-flow tests unchanged, lint allow-list
  unchanged, off-phase screens show zero diff via `git diff --stat`,
  PR-attached screenshot whose **structural composition** — header
  layout, hairline rules, action row position, source glyph row — Claude
  confirms matches the JSX). Properties 1–6 (Phase 1+) and 1–5 (Phase 0)
  are mechanical grep/test gates; property 7 (Phase 1+) is the only
  structural review item and is deliberately not pixel-diffed. No
  Paparazzi/Roborazzi screenshot-test infra is added in this branch.
  `NoAgentVoiceMarkOutsideAgentSurfaces` allow-list is unchanged.
- **SC-004**: All existing instrumented tests for Diary, Cluster, Settings,
  and Capture sheet remain green with `useNewVisualLanguage = false`.
- **SC-005**: With `useNewVisualLanguage = true`, the four refitted screens
  match the design-bundle reference renders within tolerance documented in
  `research.md`. (Diff measured by hand-reviewed screenshot for v1.)
- **SC-006**: Zero changes to `IntentEnvelope` schema files / migrations on
  this branch.
- **SC-007**: Zero changes under `app/src/main/java/com/capsule/app/bubble/`
  on this branch.
- **SC-008**: Every commit on `015-visual-refit` records, in its commit body,
  the Claude review approval (or marker that approval was received).

## Assumptions

- Cormorant Garamond and JetBrains Mono are licensed for redistribution in the
  Android APK (SIL OFL / Apache 2.0). Inter is already broadly used and
  similarly licensed.
- Material You / dynamic color is intentionally NOT adopted for refit surfaces;
  Quiet Almanac is a fixed palette.
- The flag default flip from `false` to `true` is a separate, post-Phase-4
  decision — not part of this spec.
- Demo Day is 2026-05-22; phases land before that date for screens NOT named
  "bubble".
- The phase-11-block-7 primitives (`AgentVoiceMark`, `ClusterActionRow`,
  `CapsulePalette`) survive into the new language. Phase 11 Block 8
  (`ClusterSuggestionCard`) consumes them per the existing spec 002 plan.

## Dependencies

- **DEP-001 (RESOLVED 2026-04-29 — referred out to spec 016)** —
  IntentEnvelope schema migration to adopt the LD-002 intent set ("drop
  in-orbit + archive, add for-someone") is authored as its own spec:
  **`016-intent-set-migration`**. It is a data-layer migration (Room
  schema version bump, `IntentEnvelope` enum change, migration SQL with
  `intentHistoryJson` rewrite for existing rows) with its own rollback
  story, contracts, and risk profile. It cross-cuts capture flow, diary
  mini-intent display, Salesforce `Alpha_User__c`, design canvas
  reference docs, founder kit personas, and `product-dna.md`. Spec 016
  may be drafted in parallel; it does **not** gate spec 015 Phases 0–3.
  It **must** merge before spec 015 Phase 4 (capture sheet) lands.
- **DEP-002 (RESOLVED 2026-04-29)** — User confirmed the D4 amendment
  target is `specs/010-visual-polish-pass/spec.md` (immediately after
  the existing D4 entry, currently at line 171). Spec 010 never had a
  `tasks.md` — `spec.md` was always the only file. The amendment
  wording is locked; see [tasks.md](tasks.md) T015-018 for the exact
  block to insert.
- **DEP-003 (RESOLVED 2026-04-29 — gated)** — Phase 11 Block 7 (PR #4)
  must be merged into `main` before Phase 0 commit 1 can start, because
  Phase 0 commit 1 extends `Colors.kt` (introduced in PR #4) and Phase 0
  commit 3 modifies `AgentVoiceMark.kt` (also PR #4). Required merge
  order: **PR #3 → PR #4 → rebase `015-visual-refit` onto fresh main →
  user green-lights Phase 0 commit 1**. Non-negotiable.

## Out of Scope (Explicit)

- Bubble overlay refit (deferred per LD-005 to post-Demo Day).
- Slate / philosophy page in app (belongs to `orbitassistant.com`).
- Launch video in app (press kit / website).
- IntentEnvelope schema migration (DEP-001).
- Material You / dynamic color adoption.
- Any change under `app/src/main/java/com/capsule/app/bubble/`.
- Cloud LLM gateway changes (spec 014).
- Cluster engine / detector behavior (spec 002 territory).

## Constitutional Notes

- **Principle III (Intent Before Artifact)**: User-facing wording becomes
  "reclassify adds a layer, latest visible" (LD-003). The data layer's
  append-only `intentHistoryJson` semantics are unchanged. Per spec 002
  data-model, a reclassify still appends an audit entry; the refit only
  renames how that semantics is described to the user.
- **Principle X (Sovereign Cloud Storage)** and **Principle IX
  (User-Sovereign Cloud LLM)**: Settings danger-row wording (FR-015-011)
  must not promise behavior that depends on third-party SLAs Orbit cannot
  enforce. "Forget everything" copy explicitly bounds Orbit's commitment to
  Orbit-controlled storage and on-device caches.
- All other 14 principles are untouched by this presentation-only refit.
