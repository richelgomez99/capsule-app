package com.capsule.app.ai.prompts

/**
 * T064 — prompt template for URL content summarisation via on-device
 * Gemini Nano. Produces **2–3 neutral sentences**, no speculation, no
 * PII fabrication, no marketing language.
 *
 * Contract: contracts/continuation-engine-contract.md §4.1 step 5c.
 *
 * The prompt is intentionally terse. Nano follows negative constraints
 * better when they are listed explicitly; the [SKIP_MARKER] sentinel
 * gives the model a safe exit when the content is too short or unclear
 * to summarise honestly — callers treat `SKIP` the same as a throw and
 * degrade to `summaryModel = "fallback"`.
 */
object UrlSummaryPrompt {

    /** Maximum characters of readable content we feed into the prompt. */
    const val MAX_CONTENT_CHARS = 1500

    /** Token budget for the summary response (SummaryResult.text). */
    const val MAX_SUMMARY_TOKENS = 120

    /** Sentinel the model may return when it refuses to summarise. */
    const val SKIP_MARKER = "SKIP"

    private const val SYSTEM =
        "You are a neutral reader. Summarise the article below in 2 to 3 plain sentences.\n" +
            "Rules:\n" +
            "- No speculation about motive, tone, or intent.\n" +
            "- Do not fabricate names, numbers, quotes, or dates. Only use facts stated in the article.\n" +
            "- No marketing language. No calls to action.\n" +
            "- If the article is unclear, paywalled, or too short to summarise honestly, respond with the single word: SKIP."

    /**
     * Build the full prompt. [title] is optional (gateway may return
     * null if the page had no `<title>`). [readableSlug] is the plain
     * text extracted by Readability; it will be truncated to
     * [MAX_CONTENT_CHARS] here so callers don't have to.
     */
    fun build(title: String?, readableSlug: String): String {
        val trimmedTitle = title?.take(200)?.trim()?.ifBlank { null }
        val content = readableSlug.take(MAX_CONTENT_CHARS)
        return buildString {
            append(SYSTEM)
            append("\n\n")
            if (trimmedTitle != null) {
                append("TITLE: ").append(trimmedTitle).append('\n')
            }
            append("CONTENT:\n")
            append(content)
        }
    }
}
