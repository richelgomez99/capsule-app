package com.capsule.app.cluster

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.NanoLlmProvider
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.OrbitDatabase

/**
 * T129 (spec 002 amendment Phase 11) — daily cluster-detection
 * background scan. Runs at 03:00 local on charger + UNMETERED + not
 * battery-low (Principle IV — Privacy on Cellular). The thin worker
 * just constructs the dependency triple (DAOs, LLM, audit writer)
 * and delegates to [ClusterDetector.detect]; the algorithm itself is
 * tested in pure JVM via the composable core.
 *
 * Same single-process direct-DB pattern as
 * [com.capsule.app.continuation.SoftDeleteRetentionWorker]: the
 * worker runs in the WorkManager-default process and writes through
 * Room directly. The 03:00 local anchor + charging constraint keeps
 * us out of the way of the 07:00 capture-seal window even though
 * we're not running in `:ml`.
 *
 * Result mapping:
 * - [ClusterDetector.Outcome.Completed] → `Result.success()`
 * - [ClusterDetector.Outcome.Skipped]   → `Result.success()`
 *   (FR-030 silent-skip; the audit row is the only signal.)
 * - any uncaught throw                  → `Result.retry()`
 *   (capture-seal contention, transient Room exception, etc.)
 */
class ClusterDetectionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val db = OrbitDatabase.getInstance(applicationContext)
        val llm: LlmProvider = llmFactory()
        val detector = ClusterDetector(
            clusterDao = db.clusterDao(),
            auditLogDao = db.auditLogDao(),
            llm = llm,
            auditWriter = AuditLogWriter()
        )
        return runCatching { detector.detect() }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() }
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME: String = "orbit.cluster_detection"

        /**
         * Test seam — overridable to inject a fake/stub
         * [LlmProvider]. Production code uses the default
         * [NanoLlmProvider] which honours
         * [com.capsule.app.ai.LlmProviderDiagnostics.forceNanoUnavailable].
         */
        @Volatile
        var llmFactory: () -> LlmProvider = { NanoLlmProvider() }
    }
}
