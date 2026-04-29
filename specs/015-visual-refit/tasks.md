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

**Pre-Phase**: confirm with user — DEP-003 (Phase 11 Block 7 PR #4 merged
into `main`) and DEP-002 (D4 amendment goes to `spec.md` not `tasks.md`
which doesn't exist).

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
- [ ] **T015-003** [P0c1] Subset Cormorant Garamond (Regular + Italic) to
  Latin Extended-A glyph set (license: SIL OFL). Place TTFs under
  `app/src/main/res/font/`. Add `font_cormorant_garamond.xml`
  `<font-family>` declaration.
- [ ] **T015-004** [P0c1] Add Inter Regular + JetBrains Mono Regular under
  `app/src/main/res/font/` with matching `<font-family>` XMLs.
- [ ] **T015-005** [P0c1] Add `RuntimeFlags.useNewVisualLanguage: Boolean`
  in `RuntimeFlags.kt`, default `false`. Add `@Volatile` per existing
  convention. KDoc: "spec 015 — gates the Quiet Almanac visual refit."
- [ ] **T015-006** [P0c1] Verify gates: `:app:compileDebugKotlin`,
  `:app:lintDebug` (no new warnings), `:build-logic:lint:test` (8/8 green).
- [ ] **T015-007** [P0c1] **CLAIM REVIEW GATE**: commit, push, request
  Claude review. **DO NOT START Commit 2 until approval is recorded in the
  next commit body or the branch's review log.**

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
  `:app:lintDebug` (no new warnings), `:build-logic:lint:test` (still 8/8 —
  these primitives are not gated by the AgentVoiceMark detector).
- [ ] **T015-015** [P0c2] **CLAIM REVIEW GATE**: commit, push, request
  Claude review. **DO NOT START Commit 3 until approval.**

### Commit 3 — `feat(015): consolidate AgentVoiceMark to brand amber + spec 010 D4 amendment`

**Files touched**:

- `app/src/main/java/com/capsule/app/ui/primitives/AgentVoiceMark.kt`
- `build-logic/lint/src/test/java/.../NoAgentVoiceMarkOutsideAgentSurfacesDetectorTest.kt`
- `specs/010-visual-polish-pass/spec.md` (D4 amendment line)

- [ ] **T015-016** [P0c3] One-line color change in `AgentVoiceMark.kt`:
  swap the rendering color from `tokens.inkAccentCluster` to
  `tokens.brandAccent`. Update KDoc to note the consolidation per spec 015
  LD-001.
- [ ] **T015-017** [P0c3] Update `NoAgentVoiceMarkOutsideAgentSurfacesDetectorTest`
  expectations to reflect the brand-amber color (test fixture only —
  detector logic and allow-list are unchanged).
- [ ] **T015-018** [P0c3] Append D4 amendment entry to
  `specs/010-visual-polish-pass/spec.md`. Format:
  ```
  - **D4 amendment (2026-04-29 via spec 015)**: `AgentVoiceMark` color
    consolidated from `--ink-accent-cluster` to single brand amber
    (`BrandAccent` #e8b06a) per spec 015 LD-001. Glyph (✦) and
    exclusivity lint allow-list unchanged.
  ```
  Targeted at `spec.md` rather than `tasks.md` because spec 010 has no
  `tasks.md` (DEP-002).
- [ ] **T015-019** [P0c3] Verify gates: `:app:compileDebugKotlin`,
  `:app:lintDebug` (no new warnings), `:build-logic:lint:test` (8/8 green
  with updated expectations).
- [ ] **T015-020** [P0c3] **CLAIM REVIEW GATE**: commit, push, request
  Claude review. **Phase 0 complete on approval.**

**Phase 0 Checkpoint**: 3 commits landed, each Claude-approved. Tokens +
primitives + AgentVoiceMark consolidation in tree. Flag OFF. Zero behavior
change. Phase 1 may begin.

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
- [ ] **T015-104** [P1] Verify gates: compile + lint clean; existing cluster
  tests (if any) green; manual flag-ON screenshot vs JSX reference recorded
  in PR body.
- [ ] **T015-105** [P1] **REVIEW GATE** per commit landed in Phase 1.
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
- [ ] **T015-204** [P2] Verify existing instrumented diary tests green on
  flag-OFF. Add flag-ON manual screenshot or screenshot test.
- [ ] **T015-205** [P2] **REVIEW GATE** per commit.

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
- [ ] **T015-305** [P3] Verify settings instrumented tests green on
  flag-OFF.
- [ ] **T015-306** [P3] **REVIEW GATE** per commit.

**Phase 3 Checkpoint**: settings refitted; danger-row wording approved
against constitution.

---

## Phase 4 — Capture sheet refit

**Goal**: new chrome, intent chips adopt the LD-002 5-intent set,
"Save" / "Cancel" CTA styling, no "sealed at save" wording.

**Pre-Phase**: confirm with user — DEP-001 (IntentEnvelope schema
migration to drop "in orbit" + "archive", add "for someone") status.
Default = wait for DEP-001 to land. If user approves option (b), surface
the asymmetry explicitly.

- [ ] **T015-401** [P4] Refit `CaptureSheet` chrome per
  `design/visual-refit-2026-04-29/project/orbit-screen-capture.jsx`.
- [ ] **T015-402** [P4] Replace intent chip set with `IntentChip`
  consuming the LD-002 5 intents. Drop "in orbit" + "archive". Add
  "for someone" (with contact-picker stub if DEP-001 landed; else
  rendering-only).
- [ ] **T015-403** [P4] Refit "Save" / "Cancel" CTAs to the new pattern.
  ViewModel calls unchanged.
- [ ] **T015-404** [P4] Audit copy for any "sealed at save" wording across
  capture flow + adjacent strings; replace with "reclassify adds a layer,
  latest visible." (LD-003).
- [ ] **T015-405** [P4] If "for someone" wires to a contact picker, scope
  the picker UI to a separate sub-task; keep behind the same flag.
- [ ] **T015-406** [P4] Verify capture instrumented tests green on
  flag-OFF; on flag-ON, save path persists envelope correctly per
  DEP-001 status.
- [ ] **T015-407** [P4] **REVIEW GATE** per commit.

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
- Phase 4 depends on (Phase 0 complete) AND (DEP-001 resolved or option-b
  approved).
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
