package com.capsule.app.diary

import com.capsule.app.action.ipc.ActionExecuteRequestParcel
import com.capsule.app.action.ipc.ActionExecuteResultParcel
import com.capsule.app.data.ipc.ActionProposalParcel
import com.capsule.app.data.ipc.AuditEntryParcel
import com.capsule.app.data.ipc.DayPageParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EnvelopeDetailViewModelTest {

    private fun env(
        id: String = "env-1",
        intent: String = "AMBIGUOUS",
        historyJson: String = "[]"
    ) = EnvelopeViewParcel(
        id = id,
        contentType = "TEXT",
        textContent = "hello https://example.com",
        imageUri = null,
        intent = intent,
        intentSource = "AUTO_AMBIGUOUS",
        createdAtMillis = 1_745_164_800_000L,
        dayLocal = "2026-04-20",
        isArchived = false,
        title = null,
        domain = null,
        excerpt = null,
        summary = null,
        appCategory = "BROWSER",
        activityState = "UNKNOWN",
        hourLocal = 12,
        dayOfWeekLocal = 1,
        intentHistoryJson = historyJson,
        canonicalUrl = null
    )

    private class FakeRepo : DiaryRepository {
        var envelope: EnvelopeViewParcel? = null
        var reassigns = mutableListOf<Triple<String, String, String?>>()
        var archives = mutableListOf<String>()
        var deletes = mutableListOf<String>()
        var retries = mutableListOf<String>()
        var getCalls = 0

        override fun observeDay(isoDate: String): Flow<DayPageParcel> = emptyFlow()
        override suspend fun reassignIntent(envelopeId: String, newIntentName: String, reason: String?) {
            reassigns += Triple(envelopeId, newIntentName, reason)
        }
        override suspend fun archive(envelopeId: String) { archives += envelopeId }
        override suspend fun delete(envelopeId: String) { deletes += envelopeId }
        override suspend fun retryHydration(envelopeId: String) { retries += envelopeId }
        override suspend fun getEnvelope(envelopeId: String): EnvelopeViewParcel {
            getCalls++
            return envelope ?: error("no envelope stubbed")
        }
        override suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String> = emptyList()

        // 003 v1.1 — stub-only; not exercised by these envelope-detail tests.
        override fun observeProposals(envelopeId: String): Flow<List<ActionProposalParcel>> = emptyFlow()
        override suspend fun markProposalConfirmed(proposalId: String): Boolean = false
        override suspend fun markProposalDismissed(proposalId: String): Boolean = false
        override suspend fun executeAction(request: ActionExecuteRequestParcel): ActionExecuteResultParcel =
            error("executeAction not stubbed")
        override suspend fun cancelWithinUndoWindow(executionId: String): Boolean = false
        override suspend fun setTodoItemDone(envelopeId: String, itemIndex: Int, done: Boolean) {}
    }

    @Test
    fun `loads envelope on init and exposes Ready`() = runTest {
        val repo = FakeRepo().apply { envelope = env() }
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val vm = EnvelopeDetailViewModel("env-1", repo, scopeOverride = scope)

        advanceUntilIdle()
        val s = vm.state.value
        assertTrue("expected Ready, got $s", s is EnvelopeDetailUiState.Ready)
    }

    @Test
    fun `parses intent history oldest-first`() = runTest {
        val json = """[
          {"at":3000,"intent":"REFERENCE","source":"DIARY_REASSIGN"},
          {"at":1000,"intent":"AMBIGUOUS","source":"AUTO_AMBIGUOUS"},
          {"at":2000,"intent":"WANT_IT","source":"OVERLAY"}
        ]""".trimIndent()
        val repo = FakeRepo().apply { envelope = env(historyJson = json) }
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val vm = EnvelopeDetailViewModel("env-1", repo, scopeOverride = scope)

        advanceUntilIdle()
        val rows = (vm.state.value as EnvelopeDetailUiState.Ready).intentHistory
        assertEquals(listOf("AMBIGUOUS", "WANT_IT", "REFERENCE"), rows.map { it.intent })
    }

    @Test
    fun `onReassign delegates and refreshes`() = runTest {
        val repo = FakeRepo().apply { envelope = env() }
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val vm = EnvelopeDetailViewModel("env-1", repo, scopeOverride = scope)
        advanceUntilIdle()
        val before = repo.getCalls

        vm.onReassignIntent("WANT_IT")
        advanceUntilIdle()

        assertEquals(1, repo.reassigns.size)
        assertEquals("WANT_IT", repo.reassigns[0].second)
        assertEquals("DIARY_REASSIGN", repo.reassigns[0].third)
        assertTrue(repo.getCalls > before)
    }

    @Test
    fun `onArchive sets finished`() = runTest {
        val repo = FakeRepo().apply { envelope = env() }
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val vm = EnvelopeDetailViewModel("env-1", repo, scopeOverride = scope)
        advanceUntilIdle()

        assertFalse(vm.finished.value)
        vm.onArchive()
        advanceUntilIdle()

        assertEquals(listOf("env-1"), repo.archives)
        assertTrue(vm.finished.value)
    }

    @Test
    fun `onDelete sets finished`() = runTest {
        val repo = FakeRepo().apply { envelope = env() }
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val vm = EnvelopeDetailViewModel("env-1", repo, scopeOverride = scope)
        advanceUntilIdle()

        vm.onDelete()
        advanceUntilIdle()

        assertEquals(listOf("env-1"), repo.deletes)
        assertTrue(vm.finished.value)
    }

    @Test
    fun `refresh re-fetches envelope`() = runTest {
        val repo = FakeRepo().apply { envelope = env() }
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val vm = EnvelopeDetailViewModel("env-1", repo, scopeOverride = scope)
        advanceUntilIdle()
        val initial = repo.getCalls

        vm.refresh()
        advanceUntilIdle()

        assertTrue(repo.getCalls > initial)
    }

    @Test
    fun `audit provider feeds Ready state`() = runTest {
        val entries = listOf(
            AuditEntryParcel(
                id = "a1", atMillis = 1L, action = "ENVELOPE_CREATED",
                description = "seal", envelopeId = "env-1", extraJson = null
            )
        )
        val repo = FakeRepo().apply { envelope = env() }
        val scope = TestScope(UnconfinedTestDispatcher(testScheduler))
        val vm = EnvelopeDetailViewModel(
            envelopeId = "env-1",
            repository = repo,
            auditProvider = { entries },
            scopeOverride = scope
        )
        advanceUntilIdle()

        val ready = vm.state.value as EnvelopeDetailUiState.Ready
        assertEquals(1, ready.auditTrail.size)
        assertEquals("ENVELOPE_CREATED", ready.auditTrail[0].action)
    }
}
