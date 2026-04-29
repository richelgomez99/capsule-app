package com.capsule.app.cluster

import com.capsule.app.ai.EmbeddingResult
import kotlin.math.sqrt

/**
 * Spec 002 Phase 11 / T127 — pure-Kotlin similarity primitives backing
 * the cluster-detection worker. No Android, Room, or AICore dependency
 * — runs in any unit-test JVM.
 *
 * Three independent operators feed the [isCluster] composite predicate:
 *  - [cosine] over Nano 4 sentence embeddings (FR-028a),
 *  - distinct-domain count via [Iterable.distinctDomains] (FR-028b),
 *  - [tokenJaccard] over extracted nouns (FR-028c).
 *
 * All thresholds live in [Thresholds] so FR-028 numbers move in one
 * place when the May-4 measurement window finishes.
 */
object SimilarityEngine {

    /**
     * Locked thresholds per FR-028 (/autoplan 2026-04-26 E8 false-positive
     * guard). Bumping these requires an amendment cycle.
     */
    object Thresholds {
        /** FR-028a — pairwise cosine over Nano 4 embeddings. Inclusive. */
        const val COSINE: Float = 0.7f
        /** FR-028b — distinct hostname count across cluster members. */
        const val MIN_DOMAINS: Int = 2
        /** FR-028c — pairwise token-jaccard over extracted nouns. Inclusive. */
        const val JACCARD: Float = 0.3f
        /** Minimum cluster size (FR-026 / FR-028 implicit ≥3). */
        const val MIN_SIZE: Int = 3
        /** Bucket width (FR-026 / FR-028 — single 4-hour window). */
        const val BUCKET_WIDTH_MS: Long = 4L * 60L * 60L * 1000L
    }

    /**
     * Cosine similarity. Returns 0.0 for either zero-vector (per FR-028
     * conservative stance — undefined-similarity should never accidentally
     * cross threshold). Throws [EmbeddingDimensionMismatch] on size mismatch
     * so callers can never silently cluster across model rev boundaries
     * (FR-029, FR-030, FR-038).
     */
    fun cosine(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) {
            throw EmbeddingDimensionMismatch(a.size, b.size)
        }
        var dot = 0.0
        var magA = 0.0
        var magB = 0.0
        for (i in a.indices) {
            val ai = a[i].toDouble()
            val bi = b[i].toDouble()
            dot += ai * bi
            magA += ai * ai
            magB += bi * bi
        }
        if (magA == 0.0 || magB == 0.0) return 0.0f
        return (dot / (sqrt(magA) * sqrt(magB))).toFloat()
    }

    /**
     * Group [captures] into 4-hour windows aligned to capture-density
     * centers per FR-028 — explicitly NOT wall-clock midnight.
     *
     * Greedy "session" assignment: sort by [ClusterCandidate.createdAtMillis],
     * then for each as-yet-unbucketed capture, open a new window starting at
     * its timestamp and absorb every subsequent capture whose timestamp is
     * within [Thresholds.BUCKET_WIDTH_MS] of that anchor. The next un-
     * absorbed capture starts the next bucket. This guarantees that any
     * sub-sequence of captures spaced ≤ 4h apart with a span ≤ 4h lands
     * in one bucket — which is the intent of FR-028's "single 4-hour
     * window" wording.
     *
     * Bucket key is the anchor timestamp (epoch ms). Empty input → empty
     * map. Buckets with fewer than [Thresholds.MIN_SIZE] members are
     * still emitted (caller filters), so the worker can audit sub-
     * threshold near-misses for product tuning.
     *
     * DST is irrelevant: comparisons are in epoch ms, so spring-forward /
     * fall-back never shift bucket boundaries. That matches the spec's
     * "NOT wall-clock midnight" requirement.
     */
    fun bucket4h(captures: List<ClusterCandidate>): Map<Long, List<ClusterCandidate>> {
        if (captures.isEmpty()) return emptyMap()
        val sorted = captures.sortedBy { it.createdAtMillis }
        val buckets = LinkedHashMap<Long, MutableList<ClusterCandidate>>()
        var anchor: Long? = null
        var current: MutableList<ClusterCandidate>? = null
        for (capture in sorted) {
            val a = anchor
            if (a == null || capture.createdAtMillis - a > Thresholds.BUCKET_WIDTH_MS) {
                anchor = capture.createdAtMillis
                current = mutableListOf(capture)
                buckets[capture.createdAtMillis] = current
            } else {
                current!!.add(capture)
            }
        }
        return buckets
    }

    /**
     * Token-jaccard over the noun-set of [a] vs [b]. Result ∈ [0, 1].
     * Both inputs blank → 0.0 (vacuous match shouldn't satisfy
     * threshold). Either single side blank → 0.0.
     *
     * "Nouns" here is a heuristic — pure-Kotlin extraction without an
     * NLP model: lowercase + split on non-letters, drop stopwords + tokens
     * shorter than 4 chars. Good enough to discriminate "AI / model /
     * embedding" overlap from "recipe / pasta / oven" non-overlap, which
     * is all FR-028c is asked to do.
     */
    fun tokenJaccard(a: String, b: String): Float {
        val ta = extractNouns(a)
        val tb = extractNouns(b)
        if (ta.isEmpty() || tb.isEmpty()) return 0.0f
        val intersection = ta.intersect(tb).size
        val union = ta.union(tb).size
        if (union == 0) return 0.0f
        return intersection.toFloat() / union.toFloat()
    }

    /**
     * Composite cluster predicate per FR-028. Returns true iff:
     *  - [candidates].size ≥ 3,
     *  - every pair has [cosine] ≥ 0.7 over its embedding,
     *  - distinct-domain count ≥ 2,
     *  - every pair has [tokenJaccard] ≥ 0.3 over its text.
     *
     * Failing any single criterion rejects the cluster (false-positive
     * guard). [embeddings] is keyed by [ClusterCandidate.id]; missing
     * keys reject (defensive — no embedding implies the worker should
     * have skipped the candidate upstream).
     *
     * Pairwise (rather than centroid) checks intentionally — a chain
     * [A↔B≥0.7, B↔C≥0.7] doesn't force A↔C≥0.7 transitively, and the
     * spec authors want the strict version.
     */
    fun isCluster(
        candidates: List<ClusterCandidate>,
        embeddings: Map<String, FloatArray>
    ): Boolean {
        if (candidates.size < Thresholds.MIN_SIZE) return false
        if (candidates.distinctDomains() < Thresholds.MIN_DOMAINS) return false
        for (i in candidates.indices) {
            val ci = candidates[i]
            val ei = embeddings[ci.id] ?: return false
            for (j in (i + 1) until candidates.size) {
                val cj = candidates[j]
                val ej = embeddings[cj.id] ?: return false
                if (cosine(ei, ej) < Thresholds.COSINE) return false
                if (tokenJaccard(ci.text, cj.text) < Thresholds.JACCARD) return false
            }
        }
        return true
    }

    /**
     * Average pairwise cosine across [candidates]. The worker stamps this
     * on [com.capsule.app.data.entity.ClusterEntity.similarityScore] so
     * downstream UI / debug surfaces can show how tight a cluster is.
     * Caller must guarantee all ids appear in [embeddings].
     */
    fun averagePairwiseCosine(
        candidates: List<ClusterCandidate>,
        embeddings: Map<String, FloatArray>
    ): Float {
        if (candidates.size < 2) return 0.0f
        var sum = 0.0
        var count = 0
        for (i in candidates.indices) {
            val ei = embeddings[candidates[i].id] ?: return 0.0f
            for (j in (i + 1) until candidates.size) {
                val ej = embeddings[candidates[j].id] ?: return 0.0f
                sum += cosine(ei, ej).toDouble()
                count++
            }
        }
        return if (count == 0) 0.0f else (sum / count).toFloat()
    }

    // ---- internals --------------------------------------------------

    private fun List<ClusterCandidate>.distinctDomains(): Int =
        this.mapNotNull { it.domain?.takeIf { d -> d.isNotBlank() } }
            .map { it.lowercase() }
            .toSet()
            .size

    /**
     * Pure-Kotlin noun heuristic. Lowercase, split on any non-letter,
     * drop tokens < 4 chars, drop a small English stopword list. Not
     * a real PoS tagger — the cluster worker only needs *enough*
     * specificity to separate topical neighbours from random text.
     */
    internal fun extractNouns(text: String): Set<String> {
        if (text.isBlank()) return emptySet()
        return text.lowercase()
            .split(NON_LETTER)
            .filter { it.length >= 4 && it !in STOPWORDS }
            .toSet()
    }

    private val NON_LETTER = Regex("[^a-z]+", RegexOption.IGNORE_CASE)

    /**
     * Minimal stopword list — only the English words that show up in
     * almost every web article and would inflate jaccard above 0.3 on
     * pure noise. NOT a complete NLP stopword set; the 4-char filter
     * already drops `the/and/for/...`.
     */
    private val STOPWORDS = setOf(
        "this", "that", "with", "from", "have", "they", "their", "there",
        "here", "what", "when", "where", "which", "while", "would", "could",
        "should", "about", "into", "than", "then", "them", "your", "yours",
        "been", "being", "were", "will", "just", "more", "most", "some",
        "such", "also", "very", "much", "many", "like", "over", "only",
        "each", "even", "after", "before", "because", "between"
    )
}

/**
 * Throws when [SimilarityEngine.cosine] gets vectors of different
 * dimensionality — never silently zero, because that masks a model-rev
 * boundary bug (FR-029, FR-030, FR-038).
 */
class EmbeddingDimensionMismatch(
    val sizeA: Int,
    val sizeB: Int
) : IllegalArgumentException(
    "Embedding dimensionality mismatch: $sizeA vs $sizeB " +
        "(model rev drift? check ClusterEntity.modelLabel)"
)

/**
 * Minimal projection of [com.capsule.app.data.entity.IntentEnvelopeEntity]
 * the similarity engine consumes. The Block 4 worker maps Room rows to
 * this shape; tests construct it directly. Keeping the engine ignorant
 * of Room / IPC types lets it run in pure-JVM unit tests.
 *
 * - [id] — envelope UUID, also the key into the embedding map.
 * - [createdAtMillis] — epoch ms; used for [SimilarityEngine.bucket4h].
 * - [domain] — extracted hostname (e.g., `medium.com`); null when text
 *   capture rather than URL.
 * - [text] — hydrated content body (≥ 64 tokens per FR-026); used for
 *   [SimilarityEngine.tokenJaccard].
 */
data class ClusterCandidate(
    val id: String,
    val createdAtMillis: Long,
    val domain: String?,
    val text: String
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
    }
}
