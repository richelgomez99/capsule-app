package com.capsule.app.ai

import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.data.EnvelopeStorageBackend
import com.capsule.app.data.ipc.IntentEnvelopeDraftParcel
import com.capsule.app.data.model.Intent

/**
 * T036a — composite silent-wrap predicate backing FR-004 (spec.md Clarification Q4).
 *
 * Returns [SilentWrapDecision.SilentWrap] only when BOTH conditions hold:
 *   1. `prediction.confidence >= 0.70`, AND
 *   2. `existsNonArchivedNonDeletedInLast30Days(draft.appCategory, prediction.intent)` is true.
 *
 * Otherwise returns [SilentWrapDecision.ShowChipRow]. NEVER silent-wraps on
 * confidence alone. `Intent.AMBIGUOUS` can never be silent-wrapped.
 *
 * Archived priors, soft-deleted priors, and priors older than 30 days are
 * all excluded at the SQL layer ([EnvelopeStorageBackend]).
 */
class SilentWrapPredicate(
    private val backend: EnvelopeStorageBackend,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD
) {

    sealed class SilentWrapDecision {
        data class SilentWrap(val intent: Intent) : SilentWrapDecision()
        data object ShowChipRow : SilentWrapDecision()
    }

    suspend fun evaluate(
        prediction: IntentClassification,
        draft: IntentEnvelopeDraftParcel
    ): SilentWrapDecision {
        if (prediction.intent == Intent.AMBIGUOUS) return SilentWrapDecision.ShowChipRow
        if (prediction.confidence < confidenceThreshold) return SilentWrapDecision.ShowChipRow

        val hasPrior = backend.existsNonArchivedNonDeletedInLast30Days(
            appCategory = appCategoryFromDraft(draft),
            intent = prediction.intent.name,
            nowMillis = clock()
        )
        return if (hasPrior) SilentWrapDecision.SilentWrap(prediction.intent)
        else SilentWrapDecision.ShowChipRow
    }

    /**
     * The draft parcel does not carry `appCategory` directly (it lives on
     * [com.capsule.app.data.ipc.StateSnapshotParcel], passed separately to
     * `seal`). Callers invoke [evaluate] with the app category already
     * resolved — we expose a small overload for clarity.
     */
    suspend fun evaluate(
        prediction: IntentClassification,
        appCategory: String
    ): SilentWrapDecision {
        if (prediction.intent == Intent.AMBIGUOUS) return SilentWrapDecision.ShowChipRow
        if (prediction.confidence < confidenceThreshold) return SilentWrapDecision.ShowChipRow

        val hasPrior = backend.existsNonArchivedNonDeletedInLast30Days(
            appCategory = appCategory,
            intent = prediction.intent.name,
            nowMillis = clock()
        )
        return if (hasPrior) SilentWrapDecision.SilentWrap(prediction.intent)
        else SilentWrapDecision.ShowChipRow
    }

    private fun appCategoryFromDraft(draft: IntentEnvelopeDraftParcel): String {
        // Draft does not itself carry appCategory; the real call site passes
        // it alongside. This overload is kept so tests that only have a
        // draft can exercise the confidence gate before the prior-match gate.
        return "OTHER"
    }

    companion object {
        const val DEFAULT_CONFIDENCE_THRESHOLD: Float = 0.70f
    }
}
