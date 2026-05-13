# Agent Stack Landscape Research - 2026-05-12

This is a broader comparison pass after the first two stack docs:

- [Agent Stack Research Pass - 2026-05-12](agent-stack-research-2026-05-12.md)
- [Agent Stack POC Research - 2026-05-12](agent-stack-poc-research-2026-05-12.md)
- [Capture Understanding Stack Research - 2026-05-12](capture-understanding-stack-research-2026-05-12.md)
- [Orbit Agent Architecture Round 3 - 2026-05-12](orbit-agent-architecture-round-3-2026-05-12.md)
- [Orbit Agent Architecture Round 4 - 2026-05-12](orbit-agent-architecture-round-4-2026-05-12.md)
- [Orbit Agent Architecture Round 5 - 2026-05-12](orbit-agent-architecture-round-5-2026-05-12.md)
- [Orbit Agent Architecture Round 6 - 2026-05-12](orbit-agent-architecture-round-6-2026-05-12.md)

The earlier work was directionally useful, but too narrow. It validated a likely stack instead of first mapping the competitive option space. This pass starts wide, then narrows against Orbit's actual product needs.

## Context7 note

This pass initially used primary docs and AgentCash/Exa because Context7 was not exposed in the VS Code tool session. The local MCP config was later fixed and Context7 is now available for subsequent rounds. Round 1 agent architecture research used Context7 for Mastra, LangGraph JS, and Pydantic AI framework details.

This pass uses:

- primary vendor docs where available;
- AgentCash/Exa broad landscape research;
- direct doc fetches for serious contenders;
- explicit notes where future Context7 follow-up should re-check specific APIs before implementation.

## Orbit's evaluation criteria

Orbit is not a generic chatbot. The stack has to support an agentic memory product with captured artifacts, user intent, provenance, summaries, actions, feedback, and deletion/export promises.

The important criteria are:

| Criterion | Why it matters for Orbit |
| --- | --- |
| Provenance | Every fact, suggestion, and relationship must trace back to captures, notes, conversations, or feedback. |
| Temporal reasoning | User interests and tasks evolve; memory needs history and invalidation, not just latest preference blobs. |
| Tenant isolation | Personal memory leakage is existential product risk. Filters alone are not enough unless tested. |
| Deletion/export | Delete capture, delete account, and export memory must work across derived data, embeddings, graph facts, traces, and eval datasets. |
| Sparse-data humility | Agent must know when it does not have enough evidence and ask a question instead of inventing a profile. |
| Cost control | Cloud AI is default, so every capability needs budgets, fallback behavior, and cost attribution. |
| Observability | Agent failures are often semantic, not HTTP failures. We need traces, evals, datasets, regressions, and cost views. |
| Human approval | External actions like calendar, email, grocery, to-do, or draft sending must pause for confirmation. |
| Type and schema safety | Android + cloud contracts need predictable envelopes, structured outputs, and typed workflow state. |
| Team velocity | The app is stalled; POC choices must avoid building an infrastructure company too early. |

## Broad landscape by layer

### Memory and knowledge candidates

| Option | Category | Strength | Orbit concern | Verdict |
| --- | --- | --- | --- | --- |
| Graphiti OSS | Temporal KG engine | Best conceptual fit for evolving facts and provenance graph. | Deletion, tenant isolation, graph DB ops, Python service. | Keep as first KG POC. |
| Zep managed | Managed temporal KG/memory | Fastest production path for Graphiti-like managed memory with dashboards and tenant claims. | Vendor dependence, credit cost, graph summary deletion semantics. | Keep as managed fallback. |
| Mem0 Platform | Managed agent memory | Strong managed memory, analytics, workspace governance, vector + entity memory approach. | Current OSS graph support removed; more retrieval memory than canonical provenance graph. | Add as memory baseline/challenger, not canonical KG. |
| Mem0 OSS | Self-hosted memory library/server | Python/Node, self-hosted, Qdrant/Postgres defaults, hybrid search/entity linking. | ADD-only memory, no queryable graph store, not enough provenance semantics. | Useful baseline for retrieval and profile memory, not enough for Orbit graph. |
| Letta | Stateful agent runtime | Deep agent memory blocks, persistent messages, tools, runs, conversations, API/SDKs. | Owns the agent runtime; memory is agent-centric not product-data-centric. | Do not use as canonical memory; revisit for agent runtime experiments only. |
| LangMem | LangGraph memory component | Simple memory tools/background extraction, native LangGraph store integration. | Flat memory, LangGraph-coupled, not graph/provenance rich. | Use only if LangGraph chosen and only for session/profile convenience. |
| Cognee | Memory architecture toolkit | Explicit relational + vector + graph architecture; provenance is first-class in docs. | Docs surface is younger, fetch gaps, production story needs validation. | Watchlist/challenger for graph/memory architecture. |
| LlamaIndex PropertyGraphIndex | KG/RAG toolkit | Schema extractors, graph stores, vector retrievers, TextToCypher, Neo4j/Qdrant/FalkorDB integrations. | Toolkit, not product memory platform; text-to-cypher safety burden. | Strong ingestion/retrieval toolkit, not canonical memory store by itself. |
| Raw Supabase pgvector | Product DB + vector | Already in stack, RLS, SQL, hybrid search possible, simplest ops. | Not a graph; relationship/provenance logic must be built by us. | Keep as control plane and baseline retrieval. |

#### Memory conclusion

Graphiti still looks like the best first canonical-memory POC because Orbit needs a temporal provenance graph, not only semantic recall. But the POC should compare it against two practical challengers:

1. **Zep managed**, to price the speed-vs-control tradeoff.
2. **Mem0 OSS/Platform**, to test whether a simpler memory layer is good enough for early personalization, profile facts, and related capture retrieval.

Cognee and LlamaIndex should be treated as architecture/toolkit references. They may supply ingestion/retrieval techniques, but they do not replace the product-memory decision.

Important new finding: Mem0 OSS removed graph-store support in its current algorithm and replaced it with entity linking plus hybrid retrieval. That makes it less suitable as Orbit's canonical knowledge graph, even though it may be a strong memory baseline.

### Vector and retrieval candidates

| Option | Category | Strength | Orbit concern | Verdict |
| --- | --- | --- | --- | --- |
| Supabase pgvector | Postgres extension | Lowest operational complexity, SQL filters, RLS, existing app fit, hybrid search docs. | Performance ceiling at larger vector counts; index rebuild/ops risks. | Default until proven insufficient. |
| Qdrant | Purpose-built vector DB | Strong filtering, hybrid dense/sparse, payload indexes, strict mode, self-host/cloud/private options. | Adds another data store and ops surface. | POC if pgvector retrieval quality or latency struggles. |
| Weaviate | Vector DB + AI ecosystem | Hybrid search, schema-first, agents, embedded/cloud/K8s options. | Heavier/opinionated; may overlap with app-level memory layer. | Good managed/self-host alternative, not first. |
| Pinecone | Managed vector DB | Zero-ops, namespaces for multitenancy, full-text + dense/sparse workflows. | Vendor lock-in and less relational/provenance control. | Consider only if vector ops become bottleneck. |
| Milvus/Zilliz | Large-scale vector DB | Scale, hybrid search, flexible multi-tenancy, backup/CDC, billion-scale. | Overkill for early Orbit; Kubernetes/ops heavy. | Later-scale option. |
| Elasticsearch/OpenSearch | Search + vector | Strong lexical search and existing operational maturity. | Not currently in stack; vector experience can be awkward. | Only if product needs serious search facets/log search. |
| Vespa | Search/ranking engine | Massive hybrid ranking and ML ranking. | Too complex for current team/stage. | Not now. |

#### Retrieval conclusion

Supabase pgvector should remain the default because it keeps Orbit's product records, permissions, and vectors close together. The first serious challenger should be Qdrant, because Qdrant's filtering and hybrid retrieval model map well to personal memory queries with `user_id`, source, intent, date, and topic filters.

Do not migrate vectors prematurely. Build the eval set first, then let retrieval quality and latency decide.

### Agent orchestration candidates

| Option | Category | Strength | Orbit concern | Verdict |
| --- | --- | --- | --- | --- |
| Mastra | TypeScript agent/workflow framework | TS-native, agents + workflows, memory, approval, observability, Studio, suspend/resume. | Younger than LangGraph; canonical memory must stay outside Mastra memory. | Keep as top TS POC. |
| LangGraph | Graph orchestration | Durable execution, typed state, human-in-loop, checkpointing, stateful graph control. | Python/LangChain gravity; more backend complexity if Orbit cloud stays TS. | Keep as top precision POC. |
| Pydantic AI | Python typed agent framework | Type safety, model-agnostic, OTel/Logfire, evals, graph support, human approval, durable execution. | Python service; less proven in Orbit than LangGraph/Mastra. | Add to POC shortlist. |
| Google ADK | Multi-language agent framework | Python/TS/Go/Java, graph workflows, evals, deployment, sessions, memory, action confirmations. | Google Cloud gravity; maturity/fit needs hands-on test. | Add as serious challenger, especially if GCP path emerges. |
| OpenAI Agents SDK | Python agent runtime | Simple primitives, tools, guardrails, sessions, human-in-loop, tracing, sandbox agents. | OpenAI ecosystem gravity; not ideal as model-agnostic control plane. | Useful reference or thin workflows, not default. |
| Microsoft Agent Framework | Enterprise framework | Python/C#, workflows, memory/persistence, hosting, Azure/Durable Task integration. | Azure ecosystem gravity; less aligned with current Vercel/Supabase path. | Revisit only if Azure enterprise path appears. |
| CrewAI | Multi-agent crews/flows | Role-based agent teams, memory/knowledge/observability claims. | Role metaphors are not Orbit's core problem; complex state/provenance still external. | Not a first POC. |
| AutoGen/AG2 | Conversational multi-agent | Research/multi-agent heritage. | Less direct fit for a user-facing memory product with approval gates. | Not first. |
| Semantic Kernel | Enterprise orchestration SDK | Microsoft ecosystem and planners. | Similar Azure gravity; less compelling for current stack. | Not first. |
| Vercel AI SDK | UI/model interaction toolkit | Excellent TS streaming/model ergonomics. | Not an agent orchestration/runtime by itself. | Use at gateway/UI edges if helpful. |

#### Agent orchestration conclusion

The POC set should expand from **Mastra vs LangGraph** to **Mastra vs LangGraph vs Pydantic AI vs Google ADK**.

Use the same Orbit scenario for all four:

1. Retrieve related captures.
2. Explain relatedness with citations.
3. Detect sparse/ambiguous data.
4. Ask one profile question.
5. Convert answer into a candidate profile fact with provenance.
6. Draft an action from captures.
7. Pause for user approval.
8. Record rejection as feedback.
9. Emit masked trace and eval artifacts.

Choose based on implementation clarity, human approval semantics, trace quality, deployment complexity, and whether framework memory can be kept subordinate to Orbit's canonical memory graph.

### Durable workflow candidates

| Option | Category | Strength | Orbit concern | Verdict |
| --- | --- | --- | --- | --- |
| Inngest | Event-driven durable execution | TS/Python/Go, steps, concurrency, throttling, observability, Vercel-friendly. | Managed service unless self-hosted; less heavyweight than Temporal. | Strong first durable workflow candidate. |
| Trigger.dev | Open-source background jobs | Plain async code, AI tasks, retries, no timeouts, dashboard, self-host/cloud. | Background jobs more than full durable workflow semantics. | Strong simple jobs candidate. |
| Temporal | Durable execution platform | Crash-proof workflows over seconds to years, strongest reliability semantics. | Operationally heavy; overkill before real long-running workflows. | Later, when external actions require mission-critical durability. |
| Cloud queues/cron | Basic infra | Simple and cheap. | Weak trace/retry/state semantics for agent workflows. | Use for narrow jobs only. |

#### Workflow conclusion

Do not make Temporal part of the first agent POC. Use Inngest or Trigger.dev for early background/durable steps, then graduate to Temporal only when Orbit is running long-lived external action workflows where loss or duplicate execution is unacceptable.

### Observability and eval candidates

| Option | Category | Strength | Orbit concern | Verdict |
| --- | --- | --- | --- | --- |
| Langfuse | Open-source LLM observability/evals | Framework-agnostic, OTel data model, self-hostable, traces, datasets, costs, LiteLLM integration. | Must enforce client-side masking/no-content traces. | Keep as default trace/eval spine. |
| Arize Phoenix | Open-source AI observability/evals | Strong RAG evals, OpenInference/OTel, traces, built-in eval templates, drift/debug workflows. | Less focused on prompt/session trace product than Langfuse. | Add as RAG eval complement/challenger. |
| LangSmith | LangChain platform | Best integration if LangGraph/LangChain chosen, tracing/evals/deployments. | Proprietary/cloud and LangChain gravity. | Use if LangGraph wins and data policy allows; otherwise avoid. |
| Braintrust | Managed eval/observability | Strong eval workflow, prompt iteration, regression testing. | Managed vendor; data policy and self-host story need review. | Good eval challenger, not default. |
| W&B Weave | Observability/evals | Good for research-heavy teams, Python/TS libraries, evals. | W&B ecosystem; likely too broad/research-oriented for Orbit's first spine. | Watchlist. |
| Helicone | Gateway observability/prompt management | Easy gateway logging, prompt management, AI Gateway. | More gateway-oriented; may overlap with LiteLLM/Portkey. | Consider if prompt management becomes primary need. |
| Portkey observability | Gateway + observability | OTel-compliant, unified logs, guardrails/evals integration. | Could couple gateway and observability too tightly. | Serious if Portkey gateway wins. |
| Pydantic Logfire | General OTel observability | Natural if Pydantic AI wins. | Not Orbit-wide eval platform by itself. | Framework-specific add-on. |

#### Observability conclusion

Langfuse remains the best default because it is self-hostable, framework-agnostic, and directly matches the trace/eval/cost spine Orbit needs. But Phoenix should be added to the POC as a **RAG/retrieval eval challenger or complement**, especially for related-capture retrieval and embedding drift.

A realistic production stack may be:

- Langfuse for traces, sessions, costs, prompt/version metadata, and general eval datasets.
- Phoenix for retrieval-quality diagnostics and RAG eval experiments if Langfuse is not enough.
- No raw content in production traces by default.

### LLM gateway candidates

| Option | Category | Strength | Orbit concern | Verdict |
| --- | --- | --- | --- | --- |
| LiteLLM | Self-hosted/model proxy | Virtual keys, budgets, rate limits, spend tracking, fallbacks, redaction, Langfuse/OTel integrations. | Operate proxy + Postgres; security track record must be monitored. | Keep as self-hosted/control-plane favorite. |
| Portkey | Managed/open gateway + guardrails | Routing, configs, retries, fallbacks, guardrails, observability, prompt management, open gateway. | Vendor/control tradeoff; exact per-user budget model needs hands-on validation. | Serious managed challenger. |
| Cloudflare AI Gateway | Edge gateway | Analytics, logs, caching, rate limits, dynamic routing with metadata, budget limits, fallback, global edge. | Ecosystem lock-in; privacy/logging/redaction needs deeper test. | Serious challenger, stronger than prior pass assumed. |
| Vercel AI Gateway | Vercel-native gateway | Hundreds of models, AI SDK, retries, spend monitoring, generation lookup, BYOK, no token markup. | Per-user/capability budgets less explicit than LiteLLM/Cloudflare dynamic routes. | Good simple gateway, especially short term. |
| OpenRouter | Hosted model aggregator | Fast access to many models, simple OpenAI-compatible API, automatic fallback/cost-effective model selection. | Limited observability/budgets/control for production personal-memory app. | Use for model exploration, not production control plane. |
| Helicone AI Gateway | Gateway + observability | OpenAI-compatible gateway, logging, prompt management. | Less obviously strong on hard budget enforcement than LiteLLM/Cloudflare/Portkey. | Watchlist. |
| AWS Bedrock gateway | Provider platform | Enterprise controls, private cloud integrations. | AWS commitment; app is already Supabase/Vercel leaning. | Only if AWS architecture emerges. |
| Kong AI Gateway | API gateway plugin path | Enterprise API governance. | Too much gateway platform for current stage. | Not now. |

#### Gateway conclusion

The gateway POC should expand from **LiteLLM vs Vercel AI Gateway** to **LiteLLM vs Portkey vs Cloudflare AI Gateway vs Vercel AI Gateway**.

OpenRouter is valuable for model exploration but does not look like the production control plane for Orbit because Orbit needs per-user/capability cost controls, redaction, audit metadata, and trace correlation.

Cloudflare AI Gateway deserves a real look because its dynamic routing docs explicitly include metadata-driven conditionals, rate limits, budget limits, fallbacks, A/B rollouts, and versioned routes. That overlaps with many reasons we liked LiteLLM.

Portkey deserves a real look because it combines gateway configs, guardrails, OTel observability, prompt management, and an open gateway path. It may be a better managed gateway than Vercel if Orbit wants governance faster than self-hosting LiteLLM.

### Model and provider candidates

This pass focused on infrastructure, not a full model benchmark, but the gateway decision should preserve model flexibility.

Near-term provider set:

| Provider/model family | Use |
| --- | --- |
| Anthropic Claude Sonnet/Haiku | Summaries, relatedness, action plans, classification, careful answers. |
| OpenAI GPT-5.x / GPT-4.1 family | Structured extraction, tool-compatible paths, fallback, possibly embeddings. |
| Google Gemini | Cost/performance fallback, long-context experiments, ADK path. |
| OpenAI text-embedding-3-small | Current embedding default from specs. |
| Voyage/Cohere/Jina embeddings | Future retrieval benchmark challengers. |
| Local/Nano/Ollama | Kill switch/local fallback, not default product promise. |

The POC should benchmark retrieval and extraction quality by task, not by provider loyalty.

## Option shortlist after broad pass

### Canonical memory POC

Test:

1. Graphiti OSS.
2. Zep managed.
3. Mem0 OSS or Platform baseline.
4. Supabase pgvector-only baseline.

Watch:

- Cognee.
- LlamaIndex PropertyGraphIndex as ingestion/retrieval toolkit.

Do not prioritize:

- Letta as canonical memory.
- LangMem as canonical memory.

### Retrieval POC

Test:

1. Supabase pgvector hybrid search.
2. Qdrant hybrid search.
3. Graphiti/Zep graph retrieval.

Watch:

- Weaviate if schema-first vector DB becomes appealing.
- Pinecone if zero-ops vector scale becomes worth vendor lock-in.

Do not prioritize:

- Milvus/Zilliz until scale demands it.
- Vespa until advanced search/ranking demands it.

### Agent runtime POC

Test:

1. Mastra.
2. LangGraph.
3. Pydantic AI.
4. Google ADK.

Use as reference:

- OpenAI Agents SDK.

Do not prioritize:

- CrewAI.
- AutoGen/AG2.
- Semantic Kernel/Microsoft Agent Framework unless Azure becomes strategic.

### Workflow POC

Test:

1. Inngest.
2. Trigger.dev.

Defer:

- Temporal until external actions become truly mission-critical and long-lived.

### Observability/eval POC

Test:

1. Langfuse.
2. Phoenix for RAG/retrieval eval complement.
3. Braintrust as managed eval challenger if data policy allows.

Use conditionally:

- LangSmith only if LangGraph/LangChain wins.
- Pydantic Logfire only if Pydantic AI wins.
- Portkey observability if Portkey gateway wins.

### Gateway POC

Test:

1. LiteLLM.
2. Portkey.
3. Cloudflare AI Gateway.
4. Vercel AI Gateway.

Use only for exploration:

- OpenRouter.

Watch:

- Helicone AI Gateway/prompt management.

## Revised recommendation

The broad pass does not overturn the original direction, but it should change the POC plan.

Previous recommendation:

- Supabase Postgres/pgvector.
- Graphiti first, Zep fallback.
- Langfuse.
- LiteLLM behind Orbit gateway.
- Mastra vs LangGraph.

Revised recommendation:

| Layer | Recommended default | Must-compare challengers |
| --- | --- | --- |
| Product/control plane | Supabase Postgres/pgvector | None for v1; Qdrant only for retrieval scale/quality. |
| Canonical memory | Graphiti OSS | Zep managed, Mem0 baseline, Supabase-only baseline. |
| Retrieval/vector | Supabase pgvector | Qdrant hybrid. |
| Gateway | LiteLLM or Cloudflare/Portkey depending ops appetite | Vercel AI Gateway, Portkey, Cloudflare AI Gateway. |
| Observability | Langfuse | Phoenix for RAG eval, Braintrust for managed eval. |
| Agent runtime | Mastra if TS path; LangGraph if precision path | Pydantic AI, Google ADK. |
| Durable workflows | Inngest or Trigger.dev | Temporal later. |

## Strong product opinion

Orbit should not pick the stack that makes the cleanest architecture diagram. It should pick the stack that makes the product honest.

Honest means:

- every memory has provenance;
- every retrieval can cite why it appeared;
- every agent guess can admit uncertainty;
- every cloud call can be traced without leaking raw personal content;
- every user and feature has cost limits;
- every deletion can be tested across derived systems;
- every external action waits for user confirmation.

That points away from pure agent-memory platforms as the canonical store. Letta, LangMem, Mem0, and framework memory are useful, but the canonical memory must remain product-owned and inspectable.

Graphiti/Zep still best match that thesis, but the broader pass says we should **prove it**, not assume it. A credible POC should include Mem0 and Supabase-only baselines so we know whether the graph is earning its complexity.

## Practical next research and POC sequence

### Step 1: Create the eval dataset first

Before choosing graph/vector/agent tools, create the 100-capture synthetic dataset described in the POC doc.

Add expected outputs for:

- related captures;
- non-related hard negatives;
- expected actions;
- profile questions;
- rejection behavior;
- delete/export behavior;
- sparse-data humility.

### Step 2: Memory/retrieval bakeoff

Run the same dataset through:

1. Supabase pgvector hybrid baseline.
2. Graphiti OSS.
3. Zep managed.
4. Mem0 baseline.
5. Qdrant hybrid if pgvector struggles.

Measure:

- `precision@5`;
- `recall@10`;
- hard-negative rejection;
- citation/provenance quality;
- deletion behavior;
- tenant isolation;
- latency;
- ingestion cost;
- implementation complexity.

### Step 3: Gateway bakeoff

Run the same model calls through:

1. Existing Vercel Edge Function direct/provider path.
2. LiteLLM.
3. Portkey.
4. Cloudflare AI Gateway.
5. Vercel AI Gateway.

Measure:

- per-user budget enforcement;
- per-capability budget enforcement;
- redaction/no-message logging;
- call IDs/generation IDs;
- Langfuse or OTel trace correlation;
- fallback behavior;
- failure envelopes to Android;
- operational burden.

### Step 4: Observability bakeoff

Run the same traces/evals through:

1. Langfuse.
2. Phoenix for retrieval evals.
3. Braintrust if managed evals are acceptable.

Measure:

- trace clarity;
- eval dataset versioning;
- production-trace-to-eval workflow;
- masking/no-content trace workflow;
- cost tracking;
- debugging speed.

### Step 5: Agent runtime bakeoff

Run the same Orbit scenario through:

1. Mastra.
2. LangGraph.
3. Pydantic AI.
4. Google ADK.

Measure:

- typed state clarity;
- human approval pause/resume;
- trace quality;
- deployment complexity;
- Kotlin/Android integration path;
- framework memory bypass/subordination;
- developer velocity.

## Open questions

1. Do we want Orbit cloud services to stay TypeScript-first, or is a Python service acceptable for memory/agent runtime?
2. Is managed cloud acceptable for the canonical memory layer during alpha, or must canonical memory be self-hosted/product-owned from the beginning?
3. Do we prefer one integrated managed gateway/guardrail platform, or a self-hosted gateway plus separate Langfuse/Phoenix observability stack?
4. How strict should production trace masking be for alpha users who opt into debugging?
5. Is the first agent mostly a retrieval/planning assistant, or does it need durable external actions in the first cloud POC?

## Sources checked

Broad AgentCash/Exa research:

- AI memory/KG landscape: Graphiti, Zep, Letta, Mem0, LangMem, Cognee, LlamaIndex.
- Vector DB landscape: pgvector, Qdrant, Weaviate, Pinecone, Milvus, Vespa, OpenSearch.
- Agent orchestration landscape: LangGraph, Mastra, OpenAI Agents SDK, Microsoft Agent Framework, Google ADK, CrewAI, Pydantic AI, AutoGen, Semantic Kernel.
- Observability/eval landscape: Langfuse, LangSmith, Arize Phoenix, Braintrust, Helicone, Portkey, W&B Weave.
- Gateway landscape: LiteLLM, Portkey, OpenRouter, Vercel AI Gateway, Cloudflare AI Gateway, AWS Bedrock gateway, Kong AI Gateway.

Primary docs fetched:

- Graphiti and Zep docs from prior pass.
- `https://docs.mem0.ai/overview`
- `https://docs.mem0.ai/open-source/overview`
- `https://docs.mem0.ai/platform/platform-vs-oss`
- `https://docs.mem0.ai/platform/features/graph-memory`
- `https://docs.letta.com/overview`
- `https://docs.letta.com/guides/get-started/intro`
- `https://docs.letta.com/guides/core-concepts/stateful-agents`
- `https://langchain-ai.github.io/langmem/`
- `https://docs.cognee.ai/`
- `https://docs.cognee.ai/core-concepts/architecture`
- `https://developers.llamaindex.ai/python/framework/module_guides/indexing/lpg_index_guide/`
- `https://supabase.com/docs/guides/ai`
- `https://qdrant.tech/documentation/overview/`
- `https://docs.weaviate.io/weaviate`
- `https://docs.pinecone.io/guides/get-started/overview`
- `https://milvus.io/docs/overview.md`
- `https://docs.langchain.com/oss/python/langgraph/overview`
- `https://mastra.ai/docs/agents/overview`
- `https://openai.github.io/openai-agents-python/`
- `https://learn.microsoft.com/en-us/agent-framework/`
- `https://adk.dev/`
- `https://docs.crewai.com/`
- `https://pydantic.dev/docs/ai/overview/`
- `https://docs.temporal.io/`
- `https://www.inngest.com/docs`
- `https://trigger.dev/docs`
- `https://langfuse.com/docs` pages from prior pass.
- `https://docs.langchain.com/langsmith`
- `https://arize.com/docs/phoenix`
- `https://arize.com/docs/phoenix/evaluation`
- `https://www.braintrust.dev/docs`
- `https://docs.helicone.ai/`
- `https://portkey.ai/docs`
- `https://docs.wandb.ai/weave/`
- `https://docs.litellm.ai/docs/`
- `https://portkey.ai/docs/product/ai-gateway-streamline-llm-integrations`
- `https://portkey.ai/docs/product/observability`
- `https://portkey.ai/docs/product/guardrails`
- `https://openrouter.ai/docs/quickstart`
- `https://vercel.com/docs/ai-gateway`
- `https://vercel.com/docs/ai-gateway/capabilities/usage`
- `https://vercel.com/docs/ai-gateway/authentication-and-byok/byok`
- `https://developers.cloudflare.com/ai-gateway/`
- `https://developers.cloudflare.com/ai-gateway/features/dynamic-routing/`
- `https://developers.cloudflare.com/ai-gateway/features/rate-limiting/`

Fetch gaps:

- Context7 was not available in this tool environment.
- Some docs redirected and were fetched at their redirected URLs.
- Some specific Cognee, Braintrust, W&B, and pgvector pages failed extraction or returned limited content; the broad pass should be followed by a Context7/docs-MCP pass if available.
