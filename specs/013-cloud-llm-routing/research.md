# Phase 0 Research — Cloud LLM Routing + Supabase Backbone

**Spec**: [spec.md](spec.md) | **Plan**: [plan.md](plan.md) | **Date**: 2026-04-28

This document captures decisions and validated facts for the Day 1 work. Per the orchestrator instruction, validated facts come from the authoritative reference docs and are recorded here so the implementer does not need to re-research.

## Decisions resolved during planning

### D-001: Default mode (cloud vs. local)
- **Decision**: Cloud is the default (`useLocalAi = false`). Local mode is a kill switch.
- **Rationale**: `hasNanoCapableHardware()` returns `false` unconditionally in v1 (FR-013-015) so local mode is structurally unreachable on every device until Pixel 9 Pro / S24 detection lands in a later spec. Defaulting to cloud is the only viable choice for the alpha install.
- **Alternatives considered**: Local default with cloud opt-in (rejected — would gate alpha on Nano integration, which is `TODO("AICore integration — US2")` everywhere).

### D-002: Gateway URL on Day 1
- **Decision**: Use `https://gateway.example.invalid/llm` as the placeholder URL.
- **Rationale**: `example.invalid` is RFC-2606 / RFC-6761 reserved — guaranteed never to resolve, safe in source. The Edge Function spec (Day 4–7) will swap this to the real Vercel AI Gateway URL via configuration; ADR-003's provider-agnostic envelope guarantees no binary-shape change is required when that swap happens.
- **Alternatives considered**: Real URL (rejected — Edge Function not deployed; would 404 in tests). Empty string + flag (rejected — adds branching for no benefit).

### D-003: Provider-specific JSON shaping locus
- **Decision**: Android sends a provider-agnostic envelope `{type, requestId, payload}`; the Edge Function shapes provider-specific JSON server-side.
- **Rationale**: Models change frequently (Sonnet 4.6 → 4.7 etc.); the mobile binary should stay stable across model swaps. Server-side shaping also keeps API keys server-side per NFR-013-003.
- **Alternatives considered**: Send Anthropic Messages JSON directly (rejected — couples mobile binary to Anthropic-specific shape).

### D-004: Model selection per request type
- **Decision**:
  - `embed` → `openai/text-embedding-3-small` (1536d, $0.02/1M tokens).
  - `summarize`, `extractActions`, `generateDayHeader` → `anthropic/claude-sonnet-4-6`.
  - `classifyIntent`, `scanSensitivity` → `anthropic/claude-haiku-4-5` with prompt-cached prefix.
- **Rationale**: Validated against the 2026-04-28 tech-stack research. Hot-path classification on Haiku 4.5 + prompt cache hits the $1/user/month budget at 100 captures/day. Sonnet 4.6 reserved for stage-path summarisation where quality matters. Embedding-3-small is the cost/quality sweet spot for cluster engine vectors.

### D-005: Schema column shape (Round 2 clarification — Option C, Hybrid)
- **Decision**: Day-1 schema mirrors Room as **plaintext** columns AND adds nullable `*_ct bytea` ciphertext columns per [`specs/contracts/envelope-content-encryption-contract.md`](../contracts/envelope-content-encryption-contract.md). Day-1 writes plaintext only; ciphertext columns stay `NULL` until spec 006 lands.
- **Rationale**: Future-proofs the schema. When spec 006 ships DEK/KEK + AES-GCM, the migration only has to populate `*_ct` and drop plaintext — no `ALTER TABLE` needed. ~20 extra column declarations, zero runtime cost.
- **Alternatives considered**: Plaintext-only with rename later (rejected — expensive `ALTER TABLE`); ciphertext-only Day 1 (rejected — blocks alpha until spec 006 ships DEK/KEK provisioning).

### D-006: ClusterDetectionWorker migration scope (Round 2 clarification — Option B, carve-out)
- **Decision**: `ClusterDetectionWorker` keeps its direct `NanoLlmProvider()` construction with a `// CLUSTER-LOCAL-PIN: migrated in Phase 11 Block 4` comment. Migration is owned by the Phase 11 Block 4 spec, not Day 1.
- **Rationale**: ADR-006 explicitly gates cluster engine cloud migration to a separate spec. Forcing it into Day 1 would couple the Day-1 abstraction work to cluster-engine semantics that have not been re-validated against cloud embeddings.
- **Verifiable signal**: `grep -n "CLUSTER-LOCAL-PIN" app/src/main/java/com/capsule/app/cluster/ClusterDetectionWorker.kt` returns exactly one line on Day 1; zero lines after Phase 11 Block 4 lands.

### D-007: AIDL parcel wire format
- **Decision**: Parcels carry the sealed-class instance as **kotlinx.serialization JSON encoded to UTF-8 bytes, written into a String field via `parcel.writeString`** (the JSON itself is the parcel payload).
- **Rationale**: AIDL does not natively support Kotlin sealed classes. The standard Android pattern is to JSON-encode the value and put it inside a single String field. kotlinx.serialization with `@Serializable` and a polymorphic registry handles forward compatibility automatically.
- **Alternatives considered**: Per-subtype Parcelable per request type (rejected — six request types × two halves = 12 Parcelables, painful to keep AIDL signature in sync). Protobuf (rejected — adds new dep; benefit at this scale is marginal).

### D-008: Embed equality contract
- **Decision**: `LlmGatewayResponse.Embed` overrides `equals`/`hashCode` using `FloatArray.contentEquals` / `contentHashCode`.
- **Rationale**: Default `data class` equality on `FloatArray` uses reference equality, which breaks `assertEquals` in tests and `Set`/`Map` keying downstream. Spec 002's existing `EmbeddingResult` already uses this pattern.

### D-009: Auth header on Day 1
- **Decision**: `LlmGatewayClient` attempts to read `AuthSessionStore.getCurrentToken()`; if the store doesn't yet exist (Day 1 reality), proceed with no `Authorization` header.
- **Rationale**: Auth flow lands Day 2–3. Blocking the entire AI surface on auth would couple two specs that should ship independently.

### D-010: 5xx fallback policy
- **Decision**: On 5xx from Vercel AI Gateway, retry once against the corresponding direct provider endpoint (Anthropic Messages or OpenAI Embeddings — placeholder URLs acceptable for Day 1). No exponential backoff. Two consecutive 5xx → typed error to the AIDL caller.
- **Rationale**: ADR-003. Per-request retry policy keeps semantics simple and predictable; callers can re-issue at a higher level.

### D-011: Tooling deviation — branch name
- **Decision**: The git branch is `cloud-pivot` (semantic name) but the spec dir is `013-cloud-llm-routing` (numeric per speckit). The plan was generated with `SPECIFY_FEATURE=013-cloud-llm-routing` to satisfy speckit's branch-name validation.
- **Rationale**: The product team chose `cloud-pivot` for human readability; speckit's `setup-plan.sh` requires `NNN-name` format. The override is benign (the script's only use of branch name is `find_feature_dir_by_prefix`, which still resolves correctly when `SPECIFY_FEATURE` is set).

## Validated facts (recorded, not re-researched)

Source: `~/.gstack/projects/richelgomez99-capsule-app/orbit-tech-stack-research-2026-04-28.md` and `orbit-pivot-plan-2026-04-28.md`, validated 2026-04-28.

- **F-001**: Anthropic Sonnet 4.6, Haiku 4.5, Opus 4.6 are available via Vercel AI Gateway with model strings `anthropic/claude-sonnet-4-6`, `anthropic/claude-haiku-4-5`, `anthropic/claude-opus-4-6`.
- **F-002**: OpenAI `text-embedding-3-small` is 1536-dimensional at $0.02 per 1M input tokens.
- **F-003**: Vercel AI Gateway documentation (updated 2026-04-02) supports OpenAI Chat Completions, Anthropic Messages, and AI SDK v5/v6 envelopes; pricing is zero-markup pass-through.
- **F-004**: Anthropic prompt caching has a 5-minute cache TTL; cache writes cost 1.25× input price, cache reads cost 0.10× input price. Hot-path classification with a stable prefix hits cache reads on the second-and-later calls within the 5-min window.
- **F-005**: Supabase Postgres + pgvector 0.9 is production-ready in 2026; supabase-kt v3.x Android client supports realtime + auth + postgrest. (Day 1 does not use the client; recorded for the Day 2–3 spec.)
- **F-006**: Sign in with Google on Android uses Credential Manager 1.x + googleid 1.x. Deferred to Day 2–3 spec.
- **F-007**: AIDL JSON-string parcel pattern is the standard Android approach when sealed-class IPC is needed. Validated against multiple production Android apps.
- **F-008**: `gateway.example.invalid` is RFC-2606 / RFC-6761 reserved (`.invalid` TLD); guaranteed never to resolve, safe as a placeholder.
- **F-009**: Existing `:net` process already has OkHttp on the classpath via `gradle/libs.versions.toml`; no new HTTP client is needed.
- **F-010**: kotlinx.serialization is already on the classpath of the Android module (used elsewhere); no new dependency required.
- **F-011**: AIDL Binder transaction limit is ~1 MB. A 1536-float embedding payload is ~6 KB binary (~20 KB JSON-encoded as numbers); well within limit. Batch embedding designs in later specs need to be aware.
- **F-012**: Existing `:capture` ↔ `:net` AIDL surface is bound via `INetworkGateway.aidl` and uses the pattern visible in `app/src/main/java/com/capsule/app/continuation/UrlHydrateWorker.kt`. `CloudLlmProvider` will mirror this pattern.

## Open items (out of scope — flagged for downstream specs)

- **O-001**: Real Vercel AI Gateway URL — known after Edge Function deploy (Day 4–7 spec).
- **O-002**: Real provider integration (canned responses suffice for Day 1) — Edge Function spec.
- **O-003**: BYOK key entry surface, per-capability cloud toggles, audit log entries for cloud LLM calls — Phase 11 Block 5+ specs. Required before first external alpha install.
- **O-004**: Pixel 9 Pro arrival May 1, 2026 → enables physical-device verification of `hasNanoCapableHardware()` and end-to-end cluster-detection-on-cloud (Phase 11 Block 4 spec).
- **O-005**: supabase-kt Android client integration, write-through sync — Day 2–3 spec.
- **O-006**: Constitution amendment for Principle I/II — invoke `/speckit.constitution` next.

All NEEDS CLARIFICATION from spec Round 1 + Round 2 are resolved. No open clarifications remain that block planning.
