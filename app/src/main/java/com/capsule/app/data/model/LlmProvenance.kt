package com.capsule.app.data.model

/**
 * The source process / model that produced a proposal. Persisted on
 * [com.capsule.app.data.entity.ActionProposalEntity.provenance] so the
 * audit log can attribute every proposed action to its originating LLM.
 *
 * - LOCAL_NANO: on-device Gemini Nano via AICore (the only v1.1 source).
 * - ORBIT_MANAGED: Orbit-managed cloud proxy (spec 005, future).
 * - BYOK: user-provided cloud key (spec 005, future).
 */
enum class LlmProvenance {
    LOCAL_NANO,
    ORBIT_MANAGED,
    BYOK
}
