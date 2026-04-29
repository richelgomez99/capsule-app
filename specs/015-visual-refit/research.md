# Research: Visual Refit (Quiet Almanac)

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md)
**Reference bundle**: `design/visual-refit-2026-04-29/`

This document inventories the design bundle, maps JSX tokens to Compose
primitives, records the type scale, and walks the 5 screen compositions.
It is the load-bearing reference for Phase 0 implementation.

## 1. Design bundle inventory

| File | Purpose | Maps to (Compose) |
|------|---------|-------------------|
| `project/orbit-tokens.jsx` | colors + type stacks + base primitives | `tokens/Colors.kt` + `tokens/Type.kt` + 5 primitives |
| `project/orbit-screen-diary.jsx` | diary (home) | Phase 2 |
| `project/orbit-screen-cluster.jsx` | cluster detail | Phase 1 |
| `project/orbit-screen-capture.jsx` | capture sheet | Phase 4 |
| `project/orbit-screen-settings.jsx` | settings | Phase 3 |
| `project/orbit-screen-bubble.jsx` | bubble overlay | Phase 5 (DEFERRED) |
| `project/android-frame.jsx` | phone chrome (preview helper) | not shipped |
| `project/Orbit App.html` | preview entry | not shipped |
| `project/Orbit Launch Video.html` | press kit | NOT shipped (out of scope) |
| `chats/` | exploration history | not shipped (reference only) |

## 2. Color token map (JSX → Compose)

Source: `project/orbit-tokens.jsx`, lines 4–18.

| JSX (`ORBIT.*`) | Hex | Compose token | Notes |
|-----------------|-----|---------------|-------|
| `bg`           | `#0e1320` | `paper` (Dark) — already exists, may need re-tone | dark-mode page bg |
| `bgDeep`       | `#080b14` | (new) — used by capture/cluster sheets | sub-surface bg |
| `bgPanel`      | `#141a2b` | (new) — card surface | |
| `bgPanelHi`    | `#1a2236` | (new) — elevated card | |
| `cream`        | `#f3ead8` | `ink` (Dark) | primary text |
| `creamDim`     | `rgba(243,234,216,0.55)` | `inkFaint` | captions |
| `creamFaint`   | `rgba(243,234,216,0.22)` | (use `inkFaint` w/ alpha) | tertiary captions |
| `rule`         | `rgba(243,234,216,0.10)` | `rule` | hairline |
| `ruleHi`       | `rgba(243,234,216,0.18)` | (alpha variant) | emphasized hairline |
| `accent`       | `#e8b06a` | **`brandAccent` (NEW — LD-001)** | single brand accent |
| `accentDim`    | `rgba(232,176,106,0.16)` | **`brandAccentDim` (NEW)** | active chip bg, soft wash |
| `accentInk`    | `#1a1206` | **`brandAccentInk` (NEW)** | text on accent bg |
| `green`        | `#7fb38a` | (no token; status only) | reserved for status |
| `red`          | `#d97a6c` | (no token; status only) | reserved for danger |

**Decision (Phase 0 c1)**: add only `brandAccent`, `brandAccentDim`,
`brandAccentInk` to `CapsulePalette.Tokens` in commit 1. The `bgPanel`,
`bgPanelHi`, `bgDeep`, `ruleHi`, `creamFaint` are screen-local and can be
expressed as alpha variants of existing tokens at the call site, OR added
incrementally per phase. Keep Phase 0 c1 minimal: 3 new tokens.

The `--ink-accent-cluster` token (existing) is **retired in spirit** — its
sole consumer (`AgentVoiceMark`) consolidates to `brandAccent` in Phase 0
c3. Per LD-001, we keep the `inkAccentCluster` field on `Tokens` for one
release as a deprecated alias to avoid breaking out-of-tree consumers,
then remove in a follow-up.

## 3. Typography stacks

Source: `project/orbit-tokens.jsx` lines 21–23.

| Stack | JSX | Compose `CapsuleType.*` | Resource |
|-------|-----|--------------------------|----------|
| Display | `"Cormorant Garamond", "EB Garamond", Georgia, serif` | `displaySerif` | `res/font/cormorant_garamond_*.ttf` |
| Body | `"Inter", system-ui, -apple-system, sans-serif` | `bodySans` | `res/font/inter_regular.ttf` |
| Caption | `"JetBrains Mono", ui-monospace, monospace` | `captionMono` | `res/font/jetbrains_mono_regular.ttf` |

### Type scale (extracted from screens)

Reading the JSX layout values across all 5 screens, the scale is:

| Token | Size (sp) | Stack | Tracking | Usage |
|-------|-----------|-------|----------|-------|
| `displayLarge` | 26 | serif | -0.018em | Cluster hero question, large editorial titles |
| `displayMedium` | 22 | serif | -0.015em | Diary cluster card title |
| `displaySmall` | 17 | serif | -0.01em | Calendar block proposal |
| `wordmark` | 26–28 | serif | -0.01em | `OrbitWordmark` |
| `bodyLarge` | 14 | sans | -0.01em | Standard body / list rows |
| `bodyMedium` | 13 | sans | -0.01em | Chip labels, buttons |
| `captionMonoSmall` | 9–10 | mono | 0.12–0.18em | `MonoLabel` everywhere |
| `captionMonoXSmall` | 9 | mono | 0.14em | Citation labels in cluster bullets |

Italic is used as **emphasis only**, never structural. Italic spans inside
serif headlines render in `brandAccent` color (e.g., "How to think about
*pre-seed valuation* when…").

## 4. Primitive mechanism notes

All primitives shipped in Phase 0 c2 use the existing `Canvas` + `Path`
mechanism that `AgentVoiceMark` and the wax-seal family already use. No
font dependency for the logo glyphs. This is intentional — pixel-stable,
matches the established Phase 11 Block 7 pattern.

### `OrbitMark`

```
- Outer ellipse: rotate(-22°), rx=26, ry=13, stroke=ink @ 1.4 px,
  strokeOpacity=0.55 (mono=true → 0.4)
- Captured object: circle r=3 at (55, 22), fill=brandAccent
- Self: circle r=6.5 at (32, 32), fill=ink
```

Default size 40 dp; viewport 64×64.

### `OrbitWordmark`

`OrbitMark` (size = height × 1.15) + serif "Orbit" text + period rendered
in `brandAccent`. `mono = true` overrides period to `ink` for press use.

### `MonoLabel`

`Text` with `CapsuleType.captionMonoSmall`, uppercase, default color
`inkFaint`. No background.

### `IntentChip`

Pill (corner radius full), 1 px border, optional 14% alpha fill on active.
Tiny 6 dp dot in the chip color leads the label. Intent → color map per
LD-002 (see tasks.md T015-011).

### `SourceGlyph`

Round 22 dp chip per source. Background per source map; glyph (single
char) in white. Mirror the JSX `SourceGlyph` map exactly.

## 5. Screen-by-screen composition

### Diary (`orbit-screen-diary.jsx`) — Phase 2

```
[ Header — MonoLabel(date) above OrbitWordmark | search + clock icons ]
[ Hairline ]
[ "// Orbit noticed" (MonoLabel) ]
[ Cluster card — soft amber wash, brand-amber border, serif title with
  italic accent, source-glyph row, action row ]
[ Day section — "Today · Saturday" (MonoLabel) over hairline ]
[ Diary rows — time | SourceGlyph | preview | intent chip ]
[ More day sections ]
```

### Cluster detail (`orbit-screen-cluster.jsx`) — Phase 1

```
[ Top bar — back arrow | MonoLabel(session name) over title | overflow ]
[ Hairline ]
[ "// what you've been circling" (MonoLabel) ]
[ Hero serif question with italic-amber span ]
[ Source glyphs row + mono timestamp ]
[ Card: bullets — each bullet = MonoLabel(01/02/03 in amber) + sans body
  + citation chips (SourceGlyph + mono label) ]
[ Calendar block proposal — bordered card, serif headline w/ italic-amber
  span, mono caption, 3 buttons ]
[ Footer mono caption — provenance promise ]
```

### Capture sheet (`orbit-screen-capture.jsx`) — Phase 4

(Read this file before Phase 4 begins. Layout includes intent-chip row
using LD-002 5-intent set, serif title, mono captions, Save/Cancel CTAs.)

### Settings (`orbit-screen-settings.jsx`) — Phase 3

(Read this file before Phase 3 begins. Editorial section pattern: MonoLabel
section header, hairline rule, rows. Amber toggle pill. "Forget everything"
danger row.)

### Bubble (`orbit-screen-bubble.jsx`) — Phase 5 DEFERRED

Not analyzed here. Out of scope until post-Demo Day.

## 6. Competitive aesthetic refs

The Quiet Almanac language sits adjacent to:

- **The Browser Company (Arc)** — editorial mono captions, generous serif headlines.
- **Linear** — high-contrast dark + restrained accent; differs in Linear's
  monochrome approach vs Orbit's single warm accent.
- **Things 3 / Bear Notes** — serif emphasis in a productivity tool.
- **NYT Cooking** — editorial section pattern with mono labels.

These are inspirational only; the visual is led by the bundle, not the
refs.

## 7. Open questions surfaced (for user before implementation)

1. ~~**DEP-001**~~ — **RESOLVED 2026-04-29**: authored as its own spec,
   `016-intent-set-migration`. Spec 015 Phase 4 gates on spec 016 merge.
2. ~~**DEP-002**~~ — **RESOLVED 2026-04-29**: D4 amendment targets
   `specs/010-visual-polish-pass/spec.md` (line 171 area); exact wording
   locked in tasks.md T015-018.
3. ~~**DEP-003**~~ — **RESOLVED 2026-04-29**: PR #3 → PR #4 must merge
   into main first; rebase `015-visual-refit` onto fresh main; user
   green-lights Phase 0 c1.
4. ~~**`inkAccentCluster` deprecation strategy**~~ — **RESOLVED**:
   retire the field immediately in Phase 0 c3 (no deprecation window —
   `AgentVoiceMark` was the sole consumer).
5. **Font subsetting** — confirm we may subset Cormorant Garamond to
   Latin Extended-A (drops Cyrillic/Vietnamese). Reduces APK size delta.
   *Open.*
6. **Material You / dynamic color** — confirm fixed palette (no Material
   You) is acceptable to product. LD-001 implies yes; calling out
   explicitly. *Open — likely a non-issue but worth a one-line confirm.*
