package com.capsule.app.ai

import com.capsule.app.ai.prompts.UrlSummaryPrompt

/**
 * T065 — URL-content summariser backed by [LlmProvider.summarize].
 *
 * Graceful-degrade contract (mirrors
 * [com.capsule.app.diary.DayHeaderGenerator] idiom):
 *   - Never throws.
 *   - On any failure path — [NotImplementedError] from the v1
 *     [NanoLlmProvider] stub, AICore unavailable, coroutine timeout,
 *     blank input, or the model returning the [UrlSummaryPrompt.SKIP_MARKER]
 *     sentinel — returns `null`. Callers persist
 *     `summaryModel = "fallback"` per contracts/continuation-engine-contract.md
 *     §4.1 step 5c "On Nano unavailable (SC-010 fallback), skip summary".
 *
 * The [modelLabel] is stamped onto successful results and surfaced in
 * `ContinuationResultEntity.summaryModel` so the Diary / audit trail
 * can distinguish local-Nano output from future Cloud Boost or BYOK
 * providers (Principle IX — LLM Sovereignty).
 */
class NanoSummariser(
    private val llmProvider: LlmProvider,
    private val modelLabel: String = DEFAULT_MODEL_LABEL
) {

    data class Summary(val text: String, val model: String)

    /**
     * Summarise [readableSlug] (optionally hinted by page [title]).
     * Returns `null` when the model declined, failed, or is unavailable.
     */
    suspend fun summarise(title: String?, readableSlug: String): Summary? {
        if (readableSlug.isBlank()) return null

        val prompt = UrlSummaryPrompt.build(title, readableSlug)

        val raw = try {
            llmProvider.summarize(prompt, UrlSummaryPrompt.MAX_SUMMARY_TOKENS)
        } catch (_: NotImplementedError) {
            // v1 NanoLlmProvider stub — expected until AICore ships.
            return null
        } catch (_: Throwable) {
            // Any other failure (timeout, binder death, AICore error) —
            // degrade silently; the caller stamps summaryModel="fallback".
            return null
        }

        val text = raw.text.trim()
        if (text.isEmpty()) return null
        if (text.equals(UrlSummaryPrompt.SKIP_MARKER, ignoreCase = true)) return null
        // Defensive: if the model just echoes the prompt preamble, skip.
        if (text.startsWith("You are a neutral reader", ignoreCase = true)) return null

        return Summary(text = text, model = modelLabel)
    }

    companion object {
        const val DEFAULT_MODEL_LABEL = "nano-v1"
        const val FALLBACK_MODEL_LABEL = "fallback"
    }
}
