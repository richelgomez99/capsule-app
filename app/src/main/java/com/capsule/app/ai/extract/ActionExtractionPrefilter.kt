package com.capsule.app.ai.extract

/**
 * T042 — Phase A pre-filter for the action-extraction pipeline.
 *
 * Runs synchronously in `:ml` after seal commits but BEFORE any
 * Nano work is enqueued. Returns true iff the envelope's text
 * contains at least one cheap signal that an actionable item may
 * be present. False → no `ACTION_EXTRACT` continuation is enqueued
 * (saves a Nano roundtrip on charger+wifi).
 *
 * Design — see specs/003-orbit-actions/research.md §2:
 *  - flight booking codes: /\b[A-Z]{2}\d{2,4}\b/
 *  - weekday tokens: Monday|Tuesday|...|Mon|Tue|...
 *  - imperative-list patterns: lines starting with "- ", "* ", "1. "
 *  - "RSVP", "confirm", "register", "book"
 *  - currency-followed-by-digits: $|€|£|¥ followed by digits
 *  - relative date phrases: "today", "tomorrow", "tonight", "next week"
 *  - time-of-day: "Xam"/"Xpm" or "HH:MM"
 *
 * Conservative bias: false positives (some Nano time wasted) are far
 * cheaper than false negatives (proposal never surfaces). The 0.55
 * confidence floor in [ActionExtractor] catches Nano's noise.
 */
object ActionExtractionPrefilter {

    /** Returns true iff [text] looks plausibly actionable. Empty/blank → false. */
    fun shouldExtract(text: String): Boolean {
        if (text.isBlank()) return false
        return RULES.any { it.containsMatchIn(text) }
    }

    private val RULES: List<Regex> = listOf(
        // Flight / booking confirmation codes (e.g., "UA437", "BA1023").
        Regex("""\b[A-Z]{2}\d{2,4}\b"""),
        // Generic 6-char alphanumeric reservation codes (PNRs).
        Regex("""\b[A-Z0-9]{6}\b"""),
        // Weekday tokens (full + 3-letter abbrev), case-insensitive.
        Regex(
            """\b(?:mon|tue|tues|wed|thu|thur|thurs|fri|sat|sun|monday|tuesday|wednesday|thursday|friday|saturday|sunday)\b""",
            RegexOption.IGNORE_CASE
        ),
        // Relative date phrases.
        Regex(
            """\b(?:today|tomorrow|tonight|yesterday|next\s+(?:week|month)|in\s+\d{1,3}\s+(?:minute|minutes|hour|hours|day|days|week|weeks))\b""",
            RegexOption.IGNORE_CASE
        ),
        // Imperative list markers anchored at line start.
        Regex("""(?m)^\s*(?:[-*•]|\d+\.)\s+\S"""),
        // Confirmation / RSVP / booking verbs.
        Regex(
            """\b(?:rsvp|confirm|register|book|booking|reservation|appointment|meeting|invite|invitation)\b""",
            RegexOption.IGNORE_CASE
        ),
        // Currency-prefixed amounts.
        Regex("""[$€£¥]\s?\d"""),
        // Time-of-day: "3pm", "10:30am", "14:30".
        Regex("""\b(?:[01]?\d|2[0-3]):[0-5]\d\b"""),
        Regex("""\b\d{1,2}\s?(?:am|pm)\b""", RegexOption.IGNORE_CASE)
    )
}
