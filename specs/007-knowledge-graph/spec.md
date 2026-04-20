# Knowledge Graph (v1.2)

**Status**: DRAFT — full PRD to be drafted after spec 006 ships
**Target release**: v1.2
**Depends on**: spec 002 complete; spec 006 (Orbit Cloud) shipped (KG lives in Orbit Cloud by default); spec 004 (Ask Orbit) consumes KG
**Governing document**: `.specify/memory/constitution.md` — adheres to Principles I, IX, X, XI, XII

---

## Summary

The Knowledge Graph turns Orbit from a chronological diary into a
relational, time-aware memory. An extraction pass identifies typed
entities (Person, Place, Project, Topic, Item, Event, State, Skill,
Envelope, Pattern) in every envelope and continuation result, and
records bi-temporal edges between them. Every edge carries provenance
(episode reference per Principle XII), a sensitivity tag, and
bi-temporal validity so that corrections, retractions, and temporal
decay work without data loss.

The user profile is **the same knowledge graph** — a subgraph where
`subject = user_id`. The same tables, the same queries, the same
temporal semantics. There is no separate "profile store" schema.
Declared identity, inferred identity, and corrections/rejections all
live as KG edges pointing from the user_id node, each with its own
sensitivity tag and audit trail.

This graduates Ask Orbit (spec 004) from single-hop keyword retrieval
to multi-hop temporal traversal ("what did Alice say about project X
between February and April, and what have I done about it?"), and
gives the Orbit Agent (spec 008) a durable, inspectable long-term
memory.

---

## Storage strategy

- **Orbit Cloud (Tier 1, default).** `kg_nodes`, `kg_edges`, `episodes`,
  and `profile_facts` tables live inside the user's tenant schema
  (spec 006). pgvector powers fuzzy entity resolution; recursive CTEs
  over `kg_edges` with bi-temporal predicates power multi-hop
  queries. No Apache AGE in v1.2 — recursive CTEs are sufficient and
  operationally simpler.
- **BYOC (Tier 2, v1.3).** Identical schema in the user's own
  Postgres (spec 009). Lossless migration path guaranteed.
- **Local (Tier 0).** The on-device last-N-days projection of the
  diary holds a minimal KG slice — enough for the Diary's entity
  pages to render offline. Full KG lives in Tier 1; on-device is
  cache + projection, not source of truth for the graph.
- **Local-only nodes and edges.** Anything tagged `local_only` stays
  on-device forever and is structurally blocked from upload by the
  `:agent` consent filter (Principle XI).

---

## Design Principles

1. **Entities live where their tier places them.** The graph's source
   of truth is Orbit Cloud once enabled; device is a projection.
   Pre-cloud-enable users have a local-only mini-graph for offline
   diary rendering.
2. **Extraction is opt-in and capability-gated.** Entity extraction is
   a `ContinuationType.ENTITY_EXTRACT` runnable on Nano (default),
   Orbit-managed LLM proxy (spec 005), or BYOK.
3. **User owns and edits the graph.** Merge, split, delete, re-type,
   re-weight, invalidate-as-of — every edit records an episode and
   writes an audit row.
4. **The graph informs retrieval but never the audit log.** Retrieval
   counts live in the audit log; graph-derived inferences do not.
5. **Profile is a subgraph, not a separate schema.** Queries for
   "what does Orbit know about me" are KG queries rooted at
   `user_id`.
6. **Every edge has provenance (Principle XII).** No edge exists
   without an `episode_id` reference to a raw source — capture,
   continuation result, agent action, user edit, or import. Deleting
   an episode cascades edge invalidation.

---

## Functional Requirements

### Extraction and storage

- **FR-007-001**: System MUST add `ContinuationType.ENTITY_EXTRACT`
  that produces `(entity_type, canonical_name, aliases, confidence,
  sensitivity)` tuples via the `LlmProvider` interface (002 T025a).
- **FR-007-002**: System MUST store nodes in `kg_nodes(id, type,
  canonical_name_ct, aliases_ct, embedding, sensitivity, created_at,
  updated_at)` and edges in `kg_edges(id, from_node, to_node,
  relation_type, weight, valid_from, valid_to, invalidated_at,
  episode_id, sensitivity, created_at)` per spec 006 schema.
- **FR-007-003**: System MUST provide a Diary entity page for any
  clicked entity showing mentions, one-hop neighbors, and a timeline
  of edges.

### Unique KG features

- **FR-007-004 (intent-typed edges)**: Edge relation types MUST be a
  closed set aligned with Orbit's intent taxonomy
  (`co_occurs_with`, `mentions`, `works_on`, `met_at`, `lives_in`,
  `cites`, `continues`, `contradicts`, `is_a`, `part_of`,
  `user_prefers`, `user_rejects`, etc.). Extension requires an
  `ontology` row (FR-007-008).
- **FR-007-005 (temporal decay + reinforcement)**: Edge `weight`
  MUST decay on a configurable half-life per relation type, and
  re-extraction of the same edge MUST reinforce weight rather than
  create a duplicate. Decay and reinforcement events are written as
  system-generated episodes.
- **FR-007-006 (state-anchored nodes)**: A node of type `State`
  captures a snapshot that an edge can reference — e.g., "as of
  state=S1, Alice works_on project X" — so that retroactive
  corrections are expressed as new edges anchored to new states, not
  destructive updates.
- **FR-007-007 (continuation-lineage edges)**: Every continuation
  result MUST emit an edge of type `continues` from the derived node
  back to its source envelope node, preserving the full chain of
  derivation for Principle XII.
- **FR-007-008 (user-editable ontology with audit)**: Users MAY add
  or rename relation types and node types through a Settings →
  Knowledge → Ontology screen. Every ontology change writes an
  `ontology` row and an audit entry. Existing edges using renamed
  relations are migrated, not deleted.

### Bi-temporal and provenance

- **FR-007-009**: Every edge MUST carry `valid_from`, nullable
  `valid_to`, nullable `invalidated_at`, and a mandatory `episode_id`.
- **FR-007-010**: Every node and every edge MUST carry a `sensitivity`
  tag chosen from `public_to_orbit | local_only | ephemeral`. The
  `:agent` consent filter (Principle XI) structurally blocks any
  `local_only` node or edge from being included in prompts, logs, or
  cloud writes.

### Profile as KG subgraph

- **FR-007-011**: The user profile MUST be modeled as the subgraph
  rooted at the `user_id` node. Declared identity, inferred identity,
  and corrections/rejections MUST all be `kg_edges` pointing from
  `user_id` with distinct relation types
  (`declared_name`, `declared_pronoun`, `works_with`, `prefers_tone`,
  `rejects_suggestion`, etc.).
- **FR-007-012**: Corrections and rejections MUST be modeled as
  negative-weight edges, not as edge deletions. Deletion implies no
  signal; negative weight implies an informative disagreement.
- **FR-007-013**: The `profile_facts` table (spec 006) MUST be a
  materialised view over the user-rooted subgraph, refreshed when
  relevant edges change.

### User controls

- **FR-007-014**: System MUST provide user-editable merge, split,
  delete, re-weight, and invalidate-as-of actions on nodes and edges.
  Every edit writes an episode and an audit row.
- **FR-007-015**: Ask Orbit (spec 004) MUST use graph traversal as a
  retrieval strategy alongside keyword (on-device FTS) and vector
  (Tier 1 pgvector); multi-hop traversal is only available once the
  graph is populated.

---

## Non-Goals (v1.2)

- No on-device graph DB. Tier 0 projection is minimal; full graph is
  in Tier 1.
- No Apache AGE or Neo4j. Recursive CTEs on Postgres are the v1.2
  traversal engine.
- No cross-tenant graph features (never; Principle X Model A #3).

---

## Open Questions

- Default decay half-life per relation type: probably 90 days for
  most, 365 days for declared identity, no decay for audit-anchor
  edges. Revisit after dogfooding.
- Default `sensitivity` inference: can the extractor auto-tag
  `local_only` (e.g., anything mentioning health, finance, or legal
  matters) or do we always default `public_to_orbit` and rely on the
  user to downgrade? Leaning conservative-auto-tag with user override.
- UX for ontology editing: how do we keep power-user flexibility
  without giving non-technical users a way to shoot themselves in
  the foot? Separate advanced flag likely.

---

*To be fleshed out into a full speckit spec after spec 006 ships.
Constitutionally bounded by Principles X, XI, XII.*
