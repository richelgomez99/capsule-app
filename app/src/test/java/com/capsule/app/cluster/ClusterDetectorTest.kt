package com.capsule.app.cluster

import com.capsule.app.ai.EmbeddingResult
import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.model.ActionExtractionResult
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.dao.AuditLogDao
import com.capsule.app.data.dao.ClusterCandidateRow
import com.capsule.app.data.dao.ClusterDao
import com.capsule.app.data.dao.ClusterStateCount
import com.capsule.app.data.dao.ClusterWithMembers
import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.entity.ClusterEntity
import com.capsule.app.data.entity.ClusterMemberEntity
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.ClusterState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T130 — JVM coverage of the [ClusterDetector] composable core.
 *
 * Validates the Phase 11 worker contract end-to-end without touching
 * Room or WorkManager:
 *  - modelLabel boundary gate (FR-030) emits a `skipped` audit row,
 *  - empty / sparse candidate sets short-circuit cleanly,
 *  - failing buckets (size <3, 1-domain, low cosine) never persist,
 *  - passing buckets persist atomically with the right modelLabel,
 *  - candidates whose embedding throws or returns null drop out,
 *  - terminal `CONTINUATION_COMPLETED` audit row is always written
 *    when a run completes (and only `model_label_mismatch` skip emits
 *    the alternate skip row).
 *
 * Hand-rolled fakes (no mockito/mockk in the project) implement the
 * full DAO surface; helpers stubbing the unused methods raise
 * [NotImplementedError] so a future refactor accidentally calling them
 * surfaces immediately.
 */
class ClusterDetectorTest {

    // ---- helpers -------------------------------------------------------

    private val MODEL_LABEL = "gemini-nano-4@2026-04-15"

    private fun unitVector(angleDeg: Float): FloatArray {
        val rad = angleDeg.toDouble() * Math.PI / 180.0
        return floatArrayOf(Math.cos(rad).toFloat(), Math.sin(rad).toFloat())
    }

    private fun candidate(
        id: String,
        atMillis: Long,
        domain: String?,
        summaryWords: List<String>
    ): ClusterCandidateRow = ClusterCandidateRow(
        envelopeId = id,
        createdAtMillis = atMillis,
        domain = domain,
        // Padded so realistic Jaccard input — 8+ "noun-like" tokens.
        summary = summaryWords.joinToString(" ") { "$it" }
    )

    /**
     * Three candidates with high-similarity embeddings, two distinct
     * domains, and a strong shared noun set. The sweet-spot positive.
     */
    private fun positiveTriple(t0: Long): List<ClusterCandidateRow> = listOf(
        candidate(
            id = "env-a",
            atMillis = t0,
            domain = "site-one.example",
            summaryWords = listOf(
                "transformer", "attention", "embedding", "encoder",
                "decoder", "vector", "neural", "language"
            )
        ),
        candidate(
            id = "env-b",
            atMillis = t0 + 30L * 60_000L, // +30 min
            domain = "site-two.example",
            summaryWords = listOf(
                "transformer", "attention", "embedding", "encoder",
                "decoder", "vector", "neural", "training"
            )
        ),
        candidate(
            id = "env-c",
            atMillis = t0 + 90L * 60_000L, // +90 min
            domain = "site-one.example",
            summaryWords = listOf(
                "transformer", "attention", "embedding", "encoder",
                "decoder", "vector", "neural", "inference"
            )
        )
    )

    private fun positiveEmbeddings(): Map<String, FloatArray> = mapOf(
        "env-a" to unitVector(0f),
        "env-b" to unitVector(2f),
        "env-c" to unitVector(4f)
    )

    // ---- modelLabel boundary gate (FR-030) -----------------------------

    @Test
    fun `model label mismatch emits skip audit row and returns Skipped`() = runBlocking {
        val dao = FakeClusterDao(positiveTriple(t0 = 1_000L))
        val audit = FakeAuditLogDao()
        val llm = FakeLlmProvider(positiveEmbeddings())

        val detector = ClusterDetector(
            clusterDao = dao,
            auditLogDao = audit,
            llm = llm,
            auditWriter = AuditLogWriter(clock = { 42L }, idGen = { "audit-id" }),
            clock = { 1_000_000L },
            idGen = { "cluster-id" },
            currentModelLabel = { "gemini-nano-4@2026-04-15" },
            modelLabelLock = { "gemini-nano-3@2025-12-01" }
        )

        val outcome = detector.detect()
        assertEquals(ClusterDetector.Outcome.Skipped, outcome)
        assertEquals(0, dao.persistedClusters.size)

        // Exactly one audit row, the skip notice with reason=model_label_mismatch.
        assertEquals(1, audit.entries.size)
        val row = audit.entries.single()
        assertEquals(AuditAction.CONTINUATION_COMPLETED, row.action)
        val extra = JSONObject(row.extraJson!!)
        assertEquals("CLUSTER_DETECT", extra.getString("type"))
        assertEquals("skipped", extra.getString("outcome"))
        assertEquals("model_label_mismatch", extra.getString("reason"))
        assertEquals("gemini-nano-4@2026-04-15", extra.getString("currentModelLabel"))
        assertEquals("gemini-nano-3@2025-12-01", extra.getString("clusterModelLabelLock"))
    }

    // ---- empty / sparse candidate sets ---------------------------------

    @Test
    fun `empty candidate list completes with all zero counters`() = runBlocking {
        val dao = FakeClusterDao(emptyList())
        val audit = FakeAuditLogDao()
        val detector = makeDetector(dao = dao, audit = audit, llm = FakeLlmProvider(emptyMap()))
        val outcome = detector.detect() as ClusterDetector.Outcome.Completed
        assertEquals(0, outcome.clustersFormed)
        assertEquals(0, outcome.candidatesScanned)
        assertEquals(0, outcome.embeddingsObtained)
        assertEquals(0, outcome.bucketsConsidered)
        assertEquals(0, dao.persistedClusters.size)
        assertEquals(1, audit.entries.size)
        val extra = JSONObject(audit.entries.single().extraJson!!)
        assertEquals("ok", extra.getString("outcome"))
    }

    @Test
    fun `single domain bucket fails MIN_DOMAINS and persists nothing`() = runBlocking {
        val t0 = 10_000L
        val rows = positiveTriple(t0).map { it.copy(domain = "only.example") }
        val dao = FakeClusterDao(rows)
        val audit = FakeAuditLogDao()
        val outcome = makeDetector(dao = dao, audit = audit, llm = FakeLlmProvider(positiveEmbeddings()))
            .detect() as ClusterDetector.Outcome.Completed
        assertEquals(0, outcome.clustersFormed)
        assertEquals(3, outcome.candidatesScanned)
        assertEquals(3, outcome.embeddingsObtained)
        assertEquals(1, outcome.bucketsConsidered)
        assertEquals(0, dao.persistedClusters.size)
        // Only the terminal completed audit row, no CLUSTER_FORMED entries.
        assertEquals(1, audit.entries.size)
        assertEquals(AuditAction.CONTINUATION_COMPLETED, audit.entries.single().action)
    }

    @Test
    fun `two candidates fail MIN_SIZE and persist nothing`() = runBlocking {
        val pos = positiveTriple(0L).take(2)
        val dao = FakeClusterDao(pos)
        val audit = FakeAuditLogDao()
        val outcome = makeDetector(
            dao = dao,
            audit = audit,
            llm = FakeLlmProvider(positiveEmbeddings())
        ).detect() as ClusterDetector.Outcome.Completed
        assertEquals(0, outcome.clustersFormed)
        assertEquals(0, dao.persistedClusters.size)
    }

    @Test
    fun `low cosine bucket fails predicate and persists nothing`() = runBlocking {
        val t0 = 100_000L
        val rows = positiveTriple(t0)
        val dao = FakeClusterDao(rows)
        val audit = FakeAuditLogDao()
        // Embeddings 90° apart → cosine ≈ 0 → fails 0.7 threshold.
        val embeddings = mapOf(
            "env-a" to unitVector(0f),
            "env-b" to unitVector(89f),
            "env-c" to unitVector(91f)
        )
        val outcome = makeDetector(
            dao = dao,
            audit = audit,
            llm = FakeLlmProvider(embeddings)
        ).detect() as ClusterDetector.Outcome.Completed
        assertEquals(0, outcome.clustersFormed)
        assertEquals(0, dao.persistedClusters.size)
    }

    // ---- happy path: persistence + audit -------------------------------

    @Test
    fun `passing bucket persists cluster with correct modelLabel and audit row`() = runBlocking {
        val t0 = 2_000_000L
        val dao = FakeClusterDao(positiveTriple(t0))
        val audit = FakeAuditLogDao()
        val now = 9_999_999L

        val detector = ClusterDetector(
            clusterDao = dao,
            auditLogDao = audit,
            llm = FakeLlmProvider(positiveEmbeddings()),
            auditWriter = AuditLogWriter(clock = { now }, idGen = { "audit-${audit.entries.size}" }),
            clock = { now },
            idGen = { "cluster-1" },
            currentModelLabel = { MODEL_LABEL },
            modelLabelLock = { MODEL_LABEL }
        )

        val outcome = detector.detect() as ClusterDetector.Outcome.Completed
        assertEquals(1, outcome.clustersFormed)
        assertEquals(3, outcome.candidatesScanned)
        assertEquals(3, outcome.embeddingsObtained)
        assertEquals(1, outcome.bucketsConsidered)

        // Cluster persisted with the right state + modelLabel.
        assertEquals(1, dao.persistedClusters.size)
        val (cluster, members) = dao.persistedClusters.single()
        assertEquals("cluster-1", cluster.id)
        assertEquals(ClusterState.SURFACED, cluster.state)
        assertEquals(MODEL_LABEL, cluster.modelLabel)
        assertEquals(t0, cluster.timeBucketStart)
        assertEquals(t0 + SimilarityEngine.Thresholds.BUCKET_WIDTH_MS, cluster.timeBucketEnd)
        assertEquals(now, cluster.createdAt)
        assertEquals(now, cluster.stateChangedAt)
        assertNull(cluster.dismissedAt)
        assertTrue("similarity ≥ 0.7", cluster.similarityScore >= 0.7f)
        assertEquals(3, members.size)
        assertEquals(setOf("env-a", "env-b", "env-c"), members.map { it.envelopeId }.toSet())
        // Member indices are 0..2.
        assertEquals(listOf(0, 1, 2), members.sortedBy { it.memberIndex }.map { it.memberIndex })

        // Audit: one CLUSTER_FORMED + one terminal CONTINUATION_COMPLETED.
        assertEquals(2, audit.entries.size)
        val formed = audit.entries.first { it.action == AuditAction.CLUSTER_FORMED }
        val formedExtra = JSONObject(formed.extraJson!!)
        assertEquals("cluster-1", formedExtra.getString("clusterId"))
        assertEquals(3, formedExtra.getInt("memberCount"))
        assertEquals(MODEL_LABEL, formedExtra.getString("modelLabel"))
        assertEquals(t0, formedExtra.getLong("timeBucketStart"))

        val completed = audit.entries.last()
        assertEquals(AuditAction.CONTINUATION_COMPLETED, completed.action)
        val extra = JSONObject(completed.extraJson!!)
        assertEquals("ok", extra.getString("outcome"))
        assertEquals("CLUSTER_DETECT", extra.getString("type"))
        assertEquals(1, extra.getInt("clustersFormed"))
        assertEquals(3, extra.getInt("candidatesScanned"))
    }

    // ---- embedding-failure resilience ---------------------------------

    @Test
    fun `null embedding drops candidate without throwing`() = runBlocking {
        val t0 = 0L
        val rows = positiveTriple(t0)
        val dao = FakeClusterDao(rows)
        val audit = FakeAuditLogDao()
        // Drop env-b → only 2 candidates left → bucket fails MIN_SIZE.
        val embeddings = mapOf(
            "env-a" to unitVector(0f),
            "env-c" to unitVector(4f)
        )
        val outcome = makeDetector(
            dao = dao,
            audit = audit,
            llm = FakeLlmProvider(embeddings)
        ).detect() as ClusterDetector.Outcome.Completed
        assertEquals(2, outcome.embeddingsObtained)
        assertEquals(3, outcome.candidatesScanned)
        assertEquals(0, outcome.clustersFormed)
        assertEquals(0, dao.persistedClusters.size)
    }

    @Test
    fun `embed throwing on one candidate is caught and run survives`() = runBlocking {
        val t0 = 0L
        val rows = positiveTriple(t0)
        val dao = FakeClusterDao(rows)
        val audit = FakeAuditLogDao()
        val llm = FakeLlmProvider(
            embeddings = positiveEmbeddings(),
            throwForIds = setOf("env-b")
        )
        val outcome = makeDetector(dao = dao, audit = audit, llm = llm)
            .detect() as ClusterDetector.Outcome.Completed
        // env-b throws → 2 successful embeds → no cluster, but no exception.
        assertEquals(2, outcome.embeddingsObtained)
        assertEquals(0, outcome.clustersFormed)
    }

    // ---- helper --------------------------------------------------------

    private fun makeDetector(
        dao: ClusterDao,
        audit: AuditLogDao,
        llm: LlmProvider
    ): ClusterDetector = ClusterDetector(
        clusterDao = dao,
        auditLogDao = audit,
        llm = llm,
        auditWriter = AuditLogWriter(clock = { 1L }, idGen = { "aud" }),
        clock = { 1_000_000L },
        idGen = { "cluster-x" },
        currentModelLabel = { MODEL_LABEL },
        modelLabelLock = { MODEL_LABEL }
    )
}

// =====================================================================
// Hand-rolled fakes (no mockito/mockk available in this project).
// =====================================================================

private class FakeClusterDao(
    private val candidates: List<ClusterCandidateRow>
) : ClusterDao {

    val persistedClusters: MutableList<Pair<ClusterEntity, List<ClusterMemberEntity>>> =
        mutableListOf()

    override suspend fun insertCluster(cluster: ClusterEntity) {
        persistedClusters += cluster to emptyList()
    }

    override suspend fun insertMembers(members: List<ClusterMemberEntity>) {
        // Replace the most-recent stub with members attached.
        val (last, _) = persistedClusters.removeAt(persistedClusters.size - 1)
        persistedClusters += last to members
    }

    override suspend fun findClusterCandidates(
        sinceMillis: Long,
        minSummaryLength: Int
    ): List<ClusterCandidateRow> = candidates

    // ---- unused surface in this test --------------------------------
    override suspend fun updateState(
        id: String,
        newState: String,
        stateChangedAt: Long,
        dismissedAt: Long?
    ) = error("not used")

    override suspend fun byId(id: String): ClusterEntity? = error("not used")

    override suspend fun byClusterIdWithMembers(id: String): ClusterWithMembers? =
        error("not used")

    override fun observeSurfaced(): Flow<List<ClusterWithMembers>> = flowOf(emptyList())

    override suspend fun findOrphaned(): List<String> = error("not used")

    override suspend fun listAll(): List<ClusterEntity> = error("not used")

    override suspend fun countByState(): List<ClusterStateCount> = error("not used")
}

private class FakeAuditLogDao : AuditLogDao {
    val entries: MutableList<AuditLogEntryEntity> = mutableListOf()

    override suspend fun insert(entry: AuditLogEntryEntity) {
        entries += entry
    }

    override suspend fun entriesForDay(startMillis: Long, endMillis: Long): List<AuditLogEntryEntity> =
        error("not used")

    override suspend fun entriesForEnvelope(envelopeId: String): List<AuditLogEntryEntity> =
        error("not used")

    override suspend fun countForDay(startMillis: Long, endMillis: Long, action: String): Int =
        error("not used")

    override suspend fun deleteOlderThan(cutoffMillis: Long): Int = error("not used")

    override suspend fun deleteByEnvelopeId(envelopeId: String) = error("not used")

    override suspend fun listAll(): List<AuditLogEntryEntity> = error("not used")
}

private class FakeLlmProvider(
    private val embeddings: Map<String, FloatArray>,
    private val throwForIds: Set<String> = emptySet()
) : LlmProvider {

    override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification =
        error("not used")

    override suspend fun summarize(text: String, maxTokens: Int): SummaryResult =
        error("not used")

    override suspend fun scanSensitivity(text: String): SensitivityResult =
        error("not used")

    override suspend fun generateDayHeader(
        dayIsoDate: String,
        envelopeSummaries: List<String>
    ): DayHeaderResult = error("not used")

    override suspend fun extractActions(
        text: String,
        contentType: String,
        state: StateSnapshot,
        registeredFunctions: List<AppFunctionSummary>,
        maxCandidates: Int
    ): ActionExtractionResult = error("not used")

    override suspend fun embed(text: String): EmbeddingResult? {
        // The first word of every positive-triple summary is the same
        // ("transformer"), so we route on the *last* word, which is
        // unique-per-id by construction in `ClusterDetectorTest`.
        val tail = text.trim().substringAfterLast(' ').lowercase()
        val id = WORD_TO_ID[tail] ?: return null
        if (id in throwForIds) throw RuntimeException("synthetic embed failure for $id")
        val v = embeddings[id] ?: return null
        return EmbeddingResult(vector = v, modelLabel = "test-model", dimensionality = v.size)
    }

    companion object {
        val WORD_TO_ID: Map<String, String> = mapOf(
            "language" to "env-a",
            "training" to "env-b",
            "inference" to "env-c"
        )
    }
}

/**
 * Note: when adding new positive-triple variants, ensure the trailing
 * word of each summary is registered in [FakeLlmProvider.WORD_TO_ID].
 */
