# Orbit Agent Architecture Round 4 - 2026-05-12

This is Round 4 of the deeper replanning sequence. Round 1 chose the product spine: build the trustworthy capture/memory substrate before a general-purpose agent. Round 2 defined evidence, understanding, retrieval, relatedness, action, audit, and eval contracts. Round 3 defined memory as a ladder: capture/retrieval/feedback first, candidate/promoted/graph/agent memory later.

Round 4 focuses on runtime and execution boundaries:

- where the future `:agent` process starts and stops;
- how Android, cloud services, workflow runners, model gateways, observability, and memory adapters interact;
- how user approval maps to plans, actions, and tool calls;
- how Mastra/LangGraph/Pydantic AI/Google ADK should be evaluated without letting framework memory become product memory;
- what gateway and budget controls must exist before cloud-augmented agent work;
- what Speckit should replan first.

Related docs:

- [Orbit Agent Architecture Round 1 - 2026-05-12](orbit-agent-architecture-round-1-2026-05-12.md)
- [Orbit Agent Architecture Round 2 - 2026-05-12](orbit-agent-architecture-round-2-2026-05-12.md)
- [Orbit Agent Architecture Round 3 - 2026-05-12](orbit-agent-architecture-round-3-2026-05-12.md)
- [Orbit Agent Architecture Round 5 - 2026-05-12](orbit-agent-architecture-round-5-2026-05-12.md)
- [Product Roadmap Audit - 2026-05-12](product-roadmap-audit-2026-05-12.md)
- [Agent Stack Landscape Research - 2026-05-12](agent-stack-landscape-research-2026-05-12.md)
- [Agent Stack POC Research - 2026-05-12](agent-stack-poc-research-2026-05-12.md)
- [specs/003 Orbit Actions](../specs/003-orbit-actions/spec.md)
- [specs/008 Orbit Agent](../specs/008-orbit-agent/spec.md)
- [specs/014 Gateway request/response contract](../specs/014-edge-function-llm-gateway/contracts/gateway-request-response.md)

## Round 4 conclusion

Orbit should build an **approval-first agent runtime**, not a cloud executor.

The current app already has the correct directional skeleton:

- local action proposals and action executions;
- AppFunction-like skill registry rows;
- action handlers that run in `:capture` and must not touch network;
- `:ml` owning Room writes and audit rows;
- `:net` owning the LLM gateway and public URL fetches;
- LLM requests moving through typed discriminated unions;
- no external writes without a confirmation tap.

Round 4's decision is to preserve those boundaries and add a planner/coordinator layer around them:

> The agent may retrieve, reason, draft, ask, and propose. It may not directly fetch arbitrary network resources, mutate the database, or execute external writes. Those still happen through existing bounded services: `:net`, `:ml`, and local AppFunction handlers.

This means the first agent runtime is not a giant autonomous backend. It is a state machine that produces user-visible plans and waits.

## Runtime principle

The runtime should be split by trust boundary, not by vendor framework.

1. **Device is the approval and execution boundary.** External writes are confirmed and executed locally.
2. **`:net` is the only network egress boundary.** LLM calls, evidence acquisition, cloud retrieval, and storage sync route through typed network surfaces.
3. **Product memory owns truth.** Framework memory is disposable workflow state.
4. **Cloud services may enrich and retrieve.** They do not silently act for the user.
5. **Every transition produces a record.** Plans, approvals, tool calls, failures, cancellations, and memory writes are inspectable.

## Existing execution inventory

### Android local boundaries

Current code already has several runtime commitments:

- `ActionExecutorService` runs in `:capture`, owns the action executor AIDL surface, binds to `:ml` for repository writes, and records action executions through `IEnvelopeRepository.recordActionInvocation`.
- `ActionHandler` implementations run local side effects only and must not touch network classes.
- `CloudLlmProvider` uses `INetworkGateway.callLlmGateway`, not direct HTTP.
- `NetworkGatewayImpl` decodes typed `LlmGatewayRequest` payloads and delegates to `LlmGatewayClient`.
- `OrbitMigrations` already has `action_proposal`, `action_execution`, `appfunction_skill`, and `skill_usage` tables.
- `AppFunctionSkillEntity` and `skill_usage` are already forward-compatible with planner heuristics.

These are not throwaway v1.1 details. They are the agent's future tool boundary.

### Server/cloud boundaries

Current server-side gateway is intentionally narrow:

- `POST /llm` only;
- request types: `embed`, `summarize`, `extract_actions`, `classify_intent`, `generate_day_header`, `scan_sensitivity`;
- Zod discriminated union validation;
- no streaming;
- HTTP 200 for structured upstream failures, 401 for auth failures;
- fixed routing table and model labels;
- limited cost/budget controls.

Round 2 and Round 3 require new cloud capabilities, but they should be added as typed capabilities, not as an open-ended prompt endpoint.

## Target runtime topology

```text
Android device
  :capture
    - capture overlay
    - action executor service
    - local AppFunction handlers
    - no network

  :ml
    - Room/SQLCipher source of truth
    - continuation workers
    - local retrieval indexes
    - audit writes
    - no arbitrary HTTP

  :ui
    - diary/detail/ask/agent surfaces
    - approval and correction UX
    - no direct data mutation outside repositories

  :net
    - public URL fetch
    - LLM gateway calls
    - cloud sync/retrieval API calls
    - auth/JWT handling

  :agent (future)
    - prompt assembly
    - consent filter
    - planner state machine
    - approval requests
    - plan/session records
    - no direct network
    - no direct external writes

Cloud
  LLM Gateway
    - typed model capabilities
    - budgets/routing/fallbacks/tracing

  Evidence/Understanding workers
    - public acquisition ladder
    - extraction/summarization
    - evidence bundle writes

  Retrieval service
    - Supabase hybrid retrieval
    - citations and retrieval records

  Memory service/adapter
    - candidates/promoted memory/KG backend
    - deletion/export receipts

  Workflow runner
    - durable background enrichment
    - not external action execution
```

## What `:agent` should own

The future `:agent` process should own orchestration and safety, not raw capability.

Owns:

- `AgentRun` lifecycle;
- `AgentPlan` creation;
- prompt assembly;
- consent filtering;
- scope selection;
- retrieval request composition;
- candidate action drafting;
- approval request creation;
- plan revision after user edits;
- memory candidate proposal;
- agent session state;
- policy checks before cloud calls.

Does not own:

- public URL fetching;
- evidence extraction;
- raw LLM HTTP;
- Room writes outside repository AIDL;
- external app writes;
- direct cloud database access;
- canonical memory truth.

The agent asks other bounded services to do work. It never becomes a privileged everything-process.

## Agent run lifecycle

```text
created
  -> scoping
  -> retrieving
  -> planning
  -> needs_user_context
  -> awaiting_approval
  -> approved
  -> executing_local_action
  -> recording_outcome
  -> proposing_memory
  -> completed

failure paths:
  -> blocked_by_policy
  -> limited_by_evidence
  -> cancelled
  -> failed_retryable
  -> failed_terminal
```

### Lifecycle rules

- A run starts only from an explicit surface: Ask Orbit handoff, capture detail action, bubble long-press, memory inspector prompt, or user-opened review.
- Proactive surfacing can create a suggestion card, but not an execution run until the user taps.
- Every run has a scope: capture IDs, query IDs, memory IDs, and allowed action kinds.
- Every cloud call has a capability label and budget decision.
- Every external write has an `ApprovalRequest` and a local `ActionExecuteRequestParcel`.
- Every cancellation and rejection is signal, not a hidden failure.

## Runtime contracts

### AgentRun

```ts
const AgentRunSchema = z.object({
  schemaVersion: z.literal(1),
  runId: z.string().uuid(),
  userId: z.string().uuid(),
  invocation: z.object({
    surface: z.enum(['ask_orbit', 'capture_detail', 'bubble_long_press', 'memory_inspector', 'review', 'suggestion_card']),
    queryId: z.string().uuid().nullable(),
    envelopeIds: z.array(z.string().uuid()).max(50),
    startedAt: z.string().datetime(),
  }),
  status: z.enum([
    'created', 'scoping', 'retrieving', 'planning', 'needs_user_context',
    'awaiting_approval', 'approved', 'executing_local_action',
    'recording_outcome', 'proposing_memory', 'completed', 'cancelled',
    'blocked_by_policy', 'limited_by_evidence', 'failed_retryable', 'failed_terminal'
  ]),
  policy: z.object({
    cloudAllowed: z.boolean(),
    externalWritesAllowed: z.boolean(),
    maxUnderstandingDepth: z.enum(['basic', 'smart', 'deep']),
    sensitivityMax: z.enum(['normal', 'sensitive', 'local_only']),
    budgetTier: z.enum(['free', 'standard', 'deep', 'manual_override']),
  }),
  traceIds: z.object({
    localAuditIds: z.array(z.string().uuid()).max(100),
    cloudTraceId: z.string().nullable(),
    retrievalQueryIds: z.array(z.string().uuid()).max(20),
  }),
  createdAt: z.string().datetime(),
  updatedAt: z.string().datetime(),
})
```

### AgentPlan

```ts
const AgentPlanSchema = z.object({
  schemaVersion: z.literal(1),
  planId: z.string().uuid(),
  runId: z.string().uuid(),
  userVisibleGoal: z.string().max(300),
  status: z.enum(['draft', 'awaiting_user', 'approved', 'partially_approved', 'executing', 'succeeded', 'failed', 'cancelled']),
  steps: z.array(z.object({
    stepId: z.string().uuid(),
    kind: z.enum(['retrieve', 'ask_user', 'draft_action', 'local_tool', 'memory_candidate', 'answer', 'no_op']),
    displayText: z.string().max(240),
    citedEnvelopeIds: z.array(z.string().uuid()).max(20),
    citedEvidenceIds: z.array(z.string().uuid()).max(50),
    risk: z.enum(['low', 'medium', 'high']),
    approvalRequired: z.boolean(),
    status: z.enum(['pending', 'ready', 'approved', 'rejected', 'running', 'succeeded', 'failed', 'skipped']),
  })).min(1).max(20),
  limitations: z.array(z.string().max(160)).max(12),
  producedBy: z.object({
    runtime: z.string().max(80),
    modelLabel: z.string().max(128).nullable(),
    promptVersion: z.string().max(64).nullable(),
  }),
  createdAt: z.string().datetime(),
})
```

### ApprovalRequest

```ts
const ApprovalRequestSchema = z.object({
  schemaVersion: z.literal(1),
  approvalId: z.string().uuid(),
  runId: z.string().uuid(),
  planId: z.string().uuid(),
  stepId: z.string().uuid(),
  approvalKind: z.enum(['external_write', 'memory_promotion', 'deep_fetch', 'cloud_visual_analysis', 'connected_account_access']),
  title: z.string().max(160),
  preview: z.string().max(1000),
  argsPreviewJson: z.record(z.unknown()).nullable(),
  risk: z.enum(['low', 'medium', 'high']),
  choices: z.array(z.enum(['approve', 'edit', 'reject', 'not_now', 'always_for_this_skill', 'never_for_this_source'])).min(1),
  expiresAt: z.string().datetime().nullable(),
  createdAt: z.string().datetime(),
})
```

### ConsentFilterResult

```ts
const ConsentFilterResultSchema = z.object({
  schemaVersion: z.literal(1),
  resultId: z.string().uuid(),
  runId: z.string().uuid(),
  check: z.enum(['prompt_assembly', 'retrieval', 'evidence_acquisition', 'action_execution', 'memory_promotion']),
  status: z.enum(['allowed', 'redacted', 'requires_user_approval', 'blocked']),
  removedRefs: z.array(z.object({
    refKind: z.enum(['capture', 'evidence', 'memory', 'chunk', 'field']),
    refId: z.string(),
    reason: z.string().max(160),
  })).max(100),
  userVisibleReason: z.string().max(240).nullable(),
  createdAt: z.string().datetime(),
})
```

### BudgetDecision

```ts
const BudgetDecisionSchema = z.object({
  schemaVersion: z.literal(1),
  decisionId: z.string().uuid(),
  userId: z.string().uuid(),
  runId: z.string().uuid().nullable(),
  capability: z.enum(['embed', 'summarize', 'understand_capture', 'retrieve', 'plan', 'extract_actions', 'scan_sensitivity', 'visual_analysis', 'browser_fetch']),
  route: z.enum(['local', 'vercel_gateway', 'litellm', 'portkey', 'cloudflare_gateway', 'blocked']),
  modelLabel: z.string().max(128).nullable(),
  estimatedCostCents: z.number().nonnegative().nullable(),
  remainingBudgetCents: z.number().nonnegative().nullable(),
  decision: z.enum(['allowed', 'downgraded', 'queued', 'blocked', 'requires_approval']),
  reason: z.string().max(200),
  createdAt: z.string().datetime(),
})
```

## User approval model

Orbit needs multiple kinds of approval, not one generic `OK` button.

| Approval kind | Required when | Default |
| --- | --- | --- |
| `external_write` | Calendar, to-do, share, message, email, external app side effect. | Always required. |
| `memory_promotion` | Reusing a profile/pattern fact beyond low-stakes ranking. | Required for sensitive facts; threshold for normal facts. |
| `deep_fetch` | Browser-rendered public page, managed extractor, broader search. | Based on Understanding Depth; per-capture override. |
| `cloud_visual_analysis` | Screenshot/image sent to cloud VLM. | Explicit approval unless global setting allows. |
| `connected_account_access` | Logged-in browsing or connected source. | Always explicit, separate future feature. |
| `budget_override` | Exceeding user/capability budget. | Explicit approval. |

Approval UX should show:

- what Orbit will do;
- what app/service receives data;
- what fields will be used;
- what will be remembered;
- what can be undone;
- limitations.

## Local action execution

Do not move external execution to cloud in the first agent runtime.

The current local path is correct:

1. `CandidateAction` or existing `ActionProposal` is created.
2. User sees a preview in Diary/detail/Ask/agent plan.
3. User confirms.
4. `:ui` invokes `ActionExecutorService` in `:capture`.
5. `:capture` validates function/schema and args shape.
6. Handler performs local side effect via Android Intent or local write.
7. `:capture` records result through `:ml` repository binder.
8. `:ml` writes execution, skill usage, audit, and later episode.
9. Undo/cancel window records a corrective outcome.

Round 4 additions:

- create `ApprovalRequest` before execution;
- link execution to `AgentRun`/`AgentPlan` when action came from agent;
- add full JSON Schema validation before intent dispatch;
- attach cited evidence IDs to action proposals;
- use action edits as feedback memory;
- keep external function args out of cloud unless explicitly needed and consented.

## Cloud agent/runtime stance

### What cloud can do early

- Evidence acquisition for public URLs.
- Capture understanding and summarization.
- Retrieval over synced chunks.
- Relatedness explanation.
- Candidate action drafting.
- Ask Orbit answer synthesis.
- Memory candidate proposal.
- Eval/trace/cost logging.

### What cloud should not do early

- Execute external app writes.
- Store raw prompts/responses in production traces by default.
- Become the canonical memory store without Orbit IDs and deletion receipts.
- Use framework memory as user-visible truth.
- Browse logged-in/private content without a separate future connected-account consent model.

## Agent framework decision

Round 4 should not pick a framework by vibe. It should define the product adapter and test frameworks against it.

### Product adapter

```ts
interface OrbitAgentRuntime {
  startRun(input: StartRunInput): Promise<AgentRun>
  retrieve(run: AgentRun): Promise<RetrievalQueryRecord>
  draftPlan(run: AgentRun): Promise<AgentPlan>
  revisePlan(planId: string, edit: UserPlanEdit): Promise<AgentPlan>
  requestApproval(planId: string, stepId: string): Promise<ApprovalRequest>
  recordApproval(approvalId: string, decision: ApprovalDecision): Promise<AgentPlan>
  prepareLocalToolCall(planId: string, stepId: string): Promise<LocalToolCallEnvelope>
  recordToolOutcome(outcome: ToolOutcome): Promise<AgentRun>
  proposeMemory(runId: string): Promise<MemoryCandidate[]>
  cancelRun(runId: string, reason: string): Promise<AgentRun>
}
```

Frameworks may implement this adapter, but UI, storage, and policy should not depend on framework-specific state shapes.

### Mastra

Use if:

- backend stays TypeScript-first;
- workflow `suspend`/`resume` is enough for approvals;
- Studio/evals speed up iteration;
- deployment is cleaner alongside Vercel/Supabase;
- framework memory can be disabled or kept subordinate.

Concern:

- younger ecosystem;
- approval and long-running durability must be tested under app lifecycle edge cases.

### LangGraph

Use if:

- explicit graph state and interrupts are materially clearer;
- checkpointing/resume semantics are stronger;
- graph/KG work pulls backend toward Python anyway;
- complex conditional flows matter more than TypeScript integration.

Concern:

- Python service gravity;
- LangChain/LangSmith coupling must not leak into product contracts.

### Pydantic AI

Use if:

- typed structured outputs and dependency injection are the priority;
- Graphiti/Python sidecar wins and a Python agent service becomes natural;
- durable execution can be cleanly wrapped with Prefect/Restate/other runner;
- evals and type checking beat workflow-graph ergonomics.

Concern:

- less direct fit for current TS edge gateway;
- human approval/durability needs concrete validation.

### Google ADK

Use if:

- Gemini/GCP becomes strategic;
- ADK's sessions, memory, graph workflows, evals, and action confirmations prove faster;
- Android/Gemini integration materially improves product quality or cost.

Concern:

- Google Cloud gravity;
- avoid coupling Orbit's memory model to ADK memory.

### Recommendation

Do two rounds of POC:

1. **TypeScript local-cloud POC:** Mastra vs a thin custom TypeScript runtime, with Inngest or Trigger.dev for durable jobs.
2. **Python graph POC:** LangGraph vs Pydantic AI if Graphiti is still the KG favorite.

Google ADK stays a serious challenger if Gemini/GCP becomes a product or cost advantage.

Do not put any framework into Android contracts. Android should see `AgentRun`, `AgentPlan`, `ApprovalRequest`, and local tool-call envelopes.

## Durable workflow decision

Orbit needs two workflow systems:

### Device WorkManager

Use for:

- URL hydration;
- local OCR;
- local embedding/backfill;
- action extraction;
- retries while charging/Wi-Fi;
- local sync enqueue.

### Cloud workflow runner

Use for:

- evidence acquisition ladders;
- managed extraction/browser fallback;
- cloud summarization;
- embedding and retrieval indexing;
- memory candidate generation;
- deletion/export sweeps;
- eval batch runs.

Preferred early candidates:

- Inngest if event-driven, Vercel/Supabase-friendly durability is most important.
- Trigger.dev if developer ergonomics and open-source job code are better for the team.

Defer Temporal until:

- Orbit runs long-lived, multi-day workflows;
- duplicate execution becomes unacceptable at external-system scale;
- operations capacity exists.

## Gateway and budget decision

Current Vercel Edge gateway is fine as the Day 2 path, but agent runtime needs richer control.

Required gateway capabilities before broad cloud agent usage:

- per-user budgets;
- per-capability budgets;
- route labels;
- request/call IDs;
- model fallback;
- redaction/no-content logging;
- trace correlation;
- retry policy by error type;
- provider/model allowlists;
- prompt/version metadata;
- cost receipts surfaced to audit/settings.

POC matrix:

| Option | Best reason to choose | Main risk |
| --- | --- | --- |
| Vercel AI Gateway | Simplicity, current path, fast iteration. | Weak per-user/capability budget story. |
| LiteLLM | Strong budgets, virtual keys, spend tracking, fallbacks, Langfuse/OTel. | Operate a proxy and its database. |
| Portkey | Managed governance, guardrails, OTel, prompt management. | Vendor/control tradeoff. |
| Cloudflare AI Gateway | Edge routing, analytics, budgets/rate limits, dynamic routes. | Ecosystem lock-in and logging/privacy validation. |

Recommendation:

- Keep Vercel gateway for existing narrow LLM calls.
- Add `BudgetDecision` and trace IDs to the product contract now.
- POC LiteLLM and Cloudflare against one high-volume path: capture understanding + embeddings.
- POC Portkey if managed governance beats self-hosting.
- Do not adopt OpenRouter as production control plane; keep it for model exploration only.

## Gateway capability expansion

Current request types are insufficient for Round 2/3/4.

Add typed capabilities over time:

| Capability | Purpose | Notes |
| --- | --- | --- |
| `understand_capture` | Evidence-backed summary/entities/limitations. | References EvidenceBundle IDs, not raw everything by default. |
| `explain_relatedness` | Reasons between captures. | Must return cited evidence/reason families. |
| `answer_with_citations` | Ask Orbit synthesis. | Must return answer + citation map + limitations. |
| `draft_candidate_action` | Draft local action proposal. | Produces CandidateAction, not execution. |
| `propose_memory_candidate` | Suggest profile/pattern candidate. | Cannot promote directly. |
| `rerank_retrieval` | Rerank candidate chunks/captures. | No raw full corpus in prompt. |
| `classify_policy` | Budget/sensitivity/risk support. | Must be conservative and inspectable. |

Do not create an open `agent_prompt` endpoint. Every new capability should have a Zod schema and a bounded response shape.

## Observability and traces

Production tracing should be useful without becoming a shadow corpus.

Trace by default:

- run ID;
- capability;
- model/provider;
- token counts;
- cost;
- latency;
- error class;
- retrieval counts;
- evidence IDs/hashes;
- citation counts;
- approval status;
- user feedback label;
- eval scores.

Do not trace by default:

- raw captures;
- screenshots;
- full prompts;
- full retrieved context;
- model outputs containing personal content;
- private URLs with tokens.

Allow content traces only for:

- explicit internal dogfood accounts;
- user-authorized debugging sessions;
- synthetic eval fixtures.

Langfuse should be the main trace/eval/cost spine. Phoenix can be added for retrieval/RAG evals if Langfuse is not enough.

## Security and policy gates

### Before any cloud LLM call

- sensitivity scan complete or intentionally skipped with reason;
- local-only facts removed;
- prompt references only necessary chunks/evidence;
- provider/model allowed for sensitivity;
- user cloud setting allows capability;
- budget decision allows route;
- audit entry created or queued.

### Before any deep acquisition

- user understanding depth allows it or approval request exists;
- domain/source not blocked;
- robots/legal/provider policy reviewed in implementation spec;
- cost budget available;
- result will be labeled with acquisition method and limitations.

### Before any external write

- user sees exact preview;
- action args validate against current schema;
- side effect/reversibility displayed;
- confirmation tap recorded;
- execution stays local;
- outcome and undo window recorded.

## MVP runtime phases

### Phase 0: No new agent framework

Build product contracts first:

- `AgentRun` local table or JSON sidecar;
- `AgentPlan` draft shape;
- `ApprovalRequest` shape;
- cited `CandidateAction` linkage;
- feedback on action drafts;
- trace IDs on current LLM calls.

This can be implemented with ordinary Kotlin/WorkManager and current gateway paths.

### Phase 1: Ask/detail coordinator

Add a lightweight coordinator that:

- retrieves chunks;
- drafts relatedness/action suggestions;
- asks for approval;
- records outcomes;
- never executes without local action service.

Still no general agent runtime.

### Phase 2: Cloud workflow runner

Add Inngest/Trigger-style jobs for:

- evidence acquisition;
- understanding;
- retrieval indexing;
- deletion/export sweeps;
- eval runs.

### Phase 3: Agent framework POC

Run the same scenario through Mastra, LangGraph, Pydantic AI, and maybe ADK:

1. User asks about captures.
2. Runtime retrieves cited context.
3. Runtime detects missing user context.
4. Runtime asks one question.
5. Runtime drafts an action.
6. Runtime waits for approval.
7. Runtime records rejection/confirmation.
8. Runtime proposes a memory candidate.
9. Runtime emits masked trace/eval record.

Choose based on product adapter fit, not demo flash.

### Phase 4: Full `:agent` process

Add dedicated Android process only after the coordinator contract is stable.

Owns:

- prompt assembly;
- consent filter;
- plan state;
- approval gating;
- policy display;
- memory candidate handoff.

Does not own:

- direct network;
- external writes;
- canonical memory database.

## Speckit replanning implications

The next Speckit workflow should not start from `specs/008-orbit-agent` as written. It should first create/refresh specs in this order:

1. **Capture understanding contracts**: Round 2 objects.
2. **Retrieval and feedback contracts**: Round 3 layers 2-3.
3. **Ask Orbit citations**: answer/retrieval records over chunks/evidence.
4. **Candidate actions v2**: action drafts grounded in evidence.
5. **Approval request runtime**: Round 4 approval contracts.
6. **Memory candidates/inspector**: before promoted memory and KG.
7. **Cloud workflow/gateway controls**: budgets, traces, capability expansion.
8. **KG/memory backend POC**: Graphiti/Zep/Mem0/Supabase adapter.
9. **Agent coordinator**: no autonomous executor.
10. **Full `:agent` planner/executor**: after the above gates pass.

This order preserves momentum: users get understandable captures, Ask Orbit citations, relatedness, and action drafts before the full agent exists.

## Round 4 hard gates

Do not ship a cloud-augmented agent runtime until these are true:

- every answer/action/memory has citations or explicit limitations;
- user can see and reject an approval request before any external write;
- local-only/sensitive content is filtered before `:net`;
- per-user/per-capability budget is recorded;
- production traces do not contain raw content by default;
- deletion invalidates agent runs, plans, memory candidates, and traces linked to deleted captures;
- action execution remains local;
- framework memory is not user-visible canonical memory;
- evals cover approval, refusal, deletion, and sensitive data cases.

## What Round 5 should do

Round 5 should stop expanding architecture and turn this into a Speckit-ready implementation map:

- spec rewrite order;
- branch stacking plan;
- table/migration order;
- API contract order;
- test gates per branch;
- what existing specs should be amended versus replaced;
- what can ship to internal dogfood first.

The next step is no longer another vendor landscape pass. It is [Round 5](orbit-agent-architecture-round-5-2026-05-12.md): a build plan that converts these rounds into a coherent Speckit workflow.
