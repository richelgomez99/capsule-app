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
            scheduleWeeklyDigest()
            scheduleClusterDetection()
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

    /**
     * T073 — schedule the Sunday weekly-digest worker. Per
     * Principle IV the worker requires charging + UNMETERED + battery
     * not low so the LLM call doesn't burn user data or battery; per
     * the contract it fires once per week with a 4h flex window so
     * Doze can pick a low-cost moment. Initial delay anchors the next
     * occurrence to 06:00 local on the upcoming Sunday so the digest
     * shows up before the user opens the diary on Sunday morning.
     */
    private fun scheduleWeeklyDigest() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val initialDelay = computeInitialDelayToSunday06(
            now = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault()),
            targetLocalTime = java.time.LocalTime.of(6, 0)
        )
        val request = PeriodicWorkRequestBuilder<com.capsule.app.continuation.WeeklyDigestWorker>(
            repeatInterval = 7,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
            flexTimeInterval = 4,
            flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.capsule.app.continuation.WeeklyDigestWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    internal fun computeInitialDelayToSunday06(
        now: java.time.ZonedDateTime,
        targetLocalTime: java.time.LocalTime
    ): java.time.Duration {
        val candidate = now
            .with(java.time.temporal.TemporalAdjusters.nextOrSame(java.time.DayOfWeek.SUNDAY))
            .with(targetLocalTime)
        val anchor = if (!candidate.isAfter(now)) {
            candidate.plusWeeks(1)
        } else {
            candidate
        }
        return java.time.Duration.between(now, anchor)
    }

    /**
     * T132 (spec 002 amendment Phase 11) — schedule the daily
     * cluster-detection scan. Anchored to 03:00 local time so the
     * window is deep into off-hours and well away from the 07:00
     * capture-seal flush. Per Principle IV the worker requires
     * charging + UNMETERED + battery-not-low: cluster detection runs
     * Nano embedding inference over potentially dozens of envelopes,
     * which is too expensive to wake the radio for.
     *
     * 24h period, KEEP policy: WorkManager dedupes by unique-work
     * name, so a re-launch never collapses a still-running run or
     * shifts the next-fire anchor.
     */
    private fun scheduleClusterDetection() {
        val constraints = androidx.work.Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiredNetworkType(androidx.work.NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
        val initialDelay = computeInitialDelayToLocalTime(
            now = java.time.ZonedDateTime.now(java.time.ZoneId.systemDefault()),
            targetLocalTime = java.time.LocalTime.of(3, 0)
        )
        val request = PeriodicWorkRequestBuilder<com.capsule.app.cluster.ClusterDetectionWorker>(
            repeatInterval = 1,
            repeatIntervalTimeUnit = TimeUnit.DAYS,
            flexTimeInterval = 2,
            flexTimeIntervalUnit = TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setInitialDelay(initialDelay.toMillis(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.capsule.app.cluster.ClusterDetectionWorker.UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * T132 helper — daily anchor at [targetLocalTime]. Today's
     * [targetLocalTime] if still ahead of [now], otherwise tomorrow's.
     */
    internal fun computeInitialDelayToLocalTime(
        now: java.time.ZonedDateTime,
        targetLocalTime: java.time.LocalTime
    ): java.time.Duration {
        val candidate = now.with(targetLocalTime)
        val anchor = if (!candidate.isAfter(now)) candidate.plusDays(1) else candidate
        return java.time.Duration.between(now, anchor)
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
