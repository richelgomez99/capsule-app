# Orbit Actions (v1.1)

**Status**: STUB — full PRD to be drafted after v1 (spec 002) ships and stabilizes
**Target release**: v1.1
**Depends on**: spec 002 complete (incl. v1 cluster-suggestion engine, 2026-04-26 amendment); spec 005 optional (BYOK improves quality); **spec 012 (Resolution Semantics — confirmed Orbit Actions are canonical resolution triggers per spec 012 FR-012-005)**
**Governing document**: `.specify/memory/constitution.md` — adheres to Principles I, VII, IX
**Last amended**: 2026-04-26 (relationship to v1 cluster-suggestion engine clarified after office-hours pivot)

---

## Summary

Orbit Actions turns captured text into structured, user-confirmed actions that land in the apps the user already uses. v1.1 introduces three action kinds:

1. **Calendar events** — parse flight confirmations, RSVPs, receipts with dates into an `ACTION_INSERT` intent targeting the system calendar.
2. **To-do items** — parse captured text containing lists or imperative sentences into one or more to-do rows, written to a user-chosen target (local Orbit to-do list, Google Tasks via share intent, etc.).
3. **Weekly digest** — extend the day-header paragraph feature into a weekly summary that surfaces every Sunday morning.

This feature is the first time Orbit *writes to external apps* on the user's behalf. It is bounded by a strict rule: **no silent writes, ever.** Every action is presented as a chip with a preview and requires explicit user confirmation before being executed.

---

## Relationship to v1 cluster-suggestion engine

The v1 cluster-suggestion engine (spec 002, 2026-04-26 amendment) and Orbit Actions (this spec, v1.1) are **orthogonal layers on the Diary**, not competing systems:

| | v1 cluster-suggestion engine (spec 002) | v1.1 Orbit Actions (this spec) |
|---|---|---|
| **Granularity** | Operates on a *cluster* of captures (research-session, task, etc.) | Operates on a *single envelope* (one capture) |
| **Action target** | Internal to Orbit (summary, structured list, open-all) | External apps (Calendar, To-do, share) |
| **Trigger** | Cluster forms → suggestion card surfaces next morning | Envelope text matches an extraction template (flight confirmation, list of imperatives, etc.) |
| **External writes** | Never. All cluster actions stay on-device, in Orbit. | Yes, via Android intents. The first place Orbit writes to external apps. |
| **UI surface** | Suggestion card at the top of the diary day | Inline action chip directly under the relevant envelope |
| **Audit** | Episode written on accept/reject of suggestion | Episode written on accept/reject of action |

**They can coexist on the same Diary day.** A research-session cluster's suggestion card surfaces at the top ("Want a summary?"), while individual envelopes within that cluster may *also* have Orbit Actions chips ("Add to calendar — Flight UA437 May 22 14:15").

**The agent (v1.2, spec 008) combines both.** When the full agent lands, it will be able to look at a cluster's contents, notice an embedded flight confirmation, and propose a single composite plan ("Save reading list AND add the flight to your calendar"). Until then (v1 and v1.1), the two layers operate independently and the user composes the workflow themselves.

---

## Non-Goals (v1.1)

- No autonomous actions. Every action is user-confirmed.
- No writes to email, messaging, or social apps.
- No account linking beyond standard Android intents.
- No recurring/automated calendar creation (e.g., "every Monday at 9").

---

## Design Principles

1. **Nano-first, BYOK-optional.** Every action extraction MUST work on-device with Gemini Nano. BYOK cloud (spec 005) can be toggled to improve extraction quality for users who opt in.
2. **Never act without confirmation.** A `ContinuationType.ACTION_EXTRACT` continuation produces candidate actions; the user sees a card in the Diary with "Add to calendar" / "Add to to-dos" buttons. No chained auto-execution.
3. **Round-trip via Android intents, not APIs.** Calendar writes use `Intent.ACTION_INSERT` with `CalendarContract.Events.CONTENT_URI`. To-do writes use `Intent.ACTION_SEND` to the user's chosen task app. No account linking, no OAuth.
4. **Auditable.** Every suggested action, user-confirmed action, and action abandonment writes an audit row.

---

## Initial Functional Requirements (to be expanded)

- **FR-003-001**: System MUST add `ContinuationType.ACTION_EXTRACT` that, when an envelope's text contains a recognizable action candidate (flight confirmation pattern, RSVP, list of imperatives, etc.), produces one or more structured action proposals.
- **FR-003-002**: System MUST display proposed actions as inline cards under the envelope in the Diary with a clear preview of the values being filled (title, date, time, location) and a one-tap confirmation.
- **FR-003-003**: System MUST NEVER write to any external app without a user tap on a confirmation affordance.
- **FR-003-004**: System MUST generate a weekly digest every Sunday at a user-configurable time, as a single new envelope of type `DIGEST` appearing at the top of that Sunday's Diary page.
- **FR-003-005**: System MUST route action extraction through the `LlmProvider` interface (002 T025a), allowing Orbit-managed proxy or BYOK cloud upgrade per spec 005.
- **FR-003-006**: System MUST log every suggested action, every confirmation, and every dismissal to the audit log.

### AppFunctions integration (tools for the agent, spec 008)

- **FR-003-007 (AppFunctions schema registration)**: Every action kind (calendar insert, to-do add, digest post, share) MUST be registered as an Android AppFunction with a JSON schema describing its arguments, its side effects, its reversibility, and its required sensitivity scope. Registered schemas are persisted in the `skills` table (spec 006) with `app_package='com.capsule.app'`.
- **FR-003-008 (`ACTION_EXECUTE` as AppFunction invocation)**: System MUST add `ContinuationType.ACTION_EXECUTE` whose sole effect is invoking a registered AppFunction by `function_id` with validated arguments. Invocations run on-device inside the `:capture` process and never touch the network. Per Principle XII, each invocation MUST emit an `episode` (spec 006) with `source_kind='agent_action'` linking the invocation to its plan and outcome; only that derived episode (and the resulting KG outcome edge) flows to Orbit Cloud — never the raw function arguments.
- **FR-003-009 (`AppFunctionSkill` entity with usage stats)**: System MUST model each installed AppFunction as a skill row in `skills` and record every invocation in `skill_usage` (spec 006) with outcome, latency, and linked episode. The Orbit Agent (spec 008) consumes these stats as planner heuristics (prefer skills with high success rates, default to user-confirmed skills).
- **FR-003-010 (tool-local, facts-flow-to-cloud rule)**: AppFunction execution MUST run entirely on-device. Function arguments, return values, and any artefacts the function creates locally MUST NOT be uploaded. Only the derived facts appropriate to share (episode metadata, outcome edge in the KG, confirmation state) flow to cloud, and only after passing the `:agent` consent filter (Principle XI).

---

## Open Questions (resolved before implementation)

- Which external to-do apps to first-class (Google Tasks, TickTick, Todoist, local-only)?
- What date-parse library handles fuzzy "next Tuesday at 3pm"? Does Nano do it well enough, or do we need a structured parser on top?
- Do we want a generic "Custom action template" where power users can define regex → intent mappings?
- **Diary surface coordination with the v1 cluster-suggestion card.** When a research-session cluster contains an envelope with a flight-confirmation Orbit Action chip, the same Diary day shows both the cluster card AND the per-envelope action chip. Visual stacking: cluster card always at top? Per-envelope action chips inline under each envelope? Or a unified "actionable" lane? Defer this to design.md (and spec 010 visual polish, 2026-04-26 amendment) since it's an information-architecture call, not a functional one.
- **Cluster-suggestion's "Save as Structured List" action vs. Orbit Actions to-do extraction.** Both produce list-shaped output. In v1, cluster-suggestion's structured-list action operates on a cluster of envelopes; Orbit Actions to-do extraction operates on a single envelope's text. They produce different list types (one is "your week of saves grouped by topic," the other is "the imperatives I extracted from this one screenshot"). v1.2 agent reconciles these; v1.1 ships them as separate, non-overlapping affordances.

---

*To be fleshed out into a full speckit spec after v1 ships.*
