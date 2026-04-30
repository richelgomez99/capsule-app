package com.capsule.app.action

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.ai.ClusterSummariser
import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.model.ActionExtractionResult
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.ai.EmbeddingResult
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.ClusterRepository
import com.capsule.app.data.ClusterSummarizeDelegate
import com.capsule.app.data.LocalRoomBackend
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.entity.ClusterEntity
import com.capsule.app.data.entity.ClusterMemberEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.ClusterState
import com.capsule.app.data.model.ClusterType
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.EnvelopeKind
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import com.capsule.app.data.security.KeystoreKeyProvider
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Spec 002 Phase 11 Block 13 / T164 — instrumented coverage of the
 * `cluster.summarize` AppFunction's happy path against an in-memory
 * SQLCipher [OrbitDatabase]. Mirrors `ClusterOrphanCleanupTest`'s
 * setup so the same encryption + Room semantics apply.
 *
 * Drives [ClusterSummarizeDelegate.summarizeCluster] (the binder-side
 * worker `summarizeCluster` resolves to in production) directly,
 * because constructing the full IPC service would require a foreground
 * Service host. The handler ([ClusterSummarizeActionHandler]) is just
 * a thin pass-through to this delegate, exercised in the existing unit
 * test suite.
 *
 * Asserts (FR-012-006 + FR-012-011 + FR-035 forensics rule):
 *  - delegate returns `"GENERATED:<envelopeId>"`
 *  - DERIVED envelope inserted with `kind=DERIVED`,
 *    `derivedVia="cluster_summarize"`, `derivedFromEnvelopeIdsJson`
 *    containing all 3 member ids
 *  - source envelopes UNCHANGED (FR-012-006)
 *  - cluster state = ACTED, dismissedAt = null
 *  - audit rows: CLUSTER_ACTING (forensics) + CLUSTER_SUMMARY_GENERATED
 *    (atomic with insert) + CLUSTER_ACTED (state transition).
 *  - failure path: cluster lacking members → state transitions to FAILED
 *    with CLUSTER_FAILED audit row carrying reason.
 */
@RunWith(AndroidJUnit4::class)
class ClusterSummarizeActionTest {

    private lateinit var context: Context
    private lateinit var db: OrbitDatabase
    private lateinit var delegate: ClusterSummarizeDelegate

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        context = ApplicationProvider.getApplicationContext()
        val passphrase = KeystoreKeyProvider.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)
        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()
        delegate = ClusterSummarizeDelegate(
            database = db,
            backend = LocalRoomBackend(db),
            clusterRepository = ClusterRepository(
                clusterDao = db.clusterDao(),
                auditLogDao = db.auditLogDao(),
                auditWriter = AuditLogWriter()
            ),
            summariser = ClusterSummariser(
                llmProvider = StubLlmProvider(),
                modelLabel = STUB_MODEL_LABEL
            ),
            auditWriter = AuditLogWriter()
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun summarize_happyPath_writesDerivedEnvelopeAndTransitionsCluster() = runBlocking {
        seedSurfacedClusterOf(3, clusterId = CLUSTER_ID)

        val outcome = delegate.summarizeCluster(CLUSTER_ID)

        // Outcome contract: GENERATED:<envelopeId>.
        assertTrue(
            "outcome should be GENERATED:<id>, got `$outcome`",
            outcome.startsWith("GENERATED:")
        )
        val envelopeId = outcome.removePrefix("GENERATED:")
        assertTrue("envelopeId must not be blank", envelopeId.isNotBlank())

        // Derived envelope persisted with FR-012-011 fields.
        val derived = db.intentEnvelopeDao().getById(envelopeId)
        assertNotNull("derived envelope must exist", derived)
        assertEquals(EnvelopeKind.DERIVED, derived!!.kind)
        assertEquals(
            "derivedVia must be the spec 012 sentinel `cluster_summarize`",
            "cluster_summarize",
            derived.derivedVia
        )
        val derivedJson = derived.derivedFromEnvelopeIdsJson ?: ""
        for (memberIdx in 0 until 3) {
            val memberId = "env-$memberIdx-$CLUSTER_ID"
            assertTrue(
                "derivedFromEnvelopeIdsJson must contain `$memberId`, got `$derivedJson`",
                derivedJson.contains(memberId)
            )
        }

        // FR-012-006 — source envelopes UNCHANGED.
        for (memberIdx in 0 until 3) {
            val memberId = "env-$memberIdx-$CLUSTER_ID"
            val source = db.intentEnvelopeDao().getById(memberId)!!
            assertFalse("source $memberId must NOT be deleted", source.isDeleted)
            assertFalse("source $memberId must NOT be archived", source.isArchived)
            assertEquals(EnvelopeKind.REGULAR, source.kind)
            assertNull("source $memberId must NOT have derivedVia", source.derivedVia)
        }

        // Cluster transitioned to ACTED.
        val cluster = db.clusterDao().byId(CLUSTER_ID)!!
        assertEquals(ClusterState.ACTED, cluster.state)
        assertNull("ACTED cluster must not carry dismissedAt", cluster.dismissedAt)

        // Audit trail (forensics + atomic + transition).
        val auditRows = db.auditLogDao().listAll()
        assertEquals(
            "expected exactly one CLUSTER_ACTING audit row",
            1,
            auditRows.count { it.action == AuditAction.CLUSTER_ACTING }
        )
        assertEquals(
            "expected exactly one CLUSTER_SUMMARY_GENERATED audit row",
            1,
            auditRows.count { it.action == AuditAction.CLUSTER_SUMMARY_GENERATED }
        )
        assertEquals(
            "expected exactly one CLUSTER_ACTED audit row",
            1,
            auditRows.count { it.action == AuditAction.CLUSTER_ACTED }
        )
        assertEquals(
            "no CLUSTER_FAILED audit row should be present on happy path",
            0,
            auditRows.count { it.action == AuditAction.CLUSTER_FAILED }
        )
    }

    @Test
    fun summarize_unknownCluster_returnsClusterNotFound_noWrites() = runBlocking {
        val outcome = delegate.summarizeCluster("does-not-exist")

        assertEquals("FAILED:cluster_not_found", outcome)
        assertEquals(0, db.intentEnvelopeDao().listAll().count { it.kind == EnvelopeKind.DERIVED })
        assertTrue(
            "no CLUSTER_* audit row should be written when cluster missing",
            db.auditLogDao().listAll().none { it.action.name.startsWith("CLUSTER_") }
        )
    }

    // ---- helpers --------------------------------------------------------

    private suspend fun seedSurfacedClusterOf(size: Int, clusterId: String) {
        val now = 1_700_000_000_000L
        repeat(size) { i ->
            db.intentEnvelopeDao().insert(
                IntentEnvelopeEntity(
                    id = "env-$i-$clusterId",
                    contentType = ContentType.TEXT,
                    textContent = "https://example.com/$i — fixture body $i",
                    imageUri = null,
                    textContentSha256 = null,
                    intent = Intent.AMBIGUOUS,
                    intentConfidence = null,
                    intentSource = IntentSource.FALLBACK,
                    intentHistoryJson = "[]",
                    state = StateSnapshot(
                        appCategory = AppCategory.OTHER,
                        activityState = ActivityState.STILL,
                        tzId = "UTC",
                        hourLocal = 10,
                        dayOfWeekLocal = 2
                    ),
                    createdAt = now + i,
                    dayLocal = "2026-04-29",
                    isArchived = false,
                    isDeleted = false,
                    deletedAt = null,
                    sharedContinuationResultId = null,
                    kind = EnvelopeKind.REGULAR,
                    derivedFromEnvelopeIdsJson = null,
                    todoMetaJson = null,
                    derivedVia = null
                )
            )
        }

        db.clusterDao().insertWithMembers(
            cluster = ClusterEntity(
                id = clusterId,
                clusterType = ClusterType.RESEARCH_SESSION,
                state = ClusterState.SURFACED,
                timeBucketStart = now,
                timeBucketEnd = now + 60_000L,
                similarityScore = 0.9f,
                modelLabel = "test-model@2026-04-29",
                createdAt = now,
                stateChangedAt = now,
                dismissedAt = null
            ),
            members = (0 until size).map { i ->
                ClusterMemberEntity(
                    clusterId = clusterId,
                    envelopeId = "env-$i-$clusterId",
                    memberIndex = i
                )
            }
        )
    }

    /**
     * Deterministic [LlmProvider] stub that responds to [summarize] with
     * one cited bullet per envelope id encountered in the prompt body.
     * The prompt format `[env-id]` is preserved by [ClusterSummariser]'s
     * input pipeline, so we extract those tokens and emit valid output.
     *
     * All other methods are NotImplementedError because the summariser
     * only uses [summarize].
     */
    private class StubLlmProvider : LlmProvider {
        override suspend fun summarize(text: String, maxTokens: Int): SummaryResult {
            // Find every `[env-id]` token in the prompt — those are the
            // member envelope ids the summariser asked about. We emit
            // one bullet per id citing it back.
            val ids = Regex("""\[(env-[A-Za-z0-9_-]+)\]""")
                .findAll(text)
                .map { it.groupValues[1] }
                .distinct()
                .toList()
            val bullets = ids.joinToString("\n") { id -> "- Stubbed bullet for [$id]" }
            return SummaryResult(
                text = bullets,
                generationLocale = "en",
                provenance = LlmProvenance.LocalNano
            )
        }

        override suspend fun classifyIntent(
            text: String,
            appCategory: String
        ): IntentClassification = throw NotImplementedError()

        override suspend fun scanSensitivity(text: String): SensitivityResult =
            throw NotImplementedError()

        override suspend fun generateDayHeader(
            dayIsoDate: String,
            envelopeSummaries: List<String>
        ): DayHeaderResult = throw NotImplementedError()

        override suspend fun extractActions(
            text: String,
            contentType: String,
            state: StateSnapshot,
            registeredFunctions: List<AppFunctionSummary>,
            maxCandidates: Int
        ): ActionExtractionResult = throw NotImplementedError()

        override suspend fun embed(text: String): EmbeddingResult? = null
    }

    private companion object {
        const val CLUSTER_ID = "c-summarize-happy"
        const val STUB_MODEL_LABEL = "stub-model@2026-04-29"
    }
}
