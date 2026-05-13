# Orbit Agent Architecture Round 3 - 2026-05-12

This is Round 3 of the deeper replanning sequence. Round 1 set the product direction: do not build a general-purpose agent first; build the trustworthy substrate. Round 2 turned that substrate into contracts for source identity, evidence, understanding, retrieval chunks, relatedness, actions, audit, deletion, and evals.

Round 3 focuses on memory and retrieval beyond a single capture:

- what Orbit should remember;
- what it should not remember yet;
- how retrieval, profile, feedback, and graph memory relate;
- how Graphiti/Zep/Mem0/Supabase-only should be evaluated;
- how deletion/export/correction must work across memory;
- what the user-facing memory inspector should show;
- what Speckit should replan in which order.

Related docs:

- [Orbit Agent Architecture Round 1 - 2026-05-12](orbit-agent-architecture-round-1-2026-05-12.md)
- [Orbit Agent Architecture Round 2 - 2026-05-12](orbit-agent-architecture-round-2-2026-05-12.md)
- [Orbit Agent Architecture Round 4 - 2026-05-12](orbit-agent-architecture-round-4-2026-05-12.md)
- [Product Roadmap Audit - 2026-05-12](product-roadmap-audit-2026-05-12.md)
- [Agent Stack Landscape Research - 2026-05-12](agent-stack-landscape-research-2026-05-12.md)
- [Agent Stack POC Research - 2026-05-12](agent-stack-poc-research-2026-05-12.md)
- [Capture Understanding Stack Research - 2026-05-12](capture-understanding-stack-research-2026-05-12.md)
- [specs/004 Ask Orbit](../specs/legacy/2026-05-13-roadmap-rebaseline/004-ask-orbit/spec.md)
- [specs/006 Orbit Cloud Storage](../specs/legacy/2026-05-13-roadmap-rebaseline/006-orbit-cloud-storage/spec.md)
- [specs/007 Knowledge Graph](../specs/legacy/2026-05-13-roadmap-rebaseline/007-knowledge-graph/spec.md)
- [specs/008 Orbit Agent](../specs/legacy/2026-05-13-roadmap-rebaseline/008-orbit-agent/spec.md)
- [specs/012 Resolution Semantics](../specs/legacy/2026-05-13-roadmap-rebaseline/012-resolution-semantics/spec.md)

## Round 3 conclusion

Orbit should not treat `memory` as one product feature or one vendor system. It needs a staged memory ladder:

1. **Capture memory**: the saved artifact, source identity, evidence, and understanding.
2. **Retrieval memory**: chunks and embeddings that make captures findable.
3. **Feedback memory**: user corrections, dismissals, accepts, rejections, and edits.
4. **Candidate memory**: possible profile facts, patterns, and long-term interests that are not yet trusted.
5. **Promoted memory**: profile facts and patterns that Orbit can safely reuse.
6. **Graph memory**: temporal relationships across captures, entities, actions, and feedback.
7. **Agent memory**: sessions, plans, skills, and execution outcomes.

The early product should ship strong layers 1-3 before pretending to have a smart long-term agent. Layers 4-7 should be introduced only when Orbit can explain source, confidence, deletion, and user control.

The most important product decision:

> Memory promotion must be slower than retrieval.

Ask Orbit can retrieve a capture because the user saved it. That does not mean Orbit should promote a profile fact like `user is interested in X`. A profile fact needs repeated evidence, explicit declaration, accepted suggestions, or a lightweight confirmation question.

## Current specs: useful but need reconciliation

The existing specs contain strong ideas, but they were written before the current cloud/agent pivot and before Round 2's evidence contracts.

### Spec 004 Ask Orbit

Useful:

- answer from the user's corpus;
- cite source envelopes;
- fallback keyword search;
- retrieval + synthesis separation;
- audit every query.

Needs update:

- It assumes retrieval is local by default even when cloud features are now a central product direction.
- It talks about embeddings over envelope text and `ContinuationResult.summary`, not `RetrievalChunk`, `EvidenceBundle`, or `CaptureUnderstanding`.
- It needs explicit citation IDs, evidence limitations, and relatedness reasons.
- It needs a retrieval policy that can choose local/offline, cloud/full-corpus, and KG-backed paths.

### Spec 006 Orbit Cloud Storage

Useful:

- recognizes envelopes, embeddings, KG, profile facts, agent state, plans, skills, and export/delete as one cloud data surface;
- has a schema sketch for `kg_nodes`, `kg_edges`, `episodes`, `profile_facts`, `agent_state`, `plans`, `skills`, and `skill_usage`;
- insists on export/delete and local audit boundaries.

Needs update:

- The prose says per-user schemas/roles; current Supabase migrations use shared tables with `user_id` and RLS. Speckit must reconcile this rather than carry both as implicit truths.
- The schema uses 768-dimensional embeddings while current gateway specs use 1536-dimensional `text-embedding-3-small` vectors.
- It does not include Round 2's sidecar objects: source identity, evidence bundles, capture understandings, retrieval chunks, relatedness explanations, and candidate actions.
- It treats `profile_facts` as a hot-path view, but does not define promotion thresholds or user confirmation mechanics.

### Spec 007 Knowledge Graph

Useful:

- temporal graph with provenance;
- profile as a subgraph, not a separate unrelated store;
- corrections/rejections as negative signal;
- no Neo4j/Apache AGE in the first Postgres-native version;
- user-editable graph semantics.

Needs update:

- The graph should not be the first memory substrate. Evidence-backed retrieval should be first.
- KG edges need explicit links to Round 2 evidence and understanding IDs, not only `episode_id`.
- User-editable ontology is too much for the first real product pass. Use a closed ontology first; advanced ontology editing can wait.
- The graph should be a promoted memory layer, not the only representation of profile, relatedness, and retrieval.

### Spec 008 Orbit Agent

Useful:

- never autonomous;
- user-confirmed external actions;
- session state, patterns, plans, and skills as separate memory layers;
- every action is an episode;
- Settings -> Agent can list/delete patterns and clear session state.

Needs update:

- The full planner/executor should come after capture understanding, retrieval, feedback, Ask Orbit citations, and draft actions.
- Agent memory must not become canonical memory. The product memory layer owns facts; framework memory is only an implementation convenience.
- Pattern promotion should be tied to Round 2 evidence, feedback, and resolution outcomes.

### Spec 012 Resolution Semantics

Useful:

- resolution is the product's success metric;
- abandoned/dismissed is useful signal, not failure;
- generated content becomes first-class envelopes;
- every state transition is an episode;
- no guilt counters.

Needs update:

- Resolution events should feed memory promotion and retrieval ranking.
- Resolution state belongs in `RetrievalChunk.metadata` and KG edges once cloud sync is enabled.
- The local-only v1 language conflicts with the current cloud pivot. Keep local-first controls, but allow cloud sync under explicit cloud category settings.

## Memory ladder

### Layer 1: Capture memory

Objects:

- `IntentEnvelope`;
- `CaptureSourceIdentity`;
- `EvidenceBundle`;
- `CaptureUnderstanding`;
- original artifact and user note.

Purpose:

- answer `what did I save?`;
- preserve the original thing;
- explain source, evidence, summary, limitations.

Promotion rule:

- Every capture belongs here immediately.
- No profile or preference inference happens at this layer.

Deletion:

- Delete capture deletes or invalidates all derived objects rooted only in that capture.

### Layer 2: Retrieval memory

Objects:

- `RetrievalChunk`;
- embedding rows;
- lexical index entries;
- search metadata;
- retrieval feedback.

Purpose:

- make saved captures findable;
- power related captures;
- power Ask Orbit with citations.

Promotion rule:

- Retrieval chunks can be created from evidence and understandings without asking the user because they are derived from saved content.
- Retrieval does not create new claims about the user.

Deletion:

- Chunk invalidation follows source evidence/understanding invalidation.
- Embeddings must be invalidated and then swept.

### Layer 3: Feedback memory

Objects:

- `UserFeedbackEpisode`;
- corrections;
- accepted/dismissed relatedness;
- action draft edits;
- resolution transitions;
- source/summary corrections.

Purpose:

- turn user behavior into explicit, inspectable signal;
- improve retrieval ranking and future suggestions;
- record negative signal without pretending it is deletion.

Promotion rule:

- Feedback can influence ranking immediately.
- Feedback becomes profile/pattern memory only after thresholds or confirmation.

Deletion:

- Feedback tied to a deleted capture is deleted or detached according to the user's choice.
- Global feedback such as `never summarize this domain deeply` persists until revoked.

### Layer 4: Candidate memory

Objects:

- `MemoryCandidate`;
- `ProfileFactCandidate`;
- `PatternCandidate`;
- `InterestCandidate`;
- `RelationshipCandidate`.

Purpose:

- hold plausible memory before Orbit acts as if it knows it;
- support lightweight questions like `Want me to remember this?`;
- avoid inventing identity/profile from sparse data.

Promotion rule:

- Candidate memory is not used as fact in answers.
- Candidate memory may be used to ask clarifying questions or rank low-stakes suggestions.

Deletion:

- Candidate disappears if all supporting evidence is deleted or rejected.

### Layer 5: Promoted memory

Objects:

- `ProfileFact`;
- `Preference`;
- `RecurringInterest`;
- `Pattern`;
- `SkillDefault`;
- `CorrectionRule`.

Purpose:

- personalize Ask Orbit and draft actions;
- reduce repeated clarification;
- explain why Orbit suggests something.

Promotion rule:

- User declaration promotes immediately.
- Explicit confirmation promotes immediately.
- Repeated behavior promotes only after threshold and confidence gates.
- Sensitive facts require explicit confirmation even if repeated.

Deletion:

- Forgetting a promoted memory invalidates the fact and all derived rankings/patterns that depend only on it.

### Layer 6: Graph memory

Objects:

- graph episodes;
- entity nodes;
- temporal edges;
- relation weights;
- invalidation edges;
- provenance links.

Purpose:

- temporal, multi-hop reasoning;
- source-aware relatedness;
- profile as user-rooted subgraph;
- memory inspector graph views later.

Promotion rule:

- Not every retrieval chunk becomes a KG edge.
- KG gets entities/relations only after extraction confidence and source quality pass.
- Relationship and profile edges require evidence IDs and episode IDs.

Deletion:

- KG deletion must prove no stale node summary or relationship reason contains deleted content.

### Layer 7: Agent memory

Objects:

- `agent_state`;
- plans;
- skill registry;
- skill usage;
- execution outcomes;
- session summaries.

Purpose:

- complete user-confirmed workflows;
- remember draft/action outcomes;
- improve future plan defaults.

Promotion rule:

- Agent memory can create episodes and candidates.
- It cannot silently create promoted profile facts from private reasoning state.

Deletion:

- Clearing agent session state does not delete source captures.
- Deleting a plan/action deletes derived agent state and invalidates dependent patterns.

## Memory object contracts

### UserFeedbackEpisode

Purpose: capture explicit and implicit user signal in a reusable shape.

```ts
const UserFeedbackEpisodeSchema = z.object({
  schemaVersion: z.literal(1),
  feedbackId: z.string().uuid(),
  userId: z.string().uuid(),
  envelopeIds: z.array(z.string().uuid()).max(20),
  target: z.discriminatedUnion('kind', [
    z.object({ kind: z.literal('source_identity'), sourceIdentityId: z.string().uuid() }),
    z.object({ kind: z.literal('understanding'), understandingId: z.string().uuid() }),
    z.object({ kind: z.literal('relatedness'), relationId: z.string().uuid() }),
    z.object({ kind: z.literal('candidate_action'), actionId: z.string().uuid() }),
    z.object({ kind: z.literal('profile_fact'), profileFactId: z.string().uuid() }),
    z.object({ kind: z.literal('pattern'), patternId: z.string().uuid() }),
    z.object({ kind: z.literal('retrieval_result'), queryId: z.string().uuid() }),
  ]),
  signal: z.enum([
    'accepted', 'dismissed', 'corrected', 'not_related', 'wrong_source',
    'wrong_summary', 'too_much_context', 'not_enough_context', 'not_now',
    'useful', 'not_useful', 'confirmed_memory', 'forgot_memory'
  ]),
  reason: z.string().max(240).nullable(),
  replacementTextRef: z.string().nullable(),
  occurredAt: z.string().datetime(),
  sourceSurface: z.enum(['diary_card', 'capture_detail', 'ask_orbit', 'settings_memory', 'agent_plan', 'review']),
})
```

Rules:

- Feedback is first-class memory.
- Negative feedback is not deletion.
- Correction creates a new derived object version plus a feedback episode.
- Feedback should be cheap to give: chips first, free text optional.

### MemoryCandidate

Purpose: hold something Orbit might remember but should not treat as fact yet.

```ts
const MemoryCandidateSchema = z.object({
  schemaVersion: z.literal(1),
  candidateId: z.string().uuid(),
  userId: z.string().uuid(),
  candidateKind: z.enum(['profile_fact', 'preference', 'interest', 'pattern', 'relationship', 'correction_rule']),
  label: z.string().max(200),
  proposedFact: z.object({
    subject: z.string().max(160),
    predicate: z.string().max(80),
    object: z.string().max(300),
  }),
  supportingEnvelopeIds: z.array(z.string().uuid()).max(50),
  supportingEvidenceIds: z.array(z.string().uuid()).max(50),
  supportingFeedbackIds: z.array(z.string().uuid()).max(50),
  confidence: z.number().min(0).max(1),
  sensitivity: z.enum(['normal', 'sensitive', 'local_only']),
  promotionState: z.enum(['candidate', 'asked_user', 'promoted', 'rejected', 'expired', 'invalidated']),
  askUserCopy: z.string().max(240).nullable(),
  producedAt: z.string().datetime(),
  expiresAt: z.string().datetime().nullable(),
  modelLabel: z.string().max(128).nullable(),
  promptVersion: z.string().max(64).nullable(),
})
```

Rules:

- Candidates can be shown in a memory review surface.
- Candidates cannot be cited as facts in Ask Orbit answers.
- Candidates expire if they never receive confirmation or reinforcement.
- Sensitive candidates require confirmation before use.

### PromotedMemoryFact

Purpose: represent memory Orbit may reuse.

```ts
const PromotedMemoryFactSchema = z.object({
  schemaVersion: z.literal(1),
  memoryId: z.string().uuid(),
  userId: z.string().uuid(),
  memoryKind: z.enum(['declared_profile_fact', 'confirmed_preference', 'recurring_interest', 'behavior_pattern', 'correction_rule', 'relationship']),
  displayLabel: z.string().max(200),
  subject: z.string().max(160),
  predicate: z.string().max(80),
  object: z.string().max(300),
  confidence: z.enum(['declared', 'confirmed', 'high', 'medium', 'low']),
  sensitivity: z.enum(['normal', 'sensitive', 'local_only']),
  source: z.enum(['user_declared', 'user_confirmed', 'repeated_behavior', 'imported', 'correction']),
  supportingEnvelopeIds: z.array(z.string().uuid()).max(50),
  supportingEvidenceIds: z.array(z.string().uuid()).max(50),
  supportingFeedbackIds: z.array(z.string().uuid()).max(50),
  validFrom: z.string().datetime(),
  validTo: z.string().datetime().nullable(),
  invalidatedAt: z.string().datetime().nullable(),
  lastUsedAt: z.string().datetime().nullable(),
  useCount: z.number().int().nonnegative(),
})
```

Rules:

- Promoted memory must be visible in the memory inspector.
- Every promoted memory has sources or a user declaration.
- Use of a memory in Ask Orbit or an agent plan increments `useCount` and writes a trace/audit event.
- A memory can be disabled without deleting source captures.

### RetrievalQueryRecord

Purpose: record what retrieval did without storing raw prompt content in traces.

```ts
const RetrievalQueryRecordSchema = z.object({
  schemaVersion: z.literal(1),
  queryId: z.string().uuid(),
  userId: z.string().uuid(),
  surface: z.enum(['ask_orbit', 'capture_detail_related', 'agent_plan', 'memory_inspector', 'review']),
  queryDigest: z.string(),
  queryTextRef: z.string().nullable(),
  retrievalMode: z.enum(['local_keyword', 'local_vector', 'cloud_hybrid', 'graph', 'combined']),
  filters: z.object({
    envelopeId: z.string().uuid().nullable(),
    dateRange: z.string().nullable(),
    providers: z.array(z.string()).max(20),
    intents: z.array(z.string()).max(20),
    resolutionStates: z.array(z.string()).max(20),
    sensitivityMax: z.string().nullable(),
  }),
  resultEnvelopeIds: z.array(z.string().uuid()).max(50),
  citedEvidenceIds: z.array(z.string().uuid()).max(100),
  memoryIdsUsed: z.array(z.string().uuid()).max(50),
  graphTraversalUsed: z.boolean(),
  modelLabels: z.array(z.string().max(128)).max(10),
  latencyMs: z.number().int().nonnegative(),
  costCents: z.number().nonnegative().nullable(),
  createdAt: z.string().datetime(),
})
```

Rules:

- The query record can store hashes/digests by default.
- Full query text can be retained only according to product privacy settings.
- Retrieval records feed evals and debugging.

## Promotion policy

### Immediate promotion

Promote immediately when:

- user explicitly declares a fact: `remember that my dog is named Mochi`;
- user confirms a memory prompt;
- user corrects Orbit and selects `remember this correction`;
- user sets a default in Settings.

### Threshold promotion

Promote after repeated behavior when:

- same action pattern is confirmed at least 3 times;
- same correction is made at least 2 times;
- same source/topic cluster is accepted or revisited repeatedly;
- same preference is supported by multiple independent evidence families.

Suggested starting thresholds:

| Candidate | Minimum signal | Confirmation needed? |
| --- | --- | --- |
| Action pattern | 3 confirmed actions | No for low-risk defaults; yes for external write defaults. |
| Topic interest | 5 captures across 7+ days or 3 explicit saves with notes | Ask before profile use. |
| Person/project relationship | 2 evidence items + 1 user interaction | Ask if used for action drafting. |
| Source correction rule | 2 user corrections for same domain/app | Ask or show undo. |
| Tone/style preference | 2 edited drafts in same direction | Ask before applying broadly. |
| Sensitive fact | Any signal | Always ask. |

### Never promote automatically

Never auto-promote:

- health, finance, legal, identity, family/relationship, workplace-sensitive facts;
- inferred names/contact relationships from private messages;
- political/religious/sexual-orientation implications;
- anything based only on a screenshot thumbnail or metadata-only evidence;
- anything the user previously rejected.

## Retrieval architecture

### Principle

Retrieval is allowed to be broad; memory promotion is not. Ask Orbit should find saved things aggressively, but answer cautiously and cite evidence.

### Retrieval path order

1. **Scope and policy**: determine local/cloud, sensitivity, date/source/action filters.
2. **Lexical retrieval**: title, source label, note, summary, OCR, transcript, domain, entity labels.
3. **Vector retrieval**: retrieval chunks over evidence and understanding.
4. **Structured filters**: source/provider, app category, intent, resolution state, date, feedback exclusions.
5. **Graph retrieval**: entity/relationship/path expansion when available.
6. **Memory augmentation**: use promoted memory to disambiguate, not to replace citations.
7. **Rerank**: combine score, recency, source confidence, feedback, resolution state, and graph evidence.
8. **Answer**: cite captures/evidence and state limitations.

### Retrieval modes

| Mode | Use | Data source |
| --- | --- | --- |
| `local_keyword` | Works everywhere, offline fallback. | Room FTS / local indexes. |
| `local_vector` | On-device capable devices, recent/offline corpus. | Local embedding table. |
| `cloud_hybrid` | Full-corpus Ask Orbit and relatedness. | Supabase Postgres + pgvector + SQL filters. |
| `graph` | Multi-hop questions and memory inspector. | KG adapter or Supabase graph tables. |
| `combined` | Best product path. | Lexical + vector + graph + feedback. |

### Ask Orbit answer contract

Ask Orbit answers should include:

- answer text;
- cited capture IDs;
- cited evidence IDs;
- memory IDs used for personalization;
- limitations;
- follow-up actions;
- retrieval/query record ID.

Hard rule:

> Profile memory may help interpret the question, but factual claims about the world must cite captures/evidence.

Example:

- Allowed: `You usually save recipes for weekend cooking, so I prioritized the saved recipe captures. The recipe itself is from these captures...`
- Not allowed: `You are a meal-prepper` unless the user explicitly said that or confirmed the memory.

## Graph memory stance

### Supabase-only baseline

Build first.

Use for:

- retrieval chunks;
- lexical/vector retrieval;
- candidate memory;
- promoted memory facts;
- feedback episodes;
- simple entity tables if needed;
- deletion/export POC.

Why:

- already in stack;
- RLS exists in current migrations;
- easy to join captures, evidence, chunks, memory, feedback;
- no additional vendor or graph DB ops;
- deletion/export can be tested directly.

Limit:

- multi-hop temporal graph reasoning requires app-owned logic;
- graph visualization/debugging will be weaker;
- entity resolution and relation extraction will be ours to build.

### Graphiti OSS POC

POC after Supabase baseline.

Use for:

- temporal graph episodes;
- custom entity/edge extraction;
- hybrid semantic/BM25 + graph search;
- provenance-rich relation experiments;
- memory inspector graph prototype.

Acceptance gates:

- strict user/tenant isolation;
- deletion of unique and shared episode-derived facts;
- no stale node summaries containing deleted content;
- export path with source IDs;
- sparse-data humility;
- relatedness reason quality;
- cost/latency over dogfood corpus.

Rejection gates:

- cannot prove deletion/invalidation;
- cross-user search leakage under similar entities;
- graph DB operations exceed team capacity;
- framework summaries become opaque and uncitable.

### Zep managed fallback

Use if:

- Graphiti ops or deletion is too expensive;
- managed graph dashboards and isolation speed up alpha;
- vendor cost is acceptable;
- enterprise BYOC/BYOK/BYOM path matters.

Risks:

- core memory becomes vendor-dependent;
- credit cost can climb with naive episode modeling;
- shared node summaries may require explicit regeneration policy;
- Orbit still needs its own product memory contracts.

### Mem0 baseline/challenger

Use as:

- practical agent-memory baseline;
- profile/preference memory challenger;
- simpler retrieval/personalization test.

Do not use as:

- canonical provenance graph;
- deletion/export source of truth;
- replacement for Round 2 evidence contracts.

Why:

- current Mem0 direction is more memory/retrieval/entity-linking than a full temporal provenance graph;
- useful to test whether Orbit needs full graph memory this early.

## KG adapter contract

Whether the backend is Supabase-only, Graphiti, Zep, or Mem0, Orbit should talk through an adapter that preserves product semantics.

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

Rules:

- The adapter returns Orbit IDs, not vendor IDs alone.
- Vendor IDs can be stored as refs.
- Every returned fact/reason must carry source evidence or episode IDs.
- If a backend cannot provide this, it cannot be canonical memory.

## Episode model

Existing specs already rely on `episodes`, but Round 3 should tighten the source taxonomy.

Suggested `source_kind` values:

| Source kind | Meaning |
| --- | --- |
| `capture_created` | User saved a new artifact. |
| `evidence_acquired` | Orbit fetched/read/parsed evidence. |
| `understanding_created` | Orbit produced a summary/entities/actions. |
| `relatedness_feedback` | User accepted/rejected a relation. |
| `ask_query` | User asked Orbit. |
| `ask_answer` | Orbit answered with citations. |
| `candidate_action` | Orbit drafted an action. |
| `action_confirmed` | User confirmed action. |
| `action_executed` | Tool execution completed. |
| `resolution_transition` | Capture moved state. |
| `memory_candidate` | Orbit proposed a memory. |
| `memory_promoted` | Candidate became reusable memory. |
| `memory_rejected` | Candidate/fact was rejected or forgotten. |
| `user_correction` | User corrected source, summary, relation, or fact. |
| `import` | External import. |
| `system_decay` | Expiration/decay/invalidation event. |

Episode rules:

- Episodes are append-only.
- Deleting a source capture invalidates dependent episodes or removes user content from them.
- Audit events and memory episodes are related but not identical: audit is user-readable operational history; episodes are provenance substrate.
- Production traces should reference episode IDs, not raw content.

## Memory inspector

The memory inspector is the user-facing trust surface. It should exist before the full agent, because users need a place to inspect and correct what Orbit thinks.

### Entry points

- Settings -> Orbit Memory.
- Capture detail -> `Why this?` / `Memory used`.
- Ask Orbit answer -> `Sources and memory`.
- Agent plan -> `What Orbit used`.

### Main sections

1. **Saved facts**: confirmed profile facts, preferences, relationships.
2. **Patterns**: recurring actions or interests.
3. **Corrections**: rules learned from user edits.
4. **Not now / rejected**: negative signals that still guide Orbit.
5. **Candidates**: things Orbit may ask to remember.
6. **Activity**: recent memory reads/writes.

### Memory item display

Each item should show:

- plain-language fact or pattern;
- source count;
- last used date;
- confidence/source type: `You told Orbit`, `You confirmed`, `Inferred from repeated captures`, `Correction`;
- sensitivity: normal/sensitive/local-only;
- controls: edit, forget, keep local, never use for actions, show sources.

Do not expose graph jargon. Do not call edges or nodes by those names in the default UI.

### Memory source view

For a memory item, show:

- source captures;
- user confirmations/corrections;
- action outcomes;
- related Ask Orbit answers;
- limitations;
- delete/forget impact.

### Forget semantics

Offer clear options:

| User action | System behavior |
| --- | --- |
| `Forget this memory` | Invalidate promoted memory; keep source captures. |
| `Forget and stop inferring this` | Invalidate memory and add correction rule suppressing future candidates. |
| `Delete source captures too` | Start deletion flow for supporting captures. |
| `Keep on this device only` | Mark memory local-only and remove/suppress cloud copy. |

## Deletion/export rules

### Delete one capture

Must process:

1. source artifact;
2. source identity;
3. evidence bundles;
4. understandings;
5. retrieval chunks and embeddings;
6. relatedness explanations;
7. candidate actions based only on that capture;
8. memory candidates supported only by that capture;
9. promoted memory facts supported only by that capture;
10. KG episodes/edges/nodes derived only from that capture;
11. eval rows containing real content from that capture;
12. traces with content references;
13. blob/storage refs.

If a memory has multiple sources, remove this source and re-score. If confidence falls below threshold, demote or invalidate.

### Forget one memory

Must not delete source captures by default.

Actions:

- invalidate promoted memory;
- write `memory_rejected` episode;
- add optional suppression rule;
- remove from hot-path profile view;
- update retrieval reranking;
- mark KG edge invalidated.

### Delete account/cloud tenant

Must delete:

- all Supabase rows under user;
- all storage objects;
- all vector rows;
- all graph backend user data;
- all vendor memory IDs;
- all non-aggregate trace/eval links;
- all wrapped keys.

Return:

- deletion receipt ID;
- deleted categories;
- started/completed timestamps;
- known delayed sweeps;
- local audit receipt.

### Export

Export should include:

- captures and artifacts;
- evidence metadata;
- understandings and summaries;
- retrieval chunks without necessarily exporting embeddings unless requested;
- memory candidates;
- promoted memory facts;
- feedback episodes;
- graph episodes/nodes/edges if enabled;
- agent plans/actions/skill usage;
- deletion/invalidation receipts;
- schema versions.

## Eval plan

Round 3 needs a memory-specific eval set, separate from capture understanding.

### Dataset cases

Build 80 cases:

- 10 profile declaration cases.
- 10 repeated-interest cases.
- 10 repeated-action pattern cases.
- 10 correction-rule cases.
- 10 negative feedback/relatedness rejection cases.
- 10 sensitive-fact suppression cases.
- 10 deletion/invalidation cases.
- 10 Ask Orbit multi-hop/citation cases.

### Metrics

- promoted memory precision;
- over-promotion rate;
- sensitive auto-promotion violations;
- deletion completeness;
- retrieval answer citation correctness;
- relatedness explanation quality;
- user feedback application;
- graph/backend tenant isolation;
- cost per promoted memory;
- memory inspector explainability.

Hard gates:

- zero cross-user leakage;
- zero sensitive auto-promotion;
- zero promoted fact without source or user declaration;
- deletion test must pass for unique and shared facts;
- Ask Orbit answer claims must cite captures/evidence.

## Implementation sequence recommendation

Round 3 changes the likely Speckit order:

1. Define Round 2 contracts in specs first.
2. Add retrieval chunks and feedback episodes before KG.
3. Build Ask Orbit over retrieval chunks with citations.
4. Add memory candidates and promotion policy.
5. Build memory inspector MVP.
6. Add promoted memory facts for declared/confirmed facts only.
7. Add repeated-pattern promotion.
8. POC Supabase-only graph-ish relations.
9. POC Graphiti vs Zep vs Mem0 with the same eval set.
10. Only then choose canonical KG backend.
11. Add full graph-backed Ask Orbit multi-hop.
12. Add agent planner/executor.

This is slower than jumping to Graphiti, but faster to trustworthy product value. Users do not need a perfect temporal graph to feel Orbit is useful. They need captures they can understand, find, connect, correct, and trust.

## Immediate Speckit notes

The future Speckit replanning should create or rewrite specs around these nouns:

- `capture_understanding`;
- `retrieval_chunk`;
- `feedback_episode`;
- `memory_candidate`;
- `promoted_memory_fact`;
- `memory_inspector`;
- `retrieval_query_record`;
- `orbit_memory_graph_adapter`;
- `deletion_receipt`;
- `memory_eval_case`.

Do not let old `kg_nodes` / `kg_edges` be the first implementation surface unless a graph POC already passed deletion and isolation. Keep them in the architecture, but put product-facing memory contracts above backend-specific graph tables.

## What Round 4 should do

Round 4 should focus on execution planning and cloud runtime boundaries:

- where `:agent` starts and stops;
- whether Mastra/LangGraph/Pydantic AI/Google ADK should own workflows or only POCs;
- how user approval/interrupts map to CandidateAction and AgentPlan;
- how LiteLLM/Portkey/Cloudflare/Vercel gateway choice affects budgets and trace IDs;
- what the first actual Speckit branches should be.

Round 3 says what memory is. Round 4 should decide how the runtime moves through it without becoming an opaque autonomous agent.
