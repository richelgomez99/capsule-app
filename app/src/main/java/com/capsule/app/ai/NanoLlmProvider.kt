package com.capsule.app.ai

import com.capsule.app.ai.model.ActionExtractionResult
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.entity.StateSnapshot

/**
 * Sole v1 LlmProvider — delegates to on-device Gemini Nano via AICore.
 *
 * All results carry [LlmProvenance.LocalNano] provenance.
 * Implementation will be fleshed out when AICore SDK integration lands (US2).
 *
 * **Diagnostic seam (T097, spec/003)**: when [LlmProviderDiagnostics.forceNanoUnavailable]
 * is `true`, the methods that production code routes through the LLM in 003's
 * Orbit Actions / Weekly Digest paths ([extractActions], [summarize]) throw
 * [NanoUnavailableException] *before* doing any work. The flag is intended to
 * be flipped only from the debug-build `DiagnosticsActivity`; production code
 * paths never set it. See quickstart §6 N2.
 */
class NanoLlmProvider : LlmProvider {

    override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification {
        TODO("AICore integration — US2")
    }

    override suspend fun summarize(text: String, maxTokens: Int): SummaryResult {
        if (LlmProviderDiagnostics.forceNanoUnavailable) {
            throw NanoUnavailableException("forced via LlmProviderDiagnostics (debug seam)")
        }
        TODO("AICore integration — US2")
    }

    override suspend fun scanSensitivity(text: String): SensitivityResult {
        TODO("AICore integration — US2")
    }

    override suspend fun generateDayHeader(
        dayIsoDate: String,
        envelopeSummaries: List<String>
    ): DayHeaderResult {
        TODO("AICore integration — US2")
    }

    override suspend fun extractActions(
        text: String,
        contentType: String,
        state: StateSnapshot,
        registeredFunctions: List<AppFunctionSummary>,
        maxCandidates: Int
    ): ActionExtractionResult {
        if (LlmProviderDiagnostics.forceNanoUnavailable) {
            throw NanoUnavailableException("forced via LlmProviderDiagnostics (debug seam)")
        }
        // Until AICore is wired up, return an empty list with LocalNano
        // provenance. Callers treat this the same as a model that decided
        // there are no actions in the text — extraction-contract §5.
        return ActionExtractionResult(
            provenance = LlmProvenance.LocalNano,
            candidates = emptyList()
        )
    }

    /**
     * T124 — embedding via AICore (TODO until AICore SDK lands).
     *
     * Graceful-degrade contract per [LlmProvider.embed]:
     *  - blank input → `null` (no AICore call),
     *  - [LlmProviderDiagnostics.forceNanoUnavailable] flag set → `null`
     *    (no exception escapes — embedding is a best-effort signal that
     *    must never crash the cluster worker),
     *  - any [Throwable] escaping the underlying call → `null`.
     *
     * When the AICore embedding API lands, this method will:
     *  1. early-return `null` for `text.isBlank()`,
     *  2. invoke the AICore embedding model,
     *  3. wrap the float[] in [EmbeddingResult] stamped with [MODEL_LABEL]
     *     and the model's dimensionality,
     *  4. catch [Throwable] → log + return `null`.
     */
    override suspend fun embed(text: String): EmbeddingResult? {
        if (text.isBlank()) return null
        if (LlmProviderDiagnostics.forceNanoUnavailable) return null
        return try {
            // TODO(US8 — AICore embedding integration): replace with real call.
            // Returning null until the AICore embedding API is available; the
            // cluster worker treats null as "no embedding available, skip".
            null
        } catch (_: Throwable) {
            null
        }
    }

    companion object {
        /**
         * Stamped onto every [EmbeddingResult] this provider produces, and
         * onto every [com.capsule.app.data.entity.ClusterEntity.modelLabel]
         * so the worker can refuse to compute similarity across model rev
         * boundaries (FR-038, FR-039). Bumped when AICore ships a new build.
         */
        const val MODEL_LABEL: String = "nano-v4-build-2026-05-01"
    }
}

