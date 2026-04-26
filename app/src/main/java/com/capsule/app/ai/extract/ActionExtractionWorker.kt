package com.capsule.app.ai.extract

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

/**
 * T044 — `ACTION_EXTRACT` continuation worker.
 *
 * Lives in the default WorkManager process. Binds to `:ml`'s
 * [com.capsule.app.data.ipc.EnvelopeRepositoryService] and forwards the
 * envelope id to [IEnvelopeRepository.extractActionsForEnvelope] — the
 * `:ml`-side implementation runs the [ActionExtractor] which holds the
 * encrypted Room reference.
 *
 * Retry semantics (specs/003-orbit-actions/contracts/action-extraction-contract.md §5):
 *  - `PROPOSED:N` / `NO_CANDIDATES` / `SKIPPED:*` → `Result.success()`
 *    (terminal; even when no row was inserted, the work unit completed).
 *  - `FAILED:*` → `Result.retry()` until [ContinuationEngine.MAX_ATTEMPTS]
 *    after which we surrender via `Result.failure()`.
 *  - Binder unavailable / unexpected throw → retry (transient).
 *
 * NB the worker process can't write the audit row for "give-up after
 * N failures" itself (it doesn't own the DB). The contract notes that
 * gap; `:ml` should fold a `CONTINUATION_FAILED reason=action_extract_exhausted`
 * row into `extractActionsForEnvelope` once we add per-call attempt count.
 * v1.1 ships the structural retry path; the audit-row hardening is T097
 * (Polish phase).
 */
class ActionExtractionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val envelopeId = inputData.getString(KEY_ENVELOPE_ID)
            ?: return Result.failure()

        val repo = repositoryBinder(applicationContext) ?: run {
            return if (runAttemptCount + 1 >= ContinuationEngine.MAX_ATTEMPTS) Result.failure()
                   else Result.retry()
        }

        val outcome: String = try {
            repo.extractActionsForEnvelope(envelopeId)
        } catch (t: Throwable) {
            android.util.Log.w(
                "ActionExtract",
                "extractActionsForEnvelope($envelopeId) threw ${t.javaClass.simpleName}: ${t.message}"
            )
            return if (runAttemptCount + 1 >= ContinuationEngine.MAX_ATTEMPTS) Result.failure()
                   else Result.retry()
        }

        return when {
            outcome.startsWith("PROPOSED:") -> Result.success()
            outcome == "NO_CANDIDATES" -> Result.success()
            outcome.startsWith("SKIPPED:") -> Result.success()
            outcome == "UNAVAILABLE" -> Result.failure()
            outcome.startsWith("FAILED:") ->
                if (runAttemptCount + 1 >= ContinuationEngine.MAX_ATTEMPTS) Result.failure()
                else Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        const val KEY_ENVELOPE_ID = "envelopeId"
        private const val BIND_TIMEOUT_MS: Long = 5_000L

        /** Test seam — same pattern as [com.capsule.app.continuation.UrlHydrateWorker]. */
        @Volatile
        internal var repositoryBinder: suspend (Context) -> IEnvelopeRepository? =
            ::bindRepositoryDefault

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
