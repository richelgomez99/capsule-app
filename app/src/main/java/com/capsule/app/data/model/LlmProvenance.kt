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

/**
 * Map the runtime sealed-class provenance from `:ml` ([com.capsule.app.ai.model.LlmProvenance])
 * down to the persistable enum used on entities.
 */
fun com.capsule.app.ai.model.LlmProvenance.toEntityEnum(): LlmProvenance = when (this) {
    is com.capsule.app.ai.model.LlmProvenance.LocalNano -> LlmProvenance.LOCAL_NANO
    is com.capsule.app.ai.model.LlmProvenance.OrbitManaged -> LlmProvenance.ORBIT_MANAGED
    is com.capsule.app.ai.model.LlmProvenance.Byok -> LlmProvenance.BYOK
}
