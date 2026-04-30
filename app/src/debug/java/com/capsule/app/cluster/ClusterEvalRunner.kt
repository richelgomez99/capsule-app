package com.capsule.app.cluster

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.room.Room
import com.capsule.app.ai.ClusterSummariser
import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.NanoLlmProvider
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.entity.ContinuationEntity
import com.capsule.app.data.entity.ContinuationResultEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.ContinuationStatus
import com.capsule.app.data.model.ContinuationType
import com.capsule.app.data.model.EnvelopeKind
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import com.capsule.app.data.security.KeystoreKeyProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.time.Instant
import java.util.Date
import java.util.Locale

/**
 * T159 (spec/002 Phase 11) — debug-only cluster eval harness.
 *
 * Loads the 20 hand-authored fixtures committed under T158
 * (`app/src/test/resources/fixtures/clusters/research-session-XX.json`,
 * mounted as debug-only assets via `app/build.gradle.kts` →
 * `sourceSets.debug.assets.srcDirs`).
 *
 * Block 11 wires the real pipeline:
 *  - **Run eval** — for each fixture, builds an in-memory SQLCipher
 *    [OrbitDatabase], seeds the fixture's envelopes + continuation
 *    results, runs [ClusterDetector] with the on-device
 *    [NanoLlmProvider] embedder, then summarises any formed cluster
 *    via [ClusterSummariser] (Block 6) and computes a token-overlap
 *    drift score against the fixture's `expectedSummary.bullets`.
 *    Aggregates per-fixture results into corpus-level precision /
 *    recall and the cosine-distribution snapshot from Block 10.
 *  - **Run calibration** — embeds each fixture envelope once, computes
 *    actual pairwise cosine minima per fixture, prints
 *    fixture-vs-declared deltas, and loudly flags any POSITIVE
 *    fixture whose measured `cosine_min` drops below 0.70.
 *
 * Both actions write a JSON report to the app's external files dir
 * (`getExternalFilesDir(null)/cluster-eval-YYYYMMDD-HHmmss.json` or
 * `cluster-calibration-YYYYMMDD-HHmmss.json`) so the operator can
 * `adb pull` it for off-device review.
 *
 * Lives only in `:debug`; the release manifest never declares this
 * Activity (matches the security model of T097/003 and T157/002).
 */
class ClusterEvalRunner : Activity() {

    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP
            setPadding(48, 48, 48, 48)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val title = TextView(this).apply {
            text = "Cluster eval runner (T159)"
            textSize = 18f
            setPadding(0, 0, 0, 16)
        }

        statusView = TextView(this).apply {
            textSize = 12f
            setPadding(0, 0, 0, 16)
            text = "Press \"Run eval\" or \"Run calibration\" to load the 20 fixtures."
        }

        val runEvalButton = Button(this).apply {
            text = "Run eval"
            setOnClickListener { runEval() }
        }

        val runCalibrateButton = Button(this).apply {
            text = "Run calibration"
            setOnClickListener { runCalibrate() }
        }

        val scroll = ScrollView(this).apply {
            addView(statusView)
        }

        root.addView(title)
        root.addView(runEvalButton)
        root.addView(runCalibrateButton)
        root.addView(scroll)
        setContentView(root)
    }

    // ---- Run eval ------------------------------------------------------

    private fun runEval() {
        val report = try {
            runBlocking(Dispatchers.IO) { evaluate() }
        } catch (t: Throwable) {
            Log.e(LOG_TAG, "eval failed", t)
            statusView.text = "FAILED: ${t.javaClass.simpleName}: ${t.message}"
            return
        }

        val outFile = writeReport(report, prefix = "cluster-eval")
        val summary = buildString {
            append("Eval complete.\n")
            append("Fixtures parsed: ${report.optInt("fixturesParsed")}\n")
            append("Positive expected: ${report.optInt("positiveCount")}\n")
            append("Negative expected: ${report.optInt("negativeCount")}\n")
            val det = report.optJSONObject("detectionMetrics") ?: JSONObject()
            append("Detection — TP=${det.optInt("truePositive")} ")
            append("FP=${det.optInt("falsePositive")} ")
            append("FN=${det.optInt("falseNegative")} ")
            append("TN=${det.optInt("trueNegative")}\n")
            append("Precision: ${det.opt("precision") ?: "n/a"}\n")
            append("Recall: ${det.opt("recall") ?: "n/a"}\n")
            append("Domains: ${report.optJSONObject("domainCounts")}\n")
            append("Report: ${outFile.absolutePath}\n")
        }
        statusView.text = summary
        Log.i(LOG_TAG, summary)
    }

    private suspend fun evaluate(): JSONObject {
        val fixtures = loadFixtures()
        val perDomain = mutableMapOf<String, Int>()
        val outcomes = mutableMapOf<String, Int>()
        val cosineSamples = mutableListOf<Double>()
        val perFixtureReport = JSONArray()

        var positiveCount = 0
        var negativeCount = 0
        var truePositive = 0
        var falsePositive = 0
        var falseNegative = 0
        var trueNegative = 0
        val tokenOverlapSamples = mutableListOf<Double>()

        for (fx in fixtures) {
            val id = fx.optString("id")
            val domain = fx.optString("domain", "unknown")
            perDomain.merge(domain, 1, Int::plus)

            val meta = fx.optJSONObject("expected_metadata") ?: JSONObject()
            val outcome = meta.optString("expected_outcome", "UNKNOWN")
            outcomes.merge(outcome, 1, Int::plus)
            val isPositive = outcome == "POSITIVE"
            if (isPositive) positiveCount++ else negativeCount++

            val cosine = meta.optDouble("cosine_min_observed", Double.NaN)
            if (!cosine.isNaN()) cosineSamples += cosine

            // ---- real detection per fixture ----
            val detection = runDetection(fx)
            val clustersFormed = detection.optInt("clustersFormed", 0)
            val clusterFormedAsExpected = if (isPositive) clustersFormed >= 1 else clustersFormed == 0

            when {
                isPositive && clustersFormed >= 1 -> truePositive++
                isPositive && clustersFormed == 0 -> falseNegative++
                !isPositive && clustersFormed >= 1 -> falsePositive++
                !isPositive && clustersFormed == 0 -> trueNegative++
            }

            val tokenOverlap = detection.optDouble("tokenOverlapJaccard", Double.NaN)
            if (!tokenOverlap.isNaN()) tokenOverlapSamples += tokenOverlap

            perFixtureReport.put(
                JSONObject().apply {
                    put("id", id)
                    put("domain", domain)
                    put("expected_outcome", outcome)
                    put("envelope_count", fx.optJSONArray("envelopes")?.length() ?: 0)
                    put("cosine_min_observed", cosine.takeUnless { it.isNaN() } ?: JSONObject.NULL)
                    put("formedAsExpected", clusterFormedAsExpected)
                    put("detection", detection)
                }
            )
        }

        val precision = computeRatio(truePositive, truePositive + falsePositive)
        val recall = computeRatio(truePositive, truePositive + falseNegative)

        return JSONObject().apply {
            put("generatedAt", System.currentTimeMillis())
            put("fixturesParsed", fixtures.size)
            put("positiveCount", positiveCount)
            put("negativeCount", negativeCount)
            put("outcomeCounts", JSONObject(outcomes as Map<String, Int>))
            put("domainCounts", JSONObject(perDomain as Map<String, Int>))
            put(
                "cosineDistribution",
                JSONObject().apply {
                    put("samples", cosineSamples.size)
                    put("min", cosineSamples.minOrNull() ?: JSONObject.NULL)
                    put("max", cosineSamples.maxOrNull() ?: JSONObject.NULL)
                    put(
                        "mean",
                        if (cosineSamples.isEmpty()) JSONObject.NULL
                        else cosineSamples.average()
                    )
                }
            )
            put(
                "detectionMetrics",
                JSONObject().apply {
                    put("truePositive", truePositive)
                    put("falsePositive", falsePositive)
                    put("falseNegative", falseNegative)
                    put("trueNegative", trueNegative)
                    put("precision", precision ?: JSONObject.NULL)
                    put("recall", recall ?: JSONObject.NULL)
                    put(
                        "tokenOverlapMean",
                        if (tokenOverlapSamples.isEmpty()) JSONObject.NULL
                        else tokenOverlapSamples.average()
                    )
                    put("tokenOverlapSamples", tokenOverlapSamples.size)
                }
            )
            put("perFixture", perFixtureReport)
        }
    }

    // ---- Detection one fixture ----------------------------------------

    /**
     * Real per-fixture pipeline run. Builds a private SQLCipher
     * in-memory [OrbitDatabase], seeds the envelopes + continuation
     * results so [ClusterDao.findClusterCandidates] picks them up,
     * runs [ClusterDetector.detect] with [NanoLlmProvider], then for
     * each formed cluster runs [ClusterSummariser.summarise] and scores
     * the resulting bullets against the fixture's `expectedSummary`.
     *
     * Each fixture gets its own DB so cross-fixture state can never
     * leak into the metrics; the in-memory DB is closed in a
     * `finally` to free resources before the next fixture.
     */
    private suspend fun runDetection(fixture: JSONObject): JSONObject {
        val envelopesJson = fixture.optJSONArray("envelopes") ?: return JSONObject().apply {
            put("status", "ERROR")
            put("reason", "fixture has no envelopes array")
        }

        // Determine clock so all fixture envelopes are within the
        // detector's 24h lookback. anchor = max(captured_at) + 1h.
        val capturedTimes = (0 until envelopesJson.length()).map { i ->
            val s = envelopesJson.getJSONObject(i).optString("captured_at")
            runCatching { Instant.parse(s).toEpochMilli() }.getOrDefault(0L)
        }
        val anchorClock = (capturedTimes.maxOrNull() ?: System.currentTimeMillis()) +
            60L * 60L * 1000L

        val db = openInMemoryDb(this)
        return try {
            seedFixture(db, envelopesJson, capturedTimes)
            val detector = ClusterDetector(
                clusterDao = db.clusterDao(),
                auditLogDao = db.auditLogDao(),
                llm = NanoLlmProvider(),
                clock = { anchorClock },
            )
            val outcome = detector.detect()
            val (formed, scanned, embeddings) = when (outcome) {
                is ClusterDetector.Outcome.Skipped -> Triple(0, 0, 0)
                is ClusterDetector.Outcome.Completed -> Triple(
                    outcome.clustersFormed,
                    outcome.candidatesScanned,
                    outcome.embeddingsObtained
                )
            }

            // Summarise + score every cluster the detector wrote.
            val summariser = ClusterSummariser(NanoLlmProvider())
            val expectedBullets = fixture.optJSONObject("expectedSummary")
                ?.optJSONArray("bullets")
                ?.let { arr -> List(arr.length()) { arr.getString(it) } }
                .orEmpty()
            val expectedTokens = tokenise(expectedBullets.joinToString(" "))

            val summaryReports = JSONArray()
            var bestOverlap: Double? = null
            // Detector wrote rows directly via DAO; one-shot read of
            // every cluster (test scope is per-fixture so this is OK).
            val allClusterIds = db.clusterDao().listAll().map { it.id }
            for (cid in allClusterIds) {
                val cwm = db.clusterDao().byClusterIdWithMembers(cid) ?: continue
                val summary = summariser.summarise(cwm)
                val report = JSONObject().apply {
                    put("clusterId", cid)
                    put("memberCount", cwm.members.size)
                    put("similarityScore", cwm.cluster.similarityScore.toDouble())
                    put("modelLabel", cwm.cluster.modelLabel)
                }
                if (summary == null) {
                    report.put("summary", JSONObject.NULL)
                    report.put("tokenOverlapJaccard", JSONObject.NULL)
                } else {
                    val actualTokens = tokenise(summary.bullets.joinToString(" "))
                    val overlap = jaccard(actualTokens, expectedTokens)
                    bestOverlap = listOfNotNull(bestOverlap, overlap).maxOrNull()
                    report.put(
                        "summary",
                        JSONObject().apply {
                            put("bullets", JSONArray(summary.bullets))
                            put("citations", JSONArray(summary.citations.toList()))
                            put("model", summary.model)
                        }
                    )
                    report.put("tokenOverlapJaccard", overlap)
                }
                summaryReports.put(report)
            }

            JSONObject().apply {
                put("status", "OK")
                put("clustersFormed", formed)
                put("candidatesScanned", scanned)
                put("embeddingsObtained", embeddings)
                put("outcome", outcome::class.java.simpleName)
                put("summaries", summaryReports)
                put(
                    "tokenOverlapJaccard",
                    bestOverlap ?: JSONObject.NULL
                )
            }
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "fixture detection failed: ${fixture.optString("id")}", t)
            JSONObject().apply {
                put("status", "ERROR")
                put("reason", "${t.javaClass.simpleName}: ${t.message}")
            }
        } finally {
            try {
                db.close()
            } catch (_: Throwable) {
                // best-effort
            }
        }
    }

    // ---- Calibration --------------------------------------------------

    private fun runCalibrate() {
        val report = try {
            runBlocking(Dispatchers.IO) { calibrate() }
        } catch (t: Throwable) {
            Log.e(LOG_TAG, "calibration failed", t)
            statusView.text = "CALIBRATION FAILED: ${t.javaClass.simpleName}: ${t.message}"
            return
        }

        val outFile = writeReport(report, prefix = "cluster-calibration")
        val flagged = report.optJSONArray("belowFloorFlags")?.length() ?: 0
        val summary = buildString {
            append("Calibration complete.\n")
            append("Fixtures parsed: ${report.optInt("fixturesParsed")}\n")
            append("Positive fixtures embedded: ${report.optInt("positiveEmbedded")}\n")
            append("Mean delta (measured - declared): ${report.opt("meanDelta") ?: "n/a"}\n")
            append("POSITIVE fixtures < 0.70 floor: $flagged\n")
            append("Report: ${outFile.absolutePath}\n")
        }
        statusView.text = summary
        Log.i(LOG_TAG, summary)
    }

    /**
     * Cosine-drift calibration. Embeds each envelope's `hydrated_text`
     * once via [NanoLlmProvider], computes the actual pairwise cosine
     * minimum within each fixture, and compares to the declared
     * `expected_metadata.cosine_min_observed`. POSITIVE fixtures whose
     * measured min drops below 0.70 are flagged loudly because the
     * cluster predicate's threshold (FR-028) would silently drop them.
     */
    private suspend fun calibrate(): JSONObject {
        val fixtures = loadFixtures()
        val llm: LlmProvider = NanoLlmProvider()
        val perFixture = JSONArray()
        val deltas = mutableListOf<Double>()
        val belowFloor = JSONArray()
        var positiveEmbedded = 0

        for (fx in fixtures) {
            val id = fx.optString("id")
            val isPositive = fx.optJSONObject("expected_metadata")
                ?.optString("expected_outcome") == "POSITIVE"
            val declared = fx.optJSONObject("expected_metadata")
                ?.optDouble("cosine_min_observed", Double.NaN) ?: Double.NaN
            val envs = fx.optJSONArray("envelopes") ?: continue

            val vectors = ArrayList<FloatArray>(envs.length())
            for (i in 0 until envs.length()) {
                val text = envs.getJSONObject(i).optString("hydrated_text")
                if (text.isBlank()) continue
                val emb = try { llm.embed(text) } catch (_: Throwable) { null }
                if (emb != null) vectors += emb.vector
            }

            val measured = pairwiseMinCosine(vectors)
            if (isPositive && vectors.isNotEmpty()) positiveEmbedded++

            val delta = if (!declared.isNaN() && measured != null) measured - declared else null
            if (delta != null) deltas += delta

            val floorBreach = isPositive && measured != null && measured < 0.70
            if (floorBreach) {
                belowFloor.put(
                    JSONObject().apply {
                        put("id", id)
                        put("declared", declared)
                        put("measured", measured)
                    }
                )
                Log.w(
                    LOG_TAG,
                    "calibration_floor_breach fixture=$id declared=$declared measured=$measured"
                )
            }

            perFixture.put(
                JSONObject().apply {
                    put("id", id)
                    put("expected_outcome", fx.optJSONObject("expected_metadata")?.optString("expected_outcome") ?: JSONObject.NULL)
                    put("envelopeCount", envs.length())
                    put("embeddingsObtained", vectors.size)
                    put("declared_cosine_min", if (declared.isNaN()) JSONObject.NULL else declared)
                    put("measured_cosine_min", measured ?: JSONObject.NULL)
                    put("delta", delta ?: JSONObject.NULL)
                    put("breaches_positive_floor", floorBreach)
                }
            )
        }

        return JSONObject().apply {
            put("generatedAt", System.currentTimeMillis())
            put("fixturesParsed", fixtures.size)
            put("positiveEmbedded", positiveEmbedded)
            put("meanDelta", if (deltas.isEmpty()) JSONObject.NULL else deltas.average())
            put("belowFloorFlags", belowFloor)
            put("perFixture", perFixture)
        }
    }

    // ---- Helpers ------------------------------------------------------

    private fun loadFixtures(): List<JSONObject> {
        val names = assets.list(FIXTURE_DIR).orEmpty()
            .filter { it.endsWith(".json") }
            .sorted()
        return names.map { name ->
            val text = assets.open("$FIXTURE_DIR/$name")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            JSONObject(text)
        }
    }

    private fun writeReport(report: JSONObject, prefix: String): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val dir = getExternalFilesDir(null) ?: filesDir
        val out = File(dir, "$prefix-$stamp.json")
        out.writeText(report.toString(2))
        return out
    }

    private fun openInMemoryDb(context: Context): OrbitDatabase {
        System.loadLibrary("sqlcipher")
        val passphrase = KeystoreKeyProvider.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)
        return Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()
    }

    private suspend fun seedFixture(
        db: OrbitDatabase,
        envelopes: JSONArray,
        capturedTimes: List<Long>,
    ) {
        val envDao = db.intentEnvelopeDao()
        val contDao = db.continuationDao()
        val resultDao = db.continuationResultDao()

        for (i in 0 until envelopes.length()) {
            val e = envelopes.getJSONObject(i)
            val envId = e.optString("envelope_id", "env-fixture-$i")
            val createdAt = capturedTimes.getOrNull(i) ?: System.currentTimeMillis()
            val urlValue = e.optString("url").ifBlank { null }
            val urlDomain = e.optString("url_domain").ifBlank { null }
                ?: e.optString("domain").ifBlank { null }
            val hydrated = e.optString("hydrated_text", "")
            val dayLocal = Instant.ofEpochMilli(createdAt).toString().substring(0, 10)
            val modelLabel = e.optString("modelLabel").ifBlank { NanoLlmProvider.MODEL_LABEL }

            envDao.insert(
                IntentEnvelopeEntity(
                    id = envId,
                    contentType = ContentType.TEXT,
                    textContent = urlValue,
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
                        hourLocal = 12,
                        dayOfWeekLocal = 2,
                    ),
                    createdAt = createdAt,
                    dayLocal = dayLocal,
                    isArchived = false,
                    isDeleted = false,
                    deletedAt = null,
                    sharedContinuationResultId = null,
                    kind = EnvelopeKind.REGULAR,
                    derivedFromEnvelopeIdsJson = null,
                    todoMetaJson = null,
                )
            )
            val contId = "$envId-cont"
            contDao.insert(
                ContinuationEntity(
                    id = contId,
                    envelopeId = envId,
                    type = ContinuationType.URL_HYDRATE,
                    status = ContinuationStatus.SUCCEEDED,
                    inputUrl = urlValue,
                    scheduledAt = createdAt,
                    startedAt = createdAt,
                    completedAt = createdAt + 1000L,
                    attemptCount = 1,
                    failureReason = null,
                )
            )
            resultDao.insert(
                ContinuationResultEntity(
                    id = "$envId-res",
                    continuationId = contId,
                    envelopeId = envId,
                    producedAt = createdAt + 1000L,
                    title = null,
                    domain = urlDomain,
                    canonicalUrl = urlValue,
                    canonicalUrlHash = "$envId-hash",
                    excerpt = hydrated.take(160),
                    summary = hydrated,
                    summaryModel = modelLabel,
                )
            )
        }
    }

    private fun tokenise(text: String): Set<String> =
        text.lowercase(Locale.US)
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .toSet()

    private fun jaccard(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() && b.isEmpty()) return 1.0
        val inter = a.intersect(b).size
        val union = a.union(b).size
        return if (union == 0) 0.0 else inter.toDouble() / union.toDouble()
    }

    private fun pairwiseMinCosine(vectors: List<FloatArray>): Double? {
        if (vectors.size < 2) return null
        var min = Double.POSITIVE_INFINITY
        for (i in vectors.indices) {
            for (j in i + 1 until vectors.size) {
                val c = cosine(vectors[i], vectors[j]) ?: return null
                if (c < min) min = c
            }
        }
        return if (min == Double.POSITIVE_INFINITY) null else min
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double? {
        if (a.size != b.size || a.isEmpty()) return null
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            dot += (a[i] * b[i]).toDouble()
            na += (a[i] * a[i]).toDouble()
            nb += (b[i] * b[i]).toDouble()
        }
        if (na == 0.0 || nb == 0.0) return null
        return dot / (Math.sqrt(na) * Math.sqrt(nb))
    }

    private fun computeRatio(numerator: Int, denominator: Int): Double? =
        if (denominator <= 0) null else numerator.toDouble() / denominator.toDouble()

    companion object {
        private const val LOG_TAG = "ClusterEvalRunner"
        private const val FIXTURE_DIR = "fixtures/clusters"
    }
}
