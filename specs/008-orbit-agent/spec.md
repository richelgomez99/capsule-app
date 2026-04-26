# Orbit Agent (v1.2)

**Status**: STUB — full PRD to be drafted after v1.1 stabilizes
**Target release**: v1.2 (full agent with planner + executor + consent filter)
**v1 precursor**: Cluster-suggestion engine ships in v1 per spec 002 amendment (see "Relationship to v1 cluster-suggestion engine" below). The v1 precursor is NOT this spec; this spec describes the full agent that lands in v1.2.
**Depends on**: spec 002 (incl. v1 cluster-suggestion engine), spec 003 (Orbit Actions as AppFunctions), spec 005 (LLM routing), spec 006 (Orbit Cloud), spec 007 (Knowledge Graph), **spec 012 (Resolution Semantics — provides agent's success metric "loops closed" and pattern-learning input from resolution episodes)**
**Governing document**: `.specify/memory/constitution.md` — implements Principles I, V, IX, X, XI, XII
**Created**: 2026-04-20
**Last amended**: 2026-04-26 (v1 precursor relationship added after office-hours pivot)

---

## Summary

The Orbit Agent is a user-invoked planner + executor that reads the
knowledge graph and profile subgraph, composes a plan, and executes
it by invoking registered AppFunctions (spec 003) as tools. The agent
runs in a dedicated `:agent` process and assembles every prompt
on-device through the consent-aware prompt assembly gate (Principle
XI) before sending anything to `:net` for LLM inference.

The agent is **never autonomous**. It is invoked by explicit user
action — bubble long-press, an Ask Orbit follow-up that asks it to
act, or a scheduled digest that asks the user for confirmation.
Every plan is user-visible, every tool invocation is auditable, and
every successful action becomes a knowledge graph episode so that
the same agent remembers what it did last time.

---

## Relationship to v1 cluster-suggestion engine

A **lightweight precursor** to this agent ships in v1 (per the
2026-04-26 amendment to spec 002): a cluster-suggestion engine that
detects clusters of captures (research-session as the canonical
v1 cluster type, with task / shopping / meeting prep / travel as
v1+ stretch types) and surfaces an inline suggestion card in the
diary the morning after a cluster forms. Each card carries 2-3
hardcoded inline actions (e.g., for research-session: Summarize /
Open All / Save as Structured Reading List).

The v1 precursor is intentionally NOT the full agent described in
this spec. Differences:

| Aspect | v1 cluster-suggestion engine | v1.2 Orbit Agent (this spec) |
|---|---|---|
| Invocation | Proactive (surface card after cluster forms) | User-invoked (bubble long-press, Ask Orbit hand-off) |
| Actions | Hardcoded per cluster type | AppFunctions registered by other apps (spec 003) |
| Planning | None (templated card → tap → run) | ReAct planner with human-readable plan |
| Process | Runs inside `:ml` (continuations) + `:ui` (card render) | Dedicated `:agent` process |
| Consent filter | N/A — all on-device, no LLM beyond Nano | Mandatory four-point filter before any `:net` call |
| Memory | Cluster nodes in KG (precursor to Pattern nodes) | Full memory taxonomy (sessions, patterns, plans, skills) |
| Audit | Every accepted/rejected suggestion writes an episode | Every tool call writes an episode |

**Evolution path:** in v1.2, the cluster-suggestion engine becomes
one of the agent's *invocation surfaces* alongside the bubble
long-press. The hardcoded actions become AppFunctions. The card
itself remains, but tapping an action routes through the agent's
planner with a pre-filled plan corresponding to the templated
action. This keeps the v1 UX unchanged while letting the v1.2 agent
take over the executor side.

**Episodes are forward-compatible.** v1's cluster-suggestion
acceptance/rejection records become episodes in the same `episodes`
table (spec 006) used by the v1.2 agent, so the agent inherits all
v1 user feedback as patterns from day one of v1.2.

### V1 precursor functional requirements (must ship)

These three requirements bind the v1 cluster-suggestion engine
(spec 002, 2026-04-26 amendment) so that v1 is forward-compatible
with the v1.2 agent and constitutionally compliant. They are owned
by spec 008 (this spec) but implemented as part of v1 in spec 002.

- **FR-008-V1-001 (episode persistence, forward-compat)**: Every
  cluster-suggestion card surfaced, accepted (action tapped),
  dismissed, or expired (max card lifetime per cluster, default
  72 h) MUST write a row to the `episodes` table (spec 006) with
  `source_kind='cluster_suggestion'` and metadata including
  `cluster_id`, `cluster_type`, `action_taken` (or null on
  dismissal), and `confidence` (the cluster engine's similarity
  score). This guarantees v1.2 agent inherits v1 user feedback for
  Pattern node bootstrapping (Principle XII).
- **FR-008-V1-002 (privacy lock, no cloud routing in v1)**: The v1
  cluster engine, the suggestion card actions (Summarize / Open All
  / Save as Structured List for research-session, plus
  cluster-type-specific actions for any v1.x stretch types) MUST
  run entirely on-device. No LLM call routes to `:net`, even if
  BYOK (spec 005) is enabled. This is a hard architectural
  constraint, NOT a default. v1.1+ may opt clusters into
  BYOK-routable variants only with explicit per-cluster user
  consent and a separate spec amendment. Closes Principle I in v1
  cluster ops specifically.
- **FR-008-V1-003 (reject-with-feedback)**: When the user dismisses
  a cluster-suggestion card, the dismiss interaction MUST optionally
  surface a brief chip-row of common rejection reasons (proposed:
  *"not actually related"*, *"wrong topic"*, *"too small to bother"*,
  *"not now"*) plus a free-text option. Selected reason persists in
  the episode row's metadata as `rejection_reason`. The chip-row is
  NOT modal — dismissing without picking a reason still works and
  records `rejection_reason='unspecified'`. v1.2 agent uses these
  labeled rejections as negative signal for cluster-type
  classification.

---

## User Stories

### US-008-001 — Long-press the bubble to invoke the agent (Priority: P1)

As a user with the Orbit bubble on screen, I long-press the bubble
and say "remind me to ping Alice tomorrow about the pitch." The agent
reads my profile subgraph to find Alice, drafts a reminder plan,
shows the plan inline with a one-tap "Do it" and "Not right now,"
and on confirm calls the Reminders AppFunction.

**Acceptance**:

1. Long-press on the bubble opens the agent input sheet; voice and
   text both accepted.
2. The agent produces a human-readable plan (tool name, arguments,
   expected effect) before executing anything.
3. Tapping "Do it" invokes the tool and writes an outcome episode;
   tapping "Not right now" records a rejection edge in the KG.
4. The agent never executes without an explicit user tap.

### US-008-002 — Ask Orbit hand-off to the agent (Priority: P1)

As a user asking Orbit "what should I follow up on this week and
draft the messages," I get a plan that drafts per-contact messages
using my profile subgraph tone preferences and waits for my
per-message confirmation.

**Acceptance**:

1. Ask Orbit (spec 004) can hand off to the agent when a query
   implies action.
2. The agent reads profile subgraph (spec 007 FR-007-011) for
   tone/style, applies it, and surfaces drafts.
3. User confirms or edits each draft; execution is per-message.

### US-008-003 — The agent remembers across sessions (Priority: P2)

As a user who regularly asks the agent to draft standup messages,
the agent learns my standup shape over time and defaults to it
without being reminded.

**Acceptance**:

1. Recurring patterns (same tool, similar args, confirmed > 3 times)
   become `kg_nodes` of type `Pattern` with usage stats.
2. Future plans reference patterns and reduce ask-for-clarification
   steps.
3. User can open Settings → Agent → Patterns and delete any pattern
   with an audit entry.

---

## Non-Goals (v1.2)

- No autonomous scheduled agents. Every execution is explicitly
  invoked or explicitly confirmed.
- No agent-to-agent orchestration.
- No custom-skill authoring UI in v1.2. Skills are AppFunctions that
  other apps register; Orbit does not ship a skill SDK.
- No on-device LLM agent loops beyond the Nano context window. Long
  reasoning routes through Orbit-managed LLM proxy (spec 005).

---

## Design Principles

1. **Dedicated `:agent` process.** Planner, executor, prompt assembly,
   and consent filter all live in `:agent`, isolated from `:ml`,
   `:net`, `:capture`, and `:ui` by process boundaries and AIDL
   surfaces.
2. **Tools are AppFunctions.** Every capability the agent can take
   action with is a registered AppFunction (spec 003). Tools run
   on-device; only their results become episodes and flow to cloud.
3. **Consent filter is the gate to `:net`.** No prompt leaves
   `:agent` without passing the four-point filter (Principle XI):
   strip `local_only`, verify fresh consent, check sensitivity
   against the chosen provider, verify necessity.
4. **Every tool call is an episode.** Invocation, arguments outcome,
   and user feedback become a row in `episodes` and an edge in the
   KG so the agent remembers and so audit is complete (Principle
   XII).
5. **User is always in the loop.** No autonomous writes. No
   self-chaining beyond the confirmed plan.

---

## Agent memory taxonomy

The agent has four memory layers, each living in its natural
representation:

1. **Session state (Postgres JSONB, high churn).** `agent_state`
   table (spec 006). Opened when the user invokes the agent, closed
   on completion or timeout. Holds in-flight reasoning state,
   intermediate results, pending confirmations. Not subject to KG
   constraints; ciphertext at rest.
2. **Long-term patterns (KG `Pattern` nodes, low churn).** Recurring
   plan shapes get promoted to `kg_nodes(type='Pattern')` with
   usage stats (`agent_patterns` view in spec 006). Future planning
   prefers patterns with positive reinforcement history.
3. **Plans (own table with explicit state machine).** `plans` table
   (spec 006) with status column
   (`pending|running|awaiting_user|succeeded|failed|cancelled`) and
   `outcome_episode_id` linking back to the executed action.
4. **Skill registry (own table with usage stats).** `skills` and
   `skill_usage` tables (spec 006). Installed AppFunctions, their
   schemas, versions, user-set defaults, per-skill success/latency
   rolled up for planner heuristics.

---

## Functional Requirements

### Process and isolation

- **FR-008-001**: The agent MUST run in a dedicated `:agent` process.
  `:agent` MUST NOT have direct network access; all outbound LLM
  and storage calls route through `:net`.
- **FR-008-002**: `:agent` MUST read profile subgraph, session
  state, patterns, and skills from local caches + Orbit Cloud (via
  `:net`), never directly from `:ml`.

### Invocation

- **FR-008-003**: Bubble long-press MUST open the agent input sheet
  and accept voice or text input.
- **FR-008-004**: Ask Orbit (spec 004) MUST be able to hand off a
  query to the agent.
- **FR-008-005**: The agent MUST NEVER execute a tool without an
  explicit user tap on a plan confirmation affordance.

### Planning and execution

- **FR-008-006**: The agent MUST produce a human-readable plan
  enumerating intended tool calls, their arguments, and expected
  effects before any execution.
- **FR-008-007**: Tool invocation MUST go through the AppFunctions
  API (spec 003 FR-003-007). On-device only.
- **FR-008-008**: Every tool invocation MUST write an `episode`
  (spec 006) and an outcome edge in the KG (spec 007). Success,
  failure, cancellation, and undo all become episodes.

### Prompt assembly and consent

- **FR-008-009**: Every LLM prompt the agent sends to `:net` MUST
  be assembled inside `:agent` and MUST pass the Principle XI
  four-point consent filter: strip `local_only` facts; verify
  consent freshness; check sensitivity vs. provider policy;
  verify necessity.
- **FR-008-010**: The filter MUST be a hard gate; a failed check
  returns the prompt to the planner to retry with redactions or
  fall back to Nano.

### Memory

- **FR-008-011**: Session state MUST be written to `agent_state`
  (spec 006) and ciphertext at rest per spec 006 FR-006-006.
- **FR-008-012**: Patterns promoted from session state MUST become
  `kg_nodes(type='Pattern')` (spec 007) and MUST carry episode
  references per Principle XII.
- **FR-008-013**: Plans MUST persist to the `plans` table with the
  state-machine column, and MUST link outcomes via
  `outcome_episode_id`.
- **FR-008-014**: Skill registry and skill usage MUST persist to
  `skills` and `skill_usage` (spec 006); planner heuristics consume
  usage stats.

### User controls

- **FR-008-015**: Settings → Agent MUST expose: enable/disable agent;
  list and delete patterns; list skills and set defaults; clear
  session state; revoke cloud sync for agent memory.
- **FR-008-016**: Every agent action MUST be undo-able for at least
  24 hours via the Diary's action card undo affordance; undo writes
  a corrective episode and negative-weight edge.

---

## Dependencies

- 002 complete
- 003 AppFunctions registration (FR-003-007 and onward)
- 005 Orbit-managed LLM proxy (long reasoning path)
- 006 Orbit Cloud schema (agent_state, plans, skills, skill_usage,
  agent_patterns view)
- 007 Knowledge graph + profile subgraph
- New process: `:agent` (manifest entry, AIDL boundaries)
- New: `com.capsule.app.agent.Planner`
- New: `com.capsule.app.agent.Executor`
- New: `com.capsule.app.agent.ConsentFilter` (Principle XI
  implementation)
- New: `com.capsule.app.agent.PromptAssembler`

---

## Open Questions

- Planner architecture: single-pass chain-of-thought (Nano) vs
  ReAct loop (cloud-augmented). Leaning Nano-first with cloud
  fallback for plans > 3 steps.
- Pattern promotion threshold: 3 confirmed uses? 5? Make
  configurable, default 3.
- When the consent filter blocks a prompt, do we silently retry
  with redactions or show the user what got blocked? Leaning
  transparent — surface a "redacted because…" note so users can
  correct sensitivity tags.
- **v1 precursor → v1.2 evolution path.** v1's cluster-suggestion
  card uses hardcoded actions (Summarize, Open All, Save as
  Structured List). In v1.2, do those actions get refactored into
  AppFunctions (clean architecture but more code shuffling) or do
  they coexist as templated shortcuts that the agent can also call
  (less refactor, more surface area)? Leaning refactor for
  consistency — every action through the AppFunctions API.
- **Cluster nodes vs. Pattern nodes.** v1's cluster nodes
  (research-session, task, etc.) live in the KG already. v1.2 adds
  Pattern nodes for recurring plan shapes. Are clusters a kind of
  Pattern, or a separate node type that Patterns can reference?
  Leaning separate types with explicit edges between them
  (Pattern -[derives_from]-> Cluster).
- **Conversational follow-up after a cluster-suggestion action
  (forward flag).** After the user taps *Summarize* and the
  3-bullet output appears, can they ask *"make it shorter"* or
  *"focus on the technical aspects"*? In v1, no — the action runs
  once and stops. In v1.2, yes — the agent's planner handles
  conversational follow-up natively. Question for v1.2 design:
  does the cluster-suggestion-card surface accept follow-up turns,
  or do follow-ups bubble up into the agent invocation surface?
  Probably the former (stay in context), but the planner ergonomics
  matter.
- **Cluster-type registry (forward flag).** v1 ships with one
  cluster type required (research-session) and one or more
  conditional stretch types (task, shopping, meeting prep, travel).
  v1.2 should let third-party apps or user power-customizations
  register new cluster types via an extensible registry — e.g.,
  someone building a workout-tracking app could register a
  *workout-session* cluster type that produces *Add to Habit*
  / *Plan Recovery Day* actions. Registry mechanism is v1.2 work,
  but v1's cluster_type column should NOT be a closed enum; use a
  string with a known-set hint.
- **Explain-why capability (forward flag).** When a card surfaces
  *"You had a research session about X — 4 captures across 3 apps,"*
  the user might want to tap to see *which* 4 captures and *why* they
  were clustered. v1's cluster nodes already carry the constituent
  envelope_ids and confidence score in the KG, so the data is
  there. v1.2 surfaces this as a "see why" affordance on the card
  body. v1 doesn't render it, but data structure must support it.

---

*To be fleshed out into a full speckit spec after v1.1 ships.
Constitutionally bounded by Principles V, IX, X, XI, XII.*
