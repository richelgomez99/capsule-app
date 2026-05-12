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

- [x] **T015-001** [P0c1] Extend `CapsulePalette.Tokens` (Light + Dark) with
  `brandAccent` (`#e8b06a`), `brandAccentDim` (16% alpha amber),
  `brandAccentInk` (`#1a1206`). Mirror in `CapsulePalette.Light` and
  `CapsulePalette.Dark`. Update KDoc to reflect that `brandAccent` is the
  consolidated single brand accent (per LD-001).
- [x] **T015-002** [P0c1] Create `app/src/main/java/com/capsule/app/ui/tokens/Type.kt`
  exposing `CapsuleType` object with `displaySerif` (Cormorant Garamond),
  `bodySans` (Inter), `captionMono` (JetBrains Mono), and a typography scale
  (`displayLarge`, `displayMedium`, `bodyLarge`, `bodyMedium`,
  `captionMonoSmall`). Match the size/tracking values from
  `design/visual-refit-2026-04-29/project/orbit-tokens.jsx`.
- [x] **T015-003** [P0c1] Subset Cormorant Garamond (Regular + Italic) to
  Latin Extended-A glyph set (license: SIL OFL). Place TTFs under
  `app/src/main/res/font/`. Add `font_cormorant_garamond.xml`
  `<font-family>` declaration.
- [x] **T015-004** [P0c1] Add Inter Regular + JetBrains Mono Regular under
  `app/src/main/res/font/` with matching `<font-family>` XMLs.
- [x] **T015-005** [P0c1] Add `RuntimeFlags.useNewVisualLanguage: Boolean`
  in `RuntimeFlags.kt`, default `false`. Add `@Volatile` per existing
  convention. KDoc: "spec 015 — gates the Quiet Almanac visual refit."
- [x] **T015-006** [P0c1] Verify gates: `:app:compileDebugKotlin`,
  `:app:lintDebug` (no new warnings), `:build-logic:lint:test` (8/8 green).
- [x] **T015-007** [P0c1] **CLAIM REVIEW GATE**: commit, push, request
  Claude review. **DO NOT START Commit 2 until approval is recorded in the
  next commit body or the branch's review log.**

### Commit 2 — `feat(015): OrbitMark + OrbitWordmark + MonoLabel + IntentChip + SourceGlyph`

**Files touched** (all new):

- `app/src/main/java/com/capsule/app/ui/primitives/OrbitMark.kt`
- `app/src/main/java/com/capsule/app/ui/primitives/OrbitWordmark.kt`
- `app/src/main/java/com/capsule/app/ui/primitives/MonoLabel.kt`
- `app/src/main/java/com/capsule/app/ui/primitives/IntentChip.kt`
- `app/src/main/java/com/capsule/app/ui/primitives/SourceGlyph.kt`

- [x] **T015-008** [P0c2] Create `OrbitMark.kt` — `Canvas` + `Path` with
  tilted ellipse (`-22°`), self-dot (cream), accent-dot (brand amber). No
  font dependency. Mechanism mirrors `AgentVoiceMark`. Default size 40 dp.
- [x] **T015-009** [P0c2] Create `OrbitWordmark.kt` — composes `OrbitMark` +
  serif "Orbit." text using `CapsuleType.displaySerif`. Period rendered in
  brand amber unless `mono = true`.
- [x] **T015-010** [P0c2] Create `MonoLabel.kt` — uppercase tracked mono
  caption per `orbit-tokens.jsx::MonoLabel`. Default 10 sp, letter-spacing
  0.18em equivalent.
- [x] **T015-011** [P0c2] Create `IntentChip.kt` — pill chip consuming the
  LD-002 5-intent palette. Intent → color map:
  - `want it`       → `brandAccent` (amber)
  - `interesting`   → `#c8a4dc`
  - `reference`     → `#a4c8a4`
  - `read later`    → `#dcc384`
  - `for someone`   → `#84b8d6`
  Active state fills 14% alpha background; inactive is hairline border.
  `require(intent in IntentChipKind.values())` enforces the API at the
  boundary.
- [x] **T015-012** [P0c2] Create `SourceGlyph.kt` — round chip per source
  (twitter, safari, podcasts, notes, instagram, sms, chrome, gmail, photos,
  youtube, nyt, substack, files). Mirror the `orbit-tokens.jsx::SourceGlyph`
  map.
- [x] **T015-013** [P0c2] Add Compose `@Preview` for each primitive
  (own-file). Verify they render in the IDE preview pane.
- [x] **T015-014** [P0c2] Verify gates: `:app:compileDebugKotlin`,
  `:app:lintDebug` (no new warnings), `:build-logic:lint:test` (still 8/8 —
  these primitives are not gated by the AgentVoiceMark detector).
- [x] **T015-015** [P0c2] **CLAIM REVIEW GATE**: commit, push, request
  Claude review. **DO NOT START Commit 3 until approval.**

### Commit 3 — `feat(015): consolidate AgentVoiceMark to brand amber + spec 010 D4 amendment`

**Files touched**:

- `app/src/main/java/com/capsule/app/ui/primitives/AgentVoiceMark.kt`
- `build-logic/lint/src/test/java/.../NoAgentVoiceMarkOutsideAgentSurfacesDetectorTest.kt`
- `specs/010-visual-polish-pass/spec.md` (D4 amendment line)

- [x] **T015-016** [P0c3] One-line color change in `AgentVoiceMark.kt`:
  swap the rendering color from `tokens.inkAccentCluster` to
  `tokens.brandAccent`. Retire the `inkAccentCluster` field on
  `CapsulePalette.Tokens` (per the user's 2026-04-29 confirmation —
  no deprecation window; `AgentVoiceMark` was the sole consumer).
  Update KDoc to note the consolidation per spec 015 LD-001.
- [x] **T015-017** [P0c3] Update `NoAgentVoiceMarkOutsideAgentSurfacesDetectorTest`
  expectations to reflect the brand-amber color (test fixture only —
  detector logic and allow-list are unchanged).
- [x] **T015-018** [P0c3] Append D4 amendment entry to
  `specs/010-visual-polish-pass/spec.md`, immediately after the
  existing D4 entry (currently at line 171). Use this exact wording
  (confirmed by user 2026-04-29, DEP-002 resolved):

  ```markdown
  **Amendment 2026-04-29 (spec 015 visual-refit):** AgentVoiceMark
  color consolidates with the brand amber accent (`#e8b06a` /
  `BrandAccent`). The previously reserved `--ink-accent-cluster` token
  is retired. Single-accent rationale: the ✦ glyph carries symbolic
  weight; a separate color was load-bearing only when "agent voice"
  needed to register against a non-amber palette. The visual refit
  unifies on amber, and the ✦ remains the agent-voice marker via
  shape, not color.
  ```

- [x] **T015-019** [P0c3] Verify gates: `:app:compileDebugKotlin`,
  `:app:lintDebug` (no new warnings), `:build-logic:lint:test` (8/8 green
  with updated expectations).
- [x] **T015-020** [P0c3] **CLAIM REVIEW GATE**: commit, push, request
  Claude review. **Phase 0 complete on approval.**

**Phase 0 Checkpoint**: 3 commits landed, each Claude-approved. Tokens +
primitives + AgentVoiceMark consolidation in tree. Flag OFF. Zero behavior
change. Phase 1 may begin.

---

## Phase 1 — Cluster surface (greenfield)

**Goal**: build `ClusterSuggestionCard` and `ClusterDetailScreen` from
scratch on the new tokens. Coordinates with spec 002 Phase 11 Block 8.

- [x] **T015-101** [P1] Build `ClusterSuggestionCard` per
  `design/visual-refit-2026-04-29/project/orbit-screen-diary.jsx` cluster
  card section. Consume `OrbitMark`, `MonoLabel`, `SourceGlyph`,
  `ClusterActionRow`, `AgentVoiceMark` (where applicable per its lint
  allow-list). Read `RuntimeFlags.useNewVisualLanguage`; flag-OFF falls
  through to existing card (or no-op if none yet).
- [x] **T015-102** [P1] Build `ClusterDetailScreen` per
  `design/visual-refit-2026-04-29/project/orbit-screen-cluster.jsx`. Hero
  question (serif), citation chips (per Principle XII), calendar block
  proposal section. ViewModel calls UNCHANGED from spec 002 expectations.
- [x] **T015-103** [P1] Add Compose `@Preview` for both surfaces with
  representative fixture data.
- [ ] **T015-104** [P1] Verify gates: compile + lint clean; existing cluster
  tests (if any) green; manual flag-ON screenshot vs JSX reference recorded
  in PR body.
  - Automated gates run 2026-05-12: `:app:compileDebugKotlin`,
    `:app:testDebugUnitTest --tests 'com.capsule.app.diary.ui.ClusterSuggestionCardTest'`,
    `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`. Manual flag-ON
    screenshot remains pending for the PR body.
- [x] **T015-105** [P1] **REVIEW GATE** per commit landed in Phase 1.
  Phase 1 may span multiple commits; each gated.
  - Review for `ed24b46` completed 2026-05-12: no blockers. Deferred note:
    category-to-glyph mapping centralization belongs to Phase 2 T015-204.

**Phase 1 Checkpoint**: cluster surfaces refitted. Ready for Phase 2.

---

## Phase 2 — Diary refit

**Goal**: Header gets `OrbitWordmark` + `MonoLabel` date stamp. Day groups
adopt hairline + mono date pattern. Source glyphs replace existing
app-icon dots. Navigation/data contracts unchanged.

- [x] **T015-201** [P2] Refit `DiaryScreen` header — `OrbitWordmark` +
  `MonoLabel` date. Read `useNewVisualLanguage` to gate.
- [x] **T015-202** [P2] Refit day-group section pattern — hairline rule
  (`Rule`) + `MonoLabel` date stamp. Match
  `design/visual-refit-2026-04-29/project/orbit-screen-diary.jsx::DiaryDay`.
- [x] **T015-203** [P2] Replace existing app-icon dots in diary rows with
  `SourceGlyph` consumption. Preserve content-descriptions and test tags.
- [x] **T015-204** [P2] Add shared `SourceIdentityResolver` under
  `app/src/main/java/com/capsule/app/ui/primitives/` and consume it from
  `DiaryScreen` so provider URL identity wins over foreground origin for
  primary glyphs. Cover YouTube host variants: `youtube.com`, subdomains,
  `youtu.be`, and `youtube-nocookie.com`.
- [x] **T015-205** [P2] Add unit coverage proving a YouTube URL copied from
  Brave/Messages still renders `SourceGlyphKind.youtube`, while non-YouTube
  browser captures render the browser glyph.
- [ ] **T015-206** [P2] Verify existing instrumented diary tests green on
  flag-OFF. Add flag-ON manual screenshot or screenshot test.
  - Automated gates run 2026-05-12: `:app:compileDebugKotlin`,
    `:app:testDebugUnitTest --tests 'com.capsule.app.ui.primitives.SourceIdentityResolverTest'`,
    `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`.
  - Added flag-OFF/flag-ON coverage in `DiaryScreenWithClusterTest`, including
    provider-first YouTube glyph rendering from a Brave-copied link. Focused
    connected run on Tab S9 still fails before assertions with the repo's
    existing Compose harness error: `No compose hierarchies found in the app`.
    Manual flag-ON screenshot remains pending.
- [x] **T015-207** [P2] **REVIEW GATE** per commit.
  - Review for `11e8881` blocked on source-glyph a11y + duplicated cluster
    glyph mapping; follow-up `2faa5ba` fixed both. Review for `2faa5ba`
    completed 2026-05-12 with no blockers. T015-206 connected/manual gap
    remains open.
- [x] **T015-208** [P2] Refit the clicked capture detail route
  (`EnvelopeDetailScreen`) behind `useNewVisualLanguage`, including source
  header, intent reassignment, enriched link area, original text, intent
  history, audit trail, archive/delete/open/copy/share actions, and loading/
  error states.
  - 2026-05-12 validation: `:app:compileDebugKotlin`,
    `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`,
    `:app:lintDebug`.

**Phase 2 Checkpoint**: diary refitted, tests green.

---

## Phase 3 — Settings refit

**Goal**: editorial section pattern, amber toggle pill, "Forget everything"
danger row with refined wording.

- [x] **T015-301** [P3] Refit `SettingsScreen` section pattern per
  `design/visual-refit-2026-04-29/project/orbit-screen-settings.jsx`.
  Editorial `MonoLabel` headers, hairline rules, serif section titles
  where the JSX uses serif.
- [x] **T015-302** [P3] Replace toggle row chrome with amber toggle pill.
  Preserve toggle ViewModel calls verbatim.
- [x] **T015-303** [P3] Refit "Forget everything" danger row. Copy MUST
  satisfy FR-015-011 — distinguish what Orbit controls (local data,
  on-device caches) vs third-party LLM-provider SLAs. Use LD-004 hardware
  phrasing where local-AI is mentioned ("for local-model-capable phones
  (Pixel 8 Pro+, Galaxy S24+, capable hardware)"). NOT "Pixel 8 and up."
- [x] **T015-304** [P3] Constitutional copy review against Principles IX
  and X recorded in commit body.
- [ ] **T015-305** [P3] Verify settings instrumented tests green on
  flag-OFF.
  - Automated gates run 2026-05-12: `:app:compileDebugKotlin`,
    `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`. Added flag-OFF
    and flag-ON callback/copy coverage in `SettingsScreenTest`. Connected
    execution remains pending with the same Compose harness limitation noted
    under T015-206.
- [x] **T015-306** [P3] Audit nested settings routes and setup/settings
  surfaces for legacy `Capsule` copy. Replace user-facing product copy with
  `Orbit`; leave package names, process names, and developer-only identifiers
  unchanged.
- 2026-05-12 audit replaced user-facing setup, battery guidance, and foreground
  notification copy with `Orbit`. Remaining `Capsule` hits are debug-only labels,
  log tags, theme/package/service identifiers, and token KDoc.
- [x] **T015-307** [P3] Refit nested settings pages, including capture overlay
  settings, to the same Quiet Almanac section pattern as the top-level settings
  screen.
- 2026-05-12 refitted flag-ON `ActionsSettingsUI`, `TrashScreen`, and the
  `MainActivity` capture setup route opened by Settings → Floating bubble.
- [x] **T015-308** [P3] Add/extend settings tests or manual QA notes covering
  top-level settings plus nested capture overlay route with flag ON.
- Added `SettingsScreenTest` coverage for the flag-ON Floating bubble route
  callback and the flag-ON Actions settings skill toggle callback. Automated
  gate: `:app:compileDebugKotlin :app:testDebugUnitTest
  :app:compileDebugAndroidTestKotlin :app:lintDebug`.
- 2026-05-12 physical QA follow-up: Samsung battery guidance now always
  exposes an Open action with safe fallbacks from OEM-specific action to
  battery optimization settings, app details, then system settings. The
  audit-log route opened from Settings → What Orbit did today now has a
  flag-ON Quiet Almanac surface; Actions remains the separate skill settings
  route.
- [x] **T015-309** [P3] **REVIEW GATE** per commit.
  - Review completed 2026-05-12. Follow-up removed duplicate row/switch
    toggle handlers and added a stable test tag for the capture setup row.

**Phase 3 Checkpoint**: settings refitted; danger-row wording approved
against constitution.

---

## Phase 4 — Capture sheet refit

**Goal**: new chrome, intent chips adopt the LD-002 5-intent set,
"Save" / "Cancel" CTA styling, no "sealed at save" wording.

**Pre-Phase**: spec 016 (`016-intent-set-migration`) must be merged into
`main` before Phase 4 starts. (DEP-001 resolved 2026-04-29 — referred out
to its own spec; drafted in parallel; Phase 4 simply waits.)

Local implementation note 2026-05-11: 015 is stacked on clean 016 commit
`96ac77d` via merge commit `aebac2f` so Phase 4 can compile against
`READ_LATER`. Final landing still depends on 016 merging first.

- [x] **T015-401** [P4] Refit `CaptureSheet` chrome per
  `design/visual-refit-2026-04-29/project/orbit-screen-capture.jsx`.
- [x] **T015-402** [P4] Replace intent chip set with `IntentChip`
  consuming the LD-002 5 intents. Drop "in orbit" + "archive". Add
  "for someone" (with contact-picker stub backed by spec 016's enum,
  which has merged by Phase 4 start).
- [x] **T015-403** [P4] Refit "Save" / "Cancel" CTAs to the new pattern.
  ViewModel calls unchanged.
- [x] **T015-404** [P4] Audit copy for any "sealed at save" wording across
  capture flow + adjacent strings; replace with "reclassify adds a layer,
  latest visible." (LD-003).
- [x] **T015-405** [P4] Consume shared `SourceIdentityResolver` in
  `CaptureSheetUI`; display provider-first glyphs consistently with Diary and
  preserve origin label for secondary copy.
- [x] **T015-406** [P4] Refit post-capture intent chip row (`ChipRow`) to the
  Quiet Almanac visual language. This is presentation-only; chip semantics stay
  owned by spec 016.
- [x] **T015-407** [P4] Refit undo/silent-save/confirmation pills to Quiet
  Almanac styling and verify compact pill touch bounds match visible content.
- 2026-05-12 physical QA follow-up: post-capture overlay window width is now
  state-aware: chip selection keeps full-screen width, while undo/status/
  confirmation pills wrap visible content so adjacent app taps are not blocked
  by a full-row overlay.
- [x] **T015-408** [P4] If "for someone" wires to a contact picker, scope
  the picker UI to a separate sub-task; keep behind the same flag.
  - 2026-05-12 audit: no contact picker is wired in spec 015; no-op / N/A.
    "For someone" remains an intent chip only; picker UI stays out of this
    branch.
- [ ] **T015-409** [P4] Verify capture instrumented tests green on
  flag-OFF; on flag-ON, save path persists envelope correctly per
  DEP-001 status.
  - Automated gates run 2026-05-11: `:app:compileDebugKotlin`,
    `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`,
    `:app:lintDebug`. Manual/connected capture verification remains pending;
    use physical S24/Tab S9 flow per T015-905.
  - 2026-05-12 physical QA follow-up removed the capture-sheet footer promise
    of local-only behavior; flag-ON footer now says `PRIVATE BY DEFAULT · USER
    CONTROLLED`.
- [x] **T015-410** [P4] **REVIEW GATE** per commit.
  - Review completed 2026-05-11 after font-weight cleanup: no
    blocking/high/medium findings. Residual physical-QA notes track under
    T015-409/T015-905.

**Phase 4 Checkpoint**: capture sheet refitted; LD-002 intent set live in
UI; LD-003 wording purged.

---

## Phase 5 — Bubble overlay refit

**Goal**: refit the primary floating capture affordance to the Orbit mark
language before Demo Day, while preserving tap/drag reliability and overlay
touch bounds.

- [ ] **T015-501** [P5] Refit the draggable bubble to the Orbit mark language
  from `design/visual-refit-2026-04-29/project/orbit-screen-bubble.jsx`.
- [ ] **T015-502** [P5] Refit dismiss/remove target visuals for portrait,
  landscape, phone, and tablet layouts.
- [ ] **T015-503** [P5] Verify drag-to-remove in landscape on S24 and Tab S9;
  the bubble must be able to reach the target across the full window bounds.
- [ ] **T015-504** [P5] Verify bubble hit target and drag affordance with
  TalkBack/accessibility touch exploration enabled.
- [ ] **T015-505** [P5] Decide whether user-adjustable bubble size and
  transparency belong in this visual-refit phase or a follow-up overlay
  customization spec. If accepted here, add persisted settings controls,
  bounded defaults, and physical QA for tap/drag reliability at every size and
  alpha value.
- [ ] **T015-506** [P5] **REVIEW GATE** per commit.

---

## Cross-Cutting / Polish (post-Phase 4, pre-flag-flip)

- [ ] **T015-901** Manual screenshot sweep: each refitted screen with
  flag = true vs JSX reference. Record diffs in PR body.
- [x] **T015-902** APK size delta report (Cormorant + Inter + JetBrains Mono
  subsets). If > +500 KB, flag to user.
  - 2026-05-12 debug APK font entries: 577,532 bytes uncompressed, 257,591
    bytes compressed (251.6 KiB). Raw source font/XML footprint is 563.5 KiB,
    but packaged APK font payload stays below the +500 KiB flag threshold.
- [x] **T015-903** A11y check: WCAG AA contrast on `bgDeep` + `cream`,
  serif italic accent at body size.
  - 2026-05-12 contrast ratios calculated from Quiet token hex values: cream
    on bgDeep 16.45:1, creamDim over bgDeep approx 5.56:1, accent serif italic
    on bgDeep 10.16:1, red on bgDeep 6.51:1, accentInk on accent 9.58:1.
- [ ] **T015-904** Decision point: flip `RuntimeFlags.useNewVisualLanguage`
  default to `true` (or alpha-build override). Out of scope for this spec
  to actually flip — surface the readiness signal to the user.
- [ ] **T015-905** Physical QA matrix: S24 portrait/landscape + Tab S9
  portrait/landscape for Diary, Settings, Capture sheet, post-capture pills,
  and bubble drag/remove.
  - 2026-05-12 user physical QA pass on temporary flag-ON clean-launcher build:
    new What Orbit did today page renders in Quiet design; Samsung battery
    settings Open action launches; capture-sheet footer no longer promises
    local-only behavior; undo/status pill no longer blocks adjacent taps.
    Remaining matrix coverage: Diary, landscape passes, and bubble drag/remove.
- [ ] **T015-906** Branch hygiene: before opening PRs, split current mixed
  worktree changes into the owning branches: `015-*` for visual refit,
  `016-intent-set-migration` for enum/intent-label migration, and
  `017-capture-feedback-actions` for duplicate/Already Saved product behavior.
  Use [docs/capture-overlay-followups.md](../../docs/capture-overlay-followups.md)
  as the current dirty-worktree routing map.

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
