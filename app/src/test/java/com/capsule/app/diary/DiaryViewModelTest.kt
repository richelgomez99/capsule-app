package com.capsule.app.diary

import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.ipc.DayPageParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DiaryViewModelTest {

    private val baseTime = 1_745_164_800_000L

    private fun env(id: String, app: String = "BROWSER", offsetMin: Long = 0L): EnvelopeViewParcel =
        EnvelopeViewParcel(
            id = id,
            contentType = "TEXT",
            textContent = "content $id",
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
            appCategory = app,
            activityState = "UNKNOWN",
            hourLocal = 12,
            dayOfWeekLocal = 1
        )

    private class FakeRepo : DiaryRepository {
        val pages = MutableSharedFlow<DayPageParcel>(replay = 1)
        var throwOnObserve: Throwable? = null
        var reassignCalls = mutableListOf<Triple<String, String, String?>>()
        var archiveCalls = mutableListOf<String>()
        var deleteCalls = mutableListOf<String>()

        override fun observeDay(isoDate: String): Flow<DayPageParcel> = flow {
            throwOnObserve?.let { throw it }
            pages.collect { emit(it) }
        }

        override suspend fun reassignIntent(envelopeId: String, newIntentName: String, reason: String?) {
            reassignCalls += Triple(envelopeId, newIntentName, reason)
        }

        override suspend fun archive(envelopeId: String) {
            archiveCalls += envelopeId
        }

        override suspend fun delete(envelopeId: String) {
            deleteCalls += envelopeId
        }

        var retryCalls = mutableListOf<String>()
        override suspend fun retryHydration(envelopeId: String) {
            retryCalls += envelopeId
        }

        var stubEnvelope: EnvelopeViewParcel? = null
        override suspend fun getEnvelope(envelopeId: String): EnvelopeViewParcel =
            stubEnvelope ?: error("FakeRepo.getEnvelope not stubbed")

        var stubDistinctDays: List<String> = emptyList()
        override suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String> =
            stubDistinctDays.drop(offset).take(limit)
    }

    private class StubNano(private val response: String = "Nano output") : LlmProvider {
        override suspend fun classifyIntent(text: String, appCategory: String) = error("unused")
        override suspend fun summarize(text: String, maxTokens: Int): SummaryResult = error("unused")
        override suspend fun scanSensitivity(text: String): SensitivityResult = error("unused")
        override suspend fun generateDayHeader(
            dayIsoDate: String,
            envelopeSummaries: List<String>
        ): DayHeaderResult = DayHeaderResult(
            text = response,
            generationLocale = "en",
            provenance = LlmProvenance.LocalNano
        )
    }

    private fun newVm(
        repo: DiaryRepository,
        scope: CoroutineScope,
        nano: LlmProvider = StubNano()
    ): DiaryViewModel = DiaryViewModel(
        repository = repo,
        threadGrouper = ThreadGrouper(),
        dayHeaderGenerator = DayHeaderGenerator(nano, localeProvider = { "en-US" }),
        scopeOverride = scope
    )

    /**
     * Build a VM wired to an [UnconfinedTestDispatcher] rooted in the test's
     * [kotlinx.coroutines.test.TestScope.backgroundScope] so long-running
     * flow collectors don't trip [kotlinx.coroutines.test.UncompletedCoroutinesError]
     * when `runTest` finishes. Unconfined makes launches run eagerly, so
     * `advanceUntilIdle()` can drain any subsequent suspensions.
     */
    private fun TestScopeVm(
        runScope: kotlinx.coroutines.test.TestScope,
        repo: DiaryRepository,
        nano: LlmProvider = StubNano()
    ): DiaryViewModel {
        val dispatcher = UnconfinedTestDispatcher(runScope.testScheduler)
        val vmScope = CoroutineScope(runScope.backgroundScope.coroutineContext + dispatcher)
        return newVm(repo = repo, scope = vmScope, nano = nano)
    }

    @Test
    fun initialState_isLoadingWithBlankDate() = runTest {
        val vm = newVm(FakeRepo(), backgroundScope)
        assertTrue(vm.state.value is DayUiState.Loading)
    }

    @Test
    fun observe_emptyPage_producesEmptyState() = runTest {
        val repo = FakeRepo()
        val vm = TestScopeVm(this, repo)
        vm.observe("2026-04-20")
        repo.pages.emit(DayPageParcel("2026-04-20", emptyList()))
        advanceUntilIdle()
        assertEquals(DayUiState.Empty("2026-04-20"), vm.state.value)
    }

    @Test
    fun observe_nonEmptyPage_producesReadyWithThreadsAndHeader() = runTest {
        val repo = FakeRepo()
        val vm = TestScopeVm(this, repo)
        vm.observe("2026-04-20")
        val envs = (1..4).map { env("e$it", offsetMin = it.toLong()) }
        repo.pages.emit(DayPageParcel("2026-04-20", envs))
        advanceUntilIdle()

        val ready = vm.state.value as DayUiState.Ready
        assertEquals("2026-04-20", ready.isoDate)
        assertEquals("Nano output", ready.header)
        assertEquals("en", ready.generationLocale)
        assertEquals(1, ready.threads.size)
        assertEquals(4, ready.threads[0].envelopes.size)
    }

    @Test
    fun observe_repositoryFlowError_producesErrorState() = runTest {
        val repo = FakeRepo().apply { throwOnObserve = IllegalStateException("boom") }
        val vm = TestScopeVm(this, repo)
        vm.observe("2026-04-20")
        advanceUntilIdle()

        val err = vm.state.value as DayUiState.Error
        assertEquals("2026-04-20", err.isoDate)
        assertEquals("boom", err.message)
    }

    @Test
    fun observe_sameDateTwice_isIdempotent() = runTest {
        val repo = FakeRepo()
        val vm = TestScopeVm(this, repo)
        vm.observe("2026-04-20")
        vm.observe("2026-04-20")
        repo.pages.emit(DayPageParcel("2026-04-20", listOf(env("a"))))
        advanceUntilIdle()
        assertTrue(vm.state.value is DayUiState.Ready)
    }

    @Test
    fun observe_differentDate_resubscribes() = runTest {
        val repo = FakeRepo()
        val vm = TestScopeVm(this, repo)
        vm.observe("2026-04-20")
        repo.pages.emit(DayPageParcel("2026-04-20", listOf(env("a"))))
        advanceUntilIdle()
        assertEquals("2026-04-20", vm.state.value.isoDate)

        vm.observe("2026-04-19")
        advanceUntilIdle()
        // isoDate must have switched — whether to Loading or a new reduce
        // result depends on whether the SharedFlow replay cache leaks, but
        // the date must have advanced.
        assertEquals("2026-04-19", vm.state.value.isoDate)
    }

    @Test
    fun onReassignIntent_delegatesToRepository() = runTest {
        val repo = FakeRepo()
        val vm = TestScopeVm(this, repo)
        vm.onReassignIntent("abc", "WANT_IT", reason = "DIARY_REASSIGN")
        advanceUntilIdle()
        assertEquals(
            listOf(Triple("abc", "WANT_IT", "DIARY_REASSIGN")),
            repo.reassignCalls
        )
    }

    @Test
    fun onArchive_delegatesToRepository() = runTest {
        val repo = FakeRepo()
        val vm = TestScopeVm(this, repo)
        vm.onArchive("abc")
        advanceUntilIdle()
        assertEquals(listOf("abc"), repo.archiveCalls)
    }

    @Test
    fun onDelete_delegatesToRepository() = runTest {
        val repo = FakeRepo()
        val vm = TestScopeVm(this, repo)
        vm.onDelete("abc")
        advanceUntilIdle()
        assertEquals(listOf("abc"), repo.deleteCalls)
    }
}
