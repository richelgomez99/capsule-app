package com.capsule.app.continuation

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.EnvelopeStorageBackend
import com.capsule.app.data.LocalRoomBackend
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.model.AuditAction

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
    }
}
