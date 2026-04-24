package com.capsule.app.diary

import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.LocaleSupport
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.data.ipc.EnvelopeViewParcel

/**
 * T048 — day-header paragraph generator for the diary top-of-day moment.
 *
 * Wraps [LlmProvider.generateDayHeader] with:
 *   1. A small-day short-circuit (0 envelopes → empty text; < 3 → single
 *      short structured sentence per research.md §10).
 *   2. Locale gating — if the user's locale's primary language is not in
 *      [LocaleSupport.NANO_SUPPORTED_LOCALES], we skip Nano entirely
 *      (v1 Nano is English-only per product decision) and fall back to
 *      the structured template. The returned [DayHeaderResult] carries
 *      `generationLocale = "en"` so the UI can render the "Generated in
 *      English" indicator per tasks.md T048.
 *   3. Nano-failure fallback — any exception from the LLM degrades to the
 *      structured `"{N} captures today across {M} apps"` template
 *      (or `"{N} captures from {top app}"` if `M == 1`) per research.md
 *      §10 Fallback.
 *
 * Pure + deterministic given the injected `localeProvider`. v1 callers
 * should pass `{ Locale.getDefault().toLanguageTag() }`; tests override.
 */
class DayHeaderGenerator(
    private val llmProvider: LlmProvider,
    private val localeProvider: () -> String = { java.util.Locale.getDefault().toLanguageTag() }
) {

    /**
     * Produce a [DayHeaderResult] for the given [dayIsoDate] and
     * [envelopes]. Never throws; LLM errors degrade to the fallback.
     *
     * @param envelopes must be pre-filtered to a single day. Ordering is
     *   not required; the generator uses them as a set.
     */
    suspend fun generate(
        dayIsoDate: String,
        envelopes: List<EnvelopeViewParcel>
    ): DayHeaderResult {
        val count = envelopes.size
        val languageTag = localeProvider()

        // 0 captures — caller should not render a header.
        if (count == 0) {
            return DayHeaderResult(
                text = "",
                generationLocale = primaryLanguage(languageTag),
                provenance = LlmProvenance.LocalNano
            )
        }

        // Locale gating — unsupported locale → structured fallback
        // generated in English.
        if (!LocaleSupport.isSupported(languageTag)) {
            return structuredFallback(envelopes, forcedEnglish = true)
        }

        // < 3 captures — per research.md §10, emit a single short sentence
        // without paying for a Nano call. Short-circuit matches the prompt
        // contract ("fewer than 3 → one short sentence").
        if (count < SHORT_DAY_THRESHOLD) {
            return structuredFallback(envelopes, forcedEnglish = false)
        }

        // Nano path — ask the provider for a 1–2 sentence paragraph.
        // Any failure (stub TODO, timeout, network, etc.) degrades to the
        // structured fallback so the diary never renders a blank header.
        return runCatching {
            val summaries = envelopes.map(::envelopeSummaryLine)
            llmProvider.generateDayHeader(dayIsoDate, summaries)
        }.getOrElse {
            structuredFallback(envelopes, forcedEnglish = false)
        }
    }

    private fun structuredFallback(
        envelopes: List<EnvelopeViewParcel>,
        forcedEnglish: Boolean
    ): DayHeaderResult {
        val count = envelopes.size
        val appCounts = envelopes.groupingBy { it.appCategory }.eachCount()
        val distinctApps = appCounts.size
        val topApp = appCounts.maxByOrNull { it.value }?.key ?: "OTHER"

        val text = when {
            count == 1 -> "1 capture today from ${friendlyApp(topApp)}."
            distinctApps == 1 -> "$count captures today from ${friendlyApp(topApp)}."
            else -> "$count captures today across $distinctApps apps."
        }

        return DayHeaderResult(
            text = text,
            generationLocale = "en",
            provenance = LlmProvenance.LocalNano
        )
    }

    private fun envelopeSummaryLine(env: EnvelopeViewParcel): String {
        val hh = env.hourLocal.toString().padStart(2, '0')
        val preview = env.textContent?.take(PREVIEW_CHARS)?.replace('\n', ' ') ?: ""
        return "- $hh:00 · ${env.appCategory} · ${env.intent} · $preview"
    }

    private fun primaryLanguage(tag: String): String =
        tag.substringBefore('-').lowercase().ifBlank { "en" }

    /**
     * Render the raw `AppCategory.name` enum string as something vaguely
     * English ("BROWSER" → "Browser"). Good enough for v1 fallback; a
     * richer localisation pass can happen when we add non-English locales.
     */
    private fun friendlyApp(appCategoryName: String): String =
        appCategoryName.lowercase().replace('_', ' ')
            .replaceFirstChar { it.uppercase() }

    companion object {
        /** research.md §10: "If there are fewer than 3 captures, write a single short sentence." */
        const val SHORT_DAY_THRESHOLD: Int = 3

        /** 80 chars of preview per envelope — matches the prompt template in research.md §10. */
        const val PREVIEW_CHARS: Int = 80
    }
}
