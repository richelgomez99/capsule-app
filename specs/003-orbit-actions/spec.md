# Orbit Actions (v1.1)

**Status**: STUB — full PRD to be drafted after v1 (spec 002) ships and stabilizes
**Target release**: v1.1
**Depends on**: spec 002 complete; spec 005 optional (BYOK improves quality)
**Governing document**: `.specify/memory/constitution.md` — adheres to Principles I, VII, IX

---

## Summary

Orbit Actions turns captured text into structured, user-confirmed actions that land in the apps the user already uses. v1.1 introduces three action kinds:

1. **Calendar events** — parse flight confirmations, RSVPs, receipts with dates into an `ACTION_INSERT` intent targeting the system calendar.
2. **To-do items** — parse captured text containing lists or imperative sentences into one or more to-do rows, written to a user-chosen target (local Orbit to-do list, Google Tasks via share intent, etc.).
3. **Weekly digest** — extend the day-header paragraph feature into a weekly summary that surfaces every Sunday morning.

This feature is the first time Orbit *writes to external apps* on the user's behalf. It is bounded by a strict rule: **no silent writes, ever.** Every action is presented as a chip with a preview and requires explicit user confirmation before being executed.

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
- **FR-003-005**: System MUST route action extraction through the `LlmProvider` interface (002 T025a), allowing BYOK cloud upgrade per spec 005.
- **FR-003-006**: System MUST log every suggested action, every confirmation, and every dismissal to the audit log.

---

## Open Questions (resolved before implementation)

- Which external to-do apps to first-class (Google Tasks, TickTick, Todoist, local-only)?
- What date-parse library handles fuzzy "next Tuesday at 3pm"? Does Nano do it well enough, or do we need a structured parser on top?
- Do we want a generic "Custom action template" where power users can define regex → intent mappings?

---

*To be fleshed out into a full speckit spec after v1 ships.*
