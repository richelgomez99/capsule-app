package com.capsule.app.data.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * Compound read model pairing an [IntentEnvelopeEntity] with every hydration
 * result that references it via `continuation_result.envelopeId` **and** the
 * (at most one) shared result it deduped against via
 * `intent_envelope.sharedContinuationResultId` → `continuation_result.id`.
 *
 * Room tracks all involved tables, so Flows backed by this class re-emit when
 * a URL hydrate worker writes back a new [ContinuationResultEntity]. UI
 * consumers should prefer the shared result first (dedupe hit) and fall back
 * to the most recent per-envelope result so that repeated captures of the
 * same URL render the hydrated title/domain/summary immediately.
 */
data class IntentEnvelopeWithResults(
    @Embedded val envelope: IntentEnvelopeEntity,
    @Relation(parentColumn = "id", entityColumn = "envelopeId")
    val results: List<ContinuationResultEntity>,
    @Relation(parentColumn = "sharedContinuationResultId", entityColumn = "id")
    val sharedResults: List<ContinuationResultEntity>
) {
    /**
     * Best-effort hydrated result for this envelope:
     *   1. Dedupe hit — `sharedContinuationResultId` resolved to a real row.
     *   2. Most recent worker write-back keyed by `envelopeId`.
     *   3. null — genuinely not hydrated yet.
     */
    val latestResult: ContinuationResultEntity?
        get() = sharedResults.firstOrNull()
            ?: results.maxByOrNull { it.producedAt }
}
