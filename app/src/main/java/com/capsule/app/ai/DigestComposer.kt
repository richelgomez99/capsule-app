package com.capsule.app.ai

import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.model.LlmProvenance
import com.capsule.app.data.model.toEntityEnum
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * T071 (003 US3) — assembles the weekly digest prompt and dispatches it
 * through [LlmProvider.summarize], with structural fallbacks per
 * weekly-digest-contract.md §5.
 *
 * Pure logic: takes a [DigestWindow] + already-loaded envelope rows so
 * the type can be exercised by JVM tests with no Room/binder runtime.
 * The owning worker ([com.capsule.app.continuation.workers.WeeklyDigestWorker])
 * loads envelopes, runs this, and persists the result.
 *
 * Fallback ladder (in order):
 *  1. `< 3` envelopes in the window → [DigestComposition.EmptyWindow]
 *     (the worker writes `DIGEST_SKIPPED reason=too_sparse`).
 *  2. Locale not English (`en*`) → structured English fallback,
 *     `locale="fallback-structured"`. We don't generate non-English
 *     digests in v1.1 because Nano summarisation quality varies
 *     substantially across languages.
 *  3. Nano `summarize` throws → same structured English fallback.
 *  4. Prompt exceeds 4 KB after truncation → structured fallback.
 */
class DigestComposer(
    private val llmProvider: LlmProvider,
    private val locale: Locale = Locale.getDefault(),
    private val maxPromptBytes: Int = MAX_PROMPT_BYTES
) {

    suspend fun compose(
        window: DigestWindow,
        envelopes: List<DigestInputEnvelope>
    ): DigestComposition {
        if (envelopes.size < MIN_ENVELOPES) return DigestComposition.EmptyWindow

        val derivedIds = envelopes.map { it.id }

        // Locale gate: only `en*` BCP47 tags get the LLM path; everything
        // else falls back to structured English. This is a hard gate so
        // multilingual users see a deterministic, explainable summary
        // until v1.2 broadens locale support.
        if (!isEnglish(locale)) {
            return DigestComposition.Composed(
                text = structuredFallback(window, envelopes),
                derivedFromEnvelopeIds = derivedIds,
                provenance = LlmProvenance.LOCAL_NANO,
                locale = LOCALE_FALLBACK
            )
        }

        val prompt = buildBoundedPrompt(window, envelopes)
        if (prompt.toByteArray(Charsets.UTF_8).size > maxPromptBytes) {
            // Even after truncation we couldn't fit; fall back rather
            // than send a knowingly-truncated prompt to Nano.
            return DigestComposition.Composed(
                text = structuredFallback(window, envelopes),
                derivedFromEnvelopeIds = derivedIds,
                provenance = LlmProvenance.LOCAL_NANO,
                locale = LOCALE_FALLBACK
            )
        }

        val result: SummaryResult? = try {
            llmProvider.summarize(text = prompt, maxTokens = SUMMARY_MAX_TOKENS)
        } catch (t: Throwable) {
            null
        }

        return if (result == null || result.text.isBlank()) {
            DigestComposition.Composed(
                text = structuredFallback(window, envelopes),
                derivedFromEnvelopeIds = derivedIds,
                provenance = LlmProvenance.LOCAL_NANO,
                locale = LOCALE_FALLBACK
            )
        } else {
            DigestComposition.Composed(
                text = result.text.trim(),
                derivedFromEnvelopeIds = derivedIds,
                provenance = result.provenance.toEntityEnum(),
                locale = result.generationLocale
            )
        }
    }

    /**
     * Builds the structured prompt and trims bottom-ranked envelopes
     * until it fits within [maxPromptBytes]. We keep the top-3 per day
     * but progressively drop the lowest-salience days first.
     */
    private fun buildBoundedPrompt(
        window: DigestWindow,
        envelopes: List<DigestInputEnvelope>
    ): String {
        var working = envelopes
        var rendered = renderPrompt(window, working)

        // Progressive truncation: drop the bottom 10% by salience until
        // we fit, with a floor of 5 envelopes (below which the structured
        // fallback is more useful than a near-empty LLM prompt).
        while (rendered.toByteArray(Charsets.UTF_8).size > maxPromptBytes &&
            working.size > MIN_ENVELOPES_AFTER_TRUNCATION
        ) {
            val keep = (working.size * 0.9).toInt().coerceAtLeast(MIN_ENVELOPES_AFTER_TRUNCATION)
            working = working.sortedByDescending { it.salience }.take(keep)
            rendered = renderPrompt(window, working)
        }
        return rendered
    }

    private fun renderPrompt(
        window: DigestWindow,
        envelopes: List<DigestInputEnvelope>
    ): String = buildString {
        append("[SYSTEM]\n")
        append(SYSTEM_PROMPT)
        append("\n\n[INPUT]\n")
        envelopes.groupBy { it.dayLocal }
            .toSortedMap()
            .forEach { (day, rows) ->
                val date = runCatching { LocalDate.parse(day) }.getOrNull()
                val label = date?.let { "${it} (${it.dayOfWeek.name.take(3).lowercase().replaceFirstChar { c -> c.uppercase() }})" }
                    ?: day
                val top = rows.sortedByDescending { it.salience }.take(3)
                append(label).append(": ")
                append(top.joinToString(" • ") { "[${intentLabel(it.intent)}] ${it.title}" })
                append('\n')
            }
        append("\n[CROSS-DAY]\n")
        append("Total captures: ${envelopes.size}\n")
        append("Top categories: ")
        append(
            envelopes.groupingBy { it.appCategory }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .take(3)
                .joinToString(", ") { "${it.key} (${it.value})" }
        )
        append('\n')
        append("Intent distribution: ")
        append(
            envelopes.groupingBy { it.intent }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .joinToString(", ") { "${intentLabel(it.key)}=${it.value}" }
        )
        append('\n')
    }

    /**
     * Deterministic fallback when Nano isn't available or the user's
     * locale isn't supported. Uses the same data the LLM would have seen
     * but renders a fixed sentence template so the output is predictable.
     */
    private fun structuredFallback(
        window: DigestWindow,
        envelopes: List<DigestInputEnvelope>
    ): String {
        val total = envelopes.size
        val categoryCounts = envelopes.groupingBy { it.appCategory }.eachCount()
        val topCategories = categoryCounts.entries
            .sortedByDescending { it.value }
            .take(3)
            .joinToString(", ") { it.key.lowercase().replace('_', ' ') }
        val intentCounts = envelopes.groupingBy { it.intent }.eachCount()
        val topIntent = intentCounts.entries.maxByOrNull { it.value }?.key ?: "REFERENCE"
        val sundayLabel = window.targetSunday.format(DateTimeFormatter.ofPattern("MMM d", Locale.US))
        return buildString {
            append("This week: ")
            append("$total captures across ${categoryCounts.size} ")
            append(if (categoryCounts.size == 1) "category" else "categories")
            append(". ")
            if (topCategories.isNotEmpty()) {
                append("Most often: $topCategories. ")
            }
            append("Intent leaned ")
            append(intentLabel(topIntent).lowercase())
            append(". ")
            append("Surfaced on $sundayLabel.")
        }
    }

    private fun intentLabel(raw: String): String = when (raw.uppercase()) {
        "WANT_IT" -> "Want it"
        "REFERENCE" -> "Reference"
        "FOR_SOMEONE" -> "For someone"
        "INTERESTING" -> "Interesting"
        "AMBIGUOUS" -> "Ambiguous"
        else -> raw
    }

    private fun isEnglish(locale: Locale): Boolean =
        locale.language.equals("en", ignoreCase = true)

    companion object {
        const val MIN_ENVELOPES = 3
        const val MIN_ENVELOPES_AFTER_TRUNCATION = 5
        const val MAX_PROMPT_BYTES = 4 * 1024
        const val SUMMARY_MAX_TOKENS = 300
        const val LOCALE_FALLBACK = "fallback-structured"

        private const val SYSTEM_PROMPT =
            "You are summarising a user's week of personal captures from " +
                "their journal. Write 4–6 sentences. Friendly, observational, " +
                "not promotional. No first person plural. Use the user's intent " +
                "labels (\"Want it\", \"Reference\", \"For someone\", " +
                "\"Interesting\") when helpful. Do not enumerate every entry. " +
                "Surface patterns: recurring topics, app-category shifts, " +
                "intent shifts."
    }
}

/**
 * Window of days a single weekly digest covers.
 *
 * Per weekly-digest-contract.md §4: `[targetSunday - 7d, targetSunday - 1d]`
 * inclusive in the device's local zone. The target Sunday itself is
 * excluded — its day-header runs separately on its own day.
 */
data class DigestWindow(
    val zoneId: ZoneId,
    val targetSunday: LocalDate,
    val windowStart: LocalDate,
    val windowEndInclusive: LocalDate
) {
    init {
        require(!windowEndInclusive.isBefore(windowStart)) {
            "windowEndInclusive ($windowEndInclusive) precedes windowStart ($windowStart)"
        }
    }

    companion object {
        /**
         * Builds a `[Mon..Sat] → Sun` window for the given Sunday.
         * Throws if [targetSunday] is not actually a Sunday — the worker
         * uses [java.time.DayOfWeek] math to pick the right anchor and
         * passing a non-Sunday is a programmer error.
         */
        fun forSunday(zoneId: ZoneId, targetSunday: LocalDate): DigestWindow {
            require(targetSunday.dayOfWeek == java.time.DayOfWeek.SUNDAY) {
                "targetSunday must be a Sunday; got ${targetSunday.dayOfWeek}"
            }
            return DigestWindow(
                zoneId = zoneId,
                targetSunday = targetSunday,
                windowStart = targetSunday.minusDays(7),
                windowEndInclusive = targetSunday.minusDays(1)
            )
        }
    }
}

/**
 * One row of the digest input. Built by the worker from
 * [com.capsule.app.data.entity.IntentEnvelopeEntity] rows; only the
 * fields the composer actually reads are projected so unit tests can
 * fabricate inputs without the full entity.
 */
data class DigestInputEnvelope(
    val id: String,
    val title: String,
    val intent: String,
    val appCategory: String,
    val dayLocal: String,
    val salience: Float
)

/** What the composer hands back to the worker. */
sealed class DigestComposition {
    /** `< 3` envelopes total — worker writes `DIGEST_SKIPPED reason=too_sparse`. */
    data object EmptyWindow : DigestComposition()

    /**
     * Either an LLM-generated paragraph or the structured English
     * fallback. The two are distinguished by [locale]: anything other
     * than "en"/"en-*" is the structured fallback.
     */
    data class Composed(
        val text: String,
        val derivedFromEnvelopeIds: List<String>,
        val provenance: LlmProvenance,
        val locale: String
    ) : DigestComposition()
}
