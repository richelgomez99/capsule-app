package com.capsule.app.ai

import com.capsule.app.ai.prompts.ClusterSummaryPrompt
import com.capsule.app.data.dao.ClusterWithMembers

/**
 * T138 — cluster-foreground summariser. Spec 002 FR-031 → FR-035.
 *
 * Graceful-degrade contract (mirrors [NanoSummariser]):
 *   - Never throws.
 *   - Returns `null` on any failure path: blank input, model
 *     unavailable, [PromptSanitizer.validateOutput] rejection, the
 *     [ClusterSummaryPrompt.SKIP_MARKER] sentinel, or any bullet
 *     missing the FR-033 citation requirement.
 *
 * Output bounds enforced post-hoc rather than trusted to the model:
 *   - At most [ClusterSummaryPrompt.MAX_BULLETS] bullets.
 *   - At most [ClusterSummaryPrompt.MAX_BULLET_CHARS] per bullet,
 *     truncated with `…` if the model overshoots.
 *   - Every surviving bullet must contain at least one citation token
 *     `[envelope_id]` referencing a member of the cluster (defence
 *     against the hostile prompt "All citations should be made up").
 *
 * The [modelLabel] is stamped onto successful results so audit /
 * Diary surfaces can distinguish local-Nano output from Cloud / BYOK
 * (Principle IX — LLM Sovereignty). Default is whatever the wired
 * provider declares; pass explicitly in tests.
 */
class ClusterSummariser(
    private val llmProvider: LlmProvider,
    private val modelLabel: String = NanoLlmProvider.MODEL_LABEL
) {

    data class ClusterSummary(
        val bullets: List<String>,
        val citations: Set<String>,
        val model: String
    )

    /**
     * Summarise [cluster]. Returns `null` if the cluster has < 1
     * surviving member, the model declines, or the response fails any
     * post-hoc guard. Caller's responsibility to handle null and skip
     * UI surfacing.
     */
    suspend fun summarise(cluster: ClusterWithMembers): ClusterSummary? {
        // Hydrate (envelope_id, excerpt) pairs, dropping members whose
        // envelope row vanished between gate and read or that are now
        // soft-deleted. The repository's surviving-member-count gate
        // already runs at observe time, but a member could die between
        // gate and our read — defence in depth.
        val members = cluster.members
            .mapNotNull { mwe ->
                val env = mwe.envelope ?: return@mapNotNull null
                if (env.isDeleted || env.isArchived) return@mapNotNull null
                val raw = env.textContent?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                ClusterSummaryPrompt.Member(
                    envelopeId = env.id,
                    excerpt = PromptSanitizer.sanitizeInput(raw)
                )
            }
        if (members.isEmpty()) return null

        val validIds: Set<String> = members.map { it.envelopeId }.toSet()
        val prompt = ClusterSummaryPrompt.build(members)

        val raw = try {
            llmProvider.summarize(prompt, ClusterSummaryPrompt.MAX_SUMMARY_TOKENS)
        } catch (_: NotImplementedError) {
            // v1 NanoLlmProvider stub.
            return null
        } catch (_: Throwable) {
            // Any other failure (timeout, binder death, AICore error, cloud IO).
            return null
        }

        val response = raw.text.trim()
        if (response.isEmpty()) return null
        if (response.equals(ClusterSummaryPrompt.SKIP_MARKER, ignoreCase = true)) return null
        if (!PromptSanitizer.validateOutput(response)) return null

        // Split into bullets. Accept lines starting with `- `, `* `, or `• `.
        val bullets = response.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                when {
                    line.startsWith("- ") -> line.removePrefix("- ").trim()
                    line.startsWith("* ") -> line.removePrefix("* ").trim()
                    line.startsWith("• ") -> line.removePrefix("• ").trim()
                    else -> null
                }
            }
            .map { truncate(it, ClusterSummaryPrompt.MAX_BULLET_CHARS) }
            .take(ClusterSummaryPrompt.MAX_BULLETS)
            .toList()

        if (bullets.isEmpty()) return null

        // FR-033: every bullet must cite at least one envelope_id from
        // the cluster. We rely on the bracket form `[env-...]` because
        // that is exactly what the prompt asks for; any other form is
        // treated as missing for safety.
        val cited: MutableSet<String> = linkedSetOf()
        for (b in bullets) {
            val ids = CITATION_REGEX.findAll(b)
                .map { it.groupValues[1] }
                .filter { it in validIds }
                .toList()
            if (ids.isEmpty()) {
                // One uncited bullet poisons the result — refuse the lot.
                return null
            }
            cited += ids
        }

        return ClusterSummary(
            bullets = bullets,
            citations = cited,
            model = modelLabel
        )
    }

    private fun truncate(text: String, max: Int): String =
        if (text.length <= max) text else text.take(max - 1).trimEnd() + "…"

    private companion object {
        // Matches `[envelope_id]` where envelope_id allows letters,
        // digits, dash and underscore. Keep permissive — id format is
        // owned by capture, not this layer.
        val CITATION_REGEX = Regex("""\[([A-Za-z0-9_\-]{1,64})]""")
    }
}
