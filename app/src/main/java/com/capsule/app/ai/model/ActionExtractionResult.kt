package com.capsule.app.ai.model

import com.capsule.app.data.model.SensitivityScope

/**
 * Lightweight projection of an [com.capsule.app.data.entity.AppFunctionSkillEntity] passed to
 * [com.capsule.app.ai.LlmProvider.extractActions]. The provider never sees the registry's
 * private metadata (registeredAt, etc.); only what the model needs to choose
 * a function and shape valid args.
 *
 * See `specs/003-orbit-actions/contracts/action-extraction-contract.md` §3.
 */
data class AppFunctionSummary(
    val functionId: String,
    val schemaVersion: Int,
    val displayName: String,
    val description: String,
    /** JSON Schema describing the args object the model must produce. */
    val argsSchemaJson: String,
    val sensitivityScope: SensitivityScope
)

/**
 * One model-proposed call. The extractor MUST guarantee:
 *  - `functionId` ∈ the registered set passed in
 *  - `argsJson` validates against that function's `argsSchemaJson`
 *  - `confidence` ∈ [0.0, 1.0]
 *  - `previewTitle` ≤ 120 chars (UI compresses to 80 with ellipsis)
 *
 * Candidates failing those invariants MUST be dropped before this struct
 * surfaces to callers (the caller's only filter is the 0.55 floor).
 */
data class ActionCandidate(
    val functionId: String,
    val schemaVersion: Int,
    val argsJson: String,
    val previewTitle: String,
    val previewSubtitle: String?,
    val confidence: Float,
    val sensitivityScope: SensitivityScope
)

/**
 * Result of an [com.capsule.app.ai.LlmProvider.extractActions] call.
 *
 * - [provenance] records which model produced the candidates so the audit log
 *   row written by the orchestrator (`ACTION_PROPOSED`) can stamp the right
 *   `llmProvider`/`llmModel` columns (Principle IX).
 * - [candidates] is already truncated to `maxCandidates` and sorted by
 *   confidence descending. Empty list is a valid "no actions" outcome.
 */
data class ActionExtractionResult(
    val provenance: LlmProvenance,
    val candidates: List<ActionCandidate>
)
