# Knowledge Graph (v1.3)

**Status**: STUB — full PRD to be drafted after v1.2 (spec 006) ships
**Target release**: v1.3
**Depends on**: spec 002 complete; spec 004 complete; spec 006 optional (cloud storage dramatically improves graph quality)
**Governing document**: `.specify/memory/constitution.md` — adheres to Principles I, IX, X

---

## Summary

The Knowledge Graph turns Orbit from a chronological diary into a relational memory. An entity extraction pass (Nano-first, BYOK-optional) identifies people, projects, topics, places, URLs, and organizations in every envelope and `ContinuationResult`. Relationship edges are formed between entities that co-occur in the same envelope, between entities that share a continuation lineage, or between entities tagged the same way by the user.

The result: Ask Orbit (spec 004) graduates from single-hop keyword retrieval ("what did I save about puppies") to multi-hop graph traversal ("what projects is Alice involved in, and what did we discuss about those projects in the last month").

---

## Storage strategy

The knowledge graph works at both storage tiers:

- **Tier 0 (local only, SQLite).** Two tables `orbit_entity` and `orbit_entity_edge` with self-joins; Room native. Acceptable up to ~10k envelopes; multi-hop queries degrade beyond that.
- **Tier 1 (BYOC cloud, Postgres).** Same schema but graph traversal uses recursive CTEs for multi-hop queries, `pg_trgm` for entity deduplication, and optional `pgvector` index over entity embeddings for fuzzy entity resolution. Scales well past 100k envelopes.

The local tier is the fallback. Users who haven't enabled BYOC still get a working graph; cloud users get a richer one.

---

## Design Principles

1. **Entities live where envelopes live.** If envelopes are local-only, entities are local-only. If envelopes are mirrored to BYOC, entities mirror too (per user's opt-in toggle).
2. **Extraction is opt-in and capability-gated.** Entity extraction is a new `ContinuationType.ENTITY_EXTRACT` runnable on Nano (default) or BYOK cloud (per spec 005). Users who disable the capability do not generate graph data.
3. **User owns the graph.** Entities and edges are user-editable from the Diary: merge two entities ("Alice = Alice Smith"), split a wrongly-merged entity, delete an entity (cascades to edges).
4. **The graph informs retrieval but never the audit log.** Retrieval counts stay in the audit log; graph-derived inferences do not.

---

## Initial Functional Requirements

- **FR-007-001**: System MUST add `ContinuationType.ENTITY_EXTRACT` that, when run on an envelope, produces a list of (entity_type, canonical_name, alias, confidence) tuples via the `LlmProvider` interface.
- **FR-007-002**: System MUST store entities in `orbit_entity(id, type, canonical_name, aliases[], embedding?, created_at)` and edges in `orbit_entity_edge(id, from_entity, to_entity, relation_type, weight, source_envelope_id, created_at)`.
- **FR-007-003**: System MUST provide a Diary-level "Entity page" for any clicked entity: shows all envelopes mentioning it, related entities (one-hop), timeline of captures mentioning it.
- **FR-007-004**: System MUST provide user-editable merge/split/delete actions on entities; every edit writes to the audit log.
- **FR-007-005**: Ask Orbit (spec 004) MUST use graph traversal as a second retrieval strategy alongside k-NN; multi-hop traversal is only available if the graph has been populated.

---

## Open Questions

- Entity types: do we start with {PERSON, ORG, PROJECT, TOPIC, PLACE, URL} or allow user-defined types?
- Entity resolution across envelopes: exact-match only, substring, or embedding similarity? Probably tier-dependent — local uses substring, cloud uses embedding.
- Do we extract entities continuously or only on charger+wifi? Leaning charger+wifi.
- Does entity extraction cost enough battery to warrant per-capability opt-in by default? Leaning yes.

---

*To be fleshed out into a full speckit spec after v1.2 ships.*
