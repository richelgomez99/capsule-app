# Orbit Agent Architecture Round 5 - 2026-05-12

This is Round 5 of the deeper replanning sequence. Rounds 1-4 established the product and architecture direction:

- Round 1: build trustworthy capture/memory substrate before a general-purpose agent.
- Round 2: split continuation output into source identity, evidence, understanding, retrieval, relatedness, action, audit, deletion, and eval contracts.
- Round 3: treat memory as a ladder; ship capture/retrieval/feedback before promoted profile facts, KG, or agent memory.
- Round 4: build an approval-first runtime; keep `:net` as sole network egress and local AppFunction execution as the external-write boundary.

Round 5 stops expanding the architecture. It turns the prior rounds into a Speckit-ready implementation map:

- what to finish first;
- what specs to amend versus replace;
- what branch stack to create;
- what tables/contracts/APIs/tests land in each branch;
- what gets dogfooded first;
- what gates prevent the app from drifting back into stale local-only or opaque-agent assumptions.

Related docs:

- [Orbit Agent Architecture Round 1 - 2026-05-12](orbit-agent-architecture-round-1-2026-05-12.md)
- [Orbit Agent Architecture Round 2 - 2026-05-12](orbit-agent-architecture-round-2-2026-05-12.md)
- [Orbit Agent Architecture Round 3 - 2026-05-12](orbit-agent-architecture-round-3-2026-05-12.md)
- [Orbit Agent Architecture Round 4 - 2026-05-12](orbit-agent-architecture-round-4-2026-05-12.md)
- [Orbit Agent Architecture Round 6 - 2026-05-12](orbit-agent-architecture-round-6-2026-05-12.md)
- [Product Roadmap Audit - 2026-05-12](product-roadmap-audit-2026-05-12.md)
- [Capture Understanding Stack Research - 2026-05-12](capture-understanding-stack-research-2026-05-12.md)
- [Agent Stack Landscape Research - 2026-05-12](agent-stack-landscape-research-2026-05-12.md)
- [Agent Stack POC Research - 2026-05-12](agent-stack-poc-research-2026-05-12.md)

## Round 5 conclusion

The app needs a controlled reset of planning truth before more coding.

Do not start the next build phase by opening `specs/008-orbit-agent` and implementing a planner. That spec is now directionally useful but structurally premature. The build should instead proceed through a staged Speckit stack:

1. Finish and land the current 015/016/017 QA stack.
2. Reconcile cloud-pivot docs and stale README/spec claims.
3. Write a new capture-understanding spec from Round 2.
4. Write a retrieval/Ask Orbit citations spec from Rounds 2-3.
5. Write an approval/candidate-action runtime spec from Round 4.
6. Write memory-candidate/inspector spec from Round 3.
7. Only then reopen KG/backend POCs and full agent runtime.

The user-visible product sequence should be:

```text
Understand captures -> show evidence -> retrieve with citations -> explain relatedness -> draft next steps -> ask/answer with citations -> remember only with confirmation -> execute only with approval
```

## Current repo state that matters

Task inventory from existing `tasks.md` files:

| Spec | Task count | Done | Open | Round 5 interpretation |
| --- | ---: | ---: | ---: | --- |
| 001 core capture overlay | 47 | 47 | 0 | Shipped foundation. Do not reopen except regressions. |
| 002 intent envelope/diary | 199 | 190 | 9 | Mostly done; open items are physical-device/demo/stretch cluster tasks. Treat as QA/dogfood, not new architecture. |
| 003 Orbit Actions | 113 | 84 | 29 | Substantial implementation exists, but checkbox drift and physical-device/test gates remain. Reconcile before agent work. |
| 013 cloud LLM routing | 28 | 14 | 14 | Code appears ahead of task checkboxes in places. Needs reconciliation and closure or rebaseline. |
| 014 edge function gateway | 0 checkbox tasks | n/a | n/a | Status log says Phase I complete. Keep as baseline, but add follow-up for future gateway expansion. |
| 015 visual refit | 64 | 50 | 14 | Current QA split work. Finish/land before new Speckit branches. |
| 016 intent set migration | 17 | 17 | 0 | Complete. Keep labels from repo memory, not stale old text. |
| 017 capture feedback actions | 30 | 30 | 0 | Complete/split; land with QA stack. |

Important repo memory:

- Current integration branch is `qa/015-017-stacked`.
- Split worktrees already exist for 015 and 017.
- 015 visual refit and 017 duplicate feedback changes passed the standard Android gate in split worktrees.
- README is stale: it still promises local-only/BYOK/BYOC-first/no-Orbit-server posture.
- Product truth is now local-first/cloud-augmented with an explicit local/Nano kill switch.

## Non-negotiable build rules

1. **Do not implement full agent first.** Build substrate and trust surfaces first.
2. **Do not keep stale local-only copy.** Update README/spec framing before external users see the app.
3. **Do not silently broaden network egress.** `:net` remains sole network boundary.
4. **Do not make graph memory canonical before deletion tests.** Supabase/retrieval baseline first.
5. **Do not promote profile facts from sparse data.** Candidate memory before promoted memory.
6. **Do not add open-ended LLM endpoints.** Every cloud capability gets typed request/response schema.
7. **Do not ship action execution through cloud.** External writes remain local and user-confirmed.
8. **Do not let framework memory own product truth.** Mastra/LangGraph/Pydantic AI/ADK are implementation experiments behind Orbit contracts.

## Phase 0: Land the current QA stack

Goal: stop carrying visual/duplicate/settings integration as ambient branch debt.

### Branches

| Branch | Contents | Status |
| --- | --- | --- |
| `015-phase1-cluster-surface` | Visual refit, settings/capture overlay/bubble polish. | Split worktree exists and passed standard gate. |
| `017-capture-feedback-actions` | Duplicate fallback/Already Saved/feedback action changes. | Split worktree exists and passed standard gate. |
| `qa/015-017-stacked` | Integration branch. | Use only as source/reference; do not keep adding future architecture docs there indefinitely. |

### Required tasks

1. Confirm split worktrees are still clean.
2. Finish 015 physical/manual gates or explicitly defer them.
3. Open/prepare PRs in dependency order: 015 first if 017 has UI dependencies, otherwise 017 can go first if isolated.
4. Keep 016 closed; do not create a new 016 branch unless a regression appears.
5. Preserve device QA notes: S24/Tab S9 behavior, Room v7 downgrade warning, corrected Orbit mark philosophy.

### Gates

- Standard Android gate:

```bash
ANDROID_HOME="$HOME/Library/Android/sdk" JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' ./gradlew :app:compileDebugKotlin :app:testDebugUnitTest :app:compileDebugAndroidTestKotlin :app:lintDebug
```

- Manual flag-on screenshots for refitted surfaces.
- S24 + Tab S9 smoke for bubble/settings/capture/duplicate flows.

### Speckit impact

This is not a new Speckit feature. It is closure of current stacked work.

## Phase 1: Documentation truth reset

Goal: make the repo stop telling two product stories.

### Product truth reset branch

`018-product-truth-reset`

### Product truth reset amendments

- [README.md](../README.md)
- `.github/copilot-instructions.md` manual additions if needed
- `.specify/memory/constitution.md` if it still encodes local-only/no-Orbit-server wording
- `.specify/memory/PRD.md` if stale
- specs 004/005/006/007/008/009/012 intro/status sections as needed

### Product truth reset required edits

- Replace `private, local-first personal agent` framing with local-first/cloud-augmented truth.
- Replace `Nothing leaves your device unless you opt in` with precise tier language: local/Nano kill switch, cloud-mode capability controls, explicit consent for sensitive/deep/connected-account cases.
- Replace BYOC-first roadmap with Orbit Cloud default opt-in + BYOC later power-user tier.
- Mark specs 004/006/007/008/012 as needing replanning against Rounds 1-5.
- Link the new planning docs as the current strategy source of truth.

### Product truth reset gates

- Markdown diagnostics clean.
- No code build required unless docs tooling exists.

### Product truth reset acceptance

External readers should understand:

- Orbit is local-first, not local-only.
- Cloud exists to understand/retrieve/summarize, not to silently act.
- User-confirmed local execution remains the action boundary.

## Phase 2: Capture understanding spec

Goal: turn Round 2 and capture-understanding research into the next real Speckit feature.

### Capture understanding branch/spec

`018-capture-understanding-contracts` or `018-understood-captures`

If Phase 1 consumes `018`, number this `019`.

### Capture understanding create or replace

Create a new spec rather than trying to overload spec 002 or 004.

Proposed directory:

```text
specs/018-capture-understanding/
  spec.md
  plan.md
  data-model.md
  quickstart.md
  tasks.md
  contracts/
    source-identity-contract.md
    evidence-bundle-contract.md
    capture-understanding-contract.md
    understanding-depth-contract.md
    deletion-invalidation-contract.md
```

### Capture understanding core objects

- `CaptureSourceIdentity`
- `EvidenceBundle`
- `CaptureUnderstanding`
- `UnderstandingDepthPolicy`
- `UnderstandingJob`
- `UnderstandingAuditEvent`
- `DeletionReceipt`

### Android implementation scope

- Sidecar Room tables, not destructive migration of `ContinuationResultEntity`.
- Source identity resolver centralized and tested.
- URL hydration writes both current compatibility result and new understanding sidecar.
- Capture detail can prefer `CaptureUnderstanding` when present.
- Basic/Smart/Deep settings and per-capture overrides stored locally.
- Evidence limitations surfaced in UI.

### Cloud implementation scope

- No browser/managed extraction in first branch unless spec explicitly includes it.
- Existing `fetchPublicUrl` remains Basic path.
- Cloud LLM summarization can produce understanding when cloud mode is enabled.
- Zod schema additions only if needed for `understand_capture`.

### Capture understanding tests

- Source identity unit tests across YouTube URL formats and app labels.
- No `video` category -> YouTube glyph without provider evidence.
- Room migration test for sidecar tables.
- URL hydration compatibility test.
- Summary limitation tests: metadata-only cannot produce full-content summary.
- Deletion cascade/invalidation test.
- S24/Tab S9 capture detail visual smoke.

### Capture understanding dogfood gate

Internal dogfood can start when:

- capture detail shows source/evidence/summary/limitations for URL captures;
- user can mark source/summary wrong;
- Basic mode works without cloud;
- Smart mode cloud summary works on public URLs.

## Phase 3: Retrieval chunks and Ask Orbit citations

Goal: make captures findable and answerable with citations before KG.

### Retrieval citations branch/spec

`019-retrieval-citations` or next number after Phase 2.

### Retrieval citations amend vs replace

Replace `specs/004-ask-orbit/spec.md` stub with a full PRD, but keep the directory and spec number as Ask Orbit's home.

### Retrieval citations contracts

```text
specs/004-ask-orbit/contracts/
  retrieval-chunk-contract.md
  retrieval-query-record-contract.md
  ask-answer-citation-contract.md
  related-capture-explanation-contract.md
```

### Retrieval citations core objects

- `RetrievalChunk`
- `RetrievalQueryRecord`
- `AskOrbitAnswer`
- `CitationMap`
- `RelatedCaptureExplanation`
- `RetrievalFeedbackEpisode`

### Retrieval citations Android scope

- Local FTS/keyword over source labels, titles, summaries, notes, OCR/excerpts.
- Retrieval chunks generated from `CaptureUnderstanding`.
- Ask Orbit initial surface can be text-only.
- Answer card must show citations and limitations.
- Related captures in capture detail must include display reasons.

### Retrieval citations Cloud/Supabase scope

- Supabase `retrieval_chunks` table with RLS.
- pgvector baseline for cloud retrieval.
- No Qdrant until eval says pgvector is insufficient.
- Hybrid query with user/date/provider/intent/resolution filters.

### Retrieval citations gateway scope

- Add typed `answer_with_citations` only when cloud synthesis is needed.
- Add `rerank_retrieval` only after baseline retrieval exists.

### Retrieval citations tests

- Retrieval chunks are invalidated when capture/evidence deleted.
- Ask answer never cites absent evidence.
- Metadata-only capture answer includes limitation.
- Relatedness reason exists for every related card.
- Cross-user RLS SQL smoke for `retrieval_chunks`.
- Eval fixture set from Round 2/3.

### Retrieval citations dogfood gate

- User can ask `what was that thing I saved about X?`
- Answer cites captures.
- Related captures feel explainable in detail view.

## Phase 4: Candidate actions v2 and approval runtime

Goal: turn action extraction into evidence-grounded drafts and future agent plans without changing the local execution boundary.

### Approval runtime branch/spec

`020-approval-runtime-actions`

### Approval runtime amendments

- `specs/003-orbit-actions/`
- Future portions of `specs/008-orbit-agent/`

### Approval runtime contracts

```text
specs/003-orbit-actions/contracts/
  candidate-action-contract.md
  approval-request-contract.md
  local-tool-call-envelope-contract.md
  action-feedback-contract.md
```

### Approval runtime core objects

- `CandidateAction`
- `CandidateActionEnvelope`
- `ApprovalRequest`
- `LocalToolCallEnvelope`
- `ActionFeedbackEpisode`
- `ActionEdit`

### Approval runtime implementation scope

- Bridge existing `action_proposal` to `CandidateActionEnvelope` semantics.
- Attach evidence/understanding IDs to action proposals.
- Require `ApprovalRequest` before execution.
- Add full JSON Schema validation before dispatch if dependency is acceptable.
- Preserve `ActionExecutorService` in `:capture` as external-write executor.
- Record action edits/rejections as feedback episodes.

### Approval runtime out of scope

- Full `:agent` process.
- Cloud execution.
- Email/message sending.
- Connected-account writes.

### Approval runtime tests

- External write never happens without approval.
- Args schema mismatch prevents Intent dispatch.
- Action proposal cites supporting evidence.
- Action edit produces feedback episode.
- Undo/cancel writes corrective outcome.
- No network classes reachable from action handlers.

### Approval runtime dogfood gate

- Calendar/to-do/share drafts show source evidence.
- User can confirm/reject/edit draft.
- Local execution continues to work on S24/Tab S9.

## Phase 5: Memory candidates and inspector MVP

Goal: give users a trust surface before promoted memory or KG.

### Memory inspector branch/spec

`021-memory-candidates-inspector`

### Memory inspector create

New spec is better than overloading KG.

```text
specs/021-memory-candidates-inspector/
  spec.md
  plan.md
  data-model.md
  quickstart.md
  tasks.md
  contracts/
    user-feedback-episode-contract.md
    memory-candidate-contract.md
    promoted-memory-fact-contract.md
    memory-inspector-contract.md
    forget-memory-contract.md
```

### Memory inspector core objects

- `UserFeedbackEpisode`
- `MemoryCandidate`
- `PromotedMemoryFact`
- `MemoryInspectorItem`
- `ForgetMemoryRequest`
- `MemorySuppressionRule`

### Memory inspector implementation scope

- Settings -> Orbit Memory.
- Show candidates and confirmed facts separately.
- Allow forget/keep local/never infer this.
- Promote only user-declared or user-confirmed facts in MVP.
- Rejections/corrections influence retrieval ranking.

### Memory inspector out of scope

- Graphiti/Zep/Mem0 production adoption.
- Automatic sensitive fact promotion.
- Advanced ontology editing.

### Memory inspector tests

- Sensitive candidates require confirmation.
- Rejected candidate is not used in answer/action draft.
- Forget memory does not delete source captures by default.
- Delete source capture invalidates memory candidate if it was the only support.
- Memory inspector shows sources and last-used metadata.

### Memory inspector dogfood gate

- User can see what Orbit might remember.
- User can confirm or reject memory.
- Ask/action drafts can explain when memory was used.

## Phase 6: Cloud workflow and gateway controls

Goal: make cloud use traceable and budgeted before broader agent work.

### Cloud controls branch/spec

`022-cloud-capability-controls`

### Cloud controls amendments

- `specs/013-cloud-llm-routing/`
- `specs/014-edge-function-llm-gateway/`
- Supabase migrations/contracts.

### Cloud controls contracts

```text
specs/014-edge-function-llm-gateway/contracts/
  gateway-capability-v2-contract.md
  budget-decision-contract.md
  trace-redaction-contract.md
  cost-receipt-contract.md
```

### Cloud controls required capabilities

- `understand_capture`
- `answer_with_citations`
- `explain_relatedness`
- `draft_candidate_action`
- `propose_memory_candidate`
- `rerank_retrieval` later

### Gateway controls

- per-user budgets;
- per-capability budgets;
- route labels;
- trace IDs;
- model fallback;
- no-content production traces;
- provider/model allowlists;
- prompt/version metadata;
- cost receipts.

### POC order

1. Keep current Vercel gateway for existing calls.
2. Add `BudgetDecision` rows/logging independent of gateway vendor.
3. POC LiteLLM and Cloudflare AI Gateway on capture understanding + embeddings.
4. POC Portkey if managed governance is attractive.
5. Do not use OpenRouter as production control plane.

### Cloud controls tests

- Budget denial returns structured fallback.
- Trace has IDs/cost/model labels but no raw content by default.
- Gateway schemas reject malformed capability requests.
- RLS/cost views scoped per user.
- Delete capture invalidates trace/eval content refs.

## Phase 7: KG/memory backend POC

Goal: choose memory backend after product memory contracts exist.

### KG backend branch/spec

`023-memory-backend-poc`

### KG backend amendments

- Rewrite `specs/007-knowledge-graph/` against Round 3.
- Keep old temporal/provenance ideas, but stop presenting KG as the first memory substrate.

### POC backends

1. Supabase-only baseline.
2. Graphiti OSS.
3. Zep managed.
4. Mem0 baseline/challenger.

### Adapter

All backends must implement product semantics:

```kotlin
interface OrbitMemoryGraph {
    suspend fun ingestEpisode(episode: MemoryEpisode): IngestResult
    suspend fun search(query: GraphSearchQuery): GraphSearchResult
    suspend fun explainRelatedness(sourceEnvelopeId: String, targetEnvelopeId: String): RelatednessReason?
    suspend fun proposeMemoryCandidates(input: CandidateProposalInput): List<MemoryCandidate>
    suspend fun promoteMemory(candidateId: String, decision: PromotionDecision): PromotedMemoryFact
    suspend fun invalidateSource(sourceId: String, reason: InvalidationReason): InvalidationReceipt
    suspend fun deleteUser(userId: String): DeletionReceipt
    suspend fun exportUser(userId: String): MemoryExportBundle
}
```

### Hard gates

- zero cross-user leakage;
- unique fact deletion;
- shared fact deletion/re-score;
- no stale node summary containing deleted content;
- export with source IDs;
- relatedness reasons cite evidence;
- sensitive auto-promotion blocked;
- cost acceptable on dogfood corpus.

### Decision output

- Choose one canonical backend path or explicitly defer KG.
- Record why Supabase-only is or is not enough.
- Do not merge framework-specific memory into product contracts.

## Phase 8: Agent coordinator, not full planner

Goal: ship the smallest useful agent-like coordinator after understanding/retrieval/actions/memory exist.

### Agent coordinator branch/spec

`024-agent-coordinator`

### Replace/amend

Rewrite `specs/008-orbit-agent/` into two specs:

1. `Agent Coordinator` for MVP.
2. `Full Agent Planner/Executor` deferred.

### Agent coordinator core objects

- `AgentRun`
- `AgentPlan`
- `ApprovalRequest`
- `ConsentFilterResult`
- `BudgetDecision`
- `LocalToolCallEnvelope`

### MVP coordinator capabilities

- Start from Ask Orbit or capture detail.
- Retrieve cited context.
- Draft one plan with one or more candidate actions.
- Ask one clarifying question when context is missing.
- Wait for user approval.
- Hand local execution to existing action executor.
- Record rejection/confirmation as feedback.
- Optionally propose a memory candidate.

### Agent coordinator out of scope

- Autonomous schedules.
- Multi-agent orchestration.
- Cloud external writes.
- Long-running chained tools.
- Framework lock-in.

### Framework POC

- Mastra vs thin TypeScript runtime first.
- LangGraph vs Pydantic AI only if Python graph service becomes likely.
- ADK remains challenger if Gemini/GCP path becomes strategic.

### Agent coordinator tests

- Agent run cannot execute without approval.
- Consent filter strips local-only memory before `:net`.
- Plan cites evidence.
- Rejection updates feedback memory.
- Cancellation leaves no orphan running state.
- Framework implementation produces same product contracts.

## Phase 9: Full agent runtime

Goal: only after phases 2-8 prove product value and trust.

### Full runtime branch/spec

`025-orbit-agent-runtime`

### Conditions to start

- Ask Orbit with citations is dogfooded.
- Candidate actions v2 are dogfooded.
- Memory inspector exists.
- Cloud budgets/traces exist.
- KG/backend POC decision is recorded.
- Approval-first coordinator works.

### Scope

- dedicated `:agent` process;
- prompt assembly;
- consent filter;
- plan/session records;
- framework runtime adapter;
- durable workflow integration;
- advanced multi-step plans.

### Still out of scope unless separately approved

- silent writes;
- autonomous agents;
- logged-in browsing;
- message/email sending;
- third-party account OAuth beyond explicit connected-account specs.

## Spec amendment matrix

| Existing spec | Round 5 action |
| --- | --- |
| 001 Capture overlay | Leave closed. Regression-only. |
| 002 Intent envelope/diary | Amend only for sidecar references and closure of physical/stretches. Do not overload with understanding/memory. |
| 003 Orbit Actions | Amend for CandidateAction v2, evidence links, ApprovalRequest, action feedback. |
| 004 Ask Orbit | Replace stub with full retrieval/citation spec. |
| 005 Cloud Boost/BYOK | Mark stale; fold into gateway/cloud controls and local/Nano kill switch policy. |
| 006 Orbit Cloud Storage | Reconcile Supabase shared-table/RLS reality vs per-schema prose; add Round 2/3 tables. |
| 007 Knowledge Graph | Rewrite as backend POC + promoted graph memory, not first substrate. |
| 008 Orbit Agent | Split into MVP coordinator and deferred full runtime. |
| 009 BYOC | Defer; keep migration promise but no near-term implementation. |
| 010/015 Visual | Finish/land current refit; no new strategic scope. |
| 011 Manual compose | Keep as product-needed after capture understanding or alongside it; not agent blocker. |
| 012 Resolution semantics | Amend cloud/local language and feed resolution into retrieval/memory. |
| 013 Cloud LLM routing | Reconcile task checkbox drift; keep `:net` boundary. |
| 014 Edge gateway | Keep as Day 2 baseline; add v2 capability/budget contracts later. |
| 016 Intent migration | Closed; preserve current labels. |
| 017 Capture feedback | Closed; use feedback patterns in Round 3/Phase 5. |

## Migration order

Recommended additive Room migration sequence after current Room v7 stack:

1. `capture_source_identity`
2. `evidence_bundle`
3. `capture_understanding`
4. `retrieval_chunk_local`
5. `user_feedback_episode`
6. `candidate_action_v2` or bridge table linking existing `action_proposal` to evidence IDs
7. `approval_request`
8. `memory_candidate`
9. `promoted_memory_fact`
10. `agent_run` / `agent_plan` only after coordinator branch

Recommended Supabase migration sequence:

1. `source_identities`
2. `evidence_bundles`
3. `capture_understandings`
4. `retrieval_chunks` with pgvector HNSW index and RLS
5. `retrieval_query_records`
6. `feedback_episodes`
7. `candidate_actions`
8. `approval_requests`
9. `memory_candidates`
10. `promoted_memory_facts`
11. `budget_decisions` / cost receipts
12. backend-specific KG tables or adapter refs
13. `agent_runs` / `agent_plans`

Rules:

- Every table has `user_id` where cloud-side.
- Every derived table has source IDs and invalidation fields.
- No migration should require rewriting existing captures in place.
- Backfill jobs should be resumable and budgeted.

## API/contract order

1. Kotlin data classes/Room entities for Round 2 objects.
2. TypeScript/Zod schemas mirroring cloud payloads.
3. AIDL/repository methods for sidecar reads/writes.
4. Cloud REST/RPC contracts for sync/retrieval.
5. Gateway capability schemas for model calls.
6. Audit event enums/copy templates.
7. Deletion/export receipt contracts.
8. Eval case schemas.

Do not add cloud APIs before local contracts exist. Otherwise cloud shape will leak into product semantics.

## Test strategy by layer

### Unit

- source identity resolver;
- URL canonicalization/hash;
- evidence limitation rules;
- retrieval scoring/reranking;
- promotion thresholds;
- consent filter;
- budget decision;
- action schema validation.

### Room/instrumented

- additive migrations from production-shaped DB;
- cascade/invalidation behavior;
- sidecar compatibility with existing diary queries;
- action approval/execution transactions;
- memory forget semantics.

### Compose/UI

- capture detail evidence/limitation display;
- relatedness reasons;
- Ask answer citations;
- approval request preview;
- memory inspector.

### Supabase SQL

- RLS for every table;
- cross-user leakage tests;
- delete capture invalidation;
- account delete/export receipt;
- vector query filters.

### Eval

- 100 capture understanding cases from Round 2;
- 80 memory/retrieval cases from Round 3;
- agent approval/refusal/deletion cases from Round 4;
- cost/latency snapshots per capability.

### Physical device

- S24 + Tab S9 baseline smoke;
- capture URL -> understanding -> detail;
- Ask Orbit answer with citations;
- action draft -> approval -> local execution;
- settings controls for Basic/Smart/Deep and memory inspector;
- no unexpected network outside `:net`.

## Dogfood milestones

### Dogfood A: Understood captures

User can capture links/screenshots and see honest source/evidence/summary/limitations.

Minimum:

- source identity correct for top providers;
- Basic/Smart controls;
- capture detail center of gravity;
- feedback buttons for wrong source/summary.

### Dogfood B: Find and connect

User can search/ask over captures with citations and see related captures with reasons.

Minimum:

- retrieval chunks;
- Ask answer with citations;
- related capture cards;
- feedback affects future ranking.

### Dogfood C: Draft next step

User can get candidate actions grounded in evidence and approve local execution.

Minimum:

- candidate actions cite evidence;
- approval preview;
- local AppFunction execution;
- undo/cancel;
- action feedback.

### Dogfood D: Memory controls

User can see, confirm, reject, and forget what Orbit remembers.

Minimum:

- memory candidates;
- confirmed facts;
- inspector source view;
- forget semantics.

### Dogfood E: Coordinator

User can ask Orbit to help with a capture/cluster, get a plan, answer a question, approve/reject, and see what happened.

Minimum:

- `AgentRun`/`AgentPlan`;
- approval-first flow;
- no autonomous execution;
- traces/evals/budgets.

## Release risk controls

### Biggest technical risks

- stale spec/task checkboxes mislead future work;
- Room migration churn with too many sidecar tables;
- cloud schema drifting from Android contracts;
- summary overclaim from weak evidence;
- profile fact over-promotion;
- RLS or graph backend leakage;
- gateway cost surprise;
- action execution approval gaps.

### Mitigations

- Run a Speckit analyze pass after each new spec/task generation.
- Keep migrations additive and sidecar-based.
- Mirror Kotlin and Zod schemas in contract tests.
- Make limitation display a ship gate.
- Start with confirmed-only promoted memory.
- Run multi-user SQL smoke for every cloud table.
- Add `BudgetDecision` before high-volume enrichment.
- Keep external writes local and approval-gated.

## Immediate next Speckit workflow

After this planning-doc sequence, the next concrete workflow should be:

1. Finish/land 015/017 split work.
2. Create a new feature spec for product truth reset if docs are being treated as a feature; otherwise do the docs patch directly.
3. Run `speckit.specify` for `capture-understanding` using Round 2 and capture-understanding research as input.
4. Run `speckit.plan` for that feature.
5. Run `speckit.tasks` for that feature.
6. Run `speckit.analyze` to cross-check spec/plan/tasks.
7. Only then implement.

Suggested prompt for the first new Speckit feature:

```text
Create a feature specification for Orbit capture understanding. Orbit is local-first/cloud-augmented. The feature introduces first-class SourceIdentity, EvidenceBundle, CaptureUnderstanding, UnderstandingDepthPolicy, and deletion/invalidation contracts. It must keep current URL hydration compatibility, add sidecar storage, preserve :net as sole network egress, show evidence/limitations in capture detail, support Basic/Smart/Deep user controls, and avoid claiming more than evidence supports. Do not implement KG or full agent runtime in this feature.
```

## What not to do next

- Do not start coding Graphiti/Zep/Mem0 before retrieval chunks and feedback episodes exist.
- Do not add a generic cloud `agent_prompt` endpoint.
- Do not rewrite the whole Room schema around one new abstraction.
- Do not bury evidence/limitations behind debug UI.
- Do not create a full `:agent` process before approval/runtime contracts are proven.
- Do not ship README/local-only claims unchanged.

## Round 5 final ordering

The practical order is:

```text
Land current QA stack
  -> Docs truth reset
  -> Capture understanding
  -> Retrieval + Ask citations
  -> Candidate actions + approval runtime
  -> Memory candidates + inspector
  -> Cloud gateway/budget controls
  -> KG/memory backend POC
  -> Agent coordinator
  -> Full agent runtime
```

That sequence gives Orbit a path out of the stall. It keeps the product useful at each step and preserves the trust boundaries that make the agent feel like Orbit, not a chatbot taped to a screenshot app. [Round 6](orbit-agent-architecture-round-6-2026-05-12.md) turns this order into concrete Speckit prompts, review gates, and handoff rules.
