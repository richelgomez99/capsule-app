package com.capsule.app.ai

import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.model.Intent
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T032 — fakes [LlmProvider] to exercise [IntentPredictor]:
 *
 * - High-confidence provider output is passed through unchanged.
 * - Low-confidence output yields `AMBIGUOUS`-suitable downstream handling.
 * - Provider throwing any Throwable → graceful fallback (never throws).
 * - Provider exceeding the timeout → graceful fallback.
 * - Blank input → graceful fallback without hitting the provider.
 */
class IntentPredictorTest {

    @Test
    fun highConfidence_provider_isReturnedUnchanged() = runTest {
        val predictor = IntentPredictor(
            FakeProvider(IntentClassification(Intent.WANT_IT, 0.92f, LlmProvenance.LocalNano))
        )
        val result = predictor.classify("buy this cool gadget", "SHOPPING")
        assertEquals(Intent.WANT_IT, result.intent)
        assertEquals(0.92f, result.confidence, 0.0001f)
    }

    @Test
    fun lowConfidence_provider_isReturnedUnchanged() = runTest {
        val predictor = IntentPredictor(
            FakeProvider(IntentClassification(Intent.AMBIGUOUS, 0.20f, LlmProvenance.LocalNano))
        )
        val result = predictor.classify("asdf qwer", "OTHER")
        assertEquals(Intent.AMBIGUOUS, result.intent)
        assertTrue(result.confidence < 0.70f)
    }

    @Test
    fun providerThrows_yieldsFallbackAmbiguous() = runTest {
        val predictor = IntentPredictor(ThrowingProvider(RuntimeException("nano unavailable")))
        val result = predictor.classify("some text", "BROWSER")
        assertEquals(Intent.AMBIGUOUS, result.intent)
        assertEquals(0f, result.confidence, 0.0001f)
    }

    @Test
    fun providerThrowsNotImplemented_yieldsFallback() = runTest {
        // Mirrors the v1 NanoLlmProvider which still uses TODO() until AICore lands.
        val predictor = IntentPredictor(ThrowingProvider(NotImplementedError()))
        val result = predictor.classify("some text", "BROWSER")
        assertEquals(Intent.AMBIGUOUS, result.intent)
    }

    @Test
    fun providerTimesOut_yieldsFallback() = runTest {
        val predictor = IntentPredictor(
            SlowProvider(delayMillis = 5_000L),
            timeoutMillis = 50L
        )
        val result = predictor.classify("some text", "BROWSER")
        assertEquals(Intent.AMBIGUOUS, result.intent)
    }

    @Test
    fun blankInput_yieldsFallbackWithoutCallingProvider() = runTest {
        val provider = FakeProvider(IntentClassification(Intent.WANT_IT, 0.99f, LlmProvenance.LocalNano))
        val predictor = IntentPredictor(provider)

        val result = predictor.classify("   ", "OTHER")

        assertEquals(Intent.AMBIGUOUS, result.intent)
        assertEquals(0, provider.classifyCalls)
    }

    // ---- Fakes ----

    private class FakeProvider(private val answer: IntentClassification) : LlmProvider {
        var classifyCalls = 0
        override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification {
            classifyCalls++
            return answer
        }
        override suspend fun summarize(text: String, maxTokens: Int): SummaryResult = error("unused")
        override suspend fun scanSensitivity(text: String): SensitivityResult = error("unused")
        override suspend fun generateDayHeader(dayIsoDate: String, envelopeSummaries: List<String>): DayHeaderResult = error("unused")
        override suspend fun extractActions(
            text: String,
            contentType: String,
            state: com.capsule.app.data.entity.StateSnapshot,
            registeredFunctions: List<com.capsule.app.ai.model.AppFunctionSummary>,
            maxCandidates: Int
        ): com.capsule.app.ai.model.ActionExtractionResult = error("unused")
        override suspend fun embed(text: String): com.capsule.app.ai.EmbeddingResult? = null
    }

    private class ThrowingProvider(private val cause: Throwable) : LlmProvider {
        override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification {
            throw cause
        }
        override suspend fun summarize(text: String, maxTokens: Int): SummaryResult = error("unused")
        override suspend fun scanSensitivity(text: String): SensitivityResult = error("unused")
        override suspend fun generateDayHeader(dayIsoDate: String, envelopeSummaries: List<String>): DayHeaderResult = error("unused")
        override suspend fun extractActions(
            text: String,
            contentType: String,
            state: com.capsule.app.data.entity.StateSnapshot,
            registeredFunctions: List<com.capsule.app.ai.model.AppFunctionSummary>,
            maxCandidates: Int
        ): com.capsule.app.ai.model.ActionExtractionResult = error("unused")
        override suspend fun embed(text: String): com.capsule.app.ai.EmbeddingResult? = null
    }

    private class SlowProvider(private val delayMillis: Long) : LlmProvider {
        override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification {
            delay(delayMillis)
            return IntentClassification(Intent.WANT_IT, 1.0f, LlmProvenance.LocalNano)
        }
        override suspend fun summarize(text: String, maxTokens: Int): SummaryResult = error("unused")
        override suspend fun scanSensitivity(text: String): SensitivityResult = error("unused")
        override suspend fun generateDayHeader(dayIsoDate: String, envelopeSummaries: List<String>): DayHeaderResult = error("unused")
        override suspend fun extractActions(
            text: String,
            contentType: String,
            state: com.capsule.app.data.entity.StateSnapshot,
            registeredFunctions: List<com.capsule.app.ai.model.AppFunctionSummary>,
            maxCandidates: Int
        ): com.capsule.app.ai.model.ActionExtractionResult = error("unused")
        override suspend fun embed(text: String): com.capsule.app.ai.EmbeddingResult? = null
    }
}
