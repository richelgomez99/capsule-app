# Tasks: Visual Refit (Quiet Almanac)

**Input**: [spec.md](spec.md), [plan.md](plan.md), [research.md](research.md)
**Branch**: `015-visual-refit` (off `origin/main`)
**Review gate**: Claude reviews EVERY commit on this branch BEFORE the next
commit lands. No commit proceeds without explicit approval.

## Format: `[ID] [P?] [Phase] Description`

- **[P]** = parallelizable with other [P] tasks in the same phase (different files)
- All paths absolute from repo root

---

## Phase 0 — Foundation (3 commits, gated)

**Goal**: tokens + primitives + AgentVoiceMark color consolidation. No screen
changes. Flag defaulted OFF.

**Pre-Phase**: PR #3 → PR #4 must be merged into `main` and
`015-visual-refit` rebased onto fresh main; user green-lights Phase 0
commit 1. (DEP-002 + DEP-003 resolved 2026-04-29; D4 amendment target =
`spec.md`, exact wording locked at T015-018.)

### Commit 1 — `feat(015): brand accent + display + serif tokens`

**Files touched**:

- `app/src/main/java/com/capsule/app/ui/tokens/Colors.kt`
- `app/src/main/java/com/capsule/app/ui/tokens/Type.kt` (new)
- `app/src/main/java/com/capsule/app/RuntimeFlags.kt`
- `app/src/main/res/font/cormorant_garamond_regular.ttf` (new)
- `app/src/main/res/font/cormorant_garamond_italic.ttf` (new)
- `app/src/main/res/font/inter_regular.ttf` (new)
- `app/src/main/res/font/jetbrains_mono_regular.ttf` (new)
- `app/src/main/res/font/font_*.xml` (FontFamily declarations)

- [ ] **T015-001** [P0c1] Extend `CapsulePalette.Tokens` (Light + Dark) with
  `brandAccent` (`#e8b06a`), `brandAccentDim` (16% alpha amber),
  `brandAccentInk` (`#1a1206`). Mirror in `CapsulePalette.Light` and
  `CapsulePalette.Dark`. Update KDoc to reflect that `brandAccent` is the
  consolidated single brand accent (per LD-001).
- [ ] **T015-002** [P0c1] Create `app/src/main/java/com/capsule/app/ui/tokens/Type.kt`
  exposing `CapsuleType` object with `displaySerif` (Cormorant Garamond),
  `bodySans` (Inter), `captionMono` (JetBrains Mono), and a typography scale
  (`displayLarge`, `displayMedium`, `bodyLarge`, `bodyMedium`,
  `captionMonoSmall`). Match the size/tracking values from
  `design/visual-refit-2026-04-29/project/orbit-tokens.jsx`.
- [ ] **T015-003** [P0c1] Subset Cormorant Garamond TTFs to **Latin +
  Latin Extended-A** glyph set only (drop Cyrillic + Vietnamese per
  spec.md Clarifications 2026-04-29 / research.md §7 item 5; license:
  SIL OFL). Run `pyftsubset` (or equivalent — `fonttools`) on the
  upstream TTFs **before** wiring into `res/font/`. Produce per-weight
  files matching research.md §3 (Display serif stack uses Regular +
  Italic): `cormorant_garamond_regular.ttf` and
  `cormorant_garamond_italic.ttf`. Place subset TTFs under
  `app/src/main/res/font/`. Add `font_cormorant_garamond.xml`
  `<font-family>` declaration referencing both weights. If a future
  weight (e.g. Semibold / Semibold-Italic) is added, subset it with the
  same Latin + Latin Extended-A coverage and follow the same naming
  (`_semibold.ttf`, `_semibold_italic.ttf`).
- [ ] **T015-004** [P0c1] Add Inter Regular + JetBrains Mono Regular under
  `app/src/main/res/font/` with matching `<font-family>` XMLs.
- [ ] **T015-005** [P0c1] Add `RuntimeFlags.useNewVisualLanguage: Boolean`
  in `RuntimeFlags.kt`, default `false`. Add `@Volatile` per existing
  convention. KDoc: "spec 015 — gates the Quiet Almanac visual refit.
  Read **once at Activity create** and propagated via `LocalRuntimeFlags`
  Composition Local (FR-015-001 / spec.md Edge Cases — flag mid-session).
  Refit composables MUST NOT observe this flag reactively (no `Flow` /
  `State` / `mutableStateOf` observation); a mid-session flip has no
  visible effect until next Activity recreate."
- [ ] **T015-005A** [P0c1] Scaffold `LocalRuntimeFlags` Composition Local
  in the same commit as T015-005. Add
  `app/src/main/java/com/capsule/app/RuntimeFlagsCompositionLocal.kt`
  exposing `val LocalRuntimeFlags = staticCompositionLocalOf<RuntimeFlags>
  { error("RuntimeFlags not provided") }` (or equivalent
  `compositionLocalOf` if a sensible default is preferred). Wire the
  hosting Activity (`MainActivity` / equivalent) so it reads
  `RuntimeFlags.useNewVisualLanguage` **once** in `onCreate` (or via the
  existing flag holder) and provides it through
  `CompositionLocalProvider(LocalRuntimeFlags provides …)` around the
  root Composable. Assert in code review that **no `collectAsState`,
  `Flow`, or reactive observer reads `useNewVisualLanguage`** (FR-015-001).
- [ ] **T015-006** [P0c1] Verify gates: `:app:compileDebugKotlin`,
  `:app:lintDebug` (baseline = exactly two pre-existing warnings —
  `MissingClass ActionsSettingsActivity` AND
  `RemoveWorkManagerInitializer`; any third warning fails the gate per
  SC-002 / spec.md Clarifications 2026-04-29),
  `:build-logic:lint:test` (8/8 green).
- [ ] **T015-007** [P0c1] **CLAIM REVIEW GATE**: commit, push, request
  Claude review. Claude review verifies all Phase-0 properties 1–5 per
  research.md §8. **DO NOT START Commit 2 until approval is recorded in
  the next commit body or the branch's review log.**

### Commit 2 — `feat(015): OrbitMark + OrbitWordmark + MonoLabel + IntentChip + SourceGlyph`

**Files touched** (all new):

- `app/src/main/java/com/capsule/app/ui/primitives/OrbitMark.kt`
- `app/src/main/java/com/capsule/app/ui/primitives/OrbitWordmark.kt`
- `app/src/main/java/com/capsule/app/ui/primitives/MonoLabel.kt`
- `app/src/main/java/com/capsule/app/ui/primitives/IntentChip.kt`
- `app/src/main/java/com/capsule/app/ui/primitives/SourceGlyph.kt`

- [ ] **T015-008** [P0c2] Create `OrbitMark.kt` — `Canvas` + `Path` with
  tilted ellipse (`-22°`), self-dot (cream), accent-dot (brand amber). No
  font dependency. Mechanism mirrors `AgentVoiceMark`. Default size 40 dp.
- [ ] **T015-009** [P0c2] Create `OrbitWordmark.kt` — composes `OrbitMark` +
  serif "Orbit." text using `CapsuleType.displaySerif`. Period rendered in
  brand amber unless `mono = true`.
- [ ] **T015-010** [P0c2] Create `MonoLabel.kt` — uppercase tracked mono
  caption per `orbit-tokens.jsx::MonoLabel`. Default 10 sp, letter-spacing
  0.18em equivalent.
- [ ] **T015-011** [P0c2] Create `IntentChip.kt` — pill chip consuming the
  LD-002 5-intent palette. Intent → color map:
  - `remind me`     → `brandAccent` (amber)
  - `inspiration`   → `#c8a4dc`
  - `reference`     → `#a4c8a4`
  - `read later`    → `#dcc384`
  - `for someone`   → `#84b8d6`
  Active state fills 14% alpha background; inactive is hairline border.
  `require(intent in IntentChipKind.values())` enforces the API at the
  boundary.
- [ ] **T015-012** [P0c2] Create `SourceGlyph.kt` — round chip per source
  (twitter, safari, podcasts, notes, instagram, sms, chrome, gmail, photos,
  youtube, nyt, substack, files). Mirror the `orbit-tokens.jsx::SourceGlyph`
  map.
- [ ] **T015-013** [P0c2] Add Compose `@Preview` for each primitive
  (own-file). Verify they render in the IDE preview pane.
- [ ] **T015-014** [P0c2] Verify gates: `:app:compileDebugKotlin`,
  `:app:lintDebug` (baseline = exactly two pre-existing warnings —
  `MissingClass ActionsSettingsActivity` AND
  `RemoveWorkManagerInitializer`; any third warning fails per SC-002),
  `:build-logic:lint:test` (still 8/8 — these primitives are not gated by
  the AgentVoiceMark detector).
- [ ] **T015-015** [P0c2] **CLAIM REVIEW GATE**: commit, push, request
  Claude review. Claude review verifies all Phase-0 properties 1–5 per
  research.md §8. **DO NOT START Commit 3 until approval.**

### Commit 3 — `feat(015): consolidate AgentVoiceMark to brand amber + spec 010 D4 amendment`

**Files touched**:

- `app/src/main/java/com/capsule/app/ui/primitives/AgentVoiceMark.kt`
- `build-logic/lint/src/test/java/.../NoAgentVoiceMarkOutsideAgentSurfacesDetectorTest.kt`
- `specs/010-visual-polish-pass/spec.md` (D4 amendment line)

- [ ] **T015-016** [P0c3] One-line color change in `AgentVoiceMark.kt`:
  swap the rendering color from `tokens.inkAccentCluster` to
  `tokens.brandAccent`. Retire the `inkAccentCluster` field on
  `CapsulePalette.Tokens` (per the user's 2026-04-29 confirmation —
  no deprecation window; `AgentVoiceMark` was the sole consumer).
  Update KDoc to note the consolidation per spec 015 LD-001.
- [ ] **T015-017** [P0c3] Update `NoAgentVoiceMarkOutsideAgentSurfacesDetectorTest`
  expectations to reflect the brand-amber color (test fixture only —
  detector logic and allow-list are unchanged).
- [ ] **T015-018** [P0c3] Append D4 amendment entry to
  `specs/010-visual-polish-pass/spec.md`, immediately after the
  existing D4 entry (currently at line 171). Use this exact wording
  (confirmed by user 2026-04-29, DEP-002 resolved):

  ```
  **Amendment 2026-04-29 (spec 015 visual-refit):** AgentVoiceMark
  color consolidates with the brand amber accent (`#e8b06a` /
  `BrandAccent`). The previously reserved `--ink-accent-cluster` token
  is retired. Single-accent rationale: the ✦ glyph carries symbolic
  weight; a separate color was load-bearing only when "agent voice"
  needed to register against a non-amber palette. The visual refit
  unifies on amber, and the ✦ remains the agent-voice marker via
  shape, not color.
  ```
- [ ] **T015-019** [P0c3] Verify gates: `:app:compileDebugKotlin`,
  `:app:lintDebug` (baseline = exactly two pre-existing warnings —
  `MissingClass ActionsSettingsActivity` AND
  `RemoveWorkManagerInitializer`; any third warning fails per SC-002),
  `:build-logic:lint:test` (8/8 green with updated expectations).
- [ ] **T015-020** [P0c3] **CLAIM REVIEW GATE**: commit, push, request
  Claude review. Claude review verifies all Phase-0 properties 1–5 per
  research.md §8. **Phase 0 complete on approval.**

**Phase 0 Checkpoint**: 3 commits landed, each Claude-approved. Tokens +
primitives + AgentVoiceMark consolidation in tree. Flag OFF. Zero behavior
change. Phase 1 may begin once T015-VERIFY-CONTRAST clears.

### Phase 0 — post-commit verification (must pass before Phase 1)

- [ ] **T015-VERIFY-CONTRAST** [P0] Compute WCAG contrast ratios for
  the two body-size text-on-bg pairs called out in spec.md Edge Cases /
  Clarifications 2026-04-29:
  1. `cream` (`#f3ead8`) on `bgDeep` (`#080b14`) at **14sp regular**
     (matches `bodyLarge` per research.md §3) — MUST hit ≥ 4.5:1 (WCAG
     AA normal text).
  2. `brandAccent` (`#e8b06a`) on `bgDeep` (`#080b14`) at **14sp
     regular** — MUST hit ≥ 4.5:1.
  Use any standard contrast calculator (Material Theme Builder, WebAIM,
  scripted relative-luminance check). Record both ratios in research.md
  §3 (append a small "Contrast verification" sub-section).
  **If brand-accent fails 4.5:1 at 14sp**: amend research.md §3 to
  document that **accent-italic spans render only at ≥ 18sp display
  tier**. Either (a) bump `displaySmall` from 17sp → 18sp for italic
  spans, OR (b) restrict italic to `displayMedium` / `displayLarge`
  only. Update the KDoc on `CapsuleType` in `Type.kt` accordingly so
  future authors don't apply accent italic at body sizes that rely on
  WCAG AA "large text" 3:1 relaxation (forbidden per spec.md Edge
  Cases). Land the doc/code update as a follow-up commit on Phase 0
  (gated by Claude review against research.md §8 Phase-0 properties).
  If both pairs pass 4.5:1, record the ratios and proceed.

---

## Phase 1 — Cluster surface (greenfield)

**Goal**: build `ClusterSuggestionCard` and `ClusterDetailScreen` from
scratch on the new tokens. Coordinates with spec 002 Phase 11 Block 8.

- [ ] **T015-101** [P1] Build `ClusterSuggestionCard` per
  `design/visual-refit-2026-04-29/project/orbit-screen-diary.jsx` cluster
  card section. Consume `OrbitMark`, `MonoLabel`, `SourceGlyph`,
  `ClusterActionRow`, `AgentVoiceMark` (where applicable per its lint
  allow-list). Read `RuntimeFlags.useNewVisualLanguage`; flag-OFF falls
  through to existing card (or no-op if none yet).
- [ ] **T015-102** [P1] Build `ClusterDetailScreen` per
  `design/visual-refit-2026-04-29/project/orbit-screen-cluster.jsx`. Hero
  question (serif), citation chips (per Principle XII), calendar block
  proposal section. ViewModel calls UNCHANGED from spec 002 expectations.
- [ ] **T015-103** [P1] Add Compose `@Preview` for both surfaces with
  representative fixture data.
- [ ] **T015-104** [P1] Verify gates per research.md §8 Phase 1+
  properties:
  (1) `:app:compileDebugKotlin` clean.
  (2) `:app:lintDebug` baseline = exactly two pre-existing warnings
      (`MissingClass ActionsSettingsActivity` AND
      `RemoveWorkManagerInitializer`); any third warning fails per
      SC-002.
  (3) **Grep gate**: zero `Color(0xFF...)` hex literals in the cluster
      screen's files (`grep -rn 'Color(0xFF' app/src/main/java/com/capsule/app/<cluster-screen-paths>`
      returns nothing).
  (4) **Grep gate**: zero inline `FontFamily(Font(R.font.` in the
      cluster screen's files.
  (5) Existing cluster logic-flow tests (if any) green on flag-OFF.
  (6) **`git diff --stat origin/main...HEAD`** shows zero touches outside
      the in-scope cluster screen's files (off-phase screens untouched).
  (7) **PR-attached screenshot** of the refitted cluster screen (flag-ON)
      embedded in the PR body for Claude structural review against
      `design/visual-refit-2026-04-29/project/orbit-screen-cluster.jsx`
      and `orbit-screen-diary.jsx` (cluster card section).
- [ ] **T015-105** [P1] **REVIEW GATE** per commit landed in Phase 1.
  Claude review verifies all Phase 1+ properties 1–7 per research.md §8.
  Phase 1 may span multiple commits; each gated.

**Phase 1 Checkpoint**: cluster surfaces refitted. Ready for Phase 2.

---

## Phase 2 — Diary refit

**Goal**: Header gets `OrbitWordmark` + `MonoLabel` date stamp. Day groups
adopt hairline + mono date pattern. Source glyphs replace existing
app-icon dots. Navigation/data contracts unchanged.

- [ ] **T015-201** [P2] Refit `DiaryScreen` header — `OrbitWordmark` +
  `MonoLabel` date. Read `useNewVisualLanguage` to gate.
- [ ] **T015-202** [P2] Refit day-group section pattern — hairline rule
  (`Rule`) + `MonoLabel` date stamp. Match
  `design/visual-refit-2026-04-29/project/orbit-screen-diary.jsx::DiaryDay`.
- [ ] **T015-203** [P2] Replace existing app-icon dots in diary rows with
  `SourceGlyph` consumption. Preserve content-descriptions and test tags.
- [ ] **T015-204** [P2] Verify gates per research.md §8 Phase 1+
  properties:
  (1) `:app:compileDebugKotlin` clean.
  (2) `:app:lintDebug` baseline = exactly two pre-existing warnings
      (`MissingClass ActionsSettingsActivity` AND
      `RemoveWorkManagerInitializer`); any third warning fails per
      SC-002.
  (3) **Grep gate**: zero `Color(0xFF...)` hex literals in diary screen
      files.
  (4) **Grep gate**: zero inline `FontFamily(Font(R.font.` in diary
      screen files.
  (5) Existing instrumented diary tests green on flag-OFF.
  (6) **`git diff --stat`** shows zero touches outside diary in-scope
      files (off-phase screens untouched).
  (7) **PR-attached screenshot** of the refitted diary screen (flag-ON)
      embedded in the PR body for Claude structural review against
      `design/visual-refit-2026-04-29/project/orbit-screen-diary.jsx`.
- [ ] **T015-205** [P2] **REVIEW GATE** per commit. Claude review
  verifies all Phase 1+ properties 1–7 per research.md §8.

**Phase 2 Checkpoint**: diary refitted, tests green.

---

## Phase 3 — Settings refit

**Goal**: editorial section pattern, amber toggle pill, "Forget everything"
danger row with refined wording.

- [ ] **T015-301** [P3] Refit `SettingsScreen` section pattern per
  `design/visual-refit-2026-04-29/project/orbit-screen-settings.jsx`.
  Editorial `MonoLabel` headers, hairline rules, serif section titles
  where the JSX uses serif.
- [ ] **T015-302** [P3] Replace toggle row chrome with amber toggle pill.
  Preserve toggle ViewModel calls verbatim.
- [ ] **T015-303** [P3] Refit "Forget everything" danger row. Copy MUST
  satisfy FR-015-011 — distinguish what Orbit controls (local data,
  on-device caches) vs third-party LLM-provider SLAs. Use LD-004 hardware
  phrasing where local-AI is mentioned ("for local-model-capable phones
  (Pixel 8 Pro+, Galaxy S24+, capable hardware)"). NOT "Pixel 8 and up."
- [ ] **T015-304** [P3] Constitutional copy review against Principles IX
  + X recorded in commit body.
- [ ] **T015-305** [P3] Verify gates per research.md §8 Phase 1+
  properties:
  (1) `:app:compileDebugKotlin` clean.
  (2) `:app:lintDebug` baseline = exactly two pre-existing warnings
      (`MissingClass ActionsSettingsActivity` AND
      `RemoveWorkManagerInitializer`); any third warning fails per
      SC-002.
  (3) **Grep gate**: zero `Color(0xFF...)` hex literals in settings
      screen files.
  (4) **Grep gate**: zero inline `FontFamily(Font(R.font.` in settings
      screen files.
  (5) Existing settings instrumented tests green on flag-OFF.
  (6) **`git diff --stat`** shows zero touches outside settings
      in-scope files (off-phase screens untouched).
  (7) **PR-attached screenshot** of the refitted settings screen
      (flag-ON) embedded in the PR body for Claude structural review
      against
      `design/visual-refit-2026-04-29/project/orbit-screen-settings.jsx`.
- [ ] **T015-306** [P3] **REVIEW GATE** per commit. Claude review
  verifies all Phase 1+ properties 1–7 per research.md §8.

**Phase 3 Checkpoint**: settings refitted; danger-row wording approved
against constitution.

---

## Phase 4 — Capture sheet refit

**Goal**: new chrome, intent chips adopt the LD-002 5-intent set,
"Save" / "Cancel" CTA styling, no "sealed at save" wording.

**Pre-Phase**: spec 016 (`016-intent-set-migration`) must be merged into
`main` before Phase 4 starts. (DEP-001 resolved 2026-04-29 — referred out
to its own spec; drafted in parallel; Phase 4 simply waits.)

- [ ] **T015-401** [P4] Refit `CaptureSheet` chrome per
  `design/visual-refit-2026-04-29/project/orbit-screen-capture.jsx`.
- [ ] **T015-402** [P4] Replace intent chip set with `IntentChip`
  consuming the LD-002 5 intents. Drop "in orbit" + "archive". Add
  "for someone" (with contact-picker stub backed by spec 016's enum,
  which has merged by Phase 4 start).
- [ ] **T015-403** [P4] Refit "Save" / "Cancel" CTAs to the new pattern.
  ViewModel calls unchanged.
- [ ] **T015-404** [P4] Audit copy for any "sealed at save" wording across
  capture flow + adjacent strings; replace with "reclassify adds a layer,
  latest visible." (LD-003).
- [ ] **T015-405** [P4] If "for someone" wires to a contact picker, scope
  the picker UI to a separate sub-task; keep behind the same flag.
- [ ] **T015-406** [P4] Verify gates per research.md §8 Phase 1+
  properties:
  (1) `:app:compileDebugKotlin` clean.
  (2) `:app:lintDebug` baseline = exactly two pre-existing warnings
      (`MissingClass ActionsSettingsActivity` AND
      `RemoveWorkManagerInitializer`); any third warning fails per
      SC-002.
  (3) **Grep gate**: zero `Color(0xFF...)` hex literals in capture sheet
      files.
  (4) **Grep gate**: zero inline `FontFamily(Font(R.font.` in capture
      sheet files.
  (5) Capture instrumented tests green on flag-OFF; on flag-ON, save
      path persists envelope correctly per DEP-001 status.
  (6) **`git diff --stat`** shows zero touches outside capture-sheet
      in-scope files (off-phase screens untouched).
  (7) **PR-attached screenshot** of the refitted capture sheet (flag-ON)
      embedded in the PR body for Claude structural review against
      `design/visual-refit-2026-04-29/project/orbit-screen-capture.jsx`.
- [ ] **T015-407** [P4] **REVIEW GATE** per commit. Claude review
  verifies all Phase 1+ properties 1–7 per research.md §8.

**Phase 4 Checkpoint**: capture sheet refitted; LD-002 intent set live in
UI; LD-003 wording purged.

---

## Phase 5 — Bubble overlay refit — DEFERRED

**No actionable tasks in this spec.** Reopen post-Demo Day (after
2026-05-22). Reference: `design/visual-refit-2026-04-29/project/orbit-screen-bubble.jsx`.

---

## Cross-Cutting / Polish (post-Phase 4, pre-flag-flip)

- [ ] **T015-901** Manual screenshot sweep: each refitted screen with
  flag = true vs JSX reference. Record diffs in PR body.
- [ ] **T015-902** APK size delta report (Cormorant + Inter + JetBrains Mono
  subsets). If > +500 KB, flag to user.
- [ ] **T015-903** A11y check: WCAG AA contrast on `bgDeep` + `cream`,
  serif italic accent at body size.
- [ ] **T015-904** Decision point: flip `RuntimeFlags.useNewVisualLanguage`
  default to `true` (or alpha-build override). Out of scope for this spec
  to actually flip — surface the readiness signal to the user.

---

## Dependencies & Execution Order

- Phase 0 commits 1 → 2 → 3 are strictly sequential (each Claude-gated).
- Phase 1 depends on Phase 0 complete.
- Phases 2, 3 depend on Phase 0 complete; can be developed in parallel
  conceptually but each ships behind its own review gate.
- Phase 4 depends on (Phase 0 complete) AND (spec 016 merged into main).
- Phase 5 deferred.
- Cross-cutting tasks (T015-9xx) depend on Phases 1–4 complete.

## Review Gate Protocol (applies to every commit on this branch)

1. Developer pushes commit.
2. Developer requests Claude review (separate session) referencing the
   commit hash and the Phase/Commit ID.
3. Claude reviews against the relevant JSX reference and the spec.
4. Claude posts approve / block / iterate.
5. On approve, the next commit may begin. The approval marker SHOULD be
   recorded in the next commit's body (e.g., `Reviewed-by: Claude (session XYZ) — approved`)
   per SC-008.
6. On block, developer iterates with a follow-up commit on the same branch
   (also gated).
