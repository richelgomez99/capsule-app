package com.capsule.app.audit

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.dao.AuditLogDao

/**
 * T089 — daily retention worker that deletes audit_log rows older than
 * [RETENTION_DAYS] days, per `contracts/audit-log-contract.md §2`.
 *
 * Audit rows older than this are neither user-visible (the viewer only
 * surfaces today + the quick-range chips) nor required for provenance
 * (Principle XII — provenance is carried on the episode record, not its
 * audit trail). Retention caps audit-log disk usage without losing any
 * user-actionable signal.
 *
 * Runs silently. Scheduled from [com.capsule.app.CapsuleApplication.onCreate]
 * (T090) via `ExistingPeriodicWorkPolicy.KEEP`.
 *
 * Not to be confused with [com.capsule.app.continuation.SoftDeleteRetentionWorker]
 * (T089a), which purges soft-deleted envelopes and their transitive rows.
 * The two workers share a pattern but have disjoint scopes.
 */
class AuditLogRetentionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = OrbitDatabase.getInstance(applicationContext)
        val dao = db.auditLogDao()
        val cutoff = clock() - retentionWindowMillis()
        return runCatching {
            purge(dao, cutoff)
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val RETENTION_DAYS: Int = 90
        const val UNIQUE_WORK_NAME: String = "orbit.audit_log_retention"

        @Volatile
        var clockOverride: (() -> Long)? = null

        fun retentionWindowMillis(days: Int = RETENTION_DAYS): Long =
            days.toLong() * 24L * 60L * 60L * 1000L

        private fun clock(): Long = clockOverride?.invoke() ?: System.currentTimeMillis()

        /**
         * Extracted so unit tests can exercise the delete call without
         * WorkManager. Returns the number of rows deleted.
         */
        suspend fun purge(dao: AuditLogDao, cutoffMillis: Long): Int =
            dao.deleteOlderThan(cutoffMillis)
    }
}
