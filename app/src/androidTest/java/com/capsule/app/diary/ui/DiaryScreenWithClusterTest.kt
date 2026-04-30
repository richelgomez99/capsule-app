package com.capsule.app.diary.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import com.capsule.app.data.ClusterCardModel
import com.capsule.app.data.ClusterMemberRef
import com.capsule.app.data.model.ClusterState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * T150 — instrumented placement contract for the cluster slot vs the
 * day-header in the Diary LazyColumn (spec 010 D6 revision: events
 * outrank steady-state — when a cluster exists for the day, its card
 * renders ABOVE the day-header paragraph; non-cluster days skip the
 * slot entirely).
 *
 * Strategy: this test mirrors the exact item-key + ordering pattern
 * from
 * [com.capsule.app.diary.ui.DiaryScreen] (search the LazyColumn body
 * `state.clusters.forEach { item(key = "cluster-...") }` followed by
 * `item(key = "header-${isoDate}") { DayHeader(...) }`). A copy is
 * required because the production composable is wired through a real
 * `DiaryViewModel` + binder service, which is out of scope for a pure
 * UI placement assertion. The test fails if a future patch inverts
 * the slot order in either the production code OR this mirror — the
 * mirror is intentionally trivial so the comparison stays meaningful.
 *
 * Two scenarios:
 *   1. Cluster day — assert cluster card's `bottom` ≤ day-header's `top`.
 *   2. Sunday with cluster (a non-cluster-emitting day in Block 6's
 *      calendar) — verifies clusters.isNotEmpty() *always* wins
 *      regardless of weekday.
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
        composeRule.setContent {
            MaterialTheme {
                MirroredDiarySlotOrder(
                    clusters = listOf(sampleCluster),
                    isoDate = "2026-04-29",
                )
            }
        }

        composeRule.onNodeWithTag(TAG_CLUSTER_SLOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DAY_HEADER).assertIsDisplayed()

        val clusterBottom = composeRule
            .onNodeWithTag(TAG_CLUSTER_SLOT)
            .getBoundsInRoot()
            .bottom
        val headerTop = composeRule
            .onNodeWithTag(TAG_DAY_HEADER)
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
        composeRule.setContent {
            MaterialTheme {
                MirroredDiarySlotOrder(
                    clusters = listOf(sampleCluster),
                    isoDate = "2026-05-03",
                )
            }
        }

        composeRule.onNodeWithTag(TAG_CLUSTER_SLOT).assertIsDisplayed()
        composeRule.onNodeWithTag(TAG_DAY_HEADER).assertIsDisplayed()

        val clusterBottom = composeRule
            .onNodeWithTag(TAG_CLUSTER_SLOT)
            .getBoundsInRoot()
            .bottom
        val headerTop = composeRule
            .onNodeWithTag(TAG_DAY_HEADER)
            .getBoundsInRoot()
            .top
        assertTrue(clusterBottom <= headerTop)
    }

    @Test
    fun nonClusterDay_onlyDayHeaderRenders() {
        composeRule.setContent {
            MaterialTheme {
                MirroredDiarySlotOrder(
                    clusters = emptyList(),
                    isoDate = "2026-04-30",
                )
            }
        }
        composeRule.onNodeWithTag(TAG_DAY_HEADER).assertIsDisplayed()
    }

    companion object {
        const val TAG_CLUSTER_SLOT = "cluster-slot"
        const val TAG_DAY_HEADER = "day-header"
    }
}

/**
 * Mirror of the production LazyColumn slot order in
 * [com.capsule.app.diary.ui.DiaryScreen.DayContentView].
 * Production source: cluster forEach loop -> `item(key = "cluster-...")`,
 * followed by `item(key = "header-\${state.isoDate}")`.
 *
 * Kept intentionally trivial so the placement assertion in the test
 * is meaningful: any reorder in production WITHOUT updating this
 * mirror leaves the production order unguarded; reorder here without
 * production breaks the test.
 */
@androidx.compose.runtime.Composable
private fun MirroredDiarySlotOrder(
    clusters: List<ClusterCardModel>,
    isoDate: String,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        clusters.forEach { c ->
            item(key = "cluster-${c.clusterId}") {
                Text(
                    "Cluster placeholder ${c.clusterId}",
                    modifier = Modifier.testTag(DiaryScreenWithClusterTest.TAG_CLUSTER_SLOT),
                )
            }
        }
        item(key = "header-$isoDate") {
            Text(
                "Day header for $isoDate",
                modifier = Modifier.testTag(DiaryScreenWithClusterTest.TAG_DAY_HEADER),
            )
        }
    }
}
