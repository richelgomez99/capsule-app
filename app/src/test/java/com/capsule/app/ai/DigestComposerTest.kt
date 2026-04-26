package com.capsule.app.ai

import com.capsule.app.ai.model.ActionExtractionResult
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.LlmProvenance
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

/**
 * T066 (003 US3) — JVM unit test for [DigestComposer] per
 * weekly-digest-contract.md §5 / §9.
 *
 * Pure logic; no Room, no Android runtime. The composer's contract:
 *  - `< 3` envelopes → [DigestComposition.EmptyWindow]
 *  - non-English locale → structured fallback (`locale="fallback-structured"`)
 *  - LLM throws → structured fallback
 *  - LLM returns blank text → structured fallback
 *  - prompt grossly exceeds 4 KB after truncation floor → structured fallback
 *  - happy path → composer returns Nano output verbatim with provenance pass-through
 */
class DigestComposerTest {

    private val sundayApr26 = LocalDate.of(2026, 4, 26) // a Sunday
    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val window = DigestWindow.forSunday(zone, sundayApr26)

    @Test
    fun emptyWindow_returnsEmptyWindow_withNoLlmCall() = runTest {
        val llm = RecordingLlm(SummaryFixture.OK)
        val composer = DigestComposer(llm)

        val result = composer.compose(window, envelopes = emptyList())

        assertEquals(DigestComposition.EmptyWindow, result)
        assertEquals(0, llm.summarizeCalls)
    }

    @Test
    fun twoEnvelopes_returnsEmptyWindow_belowThreshold() = runTest {
        val llm = RecordingLlm(SummaryFixture.OK)
        val composer = DigestComposer(llm)

        val result = composer.compose(window, envelopes = listOf(
            envelope("e1", salience = 0.9f, day = "2026-04-20"),
            envelope("e2", salience = 0.8f, day = "2026-04-21")
        ))

        assertEquals(DigestComposition.EmptyWindow, result)
        assertEquals(0, llm.summarizeCalls)
    }

    @Test
    fun englishLocale_threeEnvelopes_routesToLlm_andReturnsItsText() = runTest {
        val llm = RecordingLlm(SummaryFixture.OK)
        val composer = DigestComposer(llm, locale = Locale.US)

        val result = composer.compose(window, envelopes = sampleEnvelopes())
        assertTrue(result is DigestComposition.Composed)
        result as DigestComposition.Composed

        assertEquals("This week was about reading articles.", result.text)
        assertEquals("en-US", result.locale)
        assertEquals(LlmProvenance.LOCAL_NANO, result.provenance)
        assertEquals(listOf("e1", "e2", "e3"), result.derivedFromEnvelopeIds)
        assertEquals(1, llm.summarizeCalls)
    }

    @Test
    fun nonEnglishLocale_routesToStructuredFallback_withoutCallingLlm() = runTest {
        val llm = RecordingLlm(SummaryFixture.OK)
        val composer = DigestComposer(llm, locale = Locale.FRANCE)

        val result = composer.compose(window, envelopes = sampleEnvelopes())
        assertTrue(result is DigestComposition.Composed)
        result as DigestComposition.Composed

        assertEquals(DigestComposer.LOCALE_FALLBACK, result.locale)
        assertTrue(
            "Fallback should mention capture count, got=${result.text}",
            result.text.contains("3 captures")
        )
        assertEquals(0, llm.summarizeCalls)
    }

    @Test
    fun llmThrows_routesToStructuredFallback() = runTest {
        val llm = RecordingLlm(SummaryFixture.THROW)
        val composer = DigestComposer(llm, locale = Locale.UK)

        val result = composer.compose(window, envelopes = sampleEnvelopes())
        assertTrue(result is DigestComposition.Composed)
        result as DigestComposition.Composed

        assertEquals(DigestComposer.LOCALE_FALLBACK, result.locale)
        assertTrue(result.text.startsWith("This week:"))
        assertEquals(1, llm.summarizeCalls)
    }

    @Test
    fun llmBlank_routesToStructuredFallback() = runTest {
        val llm = RecordingLlm(SummaryFixture.BLANK)
        val composer = DigestComposer(llm)

        val result = composer.compose(window, envelopes = sampleEnvelopes())
        assertTrue(result is DigestComposition.Composed)
        result as DigestComposition.Composed

        assertEquals(DigestComposer.LOCALE_FALLBACK, result.locale)
        assertEquals(1, llm.summarizeCalls)
    }

    @Test
    fun promptTinyBudget_belowFloor_routesToStructuredFallback() = runTest {
        // A 32-byte budget can't fit even a single rendered entry — the
        // truncation loop bottoms out at MIN_ENVELOPES_AFTER_TRUNCATION
        // and the composer falls back to structured English.
        val llm = RecordingLlm(SummaryFixture.OK)
        val composer = DigestComposer(llm, maxPromptBytes = 32)

        val result = composer.compose(window, envelopes = sampleEnvelopes(count = 8))
        assertTrue(result is DigestComposition.Composed)
        result as DigestComposition.Composed

        assertEquals(DigestComposer.LOCALE_FALLBACK, result.locale)
        assertEquals(0, llm.summarizeCalls)
    }

    @Test
    fun promptStaysUnder4KB_forNormalInput() = runTest {
        val llm = RecordingLlm(SummaryFixture.OK)
        val composer = DigestComposer(llm)

        composer.compose(window, envelopes = sampleEnvelopes(count = 50))

        assertEquals(1, llm.summarizeCalls)
        // Hard ceiling per contract §5: the composer must never hand a
        // prompt larger than 4 KB to the LLM; we double-check the
        // captured prompt rather than relying on internal trim logic.
        val promptBytes = llm.lastPrompt.toByteArray(Charsets.UTF_8).size
        assertTrue(
            "prompt was $promptBytes bytes; must be ≤ ${DigestComposer.MAX_PROMPT_BYTES}",
            promptBytes <= DigestComposer.MAX_PROMPT_BYTES
        )
    }

    @Test
    fun derivedFromEnvelopeIds_preservesInputOrder() = runTest {
        val llm = RecordingLlm(SummaryFixture.OK)
        val composer = DigestComposer(llm)
        val envelopes = listOf(
            envelope("z", salience = 0.5f),
            envelope("a", salience = 0.9f),
            envelope("m", salience = 0.7f)
        )

        val result = composer.compose(window, envelopes = envelopes)
        result as DigestComposition.Composed

        // Provenance integrity — order matches the worker's input list.
        assertEquals(listOf("z", "a", "m"), result.derivedFromEnvelopeIds)
    }

    @Test
    fun forSunday_throws_whenNotSunday() {
        val mondayApr27 = LocalDate.of(2026, 4, 27)
        var threw = false
        try { DigestWindow.forSunday(zone, mondayApr27) }
        catch (e: IllegalArgumentException) { threw = true }
        assertTrue(threw)
    }

    @Test
    fun forSunday_buildsMondayThroughSaturdayWindow() {
        val w = DigestWindow.forSunday(zone, sundayApr26)
        assertEquals(LocalDate.of(2026, 4, 19), w.windowStart)         // prev Sunday
        assertEquals(LocalDate.of(2026, 4, 25), w.windowEndInclusive)  // Saturday before
    }

    // ---- helpers --------------------------------------------------

    private fun sampleEnvelopes(count: Int = 3): List<DigestInputEnvelope> =
        (1..count).map { i ->
            envelope(
                id = "e$i",
                salience = (1.0f / i).coerceAtMost(1.0f),
                day = "2026-04-${20 + (i % 6)}"
            )
        }

    private fun envelope(
        id: String,
        salience: Float = 0.7f,
        day: String = "2026-04-22",
        intent: String = "REFERENCE",
        appCategory: String = "BROWSER"
    ): DigestInputEnvelope = DigestInputEnvelope(
        id = id,
        title = "Sample $id",
        intent = intent,
        appCategory = appCategory,
        dayLocal = day,
        salience = salience
    )

    private enum class SummaryFixture { OK, THROW, BLANK }

    /**
     * Records every `summarize` call so tests can assert call-count and
     * inspect the rendered prompt the composer hands to the LLM.
     */
    private class RecordingLlm(private val mode: SummaryFixture) : LlmProvider {
        var summarizeCalls = 0; private set
        var lastPrompt: String = ""; private set

        override suspend fun summarize(text: String, maxTokens: Int): SummaryResult {
            summarizeCalls++
            lastPrompt = text
            return when (mode) {
                SummaryFixture.OK -> SummaryResult(
                    text = "This week was about reading articles.",
                    generationLocale = "en-US",
                    provenance = LlmProvenance.LOCAL_NANO
                )
                SummaryFixture.THROW -> error("AICore unavailable")
                SummaryFixture.BLANK -> SummaryResult(
                    text = "   ",
                    generationLocale = "en-US",
                    provenance = LlmProvenance.LOCAL_NANO
                )
            }
        }

        // ---- unused LlmProvider members ---------------------------
        override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification =
            error("not used by DigestComposerTest")

        override suspend fun scanSensitivity(text: String): SensitivityResult =
            error("not used by DigestComposerTest")

        override suspend fun generateDayHeader(
            dayIsoDate: String,
            envelopeSummaries: List<String>
        ): DayHeaderResult = error("not used by DigestComposerTest")

        override suspend fun extractActions(
            text: String,
            contentType: String,
            state: StateSnapshot,
            registeredFunctions: List<AppFunctionSummary>,
            maxCandidates: Int
        ): ActionExtractionResult = error("not used by DigestComposerTest")
    }
}
