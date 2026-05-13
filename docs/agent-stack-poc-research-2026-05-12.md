# Agent Stack POC Research - 2026-05-12

This is the second research pass after [Agent Stack Research Pass - 2026-05-12](agent-stack-research-2026-05-12.md). The first pass answered "what stack should Orbit probably evaluate?" This pass answers "what could actually break the recommendation?"

A broader competitive landscape pass now lives at [Agent Stack Landscape Research - 2026-05-12](agent-stack-landscape-research-2026-05-12.md). The capture summarization and evidence acquisition layer is covered in [Capture Understanding Stack Research - 2026-05-12](capture-understanding-stack-research-2026-05-12.md). Memory and retrieval promotion policy is covered in [Orbit Agent Architecture Round 3 - 2026-05-12](orbit-agent-architecture-round-3-2026-05-12.md). Runtime and approval boundaries are covered in [Orbit Agent Architecture Round 4 - 2026-05-12](orbit-agent-architecture-round-4-2026-05-12.md). The Speckit-ready build map is covered in [Orbit Agent Architecture Round 5 - 2026-05-12](orbit-agent-architecture-round-5-2026-05-12.md).

The focus is tactical:

- Graphiti vs Zep for temporal knowledge memory.
- Graphiti deletion, tenant isolation, and graph backend risk.
- Langfuse trace/privacy/eval requirements.
- LiteLLM vs Vercel AI Gateway for model control.
- Mastra vs LangGraph as the agent/workflow runtime.

## Executive delta

The previous recommendation still holds, but with sharper gates:

1. **Graphiti remains the best open-source fit**, but deletion/invalidation must be treated as a hard POC gate, not an implementation detail.
2. **Zep managed is more credible than expected** for fast production because it gives tenant isolation, graph deletion APIs, dashboards, logs, and enterprise BYOC/BYOK/BYOM options. The tradeoff is vendor dependency plus credit-based ingestion cost.
3. **Langfuse should be adopted early**, but Orbit should use client-side masking/no-content traces by default. Server-side ingestion masking is useful but not sufficient by itself because unmasked events may hit the Langfuse ingestion bucket before worker masking.
4. **LiteLLM is stronger than Vercel AI Gateway for Orbit's control plane** because it has virtual keys, Postgres-backed spend tracking, budgets, multiple budget windows, rate limits, redaction, call IDs, and Langfuse/OpenTelemetry integration.
5. **Mastra deserves a real POC**, not just a footnote. It is TypeScript-native, has agents, workflows, suspend/resume, workflow state, memory, live evals, trace evals, and deployment through workflow runners. LangGraph is still the precision choice for explicit graph control and mature human-in-the-loop semantics.

## Sources checked in this pass

Primary docs:

- `https://help.getzep.com/graphiti/getting-started/overview`
- `https://help.getzep.com/graphiti/graphiti/installation`
- `https://help.getzep.com/graphiti/graphiti/custom-entity-types`
- `https://help.getzep.com/faq`
- `https://help.getzep.com/deleting-data-from-the-graph`
- `https://help.getzep.com/zep-vs-graphiti`
- `https://getzep.com/pricing`
- `https://mastra.ai/docs/agents/overview`
- `https://mastra.ai/docs/workflows/overview`
- `https://mastra.ai/docs/memory/overview`
- `https://mastra.ai/docs/evals/overview`
- `https://langfuse.com/self-hosting`
- `https://langfuse.com/self-hosting/security/data-masking`
- `https://langfuse.com/docs/observability/data-model`
- `https://langfuse.com/docs/observability/features/token-and-cost-tracking`
- `https://langfuse.com/docs/evaluation/experiments/datasets`
- `https://docs.litellm.ai/docs/proxy/virtual_keys`
- `https://docs.litellm.ai/docs/proxy/users`
- `https://docs.litellm.ai/docs/proxy/logging`
- `https://docs.litellm.ai/docs/observability/langfuse_integration`
- `https://vercel.com/docs/ai-gateway/framework-integrations/litellm`

Paid Exa research via AgentCash:

- Graphiti backend/deletion/tenant isolation.
- Zep managed deletion/tenant/pricing controls.
- Mastra vs LangGraph production orchestration.
- LiteLLM vs Vercel AI Gateway gateway controls.

## Decisions this research should force

### Decision 1: Graphiti OSS or Zep managed?

Use **Graphiti OSS** if:

- Orbit wants maximum control over graph storage and provenance.
- We can afford a Python graph service and graph database operations.
- We can verify deletion, export, tenant isolation, and provenance invalidation.
- We want custom Orbit entity/edge types without plan limits.

Use **Zep managed** if:

- We want faster production memory with less infra.
- Managed multi-tenant isolation, dashboards, logs, and retrieval performance matter more than complete control.
- Credit-based ingestion cost is acceptable.
- Enterprise BYOC/BYOK/BYOM is realistic later.

Current recommendation:

- POC Graphiti first because it keeps Orbit's core memory model under our control.
- Keep Zep managed as the fallback if Graphiti deletion/isolation/ops are too expensive.

### Decision 2: One graph per user, group IDs, or shared graph?

Do not start with a shared graph plus casual filters.

For POC:

- Use `user_id`/`group_id` as a strict namespace in every episode, entity, edge, and search call.
- Test cross-user leakage with intentionally similar captures.
- Consider one graph/database per test user if the backend supports it cleanly.

For production:

- The tenant boundary must be designed explicitly.
- A group filter is a useful application guard, but not automatically a sufficient isolation model for personal memory.

### Decision 3: Do traces contain content?

Default answer: **not in production**.

Use Langfuse for:

- trace IDs;
- model names;
- task/capability labels;
- token counts;
- cost;
- latency;
- retrieval counts;
- redacted reason strings;
- failure classes;
- source IDs/hashes;
- eval scores.

Avoid by default:

- raw user captures;
- raw prompts;
- raw model responses;
- full retrieved context;
- images/screenshots;
- provider URLs containing private tokens.

Allow full-content traces only for explicit internal test accounts or user-authorized debugging sessions.

### Decision 4: Vercel gateway or LiteLLM?

Current repo path uses Vercel Edge Function gateway. Keep it short term.

But LiteLLM should be POC'd because Orbit needs:

- per-user budget windows;
- per-capability routing;
- model fallback;
- model access groups;
- redaction/no message logging;
- call IDs for debugging;
- Langfuse/OpenTelemetry integration;
- Postgres-backed spend tracking.

Vercel AI Gateway is attractive for simplicity and Vercel-native deployment, but Orbit's need is not just "call models." It is "control, explain, budget, and audit model use per user and capability."

### Decision 5: Mastra or LangGraph?

POC both against the same Orbit scenario.

Use **Mastra** if:

- we keep backend TypeScript-first;
- agent plans can be expressed as workflows plus typed steps;
- suspend/resume is adequate for user confirmations;
- Langfuse tracing is straightforward;
- the developer loop is meaningfully faster.

Use **LangGraph** if:

- explicit graph state, interrupts, checkpointing, and complex conditional routing dominate;
- Python service is acceptable;
- we need more mature graph-control semantics than Mastra's workflow layer;
- future graph/KG work is Python-heavy anyway.

Do not decide from docs alone.

## Graphiti findings

### Graphiti strengths

Graphiti is conceptually aligned with Orbit:

- It is designed for dynamic, temporally aware knowledge graphs.
- It ingests text and structured JSON as episodes.
- It preserves provenance through episodic processing.
- It supports custom entity and edge types through Pydantic models.
- It supports hybrid semantic + BM25 search and graph-aware reranking.
- It supports alternative LLM providers beyond OpenAI.

The custom entity/edge type docs matter a lot. Orbit can define first-pass types such as:

- `Capture`
- `Topic`
- `Person`
- `Project`
- `Company`
- `Product`
- `Place`
- `Event`
- `Recipe`
- `Ingredient`
- `Task`
- `Question`
- `Preference`
- `ProfileFact`
- `Resolution`

And edges such as:

- `MENTIONS`
- `SAVED_FOR`
- `RELATED_TO`
- `CONTRADICTS`
- `SUPPORTS`
- `ACTION_FOR`
- `ASKED_ABOUT`
- `ANSWERED_BY`
- `REJECTED_AS_RELEVANT`
- `CONFIRMED_BY_USER`
- `INVALIDATED_BY_USER`

The docs explicitly recommend optional fields, careful field descriptions, atomic attributes, and balanced edge type maps. That matches Orbit's sparse-data humility requirement.

### Backend reality

Primary Graphiti installation docs list:

- Neo4j 5.26+; or
- FalkorDB 1.1.2+.

The Graphiti MCP server documentation found by Exa says FalkorDB is the default in its Docker Compose path and Neo4j is recommended for production/full-featured graph DB needs. Exa also surfaced references to a broader driver architecture and possible backends like Kuzu/Neptune, but the primary installation docs we fetched should be treated as the planning truth for now: **POC Neo4j and/or FalkorDB first.**

POC implication:

- Do not design around Kuzu/Neptune until verified directly in the current Graphiti release.
- Start with FalkorDB if setup speed matters.
- Start with Neo4j if operational maturity, tooling, and query/debugging matter.

### Multi-tenancy and isolation

Evidence found:

- Graphiti supports `group_id` filtering and group management in its MCP surface.
- Exa surfaced a merged Graphiti PR adding GraphID isolation support for FalkorDB multi-tenant architecture.
- Zep managed docs claim full graph isolation for user graphs and standalone graphs.

Risk:

- Graphiti OSS gives primitives, not a complete Orbit tenant model.
- Any shared graph design must prove every search/retrieval path is scoped.
- Multi-tenant correctness needs tests that try to leak similar data across users.

POC gate:

- Ingest the same named entities for two users with different meanings.
- Query both users with ambiguous prompts.
- Verify no entity, edge, episode, or reason string crosses boundaries.
- Verify group-scoped deletion only deletes one user's data.

### Deletion and invalidation

This is the biggest Graphiti risk.

Primary Zep graph deletion docs say:

- deleting an edge never deletes associated nodes;
- deleting a node cascades to connected edges;
- deleting an episode deletes edges/nodes only when no other episodes are associated;
- deleting an episode does not regenerate names or summaries of shared nodes;
- episode information may still exist inside shared node summaries;
- if an episode invalidates a fact and the episode is deleted, the fact remains marked invalidated;
- deleting a thread removes associated episodes and cascades using the same association rules.

Exa surfaced a Graphiti `remove_episode()` PR and an open issue about orphaned entities not being cleaned up when LLM extraction creates entities without `MENTIONS` relationships. That issue may be fixed later, but for Orbit this is enough to make deletion a hard validation gate.

Orbit implication:

- "Delete my capture" cannot only delete the capture row.
- It must delete the source artifact, derived summaries, embeddings, KG episodes, KG entity/edge associations, related trace references, and eval dataset links where applicable.
- If a derived shared node remains, Orbit must be able to explain why or regenerate it without deleted source content.

POC gate:

1. Ingest 100 synthetic captures.
2. Delete one capture that introduced a unique entity.
3. Delete one capture that contributed to a shared entity.
4. Delete a whole user.
5. Query the graph directly for orphaned entities, references to deleted episode UUIDs, stale summaries containing deleted text, and invalidated facts.
6. Fail the POC if deletion cannot be made explainable and testable.

### Product judgment on Graphiti

Graphiti is still the right first POC because Orbit's memory is not just chat memory. It is a provenance graph over captures, conversations, notes, plans, confirmations, corrections, and dismissals.

But Graphiti should not be accepted until it passes:

- tenant isolation tests;
- deletion/export tests;
- ingestion cost tests;
- retrieval quality tests;
- reason-string/citation tests;
- sparse-data humility tests.

## Zep managed findings

### Zep strengths

Zep managed gives a lot that Orbit would otherwise have to build:

- full multi-tenant isolation claims for user graphs and standalone graphs;
- built-in users, threads, and message storage;
- SDKs for Python, TypeScript, and Go;
- dashboard and graph visualization;
- API/debug logs;
- production retrieval performance claims;
- managed cloud path;
- enterprise BYOK, BYOM, and BYOC;
- SOC 2 Type II and HIPAA BAA availability on enterprise plans.

The Zep vs Graphiti docs are blunt:

- choose Zep for a turnkey enterprise platform;
- choose Graphiti for flexible OSS core when you are comfortable building/operating the surrounding system.

That is probably exactly the decision Orbit faces.

### Pricing/cost model

Zep pricing found:

- Flex: $125/month, 50,000 credits, then $25 per 10,000 credits, 600 RPM, 5 projects, 10 custom entity/edge types, 1 day API logs.
- Flex Plus: $375/month, 200,000 credits, then $75 per 40,000 credits, 1,000 RPM, 10 projects, 20 custom entity/edge types, 7 day API logs.
- Enterprise: custom credits, guaranteed rate limits, unlimited projects/entity/edge types, SOC 2/HIPAA, audit logs, 30+ day API logs, managed/BYOK/BYOM/BYOC.
- Free: 1,000 credits/month.
- Credits are consumed by episode size, in 350-byte increments.
- Storage/retrieval/users are described as unlimited on paid plans; ingestion/processing is what costs credits.

Orbit implication:

- A single screenshot summary plus metadata could become multiple episodes if modeled naively.
- Conversation memory can burn credits quickly.
- If Zep is used, we need an episode-compaction policy before production.

### Deletion and graph behavior

Zep managed docs are explicit enough to be useful:

- episode deletion exists;
- thread deletion exists;
- node and edge deletion exist;
- deleting a session/thread/capture does not necessarily scrub information from shared node summaries;
- shared graph facts may preserve invalidation state.

This is not necessarily bad. It is honest graph behavior. But it means Orbit still needs a deletion policy that distinguishes:

- delete source artifact;
- delete derived episode;
- delete derived unique facts;
- preserve shared facts that have other sources;
- regenerate summaries when deleted content might be embedded in node summaries;
- purge an entire user graph on account deletion.

### Product judgment on Zep

Zep managed is a credible fallback if the app needs faster cloud memory than Graphiti OSS can provide. It is not just generic memory SaaS; it is the managed version of the same general temporal KG direction.

The biggest downside is that Orbit's core product memory becomes vendor-dependent. That might be acceptable for an alpha if the value is speed, but it should be an explicit product/company decision.

## Langfuse findings

### Why it still fits

Langfuse gives Orbit the observability grammar it needs:

- traces for one operation/request;
- observations for individual steps such as generations, tool calls, and retrieval;
- sessions for multi-turn conversations/workflows;
- user IDs, session IDs, tags, metadata, releases, versions;
- token and cost tracking;
- datasets and experiment runs;
- production traces converted into eval cases;
- OpenTelemetry foundation.

This maps cleanly to Orbit operations:

- capture enrichment;
- source identity resolution;
- summary generation;
- KG ingestion;
- related capture retrieval;
- Ask Orbit answer;
- profile question proposal;
- action plan creation;
- user confirmation/rejection.

### Masking and privacy reality

Langfuse supports two masking approaches:

- Client-side masking: redact before data leaves Orbit's service.
- Server-side ingestion masking: Enterprise self-hosted callback on ingestion.

Important nuance:

- The server-side masking diagram indicates an unmasked event can be stored in S3 first, then processed by the worker/masking callback.
- Therefore, for Orbit's sensitive data, server-side masking is a safety net, not the primary protection.

Orbit policy:

- Client-side masking/no-content traces by default.
- Store source IDs and hashes, not raw content.
- Store prompt versions and capability labels, not full prompts.
- Full trace content only for internal/dev users or explicit debug consent.

### Evals

Langfuse datasets are a good fit because they support:

- inputs and expected outputs;
- metadata;
- folders;
- versioning;
- schema enforcement;
- synthetic datasets;
- dataset items created from production traces;
- experiments via UI/SDK/CI.

Orbit should create eval folders like:

- `retrieval/related-captures`
- `retrieval/hard-negatives`
- `agent/sparse-data-humility`
- `agent/profile-questions`
- `agent/action-extraction`
- `agent/citation-correctness`
- `gateway/cost-routing`

### Product judgment on Langfuse

Langfuse should be in the POC from day one. Without it, the stack choices will look better than they are because failures will be invisible.

## LiteLLM findings

### Why it moved up

LiteLLM is not just a provider adapter. The docs show real control-plane features:

- virtual keys;
- Postgres-backed key/user/team spend tracking;
- user, team, key, customer, and agent budgets;
- budget durations;
- multiple budget windows, such as daily and monthly caps;
- RPM/TPM/max parallel request limits;
- model access groups;
- key block/unblock;
- custom key generation hooks;
- upper bounds/defaults for key generation;
- call IDs in response headers;
- Langfuse callbacks/OTEL integration;
- message/response redaction;
- `no-log` support;
- metadata and trace propagation.

This maps directly to Orbit needs:

- free/pro/alpha budgets;
- per-capability budgets;
- runaway prevention;
- cloud kill switch;
- provider fallback;
- user-level cost accounting;
- trace correlation with Langfuse and Supabase audit metadata.

### Prompt logging controls

LiteLLM has controls that matter:

- `turn_off_message_logging=True` prevents messages/responses from being logged while still tracking metadata/spend.
- `redact_user_api_key_info=true` can remove user key information from logs.
- `no-log=true` can turn off tracking/logging for a request, though Orbit should probably disable user-controlled no-log if it breaks required audit metadata.
- Langfuse metadata can carry trace IDs, user IDs, session IDs, tags, and generation IDs.

Orbit policy:

- Turn off message logging globally.
- Pass Orbit trace IDs and source IDs as metadata.
- Track costs at capability granularity.
- Do not log raw captures/prompts unless debug consent is active.

### Vercel AI Gateway comparison

Vercel AI Gateway remains useful because:

- it is simple;
- it integrates with Vercel deployment;
- it can route to multiple models;
- it can integrate with LiteLLM and Vercel AI SDK paths.

But the docs/research do not show the same level of user/key/team budget control as LiteLLM. For Orbit, simplicity matters less than traceable, per-user cost control once cloud AI is default.

POC decision:

- Keep the existing Vercel Edge Function contract.
- Put LiteLLM behind it for a test path.
- Compare latency, failure handling, budget enforcement, trace metadata, and prompt redaction.

## Mastra findings

### Mastra strengths

Mastra is more relevant to Orbit than the first pass gave it credit for.

Primary docs show:

- TypeScript-native agents;
- workflows with explicit steps;
- input/output schemas;
- workflow state;
- suspension/resumption;
- streaming workflow results;
- workflow restart from active steps;
- workflow runners such as Inngest;
- memory with resource/thread scoping;
- observational memory;
- working memory;
- semantic recall;
- multi-agent memory isolation/sharing;
- live eval scorers;
- trace evals;
- Studio for workflow/agent debugging.

This fits Orbit if the backend remains TypeScript-first.

### Watch-outs

Mastra memory is agent memory, not Orbit's canonical product memory.

Use it for:

- active agent sessions;
- short-term working memory;
- workflow state;
- agent debug/iteration;
- maybe conversation-level memory.

Do not use it as:

- the canonical capture graph;
- the only source of profile facts;
- the only provenance store;
- the only memory inspector backend.

### Product judgment on Mastra

Mastra should be POC'd if Orbit wants a TypeScript backend. It may let us avoid a Python agent service while keeping workflows typed and inspectable.

The POC should test approval/suspend/resume because Orbit's agent must pause before external actions.

## LangGraph findings

The primary fetch struggled with LangGraph docs, but Exa and known docs consistently pointed to these production strengths:

- explicit directed graph orchestration;
- typed state;
- conditional routing;
- persistent checkpointing, often Postgres-backed;
- human-in-the-loop interrupts;
- streaming intermediate events;
- subgraphs;
- strong maturity around complex agent control.

LangGraph remains the best fit if Orbit's agent becomes a complex graph planner with many conditional branches and review gates.

The tradeoff is runtime/ecosystem:

- likely Python-first for the strongest path;
- more LangChain/LangSmith gravity;
- more backend complexity if the rest of cloud API is TypeScript.

Product judgment:

- Use LangGraph if precision beats stack simplicity.
- Use Mastra if TypeScript velocity beats graph-framework maturity.
- Do not use either as the canonical memory layer.

## Concrete POC design

### Dataset

Create 100 synthetic captures:

1. 20 coding-agent captures.
2. 15 startup/fundraising captures.
3. 15 recipes/grocery captures.
4. 15 travel/calendar captures.
5. 15 message-draft/follow-up captures.
6. 10 hard-negative same-keyword captures.
7. 10 sparse/ambiguous captures.

For each capture, create:

- `capture_id`;
- `user_id`;
- source app/provider;
- captured text/summary;
- created time;
- intent;
- optional note;
- expected related IDs;
- expected non-related IDs;
- expected actions;
- expected humility behavior.

### Graphiti/Zep POC tasks

1. Ingest captures as episodes.
2. Ingest notes as episodes.
3. Ingest user feedback as episodes.
4. Extract Orbit custom entities/edges.
5. Retrieve related captures with reasons.
6. Search by query.
7. Ask a profile question only when evidence crosses threshold.
8. Delete one unique capture.
9. Delete one shared capture.
10. Delete a full test user.
11. Export all graph facts for one user.
12. Verify no cross-user leakage.

Pass criteria:

- `precision@5 >= 0.75` on related captures.
- `recall@10 >= 0.80` for explicit expected related sets.
- zero cross-user retrieval leaks.
- deletion leaves no source text, episode UUIDs, or orphaned unique entities.
- shared facts remain only when supported by another source.
- every related result has a source ID and short reason.

### LiteLLM/Vercel gateway POC tasks

1. Route summary, extraction, relatedness, Ask Orbit, profile-question, and action-plan calls.
2. Pass `user_id`, `capability`, `capture_id`, `trace_id`, `session_id`, and `source_hash` metadata.
3. Enforce daily and monthly budgets.
4. Enforce capability budgets.
5. Test budget exceeded behavior.
6. Test provider fallback.
7. Verify prompt/message logging is disabled.
8. Verify Langfuse trace correlation.
9. Verify Supabase audit metadata receives call ID/cost/status.

Pass criteria:

- budgets fail closed;
- raw prompt/capture text absent from logs by default;
- costs attributable by user and capability;
- fallback does not lose trace continuity;
- gateway failure returns user-safe error envelope to Android.

### Langfuse POC tasks

1. Create trace schema conventions.
2. Trace one capture enrichment.
3. Trace one related-capture query.
4. Trace one Ask Orbit response.
5. Trace one action proposal.
6. Create eval datasets for relatedness, humility, citation correctness, and action extraction.
7. Run at least one experiment from a versioned dataset.
8. Convert one failed trace into a dataset item.
9. Verify client-side masking before trace export.

Pass criteria:

- every trace has `user_id`, `session_id` or `capture_id`, `capability`, `model`, `cost`, `latency`, `status`;
- traces can be found from local/cloud audit metadata;
- eval datasets are versioned and reproducible;
- production traces can be converted into eval cases without leaking raw content by default.

### Mastra/LangGraph POC tasks

Build the same scenario twice:

1. User opens a capture about coding agents.
2. Agent retrieves related captures.
3. Agent explains why they are related.
4. Agent detects ambiguity about whether this is work, research, product, or general interest.
5. Agent asks one profile question.
6. User answers.
7. Agent proposes a profile fact with provenance.
8. Agent drafts a to-do or message from two captures.
9. Agent pauses for confirmation.
10. User rejects one suggestion.
11. Agent records the rejection as feedback.
12. Full run appears in Langfuse.

Compare:

- implementation time;
- code clarity;
- trace clarity;
- suspend/resume behavior;
- failure/retry behavior;
- deployment complexity;
- TypeScript/Kotlin integration path;
- how much framework memory must be disabled or bypassed to preserve Orbit's canonical memory model.

## Updated recommendation

The likely stack after this research is now:

| Layer | Recommendation | POC risk |
| --- | --- | --- |
| Product DB | Supabase Postgres/pgvector | Reconcile RLS alpha with constitution storage isolation target. |
| KG memory | Graphiti OSS first | Deletion/invalidation, tenant isolation, graph DB ops. |
| Managed KG fallback | Zep | Vendor dependency and credit cost. |
| Observability/evals | Langfuse | Must avoid raw content traces by default. |
| Gateway | LiteLLM behind Orbit Edge/API | Added proxy/DB ops, but strongest controls. |
| Simple gateway fallback | Vercel AI Gateway | Easier, weaker budget/audit controls. |
| Agent runtime | Mastra vs LangGraph POC | TS velocity vs graph maturity. |
| Durable workflows | Inngest first; Temporal later | Only add Temporal when long-running external actions justify it. |

## Strong opinion after second pass

The architecture should be optimized for **provable trust**, not abstract privacy language.

That means:

- source-of-truth product records stay in Supabase;
- canonical memory is a provenance graph, not a chat memory blob;
- every derived fact must know which capture/conversation/action created it;
- every suggestion must cite sources and explain why;
- every LLM call must be budgeted and traceable;
- every trace must be redacted by default;
- every delete/export/account removal path must be tested at the graph level;
- every agent action must pause before external writes.

The POC should start with Graphiti + Langfuse + LiteLLM because those are the highest-risk/highest-leverage infrastructure choices. Mastra vs LangGraph can run in parallel once the retrieval/trace/gateway spine exists.
