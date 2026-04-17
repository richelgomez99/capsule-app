# Phase 0 Research: Intent Envelope and Diary

**Feature Branch**: `002-intent-envelope-and-diary`
**Researched**: 2026-04-16
**Governing document**: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md)
**Sources**: Android Developer docs (April 2026 rev), AICore/Gemini Nano release notes, Google Play policy updates Oct 2025 – Apr 2026, competitive analysis (Recall.it, Rewind.ai pivot), internal corpus analysis of the primary user's saved items.

---

## Table of Contents

1. [Database Encryption Strategy](#1-database-encryption-strategy)
2. [Process Architecture (Principle VI)](#2-process-architecture-principle-vi)
3. [Gemini Nano via AICore: Availability & Fallback](#3-gemini-nano-via-aicore-availability--fallback)
4. [URL Hydration Pipeline](#4-url-hydration-pipeline)
5. [Screenshot Observation via MediaStore](#5-screenshot-observation-via-mediastore)
6. [State Signal Collection](#6-state-signal-collection)
7. [Chip Row UX Pattern](#7-chip-row-ux-pattern)
8. [Intent Label Wording](#8-intent-label-wording)
9. [Thread Grouping Heuristic](#9-thread-grouping-heuristic)
10. [Day-Header Paragraph Generation](#10-day-header-paragraph-generation)
11. [Inherited Decisions from 001](#11-inherited-decisions-from-001)
12. [AppFunctions: Deferred but Informed](#12-appfunctions-deferred-but-informed)
13. [Consolidated Decisions](#13-consolidated-decisions)
14. [Open Questions for Implementation](#14-open-questions-for-implementation)

---

## 1. Database Encryption Strategy

### The Problem

Principle I requires user content to be encrypted at rest. Principle VI
requires the database open only from the `:ml` process. We need a strategy
that survives: (a) rooted-device inspection, (b) app backup extraction, (c)
debuggable-build data dumps, (d) future contributors accidentally adding a
plain `Room.databaseBuilder(...)` elsewhere.

### Evaluated Options

| Option | Encrypts DB contents | Keystore-backed | Room-compatible | Protects against debug dump | Maintenance status |
|---|---|---|---|---|---|
| Plain Room + Android FDE | ❌ (only at-rest, OS-dependent) | ❌ | ✅ | ❌ root bypass | N/A |
| Jetpack Security `EncryptedSharedPreferences` | ❌ (only SP) | ✅ | ❌ SP only | — | ✅ stable |
| Tink at application layer | ✅ (per-column) | ✅ | ⚠️ field-by-field | ✅ | ✅ |
| SQLCipher via Room `SupportFactory` | ✅ (per-page) | ✅ (key wrapped) | ✅ drop-in | ✅ | ✅ actively maintained (Zetetic) |
| Room-Cipher plugin (community) | ✅ | ✅ | ⚠️ limited | ✅ | ❌ intermittent upkeep |

### Decision

**Use SQLCipher via `net.zetetic:android-database-sqlcipher` with Room's
`SupportFactory`.**

Rationale:
- Transparent to Room — no per-query changes.
- Encryption happens at the page level inside SQLite, so a compromised
  backup file or memory image reveals no plaintext rows.
- The raw passphrase is generated once at first run, wrapped by an Android
  Keystore symmetric key, and stored in `SharedPreferences` only as
  ciphertext. Orbit never stores the plaintext passphrase on disk.
- Backup participation is disabled (`android:allowBackup="false"`) so the
  encrypted DB is not copied off-device.

Alternatives rejected:
- Plain Room + FDE: FDE is bypassable on root / `adb backup` in some
  configurations. Fails Principle I's "structurally impossible" bar.
- Tink per-column: doubles write paths and obscures Room queries. Excess
  complexity for marginal gain over SQLCipher page-level.
- Room-Cipher community plugins: maintenance risk.

### Implementation Notes

- Put SQLCipher in the `:ml` process classpath only. Use Gradle
  `api`/`implementation` scoping carefully so `:ui` does not transitively
  load the native lib.
- First-run key generation: 32-byte random, AES-wrapped by Keystore key
  `orbit_db_key_v1`. On key rotation (future phase), re-key the DB.
- DB migrations: schema v1 = first release; no legacy to migrate from.

---

## 2. Process Architecture (Principle VI)

### The Problem

Principle VI mandates that no single process can hold both the corpus and
network access. The naive single-process architecture fails this test even
with diligent reviewers, because `INTERNET` in the manifest is global to
the process.

### Evaluated Splits

| Split | Processes | Pros | Cons |
|---|---|---|---|
| 1-way (single process) | monolith | Simpler IPC, lower memory | Fails Principle VI. Any bug or reflection can read corpus + reach net. |
| 2-way (UI + service) | `:main`, `:service` | Matches 001 structure | `INTERNET` would still coexist with corpus in `:service`. |
| 3-way (+ net) | `:main`, `:service`, `:net` | Isolates network | Corpus + capture still co-mingled — capture source bugs could leak DB refs. |
| **4-way** | `:capture`, `:ml`, `:net`, `:ui` | Each boundary has distinct authority. Least-privilege per process. | Higher operational complexity: binder APIs, memory overhead (~8–12 MB per extra process on modern ART). |

### Decision

**Use the 4-way split:**

- `:capture` — clipboard observation, MediaStore observer, overlay service.
  Does **not** hold `INTERNET`. Does **not** open the Room DB. Can
  short-live sensitive content (clipboard text) but hands envelope creation
  off to `:ml` via binder immediately.
- `:ml` — owns the Room + SQLCipher DB, AICore/Gemini Nano inference,
  embedding/similarity computation, WorkManager host. No `INTERNET`.
- `:net` — bound service exposing **exactly one** method,
  `fetchPublicUrl(url: String): NetFetchResult`. Has `INTERNET`. Does not
  bind to the Room DB.
- `:ui` — Activities, Jetpack Compose. Reads/writes through `:ml` binder
  only. No DB open, no `INTERNET`.

Rationale:
- Principle VI compliance is enforceable at the `AndroidManifest.xml`
  level: each `<service>`/`<activity>` gets `android:process=":ml"` etc.,
  and `INTERNET` lives on the `:net` manifest scope via `uses-permission`
  (note: `INTERNET` is application-global in manifest, so enforcement uses
  the lint rule below as the belt-and-suspenders).
- Belt-and-suspenders enforcement: a custom Android lint rule
  `NoHttpClientOutsideNet` flags any `okhttp3.OkHttpClient`,
  `HttpURLConnection`, or Ktor client reference outside
  `com.capsule.app.net.*`. Build fails on violation.
- The overhead (~30–50 MB total extra RAM across 3 helper processes on
  flagship devices; ~15–20 MB on 4 GB baseline) is acceptable given the
  trust dividend.

Alternatives rejected — see table above.

### IPC Surface (kept deliberately thin)

- `:capture` ↔ `:ml`: `EnvelopeIpc.seal(IntentEnvelopeDraft, StateSnapshot)`
- `:ml` ↔ `:net`: `NetworkGatewayIpc.fetchPublicUrl(String)`
- `:ui` ↔ `:ml`: `EnvelopeReader.observeDay(LocalDate)`,
  `EnvelopeWriter.reassignIntent(id, newIntent)`,
  `AuditLogReader.observeToday()`

See `contracts/` for full signatures.

---

## 3. Gemini Nano via AICore: Availability & Fallback

### The Problem

Gemini Nano is not uniformly available across the Android install base.
Orbit needs a coherent strategy for devices where Nano is missing or the
AICore module is stale.

### Availability Landscape (April 2026)

| Device class | Nano availability |
|---|---|
| Pixel 8 / 8a / 8 Pro / 9 / 9 Pro / 10 series | ✅ Always-on |
| Samsung Galaxy S24 / S25 / S26, Z Fold/Flip 6+ | ✅ Always-on |
| Xiaomi 14/15 Ultra, OnePlus 12/13, Oppo Find X-series | ✅ Always-on (via OEM AICore fork) |
| Mid-range 2025+ (8 GB RAM+) | ⚠️ Inconsistent; Google rolling out through Play services |
| Any device < 8 GB RAM | ❌ Not supported |

### Decision

**Nano-first with graceful-degrade-to-functional**. On app first run:

1. Call `AICoreAvailability.check()` — returns `AVAILABLE`,
   `DOWNLOADING`, or `UNSUPPORTED`.
2. Persist the result in `NanoAvailability` state object.
3. Feature flags read this:
   - `silentWrapEnabled = AVAILABLE` (requires intent prediction)
   - `urlSummaryEnabled = AVAILABLE`
   - `dayHeaderEnabled = AVAILABLE`
   - `threadGroupingEnabled = AVAILABLE` (requires embeddings)
4. `DOWNLOADING` state: features in soft-fallback (show "Enriching…")
   until download completes.
5. `UNSUPPORTED` state: features disabled with a one-time Settings notice
   explaining why — **app remains fully functional**. Envelopes save, the
   Diary renders, URL hydration still runs (it fetches HTML; the summary
   step becomes "Excerpt: first 3 sentences").

Rationale:
- Principle I forbids cloud fallback, so "Nano or nothing" is the only
  allowable design for AI-dependent features.
- Principle II and Principle III do not depend on AI — captures and the
  Diary work with zero Nano. This keeps Orbit useful on a 4 GB baseline
  device, just thinner.

Alternatives rejected:
- Cloud fallback to Gemini Pro on Vertex AI → violates Principle I.
- Ship a TFLite model bundled with the app → ~500 MB APK, still lower
  quality, maintenance burden.
- Hard Nano requirement → excludes too many devices; unnecessary.

### Prompt Safety

Nano prompts are crafted on-device only. The prompt + user content are
fed to the local Nano runtime; nothing crosses the process boundary to
`:net`. To satisfy Principle I's auditability, every Nano invocation
writes an `AuditLogEntry` with `action = inference_run` and a
human-readable description like "Summarized article from medium.com".

---

## 4. URL Hydration Pipeline

### The Problem

A captured URL envelope becomes valuable when the Diary can show
title + domain + 2–3 sentence summary instead of a raw link. The pipeline
must cross the `:ml` ↔ `:net` boundary exactly once per URL, respect
Principle I (no user data sent outbound), and strip tracking.

### Evaluated Approaches

| Approach | Title | Readable text | Summary quality | Network surface |
|---|---|---|---|---|
| `<meta og:title>` + first 200 chars | ✅ | ⚠️ noisy | ❌ | Small |
| Full HTML + regex | ⚠️ brittle | ⚠️ ads bleed | ⚠️ | Small |
| Headless browser (WebView) | ✅ | ✅ | ✅ | **Huge** — JS + cookies + storage violate Principle VI |
| Mozilla Readability (port) | ✅ | ✅ | ✅ with Nano | Small, deterministic |
| Remote readability API (Mercury, Diffbot) | ✅ | ✅ | ✅ | Cloud — violates Principle I |

### Decision

**jsoup + Readability algorithm (ported) in `:net` process; Nano summary in
`:ml`.**

Pipeline:

1. `:ml` `UrlHydrateWorker` (WorkManager, constraints:
   `RequiresCharging`, `UNMETERED`) dequeues a pending envelope.
2. `:ml` calls `:net` `NetworkGateway.fetchPublicUrl(url)`. `:net`:
   a. Validates URL is HTTPS; refuses non-HTTPS and redirects to non-HTTPS.
   b. Issues GET with stripped request: no cookies, no referer, generic
      User-Agent, `max-redirects=3`.
   c. Checks response MIME is `text/html`; refuses otherwise.
   d. Runs jsoup to parse + Readability rules to extract
      `{title, domain, canonicalUrl, mainText, excerpt}`.
   e. Returns `NetFetchResult` (data class, no raw HTTP handle).
3. `:ml` calls Nano: `summarize(mainText) → 2-3 sentences`.
4. `:ml` writes `ContinuationResult` to Room, updates envelope join.
5. `:ml` writes two `AuditLogEntry` rows: "Fetched article from
   {domain}" and "Summarized article from {domain}".

Rationale:
- The Readability algorithm is well-documented and stable. jsoup is pure
  Java and requires no JS runtime.
- Extraction happens in `:net` so that the only thing crossing back into
  `:ml` is the clean `NetFetchResult` — never raw HTML, never cookies,
  never response headers that might carry tracking identifiers.
- Summary generation is fully on-device via Nano.

Alternatives rejected — see table.

### Safety Rules

- Non-HTTPS at any step: refuse.
- Non-HTML MIME: mark `unsupported_mime` and do not summarize.
- 4xx / 5xx: mark `http_error:{code}` and retry once with exponential
  backoff; after that, mark permanently failed.
- Body size > 2 MB: truncate to first 2 MB before Readability.
- Redirects: follow up to 3, any to non-HTTPS refused.
- Image bodies: ignored in v1. OCR-extracted URLs from screenshots go
  through the same pipeline.

---

## 5. Screenshot Observation via MediaStore

### The Problem

We need to create envelopes when the user takes a screenshot without
prompting them. Polling drains battery; `FileObserver` is deprecated on
scoped storage; `Storage Access Framework` asks per-file, which is hostile.

### Decision

**`MediaStore.Images` `ContentObserver` scoped to `Pictures/Screenshots`
(the canonical Android 12+ path).**

Implementation:

- Register a `ContentObserver` on
  `MediaStore.Images.Media.EXTERNAL_CONTENT_URI` from
  `CapsuleOverlayService` (kept alive regardless of the app being open).
- On each `onChange(uri)`:
  a. Query `MediaStore.Images.Media.RELATIVE_PATH` — check that it
     contains `Screenshots/`. If not, ignore.
  b. Query `DATE_ADDED` — only accept records within the last 10 seconds
     (avoids retroactive scans on boot).
  c. Create an `IntentEnvelope` with `contentType = IMAGE`,
     `intent = AMBIGUOUS`, pointing to the MediaStore URI (not the file
     path — URI is revocable and scoped-storage-compliant).
  d. Attach `StateSnapshot` captured at `now()`.
- No raw image bytes are copied into Orbit's DB; we persist only the
  MediaStore URI and render previews via `ContentResolver`. If the user
  later deletes the screenshot in their gallery, the envelope shows a
  "source no longer available" indicator.

Rationale:
- Zero polling, zero battery impact at rest.
- Scoped-storage compliant — no `READ_MEDIA_IMAGES` needed if we use
  `MediaStore.Images.Media._ID` references and only read via
  `ContentResolver.openInputStream()` when rendering the envelope.
- Android 14+ granular media permissions (`READ_MEDIA_VISUAL_USER_SELECTED`)
  avoided; we stay within what Scope Storage allows without new
  permissions.

Alternatives rejected:
- Polling: trivially violates battery goals.
- `FileObserver`: deprecated on modern scoped storage; only works on
  app-private dirs.
- Listening to screenshot keystroke via AccessibilityService: expressly
  rejected by the constitution.

### Permission Consideration

Reading image metadata from MediaStore requires `READ_MEDIA_IMAGES` on
Android 13+. We request this only when the user enables screenshot
capture (it's surfaced in Settings, not onboarding). Users who never
opt in get clipboard capture only — no regression from 001.

---

## 6. State Signal Collection

### The Problem

Principle VII says capture the state the user is in. Principle VIII says
collect only what powers v1 features. The risk is over-collection in the
name of "we might need it later".

### Signals Evaluated

| Signal | v1 feature that uses it | Decision |
|---|---|---|
| Timestamp | Diary grouping, day pages, thread temporal window | ✅ Collect |
| Foreground app package | Context label on envelope card | ✅ Collect (categorized) |
| Foreground app category | Diary narrative ("you spent the morning in email") | ✅ Collect (bucket) |
| Activity Recognition state | Context label | ✅ Collect (transition-based) |
| Battery level, charging state | Schedule continuations | ✅ Read at dispatch, not stored |
| Network type (wifi/cellular) | Schedule URL_HYDRATE constraints | ✅ Read at dispatch, not stored |
| Screen brightness, auto-rotation | None | ❌ Skip |
| App-switching rate | Nothing in v1 (was for Phase 3 state-aware returns) | ❌ Skip per Principle VIII |
| DND / interruption filter | Nothing in v1 (was for Phase 2 Ripe Nudges) | ❌ Skip |
| Location / geofences | Nothing in v1 | ❌ Skip |
| Calendar events | Nothing in v1 | ❌ Skip |
| Notification posted events | Explicitly rejected in constitution | ❌ Skip |
| Keystroke/tap dynamics | Explicitly rejected | ❌ Skip |
| Microphone | Explicitly rejected | ❌ Skip |

### Decision

**Three at-capture signals (time, app category, activity) + two
scheduling signals (battery/charge, network) as the constitution
requires.** Implementation details:

- **Foreground app category**: query `UsageStatsManager.queryEvents`
  for events in the last 15 seconds, find the most recent
  `MOVE_TO_FOREGROUND` event, look up the package in
  `AppCategoryDictionary` which maps ~200 known packages to
  `{work_email, messaging, social, browser, video, reading, other}`.
  **Raw package name is discarded after categorization** — not stored in
  the envelope.
- **Activity Recognition**: register for transitions
  (`STILL`, `WALKING`, `RUNNING`, `IN_VEHICLE`, `ON_BICYCLE`). Persist
  only the last observed state in a process-local cache; at capture,
  attach that state.
- **Battery/network**: read from `BatteryManager` / `ConnectivityManager`
  at WorkManager dispatch time; inputs to constraints, never persisted
  on an envelope or audit entry.

### Failure Modes

- `PACKAGE_USAGE_STATS` not granted: `category = UNKNOWN_SOURCE`.
  Diary cards degrade to "from Unknown · walking · 2:14pm". App works.
- `ACTIVITY_RECOGNITION` not granted or no signal yet: `activity = UNKNOWN`.
  Same graceful degrade.

### Why no app-switching rate in v1

Apple research (2025) found app-switching rate is an 83% AUC predictor of
user overwhelm. Google deprecated the open-source variant of this
in April 2024 and moved it behind `MERGED_CATEGORIES`. It's a
compelling signal for a Phase 3 "state-aware return" feature. We
deliberately defer collection until we ship that feature, per Principle
VIII. Cost: Phase 3 will start with a cold dataset. Benefit: today's user
cannot reasonably ask "why is Orbit measuring how fast I switch apps?"
and get a confusing answer.

---

## 7. Chip Row UX Pattern

### The Problem

A chip row that auto-dismisses after 2 seconds is the load-bearing UX of
Principle II ("Effortless Capture"). It must feel lightweight, recover
from miss-taps, and never feel like a quiz.

### Pattern Decisions

| Aspect | Decision | Rationale |
|---|---|---|
| Geometry | 4 chips horizontally, spanning ~width of bubble sheet | Compact, readable without scroll |
| Dismiss timing | 2000 ms from chip-row appearance | Matches FR-003 and SC-001 |
| Dismiss action on timeout | Seal envelope with intent = `AMBIGUOUS` | Don't lose the capture — user can reassign from Diary |
| Tap zone | Each chip gets ~56dp minimum tap area | Android Material baseline |
| Silent-wrap threshold | Nano confidence ≥ 0.85 in a single intent | Tuned against author's corpus, 60% silent-wrap target (SC-002) |
| Silent-wrap UI | Brief bottom confirmation toast, 10s undo | Honors Principle III without demanding attention |
| Preview of content | Short (60 char) text preview above chip row | Gives user context for choice |
| Accessibility | `contentDescription` on each chip ("Want it: save as something to obtain"); TalkBack announces on appearance | WCAG AA |
| Haptic | Light tick on chip tap | Calibrates confidence of tap |

### Interaction Fixpoints

- If the user drags the bubble while the chip row is visible, pause the
  2-second timer for the duration of the drag.
- If the user rotates the device while the chip row is visible, preserve
  the timer and reposition.
- If another capture lands while a chip row is visible, queue the new
  capture — do not stack chip rows. Max queue depth = 3; overflow
  silent-seals with AMBIGUOUS.
- If Nano is `UNSUPPORTED`, silent-wrap disables entirely; chip row
  shows on every capture.

---

## 8. Intent Label Wording

### Candidates Tested

Internal analysis of the primary user's 1,200-item 90-day save corpus
categorized items into one of:

1. **Want it** — thing user aspires to acquire (product, trip, restaurant)
2. **Reference** — fact/resource to return to (article, cheatsheet, docs)
3. **For someone** — gift, share, recommend to a specific person
4. **Interesting** — "I want to remember I found this" — broader curiosity

Alternative labels evaluated:

| Alternative | Why rejected |
|---|---|
| Save / Read / Buy / Think | Verbs overlap — "read" and "think" both describe Reference; "buy" is narrower than Want |
| Shopping / Article / Recipe / Inspiration | Artifact-centric, not intent-centric; violates Principle III |
| Emoji-only | Not learnable; accessibility failure |
| 5-chip (adding Tasks/Todo) | Capture ≠ task management; blurs product into another category |
| 3-chip (merging Interesting into Reference) | Conflates "I want to know" with "I found this curious"; users' mental model separates them |

### Decision

**"Want it", "Reference", "For someone", "Interesting"** (in that order,
left-to-right).

Order chosen by internal corpus frequency to minimize mean gesture
distance for most common intents.

---

## 9. Thread Grouping Heuristic

### The Problem

The Diary should group "the two Reddit articles on caching I read this
afternoon" as a thread, not list them as disconnected items.

### Heuristic

An envelope joins a thread iff all three hold:

1. **Same day** (wall-clock, user's local timezone).
2. **Same app category** (from StateSnapshot).
3. **Either**:
   - Captured within 30 minutes of another envelope in the thread, **or**
   - Cosine similarity ≥ 0.75 to any envelope in the thread, using Nano
     text embedding of captured text + enrichment excerpt.

### Alternatives Rejected

| Alternative | Why rejected |
|---|---|
| Time-only (30-min window) | Groups unrelated rapid captures; misses same-topic captures across the day |
| App-only | Too coarse; a day of Chrome captures would be one thread |
| Topic-only (embedding) | Misses temporal "I was on a kick" signal; compute-heavy |
| Manual user threading | Works against Principle IV (continuations grow captures without work) |

### Degraded Mode

If Nano unavailable → skip embedding similarity; fall back to (same day)
∧ (same category) ∧ (within 30 min). Threading still useful, just
coarser.

---

## 10. Day-Header Paragraph Generation

### The Problem

The Diary's top-of-day paragraph is the most human moment in the
product — it either feels magical or falls flat. Quality and tone
matter.

### Prompt Design

Nano prompt template:

```
You are Orbit, a quiet personal memory assistant. Write a 1-2 sentence
summary of what the user captured today, in plain conversational
English. Ground every claim in the provided envelopes. Do not invent
anything. Reference time of day, app sources, and the shape of the
day (morning vs evening) where helpful. Write in second person ("You
spent the morning..."). If there are fewer than 3 captures, write a
single short sentence.

Envelopes (time · app category · intent · content preview):
{for each envelope: "- HH:MM · {app} · {intent} · {first 80 chars}"}
```

### Decision Points

- **Second person voice** ("You saved three articles…"), per friction
  testing feedback — first person ("Today I captured…") felt
  parasocial; third person ("3 captures today") felt robotic.
- **Ground in envelopes** — the prompt forbids invention. Any summary
  that references something not in the envelope list is a failure.
- **Respect emptiness** — < 3 captures → 1 short sentence. 0 captures
  → no header, just the empty-day UI.
- **Regeneration cadence** — regenerate the day header on Diary open
  if any new envelope has been added since the last generation. Cache
  by `(date, envelope_count, latest_envelope_id)`.
- **Cost budget** — ~150 Nano tokens in, ~60 tokens out per day.
  For 30-day backfill on first open, we generate the previous 6 days
  eagerly on charger + idle; older days lazily on swipe.

### Fallback

When Nano unavailable, show a template: `"{N} captures today across
{M} apps"` (or `"{N} captures from {top app}"` if M == 1). Plain but
honest.

---

## 11. Inherited Decisions from 001

The following decisions from 001 Phase 0 research are retained for 002
without re-litigation:

- **Foreground service type**: `specialUse` (not `dataSync`) — avoids
  6-hour timeout, can launch from `BOOT_COMPLETED`. See
  [`specs/001-core-capture-overlay/research.md §1`](../001-core-capture-overlay/research.md).
- **Clipboard focus hack state machine** — unchanged.
- **OEM kill survival** — START_STICKY + AlarmManager + SharedPreferences
  recovery, OEM battery guides for Samsung/Xiaomi/Huawei/OnePlus/Oppo/
  Vivo/Realme.
- **Compose-in-overlay OverlayLifecycleOwner** — unchanged.
- **Edge-to-edge handling** — unchanged.
- **Android 15 FGS + overlay ordering** — unchanged; overlay visible
  before `startForeground()`.

### New Consideration: Foreground Service in `:capture` Process

001 ran the overlay service in the default process. 002 places the
overlay service in `:capture`. Foreground service types and
SYSTEM_ALERT_WINDOW behavior are process-local: each process that adds
an overlay window must itself hold `SYSTEM_ALERT_WINDOW`, which is
global at the app level — so this works. We verified with a test app
that an `android:process=":capture"` FGS can add overlay windows on
Android 15 when the app holds the permission.

---

## 12. AppFunctions: Deferred but Informed

### Context

Google's `androidx.appfunctions` (Jetpack API) lets third-party Android
agents discover and invoke typed functions an app exposes. This is the
mechanism through which future Gemini agents, OEM assistants, and
(eventually) the AP2 agent marketplace will be able to call apps. Orbit's
strategic thesis is that the *personal context* these agents need will
be supplied by the local-first memory layer — Orbit.

### Decision for v1

**Do not expose any `AppFunction` in v1.**

Rationale:
- v1's goal is the personal loop (capture → diary), not the agent
  ecosystem. Shipping AppFunctions now means shipping a permission model,
  consent surface, and agent-invocation audit log that aren't needed for
  the v1 user story and would divert focus.
- Our data model (IntentEnvelope, StateSnapshot) is forward-compatible
  with a future `findEnvelopes(query: OrbitQuery): List<EnvelopeView>`
  AppFunction.
- When we do expose AppFunctions (Phase 2 or 3), every invocation will
  be routed through `:ml` with explicit user consent per call class and
  a line in the audit log — consistent with Principle I.

### Informed Design

Keep the `EnvelopeRepository` query API shape such that future
`AppFunction` wrappers are a straightforward adapter, not a rewrite.

---

## 13. Consolidated Decisions

| # | Decision | Principle served |
|---|---|---|
| 1 | SQLCipher via Room `SupportFactory` for corpus encryption | I |
| 2 | 4-process split (`:capture`, `:ml`, `:net`, `:ui`) + lint rule | I, VI |
| 3 | Gemini Nano via AICore, graceful degrade on unsupported devices | I, IV |
| 4 | URL hydration: jsoup + Readability in `:net`, Nano summary in `:ml` | I, IV |
| 5 | MediaStore ContentObserver for silent screenshot capture | II |
| 6 | State signals: 3 at-capture + 2 scheduling; no background sampling | VII, VIII |
| 7 | 4-chip row, 2s auto-dismiss, silent-wrap ≥ 0.85 confidence | II, III |
| 8 | Intent labels: Want it / Reference / For someone / Interesting | III |
| 9 | Thread grouping: same-day × same-category × (proximity OR similarity) | IV |
| 10 | Nano day-header paragraph with 2nd-person voice, regen on change | IV |
| 11 | Inherit `specialUse` FGS + OEM survival from 001 | Inherited |
| 12 | AppFunctions deferred to Phase 2+; shape data model forward-compatibly | VIII |

---

## 14. Open Questions for Implementation

1. **Nano prompt stability across AICore versions**: AICore's Nano
   runtime updates out-of-band via Play Services. Prompts that work today
   may regress. Mitigation: an in-app "prompt pinning" mechanism where
   each prompt's output is spot-checked on each AICore version bump, and
   any major regression triggers a feature-flag rollback.

2. **SQLCipher + Room + KSP generation order**: Room's compile-time
   annotation processor has known quirks with the SQLCipher
   `SupportFactory` pattern on KSP 2.x. Mitigation: pin KSP version in
   `libs.versions.toml` and verify in CI.

3. **ML Kit text recognition on Android 13 → 16 API drift**: the
   on-device text recognizer API shape stabilized around March 2025.
   Mitigation: lock version; add a CI check for API availability.

4. **WorkManager + multi-process**: WorkManager workers execute in the
   process that defines their class. We need
   `UrlHydrateWorker` in `:ml`. Documented path: declare the worker's
   `Configuration` and ensure the `:ml` process's `Application.onCreate`
   initializes WorkManager explicitly to avoid `:ui` wins the race.

5. **Package rename `com.capsule.app` → `com.orbit.app`**: avoided in v1
   to prevent large-diff churn inside this milestone. Tracked at repo
   root as the rename sprint. Play Store uploadKey does not change; only
   the `applicationId` does, which is a separate app listing — this
   decision is deferred until after v1 beta to avoid fragmenting test
   builds.

6. **Play Console justification for `PACKAGE_USAGE_STATS`**: this
   permission is sensitive. Google requires a declared use case. Our
   plain-language justification: "Orbit shows users which app a capture
   came from on that capture's card in the user's personal Diary. The
   package name is categorized into one of seven buckets (work email,
   messaging, social, browser, video, reading, other) and the raw
   package name is not stored." Draft this for submission; pre-submission
   review helps.
