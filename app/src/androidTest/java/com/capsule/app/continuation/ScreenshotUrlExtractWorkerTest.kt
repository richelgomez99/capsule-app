package com.capsule.app.continuation

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.capsule.app.ai.OcrEngine
import com.capsule.app.data.ipc.IEnvelopeObserver
import com.capsule.app.data.ipc.IEnvelopeRepository
import com.capsule.app.data.ipc.IntentEnvelopeDraftParcel
import com.capsule.app.data.ipc.StateSnapshotParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * T072 (Phase 6 US4) — ScreenshotUrlExtractWorker contract test.
 *
 * Exercises the worker via [TestListenableWorkerBuilder] with both seams
 * ([ScreenshotUrlExtractWorker.ocrFactory] and
 * [ScreenshotUrlExtractWorker.repositoryBinder]) swapped for fakes.
 *
 * Scenarios:
 *   - OCR emits text containing a URL → worker succeeds and calls
 *     [IEnvelopeRepository.seedScreenshotHydrations] with exactly the
 *     extracted URL(s).
 *   - OCR emits text without URLs → worker succeeds, seedScreenshotHydrations
 *     still called with an empty URL array (so the audit INFERENCE_RUN row
 *     is still written for traceability).
 *   - OCR throws / returns empty + ok=false → worker succeeds with zero URLs.
 *     OCR failures never fail the worker; caller sees an empty URL list.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotUrlExtractWorkerTest {

    @After
    fun resetSeams() {
        ScreenshotUrlExtractWorker.ocrFactory = { OcrEngine() }
        // repositoryBinder stays swapped per-test via `buildWorker(...)`.
    }

    @Test
    fun doWork_withUrlInOcr_seedsExtractedUrl() = runTest {
        val recorder = RecordingRepository()
        ScreenshotUrlExtractWorker.ocrFactory = {
            FakeOcrEngine(OcrEngine.Result(text = "Visit https://example.com/path for details", ok = true))
        }
        ScreenshotUrlExtractWorker.repositoryBinder = { _ -> recorder }

        val result = buildWorker(envelopeId = "env-1", imageUri = "content://media/42")
            .doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, recorder.calls.size)
        val (envelopeId, ocrText, urls) = recorder.calls.first()
        assertEquals("env-1", envelopeId)
        assertEquals("Visit https://example.com/path for details", ocrText)
        assertArrayEquals(arrayOf("https://example.com/path"), urls)
    }

    @Test
    fun doWork_noUrlsInOcr_stillSeedsWithEmptyArray() = runTest {
        val recorder = RecordingRepository()
        ScreenshotUrlExtractWorker.ocrFactory = {
            FakeOcrEngine(OcrEngine.Result(text = "no links here", ok = true))
        }
        ScreenshotUrlExtractWorker.repositoryBinder = { _ -> recorder }

        val result = buildWorker(envelopeId = "env-2", imageUri = "content://media/43")
            .doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, recorder.calls.size)
        assertEquals(0, recorder.calls.first().third.size)
    }

    @Test
    fun doWork_ocrFailed_succeedsWithZeroUrls() = runTest {
        val recorder = RecordingRepository()
        ScreenshotUrlExtractWorker.ocrFactory = {
            FakeOcrEngine(OcrEngine.Result(text = "", ok = false))
        }
        ScreenshotUrlExtractWorker.repositoryBinder = { _ -> recorder }

        val result = buildWorker(envelopeId = "env-3", imageUri = "content://media/44")
            .doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, recorder.calls.size)
        assertEquals(0, recorder.calls.first().third.size)
    }

    @Test
    fun doWork_repoBinderNull_returnsRetry() = runTest {
        ScreenshotUrlExtractWorker.ocrFactory = {
            FakeOcrEngine(OcrEngine.Result(text = "https://example.com", ok = true))
        }
        ScreenshotUrlExtractWorker.repositoryBinder = { _ -> null }

        val result = buildWorker(envelopeId = "env-4", imageUri = "content://media/45")
            .doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    // ---- Helpers ----

    private fun buildWorker(
        envelopeId: String,
        imageUri: String
    ): ScreenshotUrlExtractWorker {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val data = Data.Builder()
            .putString(ContinuationEngine.KEY_ENVELOPE_ID, envelopeId)
            .putString(ContinuationEngine.KEY_IMAGE_URI, imageUri)
            .build()
        return TestListenableWorkerBuilder<ScreenshotUrlExtractWorker>(ctx)
            .setInputData(data)
            .build()
    }

    private class FakeOcrEngine(private val fixed: OcrEngine.Result) : OcrEngine() {
        override suspend fun extractText(context: Context, imageUri: Uri): OcrEngine.Result = fixed
    }

    private class RecordingRepository : IEnvelopeRepository.Stub() {
        val calls = CopyOnWriteArrayList<Triple<String, String, Array<String>>>()

        override fun seedScreenshotHydrations(
            envelopeId: String,
            ocrText: String?,
            urls: Array<String>?
        ) {
            calls.add(Triple(envelopeId, ocrText.orEmpty(), urls ?: emptyArray()))
        }

        // ---- Unused surface ----
        override fun seal(draft: IntentEnvelopeDraftParcel, state: StateSnapshotParcel): String = error("unused")
        override fun observeDay(isoDate: String, observer: IEnvelopeObserver) = error("unused")
        override fun stopObserving(observer: IEnvelopeObserver) = error("unused")
        override fun getEnvelope(envelopeId: String): EnvelopeViewParcel = error("unused")
        override fun reassignIntent(envelopeId: String, newIntentName: String, reasonOpt: String?) = error("unused")
        override fun archive(envelopeId: String) = error("unused")
        override fun delete(envelopeId: String) = error("unused")
        override fun undo(envelopeId: String): Boolean = error("unused")
        override fun restoreFromTrash(envelopeId: String) = error("unused")
        override fun listSoftDeletedWithinDays(days: Int): List<EnvelopeViewParcel> = error("unused")
        override fun countSoftDeletedWithinDays(days: Int): Int = error("unused")
        override fun hardDelete(envelopeId: String) = error("unused")
        override fun distinctDayLocalsWithContent(limit: Int, offset: Int): MutableList<String> = mutableListOf()
        override fun countAll(): Int = error("unused")
        override fun countArchived(): Int = error("unused")
        override fun countDeleted(): Int = error("unused")
        override fun existsPriorIntent(appCategory: String, intent: String): Boolean = error("unused")
        override fun completeUrlHydration(
            continuationId: String,
            envelopeId: String,
            canonicalUrl: String?,
            canonicalUrlHash: String?,
            ok: Boolean,
            title: String?,
            domain: String?,
            summary: String?,
            summaryModel: String?,
            failureReason: String?
        ) = error("unused")
        override fun retryHydration(envelopeId: String) = error("unused")

        // ---- Spec 003 v1.1 IPC additions (unused by URL-extract tests). ----
        override fun lookupAppFunction(functionId: String): com.capsule.app.data.ipc.AppFunctionSummaryParcel? = null
        override fun listAppFunctions(appPackage: String): MutableList<com.capsule.app.data.ipc.AppFunctionSummaryParcel> = mutableListOf()
        override fun recordActionInvocation(
            executionId: String,
            proposalId: String,
            functionId: String,
            outcome: String,
            outcomeReason: String,
            dispatchedAtMillis: Long,
            completedAtMillis: Long,
            latencyMs: Long,
            episodeId: String?
        ) = error("unused")
        override fun markProposalConfirmed(proposalId: String): Boolean = error("unused")
        override fun markProposalDismissed(proposalId: String): Boolean = error("unused")
        override fun observeProposalsForEnvelope(
            envelopeId: String,
            observer: com.capsule.app.data.ipc.IActionProposalObserver
        ) = error("unused")
        override fun stopObservingProposals(observer: com.capsule.app.data.ipc.IActionProposalObserver) = error("unused")
        override fun extractActionsForEnvelope(envelopeId: String): String = error("unused")
        override fun createDerivedTodoEnvelope(
            parentEnvelopeId: String,
            itemsJson: String,
            proposalId: String
        ): MutableList<String> = error("unused")
        override fun setTodoItemDone(envelopeId: String, itemIndex: Int, done: Boolean) = error("unused")
        override fun runWeeklyDigest(targetDayLocal: String): String = error("unused")
    }
}
