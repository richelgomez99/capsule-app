package com.capsule.app.ai

import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult

/**
 * Sole v1 LlmProvider — delegates to on-device Gemini Nano via AICore.
 *
 * All results carry [LlmProvenance.LocalNano] provenance.
 * Implementation will be fleshed out when AICore SDK integration lands (US2).
 */
class NanoLlmProvider : LlmProvider {

    override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification {
        TODO("AICore integration — US2")
    }

    override suspend fun summarize(text: String, maxTokens: Int): SummaryResult {
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
}
