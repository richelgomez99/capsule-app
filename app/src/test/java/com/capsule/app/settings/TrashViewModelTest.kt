package com.capsule.app.settings

import com.capsule.app.data.ipc.EnvelopeViewParcel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TrashViewModelTest {

    private fun envelope(id: String, deletedAt: Long? = null) = EnvelopeViewParcel(
        id = id,
        contentType = "TEXT",
        textContent = "preview-$id",
        imageUri = null,
        intent = "save_for_later",
        intentSource = "USER_SET",
        createdAtMillis = 1_000L,
        dayLocal = "2025-01-01",
        isArchived = false,
        title = null,
        domain = null,
        excerpt = null,
        summary = null,
        appCategory = "OTHER",
        activityState = "UNKNOWN",
        hourLocal = 10,
        dayOfWeekLocal = 1,
        intentHistoryJson = "[]",
        canonicalUrl = null,
        deletedAtMillis = deletedAt
    )

    private class FakeRepo(
        private val items: MutableList<EnvelopeViewParcel>
    ) : TrashRepository {
        val restoreCalls = mutableListOf<String>()
        val purgeCalls = mutableListOf<String>()
        var lastListDays: Int? = null

        override suspend fun listSoftDeleted(days: Int): List<EnvelopeViewParcel> {
            lastListDays = days
            return items.toList()
        }
        override suspend fun restore(envelopeId: String) {
            restoreCalls += envelopeId
            items.removeAll { it.id == envelopeId }
        }
        override suspend fun hardPurge(envelopeId: String) {
            purgeCalls += envelopeId
            items.removeAll { it.id == envelopeId }
        }
    }

    @Test
    fun `loads ready state on init`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = FakeRepo(mutableListOf(envelope("a"), envelope("b")))

        val vm = TrashViewModel(repo, retentionDays = 30, scopeOverride = scope)
        scope.advanceUntilIdle()

        val s = vm.state.value
        assertTrue("expected Ready, got $s", s is TrashUiState.Ready)
        s as TrashUiState.Ready
        assertEquals(2, s.envelopes.size)
        assertEquals(30, s.retentionDays)
        assertEquals(30, repo.lastListDays)
    }

    @Test
    fun `restore delegates and refreshes`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = FakeRepo(mutableListOf(envelope("a"), envelope("b")))
        val vm = TrashViewModel(repo, retentionDays = 30, scopeOverride = scope)
        scope.advanceUntilIdle()

        vm.onRestore("a")
        scope.advanceUntilIdle()

        assertEquals(listOf("a"), repo.restoreCalls)
        val s = vm.state.value as TrashUiState.Ready
        assertEquals(1, s.envelopes.size)
        assertEquals("b", s.envelopes.single().id)
    }

    @Test
    fun `purge delegates and refreshes`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = FakeRepo(mutableListOf(envelope("a")))
        val vm = TrashViewModel(repo, retentionDays = 30, scopeOverride = scope)
        scope.advanceUntilIdle()

        vm.onPurge("a")
        scope.advanceUntilIdle()

        assertEquals(listOf("a"), repo.purgeCalls)
        val s = vm.state.value as TrashUiState.Ready
        assertTrue(s.envelopes.isEmpty())
    }

    @Test
    fun `list failure surfaces as Error`() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val repo = object : TrashRepository {
            override suspend fun listSoftDeleted(days: Int): List<EnvelopeViewParcel> =
                throw IllegalStateException("boom")
            override suspend fun restore(envelopeId: String) = Unit
            override suspend fun hardPurge(envelopeId: String) = Unit
        }

        val vm = TrashViewModel(repo, retentionDays = 30, scopeOverride = scope)
        scope.advanceUntilIdle()

        val s = vm.state.value
        assertTrue("expected Error, got $s", s is TrashUiState.Error)
        assertEquals("boom", (s as TrashUiState.Error).message)
    }
}
