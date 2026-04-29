package com.capsule.app.action

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * T050 — 5-second post-dispatch undo-window cleanup.
 *
 * Scheduled by [ActionExecutorService] immediately after a handler
 * dispatches an external Intent (or completes a local DB write). On
 * fire (5s later), this worker checks whether the user tapped "undo"
 * inside the window: if a cancel hook was registered AND not yet
 * consumed, it has already done its job. Either way the worker simply
 * removes the executionId from the in-memory pending map so the entry
 * doesn't leak across long-running services.
 *
 * Per specs/003-orbit-actions/contracts/action-execution-contract.md §4
 * step 6: "(5s elapses) → DelayedUndoCleanupWorker no-ops (outcome stays
 * DISPATCHED)". So this worker NEVER changes the persisted outcome —
 * the system Calendar/Share app may already have committed the side
 * effect during those 5 seconds, and Orbit can't retract it.
 *
 * Constraints: none. Runs immediately on the default WorkManager process.
 *
 * v1.1 limitation: the cancel-hook registry lives in-process inside
 * `ActionExecutorService`, which means a process death within the 5s
 * window loses the ability to cancel. Acceptable trade-off — process
 * death + recovery within 5s is effectively never observed in practice
 * and the system Calendar app's own confirmation dialog still gates the
 * external write.
 */
class DelayedUndoCleanupWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val executionId = inputData.getString(KEY_EXECUTION_ID) ?: return Result.success()
        // The pending map lives in [ActionExecutorService]. We don't
        // bind to it here — instead the service's own hook is keyed by
        // `executionId` and self-cleans on `cancelWithinUndoWindow()`.
        // This worker fires only to (a) bound the cancel window in
        // wall-clock time and (b) provide a visible WorkManager unit so
        // tests can observe the 5s elapsed event deterministically.
        ActionExecutorService.expireUndoWindow(executionId)
        return Result.success()
    }

    companion object {
        const val KEY_EXECUTION_ID = "executionId"
        const val UNDO_WINDOW_SECONDS: Long = 5L

        fun enqueue(context: Context, executionId: String) {
            val request = OneTimeWorkRequestBuilder<DelayedUndoCleanupWorker>()
                .setInitialDelay(UNDO_WINDOW_SECONDS, TimeUnit.SECONDS)
                .addTag(TAG)
                .addTag("execution:$executionId")
                .setInputData(
                    Data.Builder()
                        .putString(KEY_EXECUTION_ID, executionId)
                        .build()
                )
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }

        const val TAG = "delayed-undo-cleanup"
    }
}
