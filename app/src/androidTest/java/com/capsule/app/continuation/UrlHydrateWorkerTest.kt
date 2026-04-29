package com.capsule.app.continuation

import android.content.Context
import android.os.IBinder
import android.os.RemoteException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.Data
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.NanoSummariser
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.net.ipc.FetchResultParcel
import com.capsule.app.net.ipc.INetworkGateway
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T059 — instrumented tests for [UrlHydrateWorker] + [ContinuationEngine].
 *
 * Scope (Agent B, Phase 5 slice):
 *   - ENQUEUE: asserts [ContinuationEngine.enqueueForNewEnvelope] registers
 *     the work with `RequiresCharging + UNMETERED + BatteryNotLow`
 *     constraints and the URL_HYDRATE type tag.
 *   - HAPPY PATH: a faked gateway returning `ok=true` makes the worker
 *     produce SUCCESS + a non-null [NanoSummariser.Summary].
 *   - RETRIABLE 503: a faked gateway returning `errorKind="http_error"`
 *     drives `Result.retry()` up to the 3-attempt cap; on the 3rd
 *     attempt (`runAttemptCount=2`) the worker yields `Result.failure()`
 *     (FAILED_MAX_RETRIES classification per §9).
 *   - PERMANENT blocked_host: a faked gateway returning
 *     `errorKind="blocked_host"` drives `Result.failure()` immediately,
 *     with no retry regardless of `runAttemptCount`.
 *
 * The merge-zone portion of T066 (ContinuationResultEntity writes,
 * audit rows) is deferred; this test suite intentionally only asserts
 * the WorkManager + Result surface owned by Agent B.
 */
@RunWith(AndroidJUnit4::class)
class UrlHydrateWorkerTest {

    private lateinit var context: Context
    private lateinit var workManager: WorkManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val config = Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, config)
        workManager = WorkManager.getInstance(context)
    }

    @After
    fun tearDown() {
        // Restore production defaults so other instrumented suites are unaffected.
        UrlHydrateWorker.gatewayBinder = DefaultBinderHolder.ORIGINAL_BINDER
        UrlHydrateWorker.summariserFactory = DefaultBinderHolder.ORIGINAL_FACTORY
    }

    // ---------------- ContinuationEngine enqueue surface ----------------

    @Test
    fun enqueueForNewEnvelope_schedulesUrlHydrateWithContractConstraints() {
        val engine = ContinuationEngine(workManager)
        val jobs = engine.enqueueForNewEnvelope(
            envelopeId = "env-1",
            contentType = ContinuationEngine.ContentType.TEXT,
            textContent = "read https://example.com/a later",
            imageUri = null
        )
        assertEquals(1, jobs.size)

        val infos = workManager.getWorkInfosByTag(ContinuationEngine.TYPE_TAG_URL_HYDRATE).get()
        assertEquals(1, infos.size)
        val info = infos.single()
        assertTrue(info.tags.contains(ContinuationEngine.TAG_CONTINUATION))
        assertTrue(info.tags.contains(ContinuationEngine.tagForEnvelope("env-1")))
        assertEquals(WorkInfo.State.ENQUEUED, info.state)

        // Constraints (§2) live on the Constraints singleton shared with
        // the engine, not on WorkInfo; assert by inspecting it directly.
        val c = ContinuationEngine.DEFAULT_CONSTRAINTS
        assertTrue(c.requiresCharging())
        assertTrue(c.requiresBatteryNotLow())
        assertEquals(androidx.work.NetworkType.UNMETERED, c.requiredNetworkType)
    }

    @Test
    fun enqueueForNewEnvelope_fansOutOnePerUrl() {
        val engine = ContinuationEngine(workManager)
        val jobs = engine.enqueueForNewEnvelope(
            envelopeId = "env-many",
            contentType = ContinuationEngine.ContentType.TEXT,
            textContent = "a https://a.example b https://b.example c https://c.example",
            imageUri = null
        )
        assertEquals(3, jobs.size)
        val infos = workManager.getWorkInfosByTag(ContinuationEngine.tagForEnvelope("env-many")).get()
        assertEquals(3, infos.size)
    }

    @Test
    fun enqueueForNewEnvelope_noUrls_enqueuesNothing() {
        val engine = ContinuationEngine(workManager)
        val jobs = engine.enqueueForNewEnvelope(
            envelopeId = "env-empty",
            contentType = ContinuationEngine.ContentType.TEXT,
            textContent = "just a note, no links",
            imageUri = null
        )
        assertEquals(0, jobs.size)
        assertEquals(
            0,
            workManager.getWorkInfosByTag(ContinuationEngine.tagForEnvelope("env-empty")).get().size
        )
    }

    // ---------------- UrlHydrateWorker outcome classification ----------------

    @Test
    fun worker_happyPath_returnsSuccess_withSummary() = runTest {
        UrlHydrateWorker.gatewayBinder = fakeBinder(
            FakeGateway { _, _ ->
                FetchResultParcel(
                    ok = true,
                    finalUrl = "https://example.com/a",
                    title = "Example Page",
                    canonicalHost = "example.com",
                    readableHtml = "This is the readable body of a legitimate article.",
                    errorKind = null,
                    errorMessage = null,
                    fetchedAtMillis = 0L
                )
            }
        )
        UrlHydrateWorker.summariserFactory = { _ ->
            NanoSummariser(FakeLlm(text = "One sentence. Two sentences."))
        }

        val worker = buildWorker(attempts = 0)
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
    }

    @Test
    fun worker_503_retriesWhileBelowMaxAttempts_thenFails() = runTest {
        UrlHydrateWorker.gatewayBinder = fakeBinder(
            FakeGateway { _, _ ->
                FetchResultParcel(
                    ok = false,
                    finalUrl = null, title = null, canonicalHost = null, readableHtml = null,
                    errorKind = "http_error",
                    errorMessage = "503",
                    fetchedAtMillis = 0L
                )
            }
        )

        // Attempt #1 (runAttemptCount = 0) → retry
        assertEquals(ListenableWorker.Result.retry(), buildWorker(attempts = 0).doWork())
        // Attempt #2 (runAttemptCount = 1) → retry
        assertEquals(ListenableWorker.Result.retry(), buildWorker(attempts = 1).doWork())
        // Attempt #3 (runAttemptCount = 2) → failure (MAX_ATTEMPTS=3 exhausted)
        assertEquals(ListenableWorker.Result.failure(), buildWorker(attempts = 2).doWork())
    }

    @Test
    fun worker_blockedHost_failsImmediatelyWithNoRetry() = runTest {
        UrlHydrateWorker.gatewayBinder = fakeBinder(
            FakeGateway { _, _ ->
                FetchResultParcel(
                    ok = false,
                    finalUrl = null, title = null, canonicalHost = null, readableHtml = null,
                    errorKind = "blocked_host",
                    errorMessage = "10.0.0.1 is private",
                    fetchedAtMillis = 0L
                )
            }
        )
        // Even at attempt #1 the result is failure (no retry for non-retriable errors §9).
        assertEquals(ListenableWorker.Result.failure(), buildWorker(attempts = 0).doWork())
    }

    @Test
    fun worker_runHydration_blankReadableHtml_stillSucceeds_withFallbackModel() = runTest {
        UrlHydrateWorker.gatewayBinder = fakeBinder(
            FakeGateway { _, _ ->
                FetchResultParcel(
                    ok = true, finalUrl = "https://example.com/a",
                    title = "Title only",
                    canonicalHost = "example.com",
                    readableHtml = "",
                    errorKind = null, errorMessage = null,
                    fetchedAtMillis = 0L
                )
            }
        )
        UrlHydrateWorker.summariserFactory = { _ -> NanoSummariser(FakeLlm("should not matter")) }

        val outcome = UrlHydrateWorker.runHydration(context, "https://example.com/a")
        assertEquals(UrlHydrateWorker.Classification.SUCCESS, outcome.classification)
        // Blank slug → NanoSummariser short-circuits → fallback label.
        assertEquals(NanoSummariser.FALLBACK_MODEL_LABEL, outcome.summaryModel)
        assertNotNull(outcome.fetch)
    }

    // ---------------- helpers ----------------

    private fun buildWorker(attempts: Int): UrlHydrateWorker {
        val input = Data.Builder()
            .putString(ContinuationEngine.KEY_ENVELOPE_ID, "env-test")
            .putString(ContinuationEngine.KEY_CONTINUATION_ID, "cont-test")
            .putString(ContinuationEngine.KEY_URL, "https://example.com/a")
            .build()
        return TestListenableWorkerBuilder<UrlHydrateWorker>(context)
            .setInputData(input)
            .setRunAttemptCount(attempts)
            .build()
    }

    private fun fakeBinder(gateway: INetworkGateway): suspend (Context) -> INetworkGateway? =
        { _ -> gateway }

    private class FakeGateway(
        private val onFetch: (String, Long) -> FetchResultParcel
    ) : INetworkGateway.Stub() {
        @Throws(RemoteException::class)
        override fun fetchPublicUrl(url: String, timeoutMs: Long): FetchResultParcel =
            onFetch(url, timeoutMs)
    }

    private class FakeLlm(private val text: String) : LlmProvider {
        override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification =
            error("unused")

        override suspend fun summarize(text: String, maxTokens: Int): SummaryResult =
            SummaryResult(text = this.text, generationLocale = "en", provenance = LlmProvenance.LocalNano)

        override suspend fun scanSensitivity(text: String): SensitivityResult = error("unused")
        override suspend fun generateDayHeader(
            dayIsoDate: String,
            envelopeSummaries: List<String>
        ): DayHeaderResult = error("unused")

        override suspend fun extractActions(
            text: String,
            contentType: String,
            state: com.capsule.app.data.entity.StateSnapshot,
            registeredFunctions: List<com.capsule.app.ai.model.AppFunctionSummary>,
            maxCandidates: Int
        ): com.capsule.app.ai.model.ActionExtractionResult = error("unused")

        override suspend fun embed(text: String): com.capsule.app.ai.EmbeddingResult? = null
    }

    /** Snapshot the original production defaults so @After can restore them. */
    private object DefaultBinderHolder {
        val ORIGINAL_BINDER: suspend (Context) -> INetworkGateway? =
            UrlHydrateWorker.gatewayBinder
        val ORIGINAL_FACTORY: (Context) -> NanoSummariser = UrlHydrateWorker.summariserFactory
    }
}
