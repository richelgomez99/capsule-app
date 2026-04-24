package com.capsule.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.capsule.app.audit.AuditLogRetentionWorker
import com.capsule.app.continuation.SoftDeleteRetentionWorker
import java.util.concurrent.TimeUnit

/**
 * Implements [Configuration.Provider] so that `WorkManager.getInstance(ctx)` can
 * lazily initialize WorkManager in **every** Orbit process (default, `:ml`,
 * `:capture`, `:net`, `:ui`) — not just the default one that the auto-installed
 * `WorkManagerInitializer` ContentProvider runs in.
 *
 * Without this, `EnvelopeRepositoryService.onCreate` in `:ml` crashes with
 * `WorkManager is not initialized properly` the first time it touches
 * `ContinuationEngine.create(context)` (which calls `WorkManager.getInstance`).
 */
class CapsuleApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        // T090a — schedule the soft-delete retention worker in the default
        // process only. Enqueuing once per boot here is safe: WorkManager
        // dedupes by unique-work-name, so subsequent launches KEEP the
        // existing schedule unchanged.
        if (isDefaultProcess()) {
            scheduleSoftDeleteRetention()
            scheduleAuditLogRetention()
        }
    }

    private fun scheduleSoftDeleteRetention() {
        val request = PeriodicWorkRequestBuilder<SoftDeleteRetentionWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            SoftDeleteRetentionWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun scheduleAuditLogRetention() {
        // T090 — audit-log retention runs daily, KEEP existing schedule.
        val request = PeriodicWorkRequestBuilder<AuditLogRetentionWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS
        ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            AuditLogRetentionWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    private fun isDefaultProcess(): Boolean {
        val pid = android.os.Process.myPid()
        val am = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return true
        val info = am.runningAppProcesses ?: return true
        val me = info.firstOrNull { it.pid == pid } ?: return true
        // Default process name is the app package (no ":subprocess" suffix).
        return me.processName == packageName
    }
}
