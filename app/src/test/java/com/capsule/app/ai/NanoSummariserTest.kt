package com.capsule.app.ai

import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T065 unit coverage for [NanoSummariser].
 *
 * Graceful-degrade contract:
 *   - Provider stub that throws [NotImplementedError]      → null
 *   - Provider that throws a generic exception             → null
 *   - Blank / empty input                                  → null (short-circuit, provider never called)
 *   - Provider returns blank or "SKIP"                     → null
 *   - Provider returns a normal 2-sentence summary         → Summary(text, "nano-v1")
 */
class NanoSummariserTest {

    private class FakeProvider(private val behaviour: (String) -> SummaryResult) : LlmProvider {
        var summarizeCalls = 0
            private set

        override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification =
            error("unused")

        override suspend fun summarize(text: String, maxTokens: Int): SummaryResult {
            summarizeCalls++
            return behaviour(text)
        }

        override suspend fun scanSensitivity(text: String): SensitivityResult = error("unused")

        override suspend fun generateDayHeader(
            dayIsoDate: String,
            envelopeSummaries: List<String>
        ): DayHeaderResult = error("unused")

        override suspend fun extractActions(
            text: String,
            contentType: String,
            state: com.capsule.app.data.entity.StateSnapshot,
            registeredFunctions: List<com.capsule.app.ai.model.AppFunctionSummary>,
            maxCandidates: Int
        ): com.capsule.app.ai.model.ActionExtractionResult = error("unused")

        override suspend fun embed(text: String): com.capsule.app.ai.EmbeddingResult? = null
    }

    @Test
    fun `returns null when provider throws NotImplementedError (v1 Nano stub)`() = runTest {
        val provider = FakeProvider { throw NotImplementedError("AICore integration — US2") }
        val result = NanoSummariser(provider).summarise("Title", "some readable content")
        assertNull(result)
    }

    @Test
    fun `returns null when provider throws generic exception`() = runTest {
        val provider = FakeProvider { throw RuntimeException("nano oom") }
        val result = NanoSummariser(provider).summarise("Title", "some readable content")
        assertNull(result)
    }

    @Test
    fun `short-circuits on blank input without touching provider`() = runTest {
        val provider = FakeProvider { error("should not be called") }
        val result = NanoSummariser(provider).summarise(null, "   ")
        assertNull(result)
        assertEquals(0, provider.summarizeCalls)
    }

    @Test
    fun `returns null when provider returns blank text`() = runTest {
        val provider = FakeProvider {
            SummaryResult(text = "   ", generationLocale = "en", provenance = LlmProvenance.LocalNano)
        }
        assertNull(NanoSummariser(provider).summarise(null, "hello world"))
    }

    @Test
    fun `returns null when provider returns SKIP sentinel (case-insensitive)`() = runTest {
        val provider = FakeProvider {
            SummaryResult(text = "skip", generationLocale = "en", provenance = LlmProvenance.LocalNano)
        }
        assertNull(NanoSummariser(provider).summarise(null, "hello world"))
    }

    @Test
    fun `returns Summary stamped with model label on happy path`() = runTest {
        val provider = FakeProvider {
            SummaryResult(
                text = "  The article reports on a new climate policy. It takes effect in March.  ",
                generationLocale = "en",
                provenance = LlmProvenance.LocalNano
            )
        }
        val result = NanoSummariser(provider).summarise(
            title = "New climate policy",
            readableSlug = "Readable body goes here..."
        )
        requireNotNull(result)
        assertEquals("The article reports on a new climate policy. It takes effect in March.", result.text)
        assertEquals("nano-v1", result.model)
        assertEquals(1, provider.summarizeCalls)
    }

    @Test
    fun `happy-path prompt carries both the title hint and the content slug`() = runTest {
        var capturedPrompt = ""
        val provider = FakeProvider { p ->
            capturedPrompt = p
            SummaryResult("ok result here.", "en", LlmProvenance.LocalNano)
        }
        NanoSummariser(provider).summarise(title = "Climate bill passes", readableSlug = "Article body.")
        assertTrue(capturedPrompt.contains("TITLE: Climate bill passes"))
        assertTrue(capturedPrompt.contains("CONTENT:"))
        assertTrue(capturedPrompt.contains("Article body."))
    }

    @Test
    fun `custom model label is stamped onto successful results`() = runTest {
        val provider = FakeProvider {
            SummaryResult("sentence one. sentence two.", "en", LlmProvenance.LocalNano)
        }
        val result = NanoSummariser(provider, modelLabel = "nano-v1.1-experimental").summarise("t", "body")
        assertEquals("nano-v1.1-experimental", result!!.model)
    }
}
