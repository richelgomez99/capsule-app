package com.capsule.app.diary

import com.capsule.app.data.ipc.DayPageParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T056 — verifies the paging invariants of [DiaryPagingSource].
 *
 * Scope:
 *   * today is always `days[0]` even when empty
 *   * older non-empty days arrive newest-first in batches
 *   * if the repo happens to return today (because it has content),
 *     the duplicate is skipped
 *   * exhaustion short-circuits subsequent `loadMore()` calls
 */
class DiaryPagingSourceTest {

    private class FakeRepo(val allDays: List<String>) : DiaryRepository {
        var calls = 0
        override fun observeDay(isoDate: String): Flow<DayPageParcel> = emptyFlow()
        override suspend fun reassignIntent(envelopeId: String, newIntentName: String, reason: String?) {}
        override suspend fun archive(envelopeId: String) {}
        override suspend fun delete(envelopeId: String) {}
        override suspend fun retryHydration(envelopeId: String) {}
        override suspend fun getEnvelope(envelopeId: String): EnvelopeViewParcel = error("unused")
        override suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String> {
            calls++
            return allDays.drop(offset).take(limit)
        }
    }

    @Test
    fun `today is always first even when empty`() = runTest {
        val repo = FakeRepo(allDays = emptyList())
        val src = DiaryPagingSource(repo, todayIsoProvider = { "2026-04-21" }, pageSize = 3)

        assertEquals(listOf("2026-04-21"), src.days.value)
        src.loadMore()
        assertEquals(listOf("2026-04-21"), src.days.value)
    }

    @Test
    fun `appends older days newest-first across batches`() = runTest {
        // 5 older days; paged in 2-day batches.
        val repo = FakeRepo(allDays = listOf(
            "2026-04-19", "2026-04-18", "2026-04-17", "2026-04-15", "2026-04-10"
        ))
        val src = DiaryPagingSource(repo, todayIsoProvider = { "2026-04-21" }, pageSize = 2)

        src.loadMore()
        assertEquals(listOf("2026-04-21", "2026-04-19", "2026-04-18"), src.days.value)
        src.loadMore()
        assertEquals(
            listOf("2026-04-21", "2026-04-19", "2026-04-18", "2026-04-17", "2026-04-15"),
            src.days.value
        )
        src.loadMore()
        assertEquals(
            listOf("2026-04-21", "2026-04-19", "2026-04-18", "2026-04-17", "2026-04-15", "2026-04-10"),
            src.days.value
        )
    }

    @Test
    fun `skips today when repo returns it as first item`() = runTest {
        val repo = FakeRepo(allDays = listOf("2026-04-21", "2026-04-19", "2026-04-17"))
        val src = DiaryPagingSource(repo, todayIsoProvider = { "2026-04-21" }, pageSize = 5)

        src.loadMore()
        assertEquals(listOf("2026-04-21", "2026-04-19", "2026-04-17"), src.days.value)
    }

    @Test
    fun `exhausts after short batch and short-circuits`() = runTest {
        val repo = FakeRepo(allDays = listOf("2026-04-19"))
        val src = DiaryPagingSource(repo, todayIsoProvider = { "2026-04-21" }, pageSize = 10)

        src.loadMore()
        val callsAfterFirst = repo.calls
        src.loadMore()
        src.loadMore()
        assertEquals(listOf("2026-04-21", "2026-04-19"), src.days.value)
        assertEquals("loadMore must short-circuit once exhausted", callsAfterFirst, repo.calls)
        assertTrue(repo.calls == 1)
    }
}
