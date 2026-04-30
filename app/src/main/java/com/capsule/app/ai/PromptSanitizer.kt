package com.capsule.app.ai

/**
 * T137 — shared prompt-injection defence per spec 002 FR-034.
 *
 * Two surfaces:
 *
 *  - [sanitizeInput]: applied to every user-supplied excerpt **before**
 *    it is interpolated into a model prompt. Neutralises common jail-
 *    break phrases by replacing them with the sentinel `[redacted]`
 *    rather than dropping them silently — preserving length lets the
 *    model see "the user tried to inject here" without acting on it.
 *
 *  - [validateOutput]: applied to every model response **before** it is
 *    returned to UI/persistence. Returns `false` when the response
 *    looks like the model echoed an injection or otherwise broke role
 *    (e.g. began with a role tag). Callers MUST treat `false` as a
 *    null result and degrade gracefully (`ClusterSummariser` returns
 *    `null`; same contract as [NanoSummariser]).
 *
 * Designed for reuse by spec 003's `ActionExtractor` in v1.1+ per the
 * status-log entry of 2026-04-27.
 */
object PromptSanitizer {

    private const val REDACTED = "[redacted]"

    /**
     * Patterns that, if present in user input, are replaced with
     * [REDACTED]. Order is significant only for overlapping matches
     * (we use [Regex.replace] sequentially so longer/more specific
     * patterns must precede their shorter cousins).
     */
    private val INPUT_NEUTRALIZERS: List<Regex> = listOf(
        // "Ignore prior/previous/all instructions"
        Regex(
            pattern = """ignore\s+(?:prior|previous|all|the\s+above)[\s\S]{0,80}?instructions?""",
            options = setOf(RegexOption.IGNORE_CASE)
        ),
        // "Disregard prior/previous instructions"
        Regex(
            pattern = """disregard\s+(?:prior|previous|the\s+above)[\s\S]{0,80}?instructions?""",
            options = setOf(RegexOption.IGNORE_CASE)
        ),
        // Closing-prompt tag ejection: </prompt>, </system>, </user>, </assistant>
        Regex(
            pattern = """</\s*(?:prompt|system|user|assistant)\s*>""",
            options = setOf(RegexOption.IGNORE_CASE)
        ),
        // Opening-prompt tag injection
        Regex(
            pattern = """<\s*(?:system|assistant)\s*>""",
            options = setOf(RegexOption.IGNORE_CASE)
        ),
        // Markdown link injection — keep the visible text, drop the URL.
        // We replace the whole "[text](url)" with "[redacted]" because
        // we cannot trust either side; the summary doesn't render
        // markdown anyway (Compose Text), but a future renderer might.
        Regex(
            pattern = """\[[^\]]{1,80}]\([^)]{1,200}\)""",
            options = emptySet()
        ),
        // "All citations should be made up / fabricated / fake"
        Regex(
            pattern = """(?:all\s+)?citations?\s+(?:should\s+be|are|must\s+be)\s+(?:made\s*up|fake|fabricated|invented)""",
            options = setOf(RegexOption.IGNORE_CASE)
        ),
        // "Now act as / pretend you are / you are now a different assistant"
        Regex(
            pattern = """(?:now\s+)?(?:act\s+as|pretend\s+(?:to\s+be|you\s+are)|you\s+are\s+now)\s+(?:a\s+)?(?:different\s+)?(?:assistant|ai|model|system)""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
    )

    /** Patterns that, if present in model output, indicate injection echo or role break. */
    private val OUTPUT_REJECTERS: List<Regex> = listOf(
        // Output begins with a role tag.
        Regex(
            pattern = """^\s*(?:system|assistant|user)\s*:""",
            options = setOf(RegexOption.IGNORE_CASE)
        ),
        // Output begins with a prompt-tag.
        Regex(
            pattern = """^\s*<\s*(?:system|assistant|prompt|user)\s*>""",
            options = setOf(RegexOption.IGNORE_CASE)
        ),
        // Output echoes a known injection refrain.
        Regex(
            pattern = """ignore\s+(?:prior|previous|all|the\s+above)[\s\S]{0,40}?instructions?""",
            options = setOf(RegexOption.IGNORE_CASE)
        ),
        // Output declares fabricated citations.
        Regex(
            pattern = """citations?\s+(?:are|were)\s+(?:made\s*up|fake|fabricated|invented)""",
            options = setOf(RegexOption.IGNORE_CASE)
        )
    )

    /** Replace every matched [INPUT_NEUTRALIZERS] occurrence with [REDACTED]. */
    fun sanitizeInput(text: String): String {
        if (text.isEmpty()) return text
        var out = text
        for (rx in INPUT_NEUTRALIZERS) {
            out = rx.replace(out, REDACTED)
        }
        return out
    }

    /** Returns `true` when [text] looks safe to surface; `false` when caller must degrade. */
    fun validateOutput(text: String): Boolean {
        if (text.isEmpty()) return false
        for (rx in OUTPUT_REJECTERS) {
            if (rx.containsMatchIn(text)) return false
        }
        return true
    }
}
