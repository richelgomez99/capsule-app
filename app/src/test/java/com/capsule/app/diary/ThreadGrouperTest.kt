package com.capsule.app.diary

import com.capsule.app.data.ipc.EnvelopeViewParcel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreadGrouperTest {

    private val baseTime = 1_745_164_800_000L // 2026-04-20T12:00:00Z

    private fun env(
        id: String,
        appCategory: String,
        offsetMin: Long,
        text: String = "capture-$id"
    ): EnvelopeViewParcel = EnvelopeViewParcel(
        id = id,
        contentType = "TEXT",
        textContent = text,
        imageUri = null,
        intent = "AMBIGUOUS",
        intentSource = "AUTO_AMBIGUOUS",
        createdAtMillis = baseTime + offsetMin * 60_000L,
        dayLocal = "2026-04-20",
        isArchived = false,
        title = null,
        domain = null,
        excerpt = null,
        summary = null,
        appCategory = appCategory,
        activityState = "UNKNOWN",
        hourLocal = 12,
        dayOfWeekLocal = 1
    )

    @Test
    fun emptyInput_returnsEmptyList() {
        val grouper = ThreadGrouper()
        assertEquals(emptyList<DiaryThread>(), grouper.group(emptyList()))
    }

    @Test
    fun singleEnvelope_producesSingletonThread() {
        val grouper = ThreadGrouper()
        val result = grouper.group(listOf(env("a", "MESSAGING", 0)))
        assertEquals(1, result.size)
        assertEquals("a", result[0].id)
        assertEquals(1, result[0].envelopes.size)
    }

    @Test
    fun sameCategoryWithin30Min_mergesIntoOneThread() {
        val grouper = ThreadGrouper()
        val input = listOf(
            env("a", "BROWSER", 0),
            env("b", "BROWSER", 15),
            env("c", "BROWSER", 25)
        )
        val result = grouper.group(input)
        assertEquals(1, result.size)
        assertEquals(listOf("a", "b", "c"), result[0].envelopes.map { it.id })
    }

    @Test
    fun sameCategoryOver30Min_breaksThread() {
        val grouper = ThreadGrouper()
        val input = listOf(
            env("a", "BROWSER", 0),
            env("b", "BROWSER", 31) // 31 minutes — outside window
        )
        val result = grouper.group(input)
        assertEquals(2, result.size)
    }

    @Test
    fun differentCategory_neverMerges_evenInSameMinute() {
        val grouper = ThreadGrouper()
        val input = listOf(
            env("a", "BROWSER", 0),
            env("b", "MESSAGING", 0),
            env("c", "BROWSER", 5)
        )
        val result = grouper.group(input)
        assertEquals(2, result.size)
        val browser = result.first { it.appCategory == "BROWSER" }
        assertEquals(listOf("a", "c"), browser.envelopes.map { it.id })
    }

    @Test
    fun proximityUsesLatestMember_notFirst() {
        // a at 0, b at 25, c at 45 — c is 45 min from a but only 20 min from b.
        // Using "latest member" proximity, all three should merge.
        val grouper = ThreadGrouper()
        val input = listOf(
            env("a", "BROWSER", 0),
            env("b", "BROWSER", 25),
            env("c", "BROWSER", 45)
        )
        val result = grouper.group(input)
        assertEquals(1, result.size)
        assertEquals(3, result[0].envelopes.size)
    }

    @Test
    fun similarityMerge_jumpsTimeWindow() {
        // a at 0, b at 120 (2h later). Same category.
        // With proximity alone: 2 threads. With similarity: 1 thread.
        val similarity: (EnvelopeViewParcel, EnvelopeViewParcel) -> Float = { x, y ->
            if (x.textContent == y.textContent) 1f else 0f
        }
        val grouper = ThreadGrouper(similarity = similarity)
        val input = listOf(
            env("a", "BROWSER", 0, text = "react hooks"),
            env("b", "BROWSER", 120, text = "react hooks")
        )
        val result = grouper.group(input)
        assertEquals(1, result.size)
    }

    @Test
    fun similarityBelowThreshold_doesNotMerge() {
        val similarity: (EnvelopeViewParcel, EnvelopeViewParcel) -> Float = { _, _ -> 0.70f }
        val grouper = ThreadGrouper(similarity = similarity)
        val input = listOf(
            env("a", "BROWSER", 0),
            env("b", "BROWSER", 120)
        )
        val result = grouper.group(input)
        assertEquals(2, result.size)
    }

    @Test
    fun degradedMode_noSimilarity_fallsBackToProximityOnly() {
        // Default constructor — similarity == 0f always.
        val grouper = ThreadGrouper()
        val input = listOf(
            env("a", "BROWSER", 0, text = "react"),
            env("b", "BROWSER", 120, text = "react")
        )
        val result = grouper.group(input)
        assertEquals(2, result.size)
    }

    @Test
    fun output_isDeterministic_regardlessOfInputOrder() {
        val grouper = ThreadGrouper()
        val shuffled = listOf(
            env("c", "BROWSER", 25),
            env("a", "BROWSER", 0),
            env("b", "BROWSER", 10)
        )
        val ordered = listOf(
            env("a", "BROWSER", 0),
            env("b", "BROWSER", 10),
            env("c", "BROWSER", 25)
        )
        val r1 = grouper.group(shuffled)
        val r2 = grouper.group(ordered)
        assertEquals(r2.map { it.id }, r1.map { it.id })
        assertEquals(
            r2.map { t -> t.envelopes.map { it.id } },
            r1.map { t -> t.envelopes.map { it.id } }
        )
    }

    @Test
    fun threadsSortedByStartTime() {
        val grouper = ThreadGrouper()
        val input = listOf(
            env("later", "BROWSER", 60),
            env("earlier", "MESSAGING", 0)
        )
        val result = grouper.group(input)
        assertEquals(2, result.size)
        assertTrue(result[0].startedAtMillis < result[1].startedAtMillis)
    }

    @Test
    fun exactProximityBoundary_isInclusive() {
        // Exactly 30 minutes — inclusive boundary per `<=`.
        val grouper = ThreadGrouper()
        val input = listOf(
            env("a", "BROWSER", 0),
            env("b", "BROWSER", 30)
        )
        val result = grouper.group(input)
        assertEquals(1, result.size)
    }
}
