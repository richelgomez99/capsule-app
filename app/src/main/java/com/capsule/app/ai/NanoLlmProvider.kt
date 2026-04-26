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
}

