package com.capsule.app.ai.prompts

/**
 * T138 — prompt template for cluster summarisation per spec 002 FR-031
 * to FR-035.
 *
 * Produces **≤ 3 bullets, ≤ 240 chars each**, every bullet citing at
 * least one envelope_id. The model speaks in agent voice (third
 * person, present tense). The [SKIP_MARKER] sentinel gives the model
 * a safe exit when it cannot honestly summarise the cluster — callers
 * treat that the same as any other failure and return `null`.
 */
object ClusterSummaryPrompt {

    /** Maximum characters of any one envelope excerpt we send to the model. */
    const val MAX_EXCERPT_CHARS = 600

    /** Token budget for the summary response (`SummaryResult.text`). */
    const val MAX_SUMMARY_TOKENS = 240

    /** Hard cap: number of bullets the post-processor will emit. */
    const val MAX_BULLETS = 3

    /** Hard cap: characters per bullet (truncated with `…`). */
    const val MAX_BULLET_CHARS = 240

    /** Sentinel the model may return when it refuses to summarise. */
    const val SKIP_MARKER = "SKIP"

    private const val SYSTEM =
        "You are an agent observer summarising a cluster of related captures the user saved.\n" +
            "Output up to 3 bullets, one per line, each starting with `- `.\n" +
            "Rules:\n" +
            "- Every bullet MUST cite at least one envelope_id from the input, in square brackets, e.g. [env-abc123].\n" +
            "- Each bullet is at most 240 characters.\n" +
            "- Speak in third person, present tense. No \"I\", no \"you\".\n" +
            "- No speculation about motive. Do not fabricate names, numbers, quotes, or dates.\n" +
            "- No marketing language. No calls to action.\n" +
            "- If the cluster is too sparse or unclear to summarise honestly, respond with the single word: SKIP."

    /**
     * Build the prompt. Each member is shown with its envelope id so
     * the model knows which token to cite. Excerpts are truncated to
     * [MAX_EXCERPT_CHARS] here so callers don't have to.
     */
    fun build(members: List<Member>): String {
        require(members.isNotEmpty()) { "ClusterSummaryPrompt.build needs at least one member" }
        return buildString {
            append(SYSTEM)
            append("\n\nCAPTURES:\n")
            for (m in members) {
                append("- envelope_id: ").append(m.envelopeId).append('\n')
                append("  excerpt: ")
                append(m.excerpt.take(MAX_EXCERPT_CHARS).trim())
                append('\n')
            }
        }
    }

    /** Pre-sanitised cluster member excerpt + its citation id. */
    data class Member(
        val envelopeId: String,
        val excerpt: String
    )
}
