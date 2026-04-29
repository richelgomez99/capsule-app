package com.capsule.app.continuation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.capsule.app.ai.OcrEngine
import com.capsule.app.data.ipc.IEnvelopeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * T076 (Phase 6 US4) — Screenshot URL extraction worker.
 *
 * Runs in the default WorkManager process. Steps:
 *   1. Load the image via `ContentResolver` (read-only URI permission from
 *      [ScreenshotObserver] / seal path).
 *   2. Run [OcrEngine] against it. Graceful-degrade on any ML Kit failure —
 *      returns an empty text result and the worker completes successfully
 *      with zero follow-up continuations.
 *   3. Extract URLs from the OCR text using [ContinuationEngine.extractUrls]
 *      (same regex as `TEXT` captures so the output is consistent).
 *   4. Bind [IEnvelopeRepository] in `:ml` and call
 *      [IEnvelopeRepository.seedScreenshotHydrations]. The repo writes the
 *      PENDING URL_HYDRATE rows + audit + enqueues the follow-up
 *      [UrlHydrateWorker] jobs in a single Room transaction.
 *
 * Retry behaviour: on binder failure we return [Result.retry] so the
 * write-back isn't lost. OCR itself never throws (graceful degrade) so
 * the only transient error surface is the binder. No hard attempt cap
 * here because `DEFAULT_CONSTRAINTS` already gate execution on
 * charging / unmetered.
 */
class ScreenshotUrlExtractWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val envelopeId = inputData.getString(ContinuationEngine.KEY_ENVELOPE_ID)
            ?: return Result.failure()
        val imageUriString = inputData.getString(ContinuationEngine.KEY_IMAGE_URI)
            ?: return Result.failure()

        val imageUri: Uri = try {
            Uri.parse(imageUriString)
        } catch (_: Throwable) {
            return Result.failure()
        }

        val ocr = ocrFactory().extractText(applicationContext, imageUri)
        val urls = ContinuationEngine.extractUrls(ocr.text)

        return try {
            val repo = repositoryBinder(applicationContext)
                ?: return Result.retry()
            repo.seedScreenshotHydrations(
                envelopeId,
                ocr.text,
                urls.toTypedArray()
            )
            Result.success()
        } catch (_: Throwable) {
            if (runAttemptCount + 1 >= ContinuationEngine.MAX_ATTEMPTS) {
                Result.failure()
            } else {
                Result.retry()
            }
        }
    }

    companion object {
        /** Test seam — swap to a fake [OcrEngine] from unit tests. */
        @Volatile
        internal var ocrFactory: () -> OcrEngine = { OcrEngine() }

        /**
         * Test seam — swap the [IEnvelopeRepository] binder in tests.
         * Mirrors [UrlHydrateWorker.repositoryBinder].
         */
        @Volatile
        internal var repositoryBinder: suspend (Context) -> IEnvelopeRepository? =
            ::bindRepositoryDefault

        private const val BIND_TIMEOUT_MS = 5_000L

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
