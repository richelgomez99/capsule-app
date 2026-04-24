# Feature Specification: Visual Polish Pass — "Quiet Almanac" v1

**Feature Branch**: `010-visual-polish-pass`
**Created**: 2026-04-21
**Status**: Draft
**Input**: User feedback — "nice and functional but not up to par with sleek 2026 standards, especially for Notion/Asana-grade knowledge workers."
**Governing documents**: [.specify/memory/constitution.md](.specify/memory/constitution.md), [.specify/memory/design.md](.specify/memory/design.md)
**Depends on**: specs 001, 002 functional surfaces landed (capture, diary, envelope detail, trash, audit log, settings). Does NOT depend on 003–009.
**Blocks**: none. This is a polish pass — behavioral specs remain authoritative for *what* each surface does; this spec binds *how it looks, moves, and feels*.

---

## Why This Spec Exists

[.specify/memory/design.md](.specify/memory/design.md) v1.0 already ratifies Orbit's visual identity (the "Quiet Almanac" — daybook typography, wax-seal intent glyphs, left-margin time rail, paper-grain overlay, zero Material icons). Every Compose task in specs 001–002 was scoped as *"land the functional behavior; defer visual polish to a dedicated slice."* That slice has never existed.

Consequences visible today:
- Plain Material 3 defaults everywhere (purple `primary`, default roboto, standard elevation shadows, filter chips for intent).
- No paper-grain texture, no cream/graphite tokens, no editorial typography.
- No wax-seal glyphs — intent is rendered as `Text("WANT_IT")` or default-styled chips.
- Material `Icons.Filled.*` used throughout the Trash, Audit, Envelope Detail, Settings, Diary day-nav surfaces — in direct violation of design.md §2 ("Zero Material icons. Every affordance is either typography or one of the four wax seals").

Target audience (knowledge workers fluent in Notion, Asana, Linear, Arc, Things) expect deliberate typography, measured motion, and a strong product voice. The current UI reads as "technically working prototype," not "a product I want to live in."

## Non-Goals (v1 polish pass)

- No new functional surfaces. Existing screens keep their current information architecture.
- No Figma redesign of flows. We execute the already-ratified design.md.
- No animation library (Lottie/Compose-Animatable complex choreography). Motion is subtle and Compose-native only.
- No dark-mode rework — design.md §3 defines both palettes; v1 ships both with parity.
- No custom webfonts requiring network fetch (violates Principle I). Bundled fonts only.

---

## User Scenarios & Testing

### User Story 1 — "This looks like Orbit, not a stock Android app" (Priority: P1)

As a returning user, I open the diary and immediately perceive the product's personality: a cream (or graphite, in dark mode) page with a subtle paper grain, a serif day-header paragraph at the top, monospace timestamps hanging in the left margin, envelope cards rendered as ruled editorial entries, and four wax-seal glyphs marking each envelope's intent. The screen breathes — nothing competes for my attention, the hierarchy reads as effortlessly as a well-set magazine.

**Why P1**: The product's defensive moat is trust + aesthetic specificity. Without the visual identity, Orbit is "another screenshot-catcher with a local DB." With it, Orbit is *the daybook app*.

**Acceptance**:
1. **Given** the diary is open on today's page, **When** I look at the top of the screen, **Then** I see the day paragraph set in the spec'd serif face at the spec'd size/leading, with the date in monospace labelmedium above it.
2. **Given** at least one envelope exists, **When** I look at the card, **Then** I see a wax-seal glyph (▲/◆/●/○) in the spec'd accent ink corresponding to its intent, NOT a Material chip or filter pill.
3. **Given** the diary scrolls, **When** it reaches the top, **Then** the paper-grain overlay remains visible at 4% opacity without banding or moiré at 120 Hz.
4. **Given** a screenshot is taken and compared against the design.md §4.2 reference mock, **Then** typography scale, margin widths, and line heights match within ±2 dp.

### User Story 2 — "Motion feels choreographed, not janky" (P1)

As a user tapping the bubble → capture sheet → chip-row → diary entry, I experience a single continuous entrance: bubble expands into sheet with a gentle arc easing, chip row fades in with a measured 120 ms stagger, the new envelope appears in the diary with a brief ink-bleed fade, not a Material slide. Reduce-motion users get static fades of the same duration.

**Why P1**: Knowledge-worker apps (Things, Linear, Arc) treat motion as a first-class product quality. Orbit's "two-second capture experience" (Principle II) deserves as much.

**Acceptance**:
1. All entrance animations respect `Settings.Global.ANIMATOR_DURATION_SCALE` multiplier.
2. Reduce-motion system preference disables directional motion in favor of fades ≤160 ms.
3. No Material default spring — all easings use one of two design.md §7 curves (entrance, settle).
4. No dropped frames on Pixel 8 / Galaxy S24 during the bubble→sheet→diary path (gate: `./gradlew connectedAndroidTest` with a Jank reporter budget).

### User Story 3 — "Every screen speaks the same language" (P1)

As a user navigating between diary → envelope detail → trash → audit log → settings, I notice the shared primitives: the same top-app-bar density, the same ruled dividers, the same intent glyph rendering, the same typography ramp. Nothing feels like "that one screen that looks different."

**Why P1**: Internal consistency IS the product. A single Material-default screen breaks the illusion.

**Acceptance**:
1. Every Compose screen imports from `com.capsule.app.ui.theme.*` (tokens) and `com.capsule.app.ui.primitives.*` (shared components). No screen-local color, typography, or elevation constants.
2. No direct `androidx.compose.material.icons.*` imports outside `/ui/primitives/WaxSeal.kt`, `/ui/primitives/TypoGlyphs.kt`, and the launcher icon resource.
3. Lint rule `OrbitMaterialIconUsage` fires on any rogue Material-icon import introduced after this pass.

### User Story 4 — "The app is readable at every accessibility scale" (P1)

As a user with large system font sizes (130%+) or a color-vision deficiency (protanopia/deuteranopia), I can still parse every screen: intent is carried by glyph **shape** (▲/◆/●/○) first and accent ink second, typography respects system scale, contrast meets WCAG AA across both palettes.

**Why P1**: Principle IV (intent before artifact) is useless if the intent indicator is unreadable to 8% of the population. Knowledge workers also skew older and do use system font scale.

**Acceptance**:
1. All text respects `FontSizeOverride` at 85%/100%/115%/130% without overflow on a 360 dp screen.
2. All text meets WCAG AA (4.5:1 body, 3:1 headlines) on both cream and graphite palettes.
3. Intent wax-seal glyphs are distinguishable at 100% zoom without color (verified via desaturated screenshot diff).

---

## Functional Requirements

**Design token layer** (in `app/src/main/java/com/capsule/app/ui/theme/`):
- **FR-010-001**: System MUST centralize color in `OrbitColorTokens.kt` with cream (light) + graphite (dark) palettes per design.md §3. No direct `Color(0xFF...)` outside this file and `Colors.xml` resources.
- **FR-010-002**: System MUST centralize typography in `OrbitTypography.kt` with a bundled serif (day-header, envelope summary) and a bundled monospace (margins, action labels) per design.md §5. No web-font fetch.
- **FR-010-003**: System MUST centralize elevation, shape, and spacing in `OrbitShapes.kt` + `OrbitSpacing.kt`. Elevation is near-zero — design.md §6 forbids drop-shadow hierarchy; separation is achieved via ruled dividers and spacing.
- **FR-010-004**: System MUST provide `OrbitMotion.kt` with two named easings (entrance, settle) and four canonical durations (instant=80 ms, short=160 ms, medium=240 ms, long=360 ms). Reduce-motion system preference substitutes fade-only variants.

**Shared primitive layer** (in `app/src/main/java/com/capsule/app/ui/primitives/`):
- **FR-010-005**: System MUST ship a `WaxSeal(intent: Intent, size: Dp)` composable rendering ▲ (WANT_IT) / ◆ (FOR_LATER) / ● (REFERENCE) / ○ (AMBIGUOUS) in the intent's accent ink. No PNG assets — glyphs are drawn as typographic characters with a custom `FontFamily` OR via `Canvas.drawPath`.
- **FR-010-006**: System MUST ship `OrbitTopAppBar`, `OrbitDivider`, `OrbitCard`, `OrbitPagerButton` primitives that every screen consumes instead of Material 3 defaults.
- **FR-010-007**: System MUST ship `PaperGrain` modifier that overlays a 4%-opacity bundled PNG (≤16 KB) on any `Surface`. The overlay respects dark/light palette parity.
- **FR-010-008**: System MUST ship `TimeMargin(time: LocalTime)` composable for the left-margin monospace timestamp. Renders right-aligned in a fixed 56 dp rail per design.md §4.1.

**Screen migrations** (every existing Compose surface):
- **FR-010-009**: System MUST migrate [DiaryScreen](app/src/main/java/com/capsule/app/diary/ui/DiaryScreen.kt), [EnvelopeDetailScreen](app/src/main/java/com/capsule/app/diary/ui/EnvelopeDetailScreen.kt), [TrashScreen](app/src/main/java/com/capsule/app/settings/TrashScreen.kt), [AuditLogScreen](app/src/main/java/com/capsule/app/audit/AuditLogScreen.kt), [SettingsScreen](app/src/main/java/com/capsule/app/settings/SettingsScreen.kt), and the Overlay capture sheet to consume the token + primitive layer. No Material 3 defaults remain.
- **FR-010-010**: System MUST migrate the Diary's day-nav bar (`Older day ‹` / `› Newer day`) to `OrbitPagerButton` with typographic glyphs (`‹`/`›`) instead of `Icons.Filled.ChevronLeft`/`Right`.
- **FR-010-011**: System MUST migrate intent display on EnvelopeCard, EnvelopeDetail header, and chip row from FilterChip to `WaxSeal`. Chip row becomes a row of four tappable wax seals labeled by typography below.

**Lint enforcement**:
- **FR-010-012**: System MUST ship lint rule `OrbitMaterialIconUsage` (in `build-logic/lint/`) that fires on any `androidx.compose.material.icons.*` import from any file outside the approved primitive allow-list. Severity ERROR in CI.
- **FR-010-013**: System MUST ship lint rule `OrbitRawColorUsage` that fires on `Color(0xFF...)` literals outside `OrbitColorTokens.kt`.

**Accessibility gates**:
- **FR-010-014**: System MUST pass a desaturated-screenshot diff test proving intent glyphs remain distinguishable without color.
- **FR-010-015**: System MUST pass WCAG AA contrast ratio verification in a `./gradlew :app:testDebugUnitTest` JVM test over the full token palette × typography ramp cross-product.
- **FR-010-016**: All touch targets MUST be ≥48 dp (Android baseline) even when the visual asset is smaller (wax seal glyph at 24 dp with 12 dp padding on each side).

**Golden tests**:
- **FR-010-017**: System MUST ship Paparazzi (or Roborazzi) golden tests for: DiaryScreen Ready state, DiaryScreen Empty state, EnvelopeDetailScreen Ready, TrashScreen with 3 items, AuditLogScreen with groupings, SettingsScreen. Both palettes × both font scales (100% + 130%). Goldens checked into repo.

---

## Success Criteria

- **SC-010-1**: A screenshot of DiaryScreen shown to 5 product-oriented people, 4+ correctly identify it as "deliberate / editorial / daybook-like" (not "default Android app").
- **SC-010-2**: Zero Material icon imports remain outside the approved allow-list. Lint gates enforce.
- **SC-010-3**: WCAG AA contrast verified across 100% of text/background pairs in both palettes.
- **SC-010-4**: Paparazzi golden tests pass on CI; any visual diff requires a human reviewer to approve the new golden.
- **SC-010-5**: First-paint latency on DiaryScreen remains ≤450 ms cold-start on Pixel 8 (no regression from functional baseline).

---

## Out of Scope / Post-v1

- Haptic choreography (Android 12+ `HapticFeedback` per surface).
- Widget glance surfaces.
- Adaptive palette (Material You dynamic color) — deliberately excluded; Orbit's palette is product identity, not user-themed.
- A dedicated onboarding visual pass (separate spec).

---

## Open Questions

- **D1**: Do we bundle a custom serif (e.g., Source Serif Pro, iA Writer Quattro) or license a paid face? Custom → zero cost but generic; paid → brand-tier look. Recommendation: ship with a well-set free face (e.g., *Source Serif 4*, OFL) in v1; revisit at v1.3.
- **D2**: Wax-seal rendering — typographic character (`FontFamily`) or `Canvas.drawPath`? Path gives pixel-perfect control + color flexibility; font gives cheap rendering + free accessibility labels. Recommendation: path, wrapped in `semantics { contentDescription = "…" }`.
- **D3**: Golden-test framework — Paparazzi (JVM, fast) or Roborazzi (Robolectric-backed, more accurate Compose)? Recommendation: Paparazzi for speed + a minimal Roborazzi pass on the capture-sheet overlay path only.
