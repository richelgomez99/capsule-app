package com.capsule.app.diary

import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.ClusterCardModel
import com.capsule.app.data.ClusterMemberRef
import com.capsule.app.data.ipc.DayPageParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel
import com.capsule.app.data.model.ClusterState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

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
        // T148 follow-up — Block 9 review FU#1: drive the cluster flow
        // from tests so we can verify combine + day-filter behaviour.
        val clusters = MutableSharedFlow<List<ClusterCardModel>>(replay = 1)
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

        // T053 Orbit Actions surface — unused by these tests; default stubs.
        override fun observeProposals(envelopeId: String) =
            kotlinx.coroutines.flow.flowOf(emptyList<com.capsule.app.data.ipc.ActionProposalParcel>())
        override suspend fun markProposalConfirmed(proposalId: String): Boolean = false
        override suspend fun markProposalDismissed(proposalId: String): Boolean = false
        override suspend fun executeAction(
            request: com.capsule.app.action.ipc.ActionExecuteRequestParcel
        ): com.capsule.app.action.ipc.ActionExecuteResultParcel =
            error("FakeRepo.executeAction not stubbed")
        override suspend fun cancelWithinUndoWindow(executionId: String): Boolean = false
        override suspend fun setTodoItemDone(envelopeId: String, itemIndex: Int, done: Boolean) {}

        // T148 follow-up — drives the cluster combine flow.
        override fun observeClusters(): Flow<List<ClusterCardModel>> = flow {
            clusters.collect { emit(it) }
        }
    }

    /** Test fake recording every Orbit Actions hop. */
    private class ActionRecordingRepo : DiaryRepository {
        val confirmCalls = mutableListOf<String>()
        val dismissCalls = mutableListOf<String>()
        val executeCalls = mutableListOf<com.capsule.app.action.ipc.ActionExecuteRequestParcel>()
        val cancelCalls = mutableListOf<String>()
        var executeResult: com.capsule.app.action.ipc.ActionExecuteResultParcel =
            com.capsule.app.action.ipc.ActionExecuteResultParcel(
                executionId = "exec-1",
                outcome = "DISPATCHED",
                outcomeReason = null,
                dispatchedAtMillis = 0L,
                latencyMs = 5L
            )

        override fun observeDay(isoDate: String) =
            kotlinx.coroutines.flow.flowOf(DayPageParcel(isoDate, emptyList()))
        override suspend fun reassignIntent(envelopeId: String, newIntentName: String, reason: String?) {}
        override suspend fun archive(envelopeId: String) {}
        override suspend fun delete(envelopeId: String) {}
        override suspend fun retryHydration(envelopeId: String) {}
        override suspend fun getEnvelope(envelopeId: String) = error("unused")
        override suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String> = emptyList()
        override fun observeProposals(envelopeId: String) =
            kotlinx.coroutines.flow.flowOf(emptyList<com.capsule.app.data.ipc.ActionProposalParcel>())
        override suspend fun markProposalConfirmed(proposalId: String): Boolean {
            confirmCalls += proposalId; return true
        }
        override suspend fun markProposalDismissed(proposalId: String): Boolean {
            dismissCalls += proposalId; return true
        }
        override suspend fun executeAction(
            request: com.capsule.app.action.ipc.ActionExecuteRequestParcel
        ): com.capsule.app.action.ipc.ActionExecuteResultParcel {
            executeCalls += request
            return executeResult
        }
        override suspend fun cancelWithinUndoWindow(executionId: String): Boolean {
            cancelCalls += executionId; return true
        }
        val todoToggleCalls = mutableListOf<Triple<String, Int, Boolean>>()
        override suspend fun setTodoItemDone(envelopeId: String, itemIndex: Int, done: Boolean) {
            todoToggleCalls += Triple(envelopeId, itemIndex, done)
        }
    }

    private fun proposal(id: String = "p1") = com.capsule.app.data.ipc.ActionProposalParcel(
        id = id,
        envelopeId = "env-1",
        functionId = "calendar.createEvent",
        schemaVersion = 1,
        argsJson = """{"title":"Coffee","startEpochMillis":1745164800000}""",
        previewTitle = "Coffee",
        previewSubtitle = null,
        confidence = 0.9f,
        provenance = "LocalNano",
        state = "PROPOSED",
        sensitivityScope = "PUBLIC",
        createdAtMillis = 0L,
        stateChangedAtMillis = 0L
    )

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
        // 003 v1.1 — stub-only; not exercised by these tests.
        override suspend fun extractActions(
            text: String,
            contentType: String,
            state: com.capsule.app.data.entity.StateSnapshot,
            registeredFunctions: List<com.capsule.app.ai.model.AppFunctionSummary>,
            maxCandidates: Int
        ): com.capsule.app.ai.model.ActionExtractionResult =
            com.capsule.app.ai.model.ActionExtractionResult(
                provenance = LlmProvenance.LocalNano,
                candidates = emptyList()
            )

        override suspend fun embed(text: String): com.capsule.app.ai.EmbeddingResult? = null
    }

    private fun newVm(
        repo: DiaryRepository,
        scope: CoroutineScope,
        nano: LlmProvider = StubNano(),
        zoneId: ZoneId = ZoneId.of("America/New_York")
    ): DiaryViewModel = DiaryViewModel(
        repository = repo,
        threadGrouper = ThreadGrouper(),
        dayHeaderGenerator = DayHeaderGenerator(nano, localeProvider = { "en-US" }),
        scopeOverride = scope,
        zoneId = zoneId
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
        nano: LlmProvider = StubNano(),
        zoneId: ZoneId = ZoneId.of("America/New_York")
    ): DiaryViewModel {
        val dispatcher = UnconfinedTestDispatcher(runScope.testScheduler)
        val vmScope = CoroutineScope(runScope.backgroundScope.coroutineContext + dispatcher)
        return newVm(repo = repo, scope = vmScope, nano = nano, zoneId = zoneId)
    }

    /** Synthetic cluster anchored at [zone] / [localDate] / [hourOfDay]. */
    private fun cluster(
        id: String,
        localDate: String,
        hourOfDay: Int = 12,
        zone: ZoneId = ZoneId.of("America/New_York"),
        memberCount: Int = 4
    ): ClusterCardModel {
        val start = java.time.LocalDate.parse(localDate)
            .atTime(hourOfDay, 0)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
        return ClusterCardModel(
            clusterId = id,
            state = ClusterState.SURFACED,
            timeBucketStart = start,
            timeBucketEnd = start + 30 * 60_000L,
            modelLabel = "nano-test",
            members = (1..memberCount).map {
                ClusterMemberRef(envelopeId = "$id-m$it", memberIndex = it - 1)
            }
        )
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

    // ---- Spec 003 v1.1 Orbit Actions hops (T053) -------------------------

    @Test
    fun onConfirmProposal_marksConfirmed_thenExecutes_thenOpensUndoToast() = runTest {
        val repo = ActionRecordingRepo()
        val vm = TestScopeVm(this, repo)
        vm.onConfirmProposal(proposal("p1"))
        advanceUntilIdle()
        assertEquals(listOf("p1"), repo.confirmCalls)
        assertEquals(1, repo.executeCalls.size)
        // Round-trip: VM passes proposal's argsJson through unchanged when
        // editedArgsJson is null (default).
        assertEquals(
            """{"title":"Coffee","startEpochMillis":1745164800000}""",
            repo.executeCalls.single().argsJson
        )
        // Per request contract: withUndo defaults to true so the executor
        // opens its 5 s window.
        assertTrue(repo.executeCalls.single().withUndo)
        assertEquals("exec-1", vm.undoState.value?.executionId)
    }

    @Test
    fun onConfirmProposal_propagatesEditedArgsJson() = runTest {
        val repo = ActionRecordingRepo()
        val vm = TestScopeVm(this, repo)
        val edited = """{"title":"Tea","startEpochMillis":1745164800000,"location":"Cafe"}"""
        vm.onConfirmProposal(proposal("p1"), editedArgsJson = edited)
        advanceUntilIdle()
        assertEquals(edited, repo.executeCalls.single().argsJson)
    }

    @Test
    fun onDismissProposal_callsRepository_doesNotExecute() = runTest {
        val repo = ActionRecordingRepo()
        val vm = TestScopeVm(this, repo)
        vm.onDismissProposal("p1")
        advanceUntilIdle()
        assertEquals(listOf("p1"), repo.dismissCalls)
        assertTrue(repo.executeCalls.isEmpty())
        // No toast surfaced for a dismiss — undo window only opens on execute.
        assertEquals(null, vm.undoState.value)
    }

    @Test
    fun onUndoExecution_cancelsAndClearsToast() = runTest {
        val repo = ActionRecordingRepo()
        val vm = TestScopeVm(this, repo)
        vm.onConfirmProposal(proposal("p1"))
        advanceUntilIdle()
        assertEquals("exec-1", vm.undoState.value?.executionId)

        vm.onUndoExecution("exec-1")
        advanceUntilIdle()
        assertEquals(listOf("exec-1"), repo.cancelCalls)
        assertEquals(null, vm.undoState.value)
    }

    @Test
    fun undoToast_autoClearsAfterFiveSeconds() = runTest {
        val repo = ActionRecordingRepo()
        val vm = TestScopeVm(this, repo)
        vm.onConfirmProposal(proposal("p1"))
        advanceUntilIdle()
        assertEquals("exec-1", vm.undoState.value?.executionId)
        // Drain the 5 s delay using the virtual scheduler.
        advanceTimeBy(5_001L)
        advanceUntilIdle()
        assertEquals(null, vm.undoState.value)
    }

    // ---- Spec 003 v1.1 US2 to-do toggle (T064) ---------------------------

    @Test
    fun onToggleTodoItem_delegatesToRepository() = runTest {
        val repo = ActionRecordingRepo()
        val vm = TestScopeVm(this, repo)
        vm.onToggleTodoItem("env-9", 2, done = true)
        advanceUntilIdle()
        assertEquals(listOf(Triple("env-9", 2, true)), repo.todoToggleCalls)
    }

    @Test
    fun onToggleTodoItem_swallowsRepositoryFailure() = runTest {
        val repo = object : DiaryRepository {
            override fun observeDay(isoDate: String) =
                kotlinx.coroutines.flow.flowOf(DayPageParcel(isoDate, emptyList()))
            override suspend fun reassignIntent(envelopeId: String, newIntentName: String, reason: String?) {}
            override suspend fun archive(envelopeId: String) {}
            override suspend fun delete(envelopeId: String) {}
            override suspend fun retryHydration(envelopeId: String) {}
            override suspend fun getEnvelope(envelopeId: String) = error("unused")
            override suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String> = emptyList()
            override fun observeProposals(envelopeId: String) =
                kotlinx.coroutines.flow.flowOf(emptyList<com.capsule.app.data.ipc.ActionProposalParcel>())
            override suspend fun markProposalConfirmed(proposalId: String) = false
            override suspend fun markProposalDismissed(proposalId: String) = false
            override suspend fun executeAction(
                request: com.capsule.app.action.ipc.ActionExecuteRequestParcel
            ) = error("unused")
            override suspend fun cancelWithinUndoWindow(executionId: String) = false
            override suspend fun setTodoItemDone(envelopeId: String, itemIndex: Int, done: Boolean) {
                throw android.os.RemoteException("simulated binder death")
            }
        }
        val vm = TestScopeVm(this, repo)
        // Must not crash — runCatching{} wrapper inside the VM swallows.
        vm.onToggleTodoItem("env-9", 0, done = true)
        advanceUntilIdle()
    }

    // ---- Spec 002 Phase 11 Block 9 follow-up #1 (T148 backfill) ----------
    // Cluster flow combine + ZoneId-aware day filter coverage.

    @Test
    fun observe_clusterOnIsoDate_appearsInReadyClusters() = runTest {
        val repo = FakeRepo()
        val zone = ZoneId.of("America/New_York")
        val vm = TestScopeVm(this, repo, zoneId = zone)
        vm.observe("2026-04-20")
        repo.pages.emit(DayPageParcel("2026-04-20", listOf(env("a"))))
        repo.clusters.emit(listOf(cluster("c1", localDate = "2026-04-20", hourOfDay = 14, zone = zone)))
        advanceUntilIdle()

        val ready = vm.state.value as DayUiState.Ready
        assertEquals(1, ready.clusters.size)
        assertEquals("c1", ready.clusters.single().clusterId)
    }

    @Test
    fun observe_clusterOnDifferentDay_isFilteredOut() = runTest {
        val repo = FakeRepo()
        val zone = ZoneId.of("America/New_York")
        val vm = TestScopeVm(this, repo, zoneId = zone)
        vm.observe("2026-04-20")
        repo.pages.emit(DayPageParcel("2026-04-20", listOf(env("a"))))
        repo.clusters.emit(
            listOf(
                cluster("c-prev", localDate = "2026-04-19", hourOfDay = 14, zone = zone),
                cluster("c-next", localDate = "2026-04-21", hourOfDay = 14, zone = zone)
            )
        )
        advanceUntilIdle()

        val ready = vm.state.value as DayUiState.Ready
        assertTrue(
            "Off-day clusters must not flow into Ready.clusters",
            ready.clusters.isEmpty()
        )
    }

    @Test
    fun observe_clusterOnly_emptyEnvelopes_routesToReadyNotEmpty() = runTest {
        val repo = FakeRepo()
        val zone = ZoneId.of("America/New_York")
        val vm = TestScopeVm(this, repo, zoneId = zone)
        vm.observe("2026-04-20")
        repo.pages.emit(DayPageParcel("2026-04-20", emptyList()))
        repo.clusters.emit(listOf(cluster("c1", localDate = "2026-04-20", hourOfDay = 9, zone = zone)))
        advanceUntilIdle()

        // Day with a cluster but no envelopes is Ready (so the slot still
        // renders), NOT Empty. Threads is empty; clusters is the single card.
        val ready = vm.state.value
        assertNotNull("State must be Ready when the day has only a cluster", ready as? DayUiState.Ready)
        val r = ready as DayUiState.Ready
        assertTrue("threads must be empty when no envelopes exist", r.threads.isEmpty())
        assertEquals(1, r.clusters.size)
    }

    @Test
    fun observe_clusterAt2330Local_staysOnSameDay_DSTBoundary() = runTest {
        val repo = FakeRepo()
        // US Eastern DST spring-forward day: 2026-03-08 02:00 → 03:00.
        // A cluster at 23:30 local on 2026-03-08 must still resolve to
        // 2026-03-08 (not 2026-03-09).
        val zone = ZoneId.of("America/New_York")
        val vm = TestScopeVm(this, repo, zoneId = zone)
        vm.observe("2026-03-08")
        repo.pages.emit(DayPageParcel("2026-03-08", listOf(env("a"))))
        repo.clusters.emit(
            listOf(cluster("c-late", localDate = "2026-03-08", hourOfDay = 23, zone = zone))
        )
        advanceUntilIdle()

        val ready = vm.state.value as DayUiState.Ready
        assertEquals(
            "Cluster anchored at 23:00 local must stay on the same iso date across DST",
            listOf("c-late"),
            ready.clusters.map { it.clusterId }
        )
    }
}
