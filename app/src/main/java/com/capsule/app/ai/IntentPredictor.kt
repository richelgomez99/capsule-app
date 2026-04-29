package com.capsule.app.ai

import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.data.model.Intent
import kotlinx.coroutines.withTimeoutOrNull

/**
 * T036 — thin wrapper over [LlmProvider.classifyIntent] with a graceful-degrade
 * fallback path.
 *
 * Per research.md §Gemini Nano: when Nano is unavailable (SDK missing, model
 * not downloaded, timeout, RemoteException) the predictor MUST return
 * `(Intent.AMBIGUOUS, confidence = 0f, provenance = LocalNano)` so callers can
 * still proceed with `IntentSource.FALLBACK` and show the chip row. The
 * predictor NEVER throws.
 *
 * Every call routes through [LlmProvider] (T025a); v1 only registers
 * [NanoLlmProvider]. Future providers land via dependency injection.
 */
class IntentPredictor(
    private val llmProvider: LlmProvider,
    private val timeoutMillis: Long = DEFAULT_TIMEOUT_MS
) {

    /**
     * Best-effort classification. Always returns a result; never throws.
     */
    suspend fun classify(text: String, appCategory: String): IntentClassification {
        if (text.isBlank()) return fallback()

        return try {
            withTimeoutOrNull(timeoutMillis) {
                llmProvider.classifyIntent(text, appCategory)
            } ?: fallback()
        } catch (_: kotlin.NotImplementedError) {
            // NanoLlmProvider is still stubbed with TODO() in v1 until AICore
            // integration lands (US2). Treat as "Nano unavailable" — fall back
            // so US1 seal path works end-to-end before then.
            fallback()
        } catch (_: Throwable) {
            fallback()
        }
    }

    private fun fallback(): IntentClassification = IntentClassification(
        intent = Intent.AMBIGUOUS,
        confidence = 0f,
        provenance = LlmProvenance.LocalNano
    )

    companion object {
        const val DEFAULT_TIMEOUT_MS: Long = 1_500L
    }
}
