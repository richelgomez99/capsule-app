package com.capsule.app

import android.app.Application
import android.util.Log
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.capsule.app.audit.AuditLogRetentionWorker
import com.capsule.app.audit.DebugDumpReceiver
import com.capsule.app.continuation.SoftDeleteRetentionWorker
import com.capsule.app.data.AppFunctionRegistry
import com.capsule.app.data.OrbitDatabase
import kotlinx.coroutines.launch
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
            registerDebugDumpReceiverIfDebug()
        }
        // T025 — :ml process owns the AppFunction registry. Register the
        // hand-curated built-in schemas at boot. Idempotent: schemas already
        // present at the same version are no-ops.
        if (isMlProcess()) {
            registerBuiltInAppFunctions()
        }
    }

    /**
     * T025 — registers the v1.1 built-in skill set into the encrypted
     * `appfunction_skill` table. Runs in `:ml` only, off the main thread,
     * fire-and-forget: a registry failure is logged but does not block
     * the rest of the process from coming up.
     */
    private fun registerBuiltInAppFunctions() {
        val scope = kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
        )
        scope.launch {
            try {
                val db = OrbitDatabase.getInstance(this@CapsuleApplication)
                val registry = AppFunctionRegistry(
                    database = db,
                    skillDao = db.appFunctionSkillDao(),
                    usageDao = db.skillUsageDao(),
                    auditLogDao = db.auditLogDao(),
                    auditWriter = com.capsule.app.audit.AuditLogWriter()
                )
                registry.registerAll(com.capsule.app.action.BuiltInAppFunctionSchemas.ALL)
            } catch (t: Throwable) {
                Log.e("CapsuleApplication", "AppFunctionRegistry boot failed", t)
            }
        }
    }

    /**
     * T105 / T106 — register the dev-only debug-dump receiver dynamically.
     * Dynamic rather than manifest-declared so it cannot accidentally ship
     * in a release build: the whole `if (BuildConfig.DEBUG)` block is dead
     * code in release and R8 strips it.
     */
    private fun registerDebugDumpReceiverIfDebug() {
        if (!BuildConfig.DEBUG) return
        val filter = android.content.IntentFilter(DebugDumpReceiver.ACTION)
        // Flag required on API 33+ for non-exported, unprotected receivers.
        val flags = android.content.Context.RECEIVER_NOT_EXPORTED
        registerReceiver(DebugDumpReceiver(), filter, flags)
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

    private fun isMlProcess(): Boolean {
        val pid = android.os.Process.myPid()
        val am = getSystemService(ACTIVITY_SERVICE) as? android.app.ActivityManager ?: return false
        val info = am.runningAppProcesses ?: return false
        val me = info.firstOrNull { it.pid == pid } ?: return false
        return me.processName == "$packageName:ml"
    }
}
