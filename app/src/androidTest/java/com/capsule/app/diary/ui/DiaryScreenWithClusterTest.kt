package com.capsule.app.diary.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.capsule.app.action.ipc.ActionExecuteRequestParcel
import com.capsule.app.action.ipc.ActionExecuteResultParcel
import com.capsule.app.ai.EmbeddingResult
import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.model.ActionExtractionResult
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.ClusterCardModel
import com.capsule.app.data.ClusterMemberRef
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.ipc.ActionProposalParcel
import com.capsule.app.data.ipc.DayPageParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel
import com.capsule.app.data.model.ClusterState
import com.capsule.app.diary.DayHeaderGenerator
import com.capsule.app.diary.DayUiState
import com.capsule.app.diary.DiaryRepository
import com.capsule.app.diary.DiaryViewModel
import com.capsule.app.diary.ThreadGrouper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T150-revisit (Block 11) — instrumented placement contract for the
 * cluster slot vs the day-header in [DiaryScreen.DayContentView].
 *
 * Block 10 used a hand-mirrored copy of the LazyColumn body; that mirror
 * silently passed if production's slot order was inverted. This revision
 * drives the real `DayContentView` directly, fed by a fake
 * [DiaryRepository] / fake [LlmProvider] wrapped in a real
 * [DiaryViewModel]. Test-tags `DiaryScreenTestTags.CLUSTER_SLOT` and
 * `DiaryScreenTestTags.DAY_HEADER` are baked into the production
 * composable, so any future reorder breaks this test loud and clear.
 *
 * Three scenarios:
 *   1. Cluster day — cluster card's `bottom` ≤ day-header's `top`.
 *   2. Sunday-with-cluster (a non-cluster-emitting day in Block 6's
 *      calendar) — clusters.isNotEmpty() always wins, weekday-independent.
 *   3. Non-cluster day — only the day-header tag exists.
 */
class DiaryScreenWithClusterTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val sampleCluster = ClusterCardModel(
        clusterId = "cluster-A",
        state = ClusterState.SURFACED,
        timeBucketStart = 1_700_000_000_000L,
        timeBucketEnd = 1_700_000_000_000L + 60_000L,
        modelLabel = "test-model@2026-04-29",
        members = listOf(
            ClusterMemberRef("e-1", 0),
            ClusterMemberRef("e-2", 1),
            ClusterMemberRef("e-3", 2),
            ClusterMemberRef("e-4", 3),
        )
    )

    @Test
    fun clusterDay_clusterRendersAboveDayHeader() {
        renderDayContent(
            state = readyState(
                isoDate = "2026-04-29",
                clusters = listOf(sampleCluster),
            )
        )

        composeRule.onNodeWithTag(DiaryScreenTestTags.CLUSTER_SLOT).assertIsDisplayed()
        composeRule.onNodeWithTag(DiaryScreenTestTags.DAY_HEADER).assertIsDisplayed()

        val clusterBottom = composeRule
            .onNodeWithTag(DiaryScreenTestTags.CLUSTER_SLOT)
            .getBoundsInRoot()
            .bottom
        val headerTop = composeRule
            .onNodeWithTag(DiaryScreenTestTags.DAY_HEADER)
            .getBoundsInRoot()
            .top

        assertTrue(
            "cluster card must render above day header (clusterBottom=$clusterBottom, headerTop=$headerTop)",
            clusterBottom <= headerTop
        )
    }

    @Test
    fun sundayWithCluster_stillPlacesClusterAboveHeader() {
        // 2026-05-03 is a Sunday in America/New_York. The slot order
        // contract is weekday-independent: clusters.isNotEmpty() wins.
        renderDayContent(
            state = readyState(
                isoDate = "2026-05-03",
                clusters = listOf(sampleCluster),
            )
        )

        composeRule.onNodeWithTag(DiaryScreenTestTags.CLUSTER_SLOT).assertIsDisplayed()
        composeRule.onNodeWithTag(DiaryScreenTestTags.DAY_HEADER).assertIsDisplayed()

        val clusterBottom = composeRule
            .onNodeWithTag(DiaryScreenTestTags.CLUSTER_SLOT)
            .getBoundsInRoot()
            .bottom
        val headerTop = composeRule
            .onNodeWithTag(DiaryScreenTestTags.DAY_HEADER)
            .getBoundsInRoot()
            .top
        assertTrue(clusterBottom <= headerTop)
    }

    @Test
    fun nonClusterDay_onlyDayHeaderRenders() {
        renderDayContent(
            state = readyState(
                isoDate = "2026-04-30",
                clusters = emptyList(),
            )
        )
        composeRule.onNodeWithTag(DiaryScreenTestTags.DAY_HEADER).assertIsDisplayed()
    }

    // ------------------------------------------------------------------

    private fun renderDayContent(state: DayUiState.Ready) {
        val viewModel = DiaryViewModel(
            repository = FakeDiaryRepository,
            threadGrouper = ThreadGrouper(),
            dayHeaderGenerator = DayHeaderGenerator(NoopLlmProvider),
            scopeOverride = CoroutineScope(Dispatchers.Main),
        )
        composeRule.setContent {
            MaterialTheme {
                DayContentView(
                    state = state,
                    viewModel = viewModel,
                    onReassign = { _, _ -> },
                    onRetry = { },
                    onDelete = { },
                    onOpenDetail = { },
                    onProposalTap = { },
                )
            }
        }
    }

    private fun readyState(
        isoDate: String,
        clusters: List<ClusterCardModel>,
    ): DayUiState.Ready = DayUiState.Ready(
        isoDate = isoDate,
        header = "Quiet day in the workshop.",
        generationLocale = "en",
        threads = emptyList(),
        clusters = clusters,
    )

    /**
     * Minimal stand-in: returns nothing on every observe path, never
     * mutates anything. The production [DiaryViewModel] only invokes
     * the repo when [DiaryViewModel.observe] runs — which this test
     * never calls — so the fake's bodies are never exercised. Keeping
     * defaults explicit so any future signature change surfaces here.
     */
    private object FakeDiaryRepository : DiaryRepository {
        override fun observeDay(isoDate: String): Flow<DayPageParcel> = emptyFlow()

        override suspend fun reassignIntent(envelopeId: String, newIntentName: String, reason: String?) = Unit
        override suspend fun archive(envelopeId: String) = Unit
        override suspend fun delete(envelopeId: String) = Unit
        override suspend fun retryHydration(envelopeId: String) = Unit

        override suspend fun getEnvelope(envelopeId: String): EnvelopeViewParcel =
            error("FakeDiaryRepository.getEnvelope not stubbed")

        override suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String> = emptyList()

        override fun observeProposals(envelopeId: String): Flow<List<ActionProposalParcel>> = flowOf(emptyList())
        override suspend fun markProposalConfirmed(proposalId: String): Boolean = false
        override suspend fun markProposalDismissed(proposalId: String): Boolean = false
        override suspend fun executeAction(request: ActionExecuteRequestParcel): ActionExecuteResultParcel =
            error("FakeDiaryRepository.executeAction not stubbed")
        override suspend fun cancelWithinUndoWindow(executionId: String): Boolean = false
        override suspend fun setTodoItemDone(envelopeId: String, itemIndex: Int, done: Boolean) = Unit
    }

    /** Stub provider — DayHeaderGenerator is constructed but never invoked. */
    private object NoopLlmProvider : LlmProvider {
        override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification =
            error("NoopLlmProvider.classifyIntent not used")
        override suspend fun summarize(text: String, maxTokens: Int): SummaryResult =
            error("NoopLlmProvider.summarize not used")
        override suspend fun scanSensitivity(text: String): SensitivityResult =
            error("NoopLlmProvider.scanSensitivity not used")
        override suspend fun generateDayHeader(
            dayIsoDate: String,
            envelopeSummaries: List<String>
        ): DayHeaderResult = error("NoopLlmProvider.generateDayHeader not used")
        override suspend fun extractActions(
            text: String,
            contentType: String,
            state: StateSnapshot,
            registeredFunctions: List<AppFunctionSummary>,
            maxCandidates: Int
        ): ActionExtractionResult = error("NoopLlmProvider.extractActions not used")
        override suspend fun embed(text: String): EmbeddingResult? = null
    }
}
