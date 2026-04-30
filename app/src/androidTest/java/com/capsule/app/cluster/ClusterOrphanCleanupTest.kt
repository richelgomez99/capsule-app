package com.capsule.app.cluster

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.continuation.SoftDeleteRetentionWorker
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T155 — instrumented coverage of the cluster orphan-cleanup cascade
 * (T153). Drives the production [SoftDeleteRetentionWorker.cascadeOrphanClusters]
 * path against an in-memory SQLCipher [OrbitDatabase].
 *
 * Spec 002 FR-037 — 'the card never lies': when a cluster's surviving
 * (non-soft-deleted, non-archived) member count drops below 3, the
 * cluster row is auto-transitioned to DISMISSED with a CLUSTER_ORPHANED
 * audit row carrying `reason=members_below_minimum`.
 *
 * Two scenarios:
 *   1. 4-member cluster, soft-delete 1 → 3 survivors → SURFACED stays.
 *   2. Soft-delete a 2nd member → 2 survivors → DISMISSED + audit row.
 */
@RunWith(AndroidJUnit4::class)
class ClusterOrphanCleanupTest {

    private lateinit var context: Context
    private lateinit var db: OrbitDatabase

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
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun softDeleteOne_keepsClusterSurfaced() = runBlocking {
        seedClusterOf(4, clusterId = "c-soft-one")

        // Soft-delete 1 of 4 → 3 surviving members ≥ 3 → not orphaned.
        db.intentEnvelopeDao().softDelete("env-0-c-soft-one", deletedAt = 1L)

        val dismissed = SoftDeleteRetentionWorker.cascadeOrphanClusters(
            clusterDao = db.clusterDao(),
            auditLogDao = db.auditLogDao(),
            auditWriter = AuditLogWriter(),
            now = 100L
        )

        assertEquals("no clusters should be auto-dismissed", 0, dismissed)
        val cluster = db.clusterDao().byId("c-soft-one")!!
        assertEquals(ClusterState.SURFACED, cluster.state)
    }

    @Test
    fun softDeleteSecond_autoDismissesCluster_withOrphanedAudit() = runBlocking {
        seedClusterOf(4, clusterId = "c-soft-two")

        // Soft-delete 2 of 4 → 2 surviving members < 3 → orphaned.
        db.intentEnvelopeDao().softDelete("env-0-c-soft-two", deletedAt = 1L)
        db.intentEnvelopeDao().softDelete("env-1-c-soft-two", deletedAt = 2L)

        val dismissed = SoftDeleteRetentionWorker.cascadeOrphanClusters(
            clusterDao = db.clusterDao(),
            auditLogDao = db.auditLogDao(),
            auditWriter = AuditLogWriter(),
            now = 200L
        )

        assertEquals("one cluster should be auto-dismissed", 1, dismissed)
        val cluster = db.clusterDao().byId("c-soft-two")!!
        assertEquals(ClusterState.DISMISSED, cluster.state)
        assertEquals(200L, cluster.dismissedAt)

        // Audit row recorded with reason=members_below_minimum.
        val auditRows = db.auditLogDao().listAll()
            .filter { it.action == AuditAction.CLUSTER_ORPHANED }
        assertEquals(1, auditRows.size)
        val extra = auditRows.first().extraJson ?: ""
        assertTrue(
            "audit extraJson should reference clusterId and reason",
            extra.contains("c-soft-two") && extra.contains("members_below_minimum")
        )
    }

    @Test
    fun cascadeIsIdempotent_terminalClusterIsNotRevisited() = runBlocking {
        seedClusterOf(4, clusterId = "c-idem")
        db.intentEnvelopeDao().softDelete("env-0-c-idem", deletedAt = 1L)
        db.intentEnvelopeDao().softDelete("env-1-c-idem", deletedAt = 2L)

        // First pass dismisses the cluster.
        val first = SoftDeleteRetentionWorker.cascadeOrphanClusters(
            clusterDao = db.clusterDao(),
            auditLogDao = db.auditLogDao(),
            auditWriter = AuditLogWriter(),
            now = 300L
        )
        assertEquals(1, first)

        // Second pass should be a no-op — DISMISSED is filtered out by
        // ClusterDao.findOrphaned (state IN ('SURFACED','TAPPED',...)).
        val second = SoftDeleteRetentionWorker.cascadeOrphanClusters(
            clusterDao = db.clusterDao(),
            auditLogDao = db.auditLogDao(),
            auditWriter = AuditLogWriter(),
            now = 400L
        )
        assertEquals(0, second)

        // Only the first pass emitted an audit row.
        val auditRows = db.auditLogDao().listAll()
            .filter { it.action == AuditAction.CLUSTER_ORPHANED }
        assertEquals(1, auditRows.size)
    }

    // ---- helpers --------------------------------------------------------

    /** Seed a SURFACED cluster of [size] envelopes with ids env-0..env-(size-1). */
    private suspend fun seedClusterOf(size: Int, clusterId: String) {
        val now = 0L
        repeat(size) { i ->
            db.intentEnvelopeDao().insert(
                IntentEnvelopeEntity(
                    id = "env-$i-$clusterId",
                    contentType = ContentType.TEXT,
                    textContent = "https://example.com/$i",
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
                    createdAt = now,
                    dayLocal = "2026-04-29",
                    isArchived = false,
                    isDeleted = false,
                    deletedAt = null,
                    sharedContinuationResultId = null,
                    kind = EnvelopeKind.REGULAR,
                    derivedFromEnvelopeIdsJson = null,
                    todoMetaJson = null
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

        // Cross-check: orphan-test envelope ids are not the synthetic
        // 'env-0' the production query uses; for the test's softDelete
        // calls below we need the helper id translation.
    }
}
