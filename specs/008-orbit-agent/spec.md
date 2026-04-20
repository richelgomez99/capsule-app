# Orbit Agent (v1.2)

**Status**: STUB — full PRD to be drafted after v1.1 stabilizes
**Target release**: v1.2
**Depends on**: spec 002, spec 003 (Orbit Actions as AppFunctions), spec 005 (LLM routing), spec 006 (Orbit Cloud), spec 007 (Knowledge Graph)
**Governing document**: `.specify/memory/constitution.md` — implements Principles I, V, IX, X, XI, XII
**Created**: 2026-04-20

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

---

*To be fleshed out into a full speckit spec after v1.1 ships.
Constitutionally bounded by Principles V, IX, X, XI, XII.*
