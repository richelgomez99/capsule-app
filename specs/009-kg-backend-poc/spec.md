# Knowledge Graph Backend POC

**Status**: Rebaselined active slot placeholder - generate fresh Speckit artifacts before implementation.

**Branch**: `009-kg-backend-poc`

## Purpose

Evaluate and prove the backend substrate for graph-like memory and retrieval once capture understanding, retrieval, action approval, memory controls, and cloud controls exist.

## Inputs To Preserve

- Backend KG concepts from archived `007-knowledge-graph`.
- BYOC migration concepts from archived `009-byoc-sovereign-storage` as a later extension.
- Supabase/pgvector baseline from specs `013` and `014`.

## Stop Signs

- Do not introduce graph persistence before deletion, invalidation, and provenance rules are defined.
- Do not create cross-user derivatives.
- Treat Qdrant/Graphiti/Zep/Mem0 as adapter candidates, not product commitments.
