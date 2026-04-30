package com.capsule.app.cluster

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.capsule.app.ai.EmbeddingResult
import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.model.ActionExtractionResult
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.entity.ContinuationResultEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.EnvelopeKind
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import com.capsule.app.data.security.KeystoreKeyProvider
import com.capsule.app.net.ipc.INetworkGateway
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T131 / Phase 11 Block 4 — instrumented coverage of [ClusterDetectionWorker].
 *
 * Tests the WorkManager glue exclusively (Result mapping, gateway bind seam,
 * provider seam). Detector semantics are covered JVM-side by
 * [ClusterDetectorTest]; this suite confirms the four production-relevant
 * Result outcomes when running through `TestListenableWorkerBuilder`:
 *
 *  1. `:net` bind failure       → [ListenableWorker.Result.retry]
 *  2. Happy path (1 cluster)    → [ListenableWorker.Result.success]
 *  3. All embeds null on ≥1 cand → [ListenableWorker.Result.retry] (transient)
 *  4. Dimension mismatch (FR-038)→ [ListenableWorker.Result.failure] (permanent)
 *
 * The test installs an in-memory SQLCipher [OrbitDatabase] via
 * [OrbitDatabase.overrideInstanceForTest], stubs [ClusterDetectionWorker.gatewayBinder]
 * with a no-op [INetworkGateway], and stubs [ClusterDetectionWorker.providerFactory]
 * with a per-case [TestLlmProvider]. Both seams are restored in `@After`.
 */
@RunWith(AndroidJUnit4::class)
class ClusterDetectionWorkerTest {

    private lateinit var context: Context
    private lateinit var db: OrbitDatabase

    private val originalProviderFactory = ClusterDetectionWorker.providerFactory
    private val originalGatewayBinder = ClusterDetectionWorker.gatewayBinder

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        context = ApplicationProvider.getApplicationContext()
        val passphrase = KeystoreKeyProvider.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)
        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()
        OrbitDatabase.overrideInstanceForTest(db)

        // Default: a no-op INetworkGateway stub. Individual tests that
        // exercise the bind-failure path replace this with `null`.
        ClusterDetectionWorker.gatewayBinder = { NoopNetworkGateway }
    }

    @After
    fun tearDown() {
        ClusterDetectionWorker.providerFactory = originalProviderFactory
        ClusterDetectionWorker.gatewayBinder = originalGatewayBinder
        OrbitDatabase.overrideInstanceForTest(null)
        db.close()
    }

    /** Bind failure (gatewayBinder returns null) → retry. */
    @Test
    fun bindFailure_returnsRetry() = runBlocking {
        ClusterDetectionWorker.gatewayBinder = { null }
        // providerFactory should never be reached; install a sentinel.
        ClusterDetectionWorker.providerFactory = { _, _ ->
            error("providerFactory must not be reached when bind fails")
        }

        val result = TestListenableWorkerBuilder<ClusterDetectionWorker>(context)
            .build()
            .doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    /** Empty DB (zero candidates) → success, no clusters persisted. */
    @Test
    fun emptyDatabase_returnsSuccess() = runBlocking {
        ClusterDetectionWorker.providerFactory = { _, _ ->
            TestLlmProvider(embeddings = emptyMap())
        }

        val result = TestListenableWorkerBuilder<ClusterDetectionWorker>(context)
            .build()
            .doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(0, db.clusterDao().listAll().size)
    }

    /**
     * Happy path: three envelopes hydrated with similar-summary URLs
     * across two domains, embeddings all returned non-null and similar.
     * Detector forms one cluster → worker returns success.
     */
    @Test
    fun happyPath_persistsClusterAndReturnsSuccess() = runBlocking {
        seedThreeCandidates(db, withinLast24h = true)
        val embeddings = mapOf(
            "env-a" to unitVector(0f),
            "env-b" to unitVector(2f),
            "env-c" to unitVector(4f)
        )
        ClusterDetectionWorker.providerFactory = { _, _ ->
            TestLlmProvider(embeddings = embeddings)
        }

        val result = TestListenableWorkerBuilder<ClusterDetectionWorker>(context)
            .build()
            .doWork()

        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(1, db.clusterDao().listAll().size)
    }

    /**
     * `:net` is bound but every embed returns `null` — the worker's
     * "candidatesScanned > 0 && embeddingsObtained == 0" heuristic
     * trips and returns retry (transient embed failure).
     */
    @Test
    fun allEmbedsNull_returnsRetry() = runBlocking {
        seedThreeCandidates(db, withinLast24h = true)
        ClusterDetectionWorker.providerFactory = { _, _ ->
            TestLlmProvider(embeddings = emptyMap()) // all returns null
        }

        val result = TestListenableWorkerBuilder<ClusterDetectionWorker>(context)
            .build()
            .doWork()

        assertEquals(ListenableWorker.Result.retry(), result)
    }

    /**
     * FR-038: same modelLabel but mismatched embedding dimensionality
     * inside a single bucket → [ClusterDetector.DimensionMismatchInRun]
     * → permanent failure (not retry).
     */
    @Test
    fun dimensionMismatch_returnsFailure() = runBlocking {
        seedThreeCandidates(db, withinLast24h = true)
        // Two 2-d vectors and one 3-d vector; same modelLabel so the
        // per-bucket rev-drift guard doesn't catch it first.
        val embeddings = mapOf(
            "env-a" to floatArrayOf(1.0f, 0.0f),
            "env-b" to floatArrayOf(1.0f, 0.0f, 0.0f),
            "env-c" to floatArrayOf(1.0f, 0.0f)
        )
        ClusterDetectionWorker.providerFactory = { _, _ ->
            TestLlmProvider(embeddings = embeddings)
        }

        val result = TestListenableWorkerBuilder<ClusterDetectionWorker>(context)
            .build()
            .doWork()

        assertEquals(ListenableWorker.Result.failure(), result)
        // No cluster persisted on permanent failure.
        assertEquals(0, db.clusterDao().listAll().size)
    }

    // ---- helpers --------------------------------------------------------

    private fun unitVector(angleDeg: Float): FloatArray {
        val rad = angleDeg.toDouble() * Math.PI / 180.0
        return floatArrayOf(Math.cos(rad).toFloat(), Math.sin(rad).toFloat())
    }

    private suspend fun seedThreeCandidates(db: OrbitDatabase, withinLast24h: Boolean) {
        val now = System.currentTimeMillis()
        val ts = if (withinLast24h) now - 60_000L else now - 8L * 24 * 60 * 60 * 1000L
        val envelopes = listOf(
            buildEnvelope("env-a", ts),
            buildEnvelope("env-b", ts + 30L * 60_000L),
            buildEnvelope("env-c", ts + 90L * 60_000L)
        )
        envelopes.forEach { db.intentEnvelopeDao().insert(it) }

        val sharedSummary = "transformer attention embedding encoder decoder vector neural language"
        val results = listOf(
            buildResult("res-a", "env-a", ts, "site-one.example", sharedSummary),
            buildResult("res-b", "env-b", ts + 30L * 60_000L, "site-two.example", sharedSummary),
            buildResult("res-c", "env-c", ts + 90L * 60_000L, "site-one.example", sharedSummary)
        )
        results.forEach { db.continuationResultDao().insert(it) }
    }

    private fun buildEnvelope(id: String, createdAt: Long): IntentEnvelopeEntity =
        IntentEnvelopeEntity(
            id = id,
            contentType = ContentType.TEXT,
            textContent = "https://example.com/$id",
            imageUri = null,
            textContentSha256 = null,
            intent = Intent.AMBIGUOUS,
            intentConfidence = null,
            intentSource = IntentSource.FALLBACK,
            intentHistoryJson = "[]",
            state = StateSnapshot(
                appCategory = AppCategory.OTHER,
                activityState = ActivityState.STILL,
                tzId = "UTC",
                hourLocal = 10,
                dayOfWeekLocal = 2
            ),
            createdAt = createdAt,
            dayLocal = "2026-04-29",
            isArchived = false,
            isDeleted = false,
            deletedAt = null,
            sharedContinuationResultId = null,
            kind = EnvelopeKind.REGULAR,
            derivedFromEnvelopeIdsJson = null,
            todoMetaJson = null
        )

    private fun buildResult(
        id: String,
        envelopeId: String,
        producedAt: Long,
        domain: String,
        summary: String
    ): ContinuationResultEntity = ContinuationResultEntity(
        id = id,
        continuationId = "$id-cont",
        envelopeId = envelopeId,
        producedAt = producedAt,
        title = "title",
        domain = domain,
        canonicalUrl = "https://$domain/$envelopeId",
        canonicalUrlHash = "$domain-$envelopeId-hash",
        excerpt = "excerpt",
        summary = summary,
        summaryModel = "test"
    )
}

/**
 * Test stub — implements the full [LlmProvider] surface but only [embed]
 * matters here. Other methods raise so a future refactor that starts
 * calling them surfaces immediately.
 */
private class TestLlmProvider(
    private val embeddings: Map<String, FloatArray>,
    private val modelLabel: String = "test-model@2026-04-29"
) : LlmProvider {

    override suspend fun embed(text: String): EmbeddingResult? {
        // Envelope ids appear at the end of the synthetic URLs we seed.
        val tail = text.trim().substringAfterLast('/').lowercase()
        // Or routes by first word for plain summaries.
        val byTail = embeddings[tail]
        val key = if (byTail != null) tail else null
        val v = (key?.let { embeddings[it] }) ?: return matchByValue(text)
        return EmbeddingResult(v, modelLabel, v.size)
    }

    private fun matchByValue(text: String): EmbeddingResult? {
        // Fallback: return the first embedding whose key appears in `text`.
        for ((k, v) in embeddings) {
            if (text.contains(k, ignoreCase = true)) {
                return EmbeddingResult(v, modelLabel, v.size)
            }
        }
        return null
    }

    override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification =
        error("not used")

    override suspend fun summarize(text: String, maxTokens: Int): SummaryResult =
        error("not used")

    override suspend fun scanSensitivity(text: String): SensitivityResult =
        error("not used")

    override suspend fun generateDayHeader(
        dayIsoDate: String,
        envelopeSummaries: List<String>
    ): DayHeaderResult = error("not used")

    override suspend fun extractActions(
        text: String,
        contentType: String,
        state: StateSnapshot,
        registeredFunctions: List<AppFunctionSummary>,
        maxCandidates: Int
    ): ActionExtractionResult = error("not used")
}

/**
 * Minimal [INetworkGateway] stub — the worker only needs *something*
 * non-null back from [ClusterDetectionWorker.gatewayBinder] to proceed
 * to the provider step. The fake [TestLlmProvider] never actually
 * routes through the gateway, so no real method is called.
 */
private object NoopNetworkGateway : INetworkGateway.Stub() {
    override fun fetchPublicUrl(
        url: String?,
        timeoutMs: Long
    ): com.capsule.app.net.ipc.FetchResultParcel? = null

    override fun callLlmGateway(
        request: com.capsule.app.net.ipc.LlmGatewayRequestParcel?
    ): com.capsule.app.net.ipc.LlmGatewayResponseParcel? = null
}
