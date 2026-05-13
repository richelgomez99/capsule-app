# Orbit Agent Architecture Round 1 - 2026-05-12

This is Round 1 of the deeper replanning sequence. It does not change app code and does not yet decide final branch/task order. The goal is to reason from the user's perspective through the logic of the Orbit agent, identify the first working product slice, and name the architecture/stack decisions that later Speckit planning must turn into specs, tasks, and branches.

Related docs:

- [Product Roadmap Audit - 2026-05-12](product-roadmap-audit-2026-05-12.md)
- [Capture Understanding Stack Research - 2026-05-12](capture-understanding-stack-research-2026-05-12.md)
- [Agent Stack Research Pass - 2026-05-12](agent-stack-research-2026-05-12.md)
- [Agent Stack POC Research - 2026-05-12](agent-stack-poc-research-2026-05-12.md)
- [Agent Stack Landscape Research - 2026-05-12](agent-stack-landscape-research-2026-05-12.md)
- [Capture Source Identity Plan](capture-source-identity-plan.md)
- [Orbit Agent Architecture Round 2 - 2026-05-12](orbit-agent-architecture-round-2-2026-05-12.md)
- [specs/008-orbit-agent/spec.md](../specs/008-orbit-agent/spec.md)

## Round 1 conclusion

Orbit should not start by shipping a general-purpose agent. It should start by making captures become understood, retrievable, and actionable objects. The agent emerges from those primitives.

The first great product is not:

> Talk to an AI that can maybe use your screenshots.

The first great product is:

> Save anything. Orbit understands what it can prove, shows why it matters, connects related saves, and helps you close the next loop when you ask.

That means the immediate planning priority is not a full planner/executor loop. It is the memory-quality ladder:

1. Capture and source identity.
2. Evidence-backed summary.
3. Capture detail as the main insight surface.
4. Related captures with explainable reasons.
5. User feedback and corrections.
6. Ask Orbit over the corpus with citations.
7. Suggested actions as drafts/previews.
8. Confirmed execution later.

The agent should be visible before it is powerful. It should feel useful as soon as it can answer, summarize, connect, and draft, even if it cannot yet execute external actions.

## Product stance

Orbit is an agentic memory system for closing loops, but the app must still feel like a quiet capture/Diary product. The user should not feel like they are operating an automation platform.

User-facing promise:

1. **I saved this. What is it?** Orbit identifies source, title, provider, summary, and evidence.
2. **Why did I save this?** Orbit preserves notes, inferred intent, nearby context, and related captures.
3. **What else connects to this?** Orbit shows related captures with reasons and citations.
4. **What should I do with it?** Orbit proposes actions as drafts, lists, reminders, or plans.
5. **What did Orbit do?** Orbit shows fetches, model calls, summaries, failures, and memory changes in audit surfaces.
6. **Can I correct it?** User can edit summary/source/memory, reject relatedness, and set future behavior.

This product stance keeps the agent from becoming an opaque chatbot bolted onto a capture app.

## The user's mental model

The user should understand Orbit through five simple nouns:

| User noun | Product meaning | System object |
| --- | --- | --- |
| **Capture** | Something I saved. | IntentEnvelope + artifact. |
| **Understanding** | What Orbit could prove about it. | EvidenceBundle + CaptureUnderstanding. |
| **Connection** | Why this relates to other saves. | Relatedness edge + explanation. |
| **Loop** | Something this capture implies I might do. | CandidateAction + Resolution state. |
| **Memory** | Something Orbit should remember for next time. | Profile fact, pattern, conversation note, or feedback episode. |

Avoid making the user think in terms of crawlers, graphs, embeddings, workflows, functions, prompts, or providers. Those are implementation details.

## Core agent loops

The agent is not one loop. It is a set of loops that should be shipped in order.

### Loop 1: Understand a capture

Trigger:

- capture created;
- user opens capture detail;
- user taps `Get more context`;
- capture becomes relevant to an Ask Orbit answer or cluster.

Steps:

1. Normalize source identity.
2. Acquire evidence according to the user's understanding depth.
3. Produce evidence-backed summary.
4. Extract entities, candidate actions, and limitations.
5. Store provenance and audit entries.
6. Show summary and evidence state in capture detail.

Output:

- `CaptureUnderstanding` continuation.
- evidence IDs;
- limitations;
- candidate actions;
- model/extractor provenance;
- audit entry.

MVP bar:

- URL captures get title/source/summary when public content is available.
- Screenshot-only captures use visible OCR text or say why they cannot be fully understood.
- YouTube captures never pretend to summarize a video without transcript/audio evidence.

### Loop 2: Explain relatedness

Trigger:

- user opens capture detail;
- nightly/idle clustering;
- Ask Orbit asks for similar saves;
- a new capture looks like an existing open loop.

Steps:

1. Retrieve candidates by metadata, lexical search, embeddings, and KG relationships.
2. Rerank by intent, source, time, entity overlap, user notes, feedback, and resolution state.
3. Generate a short reason for each surfaced relation.
4. Ask for feedback only when the user interacts or dismisses.

Output:

- related captures;
- `why related` explanation;
- feedback chips: `useful`, `not related`, `wrong topic`, `not now`.

MVP bar:

- Show at most 3 related captures in detail.
- Explain with concrete evidence: `same article topic`, `same YouTube channel`, `same recipe ingredient`, `saved within 20 minutes`, `you asked about this topic yesterday`.
- Never surface vague `AI thinks these are similar` copy.

### Loop 3: Ask Orbit with citations

Trigger:

- user asks a question from Diary, detail, or a cluster.

Steps:

1. Classify query: search, summarization, comparison, planning, memory correction, or action request.
2. Retrieve evidence-backed captures.
3. Answer with citations and limitations.
4. Offer a next step when the answer implies a loop.
5. Save conversation outputs only when useful and explainable.

Output:

- answer;
- citations;
- optional follow-up chips;
- optional derived note or plan.

MVP bar:

- Answer only from user's corpus unless the user asks for web context.
- Every claim about saved captures links back to captures/evidence.
- If evidence is weak, ask a question or say `I do not have enough saved context yet`.

### Loop 4: Propose a next action

Trigger:

- capture contains an obvious date/task/list/request;
- user asks `what should I do with this?`;
- cluster suggests a reading list, grocery list, follow-up, or draft.

Steps:

1. Extract candidate action from evidence.
2. Show action preview with source citations.
3. Let user edit fields.
4. Save as a derived artifact or mark as dismissed.
5. Only later execute external writes through tools/AppFunctions with confirmation.

Output:

- draft reminder;
- draft calendar event;
- draft to-do;
- grocery/reading list;
- message draft;
- resolution event.

MVP bar:

- Do not execute external actions yet.
- Create local drafts/lists/plans first.
- Treat confirmation and dismissal as memory episodes.

### Loop 5: Promote memory carefully

Trigger:

- repeated accepted suggestions;
- user correction;
- explicit `remember this`;
- repeated Ask Orbit context;
- pattern becomes useful for ranking.

Steps:

1. Decide whether the event is a fact, preference, pattern, or one-off note.
2. Attach provenance.
3. Ask before promoting identity/profile facts when confidence is low or sensitive.
4. Let user inspect/edit/delete memory.

Output:

- profile fact;
- pattern;
- preference;
- rejection rule;
- domain/source policy.

MVP bar:

- Do not silently infer identity.
- Start with explicit user notes and feedback episodes.
- Promote patterns only after repeated evidence and acceptance.

## Agent state machine

The agent should have boring, auditable states.

| State | Meaning | User visibility |
| --- | --- | --- |
| `idle` | No agent work active. | None. |
| `queued` | Background understanding/retrieval job waiting. | Subtle pending state on capture. |
| `acquiring_evidence` | Fetch/OCR/document/transcript work. | `Getting source context...` only if user is watching. |
| `understanding` | Summary/entity/action extraction. | `Summarizing...` or quiet background. |
| `ready` | Understanding available. | Summary/evidence shown. |
| `needs_user_context` | Agent needs a clarification before making a useful claim. | Lightweight question. |
| `suggesting` | Relatedness/action suggestion generated. | Card/chip/suggestion. |
| `drafting` | Creating a plan/list/message/reminder draft. | Preview surface. |
| `awaiting_confirmation` | External write or persistent plan needs approval. | Approval sheet. |
| `executing` | Future AppFunction/tool execution after approval. | Explicit progress and cancel/undo where possible. |
| `resolved` | Loop closed, dismissed, snoozed, or saved for later. | Resolution marker. |
| `failed_limited` | Could not fetch/understand enough. | Limitation label and retry/get-more-context action. |

State transitions should write audit events. The user does not need to see every state, but the app should be able to explain them.

## User interaction model

### First screen: Diary

Diary remains the home surface. It should feel like an organized memory stream, not a dashboard.

MVP Diary card should show:

- source/provider glyph;
- title or best available label;
- short summary or limitation;
- note/intent marker if present;
- duplicate/already-saved status when relevant;
- open loop marker only when meaningful;
- one quiet affordance for `More` or detail.

Avoid on Diary cards:

- visible model/provider names;
- long explanation text;
- too many chips;
- proactive action cards with weak confidence.

### Capture detail: the main insight surface

Capture detail is where the agent becomes visible without becoming a chat app.

Recommended layout:

1. **Source header**: provider, app origin, time, URL/domain, evidence freshness.
2. **Original artifact**: screenshot/text/link preview/note.
3. **Understanding**: summary, limitations, evidence label, refresh/get-more-context.
4. **Why it may matter**: user note, inferred intent, action-like evidence.
5. **Related**: 1-3 related captures with `why related`.
6. **Possible next steps**: local drafts first, external actions later.
7. **Memory and feedback**: wrong source, wrong summary, not related, not now.
8. **Audit drawer**: what Orbit did for this capture.

This surface should be quiet but dense. It should not use a giant hero treatment. The user came to inspect a saved object.

### Ask Orbit view

Ask Orbit should be a scoped memory interface, not a blank general chatbot.

Entry points:

- global Diary search/ask;
- capture detail: `Ask about this`;
- cluster: `Ask about these`;
- action draft: `Help me refine this`.

Behavior:

- default scope is user's captures;
- web search is explicit or governed by Understanding depth;
- answers cite captures/evidence;
- action requests become previews, not silent execution;
- conversation outputs can become notes, plans, profile facts, or corrections.

### Suggested actions

Actions should start as local previews.

Good first action families:

- reading list from a research cluster;
- grocery list from recipes/food captures;
- calendar event draft from tickets/events/travel screenshots;
- to-do draft from notes/screenshots;
- message draft from captures and user-provided context.

Do not start with external execution. Start with `Save draft`, `Copy`, `Mark done`, `Dismiss`, and later `Send/Add` after AppFunctions/permissions mature.

### Profile and memory inspector

This can be basic at first, but it must exist before deep personalization feels trustworthy.

Sections:

- `Facts Orbit knows`;
- `Patterns Orbit noticed`;
- `Sources/domains Orbit should avoid`;
- `Dismissed suggestions`;
- `Cloud and understanding depth`;
- `Delete/export`.

MVP can be read-only plus delete for profile facts/patterns, then later richer editing.

## Agent architecture

### Conceptual services

| Service | Responsibility | Ship timing |
| --- | --- | --- |
| Capture/Envelope | Device capture, artifact, source state, user note. | Exists / near-term. |
| Continuation Engine | Local async job orchestration for hydration and summaries. | Exists / expand. |
| Evidence Acquisition | Server-side public fetch, managed extraction, browser fallback, OCR/VLM, docs/transcripts. | Round 2+ POC. |
| Understanding Service | Summary/entity/action extraction with evidence contract. | MVP. |
| Retrieval Service | Lexical/vector/KG retrieval over captures/evidence. | MVP-ish. |
| Relatedness Service | Reranking and `why related` explanations. | Early alpha. |
| Ask Service | Cited answers over the corpus. | Alpha. |
| Planner Service | Converts user/capture intent into draft plans/actions. | After Ask works. |
| Executor Service | AppFunctions/external writes after confirmation. | Later. |
| Memory Service | Profile facts, patterns, feedback, corrections. | MVP skeleton, grow gradually. |
| Audit/Trace Service | User-readable audit and developer observability. | MVP. |

### Control flow

```text
Capture saved
  -> source identity
  -> basic continuation
  -> evidence bundle(s)
  -> capture understanding
  -> embeddings/retrieval index
  -> optional KG episode
  -> detail/Diary update
  -> user feedback
  -> memory/pattern update
```

For Ask/action:

```text
User asks or taps action
  -> classify intent
  -> retrieve cited evidence
  -> answer OR draft plan
  -> if action: preview/edit/confirm
  -> if external write: consent gate
  -> execute later through tool/AppFunction
  -> outcome episode + resolution update
```

### Data contracts that should exist before big agent work

1. `CaptureSourceIdentity`
2. `EvidenceBundle`
3. `CaptureUnderstanding`
4. `RelatedCaptureExplanation`
5. `CandidateAction`
6. `ResolutionState`
7. `AgentConversationTurn`
8. `ProfileFact`
9. `Pattern`
10. `AuditEvent`

These should be designed as contracts before implementation branches proliferate.

## Stack implications from Round 1

### Android

Keep Android simple:

- `:capture` gathers artifact and source state, no network.
- `:ml` handles local OCR/embedding/classification where appropriate.
- `:net` remains sole egress.
- Future `:agent` should not be rushed until planner/executor exists; early agent-like behavior can be continuation + UI + cloud service.
- Settings should expose understanding depth, pause, local/cloud kill switch, audit, and memory inspector, not vendor details.

### Cloud/product plane

Use Supabase/Postgres for product records and control plane:

- captures;
- continuation jobs;
- evidence metadata;
- summaries;
- candidate actions;
- profile facts/patterns;
- audit events;
- cost and status pointers.

Use pgvector first. Add Qdrant only after evals show pgvector quality/latency is not enough.

### Agent/workflow runtime

Do not choose the final agent runtime yet. POC the same scenario in the contenders.

Context7/doc findings from this round:

- **Mastra**: strong TypeScript fit; agents, memory, vector recall, working memory, workflows, and human approval through workflow `suspend`/`resume`. Best fit if Orbit wants one TypeScript cloud service and fast product iteration.
- **LangGraph JS**: strongest explicit graph and interrupt semantics; examples show tool calls and review/approval steps pausing with `interrupt`, then resuming via `Command`. Best fit if we need precise state machines and human-in-the-loop control.
- **Pydantic AI**: strong typed output/dependency injection/tool model; docs show structured output, durable execution wrappers through Prefect/Restate, and deferred tool calls. Best fit for typed extractors/planners if a Python service is acceptable.

Round 1 recommendation:

- Keep **Mastra vs LangGraph JS** as the first runtime POC because the existing cloud direction is TypeScript/Vercel/Supabase-friendly.
- Use **Pydantic AI** as a challenger for typed extraction/planning if Python Graphiti/Unstructured service already exists.
- Do not let framework memory become canonical memory. Orbit's product memory must stay in Orbit-owned records/KG/evidence contracts.

### Model gateway and observability

All agent-related LLM calls should route through the gateway with capability labels:

- `capture_summary`
- `entity_extract`
- `action_extract`
- `relatedness_explain`
- `ask_orbit_answer`
- `agent_plan_draft`
- `profile_question`
- `memory_promote_candidate`

Langfuse traces should record IDs, costs, latency, capability, model, failure class, and eval scores without raw content by default.

## MVP boundaries

### Not MVP

These should wait:

- autonomous scheduled agents;
- external writes to calendar/email/to-do apps;
- connected-account browser;
- CAPTCHA bypass;
- full AppFunctions integration;
- custom skill SDK;
- advanced profile editor;
- broad proactive notifications;
- multi-agent orchestration;
- full graph production rollout before deletion/isolation is proven.

### MVP 0: make captures understandable

Goal: the saved object becomes useful.

Required:

- source identity;
- static URL hydration;
- evidence-backed summary;
- limitations copy;
- basic capture detail insight section;
- feedback: wrong summary/source;
- audit entry per enrichment.

Success metric:

- user opens a capture and immediately knows what it was and why it was saved.

### MVP 1: make captures findable and connected

Goal: Orbit remembers across captures.

Required:

- search/filter;
- embedding or semantic retrieval baseline;
- related captures in detail;
- `why related` explanations;
- rejection feedback;
- simple clusters for research/session.

Success metric:

- user can recover something saved earlier without scrolling or guessing source app.

### MVP 2: Ask Orbit over saved memory

Goal: conversation becomes a retrieval and synthesis interface.

Required:

- Ask Orbit entry point;
- scoped retrieval;
- cited answers;
- conversation turns stored as episodes when useful;
- follow-up chips;
- refusal/uncertainty when evidence is thin.

Success metric:

- user asks about their saved corpus and gets a useful cited answer.

### MVP 3: draft actions, do not execute yet

Goal: Orbit helps close loops locally.

Required:

- candidate action extraction;
- draft reading list/grocery list/to-do/calendar/message;
- edit/confirm/save/dismiss;
- resolution state;
- feedback as memory.

Success metric:

- user turns a capture or cluster into a useful draft/action artifact without leaving Orbit.

### MVP 4: confirmed execution

Goal: external action with trust.

Required:

- AppFunctions/tool registry;
- planner/executor state machine;
- human approval sheet;
- audit;
- undo/failure handling;
- local/cloud consent filter.

Success metric:

- user confirms an external action and trusts exactly what happened.

## When to start collecting users

Do not wait for full agent execution. Start collecting users once the core capture memory loop works.

Recommended gates:

### Internal dogfood

Start now/soon, once build stability is restored.

Need:

- reliable capture;
- no duplicate chaos;
- Settings pause/audit surfaces not embarrassing;
- basic URL hydration;
- export/delete path credible enough for internal users.

Purpose:

- collect real capture distribution;
- identify source identity failures;
- build the capture-understanding eval dataset;
- learn which captures users actually reopen.

### Private alpha

Start after MVP 0 plus early MVP 1.

Need:

- capture detail with understanding;
- source identity good enough for top apps/sites;
- safe cloud controls;
- audit log;
- basic feedback;
- no overclaiming summaries.

Purpose:

- validate whether users trust summaries;
- learn what `Get more context` means in practice;
- measure retrieval/relatedness demand before full agent.

### Public beta

Start after MVP 2 or very strong MVP 1.

Need:

- Ask Orbit with citations or excellent search/detail;
- clear cloud/privacy story;
- deletion/export;
- cost controls;
- stable onboarding and settings;
- eval-backed summary quality.

Purpose:

- prove Orbit is more than capture storage;
- prove the agentic memory thesis before external action risk.

### Full agent users

Wait until MVP 4.

Need:

- planner/executor;
- AppFunctions/tool permissions;
- approval UI;
- durable workflow;
- action audit;
- recovery/undo.

Purpose:

- close loops outside Orbit.

## Priority order before Speckit replanning

This is the Round 1 proposed priority spine. Later rounds can revise it.

1. Stabilize current build/branch stack and land 015-017 cleanup.
2. Refresh README/cloud truth so product promises stop conflicting.
3. Define shared contracts: source identity, evidence, understanding, relatedness, candidate action, audit event.
4. Implement capture detail as the core insight surface.
5. Strengthen URL hydration and summary limitations.
6. Add understanding depth setting and per-capture overrides.
7. Build capture-understanding eval dataset from dogfood captures.
8. POC managed extraction/browser/doc/transcript adapters.
9. Add semantic retrieval and related capture explanations.
10. Add feedback episodes and memory/pattern skeleton.
11. Add Ask Orbit with citations.
12. Add local action drafts and resolution state.
13. POC agent runtime for confirmed execution.
14. Add AppFunctions/external writes after approval.

## What the app should look like

The app should remain restrained, not magical-confetti AI.

### Diary

- dense but calm;
- source-first cards;
- summaries only when useful;
- no huge hero sections;
- not a chat-first interface;
- action/proactive cards only at high confidence.

### Capture detail

- the most important new screen;
- original artifact and AI understanding side by side in hierarchy;
- evidence labels and limitations visible but not noisy;
- related captures with reasons;
- action drafts as compact sections;
- feedback affordances always nearby.

### Ask Orbit

- scoped entry, not blank chatbot island;
- starts from `Ask about my captures`, `Ask about this`, `Ask about these`;
- answer citations are first-class;
- follow-up actions are previews.

### Settings

- fewer broad controls, not dozens of toggles;
- Understanding depth: Basic/Smart/Deep;
- Pause Orbit;
- local/cloud AI kill switch;
- cloud visual understanding;
- never-fetch domains;
- What Orbit did today;
- memory inspector.

### Audit

- human-readable timeline: fetched URL, summarized capture, used cloud model, failed due to auth, created relatedness suggestion;
- not developer logs;
- enough detail for trust and debugging.

## Key unresolved questions for later rounds

Round 2 should focus on capture understanding and retrieval contracts:

- exact schemas for `EvidenceBundle`, `CaptureUnderstanding`, `RelatedCaptureExplanation`, and `CandidateAction`;
- how to store raw/cleaned evidence and deletion links;
- first eval dataset definition;
- provider/source identity resolver design;
- top app/domain coverage list.

Round 3 should focus on memory and retrieval:

- Graphiti/Zep/Mem0/Supabase-only POC scenario;
- profile facts vs patterns vs clusters;
- negative feedback model;
- memory inspector UX;
- deletion/export across derived memory.

Round 4 should focus on Ask Orbit and action drafts:

- Ask retrieval pipeline;
- answer citation format;
- action extraction schemas;
- resolution semantics;
- draft surfaces;
- when to start AppFunctions/external execution.

Round 5 should focus on Speckit replanning:

- which specs become obsolete;
- which specs get amended;
- which branches should exist;
- dependency order;
- MVP/user-collection milestones;
- test and eval gates.

## Round 1 recommendation

Do not build the agent first. Build the trustworthy substrate the agent will stand on.

Immediate product architecture:

1. **Capture detail becomes the center of gravity.** It is where summary, evidence, relatedness, actions, and feedback converge.
2. **Understanding depth becomes the user control for enrichment.** Basic/Smart/Deep is simpler than per-vendor settings.
3. **Every claim gets evidence.** Evidence IDs and limitations are mandatory from the first cloud summary work.
4. **Feedback is a product primitive.** Wrong summary/source/relatedness is not polish; it is agent training data.
5. **Actions start as drafts.** External execution waits until trust, tools, and durable approval are ready.
6. **Runtime POC waits until the scenario is fixed.** Compare Mastra/LangGraph/Pydantic AI against the same Orbit flow, not abstract examples.

The phase order should optimize for the first moment a user says: `Oh, this actually remembered what I meant.` That moment comes from evidence-backed understanding and useful retrieval before it comes from autonomous action.
