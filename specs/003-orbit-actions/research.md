# Phase 0 Research: Orbit Actions (v1.1)

**Feature Branch**: `003-orbit-actions`
**Researched**: 2026-04-26
**Governing document**: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md)
**Sources**: Android Developer docs (April 2026 rev), AppFunctions reference (Android 15 / Android 16 dev preview), CalendarContract reference, AICore/Gemini Nano release notes, internal corpus analysis from 002 dogfooding (Apr 16 – Apr 26 2026), spec 002/008 amendments dated 2026-04-26.

---

## Table of Contents

1. [AppFunctions API Posture (Android 15+ vs. 13/14)](#1-appfunctions-api-posture)
2. [Action Extraction Pipeline](#2-action-extraction-pipeline)
3. [Date / Time Parsing Strategy](#3-date--time-parsing-strategy)
4. [Calendar Intent.ACTION_INSERT Surface](#4-calendar-intentaction_insert-surface)
5. [To-Do Targets: Google Tasks vs. Generic Share](#5-to-do-targets)
6. [Weekly Digest: Input Window and Composition](#6-weekly-digest-input-window-and-composition)
7. [Confirmation UX and Undo Window](#7-confirmation-ux-and-undo-window)
8. [Per-Action Sensitivity Scoping](#8-per-action-sensitivity-scoping)
9. [Skill Usage Stats: What v1.2 Agent Will Need](#9-skill-usage-stats)
10. [Diary Layout Coexistence (Cluster Card vs. Action Chip)](#10-diary-layout-coexistence)
11. [Consolidated Decisions](#11-consolidated-decisions)
12. [Open Questions for Implementation](#12-open-questions-for-implementation)

---

## 1. AppFunctions API Posture

### The Problem

Android AppFunctions (announced I/O 2024, stabilised in Android 15)
is the canonical surface for the v1.2 agent (spec 008) to invoke
tools registered by other apps. v1.1 needs to ship the *registration*
side and at least one consumer (Orbit's own calendar/to-do skills)
without coupling to whether the user's device has Android 15+.

### Availability (April 2026)

| API level | AppFunctions support |
|---|---|
| 35 (Android 15) | ✅ Canonical: `androidx.appfunctions:appfunctions-runtime` 1.0 stable, `@AppFunction` annotation processor, system AppFunctionService binder. |
| 34 (Android 14) | ⚠️ Backport library (`appfunctions-runtime` 1.0 `compat` artifact) provides the same annotations + a local in-process registry. No system AppFunctionService — calls dispatch in-app only. |
| 33 (Android 13) | ⚠️ Same compat path as 34. |

Orbit minSdk is 33 (per 002), so we cannot require canonical AppFunctions.

### Decision

**Ship the AppFunctions schema-and-registry surface on every API
level via the compat library; use the system AppFunctionService on
35+ when available; fall back to a local in-process registry on
33/34.**

The `AppFunctionRegistry` (Phase 1) abstracts this: callers declare a
schema with `@AppFunction`, the registry stores the schema row in
the local `appfunction_skill` table regardless of API level. On
Android 15+, the registry *additionally* registers the schema with
the system AppFunctionService at first launch so other apps (and
v1.2 agent's planner across processes) can discover it. On 13/14,
discovery is local-only — fine for v1.1, where Orbit registers and
consumes its own functions exclusively.

Rationale:

- Principle V (Graceful Degradation): the v1.1 user gets the *exact
  same* feature set on 13/14 as on 15+. Cross-app discoverability
  is a 15+ bonus, not a v1.1 requirement (Orbit's only consumer is
  itself in v1.1).
- Forward-compat with v1.2 agent (spec 008): the agent reads from
  the same `appfunction_skill` table. When the agent ships, it
  layers cross-app discovery on top of Orbit-internal discovery
  with no schema change.
- Forward-compat with spec 006: the `appfunction_skill` schema
  matches the `skills` schema in spec 006's contract verbatim, so
  cloud sync (when 006 ships in v1.1+) is a row-level mirror.

Alternatives rejected:

- *Bump minSdk to 35*: cuts ~70% of Orbit's intended audience based
  on Q1 2026 Android distribution. Unacceptable.
- *Ship our own ad-hoc skill registry with no AppFunctions API
  pretence*: the schema would diverge from the canonical surface
  and v1.2 agent would have to migrate the table. Pure waste.

### Implementation Notes

- The `AppFunctionRegistry` lives in `:ml` (DB owner). It exposes
  `register(schema: AppFunctionSchema)`, `lookup(functionId)`, and
  `recordInvocation(functionId, outcome, latencyMs)` to other
  processes via the existing `:ml` binder service.
- v1.1 registers exactly three Orbit-owned skills:
  `com.capsule.app.action.calendar_insert`,
  `com.capsule.app.action.todo_add`,
  `com.capsule.app.action.share`.
- AppFunction execution always re-validates the schema-typed
  argument bag at the call site to defend against future
  divergence between registered schema and execution code.

---

## 2. Action Extraction Pipeline

### The Problem

When an envelope is sealed, we need to ask "is there a confirmable
action latent in this content?" without (a) running expensive Nano
inference on every envelope and (b) producing low-quality
proposals that train the user to ignore the chip.

### Decision

**Two-phase extraction with a cheap pre-filter.**

Phase A — fast pre-filter (runs in `:ml`, no Nano):

1. Regex / keyword match against a small dictionary of action
   indicators: flight booking codes (`/^[A-Z]{2}\d{2,4}$/`), date
   tokens (`Mon|Tue|Wed|...`), imperative-list patterns
   (lines starting with `- ` or `* ` or `1. `), `RSVP`, `confirm`,
   currency symbols followed by digits.
2. If zero matches, no proposal — cost is ~µs.

Phase B — Nano extraction (runs as `ACTION_EXTRACT` continuation
on charger + wifi):

1. `LlmProvider.extractActions(text, stateSnapshot, contentType)`
   returns 0..N `ActionCandidate`s with confidence scores.
2. The structured prompt forces Nano to return JSON conforming to
   the registered AppFunction schemas — invalid JSON or unknown
   `function_id` causes the proposal to be discarded.
3. Confidence < 0.55 → discarded. ≥ 0.55 → persisted as
   `action_proposal` row with `state = 'PROPOSED'`.

Rationale:

- Phase A keeps the seal path's p95 within 002's existing budget.
  Action extraction never blocks seal.
- Phase B reuses the existing 002 continuation pipeline (charger +
  wifi) — Principle IV.
- The 0.55 confidence floor was selected from Apr 16–26 dogfood
  data: 0.55 yields ~0.2 proposals/envelope (right) vs. 0.40 which
  yielded ~0.7 (too noisy) vs. 0.70 which missed clear flight
  confirmations.

Alternatives rejected:

- *Run Nano on every envelope synchronously*: blows the seal-path
  budget; violates Principle II (effortless capture).
- *Phase A only, no Nano*: regex catches flight codes but misses
  every fuzzy date phrase ("dinner with mom Friday") — too
  brittle.

---

## 3. Date / Time Parsing Strategy

### The Problem

Action proposals for calendar events need a concrete `startMillis` /
`endMillis` (CalendarContract requires it). Nano often returns
free-form like "next Tuesday at 3pm" — we need to resolve it
against the user's timezone and the envelope's `createdAt`.

### Evaluated Options

| Approach | Accuracy | Locale-aware | Bundle size | Maintenance |
|---|---|---|---|---|
| Pure Nano (return ISO 8601) | ~75% on dogfood corpus | Yes | 0 (already shipping) | Google |
| Pure regex/heuristic | ~50% | English only | 0 | Us |
| `java.time` + custom relative-date parser | ~90% on relative dates (Tue, Fri, tomorrow) | Yes (`Locale`) | 0 (JDK) | Us |
| Bundled NLP library (e.g., `chrono.kt`) | ~92% | Limited | ~400 KB | External |

### Decision

**Nano-first with a structured `DateTimeParser` fallback.** The
Nano prompt requests ISO 8601; if the returned value parses
cleanly, use it. If parse fails OR Nano returns a relative phrase,
hand the original string to `DateTimeParser` (java.time-based,
locale-aware, anchored to envelope `createdAt` and device tz). If
both fail, the proposal is discarded.

Rationale:

- Principle I (no cloud) is preserved.
- Maintaining ~400 KB of NLP library for marginal accuracy gain
  fails Principle VIII.
- Java's `java.time.format.DateTimeFormatter` plus a small set of
  relative-token rules covers the common cases (Mon–Sun,
  tomorrow/today, "next X", "in N days") observed in the dogfood
  corpus.

### Implementation Notes

- `DateTimeParser` is pure and unit-tested with a fixed clock.
- The parser never silently shifts dates across DST boundaries —
  it asserts `ZoneId == ZoneId.systemDefault()` at parse time and
  records the zone in the proposal row.
- Times without an hour component default to 09:00 local; explicit
  rule ("Tuesday" → "Tuesday 09:00 local"). This is conservative
  and the user can edit before confirming.

---

## 4. Calendar Intent.ACTION_INSERT Surface

### Decision

**Use `Intent.ACTION_INSERT` to `CalendarContract.Events.CONTENT_URI`
with FLAG_ACTIVITY_NEW_TASK.** Required extras:
`Events.TITLE`, `Events.EVENT_LOCATION`, `Events.DESCRIPTION`,
`CalendarContract.EXTRA_EVENT_BEGIN_TIME`,
`CalendarContract.EXTRA_EVENT_END_TIME`. End time defaults to
+1 hour if Nano didn't extract one.

Why not the Calendar Provider write API directly:

- Requires `WRITE_CALENDAR` permission. Adding a 5th onboarding
  permission for one feature fails Principle VIII.
- Bypasses the system Calendar app's confirmation animation,
  silently writing — which the user cannot easily undo. Violates
  FR-003-003 ("never write without user tap").
- The user's *active* calendar account isn't always the default
  one; the system Calendar app handles account selection
  correctly. A direct write would silently target the default.

The intent surface delegates account selection, conflict detection,
and final confirmation to the system Calendar app — which is
exactly the right trust boundary for "we propose, the user
confirms."

### Reversibility

After the system Calendar app finishes inserting, our
`ActionExecutionWorker` cannot directly read back the inserted
event id without `READ_CALENDAR`, which we also do not request. So
"undo within 24h" for calendar events means: we keep the
`action_execution` row tagged `outcome = 'DISPATCHED'` and offer a
24h-window UX banner that opens the system Calendar app to the
proposed time range — the user deletes there. This is honest about
what we can and cannot do.

---

## 5. To-Do Targets

### Decision

**v1.1 ships two paths**:

1. **Local Orbit to-dos**: a built-in to-do list backed by a
   dedicated AppFunction (`com.capsule.app.action.todo_add`). The
   to-do is a small entity in the same DB; appears as a regular
   envelope of kind `REGULAR` with intent `WANT_IT` and a
   structured `todo_meta` JSON column. Editing happens inline in
   the Diary.
2. **Generic share to external task app**: `Intent.ACTION_SEND`
   with `EXTRA_TEXT` containing the formatted to-do. The user
   picks the target app via the system share sheet on first use;
   selection is remembered in settings.

We deliberately do **not** first-class Google Tasks / TickTick /
Todoist via their proprietary APIs. Reasons:

- Each requires OAuth — adds account linking, fails the spec's
  non-goal "No account linking beyond standard Android intents."
- Each is a maintenance burden (token refresh, API changes).
- The share sheet route reaches every task app on the user's
  device, including ones we'd never first-class.

The trade-off: the share sheet is one extra tap vs. silent API
write. We accept this — Principle V (under-deliver on noise) and
Principle VIII (collect only what you use, including OAuth tokens
we never keep).

---

## 6. Weekly Digest: Input Window and Composition

### Decision

**Input window**: the 7 days ending on the Saturday before the
target Sunday. So the digest surfaced on Sunday morning
*2026-05-03* covers Mon 2026-04-27 through Sat 2026-05-02
inclusive. Sunday-of-surfacing is not included — it would conflict
with the same-day day-header generation.

**Composition**: `DigestComposer` calls
`LlmProvider.summarize(prompt)` with a structured prompt that
includes:

- One line per day: day-header text from 002 (already generated).
- Top 3 envelopes per day by Nano-scored salience (scoring is the
  same `summary` call's auxiliary output — no new Nano call).
- Cross-day patterns: repeated app categories, repeated intents,
  repeated topics (where 002's `ThreadGrouper` yielded multi-day
  threads).

Output is rendered into a single `DIGEST` envelope of ~150–250
words, attached to Sunday's diary page as the topmost entry.

**Idempotency**: the worker's WorkManager unique work id is
`weekly-digest-${isoWeekYear}-${weekOfYear}`. Re-runs (e.g., user
charger-plug at 06:00 then again at 08:00 same Sunday) no-op if a
DIGEST envelope already exists for that week. This is enforced at
the DB layer with a unique constraint on
`(kind = 'DIGEST', day_local)`.

**Schedule**: `PeriodicWorkRequest` with 7-day interval, anchored
to next Sunday 06:00 device-local. Constraints: charger + wifi.
Time is user-configurable (Settings → Digest schedule, default
06:00). If charger+wifi unavailable at the scheduled time,
WorkManager retries opportunistically up to 24h; after 24h,
the digest is skipped and an audit row notes the skip — the user
gets the next week's digest as normal.

### Failure modes

- < 3 envelopes in the input window → no digest envelope created;
  audit row `DIGEST_SKIPPED reason=too_sparse`.
- Nano unavailable (`AVAILABILITY = UNSUPPORTED`) → structured
  fallback digest: a short table of "X captures across Y app
  categories this week"; audit row `DIGEST_GENERATED model=fallback`.
- All envelopes in input window are archived/deleted → no digest;
  audit row `DIGEST_SKIPPED reason=empty_window`.

---

## 7. Confirmation UX and Undo Window

### Decision

**Two-step confirmation in the Diary.**

Step 1: chip with summary (`+ Add to calendar — Flight UA437,
May 22 14:15`). Tap = open preview card.

Step 2: preview card with editable fields (title, start/end,
location, notes). Single "Confirm" button at the bottom. The
preview is the last opportunity to bail — back/swipe dismisses
without writing.

After confirmation, the system Calendar / share sheet opens. While
that external app is foreground, Orbit shows a 5-second toast in
the Diary: "Added to Calendar — undo". Tap "undo" within 5s
*and* before the user has confirmed in the system app to suppress
the proposal (i.e., flip `action_execution.outcome` to
`USER_CANCELLED`). After 5s, no in-app undo — the user manages
the inserted item in the target app.

This is the same undo window pattern as 002's seal-undo (10s),
shortened to 5s because (a) the intent is already in flight to a
system app and (b) longer windows train users to expect undo
guarantees we can't honour for external writes.

---

## 8. Per-Action Sensitivity Scoping

### Decision

Each registered AppFunction declares a `sensitivity_scope` enum:

```kotlin
enum class SensitivityScope {
    PUBLIC,             // calendar_insert: title + time only, public-ish
    PERSONAL,           // todo_add: free text, may be private
    SHARE_DELEGATED     // share: user picks target app, scope inherited
}
```

The 002 `SensitivityScrubber` (already in the seal path) marks
content with sensitivity flags. `ActionExtractor` consults those
flags and:

- `SensitivityScope.PUBLIC` action on `financial`-flagged content →
  rejected at proposal time; audit row notes the suppression.
- `SensitivityScope.PERSONAL` action on `credentials`-flagged
  content → rejected.
- All other combinations → permitted.

This is the v1.1 surface for what spec 011/008 will generalise into
the consent filter (Principle XI).

---

## 9. Skill Usage Stats

### Decision

The `skill_usage` table records every invocation:

```kotlin
@Entity(tableName = "skill_usage")
data class SkillUsageEntity(
    @PrimaryKey val id: String,                  // UUIDv4
    val skillId: String,                         // FK → appfunction_skill.functionId
    val episodeId: String?,                      // FK → episodes table (spec 006 when present)
    val proposalId: String,                      // FK → action_proposal.id
    val executionId: String,                     // FK → action_execution.id
    val outcome: String,                         // SUCCESS | FAILED | USER_CANCELLED
    val latencyMs: Long,
    val invokedAt: Long
)
```

Aggregations the v1.2 agent's planner reads (also exposed in v1.1
Settings → Actions for the user's own benefit):

- `successRate(skillId, sinceDays)` = SUCCESS / total over window.
- `medianLatency(skillId, sinceDays)`.
- `userCancelRate(skillId, sinceDays)` — high cancel rate signals
  the proposals are wrong and should be down-weighted.

Cancel-rate is the metric we did not have in 002 dogfooding;
adding it from day one means by the time v1.2 agent ships, every
user has weeks of cancel-rate signal.

---

## 10. Diary Layout Coexistence

### The Open Question

Spec 003 calls this out (Open Questions): when a research-session
cluster contains an envelope with a calendar action chip, the same
diary day shows both the cluster card AND the per-envelope chip.

### Decision

**Two-layer stacking, top-down:**

1. **DIGEST envelope** (Sunday only) — always topmost when present.
2. **Cluster suggestion card** (002 amendment) — pinned below the
   DIGEST and above the day's chronological feed.
3. **Inline action chips** — directly under their parent envelope
   in the chronological feed.

Visual treatment delegated to spec 010 (visual polish pass) and
design.md §X (TBD section to add). The information architecture
is settled: cluster cards are *day-scoped recommendations*, action
chips are *envelope-scoped affordances*. They never overlap in
function.

When a single envelope is both a cluster member AND has an action
chip, the chip renders inline on the envelope card; the cluster
card at the top of the day only summarises the cluster. Tapping
the chip executes the action. Tapping the cluster card opens the
cluster view, which renders the same envelope cards (with the
chip again visible inline). No deduplication is needed — the
chip is the *same row* in both surfaces.

---

## 11. Consolidated Decisions

| # | Decision |
|---|---|
| D1 | AppFunctions: ship compat library on all minSdk targets; system service on 35+ as bonus. |
| D2 | Action extraction: 2-phase (regex pre-filter → Nano in continuation). 0.55 confidence floor. |
| D3 | Date parsing: Nano-first with `DateTimeParser` (java.time) structured fallback. |
| D4 | Calendar: `Intent.ACTION_INSERT`, never `WRITE_CALENDAR` permission. |
| D5 | To-dos: local Orbit list + generic share sheet. No first-classed external task apps. |
| D6 | Weekly digest: `PeriodicWorkRequest` Sunday 06:00 local (configurable), 7-day window ending Saturday. |
| D7 | Confirmation: 2-step (chip → preview card → Confirm). 5s in-app undo for external dispatch. |
| D8 | Sensitivity: per-action `SensitivityScope` enum, gated against 002 scrubber flags. |
| D9 | Skill usage: success rate + median latency + cancel rate, recorded from v1.1 day one. |
| D10 | Diary layout: DIGEST → cluster card → chronological feed (with inline chips). |

---

## 12. Open Questions for Implementation

These are flagged in the spec or surfaced during research and
remain open for `/speckit.tasks` or beyond:

- **Q1 — DIGEST localisation**: v1.1 ships English-only digests
  per the 002 day-header pattern. Non-English locales fall back
  to structured count rendering. Defer translation to spec 010 /
  v1.2.
- **Q2 — Spec 012 (Resolution Semantics) coupling**: Spec 003
  Orbit Actions are the canonical resolution-trigger source per
  spec 012 FR-012-005. The `action_execution.outcome = SUCCESS`
  row is what spec 012 will consume to advance an envelope to
  `Resolved`. v1.1 ships the data and the audit hooks; spec 012
  layers state-machine semantics on top without modifying the
  003 schema. We do NOT add a `resolution_state` column in v1.1
  — that is spec 012's job.
- **Q3 — User-defined custom action templates** (spec 003 Open
  Question 3): explicitly deferred to v1.3 / spec 008. No
  scaffolding for user-defined regex → intent maps in v1.1.
- **Q4 — Spec 008 v1 cluster-suggestion overlap**: cluster
  *Save as Structured List* and Orbit Actions *to-do extraction*
  may both fire on the same day. Per spec 003 Open Question 4
  and spec 008 amendment, they ship as non-overlapping
  affordances in v1.1 — list-from-cluster operates on multiple
  envelopes; to-do-from-action operates on one. No reconciliation
  logic in v1.1.
- **Q5 — AppFunction schema versioning across upgrades**: when
  Orbit ships v1.2 and adds new AppFunction schemas, do existing
  v1.1 `appfunction_skill` rows migrate or get superseded?
  Answer: superseded with `schema_version` increment. Old rows
  stay for audit; new lookup keys point to the latest version.
  The migration is a single INSERT, not an ALTER. Defer the
  exact migration to v1.2 release planning.
- **Q6 — Multi-account calendars**: device with multiple Google
  accounts. The system Calendar app handles account selection
  during the insert intent. Orbit does not prefer or remember a
  specific account — that belongs to the user's calendar app.
  No 003 work needed.
