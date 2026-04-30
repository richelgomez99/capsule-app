package com.capsule.app.cluster

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * T160 (spec/002 Phase 11) — JVM well-formedness check on the 20
 * cluster eval fixtures committed under T158
 * (`app/src/test/resources/fixtures/clusters/research-session-XX.json`).
 *
 * Catches schema drift at compile time so the May-4 device pass never
 * blows up against a malformed fixture. Branches on
 * `expected_metadata.expected_outcome`:
 *
 *  - **POSITIVE**: ≥3 envelopes, ≥2 distinct `url_domain` values, 1-3
 *    `expectedSummary.bullets` (matching `ClusterSummariser`'s
 *    contract per spec line 1100 — array length 1-3), every bullet
 *    carries at least one citation token of the form
 *    `(env-XX-y[, env-XX-z]*)`.
 *  - **NEGATIVE** (any `REJECTED_*` outcome): ≥3 envelopes (FR-028's
 *    member floor still applies for shape parity), `expectedSummary` is
 *    permitted to carry the placeholder `[NEGATIVE FIXTURE — ...]` block
 *    today (cleanup item #1 in tasks.md → v1.1).
 *
 * Every fixture must additionally:
 *  - declare `expected_metadata.expected_outcome` as one of the known
 *    outcomes;
 *  - declare `expected_metadata.cosine_min_observed` as a finite double;
 *  - have ISO-8601 `captured_at` strings on each envelope;
 *  - have a non-blank `hydrated_text` on each envelope.
 */
class ClusterEvalCorpusTest {

    // Block 12 cleanup carry-over (2026-04-29): JUnit's working directory is
    // the module root when run from Gradle, but the IDE may invoke the test
    // with the workspace root as cwd. Try the classpath resource first
    // (works in both), fall back to module-relative for legacy CLI runs.
    private val fixtureDir: File = run {
        val viaClasspath = javaClass.getResource("/fixtures/clusters")?.toURI()?.let(::File)
        when {
            viaClasspath != null && viaClasspath.isDirectory -> viaClasspath
            File("src/test/resources/fixtures/clusters").isDirectory ->
                File("src/test/resources/fixtures/clusters")
            else -> File("app/src/test/resources/fixtures/clusters")
        }
    }
    // If a future fixture introduces REJECTED_NO_HYDRATION or any other
    // rejection class, this test breaks intentionally — expand the enum,
    // then re-run.
    private val knownOutcomes = setOf(
        "POSITIVE",
        "REJECTED_SINGLE_DOMAIN",
        "REJECTED_4H_BUCKET",
        "REJECTED_LOW_COSINE",
        "REJECTED_TOO_FEW_CAPTURES",
    )
    private val citationToken = Regex("""\(env-[a-z0-9-]+(?:,\s*env-[a-z0-9-]+)*\)""", RegexOption.IGNORE_CASE)
    private val isoInstant = Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?Z""")

    @Test
    fun fixtureDirectoryExistsAndHasTwentyFiles() {
        assertTrue("fixtureDir should exist: ${fixtureDir.absolutePath}", fixtureDir.isDirectory)
        val files = fixtureDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
        assertEquals("expected 20 fixtures", 20, files.size)
    }

    @Test
    fun everyFixtureIsWellFormed() {
        val failures = mutableListOf<String>()
        val files = fixtureDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
        assertTrue("must have fixtures to validate", files.isNotEmpty())

        for (file in files) {
            val ctx = file.name
            val json = try {
                JSONObject(file.readText())
            } catch (t: Throwable) {
                failures += "$ctx: not valid JSON (${t.message})"
                continue
            }

            val id = json.optString("id")
            if (id.isBlank()) failures += "$ctx: missing/blank id"

            // ---- expected_metadata block ----
            val meta = json.optJSONObject("expected_metadata")
            if (meta == null) {
                failures += "$ctx: missing expected_metadata block"
                continue
            }
            val outcome = meta.optString("expected_outcome")
            if (outcome !in knownOutcomes) {
                failures += "$ctx: unknown expected_outcome=$outcome"
            }
            val cosine = meta.optDouble("cosine_min_observed", Double.NaN)
            if (cosine.isNaN()) {
                failures += "$ctx: missing/non-numeric cosine_min_observed"
            }

            // ---- envelopes ----
            val envelopes = json.optJSONArray("envelopes")
            if (envelopes == null || envelopes.length() < 3) {
                failures += "$ctx: <3 envelopes"
                continue
            }
            val domains = mutableSetOf<String>()
            for (i in 0 until envelopes.length()) {
                val e = envelopes.getJSONObject(i)
                if (e.optString("envelope_id").isBlank()) {
                    failures += "$ctx#env[$i]: missing envelope_id"
                }
                val captured = e.optString("captured_at")
                if (!isoInstant.matches(captured)) {
                    failures += "$ctx#env[$i]: captured_at not ISO-8601 instant ($captured)"
                }
                if (e.optString("hydrated_text").isBlank()) {
                    failures += "$ctx#env[$i]: hydrated_text is blank"
                }
                val dom = e.optString("url_domain").ifBlank { e.optString("domain") }
                if (dom.isNotBlank()) domains += dom
            }

            // ---- expectedSummary, branched on outcome ----
            val summary = json.optJSONObject("expectedSummary")
            val bullets = summary?.optJSONArray("bullets")
            if (outcome == "POSITIVE") {
                if (domains.size < 2) {
                    failures += "$ctx: POSITIVE fixture has <2 distinct url_domain values (got ${domains.size})"
                }
                if (bullets == null || bullets.length() !in 1..3) {
                    failures += "$ctx: POSITIVE fixture must have 1-3 expectedSummary.bullets (got ${bullets?.length() ?: 0})"
                } else {
                    for (i in 0 until bullets.length()) {
                        val text = bullets.getString(i)
                        if (!citationToken.containsMatchIn(text)) {
                            failures += "$ctx#bullet[$i]: missing (env-...) citation token"
                        }
                    }
                }
            } else {
                // Negative fixtures still must declare a summary block so the
                // eval runner's loader can branch uniformly. Placeholder
                // bullets allowed (tracked under v1.1 cleanup #1).
                assertNotNull("$ctx: NEGATIVE fixture must still declare expectedSummary block", summary)
            }
        }

        if (failures.isNotEmpty()) {
            throw AssertionError(
                "Fixture corpus validity failures:\n  - " +
                    failures.joinToString("\n  - ")
            )
        }
    }
}
