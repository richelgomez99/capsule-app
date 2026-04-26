# Orbit — Visual Architecture

**Status**: Ratified v1.0 · 2026-04-17
**Scope**: Product-wide design language for all features (001–007) and every
surface Orbit ships on Android. This document is the source of truth that
Jetpack Compose implementation tasks, Figma explorations, and marketing
surfaces all reference.

**Inheritance**:

- Grounded in `.specify/memory/constitution.md` (especially Principles I, III,
  VIII — Local-First Supremacy, Transparency Of Intelligence, Collect Only What
  You Use). Visual intent enforces those principles.
- Enacted in each `specs/00X/` spec's UI sections. Those specs defer every
  visual decision to this file.

> **How to use this doc**: Every Compose screen cites the surface section by
> header (e.g. "per design.md §4.2 Capture Sheet"). Every new visual
> decision goes *here first*, not in a component file. If two specs disagree
> on a shared primitive, this doc wins.

---

## 1. Why Orbit Looks This Way

Orbit is a personal memory layer, not a productivity dashboard, not a
chatbot, not a cloud graveyard. It captures the small stuff you'd otherwise
lose and hands it back to you as a narrative of your day. Trust is the
product. **The visual language has to feel private, intentional, earned.**

This immediately rules out the standard Android app shape: no hamburger, no
bottom nav, no Material icon set, no branded splash, no onboarding carousel
of glossy illustrations, no confetti, no purple gradient.

What it leaves us with is a **daybook**: a cream-or-graphite page, a
ruled margin where time lives, an editorial paragraph at the top of each day,
envelopes rendered as ruled entries, and four wax-seal glyphs for intent.
The app is typography, grain, and a single choreography of entrance. Icons
are rare. Screens breathe.

This is the one thing about Orbit a user remembers: *"the app that looks
like a notebook."* Everything downstream follows from that.

---

## 2. Design Thinking (applied per Anthropic's frontend-design skill)

**Purpose.** Give the user a quiet, continuous, trustworthy record of what
they paid attention to today — so that tomorrow they can find it, and so
that the act of using a phone feels less like dissipation. Audience: adults
who feel their attention leaking into 40 apps and want it back in one
place, privately.

**Tone.** *Quiet Almanac.* Editorial restraint, reference-book discipline.
Reference points: field notebooks (Field Notes, Moleskine daybooks),
editorial magazines (Offscreen, The Point, Aperture), Dieter Rams's
surface language, Japanese binding textures, the typographic hush of a
Ghost blog in read-mode. **Not** cyberpunk, not neobrutalism, not
Notion-calm (too sterile), not Apple-gloss (too performative), not Google
Material (too colorful/friendly).

**Constraints.** Jetpack Compose on Android 10+ (API 29). Must work at 120
Hz without jank. Must look correct from 360 dp to 480 dp screen widths.
Must work for color-blind users and meet WCAG AA contrast. Must respect
system-font overrides on Android for accessibility. 4 % grain overlay
budget ≤ 16 KB baked PNG; animations respect `Settings.Global.ANIMATOR_
DURATION_SCALE` and reduce-motion.

**Differentiation.** Three things nobody else on Android does:

1. **Time lives in the left margin** of the diary page, right-aligned in
   monospace, hanging like marginalia — not beside the card.
2. **Intents are wax-seal glyphs**, not pills — four shapes (▲ ◆ ● ○),
   four accent inks, one glyph per envelope.
3. **Zero Material icons.** Every affordance is either typography
   (`▸` for disclosure, `—` for divider) or one of the four wax seals.
   The only raster asset in the entire app is the launcher icon.

If you show someone a screenshot with no logo, they can tell it's Orbit.

---

## 3. Primitives

### 3.1 Typography

Four families, each with a specific job. No family is used outside its
role. All four are free and self-hosted in the APK (we are local-first —
no Google Fonts CDN calls).

| Role | Family | Weight axis | Used for |
| ---- | ------ | ----------- | -------- |
| **Display** | **Fraunces** (variable, SOFT axis 50, OPSZ optical) | 300 / 400 / 600 | Day header paragraph, page titles, intent name on active chip, onboarding statements, audit section labels |
| **UI Sans** | **Geist** (variable) | 400 / 500 / 600 | Buttons, metadata lines, chip labels, settings rows, menu items, toasts |
| **Content Serif** | **Newsreader** (variable, OPSZ 6–72) | 400 / 500 italic | Captured content body, Nano-generated summaries, blockquotes, extended diary notes |
| **Monospace** | **Berkeley Mono** (commercial; `JetBrains Mono` fallback) | 400 / 500 | Timestamps, envelope IDs, domain names, audit log extraJson, hashes, file paths |

**Scale** (single modular scale, 1.2 ratio, rem-like sp values in Compose):

```
--type-xxs    10sp    Berkeley Mono   — timestamps in margin, badge text
--type-xs     12sp    Geist 400       — metadata, chip sublabel, captions
--type-sm     14sp    Geist 400       — buttons, default UI body
--type-base   16sp    Newsreader 400  — captured content, summaries
--type-md     18sp    Geist 500       — row titles in settings
--type-lg     22sp    Fraunces 400    — section labels in magazine contents
--type-xl     28sp    Fraunces 400    — envelope expanded title
--type-2xl    34sp    Fraunces 300    — day header paragraph
--type-3xl    48sp    Fraunces 300    — onboarding statements, quote answers
--type-4xl    72sp    Fraunces 300    — launch moment, only once per session
```

**Pairing rule.** Fraunces + Newsreader together is forbidden inside the
same block (they fight). Fraunces leads the block, Newsreader carries the
body — or the reverse — but never interleaved. Geist and Berkeley Mono are
utility families and may mix freely.

**Italics.** Newsreader italic is the only italic in the system. Fraunces
italic is never used — we use Fraunces 300 weight with extra tracking
instead. Geist italic is reserved for graceful-degrade copy ("*from an
app · Still · 3:42pm*" when package-usage permission is denied).

### 3.2 Color

Cream-paper and graphite are the only two surfaces Orbit ever draws on.
Dark is the default; light is an opt-in available from Settings → Paper.

**Dark ("Graphite") — default.** Inspired by a moleskine under a reading
lamp.

```
--canvas         #0E0D0B    ≈ near-black, warm undertone     background
--paper          #1A1815    elevated surface (cards, sheets)
--paper-high     #23201B    secondary elevation (modals, drawers)
--ink            #F2EEE5    primary text (warm cream, NEVER #FFF)
--ink-dim        #8B8578    secondary text, metadata
--ink-faint      #4D4A42    tertiary, disabled, strikethrough
--rule           #2A2722    dividers, borders, margin lines
--grain          noise 4% opacity on every surface
```

**Light ("Cream") — opt-in.** Inspired by a daybook in daylight.

```
--canvas         #F5F0E6    cream paper
--paper          #EAE3D4    card surface
--paper-high     #DDD4C0    modal
--ink            #1C1B18    primary text (NEVER #000)
--ink-dim        #6E6A5F    secondary
--ink-faint      #A39E8F    tertiary
--rule           #D4CBB8    dividers
--grain          noise 3% opacity
```

**Intent accents.** Ink colors (never UI colors), used only on the wax-
seal glyph, the chip row, and the "saved as" italic. Each intent has a
**per-mode variant** — the cream-mode inks are deliberately darker and
more saturated than their graphite counterparts so they hit WCAG AA
(≥ 4.5:1 for small text) against their respective canvases. Never use
graphite's ink on cream or vice versa; resolution and warmth are
different-to-the-eye, not just swapped.

```
                    Cream mode      Graphite mode
--intent-want       #8A6D1E         #D9B44A    amber       "Want it"      ▲
--intent-reference  #3E5C4C         #6B8A7A    slate green "Reference"    ◆
--intent-for        #8A4637         #C87361    terracotta  "For someone"  ●
--intent-interest   #4A5580         #7A84B8    dusk blue   "Interesting"  ○
--intent-uncertain  var(--ink-dim)  var(--ink-dim)         "Uncertain"    ·   (was "AMBIGUOUS")
```

The per-mode delta pattern: cream variants are the graphite color
rotated ~8° on the hue wheel (where needed) and dropped ~25 % in
relative luminance. The depth of color rhymes — *"same ink, different
paper"* — even though the hex is different. T005 (theme tokens)
validates every value against WCAG AA before Compose tokens are
frozen.

> **Naming note.** User-facing labels are deliberately softened. `AMBIGUOUS`
> in the data layer is always rendered as **"Uncertain"** in the UI —
> humble, human, honest. Constitution-friendly.

> **Deep treatment.** Dark mode is not just "Graphite tokens." See § 8
> (Dark Mode — A First-Class Surface) for the full per-surface
> behavior: shadows vs. reading glows, dark-mode-only affordances,
> motion quieting, typography weight, and the per-surface delta table.

**System colors (sparing).** Error `#B05546`, warn `#D9B44A` (reuses
amber), info `#7A84B8` (reuses dusk). Success is never a color — it's the
absence of a warning plus a quiet italic word ("Saved").

**Distribution rule.** Dark mode: 70 % `--canvas`, 20 % `--paper`, 8 %
`--ink`, 2 % accent ink. **Never** fill a region wider than ~240 dp with
accent color — intent color is punctuation, not paint.

**Banned color moves** (Orbit-specific addendum to Anthropic's list):

- Purple gradients (the cliché).
- Cyan / teal / mint anywhere.
- Pure `#FFFFFF` text.
- Pure `#000000` backgrounds.
- Material Design indigo/blue system color.
- iOS system blue (`#007AFF`) in any form.
- Any gradient that spans > 2 colors.
- Color coding for status via hue alone (must also carry typographic weight).

### 3.3 Motion

One rule: **choreograph entrances, never exits.** Entrances get
stagger and spring. Exits are abrupt — a daybook flipping closed, not
fading politely.

**Spring defaults** (Compose `spring()`):

```
--motion-hair        stiffness 1200  damping 30   // chips, toasts
--motion-paper       stiffness 600   damping 28   // capture sheet, cards
--motion-margin      stiffness 300   damping 26   // page swipes
```

**Timing defaults** (`tween()` where non-spring):

```
--time-instant    80ms     ease-out cubic   state changes
--time-quick      160ms    ease-out cubic   hover/focus, card reveals
--time-measured   220ms    ease-in-out quad capture sheet rise, page turn
--time-deliberate 340ms    ease-out cubic   day header staggered entry
--time-patient    2000ms   linear           chip row countdown ring
--time-undo       10000ms  linear           10-second undo hairline
```

**Choreographed moments** (one per screen, per Anthropic's "high-impact
moments over scattered micro-interactions"):

- **Diary page open** — day header paragraph reveals line-by-line, 80 ms
  stagger between lines; envelope cards fade in with a 1 dp → 0 dp vertical
  lift, 60 ms stagger; total ≤ 600 ms.
- **Capture sheet rise** — sheet rises `--motion-paper`; clipped content
  text inside crossfades in 120 ms after sheet settles; chip row assembles
  last, each chip +40 ms.
- **Chip tap** — tapped chip fills with its ink over 160 ms (radial from
  tap point), sheet collapses 120 ms later, undo toast slides up 60 ms
  after that.
- **Intent reassignment** — on the old chip, an ink strike-through pen-
  stroke draws left-to-right 180 ms; new chip ink-fills 180 ms after.
- **Day swipe** — horizontal pager; day-header paragraph moves at 0.6×
  viewport velocity (parallax) while envelope cards move at 1.0×. Feels
  like the paragraph is on a different page.

**Reduce-motion override.** When `Settings.Global.ANIMATOR_DURATION_SCALE`
is 0 or the user has enabled "Remove animations," all springs become
`tween(80ms)` and parallax is disabled. Staggered reveals become instant.

**Banned motion moves:**

- Bounce easing.
- Spring overshoot on exits.
- Confetti, sparkles, any particle system.
- "AI thinking" dot pulses. Nano is either ready or graceful-degraded.
- Skeleton loaders (Orbit is local-first; load is < 1 s or the design is
  wrong).

### 3.4 Spatial composition

**The Orbit grid.** A 12-column grid is never used. Instead:

- Outer page padding: `24 dp` horizontal, `32 dp` vertical.
- **Margin column** (left): `64 dp` wide. Contains only timestamps, section
  numerals, hanging punctuation. Right-aligned text. Never any buttons or
  UI chrome.
- **Content column** (right): remaining width. Left-aligned. Justified
  prose only where explicitly noted (diary day header paragraph).
- Inter-card vertical rhythm: `20 dp` + a 1 px `--rule` line between
  threads.

**Asymmetry.** The margin is hard-left on the page. Day header paragraphs
sit in content column but span 0 %–70 % of its width (never edge-to-edge).
Envelope cards use 40 %–100 % of content column depending on content
length — no uniform card width. Short captures render as a single line
typography, long captures get card-like paper elevation. This is
intentionally uneven.

**Negative space.** Between a day header paragraph and the first envelope:
**96 dp.** Between threads: 40 dp. This is more than Android-native apps
use; it is the point.

**Overlap and depth.** The wax-seal glyph on an envelope card sits **-8 dp
left** of the card's left edge (hangs into the margin). That hang is the
only overlap in the system — it signals "entry."

### 3.5 Backgrounds, texture, depth

**Grain.** A 2× 256×256 px PNG of monochrome film grain, tiled across every
surface at 4 % opacity (dark mode) or 3 % (light mode). Baked once,
shipped in the APK. Non-negotiable — the grain is half the product's
character.

**Shadows.** Two only:

```
--shadow-card     y=2 dp   blur=8 dp   opacity 0.20 (dark) / 0.08 (light)
--shadow-sheet    y=-8 dp  blur=24 dp  opacity 0.30 (dark) / 0.12 (light)
```

No colored shadows. No glow. No inner shadows.

**Gradients.** One type, used twice: a 2-stop vertical gradient on the
capture sheet's top edge fading from `--paper` to `transparent` over
16 dp (a "binding" gradient). Used again on the top of the audit log
screen. Nowhere else.

**Borders.** 1 px `--rule` only. Never 2 px, never dashed, never rounded
corners > 4 dp.

**Corner radius.** Four values:

```
--radius-0    0 dp   default for cards, sheets, chips — yes, zero
--radius-1    2 dp   inputs, buttons
--radius-2    8 dp   modal, wax-seal glyph (circle approximation)
--radius-full full   overlay bubble only (the literal circle)
```

Orbit is a rectangular notebook. Rounded cards are banned.

### 3.6 Iconography

**Four glyphs** in the entire product. They are drawn in Compose with
`Canvas` primitives, not bundled as SVG, so they always render at pixel
density and can accept intent-ink tint.

| Glyph | Shape | Intent |
| ----- | ----- | ------ |
| **▲** | Filled equilateral triangle, 12 dp | Want it |
| **◆** | Filled diamond (45° square), 10 dp | Reference |
| **●** | Filled circle, 10 dp | For someone |
| **○** | Unfilled circle, 10 dp, 1.5 px stroke | Interesting |
| **·** | Small centered dot, 4 dp, `--ink-faint` | Uncertain |

**Typographic affordances** replace Material icons everywhere else:

```
▸      disclosure (closed)
▾      disclosure (open)
→      "go to" / navigation
←      back
—      divider / subtitle separator
·      metadata separator ("Social · Still · 3:42pm")
∷      section divider inside a card ("title ∷ domain")
↻      retry (the only non-typographic glyph besides wax seals; 12 dp Canvas)
```

**Launcher icon.** A bronze wax disc (filled circle `#A87434`, subtle
gradient to `#8D5E2A`), with a single hairline orbiting dot (`#F2EEE5`,
1.5 px) on a 24 ° arc. Cream-noise square background. Monochrome variant
for Android themed icons: solid `#F2EEE5` disc on transparent.

---

## 4. Surface Architecture

For each surface: **when it's built (spec), purpose, composition, key
interactions, and what *not* to do.**

### 4.1 Onboarding (spec 002, US7)

**Purpose.** A 45–60 s quiet pledge, not a feature tour. By the end, the
user knows Orbit is private, what the four intents are, and that the
first page is already open.

**Composition.** Five vertical "pages," full-bleed, swipe-forward only.

- **Page 1 — Statement.** Centered Fraunces 72sp: *"Your day deserves a
  memory."* Grain background. One 4 dp bronze dot drifting on a slow
  orbit in the lower-right quadrant (40 s loop). Bottom: `Begin →` in
  Geist 14sp.
- **Page 2 — The bubble.** Shows a small wax-seal bubble crossing from
  right edge to left over 3 s. Below it, Newsreader serif: *"Copy
  something. I'll catch it."*
- **Page 3 — The four intents.** Four wax-seal glyphs laid out vertically
  with their names in Fraunces 28sp and one-line rationales in Newsreader
  14sp. *"Want it — you mean to come back. Reference — you want to keep
  it. For someone — it's not for you. Interesting — you don't know yet,
  that's fine."*
- **Page 4 — The pledge.** A single paragraph, Newsreader 16sp, justified,
  in a 70 % width column: *"Nothing you capture leaves this device.
  Orbit never talks to a server except to fetch a page you saved, and
  only on your Wi-Fi, only on your charger. You can export or delete
  everything at any time."* Below: four rows, one per permission, each a
  single calm sentence and a `Grant ▸` button.
- **Page 5 — The first page.** Fraunces 48sp: *"Your first page is open."*
  Then a 340 ms hold, then hands off to DiaryActivity (today's page). The
  user is already on the page — no "success screen" theater.

**Reduced-mode branch.** If the user declines overlay or notifications on
Page 4, Page 5 reads: *"You can still read your days."* and hands off to
ReducedModeActivity.

**Don't.** No illustrations. No photos. No logo splash. No progress bar.
No "skip" button (the whole flow is ≤ 60 s).

### 4.2 Overlay bubble (spec 001 + 002, US1)

**Purpose.** The only out-of-app surface. Lives pinned to a screen edge,
signals "I saw something," opens to capture.

**Composition.** 40 dp circular bronze wax disc (`--radius-full`), drop
shadow `--shadow-card`. Default color `#8D5E2A`, matte. No icon inside.

**States.**

- **Idle.** Quiet. A 1.5 dp-wide subtle ring around the disc pulses once
  every 20 s (opacity 0.0 → 0.3 → 0.0 over 1.2 s). Almost invisible.
- **Primed** (clipboard contains capture-worthy content). The disc warms
  from bronze toward gold `#D9B44A` over 240 ms. Ring fills solid gold.
- **Dragging.** 1.1× scale, `--shadow-sheet`. Real physics: inertia on
  release + edge-snap animation (`--motion-paper`). Clamp to screen edges
  less status-bar height.
- **Tapped.** Disc splits horizontally (micro-animation, 140 ms), capture
  sheet rises from bottom.

**Don't.** No app icon or branding inside the disc. No count badge. No
notification dot. No draggable resize.

### 4.3 Capture sheet (spec 002, US1)

**Purpose.** The 2-second moment between "I copied something" and "Orbit
saved it with intent."

**Composition.** Rises from bottom to 70 % screen height. `--paper`
background with top binding gradient (§ 3.5). Three zones top-to-bottom:

1. **Captured content preview** — scrollable `SelectionContainer`, 40 %
   of sheet height. Content in Newsreader 16sp. If > 160 chars, last
   visible line fades to `--ink-dim`. A hairline scrollbar appears only
   while scrolling.
2. **Chip row** — four wax-seal chips with names below, 30 % of sheet
   height. Each chip is a 64 dp × 64 dp square (`--radius-2`) with the
   wax seal glyph centered, name in Geist 12sp below, all within a
   single row. A **countdown ring** draws around the entire chip row
   (not individual chips), 1.5 px `--intent-uncertain`, starts full,
   depletes counter-clockwise over 2 s.
3. **Fallback label** — below chip row: *"auto-saves as Uncertain"* in
   Newsreader italic 12sp, `--ink-dim`.

**Silent-wrap variant.** When `SilentWrapPredicate` (T036a) returns
`SILENT_WRAP(intent)`, the sheet *does not open*. Instead the bubble
flashes the intent's ink briefly (120 ms), then the undo toast appears
(§ 4.4). Zero friction.

**Don't.** No "Save" button (the chip IS the save). No other intent
options ("Tag", "Label", "Folder"). No keyboard.

### 4.4 Undo toast (spec 002, US1)

**Purpose.** 10 seconds of graceful "wait no, that's wrong."

**Composition.** Full-width band at bottom, 56 dp tall, inverse of theme
(cream on graphite in dark; ink on cream in light). Text: *"Saved as
**Reference**. Undo."* with the intent name in Fraunces italic — the only
Fraunces italic in the product (see § 3.1 — the exception proves the
rule; italics here carry the intent name's voice). Hairline `--intent-*`
progress at the bottom edge, depletes over 10 s linearly. "Undo" is a
text button in Geist 14sp 500 weight.

**Don't.** No sound. No haptic on appearance (haptic fires on the chip
tap, not the toast). No close "×" — it dismisses itself.

### 4.5 Diary — today's page (spec 002, US2) ⭐ hero surface

**Purpose.** The app's centerpiece. Opening Orbit lands here.

**Composition.** Full-bleed page with § 3.4 grid (margin + content
columns). Top-to-bottom:

1. **Date tag** — Berkeley Mono 10sp, small caps, `--ink-dim`:
   *"THURSDAY · 16 APRIL · 2026"*. Set in the margin column, hanging.
2. **Day header paragraph** — Fraunces 300 34sp, content column, 70 %
   max width, 1.3 line-height. Left-aligned. Staggered line reveal on
   page open (§ 3.3). Nano-generated, ≤ 3 sentences, grounded facts only.
3. **Thin rule** — 1 px `--rule`, spans both columns.
4. **Envelope list.** Chronological, newest on top. Each envelope row:
   - **Margin**: timestamp right-aligned in Berkeley Mono 10sp
     (`11:42A` format, no colon, no AM/PM — `A`/`P` suffix).
   - **Content column**: wax-seal glyph hanging `-8 dp` into the margin,
     then envelope card. Card is paper-elevation only for entries that
     have a summary (after US3 hydrates them); un-hydrated captures
     render as bare Newsreader 16sp typography with no card. Below
     title/content: Geist 12sp `--ink-dim` line: *"Social · Still"* or
     *"Reading · Walking · 3m ago"*.
   - Threads (group of envelopes under same appCategory + activityState +
     hour bucket) get a shared 1 px left-edge rule in `--rule` spanning
     the thread's height. No thread label — the rule is the label.

**Scroll.** Vertical within a day; horizontal across days (unlimited back-
scroll over non-empty days, per T050). Horizontal swipe: page turns with
margin parallax § 3.3.

**Empty state.** Newsreader italic 16sp, centered in the content column,
vertical-centered: *"Nothing captured yet today. Orbit is watching."*

**Don't.** No FAB. No search bar pinned at top. No filter chips. No "New
capture" button. If you want to capture, you use the bubble.

#### 4.5.1 Cluster-suggestion card (added 2026-04-26, spec 002 amendment + spec 010 FR-010-018+)

**Purpose.** The first surface in the entire product where the *agent
speaks*. Surfaces the morning after a research-session cluster forms.
Two-or-three inline actions (e.g., for research-session: *Summarize*,
*Open All*, *Save as Structured Reading List*).

**Placement.** Inside §4.5's composition list, between item #2 (Day
header paragraph) and item #3 (Thin rule). The day header remains the
Diary's hero; the cluster-suggestion card is a secondary agent voice
beneath it. No card means no slot — when no cluster has formed
overnight, the day header runs straight into the thin rule as today.

**Composition** (top-to-bottom inside the card):

1. **Agent-voice mark** — a NEW glyph reserved exclusively for
   agent-spoken surfaces, drawn via the same `Canvas.drawPath`
   mechanism as the four envelope wax seals (§ 3.6) but visually
   distinct. Recommended: ✦ (six-pointed star) at 14 sp in
   `--ink-accent-cluster` (a new accent token; lock by April 30
   alongside `AgentVoiceMark` lint allow-list, spec 010 FR-010-019).
   Hangs in the margin column, right-aligned, like a wax seal.
2. **Card body** — Newsreader italic 16 sp, content column, ≤ 2
   sentences. Lead with the cluster's *what*: *"You had a research
   session on Founder Mode this weekend. 4 captures across Twitter,
   Safari, and a podcast app."* Italic distinguishes agent voice from
   day-header (which is Fraunces non-italic). The italic is the
   product's tonal mark for "this is the agent talking."
3. **Action row** — Geist 12 sp small caps, content column,
   left-aligned. Action labels separated by typographic mid-dot (`·`)
   in `--ink-dim`. Example: `SUMMARIZE · OPEN ALL · SAVE AS LIST`. No
   button chrome. Touch targets pad to ≥ 48 dp per FR-010-016.
4. **Dismiss affordance** — small typographic close (`×` in Geist 14
   sp, `--ink-dim`, top-right of card body row, 48 dp touch target)
   OR swipe-to-dismiss horizontally. Dismissal is per
   `(cluster_id, day_local)`, persistent for that day.

**Card chrome.** Ruled-divider frame consistent with envelope cards
(§ 4.6) — 1 px `--rule` top + bottom only, no side rules, no shadow,
no fill differentiation. The card is *of the page*, not floating
above it. Internal padding: 16 dp top, 16 dp bottom, content-column
horizontal alignment (no separate gutter).

**Stacking.** Max 2 cards per Diary day in v1. If 3+ clusters form
overnight, the lowest-confidence cluster cards drop below a "more"
fold (deferred to v1.1 — flag in spec 010 D5).

**Motion.** Card fades in over §3.3 *short* duration when first
rendered on a fresh diary open. No directional motion. Reduce-motion
respects the same fade. Dismissal is an instant collapse with the
sibling envelope list animating up over the same *short* duration.

**Empty-state behavior.** When the cluster-suggestion card is
present, the Diary's existing empty-state copy (*"Nothing captured
yet today. Orbit is watching."*) is suppressed — the card itself is
the day's first content. Cards persist across days only via the KG
cluster_id; visually they only appear once on the day they form.

**Don't.** No notification, no banner, no badge. The card waits
patiently in the diary; the user finds it when they open Orbit.
Principle V (silence is a feature) is constitutionally enforced
here. No avatar (the agent doesn't have a face). No Material
Surface elevation. No purple. No emoji.

### 4.6 Envelope card — expanded (spec 002, US2 + US3)

**Purpose.** Make a single captured item legible and actionable without
leaving the diary page.

**Composition.** Tap a row; the row expands in place (pushes siblings
down). Expanded card:

1. **Title** — Fraunces 28sp, up to 3 lines. For URL envelopes, this is
   the page title after hydration; for text, the first sentence.
2. **Summary** — Newsreader 16sp, 2–3 sentences, Nano-generated. Only
   present for hydrated URL captures.
3. **Metadata strip** — Geist 12sp, `--ink-dim`, single line:
   `domain.com ∷ captured 3:42P · Instagram · Still`. Domain in Berkeley
   Mono, underlined.
4. **Intent history** — only rendered if reassigned. Newsreader italic
   12sp: *"Was Interesting at 3:42P. Became Reference at 5:18P."*
5. **Action drawer** — collapsed by default as *"▸ Actions"*. On tap,
   reveals: `Reassign` (opens chip row inline), `Archive`, `Delete`,
   `Open` (URL envelopes), `Share` (Android system share). All in Geist
   14sp, left-aligned, one per line, divider rules between.

**Don't.** No icons on action rows. No pop-up context menu. No long-press
menu. No "edit" (Orbit captures, it doesn't annotate).

### 4.7 Settings (spec 002, US6 + US7)

**Purpose.** A magazine masthead, not a preferences dump. Every row
answers a question a user might actually ask.

**Composition.** Plain scrolling page, § 3.4 grid. Top-level sections in
Fraunces 22sp small-caps as anchors:

- **WHAT ORBIT KNOWS ABOUT YOU** — four rows, one per permission, each
  with Geist 14sp name + Newsreader 12sp one-line consequence-of-denial.
  Status indicator right-aligned: *"granted"* / *"declined"* in Geist 12sp
  `--ink-dim` or `--intent-want`.
- **WHAT ORBIT DID** → audit log (§ 4.8).
- **TRASH** → trash screen (§ 4.9). Count beside: *"Trash · 3"*.
- **EXPORT** → user-triggered export (§ 4.10).
- **PAUSE** → a single `Pause Orbit` toggle row, full-width, 56 dp tall,
  toggling affects `ContinuationEngine.cancelAll` per T070.
- **PAPER** → theme selector: `Graphite (default) · Cream`. No system-
  follow option (Orbit is the whole experience — don't let the OS dictate
  its tone).
- **ABOUT** → version, constitution link, manifesto text, open-source
  credits.

Each section is separated by 48 dp + 1 px `--rule`.

**Don't.** No search bar. No category icons. No nested modals — everything
is a push navigation. No third-party brand marks.

### 4.8 Audit log — "What Orbit Did" (spec 002, US6)

**Purpose.** Make privacy auditable in a way the user can actually read.

**Composition.** Top band (summary paragraph): Newsreader 16sp, 70 % width,
reads as prose — *"Today, Orbit captured 14 things, enriched 9,
summarized 7, and made 11 network fetches. All fetches were to URLs you
had saved."* Paragraph is generated locally from grouped audit counts.

Below: day switcher (`Today · Yesterday · Last 7 days · All (90d)`),
Geist 14sp row of text links, underline on active.

Below: chronological log. Each row:

- **Margin**: timestamp in Berkeley Mono 10sp.
- **Content**: action verb in Fraunces 14sp small-caps (*"CAPTURED,"
  "ENRICHED," "FETCHED," "SUMMARIZED," "SOFT-DELETED," "HARD-PURGED"*)
  then one-line payload in Newsreader 14sp. Tap to expand: full extraJson
  in a Berkeley Mono 12sp code block, `--paper-high` background, 12 dp
  padding.

**Don't.** No icons. No color coding of actions (typography carries it).
No filter dropdown (day switcher is enough).

### 4.9 Trash (spec 002, US6, from C2 remediation)

**Purpose.** A waiting room, not a garbage bin. Gives users the grace
period they need to undo deletes.

**Composition.** Dimmer than the rest of the app — `--canvas` at 90 %
brightness (in dark mode, shift toward `#0A0908`), grain at 6 % to feel
liminal. Top band: Newsreader 16sp, 70 % width, *"These 3 envelopes will
be released on 16 May, 18 May, and 21 May. Bring any of them back."*

Each row:

- Margin: `released in 23d` in Berkeley Mono 10sp.
- Content: envelope preview (2 lines max, Newsreader 14sp), deleted-at
  timestamp below in Geist 12sp.
- Actions on the right, Geist 12sp: `Restore · Release now`.

**Don't.** No mass-delete button. No filter. No trash-can icon. The
liminality is the affordance.

### 4.10 Export (spec 002, US6)

**Purpose.** Prove that local-first is real — user walks away with
everything in a plain folder.

**Composition.** Single-screen confirmation: Fraunces 34sp headline
*"You take it all with you."* Newsreader 14sp explanation: *"We'll write
every envelope, continuation, audit row, and result to
`/Downloads/Orbit-Export-<timestamp>/` as JSON. Nothing encrypted —
that's up to you after it leaves Orbit."* Below: single `Export ▸` button
in Geist 14sp 500.

On completion: same screen, paragraph replaced with *"Done. 247
envelopes, 189 continuations, 312 audit rows. Open the folder ▸"*.

### 4.11 Reduced mode (spec 002, US7, from U2 remediation)

**Purpose.** When the user denies overlay/notification permissions,
Orbit stays readable instead of becoming broken.

**Composition.** DiaryScreen in read-only mode (no chip row on card tap,
no reassignment). Everything rendered at 85 % opacity. A persistent
banner at the top in Fraunces italic 18sp on `--paper-high` background:
*"Capture is off. Tap to enable."* Tapping deep-links to system
permission settings. On resume with both perms granted, the banner fades
out, full mode resumes, audit logs `PERMISSION_REDUCED_MODE_EXITED`.

**Don't.** No nag modal. No sad-face illustration.

---

## 5. Roadmap Surfaces (designed now, built v1.1–v1.3)

### 5.1 Orbit Actions — v1.1 (spec 003)

**Purpose.** Surface the things you *could* do with what you captured —
add to calendar, build a to-do, weekly digest — without ever
auto-executing.

**Composition.** A new top-level section in Diary: **"Possibilities"**
(Fraunces 22sp small-caps, same visual weight as other section anchors).
Appears above today's envelope list only when Nano extracted ≥ 1 action
from today's captures. Otherwise absent — no "nothing to do yet" state.

Each possibility row:

- Margin: wax-seal glyph for source envelope's intent.
- Content: Fraunces 16sp verb + object (*"Add to Calendar: Dinner with
  Alex"*), Newsreader 12sp secondary (*"from the Chrome capture at
  3:42P — Saturday 7:00 PM"*), Geist 14sp button row below: `Do it ▸ ·
  Dismiss`.

**Tap "Do it ▸".** Hands off to the Android intent (calendar, tasks,
share target) — Orbit never writes into external services directly. On
success, row collapses with a 140 ms ink-blot and a 2-second toast
*"Added."*

**Weekly digest**. Sundays at 6 PM local: Orbit generates one Newsreader
prose paragraph summarizing the week, renders it in a new "Weekly"
section that persists on that Sunday page. Never pushed as a
notification — it waits for the user.

**Don't.** Never auto-create calendar events or tasks. Never send push
notifications about suggestions. Never rank actions by a "confidence" bar.

### 5.2 Ask Orbit — v1.2 (spec 004)

**Purpose.** Retrieval-augmented answers over the personal corpus. On-
device by default; BYOK cloud as a stretch (spec 005).

**Composition.** A new entry point in Settings and via a
globally-available gesture (long-press on the overlay bubble opens *Ask*
mode directly). Ask screen:

- Top: Fraunces italic 34sp placeholder, prompt in Newsreader italic
  (inverse roles just here, because the question *is* the content):
  *"What were you looking at Tuesday about dinner?"*
- Below: single-line input, Geist 16sp, 1 px `--rule` underline only (no
  input box), no placeholder shadow.
- Submit: <kbd>Enter</kbd> or `Ask ▸`.

**Answer layout.** Two zones:

1. **The answer** — Fraunces 300 28sp, full content-column width,
   indented 12 dp, no quote marks (typography *is* the quote). Generated
   from Nano (local) or BYOK LLM (cloud escape hatch). Ends with a
   single Newsreader italic line: *"— from 3 captures on 14 April."*
2. **The citations** — diary-card style, same as § 4.5 envelope rows,
   but each card gets a small Berkeley Mono 10sp footnote number in the
   margin (`¹ ² ³`) matching the answer's citation marks.

**Don't.** No chat history (each question is standalone — Orbit is not a
chatbot). No thumbs up/down. No streaming tokens visible in the UI (if
using Nano, hide the stream, show the answer when complete).

### 5.3 Cloud Boost (BYOK LLM) — v1.1 (spec 005)

**Composition.** Appears in Settings under a new section
**CLOUD BOOST** (only after user opts in). Three states:

- **Off (default).** Section doesn't render. No nagging.
- **Opt-in flow.** A full-screen explainer: Fraunces 34sp *"Bring your
  own model."* Newsreader 14sp paragraph: *"Orbit can use a cloud LLM
  for specific tasks. You provide the key. Orbit stores it in Android's
  Keystore, only the :net process can read it, every call is audited."*
  Below: per-capability toggles (`Classify intent · Summarize · Scan
  sensitivity · Generate day headers`), each off by default. Key entry
  field at bottom.
- **On.** Small Fraunces italic 12sp badge on affected envelopes:
  *"via Anthropic claude-4.5-opus"* or similar — user sees where cloud
  inference happened, always.

**Don't.** Never use cloud LLM for anything the user did not explicitly
toggle on. Never store the key outside Keystore. Never show "thinking"
animations during cloud calls — the user knows cloud is slower, respect
that.

### 5.4 Cloud Storage (BYOC) — v1.2 (spec 006)

**Composition.** Settings section **YOUR CLOUD** (only after opt-in).
Onboarding flow for BYOC:

1. Fraunces 34sp *"Your data lives with you. Even in the cloud."*
2. Newsreader paragraph explaining: user provides their own Postgres URI,
   Orbit creates tables, user owns everything.
3. Per-category opt-in toggles: `Envelopes · Summaries · Embeddings ·
   Knowledge-graph (v1.3)`. Audit log is greyed out with note *"Audit log
   never leaves this device — that's a constitutional guarantee."*
4. Connection string field, test-connection button, sync-now button.

Active state: small Geist 12sp footer on every diary page:
*"Synced to your Postgres · 2m ago"*. Never shown as a loading indicator;
if sync is slow or failed, a small `↻ retry` text link appears.

**Don't.** Never multi-tenant Orbit-hosted storage. Never hide the
connection string. Never sync the audit log.

### 5.5 Knowledge graph — v1.3 (spec 007)

**Purpose.** Surface relationships between envelopes so Ask Orbit can do
multi-hop retrieval.

**Composition.** Graph is invisible by default — it lives underneath Ask
Orbit's retrieval layer. But there's a new diary affordance on every
envelope card's expanded state:

- New row in Actions drawer: `See connections ▸`. Tap opens a **relations
  page**: two columns. Left column: the current envelope card. Right
  column: a list of up to 10 related envelopes, sorted by relationship
  strength (same topic, same entity, linked-from). Each related envelope
  renders as a standard diary row, with a Geist 12sp italic explanatory
  line above: *"Both mention Alex · 2 days ago"* or *"Same source: The
  New Yorker · 5 days ago."*

**No graph visualization.** No node-link diagram. No D3. Orbit doesn't
show knowledge graphs as force-directed blobs — it shows them as **prose
explanations of connections**, because that's what a notebook would do.

**Don't.** Never render as a node-link force graph. Never expose entity
types as category filters (*"People · Places · Topics"*). Language
over data viz.

---

## 6. Android-Native Adaptations

### 6.1 System bars

- Status bar: transparent, content edge-to-edge; `--ink-dim`-colored
  system icons on cream mode, `--ink`-colored on graphite.
- Navigation bar: transparent, gesture navigation only. On 3-button
  devices, the nav bar gets `--canvas` color.
- No app bar / action bar anywhere — settings title is a scroll-bound
  Fraunces heading inside the content.

### 6.2 Theming

- `Orbit.Theme.Graphite` (default) and `Orbit.Theme.Cream`. Both inherit
  from `Theme.Material3.Expressive.DayNight` only so system dialogs look
  OK — Orbit's own surfaces never use Material components.
- No dynamic color (`applyDynamicColor = false`). Samsung's Monet palette
  cannot override Orbit's ink system.

### 6.3 Accessibility

- TalkBack labels: wax-seal glyphs announce *"Reference intent"* etc.;
  date tags announce full date. Timestamps announce as *"3:42 in the
  afternoon"*.
- Minimum contrast: `--ink` on `--canvas` is 14.2:1 (dark) and 12.8:1
  (light) — both far above WCAG AAA.
- System font size override honored up to `--type-3xl`; beyond that, text
  truncates rather than breaks layout.
- Minimum touch targets 48 dp even when the visual is smaller (wax-seal
  glyph is 10 dp but its tap area is 48 dp).

### 6.4 Performance

- Grain texture: a single `BitmapShader` on the root surface, not a
  per-card PNG. ~200 KB in memory.
- Fraunces and Newsreader ship as subsetted variable fonts (Latin
  Extended + common punctuation only) → total font bundle ≤ 280 KB.
- Staggered reveal uses `animateIn` with deferred invocation to keep
  first-frame at < 150 ms.
- Never block the main thread on Nano — if Nano > 80 ms, graceful-
  degrade and show the structured fallback, per research.md.

### 6.5 Haptics

Three moments, never more:

- **Chip tap** — `HapticFeedbackConstants.CONFIRM` (a short, committed
  tick).
- **Bubble settle after drag** — `HapticFeedbackConstants.CONTEXT_CLICK`
  (a subtle edge-snap).
- **Undo toast "Undo" tap** — `HapticFeedbackConstants.REJECT` (a soft
  reversal tick).

No haptic on scrolling, on card expand, on toast appearance, on page
turns. The product is quiet; the haptics are quiet.

---

## 7. Compose Implementation Notes

> These are hints for implementers, not binding rules. They exist so
> Phase 1 setup tasks (T001–T006) can adopt them when the design work
> begins.

- Package `com.capsule.app.ui.theme.*` holds `OrbitTheme`,
  `OrbitColorScheme`, `OrbitTypography`, `OrbitShapes`, `OrbitMotion`.
- **No Material components.** No `Scaffold`, no `Card`, no
  `OutlinedTextField`. Use `Box`, `Column`, `Row`, `Text`,
  `BasicTextField`, `Canvas`.
- Wax seals drawn via `Canvas` with four composables:
  `WantSeal`, `ReferenceSeal`, `ForSomeoneSeal`, `InterestingSeal` — each
  accepts `size: Dp = 12.dp, tint: Color = LocalOrbitColors.current.intentX`.
- Grain rendered via a single `Modifier.drawBehind { drawBitmap(grain,
  tileMode = Clamp) }` on the root canvas.
- Typography: `OrbitTypography` defines `display`, `display2xl`, `title`,
  `body`, `bodySerif`, `meta`, `mono`. Every `Text` composable uses
  `style = OrbitTypography.xxx` — no ad-hoc `fontSize` anywhere.
- Motion: `OrbitMotion.paperSpring`, `OrbitMotion.hairSpring`,
  `OrbitMotion.deliberate` exposed as `AnimationSpec` tokens. Never
  inline a `tween(...)` in a Compose file.
- A single `OrbitPage` root composable applies page padding, grain,
  background, and provides a `LocalOrbitMargin` composition local that
  child composables use to draw into the margin column.
- **Theme tokens are mode-aware.** `LocalOrbitColors.current.intentWant`
  resolves to different hex per mode automatically; callers never branch
  on `isDarkTheme`. Same for `shadowCard`, `glowCard`, `grainOpacity`,
  `staggerGap`, `parallaxFactor`. The per-mode delta table (§ 8.12) is
  the source of truth for what varies.

---

## 8. Dark Mode — A First-Class Surface

Orbit is dark-first. Graphite is the default, Cream is the opt-in.
There is no third mode — no OLED-true-black, no "automatic system
follow," no time-of-day adaptive shift. Two choices, both deliberate.

Yet dark mode is *not* a light-mode color inversion. A dozen primitives
behave differently on graphite than on cream. This chapter is the full
specification of what changes and why. Designers and implementers
should read it before touching any surface section (§ 4–5).

### 8.1 Why Graphite is warm, not black

Graphite (`#0E0D0B`) has an intentional warm bias (R > G > B at
roughly 2200 K color temperature). It is **the inside of a leather
daybook read under a reading lamp**, not a terminal, not an OLED void,
not a Slack channel.

Cold near-black dark modes (neutral `#0A0A0A`, blue-gray `#1E1F22`)
feel like work surfaces because the eye associates cool hues with
screens-as-tools. Orbit is never a tool-surface; even the audit log
at 2 AM should feel like a ship's log, not a dev console. The 2200 K
warmth is the single design decision that makes this difference.

Cream text `#F2EEE5` on Graphite `#0E0D0B` delivers 14.2:1 contrast —
above WCAG AAA — yet the warm shift on both sides prevents that
contrast from reading *harsh*. The same ratio on a cold-black
(`#FFFFFF` on `#0A0A0D`) feels clinical. The difference is ~200 K of
color temperature, and it is everything.

We will never ship a true-black mode. (See § 9 #21.)

### 8.2 Dark mode is not inverted light mode

Seven primitives behave differently when Graphite is the canvas:

1. **Intent inks** use their bright variants (§ 3.2); cream uses its
   darker variants. The warm graphite surface lets saturated inks
   breathe; the cream surface needs darkened inks to earn contrast.
2. **Drop shadows** are ~2.5× stronger in opacity on Graphite (the
   surface is near-black but not black; shadows still register, they
   just need more weight).
3. **Grain** is 4 % on Graphite vs. 3 % on Cream — dark surfaces
   absorb grain more generously before it becomes visible noise.
4. **Rules** (1 px divider lines) are `#2A2722` on Graphite but feel
   fainter than `#D4CBB8` does on Cream, because the eye detects
   luminance delta differently at low ambient brightness. Compensate
   with 20 dp vs. 16 dp vertical rhythm — give them breathing room.
5. **Typography weight** at Fraunces 300 is preserved on Graphite
   (the warm surface keeps stroke contrast). No auto-bump required.
   On a hypothetical pure-black surface, strokes would break — hence
   § 9 #21's ban.
6. **Motion** is quieter (see § 8.7). Dark mode implies a reading
   context; staggers stretch, parallax softens.
7. **Two dark-mode-only affordances exist** (see § 8.8): reading
   glow on focused envelopes, bronze dust on bubble pulse.

### 8.3 Intent ink, per mode — the contrast math

From § 3.2, with the reasoning:

| Intent | Cream ink | Graphite ink | Cream AA | Graphite AA |
|--------|-----------|--------------|----------|-------------|
| Want it (▲) | `#8A6D1E` | `#D9B44A` | 4.8:1 | 5.2:1 |
| Reference (◆) | `#3E5C4C` | `#6B8A7A` | 5.1:1 | 4.9:1 |
| For someone (●) | `#8A4637` | `#C87361` | 5.4:1 | 4.7:1 |
| Interesting (○) | `#4A5580` | `#7A84B8` | 4.6:1 | 5.0:1 |
| Uncertain (·) | `#6E6A5F` | `#8B8578` | 4.5:1 | 4.5:1 |

All values pass WCAG AA small-text contrast against their mode's
canvas. The cream set shifts ~25 % darker in relative luminance from
graphite; the hue rotates ~8° where needed to preserve perceived
"same-ink, different paper" identity. Final validation lives in
T005 (theme tokens) where a unit test asserts every ink/canvas pair
≥ 4.5:1 before shipping.

This resolves the open question previously tracked at § 10.2.

### 8.4 Shadows and glows — dark's depth strategy

Both modes use real drop shadows because Graphite is warm-near-black,
not pure black. Opacity differs:

```
Cream shadows (light on paper)
--shadow-card-cream     y=2 dp   blur=8 dp    rgba(0,0,0,0.08)
--shadow-sheet-cream    y=-8 dp  blur=24 dp   rgba(0,0,0,0.12)

Graphite shadows (heavier to register on dark surface)
--shadow-card-graphite  y=2 dp   blur=8 dp    rgba(0,0,0,0.30)
--shadow-sheet-graphite y=-8 dp  blur=24 dp   rgba(0,0,0,0.40)
```

**Reading glow** is the dark-only counterpoint. When a user taps an
envelope to expand it, the expanded card on Graphite gets a **reading
glow** — a 12 dp blur halo in cream ink at 6 % opacity:

```
--glow-envelope-graphite   blur=12 dp  rgba(242,238,229,0.06)
```

The glow mimics a pool of reading-lamp light falling on the page. It
does not exist on Cream — it would read as a UI hover state there,
ruining the daybook metaphor. On Graphite, it reads as *atmosphere*.

No glow on buttons, toasts, chips, or bubble. Reading glow is
reserved for the focused envelope card only.

### 8.5 Grain per mode

| Mode | Opacity | Character |
|------|---------|-----------|
| Cream | 3 % | Paper absorption — subtle, suggests ink soaking in |
| Graphite | 4 % | Film grain — low-light photography texture |

Why the delta: grain's perceptual impact on a low-luminance surface
is about 0.7× its impact on a high-luminance surface. 4 % on Graphite
reads at ~3 % on Cream visually. The numbers equalize perception.

The grain texture itself is the *same baked PNG* on both modes — only
opacity varies. One asset, two personalities.

### 8.6 Typography on Graphite — why 300 still works

On Cream `#F5F0E6`, Fraunces 300 has plenty of luminance contrast to
preserve its thin strokes. On Graphite `#0E0D0B`, the warm near-black
still has enough "surface density" (it's not true black) that
Fraunces 300 strokes hold. Tested at 16, 22, 28, 34, 48, and 72 sp;
every stroke reads.

If Orbit ever shipped on pure `#000000` (it won't), Fraunces 300
would break — the strokes would fragment into disconnected segments.
This is one of several reasons we refuse a Midnight/true-black mode.

Berkeley Mono's punctuation dots (`·`, `:`, `.`) are the other
fragile element; on Graphite they hold at 10 sp 400. On pure black
they'd vanish. Same verdict.

### 8.7 Motion on dark — quieter, longer

Dark mode implies reading; reading implies slowness. Motion obeys:

| Primitive | Cream | Graphite |
|-----------|-------|----------|
| Staggered line reveal | 80 ms gap | 120 ms gap |
| Envelope card entry stagger | 60 ms | 90 ms |
| Page swipe parallax factor | 0.6× | 0.7× |
| Bubble pulse cadence | every 20 s | every 20 s |
| Chip row countdown ring | plain 1.5 px hairline | 1.5 px hairline + 4 dp additive-blend halo at 30 % |
| Undo toast progress hairline | 100 % intent-ink opacity | 80 % intent-ink opacity |

The additive halo on the countdown ring exists only on Graphite —
the warm surface absorbs a glow without noise; Cream would just look
blurry. The toast hairline drops to 80 % because the dark canvas
already provides its own contrast; 100 % would dominate.

Reduce-motion override (§ 3.3) still collapses all of this to
instant. Dark mode does not override accessibility.

### 8.8 Dark-mode-only affordances

Three moments exist only on Graphite. They are the product's
atmospheric punctuation.

1. **Reading glow on focused envelope.** Already specified in § 8.4.
   On tap-to-expand, the card gets a 12 dp cream-ink halo at 6 %.
   Pool-of-lamplight effect.

2. **Bronze dust on bubble pulse.** The overlay bubble pulses once
   every 20 s (§ 4.2). On Graphite, during the peak of the pulse
   (~400 ms), a 6 dp blur halo in bronze `#A87434` at 5 % opacity
   surrounds the disc. Fades with the pulse. On Cream, this would
   add visual noise to a daylight context; on Graphite it makes the
   bubble feel warm in the peripheral vision. The user notices it
   without ever being able to point to it.

3. **Ship's-log audit code blocks.** On Graphite, the Berkeley Mono
   code blocks in audit log entries (§ 4.8) render on `--paper-high`
   with a 2 px bronze `#A87434` left-edge rule. On Cream, the block
   is flat `--paper-high` with no bronze edge. The bronze rule
   evokes a leather-bound logbook at night — appropriate on
   Graphite, precious on Cream.

### 8.9 Copy shifts (OPEN — see § 10.2)

Non-critical atmospheric copy *may* vary per mode. Under evaluation;
not yet ratified. A sample of what's on the table:

| Surface | Cream | Graphite |
|---------|-------|----------|
| Empty diary | "Nothing captured yet today. Orbit is watching." | "Quiet so far. Orbit is listening." |
| Trash banner (leading clause) | "These will be permanently released on…" | "These will be released on…" |
| Onboarding Page 1 (headline) | "Your day deserves a memory." | (unchanged — the headline is the product's voice) |
| Audit summary verb | "Today, Orbit captured 14 things…" | "Tonight, Orbit captured 14 things…" (after 8 PM only) |

Functional copy (button labels, permission names, data-type names)
never varies. Only atmosphere does. If ratified, copy is stored as
mode-aware string resources; if rejected, this table becomes the
set of writerly variants we explicitly declined and why.

### 8.10 When dark mode is wrong

A defensive note — dark-first products over-reach. Three cases where
Graphite is the wrong answer:

1. **Bright daylight, outdoors, on a phone at low brightness.** Cream
   is correct. Most users will pick this themselves; Orbit does not
   help or nag.
2. **Sustained reading of a long captured passage.** Cream has better
   absorbed-light legibility for dense serif text. If your day is
   mostly long-form articles, you should pick Cream in Settings.
3. **Users who chose Cream.** Never suggest Graphite. No "try dark
   mode?" banner, ever. Their choice is a preference, not a
   mistake to correct.

### 8.11 Mode switching UX

Settings → Paper displays two choices as equal-weight rows — no
"recommended" marker, no star, no preview image:

```
  PAPER

  ▸ Cream                          daylight; absorbed-light
  ▸ Graphite                       reading-lamp dark · DEFAULT
```

Tap to select. Applied across all surfaces with a **340 ms crossfade**
(the only time two modes share a frame). Grain opacity, shadows,
intent inks, motion timing — every mode-aware token interpolates in
the crossfade. No loading state, no toast.

The word `DEFAULT` appears only next to Graphite and only for new
installs that have not yet changed the setting. After the first
user-initiated change, that marker disappears forever — Orbit does
not remind people what it thought was default.

### 8.12 Per-surface dark-mode delta table

The checklist of behaviors per surface. Implementers should consult
this before coding each surface (§ 4–5).

| Surface | Cream | Graphite |
|---------|-------|----------|
| **Canvas (root)** | `#F5F0E6`, grain 3 % | `#0E0D0B`, grain 4 % |
| **Ink** | `#1C1B18` | `#F2EEE5` |
| **Ink-dim (metadata)** | `#6E6A5F` | `#8B8578` |
| **Rule lines** | `#D4CBB8`, 1 px | `#2A2722`, 1 px + 20 dp rhythm (vs 16 dp) |
| **Bubble (§ 4.2)** | Bronze disc, `shadow-card-cream` | Bronze disc, `shadow-card-graphite`, +5 % rim light, **bronze dust on pulse peak** |
| **Bubble pulse cadence** | 20 s, ring 0→30% opacity | 20 s, ring 0→30% opacity, dust halo 800 ms |
| **Capture sheet (§ 4.3)** | Cream paper, `shadow-sheet-cream`, binding gradient top | Graphite paper, `shadow-sheet-graphite`, binding gradient top |
| **Chip row countdown ring** | 1.5 px hairline | 1.5 px hairline + 4 dp additive halo at 30 % |
| **Chip tap ink-fill** | Cream-intent ink radial | Graphite-intent ink radial |
| **Undo toast (§ 4.4)** | Ink on cream (theme-inverse) | Cream on ink (theme-inverse) |
| **Undo hairline opacity** | 100 % intent-ink | 80 % intent-ink |
| **Day header (§ 4.5)** | Fraunces 300, 34 sp | Fraunces 300, 34 sp (unchanged) |
| **Staggered reveal gap** | 80 ms between lines | 120 ms between lines |
| **Timestamps (§ 4.5)** | Berkeley Mono 400, 10 sp | Berkeley Mono 400, 10 sp |
| **Wax seals** | Cream intent-ink palette (§ 8.3) | Graphite intent-ink palette (§ 8.3) |
| **Envelope focus (expanded)** | No glow | Reading glow, 12 dp cream-ink at 6 % |
| **Rules between envelope rows** | Visible, 1 px `#D4CBB8` | Visible, 1 px `#2A2722`, feels fainter (see § 8.2 #4) |
| **Swipe-page parallax** | 0.6× background velocity | 0.7× background velocity |
| **Audit code block (§ 4.8)** | `--paper-high` flat | `--paper-high` + 2 px bronze left-edge rule (ship's log) |
| **Audit verb typography** | Fraunces 14 sp small-caps | Fraunces 14 sp small-caps (unchanged) |
| **Trash (§ 4.9) canvas** | Cream −10 % brightness (≈ `#E8E1D3`) | Graphite shifted warmer (`#1A1815`), grain 6 % |
| **Trash copy tone** | Same as Cream default | May carry shifted copy per § 8.9 (open) |
| **Reduced mode banner (§ 4.11)** | `--paper-high` cream, Fraunces italic | `--paper-high` graphite, Fraunces italic |
| **Launcher icon** | Cream noise + bronze disc + hairline orbit | Same asset (launcher is theme-agnostic) |
| **System status bar** | `--ink-dim` symbols | `--ink` symbols |
| **System nav bar (3-button)** | `--canvas` cream | `--canvas` graphite |

If a surface is not in this table, it behaves identically in both
modes modulo the palette swap. When adding a new surface, add its row
here before the Compose implementation PR lands.

### 8.13 Accessibility on Graphite

- **AAA contrast** preserved: primary text `#F2EEE5` on `#0E0D0B` is
  14.2:1. All intent inks pass AA small-text (§ 8.3). Dimmed
  metadata (`--ink-dim` = `#8B8578`) passes AA at 4.6:1.
- **High-contrast Android a11y setting.** When the user enables the
  OS-level high-contrast setting, Orbit overrides its ink warmth:
  `--ink` becomes `#FFFFFF`, `--canvas` stays `#0E0D0B` (it does
  *not* snap to pure black — warmth trade-off accepted for the
  trade-off of maximum legibility). Wax seals switch to high-
  saturation variants per §  8.3 Graphite column but bumped +15 %
  saturation for redundancy.
- **Color-blind safety.** Intent recognition is glyph-first (shape
  ▲ ◆ ● ○). Color never carries meaning alone — constitutional, and
  unchanged across modes. TalkBack announces *"Want it intent"*
  etc. by shape-derived name.
- **Low-vision zoom.** System font-size override honored up to
  `--type-3xl` in both modes. Grain opacity does not compound with
  zoom (it's on the root canvas, not text).

### 8.14 What we will never build

- **A third mode.** No "Midnight," no OLED-black, no "system-
  follow." Cream and Graphite are the complete set. Orbit commits to
  two flavors; adding a third would dilute both.
- **An automatic mode switcher.** No sunset/sunrise trigger. No
  ambient-light-sensor override. No cron'd "dim at 10 PM." The user
  picks once. (Constitution-adjacent: Orbit does not act on behalf
  of the user without the user's gesture.)
- **Per-screen mode overrides.** You cannot have Cream audit log and
  Graphite diary. Mode is the paper, paper is product-wide.
- **Seasonal or holiday themes.** No light-sky-blue for May, no
  pumpkin-orange for October. Orbit's palette is the palette.

See § 9 #21–#23 for the ban list rendition.

---

## 9. What's Banned in Orbit (addendum to Anthropic's frontend-design
skill "AI slop" list)

Hard bans — a PR containing any of these is rejected on sight:

1. **Inter, Roboto, Arial, SF Pro, system fonts** for anything beyond
   Android system dialogs.
2. **Purple gradients.** Or any gradient on a white/cream background.
3. **Rounded cards** (`--radius-*` > 4 dp except the two sanctioned
   cases: wax seal and overlay bubble).
4. **Material icons.** All of them. Not even `Icons.Default.Search`.
5. **FABs.** No floating action button, ever.
6. **Bottom navigation bars.**
7. **Badges with counts** on icons.
8. **Skeleton loaders** (see § 3.3).
9. **Pill-shaped intent chips.** Intents are wax-seal glyphs.
10. **"AI sparkles,"** shimmer gradients on AI-generated text, stars,
    wands, any visual indication of "magic."
11. **Emoji** in UI copy (exception: user-captured content may contain
    emoji — that's their text, not our UI).
12. **"Thinking…" indicators.** Orbit is either ready or gracefully
    degraded.
13. **Progress bars** with percentages (except the 10-s undo hairline,
    which has no percentage label).
14. **Apple-style blurred glass** (NSVisualEffectView / Material You
    translucency on surfaces). Orbit is opaque paper.
15. **Page transitions that use scale.** No scale-in, no scale-out. Only
    slide and fade.
16. **Color-coded success state.** Success is typographic.
17. **"Powered by" footers.** Ever.
18. **Generated illustrations** from AI image models. Icons and UI are
    hand-authored primitives, period.
19. **Dark/light "auto" toggle.** Theme is always a deliberate user
    choice. (Settings → Paper.)
20. **Confetti. Any celebration animation.**
21. **True-black / OLED-black modes.** No `#000000` canvas, ever.
    Graphite is warm-near-black; pure black breaks Fraunces 300
    strokes and Berkeley Mono's punctuation dots (§ 8.6). It also
    feels cold, which is wrong for a daybook.
22. **Cold or blue-tinted dark modes.** No near-black with a cool
    bias (e.g. `#0A0A0D`, `#1E1F22`). Graphite's ~2200 K warmth is
    not a preference — it is the design. A PR introducing a cooler
    dark variant is rejected on sight.
23. **A third paper / mode beyond Cream and Graphite.** No
    "Midnight," no seasonal themes, no per-user custom canvases.
    Two flavors, both deliberate. (§ 8.14.)
24. **Automatic mode-switching.** No sunset trigger, no ambient-
    light-sensor override, no scheduled dim. The user picks once in
    Settings → Paper. Orbit doesn't act for them.
25. **Per-screen mode overrides.** Mode is the paper; paper is
    product-wide. You cannot have Cream audit log inside a Graphite
    app session.

---

## 10. Open design questions (tracked here, resolved in Figma explorations
before corresponding spec's UI tasks begin)

- **Fonts licensing.** Berkeley Mono is commercial ($). Alternatives:
  JetBrains Mono (free), iA Writer Mono (paid), IBM Plex Mono (free).
  Need to pick. *Recommendation: ship with JetBrains Mono free, keep
  Berkeley Mono as an aspirational upgrade.*
- **Atmospheric copy shifts per mode.** § 8.9 proposes varying a small
  set of non-critical atmospheric strings between Cream and Graphite
  (empty-diary, trash banner, audit summary after 8 PM). Decision:
  ratify the variants, ratify only a subset, or decline entirely and
  keep copy mode-agnostic? If ratified, needs mode-aware string
  resource plumbing in T005. Default position: **decline for v1**,
  revisit in v1.1 with real user reads.
- **Grain on low-end devices.** 4 % grain (Graphite) / 3 % (Cream) over
  the full canvas is ~3 ms per frame on Pixel 5. On sub-Pixel 4a
  devices, drop to baked PNG once per session rather than BitmapShader.
  Needs perf test.
- **First-run bubble position.** Should the bubble appear on right edge
  (right-hand users) or detect system-wide left-handed accessibility
  setting? Default right-edge, but plumb for override.
- **Onboarding Page 3 intent descriptions.** The wording proposed in
  § 4.1 is a first pass. Likely needs a writing round before US7 build.
- **Ask Orbit gesture.** Long-press bubble → Ask mode. Conflicts with
  drag? Needs prototype. Alternative: double-tap bubble.

**Resolved since v1.0:**

- ~~Cream-mode intent-ink contrast~~ — resolved in § 8.3 with per-mode
  intent-ink variants; all values pass WCAG AA against their canvas.

These are open questions. Close them in a named PR (e.g. *"Design
question: fonts licensing — Berkeley Mono vs JetBrains Mono"*) and
update this section when resolved.

---

## 11. Changelog

- **2026-04-17 · v1.1**: Promote dark mode to a first-class chapter
  (§ 8). Resolve cream-mode intent-ink contrast with per-mode variants
  (§ 8.3). Add reading glow and bronze-dust affordances on Graphite
  only (§ 8.4, § 8.8). Quieter dark-mode motion — 120 ms stagger,
  0.7× parallax (§ 8.7). Per-surface delta table (§ 8.12) becomes the
  implementer's checklist. Banned list extended with items #21–#25
  (no true-black, no cold dark, no third mode, no auto-switching, no
  per-screen mode overrides). Compose implementation notes updated to
  mandate mode-aware theme tokens — callers never branch on
  `isDarkTheme` (§ 7 final bullet).
- **2026-04-17 · v1.0**: Initial ratified visual architecture. Tone
  *Quiet Almanac*. Four-family typography (Fraunces / Geist / Newsreader
  / Berkeley Mono). Dark-first (Graphite), cream-opt-in (Cream). Wax-
  seal intents. All Material components banned. Every v1–v1.3 surface
  architected.
