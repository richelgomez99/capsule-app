package com.capsule.app.diary

import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.ipc.EnvelopeViewParcel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DayHeaderGeneratorTest {

    private val base = 1_745_164_800_000L

    private fun env(
        id: String,
        appCategory: String,
        offsetMin: Long = 0
    ): EnvelopeViewParcel = EnvelopeViewParcel(
        id = id,
        contentType = "TEXT",
        textContent = "content $id",
        imageUri = null,
        intent = "AMBIGUOUS",
        intentSource = "AUTO_AMBIGUOUS",
        createdAtMillis = base + offsetMin * 60_000L,
        dayLocal = "2026-04-20",
        isArchived = false,
        title = null,
        domain = null,
        excerpt = null,
        summary = null,
        appCategory = appCategory,
        activityState = "UNKNOWN",
        hourLocal = 12,
        dayOfWeekLocal = 1
    )

    private class StubNano(
        private val response: String = "You spent the morning reading.",
        private val throws: Throwable? = null
    ) : LlmProvider {
        override suspend fun classifyIntent(text: String, appCategory: String) =
            error("unused")
        override suspend fun summarize(text: String, maxTokens: Int): SummaryResult =
            error("unused")
        override suspend fun scanSensitivity(text: String): SensitivityResult =
            error("unused")
        override suspend fun generateDayHeader(
            dayIsoDate: String,
            envelopeSummaries: List<String>
        ): DayHeaderResult {
            throws?.let { throw it }
            return DayHeaderResult(
                text = response,
                generationLocale = "en",
                provenance = LlmProvenance.LocalNano
            )
        }

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
    fun zeroEnvelopes_returnsBlankText() = runTest {
        val gen = DayHeaderGenerator(StubNano(), localeProvider = { "en-US" })
        val result = gen.generate("2026-04-20", emptyList())
        assertEquals("", result.text)
    }

    @Test
    fun oneEnvelope_shortCircuitsToSingleSentence_doesNotCallNano() = runTest {
        val nano = StubNano(response = "NANO WAS CALLED")
        val gen = DayHeaderGenerator(nano, localeProvider = { "en-US" })
        val result = gen.generate("2026-04-20", listOf(env("a", "BROWSER")))
        assertEquals("1 capture today from Browser.", result.text)
    }

    @Test
    fun twoEnvelopesSameApp_usesStructuredFallback() = runTest {
        val gen = DayHeaderGenerator(StubNano(response = "NANO"), localeProvider = { "en" })
        val result = gen.generate(
            "2026-04-20",
            listOf(env("a", "BROWSER"), env("b", "BROWSER"))
        )
        assertEquals("2 captures today from Browser.", result.text)
    }

    @Test
    fun twoEnvelopesDifferentApps_countsDistinctApps() = runTest {
        val gen = DayHeaderGenerator(StubNano(response = "NANO"), localeProvider = { "en" })
        val result = gen.generate(
            "2026-04-20",
            listOf(env("a", "BROWSER"), env("b", "MESSAGING"))
        )
        assertEquals("2 captures today across 2 apps.", result.text)
    }

    @Test
    fun threePlusEnvelopes_callsNano_andReturnsItsText() = runTest {
        val gen = DayHeaderGenerator(
            StubNano(response = "You bounced between three apps this morning."),
            localeProvider = { "en-US" }
        )
        val envelopes = (1..5).map { env("e$it", "BROWSER", offsetMin = it.toLong()) }
        val result = gen.generate("2026-04-20", envelopes)
        assertEquals("You bounced between three apps this morning.", result.text)
        assertEquals("en", result.generationLocale)
    }

    @Test
    fun nanoThrows_fallsBackToStructuredTemplate() = runTest {
        val gen = DayHeaderGenerator(
            StubNano(throws = NotImplementedError("v1 stub")),
            localeProvider = { "en" }
        )
        val envelopes = listOf(
            env("a", "BROWSER"),
            env("b", "BROWSER"),
            env("c", "BROWSER"),
            env("d", "MESSAGING")
        )
        val result = gen.generate("2026-04-20", envelopes)
        assertEquals("4 captures today across 2 apps.", result.text)
    }

    @Test
    fun unsupportedLocale_fallsBackToEnglishStructured() = runTest {
        val gen = DayHeaderGenerator(
            StubNano(response = "NANO SHOULD NOT BE CALLED"),
            localeProvider = { "fr-FR" }
        )
        val envelopes = (1..5).map { env("e$it", "BROWSER", offsetMin = it.toLong()) }
        val result = gen.generate("2026-04-20", envelopes)
        assertEquals("5 captures today from Browser.", result.text)
        assertEquals("en", result.generationLocale)
    }

    @Test
    fun supportedLocaleVariants_allRouteToNano() = runTest {
        val gen = DayHeaderGenerator(
            StubNano(response = "Nano response"),
            localeProvider = { "en-GB" }
        )
        val envelopes = (1..4).map { env("e$it", "BROWSER", offsetMin = it.toLong()) }
        val result = gen.generate("2026-04-20", envelopes)
        assertEquals("Nano response", result.text)
    }

    @Test
    fun blankLocale_defaultsToEnglish() = runTest {
        val gen = DayHeaderGenerator(StubNano(), localeProvider = { "" })
        val result = gen.generate("2026-04-20", emptyList())
        assertEquals("en", result.generationLocale)
    }

    @Test
    fun unknownAppCategory_stillRendersReadably() = runTest {
        val gen = DayHeaderGenerator(StubNano(), localeProvider = { "en" })
        val result = gen.generate("2026-04-20", listOf(env("a", "UNKNOWN_SOURCE")))
        assertTrue(result.text.contains("Unknown source"))
    }
}
