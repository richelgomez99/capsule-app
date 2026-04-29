package com.capsule.app.continuation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.capsule.app.continuation.ContinuationEngine
import com.capsule.app.data.ipc.IEnvelopeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * T072 (003 US3) — `PeriodicWorkRequest` that runs every Sunday at the
 * user's configured local time (default 06:00) and produces 0..1
 * `kind = DIGEST` envelopes for the prior week. Wired into the
 * default WorkManager process; binds `:ml`'s
 * [com.capsule.app.data.ipc.EnvelopeRepositoryService] and forwards
 * the target Sunday's `day_local` to
 * [IEnvelopeRepository.runWeeklyDigest], which executes the
 * `WeeklyDigestDelegate`.
 *
 * Outcome string contract (per AIDL doc):
 *  - `GENERATED:<id>`             → `Result.success()` (digest written)
 *  - `SKIPPED:too_sparse`          → `Result.success()` (worker did its job)
 *  - `SKIPPED:already_exists`      → `Result.success()` (idempotent)
 *  - `FAILED:*`                    → `Result.retry()` until
 *    [ContinuationEngine.MAX_ATTEMPTS], then `Result.failure()`
 *  - binder unavailable / throw    → retry (transient)
 *
 * Per weekly-digest-contract.md §2 the schedule is built externally
 * (see [com.capsule.app.CapsuleApplication.scheduleWeeklyDigest]); the
 * worker itself only knows how to compute the target Sunday from the
 * current time at run-time so re-scheduling on locale/zone changes
 * doesn't cause it to digest the wrong week.
 */
class WeeklyDigestWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val zone = zoneIdProvider()
        val today = LocalDate.now(zone)
        // The digest is "for the prior week, surfaced on Sunday's
        // diary page". If the worker fires on Sunday (its anchor),
        // target = today; if WorkManager runs us on Monday after a
        // missed slot, target = previous Sunday so we still write the
        // weekend's digest into the right diary page.
        val targetSunday: LocalDate = when (today.dayOfWeek) {
            DayOfWeek.SUNDAY -> today
            else -> today.with(TemporalAdjusters.previous(DayOfWeek.SUNDAY))
        }

        val repo = repositoryBinder(applicationContext) ?: run {
            return if (runAttemptCount + 1 >= ContinuationEngine.MAX_ATTEMPTS) Result.failure()
                   else Result.retry()
        }

        val outcome: String = try {
            repo.runWeeklyDigest(targetSunday.toString())
        } catch (t: Throwable) {
            android.util.Log.w(
                "WeeklyDigest",
                "runWeeklyDigest($targetSunday) threw ${t.javaClass.simpleName}: ${t.message}"
            )
            return if (runAttemptCount + 1 >= ContinuationEngine.MAX_ATTEMPTS) Result.failure()
                   else Result.retry()
        }

        return when {
            outcome.startsWith("GENERATED:") -> Result.success()
            outcome.startsWith("SKIPPED:") -> Result.success()
            outcome.startsWith("FAILED:") ->
                if (runAttemptCount + 1 >= ContinuationEngine.MAX_ATTEMPTS) Result.failure()
                else Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME = "weekly-digest"
        private const val BIND_TIMEOUT_MS: Long = 5_000L

        /** Test seam — same pattern as ActionExtractionWorker. */
        @Volatile
        internal var repositoryBinder: suspend (Context) -> IEnvelopeRepository? =
            ::bindRepositoryDefault

        /** Test seam — overridable to inject a fixed zone in tests. */
        @Volatile
        internal var zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }

        private suspend fun bindRepositoryDefault(context: Context): IEnvelopeRepository? =
            withContext(Dispatchers.Main) {
                val deferred = CompletableDeferred<IEnvelopeRepository?>()
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val stub = IEnvelopeRepository.Stub.asInterface(service)
                        if (!deferred.isCompleted) deferred.complete(stub)
                    }
                    override fun onServiceDisconnected(name: ComponentName?) { /* no-op */ }
                }
                val intent = Intent("com.capsule.app.action.BIND_ENVELOPE_REPOSITORY").apply {
                    `package` = context.packageName
                }
                val bound = try {
                    context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                } catch (_: SecurityException) {
                    false
                }
                if (!bound) {
                    runCatching { context.unbindService(connection) }
                    return@withContext null
                }
                withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }
            }
    }
}
