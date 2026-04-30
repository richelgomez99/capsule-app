package com.capsule.app.continuation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.EnvelopeStorageBackend
import com.capsule.app.data.LocalRoomBackend
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.dao.AuditLogDao
import com.capsule.app.data.dao.ClusterDao
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.ClusterState
import org.json.JSONObject

/**
 * T089a — daily retention worker that hard-purges envelopes soft-deleted
 * more than [RETENTION_DAYS] days ago.
 *
 * For each purged envelope it calls [EnvelopeStorageBackend.hardDeleteTransaction],
 * which:
 *   1. Writes an `ENVELOPE_HARD_PURGED` audit row.
 *   2. Deletes the envelope (continuations + result rows cascade via FK).
 *
 * `extraJson = {"reason":"retention"}` distinguishes worker purges from
 * user-initiated purges (`"reason":"user_purge"`).
 *
 * Runs silently — no visible UI signal. Scheduled from
 * [com.capsule.app.CapsuleApplication.onCreate] (T090a).
 */
class SoftDeleteRetentionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = OrbitDatabase.getInstance(applicationContext)
        val backend: EnvelopeStorageBackend = LocalRoomBackend(db)
        val auditWriter = AuditLogWriter()
        val cutoff = clock() - retentionWindowMillis()
        return runCatching {
            purge(backend, auditWriter, cutoff)
            // T153 — orphan-cluster cascade. Runs after the envelope
            // hard-purge so the surviving-member count reflects the
            // post-purge state. Failures here do NOT poison the
            // envelope purge above (already committed); they just
            // schedule a retry of the whole worker.
            cascadeOrphanClusters(
                clusterDao = db.clusterDao(),
                auditLogDao = db.auditLogDao(),
                auditWriter = auditWriter
            )
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val RETENTION_DAYS: Int = 30
        const val UNIQUE_WORK_NAME: String = "orbit.soft_delete_retention"

        @Volatile
        var clockOverride: (() -> Long)? = null

        fun retentionWindowMillis(days: Int = RETENTION_DAYS): Long =
            days.toLong() * 24L * 60L * 60L * 1000L

        private fun clock(): Long = clockOverride?.invoke() ?: System.currentTimeMillis()

        /**
         * Extracted so unit tests can exercise the purge loop without
         * WorkManager. `runPurge` is the composable core.
         */
        suspend fun purge(
            backend: EnvelopeStorageBackend,
            auditWriter: AuditLogWriter,
            cutoffMillis: Long
        ): Int {
            val ids = backend.listIdsSoftDeletedBefore(cutoffMillis)
            ids.forEach { id ->
                val audit = auditWriter.build(
                    action = AuditAction.ENVELOPE_HARD_PURGED,
                    description = "Envelope hard-purged by retention worker",
                    envelopeId = id,
                    extraJson = """{"reason":"retention"}"""
                )
                backend.hardDeleteTransaction(id, audit)
            }
            return ids.size
        }

        /**
         * T153 — cluster orphan cascade. Spec 002 FR-037 'the card never
         * lies': when a cluster's surviving-member count drops below
         * 3 (because envelopes were soft-deleted or archived), the
         * cluster row must be transitioned to DISMISSED with reason
         * `members_below_minimum` so the diary slot stops surfacing it.
         *
         * Runs **after** the envelope hard-purge above so the count
         * reflects post-purge reality. Idempotent: terminal clusters
         * are already filtered by [ClusterDao.findOrphaned] (which
         * scopes to non-terminal states), so a second pass is a no-op.
         *
         * Returns the number of clusters dismissed in this pass.
         */
        suspend fun cascadeOrphanClusters(
            clusterDao: ClusterDao,
            auditLogDao: AuditLogDao,
            auditWriter: AuditLogWriter,
            now: Long = clock()
        ): Int {
            val orphanedIds = clusterDao.findOrphaned()
            orphanedIds.forEach { clusterId ->
                val current = clusterDao.byId(clusterId) ?: return@forEach
                clusterDao.updateState(
                    id = clusterId,
                    newState = ClusterState.DISMISSED.name,
                    stateChangedAt = now,
                    dismissedAt = now
                )
                auditLogDao.insert(
                    auditWriter.build(
                        action = AuditAction.CLUSTER_ORPHANED,
                        description = "Cluster auto-dismissed (members below minimum)",
                        envelopeId = null,
                        extraJson = JSONObject().apply {
                            put("clusterId", clusterId)
                            put("priorState", current.state.name)
                            put("reason", "members_below_minimum")
                        }.toString()
                    )
                )
            }
            return orphanedIds.size
        }
    }
}
