# Resolution Semantics — "Capture is a Beginning, Not an End"

**Feature Branch**: `012-resolution-semantics`
**Created**: 2026-04-26 (during /office-hours pivot session)
**Status**: Draft
**Target release**: v1 (state model + visual treatment), with mechanism completeness across v1.1–v1.3
**Depends on**: spec 002 complete (envelope model, IntentEnvelope at point of save) including 2026-04-26 cluster-suggestion engine amendment; spec 003 (Orbit Actions are the canonical resolution-trigger source); spec 008 (v1.2 agent reads resolution state for pattern learning); spec 010 (visual treatment of state transitions on the wax seal)
**Governing documents**: [.specify/memory/constitution.md](.specify/memory/constitution.md), [.specify/memory/design.md](.specify/memory/design.md)
**Implements**: proposed Principle XIII (see Open Questions for constitutional amendment proposal)

---

## Why This Spec Exists

People save things on their phones constantly. Screenshots, articles, recipes, tweets. **Almost none of them get acted on.** The save itself feels like the action ("done — saved"); the moment of actually doing the thing arrives at a different time and place; the phone never reminds, organizes, or surfaces at the right moment; unread/undone items accumulate as background guilt.

Apple Photos saves your screenshots. Pocket saves your articles. Notion saves your notes. **None of them help you finish what you started.** That's the gap Orbit closes.

The current Orbit spec set (002 envelope + 003 actions + 008 agent + 010 visual polish) describes capture, organization, and templated action. **None of them describe the lifecycle of an envelope after capture.** That lifecycle — surfaced, acted on, resolved (or abandoned, with reason) — is what differentiates Orbit from a memory tool. This spec defines it.

Resolution is the unit of progress in Orbit. Captures-saved is a vanity metric; **intents-fulfilled is the real one.**

---

## Summary

The IntentEnvelope (spec 002) gains a **resolution lifecycle**:

| State | Meaning | Triggered by |
|---|---|---|
| **Saved** | Default at creation. Captured with intent, no further action yet. | `IntentEnvelope.seal()` in spec 002. |
| **Acknowledged** | User has re-encountered the envelope (re-opened, re-viewed). Not action, but signal. | URL re-encounter via spec 002 dedup hash; envelope detail view opened. |
| **In motion** | Partial action started. Not done, but not idle. | Cluster-suggestion *Open All* fired; user opened a saved URL via Orbit. |
| **Resolved** | The action implied by the intent has been taken. Capture has fulfilled its purpose. | Any spec 003 Orbit Action confirmed; manual mark-done; auto-resolve from time-bound triggers. |
| **Abandoned** | Explicitly closed without action, with a reason. Honest learning signal — not a failure. | User dismisses with reason chip; auto-archive after 60d unresolved (with reason `"orbital_decay"`). |
| **Snoozed** | Not now. Surface again at a specific moment. | User picks "remind me when X" affordance (Saturday morning, next charger, etc.). |

State transitions are **monotonic forward by default** (Saved → Acknowledged → In motion → Resolved). The only backward transition is **undo within 24h** (mirroring spec 003 FR-008-016). Abandonment can fire from any state and is terminal-but-undoable. Snooze pauses the lifecycle until the snooze condition fires; on fire, state returns to Saved.

**Generated content is itself an envelope.** When the agent runs *Summarize* on a research-session cluster, the resulting 3-bullet summary becomes a new envelope of derived type. KG continuation edges link the derived envelope to its source envelopes. Some agent actions (e.g., *Save as Structured List*) **partially resolve** their source envelopes (state advances to *In motion*); others (e.g., *Summarize*) do not auto-resolve sources — only persist the derivative artifact.

**Resolution is layout-coherent with the existing diary.** Resolved items stay in the diary at the day they were captured (with state-aware visual treatment) AND a new resolution-event entry appears on the day the action was taken. Both ends of the resolution thread show. No separate "Resolved" page — resolution is part of the story of a day, not a side ledger.

---

## Non-Goals (v1)

- No aggregate unresolved counter shown in any default UI (anti-guilt commitment).
- No notification, banner, or badge about unresolved items. Ever. Principle V (silence is a feature) extends to resolution.
- No streak tracking, leaderboard, social/shareable resolution stats. Resolution is a personal satisfaction system, not a productivity-pressure system.
- No external sync of resolution metadata in v1. v1.1+ syncs only via BYOK with explicit per-feature consent.
- No "resolution-required" affordance — explicitly making the user mark things done is exactly the friction model this spec rejects.
- No ground-truth claim. Orbit infers resolution from observable signals. The user actually flying the flight is invisible to Orbit; the calendar add is visible. We name what we know and don't claim what we don't.

---

## Design Principles

1. **Resolution is satisfaction, not productivity.** The frame is "you finished what you started," not "you have items to clear." Anti-guilt is constitutional.
2. **Effortless resolution is the 80% case.** Auto-resolve from agent actions (calendar add, to-do extract, structured-list creation) is the default trigger. Manual mark-done exists as a fallback escape hatch, not the primary path.
3. **Resolution states are visual states, not data labels.** Every state has a wax-seal fill treatment (per design.md §3.6). The user reads the state by glancing at the seal, not by reading words.
4. **Old unresolved items soften, never accumulate.** Graduated visual fade by age (60% at 14d, 40% at 30d, archive at 60d). Visual decay is the anti-guilt mechanism.
5. **Abandonment is honest, not a failure.** Explicitly marking something abandoned with a reason is a high-signal learning event for the v1.2 agent. Treated as completion-with-reason, not as closure-of-failure.
6. **Generated content joins the corpus.** Agent-derived content (summaries, lists, calendar events) becomes first-class envelopes with provenance edges. The corpus grows with derived alongside captured. Source envelopes' state may advance partially or fully depending on which action fired.
7. **No ground-truth fiction.** Orbit's resolution states reflect best-effort inference from observable signals. We never claim the user did something we couldn't see them do. UX honors this with phrasing that doesn't overclaim.

---

## State Model

### State transitions

```
                     ┌─→ Acknowledged ─┐
                     │                 ↓
   (capture) → Saved ─→  In motion ─→ Resolved
                     │                 ↑
                     ├──────────────────┤
                     │                 │
                     ↓                 │
                  Abandoned ←──────────┘
                     ↑
                     │
                  Snoozed (until trigger fires → returns to Saved)
```

### Trigger taxonomy

| Trigger | Source state | Target state | Notes |
|---|---|---|---|
| `seal()` (capture) | (none) | Saved | Default at creation. |
| `re_encounter` (URL hash hit; envelope re-viewed) | Saved | Acknowledged | Cheap inference. Once-per-day dedup so view-bounce doesn't spam transitions. |
| `cluster_action_partial` (e.g., *Save as Structured List* on cluster member) | Saved/Acknowledged | In motion | Action started but not completed. |
| `orbit_action_confirmed` (calendar add, to-do extract per spec 003) | Any | Resolved | Canonical resolution event. |
| `manual_mark_done` | Any | Resolved | User long-press → "mark done" affordance. Fallback path. |
| `time_bound_expiry` (e.g., flight date passed) | Any non-Resolved | Resolved | Auto-resolve with `resolution_reason='naturally_expired'`. Honest framing — not a claim of action. |
| `user_dismiss_with_reason` | Any | Abandoned | Reason chip-row at dismissal. |
| `orbital_decay` (60d unresolved) | Any non-Resolved | Abandoned | Auto-archive. `resolution_reason='orbital_decay'`. Surface in Sunday review only. |
| `user_snooze` | Any non-Resolved | Snoozed | User picks "remind me when X" condition. |
| `snooze_trigger_fires` | Snoozed | Saved | Returns to active lifecycle when condition fires. |
| `undo_within_24h` | Resolved/Abandoned/Snoozed | (previous state) | Mirrors spec 003 FR-008-016. After 24h, terminal. |

### Schema (extends spec 002 envelopes table)

New columns on the `envelopes` table:

```sql
resolution_state         TEXT NOT NULL DEFAULT 'saved'
                         CHECK (resolution_state IN
                           ('saved','acknowledged','in_motion','resolved','abandoned','snoozed'))
resolution_at            DATETIME            -- when the current state was entered
resolution_reason        TEXT                -- nullable; e.g., 'orbit_action:calendar_insert',
                                              --  'manual_mark_done', 'naturally_expired',
                                              --  'orbital_decay', 'user_dismissed:not_interested'
resolution_episode_id    TEXT                -- FK → episodes(id) for the action that resolved it
snooze_until             DATETIME            -- nullable; non-null only when state='snoozed'
snooze_condition         TEXT                -- nullable; e.g., 'saturday_morning', 'next_charger',
                                              --  'home_arrival', 'datetime'
```

Generated content (derived envelopes) carries a sibling marker:

```sql
derived_from             TEXT                -- nullable; FK → envelopes(id) when this envelope was
                                              --  produced by an agent action operating on another envelope
derived_via              TEXT                -- nullable; e.g., 'cluster_summarize',
                                              --  'cluster_structured_list', 'orbit_action:calendar_insert'
```

Plus KG edges: `derives_from` (envelope → envelope) and `derived_for_cluster` (envelope → cluster_id) per spec 007.

---

## Functional Requirements

### State management

- **FR-012-001 (state column on envelopes)**: System MUST add `resolution_state` to the envelopes table per the schema above. Default 'saved' for all existing rows on schema migration.
- **FR-012-002 (state machine enforcement)**: System MUST enforce the state-transition table above at the data layer. Forbidden transitions (e.g., Resolved → Saved without undo within 24h) MUST be rejected with audit logging.
- **FR-012-003 (every state change is an episode)**: Every transition MUST write a row to the `episodes` table (spec 006) with metadata: from_state, to_state, trigger, resolution_reason, optional resolution_episode_id. This enables v1.2 agent pattern learning per spec 008 FR-008-V1-001.
- **FR-012-004 (24h undo window)**: All state changes MUST be undo-able for 24h. Undo writes a corrective episode and reverses the transition. After 24h, transitions are terminal except via the manual mark-done flow which can change Resolved → Saved with an explicit "undo" episode.

### Resolution triggers

- **FR-012-005 (Orbit Action confirmation triggers Resolved)**: Per spec 003, when the user confirms a templated action (calendar insert, to-do extract, share, etc.), the source envelope's state MUST transition to Resolved with `resolution_reason` set to the action's function_id (e.g., `orbit_action:calendar_insert`).
- **FR-012-006 (cluster-suggestion action partial-resolution rules)**: Per spec 002 cluster-suggestion engine amendment:
   - *Summarize*: source envelopes do NOT auto-resolve. Generated summary persists as derived envelope. Sources can be advanced to Acknowledged if user reads through (separate inference). 
   - *Open All*: source envelopes advance to In motion. Full resolution requires further action per source.
   - *Save as Structured Reading List*: source envelopes advance to In motion. Generated list-envelope is a new envelope. Reading through the list to completion can resolve sources individually.
- **FR-012-007 (re-encounter triggers Acknowledged)**: When the user re-views an envelope detail view OR a URL-hash dedup hit fires from a new capture, the envelope state advances Saved → Acknowledged. Once-per-day dedup so view-bounce doesn't spam transitions.
- **FR-012-008 (manual mark-done fallback)**: Long-press on an envelope card MUST offer a "mark done" affordance (geist 12sp, italic, no Material context menu). On tap, state transitions to Resolved with `resolution_reason='manual_mark_done'`. Optional reason chip-row similar to FR-008-V1-003 (rejection reasons) — common reasons: *"already did it"*, *"out of date"*, *"already remembered."*
- **FR-012-009 (time-bound expiry auto-resolve)**: For envelopes with detected date attributes (flight confirmations, RSVPs, deadlines), system MUST schedule a resolver job that fires when the date passes. State → Resolved with `resolution_reason='naturally_expired'`. The italic resolution line reads honestly: *"sealed · naturally expired · [date]"* — no claim that the user took action.
- **FR-012-010 (orbital-decay auto-archive)**: After 60 days unresolved (no transition events), system MUST auto-transition the envelope to Abandoned with `resolution_reason='orbital_decay'`. The envelope remains searchable but disappears from default diary view. Surface in Sunday review only as a count: *"23 saved 60+ days ago, still here if you want them."*

### Generated content

- **FR-012-011 (derived envelopes are first-class)**: When an agent action produces persistent content (summary, structured list, calendar event), the system MUST create a new envelope with `derived_from` set to the source envelope (or the cluster's primary source) and `derived_via` set to the action's function_id. The derived envelope follows the full envelope lifecycle (can be resolved, abandoned, etc.) independently of its source(s).
- **FR-012-012 (KG provenance edges)**: System MUST add `derives_from` edges in the KG (spec 007) linking each derived envelope to all its source envelopes. Edge metadata records the action and timestamp.
- **FR-012-013 (recursion bounded)**: Derived envelopes MUST NOT trigger further agent-driven derivation. The cluster engine MUST exclude `derived_from IS NOT NULL` envelopes from cluster formation. This is a hard rule to prevent corpus flood and infinite-loop generation.
- **FR-012-014 (derived-content visual marker)**: Derived envelopes render with a visual marker distinguishing them from captured envelopes. Per design.md §4.6 amendment (to be added — see Open Questions): italic Newsreader for body type, plus a small typographic mark in the margin indicating "agent-derived" (proposed: a small `~` or `↻` glyph hung in the margin column, distinct from wax seal).

### Visual treatment (cross-ref design.md and spec 010)

- **FR-012-015 (wax-seal fill states)**: System MUST extend `WaxSeal` primitive (spec 010 FR-010-005) to support all 6 resolution states via fill, not color:
   - Saved: outline-only (current default)
   - Acknowledged: outline + small ink dot inside
   - In motion: half-filled
   - Resolved: fully filled, with optional 240ms ink-bleed animation on first render in the resolved state
   - Abandoned: crossed-through (small × overlay)
   - Snoozed: outline + typographic clock-mark above
- **FR-012-016 (italic resolution line)**: When state is Resolved, Abandoned, or Snoozed, the envelope card MUST render an italic Geist 12sp `--ink-dim` line below the envelope content: e.g., *"sealed · added to calendar · Wed 4:42P"*, *"abandoned · already did it elsewhere · Mon 9:14A"*, *"snoozed · until Saturday morning."* Resolution line persists with the envelope across diary navigation.
- **FR-012-017 (graduated visual fade)**: Unresolved envelopes (states Saved, Acknowledged, In motion) MUST render at the following opacity by age:
   - Day 1-13: 100% opacity, normal weight
   - Day 14-29: 60% opacity, type weight unchanged
   - Day 30-59: 40% opacity, type compresses (smaller line height, less margin)
   - Day 60+: not rendered in default diary view (auto-archived per FR-012-010)
- **FR-012-018 (resolution event entry on action day)**: When an envelope is Resolved on a date later than its capture date, the diary's day-of-action MUST include a small "resolution event" entry showing the cross-day link. Example: a recipe captured Tuesday, sealed Friday after grocery-list extraction → Friday's diary shows: *"sealed: recipe from Tue → grocery list (Fri 4:42P)"* as a typographic line in the day's stream, distinct from envelope cards.
- **FR-012-019 (cluster card resolution awareness)**: Per spec 002 cluster-suggestion amendment, when a cluster's source envelopes have mixed resolution states, the cluster-suggestion card body text MUST adjust: *"This research session has 4 captures — 2 already sealed. Want a focused summary of the 2 still open?"* If the cluster reaches all-resolved, the card MUST NOT re-surface even if dismissal hadn't occurred — the cluster has cleared.

### Sunday review (extends spec 003 FR-003-004)

- **FR-012-020 (resolution stats in weekly digest)**: The weekly digest envelope (spec 003 FR-003-004) MUST include resolution stats for the week: total captured, sealed, abandoned, still open. Format: a single typographic line at the top of the digest body. Example: *"This week: 23 captured · 17 sealed · 2 abandoned · 4 still open."*
- **FR-012-021 (no rolling unresolved counter)**: The Sunday digest MUST NOT include a cumulative-across-weeks unresolved count. Each week's digest reports only that week's stats. Anti-guilt commitment per Principle V extends here.
- **FR-012-022 (Sunday review is the satisfaction beat)**: The weekly digest's body should feel like a small letter-to-self about the week's intent fulfillment. Tone: warm, brief, factual. Generated by Nano on-device per spec 003 FR-003-004.

### Privacy / cloud routing

- **FR-012-023 (resolution data is local-only at v1)**: Resolution state, transitions, reasons, and snooze conditions MUST stay on-device at v1. No cloud sync, no telemetry, no analytics export. Constitutional Principle I compliance.
- **FR-012-024 (BYOK resolution sync requires explicit consent)**: At v1.1+ when BYOK (spec 005) is enabled, resolution metadata MAY sync to user-supplied storage (BYOC per spec 006/009) only with an explicit per-feature consent toggle in Settings → Privacy. Default: off. The toggle copy MUST honestly name what's syncing and where ("Sync resolution state to my [storage provider]? Includes which envelopes you sealed and why").
- **FR-012-025 (no aggregate metric exfiltration ever)**: No build of Orbit ever exfiltrates aggregate resolution metrics — not to Orbit's servers (none exist), not to Crashlytics, not to any third-party. Principle I.

### Settings & user controls

- **FR-012-026 (resolution settings page)**: Settings → Resolution MUST expose:
   - Toggle: enable/disable auto-resolve from Orbit Actions (default: on)
   - Toggle: enable/disable orbital-decay auto-archive (default: on; the user can opt to keep all envelopes forever)
   - Slider: orbital-decay window (default: 60 days; range: 30-365)
   - View: list of currently snoozed envelopes with their snooze conditions
   - Export: resolution stats for arbitrary date range as a typographic summary
- **FR-012-027 (per-envelope resolution audit)**: From any envelope detail view, the user MUST be able to see the full state-transition history (every episode for that envelope) — when, what trigger, what reason. Constitutional audit-log transparency (Principle X).

---

## Cluster Lifecycle State Machine (added 2026-04-26 via /autoplan)

Spec 012's primary state machine is the **envelope** resolution state machine (Saved → Acknowledged → In motion → Resolved, plus Abandoned/Snoozed). The **cluster** state machine is a separate lifecycle for the cluster-suggestion-engine surface (spec 002 amendment §FR-026..FR-040). Clusters are not envelopes — they are agent-detected groupings that produce derived envelopes when their actions fire.

**States** (8 total):

```
                          [ClusterDetectionWorker writes row]
                                       │
                                       ▼
                                   ┌────────┐
                                   │FORMING │  (transient, in-worker, never on disk)
                                   └───┬────┘
                                       │ commit single transaction
                                       ▼
                                  ┌──────────┐
                       ┌─────────▶│ SURFACED │  (default state on disk)
                       │          └────┬─────┘
                       │               │ user taps card
                       │               ▼
                       │           ┌────────┐
                       │   ┌──────▶│ TAPPED │
                       │   │       └────┬───┘
                       │   │            │ Summarize call begins (state persisted to disk)
                       │   │            ▼
                       │   │        ┌────────────┐
                       │   │        │ ACTING     │
                       │   │        └─┬────┬─────┘
                       │   │ success  │    │ Nano error / timeout
                       │   │          ▼    ▼
                       │   │     ┌──────┐ ┌────────┐
                       │   │     │ACTED │ │ FAILED │ retry → ACTING (max 3)
                       │   │     └──────┘ └────┬───┘ third failure → DISMISSED
                       │   │                   │
                       │   │ user dismiss      │
                       │   ◀────────────────────┘
                       │
                       │ 7d aging-out from SURFACED with no tap
                       ▼
                  ┌──────────┐               ┌────────────┐
                  │ AGED_OUT │               │ DISMISSED  │
                  └──────────┘               └────────────┘
                                                   ▲
                                                   │ orphan-cleanup: from
                                                   │ any state if surviving
                                                   │ members < 3 (audit
                                                   │ entry: reason=orphaned)
```

**Functional requirements**:

- **FR-012-028 (cluster state machine enforcement)**: System MUST enforce the 8-state cluster lifecycle at the data layer. Forbidden transitions (e.g., `DISMISSED → ACTING`, `AGED_OUT → SURFACED`) MUST be rejected with audit logging via the `CLUSTER_*` enum values per spec 002 FR-039.
- **FR-012-029 (FORMING is in-worker only)**: `FORMING` is a transient state inside `ClusterDetectionWorker`'s embedding-and-similarity loop. It MUST NOT be written to disk. Persistence happens only on commit-to-`SURFACED` inside a single Room transaction; partial-batch failures leave nothing.
- **FR-012-030 (SURFACED is default on-disk state)**: Clusters that pass the FR-028 threshold heuristic AND the FR-030 modelLabel boundary gate are persisted with `state=SURFACED`. The Diary's `ClusterRepository.observeSurfacedToday()` query filters on `state IN ('SURFACED', 'TAPPED', 'ACTING', 'ACTED', 'FAILED')` AND surviving-members ≥ 3.
- **FR-012-031 (ACTING is disk-persisted)**: When the user taps Summarize, the cluster transitions to `ACTING` and the new state is **written to disk before** Nano inference begins. This guarantees that backgrounding the app mid-inference resumes correctly: on foreground, the user sees either the ACTED result OR a "tap to retry" affordance, never a frozen ACTING ellipsis.
- **FR-012-032 (FAILED retry bounds)**: A cluster MAY transition `FAILED → ACTING` up to 3 times via the user's ↻ retry affordance (per spec 010 FR-010-024). On the 3rd FAILED, the next user dismiss automatically transitions to `DISMISSED`; no further retry is offered. This bound prevents unbounded retry loops on persistent Nano failures.
- **FR-012-033 (dismissal-during-ACTING is a no-op)**: User-initiated dismiss during `ACTING` state is visually rejected (no-op). Once `ACTING` transitions to `ACTED` or `FAILED`, dismiss becomes available again. This prevents Nano inference from being orphaned mid-call and prevents UI race conditions.
- **FR-012-034 (AGED_OUT timeout)**: A cluster in `SURFACED` state for ≥ 7 calendar days with no `TAPPED` transition MUST transition to `AGED_OUT` on the next `ClusterDetectionWorker` run. The card no longer renders. Audit log records the transition.
- **FR-012-035 (orphan auto-DISMISS)**: When `SoftDeleteRetentionWorker` (002 T085) cascades a hard-delete to `ClusterMember` rows, any cluster with surviving members < 3 MUST auto-transition to `DISMISSED` with audit `CLUSTER_ORPHANED reason=members_below_minimum`. The transition is allowed from any prior state (including `ACTED`).
- **FR-012-036 (cluster ACTED does not auto-resolve sources)**: Per FR-012-006, the *Summarize* action does NOT auto-resolve source envelopes — only the cluster transitions to `ACTED`. The derived summary envelope is created per FR-012-011. Source envelopes remain in their pre-action resolution state. Reading-through inference may advance them to `Acknowledged` separately (a future FR).
- **FR-012-037 (cluster cross-product with envelope resolution)**: When a cluster reaches all-resolved at the envelope level (every member envelope's `resolution_state == Resolved`), the cluster card MUST NOT re-surface even if dismissal hadn't occurred. Per FR-012-019, the cluster has cleared. The cluster row remains in `DISMISSED` state with audit reason `cleared_via_envelope_resolution`.

**Cluster vs envelope state machines — relationship**:

The two state machines are independent but coupled through the `cluster_member` join table and the derived-envelope provenance edges (per FR-012-011, FR-012-013). Practically:

| Action | Cluster transition | Source envelopes | Derived envelope |
|---|---|---|---|
| `SURFACED → DISMISSED` (user dismiss) | DISMISSED | unchanged | none created |
| `ACTING → ACTED` (Summarize succeeds) | ACTED | unchanged | new derived envelope (`derived_via='cluster_summarize'`) |
| `ACTING → ACTED` (SaveAsList stretch, v1.1) | ACTED | partial-resolve to `In motion` per FR-012-006 | new derived envelope |
| envelope hard-delete | orphan auto-DISMISS if surviving < 3 | one fewer member | unchanged (decoupled lifecycle per FR-012-011) |
| every cluster transition | logged as `CLUSTER_*` | not affected | not affected |

The relationship is: cluster state machine governs **the agent's offer**, envelope state machine governs **the user's loop**. They intersect when an action fires; they decouple at every other moment. This separation is load-bearing — it lets the demo's "agent leads → user closes" beat be two distinct visual moments that share an event but live in different state systems.

---

## Visual Treatment (cross-references)

- design.md §3.6 (Iconography) — the wax-seal fill-state extension. Requires design.md amendment to add a "Resolution states" sub-section after the existing wax-seal vocabulary.
- design.md §4.5 (Diary today's page) — the day-header gains an optional 3-state filter chip (`all · open · sealed`).
- design.md §4.5.1 (Cluster-suggestion card, just added 2026-04-26) — the cluster card body text MUST adjust per FR-012-019 when source envelopes are partially resolved.
- design.md §4.6 (Envelope card) — gains the italic resolution line treatment per FR-012-016, plus the typographic margin mark for derived envelopes per FR-012-014.
- design.md §4.5.x (Sunday review surface) — to be added; this spec is the catalyst.
- spec 010 (Visual polish) — must add 4 new FRs covering: WaxSealFillStates primitive extension, italic resolution line primitive, graduated-fade rule, resolution-event-entry typography.

---

## Pitch / Strategic Implications

The Demo Day pitch shifts:

> *"Apple Photos remembers your screenshots. Pocket remembers your articles. Notion remembers your notes. None of them help you finish what you started. Orbit captures with intent — and helps you close the loop. This week alone, my phone helped me close 17 loops."*

The wow moment becomes resolution-led: the cluster card surfaces, the user taps an action, **the audience watches a source envelope's wax seal fill in real time** — sealed with approval — while the derived content appears below. Two beats in one demo: pattern recognition AND loop closure. The audience leaves remembering "loops closed."

**Why this is defensible:**

- Apple's incentive is engagement + ecosystem lock-in. They want you in Apple Calendar AND Apple Photos AND Apple Notes — separately, with no resolution semantics that span them. Orbit's resolution requires cross-app awareness Apple structurally won't ship.
- Google's incentive is ad attention. A "loops closed" success metric fights ad inventory, because closing a loop reduces engagement.
- Notion's incentive is daily active use. Resolution explicitly de-emphasizes accumulation, which is Notion's growth flywheel.
- **Orbit's success metric is loops closed.** Not time-in-app, not sessions, not retention-as-engagement. This is a meta-differentiator that lives in the constitution and is honored by every product decision downstream.

The Sunday review is the demo's closer:

> *"This is what your week looks like with Orbit. 23 captured. 17 sealed. Five-second sealing animation, twelve-second pattern reveal, ninety-second pitch — and a phone that, for the first time, actually helps you finish what you started."*

---

## Dependencies

- spec 002 (envelope model) — schema migration adds 6 columns + cluster-suggestion engine integration
- spec 003 (Orbit Actions) — confirmed actions become canonical resolution triggers per FR-012-005
- spec 006 (Orbit Cloud schema) — episodes table records every state transition; future BYOC sync of resolution metadata
- spec 007 (Knowledge graph) — `derives_from` edges for generated content
- spec 008 (Orbit Agent) — v1 cluster engine reads/writes resolution state; v1.2 agent uses resolution as success metric and learning signal
- spec 010 (Visual polish) — extends WaxSeal primitive, adds italic resolution line, graduated-fade rule, resolution-event-entry typography
- design.md — amendments for resolution state visual treatment, Sunday review surface, derived-content margin mark
- constitution — proposed Principle XIII addition (see Open Questions)

---

## Open Questions

- **OQ-012-001 (orbital-language framing — DEFERRED)**: This spec uses "resolution / sealed / abandoned / snoozed" as the user-facing vocabulary. An alternative framing — orbital ("bring closer / let drift / land / let escape") — was brainstormed during the 2026-04-26 office hours session and judged risky for daily UI but potentially strong as brand voice (onboarding, marketing, Sunday review header, Demo Day pitch). **Decision deferred to post-user-testing**: ship v1 with resolution language, observe how the 5 alpha users naturally describe the feature, decide on orbital framing for v1.1+ marketing + onboarding refresh based on signal. Preserve the brainstorm as a `docs/orbital-framing-deferred.md` document so it doesn't get lost.
- **OQ-012-002 (Principle XIII constitutional amendment)**: Proposed addition to constitution.md as Principle XIII, sitting between III (Intent Before Artifact) and IV (Continuations Grow Captures): *"Resolution Closes the Loop. Capture is a beginning, not an end. The IntentEnvelope has a lifecycle: created, surfaced, acted on, resolved (or abandoned, with reason). Every envelope's intent is an implicit promise from Orbit to the user — you'll get to do something with this. Orbit is the product that keeps that promise. Resolution is inferred from action wherever possible, manual only as a fallback, and never weaponized into pressure. An old unresolved capture softens; an abandoned capture is celebrated as honest. The unit of progress in Orbit is not captures-saved, it is intents-fulfilled."* Lock by April 30 if v1 ships with resolution semantics; otherwise defer.
- **OQ-012-003 (manual mark-done UX)**: Long-press on envelope card → "mark done" affordance. The exact gesture (long-press vs. swipe vs. action drawer expansion) needs design.md decision. Recommendation: extend the existing action drawer (design.md §4.6 row #5) to add "Mark done" as a row alongside Reassign / Archive / Delete. Avoid long-press as a hidden gesture; users won't discover it.
- **OQ-012-004 (snooze condition vocabulary)**: Snooze conditions need a finite vocabulary. Proposed v1: *"this weekend"*, *"next charger + wifi"*, *"a specific date/time"*, *"when home"* (requires location, defer). v1 ships first 3, "when home" defers to v1.2 with home-detection.
- **OQ-012-005 (re-encounter inference precision)**: FR-012-007 advances state on re-encounter. Risk: "re-encountered" might be a low-signal event (user accidentally taps the wrong envelope). Recommendation: require re-encounter to last >5 seconds in detail view OR be a full URL-hash dedup match (high signal) before advancing state. Tune precision vs. recall during alpha.
- **OQ-012-006 (visual marker for derived envelopes)**: FR-012-014 says derived envelopes get a margin glyph distinct from wax seals. Specific glyph TBD — proposed `~` or `↻` or a custom mark. Lock during the design.md §4.6 amendment by April 30 to unblock spec 010 FR-010-014.
- **OQ-012-007 (Sunday review tone)**: FR-012-022 says the digest should feel like a "letter to self." That's a tonal target, not a copy spec. The first version of the Nano-generated weekly summary copy needs human review and tuning. Reserve a polish-pass workstream by May 18.
- **OQ-012-008 (alpha test design)**: The 5 Canopy alpha users (per the design doc at `~/.gstack/projects/richelgomez99-capsule-app/`) will be the first humans to encounter resolution. The week-by-week prompts already cover capture and diary. Add a new prompt: Week 3 (May 13-19) — *"Did Orbit help you finish anything? Anything you wanted to abandon but didn't know how? Anything that resolved without you marking it done?"* — to surface resolution UX feedback.

---

## Implementation Order

If/when this spec moves from Draft to active implementation:

1. **Schema migration** (FR-012-001) — add the 6 columns. Reversible via one-shot down-migration.
2. **State machine + episode logging** (FR-012-002, FR-012-003, FR-012-004). Pure backend. No UI.
3. **Wax-seal fill states** (FR-012-015) — extend the primitive in spec 010. Visual feedback for the state machine.
4. **Italic resolution line** (FR-012-016). UI rendering of state.
5. **Orbit Action trigger integration** (FR-012-005). The 80% case lights up.
6. **Re-encounter inference** (FR-012-007). Cheap signal, low risk.
7. **Manual mark-done fallback** (FR-012-008). Escape hatch.
8. **Time-bound expiry** (FR-012-009). Date-attached envelopes.
9. **Graduated visual fade** (FR-012-017). Anti-guilt mechanism.
10. **Orbital-decay auto-archive** (FR-012-010). 60d cutoff.
11. **Derived envelopes** (FR-012-011, FR-012-012, FR-012-013, FR-012-014). Generated content joins corpus.
12. **Cluster card resolution awareness** (FR-012-019). Feedback loop.
13. **Resolution event entries** (FR-012-018). Cross-day threading.
14. **Sunday review extensions** (FR-012-020 through FR-012-022). The killer feature.
15. **Settings & audit** (FR-012-026, FR-012-027). User controls.
16. **Privacy lock** (FR-012-023, FR-012-024, FR-012-025). Cross-spec compliance.

Items 1-7 are v1-feasible if the design doc's hour estimate clears (per `~/.gstack/projects/richelgomez99-capsule-app/`). Items 8-16 are v1.1+.

---

## Success Criteria

- **SC-012-1**: At least 1 of the 5 Canopy alpha users explicitly references "resolution" or "closing the loop" in their week-3 check-in feedback without prompting from the structured questions. The vocabulary lands as natural.
- **SC-012-2**: After 14 days of alpha use, the median alpha user has at least 5 envelopes that transitioned to Resolved via Orbit Action confirmation (auto-resolve path, the 80% case). The mechanism works without user instruction.
- **SC-012-3**: Zero notifications about unresolved items fire across all alpha users for 28 days. Anti-guilt rule honored.
- **SC-012-4**: Sunday review (FR-012-020) is opened by ≥4 of 5 alpha users on at least 1 Sunday during the alpha window. The satisfaction beat lands.
- **SC-012-5**: No alpha user disables resolution auto-archive (FR-012-026 toggle) within 28 days. The 60d default works.
- **SC-012-6**: Orbital-language framing (OQ-012-001) decision is made by EOD May 19 based on alpha feedback, ahead of Demo Day on May 22.

---

*Drafted in 2026-04-26 office-hours session. Spec is structured for v1 minimum viability (state machine + auto-resolve + visual treatment) with v1.1+ completing the mechanism. The orbital-language framing question is consciously deferred to user testing rather than committed pre-evidence.*
