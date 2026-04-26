package com.capsule.app.ai

import com.capsule.app.ai.model.ActionExtractionResult
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.entity.StateSnapshot

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

    /**
     * Spec 003 v1.1 — Orbit Actions extraction.
     *
     * Given the raw envelope text, its content metadata, and the set of
     * registered functions, return up to [maxCandidates] proposals the user
     * is likely to want to confirm. Implementations MUST:
     *  - never invent a `functionId` outside [registeredFunctions]
     *  - emit `argsJson` that validates against the function's schema
     *  - clamp candidates to those with self-reported confidence ≥ 0.0
     *    (caller applies the 0.55 product floor)
     *  - sort the returned list by confidence descending
     *  - leave the returned list empty when the text is purely informational
     *
     * Implementations run on the :ml process and MUST NOT touch the network.
     *
     * See `specs/003-orbit-actions/contracts/action-extraction-contract.md`.
     */
    suspend fun extractActions(
        text: String,
        contentType: String,
        state: StateSnapshot,
        registeredFunctions: List<AppFunctionSummary>,
        maxCandidates: Int = 3
    ): ActionExtractionResult
}
