# Orbit Agent Architecture Round 6 - 2026-05-12

This is Round 6 of the deeper replanning sequence. Round 5 turned the architecture into a build order. Round 6 turns that order into a Speckit handoff packet.

The goal is not to add another architecture layer. The goal is to make the next feature-planning workflow hard to misread.

Related docs:

- [Orbit Agent Architecture Round 1 - 2026-05-12](orbit-agent-architecture-round-1-2026-05-12.md)
- [Orbit Agent Architecture Round 2 - 2026-05-12](orbit-agent-architecture-round-2-2026-05-12.md)
- [Orbit Agent Architecture Round 3 - 2026-05-12](orbit-agent-architecture-round-3-2026-05-12.md)
- [Orbit Agent Architecture Round 4 - 2026-05-12](orbit-agent-architecture-round-4-2026-05-12.md)
- [Orbit Agent Architecture Round 5 - 2026-05-12](orbit-agent-architecture-round-5-2026-05-12.md)
- [Android Architecture Verification - 2026-05-13](android-architecture-verification-2026-05-13.md)
- [Product Roadmap Audit - 2026-05-12](product-roadmap-audit-2026-05-12.md)
- [Capture Understanding Stack Research - 2026-05-12](capture-understanding-stack-research-2026-05-12.md)
- [Agent Stack Landscape Research - 2026-05-12](agent-stack-landscape-research-2026-05-12.md)

## Round 6 conclusion

The next successful move is not "build the agent." It is a controlled Speckit reset with three lanes:

1. **Close current branch debt**: finish/split/land 015 and 017, reconcile stale 003/013 task status, keep 014 as gateway baseline.
2. **Reset product truth**: update README/constitution/PRD/spec status language so the repo no longer promises local-only/BYOC-first/no-Orbit-server behavior.
3. **Generate the first new feature spec**: capture understanding, with source identity, evidence, summaries, depth controls, deletion, and honest limitations.

Everything after that should be generated in narrow Speckit waves. Each wave must produce one user-visible increment and one trust boundary improvement.

The minimum viable future is:

```text
Trusted understood captures
  -> cited retrieval
  -> evidence-grounded action drafts
  -> user-visible memory controls
  -> budgeted cloud capabilities
  -> backend memory POC
  -> approval-first coordinator
```

The implementation should not try to compress this into one epic. That would recreate the stall.

## What Round 6 adds beyond Round 5

Round 5 chose the build order. Round 6 specifies how to run the next planning loop:

- exact Speckit prompts;
- expected generated artifacts;
- review gates after `specify`, `plan`, `tasks`, and `analyze`;
- branch naming and PR stacking rules;
- stale-task reconciliation rules;
- dogfood scripts;
- scope stop signs;
- decision records that must be written before implementation starts.

## Handoff principles

1. **One feature, one source of truth.** A Speckit feature should own its spec, plan, tasks, contracts, quickstart, and acceptance gates.
2. **No hidden architecture changes.** If a task needs a new table, API type, process boundary, or cloud call, it must appear in the spec and plan.
3. **Contracts before UI polish.** UI can be good, but the next features live or die on evidence, citations, deletion, approval, and feedback contracts.
4. **Trust surface before automation.** Users must see what Orbit knows, why it thinks that, and how to correct it before Orbit plans more.
5. **Every cloud capability has a local fallback story.** The fallback can be limited, but it must be named.
6. **No agent memory without user memory controls.** Candidate memory can exist early; promoted memory needs an inspector and delete/forget behavior.
7. **Reconcile before replacing.** If code exists but task checkboxes are stale, audit reality first instead of reimplementing.

## Lane 0: branch debt closure packet

This lane is operational cleanup, not a new feature spec.

### Lane 0 branch inputs

Known branch/worktree state from repo memory:

- `qa/015-017-stacked` is the integration branch.
- `015-phase1-cluster-surface` has visual refit work staged in its split worktree.
- `017-capture-feedback-actions` has duplicate fallback/Already Saved work staged in its split worktree.
- 016 is closed and should stay closed unless a regression appears.
- S24 and Tab S9 were used for physical QA of the stacked work.

### Lane 0 required checks

Before starting new feature implementation:

1. Run `git status --short` in the main and split worktrees.
2. Confirm whether 015 and 017 PRs are already open, landed, or still staged locally.
3. Preserve the standard Android gate result per split branch.
4. Decide whether remaining 015 physical/manual gates block PR or become explicit follow-ups.
5. Document current Room schema version and downgrade warning.
6. Do not carry new Round 6+ implementation work on `qa/015-017-stacked`.

### Lane 0 stale task audit

The next agent should reconcile, not blindly execute, these stale-looking areas:

| Spec | Drift to check | Expected action |
| --- | --- | --- |
| 003 Orbit Actions | `ActionExecutorService` and invoker tasks appear unchecked although code exists. | Mark complete only after code/test reality is verified, or create targeted follow-up tasks. |
| 003 Orbit Actions | Many physical-device and negative-path tests remain open. | Decide blocker vs post-Demo-Day hardening. |
| 013 Cloud LLM routing | Android gateway/router/provider tasks appear unchecked although code exists. | Verify files/tests and update status. |
| 013 Cloud LLM routing | Supabase provisioning/migrations/smoke tasks appear unchecked although files exist. | Verify production/project reality and update status. |
| 014 Edge gateway | No checkbox tasks, but status log says complete. | Treat as complete baseline plus follow-up list. |

### Lane 0 exit criteria

- The active working branch for new planning is clean or intentionally docs-only.
- The split PR status is known.
- Stale tasks are labeled as complete, deferred, or real remaining work.
- No one starts capture-understanding implementation while branch debt is ambiguous.

## Lane 1: product truth reset packet

This lane may be a direct docs patch instead of a full Speckit workflow. Use Speckit only if the team wants status/acceptance discipline around documentation.

### Lane 1 target branch

`018-product-truth-reset`

If 018 is reserved for capture understanding, use `docs-product-truth-reset` and let the next numbered spec be capture understanding.

### Lane 1 files to inspect

- [README.md](../README.md)
- `.specify/memory/constitution.md`
- `.specify/memory/PRD.md`
- `.github/copilot-instructions.md`
- `specs/004-ask-orbit/spec.md`
- `specs/005-cloud-boost-byok-llm/spec.md`
- `specs/006-orbit-cloud-storage/spec.md`
- `specs/007-knowledge-graph/spec.md`
- `specs/008-orbit-agent/spec.md`
- `specs/009-byoc-sovereign-storage/spec.md`
- `specs/012-resolution-semantics/spec.md`

### Lane 1 copy changes

Replace stale claims:

| Stale wording | Replacement meaning |
| --- | --- |
| local-only | local-first/cloud-augmented |
| no Orbit servers | Orbit Cloud is an opt-in managed capability layer |
| BYOK/BYOC first | BYOK/BYOC are later power-user/control tiers |
| nothing leaves device unless opt in | local kill switch plus capability-specific consent and cloud-mode controls |
| cloud as escape hatch only | cloud as bounded understanding/retrieval/LLM layer |
| private KG first | memory ladder first, KG after deletion/export/tenant gates |

### Lane 1 required decisions

Write these decisions explicitly in the docs patch:

1. What is the default state for cloud mode in dogfood builds?
2. What is the visible user promise for local-only mode?
3. What cloud actions are allowed without extra per-request confirmation?
4. What always requires separate consent?
5. What is the current privacy sentence for public-facing README copy?

Recommended answers:

- Dogfood cloud mode can be on by default only for users who join the dogfood/cloud build and authenticate.
- Local-only mode remains a durable kill switch with reduced capability.
- Public URL hydration/summary and retrieval can use cloud if the cloud mode is enabled and the capture is not sensitive.
- Logged-in browsing, connected-account access, external writes, sensitive screenshot VLM, and deep enrichment require separate consent.
- README should say Orbit is local-first, cloud-augmented, and explicit about what leaves the device.

### Lane 1 exit criteria

- Public README and internal planning docs no longer contradict each other.
- Future specs know which old assumptions are stale.
- The next Speckit feature can cite current product truth without re-litigating the pivot.

## Lane 2: capture understanding Speckit packet

This is the first real new product feature after cleanup.

### Lane 2 target spec

Preferred:

```text
specs/018-capture-understanding/
```

Alternative if 018 is taken:

```text
specs/019-capture-understanding/
```

### Lane 2 feature boundary

In scope:

- source identity;
- evidence bundle;
- capture understanding;
- Basic/Smart/Deep depth policy;
- deletion/invalidation;
- capture detail display of source/evidence/summary/limitations;
- URL hydration compatibility;
- source/summary feedback;
- tests for YouTube/app identity correctness;
- additive Room sidecars.

Out of scope:

- full Ask Orbit;
- KG;
- memory inspector;
- agent coordinator;
- autonomous browsing;
- logged-in browser automation;
- external writes;
- new generalized agent gateway.

### Lane 2 specify prompt

Use this as the first Speckit prompt:

```text
Create a feature specification for Orbit capture understanding. Orbit is an Android app that is local-first and cloud-augmented. The feature introduces first-class SourceIdentity, EvidenceBundle, CaptureUnderstanding, UnderstandingDepthPolicy, UnderstandingJob, and deletion/invalidation behavior. It must keep current URL hydration compatibility, add sidecar storage rather than destructive rewrites, preserve :net as the only network egress process, show source/evidence/summary/limitations in capture detail, support Basic/Smart/Deep user controls, accept per-capture overrides, and avoid claiming more than the evidence supports. It must include feedback for wrong source or wrong summary. Do not implement Ask Orbit, KG, memory inspector, full agent runtime, logged-in browsing, or external writes in this feature.
```

### Lane 2 plan prompt additions

When running the planning step, add:

```text
Ground the plan in the existing Android process split: :capture for overlay/local action execution, :ml for Room/local processing/audit writes, :net as sole network egress, :ui for Diary/detail/settings. Use additive Room sidecar tables. Mirror any cloud request/response types with TypeScript/Zod schemas only if the feature needs a new gateway capability. Include migration tests, source identity tests, deletion/invalidation tests, and capture-detail UI tests. Keep browser fallback out of MVP unless explicitly justified.
```

### Lane 2 tasks prompt additions

When generating tasks, add:

```text
Generate dependency-ordered tasks with separate phases for contracts, data model/migrations, repository APIs, source identity resolver, URL hydration compatibility, cloud gateway schema if needed, capture detail UI, settings/depth controls, feedback, tests, physical-device QA, and documentation. Mark tasks parallel only when they touch disjoint files. Include explicit acceptance tasks for YouTube URL formats, app label resolution, metadata-only limitation display, deletion cascade, and :net-only network egress.
```

### Lane 2 analyze checklist

After `speckit.analyze`, reject the generated artifacts if any of these are true:

- The spec promises full web-page understanding without evidence acquisition limits.
- The plan adds network calls outside `:net`.
- The tasks rewrite `ContinuationResultEntity` destructively instead of adding sidecars.
- There is no deletion/invalidation contract.
- There is no limitation display in capture detail.
- Source identity still uses category-only YouTube detection.
- Basic/Smart/Deep are described as vendor menus instead of user intent controls.
- Tests do not cover the S24/Tab S9 unsupported/hydration confusion that started this sequence.

## Lane 3: retrieval and Ask citations packet

Run this only after Lane 2 has at least a stable spec/plan.

### Lane 3 target spec

Replace or fully expand:

```text
specs/004-ask-orbit/
```

### Lane 3 specify prompt

```text
Rewrite Ask Orbit as a retrieval-and-citations feature, not a chatbot feature. The feature should generate RetrievalChunk records from CaptureUnderstanding, support local keyword/FTS retrieval, optionally sync retrieval chunks to Supabase pgvector with RLS, answer questions with citations and limitations, show related captures with reasons, and collect retrieval feedback. It must not require KG, full agent runtime, or memory inspector to ship. Every answer must cite available evidence or say it cannot answer.
```

### Lane 3 review gates

- Retrieval chunks have source IDs and invalidation fields.
- Answers cannot cite deleted/invalidated evidence.
- Relatedness explanations are visible to users.
- Cloud retrieval is scoped by user and RLS-tested.
- Local fallback exists.
- Eval fixture set is defined before implementation.

## Lane 4: approval runtime packet

Run after capture understanding and retrieval are stable enough to ground action suggestions.

### Lane 4 target specs

Amend:

```text
specs/003-orbit-actions/
specs/008-orbit-agent/
```

### Lane 4 specify prompt

```text
Amend Orbit Actions into CandidateAction v2 and approval runtime. Existing local action execution stays in :capture and remains the only external-write boundary. Candidate actions must cite supporting evidence and CaptureUnderstanding records. ApprovalRequest is required before execution. User edits, rejections, confirmations, undo/cancel, and failures become feedback episodes. Do not add cloud execution, autonomous plans, email/message sending, or a full :agent process in this feature.
```

### Lane 4 review gates

- No external write without ApprovalRequest.
- Action proposal has evidence references.
- Schema mismatch prevents dispatch.
- Rejection/edit creates feedback.
- No network imports in action handlers.
- Existing action task drift is reconciled before new tasks are added.

## Lane 5: memory inspector packet

Run before promoted memory or KG.

### Lane 5 target spec

Create:

```text
specs/021-memory-candidates-inspector/
```

or next available number.

### Lane 5 specify prompt

```text
Create a feature specification for Orbit memory candidates and memory inspector. Orbit should collect feedback episodes from capture corrections, retrieval feedback, action approvals/rejections, and explicit user answers. It can propose MemoryCandidate records, but promoted memory facts require confirmation or strong explicit provenance. The user must be able to inspect, confirm, reject, forget, and suppress inferred memories. Do not implement KG, Graphiti, Zep, Mem0, or full agent memory in this feature.
```

### Lane 5 review gates

- Sensitive memory cannot auto-promote.
- User can see sources for candidates/facts.
- Forget memory behavior is separate from delete source capture behavior.
- Rejected memory is not used in answers/actions.
- Memory usage is explainable in UI.

## Lane 6: cloud controls packet

Run before high-volume enrichment or agent orchestration.

### Lane 6 target specs

Amend:

```text
specs/013-cloud-llm-routing/
specs/014-edge-function-llm-gateway/
```

### Lane 6 specify prompt

```text
Create a cloud capability controls feature for Orbit. The gateway must support typed capabilities such as understand_capture, answer_with_citations, draft_candidate_action, propose_memory_candidate, and explain_relatedness. Add BudgetDecision, trace IDs, cost receipts, no-content production traces, route labels, provider/model allowlists, and structured fallback behavior. Preserve :net as the only Android network egress. Do not add generic prompt endpoints or cloud-side external writes.
```

### Lane 6 review gates

- Every capability is typed.
- Production traces omit raw content by default.
- Budget denial produces a user-safe fallback.
- Costs can be inspected per user/capability/day.
- Existing Vercel gateway remains a baseline until replacement is justified.

## Lane 7: KG/backend POC packet

Run only after memory candidates and cloud controls exist.

### Lane 7 target spec

Rewrite:

```text
specs/007-knowledge-graph/
```

### Lane 7 specify prompt

```text
Rewrite Knowledge Graph as a backend POC and deletion/export verification feature. Compare Supabase-only baseline, Graphiti OSS, Zep managed, and Mem0 challenger behind an OrbitMemoryGraph adapter. The POC must prove tenant isolation, deletion invalidation, export, relatedness citation, sensitive auto-promotion blocking, and cost on a dogfood corpus. Do not make any backend canonical until the hard gates pass.
```

### Lane 7 review gates

- Deleting one source invalidates or rescores derived graph state.
- Deleting a user removes all user graph state.
- Export includes source references.
- Relatedness reasons cite evidence.
- Cross-user leakage tests exist.
- Supabase-only baseline is measured, not dismissed.

## Lane 8: coordinator packet

Run after capture understanding, retrieval, approval runtime, memory inspector, and cloud controls are usable.

### Lane 8 target spec

Rewrite/split:

```text
specs/008-orbit-agent/
```

### Lane 8 specify prompt

```text
Rewrite Orbit Agent as an approval-first coordinator MVP. It should create AgentRun and AgentPlan records from a user-invoked Ask Orbit or capture-detail flow, retrieve cited context, draft a plan and candidate actions, ask one clarifying question when needed, wait for approval, hand execution to local action executor, and record feedback. It must preserve :net as network boundary, :capture as local external-write boundary, and product contracts as canonical. Do not add autonomous schedules, cloud external writes, connected-account writes, or multi-agent orchestration.
```

### Lane 8 review gates

- Agent cannot execute without approval.
- Plan cites evidence.
- Consent filter strips local-only/sensitive data before cloud.
- Cancellation leaves no orphan run.
- Framework runtime can be replaced without changing product contracts.

## Speckit review sequence

Use this same review sequence for every lane that uses Speckit.

### Review after specify

Ask:

- Does the spec name the user-visible value in one sentence?
- Does the spec list non-goals as strongly as goals?
- Does every cloud behavior have user consent/control language?
- Does every derived object have source provenance?
- Does deletion/export behavior exist?
- Does the spec avoid implementing later lanes early?

Required output:

```text
SPEC REVIEW: PASS/FAIL
Blocking gaps:
- ...
Scope creep detected:
- ...
Questions to answer before plan:
- ...
```

### Review after plan

Ask:

- Does the plan respect Android process boundaries?
- Are migrations additive?
- Are Kotlin and TypeScript contracts kept in sync where needed?
- Are tests assigned to the right layer?
- Is there a dogfood path before full rollout?
- Are fallback states named?

Required output:

```text
PLAN REVIEW: PASS/FAIL
Architecture violations:
- ...
Missing gates:
- ...
Implementation risks:
- ...
```

### Review after tasks

Ask:

- Are tasks dependency ordered?
- Are `[P]` tasks actually parallel-safe?
- Are tests created before or alongside implementation?
- Are physical-device gates explicit?
- Are old stale tasks reconciled before new tasks depend on them?
- Does each task name concrete files or modules when possible?

Required output:

```text
TASK REVIEW: PASS/FAIL
Reordering needed:
- ...
Missing tasks:
- ...
Stale-task conflicts:
- ...
```

### Review after analyze

Ask:

- Did analyze find contradictions between spec, plan, and tasks?
- Did any acceptance criterion lose its test task?
- Did any non-goal reappear as an implementation task?
- Did any cloud/network behavior escape consent and `:net` boundaries?

Required output:

```text
ANALYZE REVIEW: PASS/FAIL
Must fix before implementation:
- ...
Safe to implement:
- yes/no
```

## Anti-drift ledger

Create a small ledger entry at the top or bottom of each future spec after generation:

```text
## Architecture Alignment

- Source planning docs: Round 2, Round 3, Round 4, Round 5, Round 6.
- Product truth: local-first/cloud-augmented; local kill switch; cloud capability controls.
- Process boundary: :net is sole network egress; :capture owns local external-write execution.
- Trust rule: evidence/citations/limitations before automation.
- Explicit non-goals: [list for this feature].
```

This small section prevents old local-only text or premature agent scope from quietly returning.

## Branch naming rules

Use branches that match the spec number and user-visible capability.

Suggested sequence:

| Work | Branch |
| --- | --- |
| Product truth reset | `018-product-truth-reset` or `docs-product-truth-reset` |
| Capture understanding | `018-capture-understanding` or `019-capture-understanding` |
| Ask citations | `004-ask-citations` |
| Approval runtime | `020-approval-runtime-actions` |
| Memory inspector | `021-memory-candidates-inspector` |
| Cloud controls | `022-cloud-capability-controls` |
| KG POC | `023-memory-backend-poc` |
| Agent coordinator | `024-agent-coordinator` |

Rules:

- Do not mix docs truth reset with app-code feature implementation unless the diff is tiny and intentional.
- Do not stack future feature work on `qa/015-017-stacked`.
- Keep cloud schema migrations separate from Android UI polish where possible.
- If a branch touches Supabase RLS, include SQL smoke results in the PR notes.

## PR description template

Use this template when each branch is ready:

```text
## What changed

- ...

## Why this branch exists

- Lane: ...
- Source docs: Round 5, Round 6, relevant spec.

## Trust boundaries

- Network egress: ...
- External writes: ...
- User consent/control: ...
- Deletion/export behavior: ...

## Verification

- [ ] Unit tests
- [ ] Room/instrumented tests
- [ ] Supabase SQL smoke if applicable
- [ ] Android gate
- [ ] Physical-device smoke if applicable
- [ ] Speckit analyze clean

## Known deferrals

- ...
```

## Dogfood scripts

These are user-level scripts, not automation scripts.

### Dogfood script for capture understanding

1. Capture a normal public article URL.
2. Capture a YouTube link in at least three formats: `youtube.com/watch`, `youtu.be`, Shorts.
3. Capture a page whose metadata is weak.
4. Capture a screenshot without URL context.
5. Open capture detail for each.
6. Check source identity, evidence, summary, limitations, and feedback controls.
7. Toggle Basic/Smart/Deep and repeat one capture.
8. Delete one capture and verify derived understanding is invalidated.

Pass if Orbit is honest about what it knows and does not know.

### Dogfood script for Ask citations

1. Capture 10 related items across two domains.
2. Ask a question that should retrieve three of them.
3. Ask a question that cannot be answered from captures.
4. Open citations and verify source capture details.
5. Mark one result irrelevant.
6. Ask again and inspect ranking behavior.

Pass if answers are cited, limited, and correctable.

### Dogfood script for approval runtime

1. Capture a calendar/event-like item.
2. Let Orbit draft an action.
3. Edit the draft.
4. Approve execution.
5. Undo/cancel within the supported window.
6. Try a schema mismatch or unsupported action path.

Pass if nothing writes externally without approval and failures are understandable.

### Dogfood script for memory inspector

1. Correct a source identity.
2. Reject a bad related capture.
3. Approve one action draft.
4. Answer one explicit profile question.
5. Open memory inspector.
6. Confirm one candidate and reject one candidate.
7. Forget one memory and ask a question that might have used it.

Pass if the user can see and control what Orbit remembers.

## No-go conditions

Stop a branch before implementation if any of these appears:

- A spec adds generic `agent_prompt` or `run_agent` API before typed capabilities.
- A task adds network access outside `:net`.
- A plan uses KG as canonical storage before memory inspector and deletion tests.
- A generated summary can be shown without evidence or limitation metadata.
- A user profile fact can promote without source provenance or confirmation policy.
- An external write can happen from cloud or without approval.
- A cloud trace stores raw capture content by default.
- A task rewrites the primary capture schema when a sidecar can work.
- A new framework owns product memory semantics.

## Open questions to defer deliberately

These are real questions, but they should not block the first capture-understanding spec:

1. Whether Orbit uses LiteLLM, Portkey, Cloudflare AI Gateway, or Vercel-only long term.
2. Whether Graphiti, Zep, Mem0, or Supabase-only becomes canonical memory backend.
3. Whether browser rendering is in Smart or Deep mode for dogfood.
4. Whether BYOC returns in a premium/control tier.
5. Whether a dedicated `:agent` process is needed for MVP coordinator.
6. Whether conversations become first-class captures or separate memory episodes.
7. Whether manual compose ships before or after capture understanding.

Deferring these is not avoidance. It keeps the first feature shippable.

## Decisions that must not be deferred

These must be answered before implementation tasks start:

1. What exact data leaves the device in Smart mode?
2. What exact data leaves the device in Deep mode?
3. What does Basic mode do with URL-only captures?
4. How does a user turn cloud understanding off?
5. What UI copy appears when evidence is metadata-only?
6. What happens to derived understanding after capture deletion?
7. What is the source identity precedence order?
8. What is the test corpus for YouTube/app recognition?

## First implementation-ready target

The first implementation-ready feature after cleanup should be extremely narrow:

```text
Capture Understanding MVP:
- sidecar SourceIdentity + EvidenceBundle + CaptureUnderstanding tables;
- source identity resolver tests;
- URL hydration compatibility;
- capture detail displays source, evidence, summary, limitations;
- Basic/Smart/Deep setting exists but Deep can be disabled/not implemented behind a clear state;
- wrong source/wrong summary feedback is recorded;
- deletion invalidates sidecars;
- no KG, no Ask, no agent coordinator.
```

If this feels small, good. It is the foundation that every later capability cites.

## Round 6 final handoff

The next concrete move is:

```text
1. Confirm 015/017 branch status.
2. Apply or spec the product truth reset.
3. Run speckit.specify for capture understanding with the Lane 2 prompt.
4. Review specify output using the Round 6 review gates.
5. Run speckit.plan, speckit.tasks, and speckit.analyze only after the prior gate passes.
6. Implement only after analyze is clean.
```

Round 6 is intentionally procedural. The architecture is now rich enough. The risk is no longer lack of ideas; it is letting too many correct ideas land in the same branch. This handoff keeps the next move small, testable, and aligned with the product truth.
