package com.capsule.app.data

import com.capsule.app.RuntimeFlags
import com.capsule.app.data.dao.ClusterCandidateRow
import com.capsule.app.data.dao.ClusterDao
import com.capsule.app.data.dao.ClusterMemberWithEnvelope
import com.capsule.app.data.dao.ClusterStateCount
import com.capsule.app.data.dao.ClusterWithMembers
import com.capsule.app.data.entity.ClusterEntity
import com.capsule.app.data.entity.ClusterMemberEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.model.ClusterState
import com.capsule.app.data.model.ClusterType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * T136 — JVM tests for [ClusterRepository] (spec 002 Phase 11 Block 5).
 *
 * The DAO-side filters (state ∈ {SURFACED,…}, surviving members ≥ 3
 * per FR-037) are exercised by the Room-backed `ClusterDao` integration
 * tests and by the cluster detection coverage; this class focuses on
 * the *read-side gates* the repository owns:
 *
 *  - [RuntimeFlags.clusterEmitEnabled] kill switch → empty list.
 *  - [RuntimeFlags.clusterModelLabelLock] mismatch → row excluded.
 *  - Mapping correctness (member ordering, field passthrough).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClusterRepositoryTest {

    private val originalLock = RuntimeFlags.clusterModelLabelLock
    private val originalEmit = RuntimeFlags.clusterEmitEnabled

    @Before fun setUp() {
        // Default: lock matches the rows the fake DAO emits.
        RuntimeFlags.clusterModelLabelLock = MODEL_LABEL_PINNED
        RuntimeFlags.clusterEmitEnabled = true
    }

    @After fun tearDown() {
        RuntimeFlags.clusterModelLabelLock = originalLock
        RuntimeFlags.clusterEmitEnabled = originalEmit
    }

    @Test
    fun `passes through DAO rows when label matches and kill switch is off`() = runTest {
        val rows = listOf(clusterWithMembers("c-1", MODEL_LABEL_PINNED))
        val repo = ClusterRepository(FakeClusterDao(MutableStateFlow(rows)))

        val emitted = repo.observeSurfaced().first()

        assertEquals(1, emitted.size)
        val card = emitted.single()
        assertEquals("c-1", card.clusterId)
        assertEquals(ClusterState.SURFACED, card.state)
        assertEquals(MODEL_LABEL_PINNED, card.modelLabel)
        assertEquals(listOf("env-a", "env-b", "env-c"), card.members.map { it.envelopeId })
        assertEquals(listOf(0, 1, 2), card.members.map { it.memberIndex })
    }

    @Test
    fun `kill switch suppresses all clusters`() = runTest {
        val rows = listOf(clusterWithMembers("c-1", MODEL_LABEL_PINNED))
        val repo = ClusterRepository(FakeClusterDao(MutableStateFlow(rows)))

        RuntimeFlags.clusterEmitEnabled = false
        val emitted = repo.observeSurfaced().first()

        assertTrue("kill switch must hide every cluster", emitted.isEmpty())
    }

    @Test
    fun `modelLabel mismatch excludes that cluster only`() = runTest {
        val matching = clusterWithMembers("c-match", MODEL_LABEL_PINNED)
        val drifted = clusterWithMembers("c-drift", "gemini-nano-99@2099-01-01")
        val repo = ClusterRepository(
            FakeClusterDao(MutableStateFlow(listOf(matching, drifted)))
        )

        val emitted = repo.observeSurfaced().first()

        assertEquals(listOf("c-match"), emitted.map { it.clusterId })
    }

    @Test
    fun `member ordering is stable by memberIndex regardless of join order`() = runTest {
        // Members emitted in shuffled order — repo must reorder by memberIndex.
        val cluster = clusterEntity("c-shuf", MODEL_LABEL_PINNED)
        val members = listOf(
            memberWithEnvelope(cluster.id, "env-z", index = 2),
            memberWithEnvelope(cluster.id, "env-x", index = 0),
            memberWithEnvelope(cluster.id, "env-y", index = 1)
        )
        val rows = listOf(ClusterWithMembers(cluster, members))
        val repo = ClusterRepository(FakeClusterDao(MutableStateFlow(rows)))

        val card = repo.observeSurfaced().first().single()

        assertEquals(listOf("env-x", "env-y", "env-z"), card.members.map { it.envelopeId })
        assertEquals(listOf(0, 1, 2), card.members.map { it.memberIndex })
    }

    // ---- fixtures ---------------------------------------------------

    private fun clusterEntity(id: String, modelLabel: String) = ClusterEntity(
        id = id,
        clusterType = ClusterType.RESEARCH_SESSION,
        state = ClusterState.SURFACED,
        timeBucketStart = 1_000L,
        timeBucketEnd = 1_000L + 4 * 60 * 60 * 1_000L,
        similarityScore = 0.85f,
        modelLabel = modelLabel,
        createdAt = 1_500L,
        stateChangedAt = 1_500L
    )

    private fun memberWithEnvelope(
        clusterId: String,
        envelopeId: String,
        index: Int
    ) = ClusterMemberWithEnvelope(
        member = ClusterMemberEntity(
            clusterId = clusterId,
            envelopeId = envelopeId,
            memberIndex = index
        ),
        envelope = null
    )

    private fun clusterWithMembers(
        id: String,
        modelLabel: String
    ): ClusterWithMembers = ClusterWithMembers(
        cluster = clusterEntity(id, modelLabel),
        members = listOf(
            memberWithEnvelope(id, "env-a", 0),
            memberWithEnvelope(id, "env-b", 1),
            memberWithEnvelope(id, "env-c", 2)
        )
    )

    private companion object {
        // Whatever lock is set in @Before — keep label literal so the
        // mismatch test can drift it deliberately.
        const val MODEL_LABEL_PINNED = "test-model@2026-04-29"
    }
}

/**
 * Minimal `ClusterDao` fake: only `observeSurfaced()` is exercised by
 * [ClusterRepository]; the other methods throw so any unintended call
 * fails the test loudly.
 */
private class FakeClusterDao(
    private val source: Flow<List<ClusterWithMembers>>
) : ClusterDao {
    override fun observeSurfaced(): Flow<List<ClusterWithMembers>> = source

    override suspend fun insertCluster(cluster: ClusterEntity) = error("not used")
    override suspend fun insertMembers(members: List<ClusterMemberEntity>) = error("not used")
    override suspend fun updateState(
        id: String,
        newState: String,
        stateChangedAt: Long,
        dismissedAt: Long?
    ) = error("not used")
    override suspend fun byId(id: String): ClusterEntity? = error("not used")
    override suspend fun byClusterIdWithMembers(id: String): ClusterWithMembers? =
        error("not used")
    override suspend fun findOrphaned(): List<String> = error("not used")
    override suspend fun listAll(): List<ClusterEntity> = error("not used")
    override suspend fun countByState(): List<ClusterStateCount> = error("not used")
    override suspend fun findClusterCandidates(
        sinceMillis: Long,
        minSummaryLength: Int
    ): List<ClusterCandidateRow> = error("not used")
}
