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

**Intent accents.** The same four regardless of mode — they're ink colors,
not UI colors, so they have to read on cream and on graphite. These are
used on the wax-seal glyph, the chip row, the "saved as" italic, and
nowhere else. Never on buttons, never on backgrounds.

```
--intent-want       #D9B44A    amber      "Want it"         ▲
--intent-reference  #6B8A7A    slate green "Reference"      ◆
--intent-for        #C87361    terracotta "For someone"     ●
--intent-interest   #7A84B8    dusk blue  "Interesting"     ○
--intent-uncertain  var(--ink-dim)        "Uncertain"       ·   (was "AMBIGUOUS")
```

> **Naming note.** User-facing labels are deliberately softened. `AMBIGUOUS`
> in the data layer is always rendered as **"Uncertain"** in the UI —
> humble, human, honest. Constitution-friendly.

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

---

## 8. What's Banned in Orbit (addendum to Anthropic's frontend-design
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

---

## 9. Open design questions (tracked here, resolved in Figma explorations
before corresponding spec's UI tasks begin)

- **Fonts licensing.** Berkeley Mono is commercial ($). Alternatives:
  JetBrains Mono (free), iA Writer Mono (paid), IBM Plex Mono (free).
  Need to pick. *Recommendation: ship with JetBrains Mono free, keep
  Berkeley Mono as an aspirational upgrade.*
- **Cream mode intent-ink contrast.** `--intent-for` terracotta
  `#C87361` on `--canvas` cream `#F5F0E6` is 3.2:1 — fails AA for small
  text. Need to darken to `#A85947` in cream mode specifically, or use
  ink variants per theme.
- **Grain on low-end devices.** 4 % grain over the full canvas is ~3 ms
  per frame on Pixel 5. On sub-Pixel 4a devices, drop to baked PNG once
  per session rather than Shader. Needs perf test.
- **First-run bubble position.** Should the bubble appear on right edge
  (right-hand users) or detect system-wide left-handed accessibility
  setting? Default right-edge, but plumb for override.
- **Onboarding Page 3 intent descriptions.** The wording proposed in
  § 4.1 is a first pass. Likely needs a writing round before US7 build.
- **Ask Orbit gesture.** Long-press bubble → Ask mode. Conflicts with
  drag? Needs prototype. Alternative: double-tap bubble.

These are open questions. Close them in a named PR (e.g. *"Design
question: fonts licensing — Berkeley Mono vs JetBrains Mono"*) and
update this section when resolved.

---

## 10. Changelog

- **2026-04-17 · v1.0**: Initial ratified visual architecture. Tone
  *Quiet Almanac*. Four-family typography (Fraunces / Geist / Newsreader
  / Berkeley Mono). Dark-first (Graphite), cream-opt-in (Cream). Wax-
  seal intents. All Material components banned. Every v1–v1.3 surface
  architected.
