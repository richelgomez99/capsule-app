# Agent Stack Research Pass - 2026-05-12

This document is a first online research pass for the cloud/agent/knowledge stack implied by Orbit's product direction. The premise has changed: Orbit should not market itself as privacy-first or local-only. The product promise is stronger and harder:

Follow-up research: [Agent Stack POC Research - 2026-05-12](agent-stack-poc-research-2026-05-12.md), [Agent Stack Landscape Research - 2026-05-12](agent-stack-landscape-research-2026-05-12.md), and [Capture Understanding Stack Research - 2026-05-12](capture-understanding-stack-research-2026-05-12.md).

> Orbit should know what a capture was, why the user saved it, what it relates to, whether it matters now, and what the user can do next.

That promise requires cloud LLMs, a cloud knowledge layer, traceability, observability, cost controls, and a careful product contract around what Orbit knows and does not know.

## Research sources checked

Web research was run through Exa via AgentCash, plus primary docs fetched directly. The useful sources were:

- Graphiti/Zep overview and Zep temporal KG paper: `https://help.getzep.com/graphiti/getting-started/overview`, `https://arxiv.org/html/2501.13956v1`
- Letta stateful agent and memory docs: `https://docs.letta.com/guides/core-concepts/stateful-agents/`, `https://docs.letta.com/letta-code/memory`
- Langfuse docs: `https://langfuse.com/docs`
- LiteLLM docs: `https://docs.litellm.ai/docs/`
- LangGraph product/docs entry: `https://www.langchain.com/langgraph`
- Mastra docs: `https://mastra.ai/docs`
- Mem0 docs: `https://docs.mem0.ai/overview`
- Braintrust docs: `https://www.braintrust.dev/docs`
- Temporal docs: `https://docs.temporal.io/`

The Exa comparison results consistently named these categories:

- Memory: Letta, Zep/Graphiti, Mem0, LangMem.
- Temporal KG: Graphiti/Zep, sometimes Neo4j/Kuzu/FalkorDB as backing graph stores.
- Orchestration: LangGraph, Temporal, Inngest, Mastra.
- Observability/evals: Langfuse, LangSmith, Arize Phoenix, Braintrust.
- Gateway/cost routing: LiteLLM, Vercel AI Gateway/AI SDK, provider-native gateways.

## Product requirements the stack must satisfy

Orbit needs a stack that supports these requirements:

1. **Capture understanding**: summaries, extracted entities, actions, source identity, notes.
2. **Cited retrieval**: every answer and related-capture suggestion must link back to source captures/episodes.
3. **Temporal memory**: facts change over time; user stage and relevance horizon matter.
4. **Sparse-data humility**: agent asks questions instead of assuming identity/profile.
5. **Pattern learning**: accepted/rejected clusters/actions become memory.
6. **Action planning**: calendar events, to-dos, grocery lists, reading lists, message drafts.
7. **Human confirmation**: no silent external writes.
8. **Traceability**: every LLM call, retrieval, ranking decision, tool proposal, and tool call has a trace.
9. **Cost control**: per-user/model/capability budgets, caching, routing, fallbacks.
10. **User controls**: memory inspector, deletion/export, cloud kill switches, per-capability controls.
11. **Android-friendly API**: mobile client talks to a narrow cloud API; no agent complexity leaks to Android.

## Recommendation in one sentence

Use **Supabase Postgres/pgvector as the product data/control plane**, evaluate **Graphiti as the temporal knowledge layer**, use **Langfuse as the observability/eval spine**, keep **LiteLLM or Vercel AI Gateway as the model gateway**, and avoid committing to Letta as the core knowledge source until we prove it can fit Orbit's provenance and editability model.

## Stack by layer

### 1. Product data and identity

Recommended now:

- Supabase Auth.
- Supabase Postgres.
- pgvector for embeddings.
- Existing RLS smoke tests and migrations.

Why:

- Already in the repo via specs 013/014.
- Fits Android auth and Edge Function path.
- Good enough for capture metadata, audit metadata, user/account/device tables, plans, settings, and initial vector search.

Caveat:

- Constitution currently says RLS-only is not enough for long-term Orbit Cloud storage isolation. We must decide whether shared-table Supabase is alpha bootstrap or whether the constitution changes. For the knowledge layer, schema-per-tenant may still be the target.

### 2. Knowledge and long-term memory

Primary candidate: **Graphiti**.

Why it fits Orbit:

- Built for dynamic, temporal knowledge graphs.
- Ingests episodes incrementally.
- Maintains historical context and temporal edge metadata.
- Supports custom entity types.
- Uses hybrid semantic, full-text, and graph search.
- The Zep paper's model maps cleanly to Orbit's desired memory ladder: episodes, semantic entities, communities/patterns.

Best use in Orbit:

- Treat captures, conversations, action confirmations, dismissals, and corrections as episodes.
- Extract entities and relationships from episodes.
- Store temporal/provenance-backed relationships.
- Retrieve related captures and explain why.
- Power profile facts and pattern promotion.

Open validation questions:

- Which graph backend should we use: Neo4j, FalkorDB, Kuzu, or another Graphiti-supported backend?
- Can we enforce per-user isolation cleanly?
- Can we support export/delete and provenance invalidation without custom surgery?
- Can Graphiti's entity extraction be made conservative enough for personal memory?
- What is ingestion cost per capture/conversation/action episode?

Alternative: **Zep managed platform**.

Pros:

- Managed temporal KG/memory layer.
- Less infra work.
- Strong fit for agent memory out of the box.

Cons:

- Vendor dependency for the core memory layer.
- Need to verify pricing, tenant isolation, deletion/export, data retention, and whether raw personal content leaves our controlled cloud boundary.
- May conflict with Orbit's need for a user-facing memory inspector and custom sensitivity/provenance model.

Alternative: **Mem0**.

Pros:

- Managed memory layer with vector + graph service positioning.
- Fast to prototype.

Cons:

- Looks more like managed agent memory than Orbit's source-of-truth KG.
- Need to verify editability, provenance, temporal relationships, deletion/export, and local/control-plane integration.

Alternative: **Letta**.

Letta is interesting, but probably not the primary knowledge layer.

Pros:

- Strong stateful agent model.
- Agents, conversations, memory, runs, steps, and tools are persisted.
- Explicit memory operations and self-editable memory are valuable design references.
- Letta Code's MemFS idea, git-backed memory files, and reflection/sleeptime agents are useful inspiration for Orbit's memory inspector and consolidation jobs.

Cons:

- Letta is agent-centered, while Orbit's memory must be product-data-centered.
- Self-editing memory is dangerous unless every memory write becomes a proposed, provenance-backed, user-inspectable fact.
- Letta does not replace the need for a graph of captures/entities/patterns/actions.

Recommendation on Letta:

- Do **not** make Letta the source of truth for Orbit's knowledge layer yet.
- Consider Letta later for agent session management or as a POC for conversational memory behavior.
- Borrow concepts: explicit memory edits, runs/steps, conversations attached to agents, memory audit/doctor, reflection jobs.

### 3. Retrieval and ranking

Recommended shape:

- Supabase/Postgres FTS for exact/lexical search.
- pgvector for semantic retrieval over captures, summaries, notes, and conversations.
- Graphiti graph retrieval for entity/profile/pattern-aware relevance.
- Reranking step before surfacing anything proactive.
- Every returned item must include a reason string and source evidence.

Ranking inputs:

- semantic similarity;
- keyword/full-text match;
- entity overlap;
- time window;
- provider/source;
- intent;
- notes;
- resolution state;
- user profile facts;
- accepted/rejected suggestions;
- explicit "not related" feedback;
- current conversation context.

Important product rule:

- Proactive surfacing needs a higher threshold than user-initiated search. Search can return broad results; agent suggestions must be conservative.

### 4. Agent orchestration

Candidate A: **LangGraph**.

Pros:

- Mature graph-based agent control.
- Explicit state, conditional routing, checkpointing, human-in-the-loop interrupts.
- Strong fit for visible plans and user confirmation.

Cons:

- Python-first. Introducing a Python service may be fine for agent backend, but it is another runtime alongside Android/Kotlin and TypeScript Edge functions.
- LangSmith is the most natural observability path, but we likely prefer Langfuse for self-hosting and framework neutrality.

Candidate B: **Mastra**.

Pros:

- TypeScript-native.
- Fits the existing Vercel/Supabase/Edge Function direction better than Python.
- Has agents, workflows, memory, tools, evals, and framework integrations.
- Potentially faster to integrate if the backend stays TypeScript.

Cons:

- Newer ecosystem than LangGraph.
- Need to verify durability, human-in-loop semantics, observability integration, and maturity.

Candidate C: **Temporal**.

Pros:

- Best-in-class durable execution.
- Excellent for workflows that must resume after crashes and can run for days.

Cons:

- Heavy for near-term Orbit.
- Probably premature until there are real multi-step, long-running plans and external actions.

Candidate D: **Inngest / Trigger.dev style serverless workflows**.

Pros:

- Easier than Temporal.
- Good fit for background jobs, async continuations, and serverless environments.
- Mastra can use Inngest-like durable workflows.

Cons:

- Need to validate complex human-in-loop plan pause/resume semantics.

Recommendation:

- Near term: implement typed `plans`, `plan_steps`, `tool_calls`, and `episodes` ourselves in Supabase/Postgres. Keep the agent service small and explicit.
- POC both **Mastra** and **LangGraph** against the same Orbit scenario before choosing.
- Use **Temporal** only if/when plan execution becomes long-running and mission-critical.

### 5. LLM gateway, model routing, and cost control

Current repo direction:

- Vercel Edge Function LLM gateway from spec 014.
- Provider-agnostic Android request envelope.
- Supabase JWT auth.
- No prompt logging.
- Audit metadata only.

Additional candidate: **LiteLLM**.

Why it is worth evaluating:

- Unified interface for 100+ LLMs.
- Self-hosted proxy gateway.
- Virtual keys, per-user/team budgets, spend tracking, rate limits.
- Built-in retries, fallbacks, caching, guardrails, and observability callbacks.
- Integrates with Langfuse and other observability systems.

Recommendation:

- Keep the current Vercel Edge Function path for immediate continuity.
- Evaluate LiteLLM as the underlying model gateway before scaling cloud usage.
- The eventual shape may be: Android -> Orbit API/Edge Function -> LiteLLM proxy -> providers, with Langfuse tracing and Supabase audit metadata.

Key constraint:

- Prompt content must not be persisted by gateway logs. Any gateway choice must support redaction, no-prompt retention, or self-hosting with storage controls.

### 6. Observability, evals, and traceability

Default pick: **Langfuse**.

Why:

- Open-source and self-hostable.
- OpenTelemetry-based, reducing lock-in.
- Traces LLM calls, retrieval, embeddings, API calls, sessions, users, and agent graphs.
- Prompt management and evaluation are built in.
- Can ingest traces via SDKs or gateways like LiteLLM.

Use Langfuse for:

- agent run traces;
- retrieval traces;
- LLM cost/latency;
- prompt versions;
- eval datasets;
- user feedback labels;
- debugging why a suggestion appeared.

Possible add-on: **Arize Phoenix**.

Why:

- Strong for RAG/embedding evaluation and drift detection.
- Useful once retrieval quality becomes central.

Possible add-on: **Braintrust**.

Why:

- Strong evaluation workflow and CI-style regression testing.
- Useful if Langfuse evals are not enough.

Recommendation:

- Start with Langfuse.
- Build Orbit-specific eval datasets early.
- Add Phoenix if retrieval/ranking becomes hard to debug.
- Add Braintrust only if we outgrow Langfuse eval workflows.

### 7. Eval datasets Orbit needs

Do this before full agent launch.

1. **Related capture evals**
   - Positive pairs: truly related captures with different wording.
   - Negative pairs: same keyword but unrelated meaning.
   - Hard cases: captures about "agents" as coding tools vs human agents vs travel agents.
2. **Sparse-data humility evals**
   - Given 3 startup captures, agent may ask a question but may not claim user is a founder.
   - Given one recipe save, agent may not assume grocery planning preference.
3. **Summary grounding evals**
   - Capture summary must only reference captured content.
   - If content is paywalled/unavailable, summary must say so.
4. **Action extraction evals**
   - Flight confirmation -> calendar event.
   - Recipe -> grocery list candidate.
   - Screenshot with tasks -> to-do candidates.
   - Ambiguous note -> ask clarifying question.
5. **Profile question evals**
   - Ask only when the answer would change ranking/action suggestions.
   - Do not ask repeatedly.
   - Store answer as provenance-backed profile fact.
6. **Agent plan evals**
   - Draft message plan must show recipient, source captures, proposed text, and confirmation step.
   - No external write without confirmation.

## Proposed architecture for Orbit POC

### Client

- Android app remains Kotlin/Compose.
- Android talks only to narrow Orbit APIs via `:net`.
- No agent framework runs on-device beyond local-mode fallback/classification.

### Cloud API

- Supabase Auth for identity.
- Vercel Edge Functions or a small Node/TypeScript service for request routing.
- Supabase Postgres for product data/control-plane records.
- pgvector for embeddings.
- Langfuse for traces/evals.
- LiteLLM evaluated as gateway under/behind the Edge Function.

### Knowledge service

- Graphiti POC service.
- Backing graph DB to be decided: Neo4j vs FalkorDB vs Kuzu.
- Ingest events from Supabase episodes queue.
- Return related captures, entities, and reason strings to Orbit API.

### Agent service

- POC both Mastra and LangGraph.
- Same test scenario for both:
  1. User opens a capture about coding agents.
  2. Agent retrieves related captures and explains why.
  3. Agent notices ambiguity: user might be founder, programmer, product person, or researcher.
  4. Agent asks one clarifying question.
  5. User answers.
  6. Agent stores an editable profile fact with provenance.
  7. Agent drafts a follow-up or to-do based on two captures.
  8. User confirms or rejects.
  9. Full trace appears in Langfuse.

## Cost-control strategy

1. Use cheaper models for extraction/classification.
2. Use stronger models only for synthesis/planning when needed.
3. Cache URL hydration and summaries by canonical hash.
4. Cache embeddings and extraction results.
5. Run batch enrichment on background schedules.
6. Set per-user/capability budgets at the gateway layer.
7. Log costs by capability: summary, extraction, relatedness, Ask Orbit, action planning.
8. Make proactive suggestions conservative to avoid wasting tokens on noise.

LiteLLM is attractive here because it has virtual keys, budgets, rate limits, spend tracking, fallbacks, and observability callbacks. Vercel AI Gateway may still be simpler early. The research task is to compare the two against Orbit's no-prompt-logging and per-user budget requirements.

## Initial stack recommendation

For the next implementation/design phase, assume this architecture unless a POC disproves it:

| Layer | Recommendation | Why |
| --- | --- | --- |
| Mobile | Existing Android/Kotlin | Keep agent complexity server-side. |
| Auth | Supabase Auth | Already aligned with specs 013/014. |
| Product DB | Supabase Postgres | Existing migrations, RLS tests, easy control plane. |
| Vector search | pgvector first | Good enough and already in Supabase. Add specialized vector DB only after scale evidence. |
| Knowledge graph | Graphiti POC | Best fit for temporal, episodic, provenance-backed memory. |
| Agent orchestration | POC Mastra vs LangGraph | Mastra fits TS stack; LangGraph is more mature for graph control. Decide with a real Orbit scenario. |
| Durable workflows | Inngest/Trigger-style first; Temporal later | Avoid Temporal heaviness until long-running workflows require it. |
| LLM gateway | Keep Vercel Edge now; evaluate LiteLLM | LiteLLM may solve budgets/fallbacks/observability better. |
| Observability | Langfuse | Self-hostable, OTel, traces, prompt mgmt, evals, cost/latency. |
| RAG eval add-on | Phoenix later | Add when retrieval drift/quality becomes hard. |
| Eval CI add-on | Braintrust later | Add if Langfuse evals are insufficient. |
| Agent memory product pattern | Borrow Letta concepts | Do not use Letta as source-of-truth KG yet. |

## Research plan beyond this pass

### Track A: Knowledge layer POC

Goal: prove whether Graphiti can be Orbit's knowledge layer.

Tasks:

1. Build a synthetic dataset of 100 captures:
   - coding agents;
   - startup stage/fundraising;
   - recipes/grocery;
   - calendar/travel;
   - message-drafting/follow-up;
   - same-keyword unrelated distractors.
2. Ingest captures as episodes.
3. Ingest notes, dismissals, and conversation answers as episodes.
4. Extract custom entity types: Person, Project, Topic, Company, Item, Event, Recipe, Task, Stage, Capture, Pattern.
5. Query related captures for 10 scenarios.
6. Require reason strings and source IDs.
7. Test deletion/invalidation of one source capture.
8. Measure ingestion latency/cost.
9. Measure retrieval latency/cost.
10. Decide if Graphiti is a fit or if a simpler Postgres schema is enough for v1.1.

### Track B: Agent orchestration POC

Goal: choose Mastra vs LangGraph vs custom typed service.

Tasks:

1. Implement the same Orbit scenario in Mastra and LangGraph.
2. Include human confirmation interrupts.
3. Include plan persistence.
4. Include user rejection and correction.
5. Include Langfuse tracing.
6. Compare developer speed, deploy complexity, trace quality, runtime cost, and data-control fit.

Decision rule:

- Pick Mastra if TypeScript integration and workflow ergonomics are clearly better and tracing is adequate.
- Pick LangGraph if graph-control/human-in-loop maturity dominates and a Python service is acceptable.
- Stay custom if both frameworks obscure too much or fight Orbit's provenance model.

### Track C: Gateway/cost-control POC

Goal: decide Vercel Edge gateway vs LiteLLM proxy shape.

Tasks:

1. Route six existing LLM capabilities through current Edge Function.
2. Route the same calls through LiteLLM proxy.
3. Compare provider fallback, per-user budgets, no-prompt-log configuration, latency, prompt caching, cost attribution, and Langfuse integration.
4. Verify Supabase JWT auth and user/capability metadata survive the route.
5. Verify prompt redaction/no persistence.

### Track D: Observability/evals POC

Goal: prove we can trace and evaluate relevance.

Tasks:

1. Instrument one capture summary, one related-capture retrieval, one Ask Orbit response, and one action proposal.
2. Store trace IDs in local/cloud audit metadata.
3. Create eval datasets for relatedness, sparse-data humility, action extraction, and citation correctness.
4. Run evals in Langfuse.
5. Test whether Phoenix adds value for embedding/retrieval drift.

### Track E: Product UX research

Goal: make the agent useful without feeling presumptuous.

Tasks:

1. Prototype capture detail with summary, related captures, actions, and "why related".
2. Prototype one curious question card.
3. Prototype conversation anchored to a capture.
4. Prototype memory inspector for profile facts/patterns.
5. Test copy that avoids overclaiming sparse data.

## Open decisions

1. Does Orbit want a managed memory vendor at all, or should memory remain fully in our controlled cloud?
2. Is Graphiti's graph DB dependency acceptable operationally for an early app?
3. Do we want a Python agent service, or should backend stay TypeScript-first?
4. Does Langfuse Cloud meet prompt-retention constraints, or do we self-host from day one?
5. Is LiteLLM worth adding now, or is the current Vercel gateway good enough until usage grows?
6. How do users inspect and edit profile facts/patterns without creating a power-user mess?
7. What is the minimum quality bar for proactive surfacing?

## My current opinion

Do not use Letta as the knowledge layer. Letta is interesting for stateful agents and explicit memory editing, but Orbit's core memory is not an agent's scratchpad. It is product data: captures, episodes, provenance, entities, relationships, patterns, profile facts, plans, and user corrections. That wants a graph/data model we own.

Graphiti is the most conceptually aligned knowledge candidate because Orbit needs temporal, episodic, provenance-backed memory. Supabase/pgvector should remain the simple control plane and first retrieval layer. Langfuse should become the trace/eval spine early, because without observability the agent will become impossible to trust or improve.

The most likely winning stack is:

> Android + Supabase + pgvector + Graphiti + Langfuse + Vercel/LiteLLM gateway + either Mastra or LangGraph for agent plans.

But the next move should be a POC, not an architectural marriage. The POC must answer: can this stack reliably surface related captures and ask useful profile questions without hallucinating identity or wasting money?
