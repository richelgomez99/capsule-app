# Ask Orbit (v1.2)

**Status**: STUB — full PRD to be drafted after v1.1 (spec 003, spec 005) ships
**Target release**: v1.2
**Depends on**: spec 002 complete; spec 005 optional; spec 006 optional; spec 007 optional
**Governing document**: `.specify/memory/constitution.md` — adheres to Principles I, VI, IX, X

---

## Summary

Ask Orbit is a natural-language interface over the user's own envelope corpus. The user types or speaks a question (*"what was that recipe I saved last weekend?"*, *"what do I know about my puppy?"*, *"which restaurants have I saved from Instagram?"*) and Orbit answers from the user's own captures, with citations back to the source envelopes.

This is a **retrieval-augmented generation** feature over the local corpus. It uses:

1. **On-device text embeddings** (Gemini Nano embedding endpoint on capable devices) produced at envelope-seal time, stored in a local vector table.
2. **Local k-NN search** over the embedding table using the envelope text corpus.
3. **Synthesis** via Gemini Nano (default) or BYOK cloud LLM (spec 005, opt-in for quality).

Ask Orbit has a fallback keyword-search mode for devices without Nano embedding support, so the feature works everywhere at some level.

---

## Relationship to other specs

- **Spec 005 (Orbit-managed LLM + BYOK)** makes Ask Orbit meaningfully better. Synthesis quality with cloud models vs. on-device Nano is a large gap. Per-capability toggle applies, and all cloud prompts pass the Principle XI consent filter.
- **Spec 006 (Orbit Cloud Storage)** enables Ask Orbit to do **retrieval** over the full corpus (pgvector similarity + keyword + KG traversal) with structural tenant isolation. Local on-device retrieval remains available for offline diary.
- **Spec 007 (Knowledge Graph)** promotes Ask Orbit from single-hop ("what do I know about X") to multi-hop ("which projects is Alice involved in and what did we discuss last week") via bi-temporal graph traversal, including the user profile subgraph.

---

## Design Principles

1. **The corpus is the ground truth.** Ask Orbit never fabricates facts. Every statement in a response is backed by a cited envelope; if no envelope backs a claim, the response says so.
2. **Retrieval is local by default.** Even if synthesis is cloud (spec 005), retrieval happens against the local index. The user's entire envelope text never needs to leave the device to power Ask Orbit.
3. **Transparent citations.** Every response shows the envelopes it drew from as tappable chips that deep-link into the Diary.
4. **The audit log sees every query.** Questions, prompt digests, retrieval counts, synthesis provenance — all logged locally.
5. **Graceful degradation.** Missing Nano → keyword search only. Missing embedding table (older captures) → re-embed in background with `BACKFILL_EMBEDDINGS` continuation.

---

## Initial Functional Requirements (to be expanded)

- **FR-004-001**: System MUST produce a text embedding (on-device Nano) for every envelope's content and every `ContinuationResult.summary` at the moment they are sealed, and store it in a local `embedding` table keyed by envelope ID and content chunk.
- **FR-004-002**: System MUST provide an Ask Orbit chat surface accessible from the Diary toolbar, accepting a typed question and returning a synthesized answer with envelope citations.
- **FR-004-003**: System MUST perform k-NN retrieval (cosine similarity) locally against the embedding table; top-K default 8, configurable per query.
- **FR-004-004**: System MUST synthesize the response through the `LlmProvider` interface, defaulting to `NanoLlmProvider`. Users with BYOK enabled for Ask Orbit (per spec 005) route synthesis to their configured cloud provider.
- **FR-004-005**: System MUST display every citation as a chip that deep-links into the source envelope in the Diary.
- **FR-004-006**: System MUST log every Ask Orbit query to the audit log with prompt digest, retrieved envelope IDs, and synthesis provenance.
- **FR-004-007**: System MUST provide a keyword-fallback retrieval mode (FTS5 over envelope text) that powers Ask Orbit on devices without Nano embedding support.
- **FR-004-008**: System MUST provide a `BACKFILL_EMBEDDINGS` continuation that processes pre-v1.2 envelopes during charger+wifi windows.

---

## Open Questions

- What embedding dimension does Gemini Nano support and how does it perform at 1k/10k/100k envelope scales?
- Do we expose a "conversation history" (follow-up questions) or is each Ask Orbit query stateless?
- Does Ask Orbit have voice input? (Tied to whether we revisit the rejected "raw microphone" constitutional stance.)

---

*To be fleshed out into a full speckit spec after v1.1 ships.*
