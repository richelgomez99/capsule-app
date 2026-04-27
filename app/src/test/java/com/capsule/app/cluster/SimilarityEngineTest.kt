package com.capsule.app.cluster

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * T128 — unit coverage of [SimilarityEngine] primitives + composite.
 *
 * Asserts the FR-028 thresholds (cosine ≥ 0.7, ≥2 domains, jaccard ≥ 0.3)
 * are wired correctly and that boundary edges (0.6999 / 0.7 / 0.7001)
 * fall on the expected side. DST behaviour is exercised because the
 * spec calls out "NOT wall-clock midnight" and we want a regression
 * gate for any future drift.
 */
class SimilarityEngineTest {

    // ------------ cosine -------------------------------------------------

    @Test
    fun `cosine of identical unit vectors is 1`() {
        val a = floatArrayOf(0.6f, 0.8f)
        assertEquals(1.0f, SimilarityEngine.cosine(a, a), 1e-6f)
    }

    @Test
    fun `cosine of orthogonal unit vectors is 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0.0f, SimilarityEngine.cosine(a, b), 1e-6f)
    }

    @Test
    fun `cosine of opposite vectors is -1`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(-1f, 0f)
        assertEquals(-1.0f, SimilarityEngine.cosine(a, b), 1e-6f)
    }

    @Test
    fun `cosine returns zero when either side is zero-vector`() {
        val a = floatArrayOf(0f, 0f, 0f)
        val b = floatArrayOf(1f, 2f, 3f)
        assertEquals(0.0f, SimilarityEngine.cosine(a, b), 1e-6f)
        assertEquals(0.0f, SimilarityEngine.cosine(b, a), 1e-6f)
        assertEquals(0.0f, SimilarityEngine.cosine(a, a), 1e-6f)
    }

    @Test
    fun `cosine throws on dimension mismatch`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val b = floatArrayOf(1f, 2f)
        val ex = assertThrows(EmbeddingDimensionMismatch::class.java) {
            SimilarityEngine.cosine(a, b)
        }
        assertEquals(3, ex.sizeA)
        assertEquals(2, ex.sizeB)
    }

    @Test
    fun `cosine known pair within tolerance`() {
        // Hand-computed: cos = (1*2 + 2*3) / (sqrt(5) * sqrt(13))
        //              = 8 / (2.2360679 * 3.6055512) = 0.99227787
        val a = floatArrayOf(1f, 2f)
        val b = floatArrayOf(2f, 3f)
        assertEquals(0.99227787f, SimilarityEngine.cosine(a, b), 1e-5f)
    }

    // ------------ jaccard ------------------------------------------------

    @Test
    fun `jaccard zero on empty input`() {
        assertEquals(0.0f, SimilarityEngine.tokenJaccard("", ""), 0.0f)
        assertEquals(0.0f, SimilarityEngine.tokenJaccard("hello world", ""), 0.0f)
        assertEquals(0.0f, SimilarityEngine.tokenJaccard("", "hello world"), 0.0f)
    }

    @Test
    fun `jaccard one for identical noun sets`() {
        val s = "Embedding models compute vector representations"
        assertEquals(1.0f, SimilarityEngine.tokenJaccard(s, s), 1e-6f)
    }

    @Test
    fun `jaccard discriminates topic overlap from non-overlap`() {
        val ai = "Embedding models compute vector representations of text"
        val ai2 = "Vector embeddings encode semantic similarity between sentences"
        val cooking = "Pasta recipe with garlic basil tomato sauce simmered slowly"
        val high = SimilarityEngine.tokenJaccard(ai, ai2)
        val low = SimilarityEngine.tokenJaccard(ai, cooking)
        assertTrue("AI-pair should overlap more than AI-vs-cooking; got high=$high low=$low",
            high > low)
    }

    @Test
    fun `jaccard above threshold for related noun overlap`() {
        // Three nouns shared (embedding/vector/representation), six total nouns
        // → expected ≥ 0.3.
        val a = "Embedding vector representation"
        val b = "Vector embedding representation analysis methodology"
        val j = SimilarityEngine.tokenJaccard(a, b)
        assertTrue("expected ≥ 0.3 got $j", j >= SimilarityEngine.Thresholds.JACCARD)
    }

    // ------------ bucket4h -----------------------------------------------

    private fun cand(id: String, millis: Long, domain: String? = "example.com", text: String = "hello world hello world") =
        ClusterCandidate(id = id, createdAtMillis = millis, domain = domain, text = text)

    @Test
    fun `bucket4h empty returns empty map`() {
        assertTrue(SimilarityEngine.bucket4h(emptyList()).isEmpty())
    }

    @Test
    fun `bucket4h groups within four hour window into one bucket`() {
        val anchor = 1_745_164_800_000L // 2026-04-20 12:00 UTC, arbitrary
        val captures = listOf(
            cand("a", anchor),
            cand("b", anchor + 60 * 60 * 1000L),         // +1h
            cand("c", anchor + 3 * 60 * 60 * 1000L)      // +3h
        )
        val buckets = SimilarityEngine.bucket4h(captures)
        assertEquals(1, buckets.size)
        assertEquals(3, buckets.values.first().size)
    }

    @Test
    fun `bucket4h splits captures separated by more than four hours`() {
        val anchor = 1_745_164_800_000L
        val captures = listOf(
            cand("a", anchor),
            cand("b", anchor + 5 * 60 * 60 * 1000L)      // +5h, definitely different bucket
        )
        val buckets = SimilarityEngine.bucket4h(captures)
        assertEquals(2, buckets.size)
    }

    @Test
    fun `bucket4h is unaffected by DST boundary because it works in epoch ms`() {
        // 2026-03-08 02:00 EST → spring forward to 03:00 EDT (US DST start).
        // We bucket two captures straddling the 02:00 wall-clock skip:
        // 01:30 EST == 06:30 UTC and 03:30 EDT == 07:30 UTC — exactly 1h
        // apart in epoch ms. They MUST share a bucket (the spec rejects
        // wall-clock midnight alignment, so DST shifts must not change
        // bucketing either).
        val ny = ZoneId.of("America/New_York")
        val before = LocalDateTime.of(2026, 3, 8, 1, 30).atZone(ny).toInstant().toEpochMilli()
        val after = LocalDateTime.of(2026, 3, 8, 3, 30).atZone(ny).toInstant().toEpochMilli()
        // Sanity: actual elapsed is ~1h despite the wall-clock 2h jump.
        assertEquals(60 * 60 * 1000L, after - before)
        val captures = listOf(cand("pre", before), cand("post", after))
        val buckets = SimilarityEngine.bucket4h(captures)
        assertEquals(1, buckets.size)
    }

    // ------------ isCluster (composite) ----------------------------------

    private fun emb(vararg vs: Float) = vs

    @Test
    fun `isCluster rejects when fewer than three candidates`() {
        val cs = listOf(cand("a", 0, "x.com", "alpha"), cand("b", 0, "y.com", "alpha"))
        val es = mapOf("a" to emb(1f, 0f), "b" to emb(1f, 0f))
        assertFalse(SimilarityEngine.isCluster(cs, es))
    }

    @Test
    fun `isCluster rejects single-domain set even when cosine and jaccard pass`() {
        val cs = listOf(
            cand("a", 0, "medium.com", "embedding vector representation"),
            cand("b", 0, "medium.com", "vector embedding representation"),
            cand("c", 0, "medium.com", "representation embedding vector")
        )
        val v = emb(1f, 0f)
        val es = mapOf("a" to v, "b" to v, "c" to v)
        assertFalse(SimilarityEngine.isCluster(cs, es))
    }

    @Test
    fun `isCluster rejects when any pair cosine below threshold`() {
        // Use 3D so we can put one vector almost-orthogonal to the others.
        val cs = listOf(
            cand("a", 0, "x.com", "embedding vector representation"),
            cand("b", 0, "y.com", "embedding vector representation"),
            cand("c", 0, "z.com", "embedding vector representation")
        )
        val es = mapOf(
            "a" to emb(1f, 0f, 0f),
            "b" to emb(1f, 0f, 0f),
            "c" to emb(0f, 1f, 0f)        // orthogonal → cosine 0 with a/b
        )
        assertFalse(SimilarityEngine.isCluster(cs, es))
    }

    @Test
    fun `isCluster rejects when any pair jaccard below threshold`() {
        val cs = listOf(
            cand("a", 0, "x.com", "embedding vector representation analysis"),
            cand("b", 0, "y.com", "embedding vector representation methodology"),
            cand("c", 0, "z.com", "pasta tomato basil garlic recipe")
        )
        val v = emb(1f, 0f)
        val es = mapOf("a" to v, "b" to v, "c" to v)
        assertFalse(SimilarityEngine.isCluster(cs, es))
    }

    @Test
    fun `isCluster rejects when an embedding is missing for a member`() {
        val cs = listOf(
            cand("a", 0, "x.com", "embedding vector representation"),
            cand("b", 0, "y.com", "embedding vector representation"),
            cand("c", 0, "z.com", "embedding vector representation")
        )
        val es = mapOf("a" to emb(1f, 0f), "b" to emb(1f, 0f))
        assertFalse(SimilarityEngine.isCluster(cs, es))
    }

    @Test
    fun `isCluster accepts when all three criteria pass`() {
        val cs = listOf(
            cand("a", 0, "medium.com", "embedding vector representation"),
            cand("b", 0, "openai.com", "vector embedding representation"),
            cand("c", 0, "anthropic.com", "representation embedding vector")
        )
        val v = emb(1f, 0f)
        val es = mapOf("a" to v, "b" to v, "c" to v)
        assertTrue(SimilarityEngine.isCluster(cs, es))
    }

    // ------------ threshold edge: 0.6999 / 0.7 / 0.7001 -----------------

    /**
     * Build two unit vectors whose cosine is exactly [target]. We pick
     * `a = (1, 0)` and `b = (target, sqrt(1-target²))`; cos(a, b) =
     * `target` because both are unit-length.
     */
    private fun pairWithCosine(target: Float): Pair<FloatArray, FloatArray> {
        val a = floatArrayOf(1f, 0f)
        val perp = kotlin.math.sqrt((1.0 - target.toDouble() * target.toDouble())).toFloat()
        val b = floatArrayOf(target, perp)
        return a to b
    }

    @Test
    fun `cosine threshold edge — 0_6999 below, 0_7000 inclusive, 0_7001 above`() {
        val (a1, b1) = pairWithCosine(0.6999f)
        val (a2, b2) = pairWithCosine(0.7000f)
        val (a3, b3) = pairWithCosine(0.7001f)
        // Sanity: each pair's actual cosine matches the target within float epsilon.
        assertEquals(0.6999f, SimilarityEngine.cosine(a1, b1), 1e-4f)
        assertEquals(0.7000f, SimilarityEngine.cosine(a2, b2), 1e-4f)
        assertEquals(0.7001f, SimilarityEngine.cosine(a3, b3), 1e-4f)
        // Threshold gate (FR-028a is ≥ 0.7, so 0.7 itself is inside).
        assertTrue(SimilarityEngine.cosine(a2, b2) >= SimilarityEngine.Thresholds.COSINE)
        assertTrue(SimilarityEngine.cosine(a3, b3) >= SimilarityEngine.Thresholds.COSINE)
        assertFalse(SimilarityEngine.cosine(a1, b1) >= SimilarityEngine.Thresholds.COSINE)
    }

    // ------------ averagePairwiseCosine ---------------------------------

    @Test
    fun `averagePairwiseCosine returns 0 for fewer than 2 candidates`() {
        val cs = listOf(cand("a", 0, "x.com", "alpha"))
        assertEquals(0.0f, SimilarityEngine.averagePairwiseCosine(cs, mapOf("a" to emb(1f, 0f))), 0.0f)
    }

    @Test
    fun `averagePairwiseCosine returns 1 when all vectors identical`() {
        val cs = listOf(
            cand("a", 0, "x.com", "alpha"),
            cand("b", 0, "y.com", "alpha"),
            cand("c", 0, "z.com", "alpha")
        )
        val v = emb(1f, 0f)
        val es = mapOf("a" to v, "b" to v, "c" to v)
        assertEquals(1.0f, SimilarityEngine.averagePairwiseCosine(cs, es), 1e-6f)
    }

    @Test
    fun `EmbeddingDimensionMismatch is not equal to ordinary IllegalArgument`() {
        // Sanity that the type narrows for catch-blocks.
        val ex = EmbeddingDimensionMismatch(3, 2)
        assertTrue(ex is IllegalArgumentException)
        assertNotEquals(IllegalArgumentException("x").message, ex.message)
    }
}
