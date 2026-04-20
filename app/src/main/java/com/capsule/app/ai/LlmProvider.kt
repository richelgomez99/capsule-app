package com.capsule.app.ai

import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult

/**
 * Principle IX — LLM Sovereignty.
 *
 * All AI inference MUST go through this interface. The sole v1 implementation
 * is [NanoLlmProvider] (on-device Gemini Nano via AICore). Future providers
 * (Cloud Boost, BYOK) will implement this same interface.
 */
interface LlmProvider {

    suspend fun classifyIntent(text: String, appCategory: String): IntentClassification

    suspend fun summarize(text: String, maxTokens: Int): SummaryResult

    suspend fun scanSensitivity(text: String): SensitivityResult

    suspend fun generateDayHeader(dayIsoDate: String, envelopeSummaries: List<String>): DayHeaderResult
}
