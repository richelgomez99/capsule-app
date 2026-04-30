package com.capsule.app.cluster

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * T159 (spec/002 Block 10) — debug-only cluster eval harness.
 *
 * Loads the 20 hand-authored fixtures committed under T158
 * (`app/src/test/resources/fixtures/clusters/research-session-XX.json`,
 * mounted as debug-only assets via `app/build.gradle.kts` →
 * `sourceSets.debug.assets.srcDirs`). For each fixture the runner:
 *
 *   1. Parses the JSON envelope list + `expected_metadata`.
 *   2. Records the declared `expected_outcome`
 *      (`POSITIVE | REJECTED_LOW_COSINE | REJECTED_TIME_GAP | ...`).
 *   3. Aggregates corpus-level metrics: per-domain envelope counts,
 *      positive/negative ratio, declared `cosine_min_observed`
 *      distribution, distinct-domain spread.
 *   4. Writes a JSON report to the app's external files dir
 *      (`getExternalFilesDir(null)/cluster-eval-YYYYMMDD-HHmmss.json`)
 *      so the operator can `adb pull` it for off-device review.
 *   5. Prints a one-line summary to logcat and a multi-line summary
 *      to the on-device TextView.
 *
 * Invoking detection + summarisation against the fixtures is a Block 6
 * concern (`ClusterDetector` requires an in-memory `OrbitDatabase`
 * pre-seeded with the fixture envelopes plus a real `LlmProvider`
 * for embeddings). The hook point is [InvokeDetection.run]; it is a
 * no-op today and returns the corpus-level fixture-validation report
 * so the May 4 evaluation can be staged. Block 11 is expected to
 * replace [InvokeDetection.run] with a full pipeline run + golden-
 * summary token-overlap scoring + the `--calibrate` flag deferred per
 * `tasks.md` line 189.
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
            text = "Press \"Run eval\" to load the 20 cluster fixtures."
        }

        val runButton = Button(this).apply {
            text = "Run eval"
            setOnClickListener { runEval() }
        }

        val scroll = ScrollView(this).apply {
            addView(statusView)
        }

        root.addView(title)
        root.addView(runButton)
        root.addView(scroll)
        setContentView(root)
    }

    private fun runEval() {
        val report = try {
            evaluate()
        } catch (t: Throwable) {
            Log.e(LOG_TAG, "eval failed", t)
            statusView.text = "FAILED: ${t.javaClass.simpleName}: ${t.message}"
            return
        }

        val outFile = writeReport(report)
        val summary = buildString {
            append("Eval complete.\n")
            append("Fixtures parsed: ${report.optInt("fixturesParsed")}\n")
            append("Positive: ${report.optInt("positiveCount")}\n")
            append("Negative: ${report.optInt("negativeCount")}\n")
            append("Domains: ${report.optJSONObject("domainCounts")}\n")
            append("Report written to: ${outFile.absolutePath}\n")
        }
        statusView.text = summary
        Log.i(LOG_TAG, summary)
    }

    private fun evaluate(): JSONObject {
        val fixtures = loadFixtures()
        val perDomain = mutableMapOf<String, Int>()
        val outcomes = mutableMapOf<String, Int>()
        val cosineSamples = mutableListOf<Double>()
        val perFixtureReport = JSONArray()

        var positiveCount = 0
        var negativeCount = 0

        for (fx in fixtures) {
            val id = fx.optString("id")
            val domain = fx.optString("domain", "unknown")
            perDomain.merge(domain, 1, Int::plus)

            val meta = fx.optJSONObject("expected_metadata") ?: JSONObject()
            val outcome = meta.optString("expected_outcome", "UNKNOWN")
            outcomes.merge(outcome, 1, Int::plus)
            if (outcome == "POSITIVE") positiveCount++ else negativeCount++

            val cosine = meta.optDouble("cosine_min_observed", Double.NaN)
            if (!cosine.isNaN()) cosineSamples += cosine

            // Per-fixture passthrough — Block 11 replaces this with
            // detection + summariser results + token-overlap drift.
            perFixtureReport.put(
                JSONObject().apply {
                    put("id", id)
                    put("domain", domain)
                    put("expected_outcome", outcome)
                    put("envelope_count", fx.optJSONArray("envelopes")?.length() ?: 0)
                    put("cosine_min_observed", cosine.takeUnless { it.isNaN() } ?: JSONObject.NULL)
                    // Detection / summariser hook (Block 11):
                    put("detection_result", InvokeDetection.run(fx))
                }
            )
        }

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
            put("perFixture", perFixtureReport)
        }
    }

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

    private fun writeReport(report: JSONObject): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US)
            .format(Date())
        val dir = getExternalFilesDir(null) ?: filesDir
        val out = File(dir, "cluster-eval-$stamp.json")
        out.writeText(report.toString(2))
        return out
    }

    /**
     * Block 11 hook — invoked once per fixture. Today returns a
     * placeholder JSON object so the runner contract is stable; Block
     * 11 wires `ClusterDetector.detect()` against an in-memory
     * OrbitDatabase seeded with the fixture envelopes + a real Nano
     * embedder, plus token-overlap scoring against
     * `expectedSummary.bullets`.
     */
    private object InvokeDetection {
        fun run(fixture: JSONObject): JSONObject = JSONObject().apply {
            put("status", "DEFERRED_TO_BLOCK_11")
            put("reason", "ClusterSummariser + Nano embedder wiring not yet landed")
        }
    }

    companion object {
        private const val LOG_TAG = "ClusterEvalRunner"
        private const val FIXTURE_DIR = "fixtures/clusters"
    }
}
