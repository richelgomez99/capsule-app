package com.capsule.app.cluster

import android.util.Log
import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.NanoLlmProvider
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.dao.AuditLogDao
import com.capsule.app.data.dao.ClusterCandidateRow
import com.capsule.app.data.dao.ClusterDao
import com.capsule.app.data.entity.ClusterEntity
import com.capsule.app.data.entity.ClusterMemberEntity
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.ClusterState
import com.capsule.app.data.model.ClusterType
import com.capsule.app.data.model.ContinuationType
import org.json.JSONObject
import java.util.UUID

/**
 * T129 (spec 002 amendment Phase 11) — composable core of the
 * `ClusterDetectionWorker`. Pulled into its own class so the JVM
 * unit-test suite (T130) can exercise the algorithm with fakes
 * without spinning up Room or WorkManager.
 *
 * Pipeline (one [detect] call):
 *   1. **modelLabel boundary gate (FR-030)** — read
 *      [com.capsule.app.RuntimeFlags.clusterModelLabelLock] and
 *      compare against the current Nano build. On mismatch we emit a
 *      `CONTINUATION_COMPLETED` audit row (`outcome=skipped,
 *      reason=model_label_mismatch`) and exit without touching the
 *      candidate query. This is the firmware-drift safety check that
 *      stops a half-pinned device from mixing embedding generations
 *      inside one cluster.
 *   2. **Pull candidates** via
 *      [ClusterDao.findClusterCandidates]. The query already filters
 *      to surviving (non-deleted, non-archived) envelopes hydrated
 *      with a sufficiently long summary, and excludes envelopes
 *      already attached to a cluster (clusters are append-only in
 *      v1.1).
 *   3. **Embed each candidate** via [LlmProvider.embed]. Nulls drop
 *      out — graceful-degrade by Block 2 contract.
 *   4. **Bucket by 4h windows** via
 *      [SimilarityEngine.bucket4h] — greedy session grouping anchored
 *      on each unbucketed capture's timestamp (FR-027).
 *   5. **Apply the cluster predicate** (`isCluster`) to every bucket
 *      with ≥ [SimilarityEngine.Thresholds.MIN_SIZE] members. Buckets
 *      that pass land in [ClusterDao.insertWithMembers] inside a
 *      single Room transaction; buckets that fail get dropped (no
 *      audit row — only formed clusters are auditable surfaces per
 *      Phase 11 amendment §9).
 *   6. **Audit** — one [AuditAction.CLUSTER_FORMED] row per cluster
 *      written, plus one terminal [AuditAction.CONTINUATION_COMPLETED]
 *      row with the run summary and `ContinuationType.CLUSTER_DETECT`.
 *
 * Per-pass guarantees:
 * - **Atomic**: each cluster + its members lands in a single Room
 *   transaction via [ClusterDao.insertWithMembers]; if the worker
 *   process dies mid-pass, no partial cluster persists.
 * - **No write-while-iterating**: the candidate read finishes before
 *   the embed loop starts, so the Room write transaction never
 *   overlaps the embedding work and the 7am capture-seal can't block
 *   on us (FR-029).
 * - **Idempotent**: clusters already on disk stay on disk; the
 *   candidate query excludes already-clustered envelope ids so a
 *   re-run on the same window is a no-op.
 *
 * Newly-written clusters land in [ClusterState.SURFACED] directly —
 * the FORMING state is internal scratch space (FR-012-029). Block 9
 * (state machine) governs subsequent transitions.
 */
class ClusterDetector(
    private val clusterDao: ClusterDao,
    private val auditLogDao: AuditLogDao,
    private val llm: LlmProvider,
    private val auditWriter: AuditLogWriter = AuditLogWriter(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val idGen: () -> String = { UUID.randomUUID().toString() },
    private val currentModelLabel: () -> String = { NanoLlmProvider.MODEL_LABEL },
    private val modelLabelLock: () -> String = { com.capsule.app.RuntimeFlags.clusterModelLabelLock },
    private val lookbackMillis: Long = DEFAULT_LOOKBACK_MILLIS,
    private val minSummaryLength: Int = DEFAULT_MIN_SUMMARY_LENGTH
) {

    /** Outcome of one [detect] pass. Worker maps to `Result.success`. */
    sealed class Outcome {
        data object Skipped : Outcome()
        data class Completed(
            val clustersFormed: Int,
            val candidatesScanned: Int,
            val embeddingsObtained: Int,
            val bucketsConsidered: Int,
            val bucketsSkippedRevDrift: Int = 0
        ) : Outcome()
    }

    /**
     * Thrown by [detect] when [SimilarityEngine.cosine] surfaces an
     * [EmbeddingDimensionMismatch]. The worker maps this to
     * `Result.failure()` per FR-038 — a dimensionality mismatch is a
     * permanent / structural error that retrying cannot fix.
     */
    class DimensionMismatchInRun(cause: EmbeddingDimensionMismatch) :
        IllegalStateException("cluster detection aborted: dimension mismatch", cause)

    suspend fun detect(): Outcome {
        // (1) modelLabel boundary gate — FR-030.
        val current = currentModelLabel()
        val lock = modelLabelLock()
        if (current != lock) {
            auditLogDao.insert(
                auditWriter.build(
                    action = AuditAction.CONTINUATION_COMPLETED,
                    description = "Cluster detection skipped (model_label_mismatch)",
                    envelopeId = null,
                    extraJson = JSONObject().apply {
                        put("type", ContinuationType.CLUSTER_DETECT.name)
                        put("outcome", "skipped")
                        put("reason", "model_label_mismatch")
                        put("currentModelLabel", current)
                        put("clusterModelLabelLock", lock)
                    }.toString()
                )
            )
            return Outcome.Skipped
        }

        // (2) Pull candidates from the read snapshot.
        val sinceMillis = clock() - lookbackMillis
        val candidates: List<ClusterCandidateRow> =
            clusterDao.findClusterCandidates(sinceMillis, minSummaryLength)
        if (candidates.isEmpty()) {
            return finishCompleted(
                clustersFormed = 0,
                candidatesScanned = 0,
                embeddingsObtained = 0,
                bucketsConsidered = 0
            )
        }

        // (3) Embed every candidate; nulls (blank text, AICore
        // unavailable, transient throw) drop out by Block 2 contract.
        val embeddings = HashMap<String, FloatArray>(candidates.size)
        val perEnvelopeModelLabel = HashMap<String, String>(candidates.size)
        val embeddedCandidates = ArrayList<ClusterCandidate>(candidates.size)
        for (row in candidates) {
            val text = row.summary
            val emb = try {
                llm.embed(text)
            } catch (_: Throwable) {
                null
            }
            if (emb != null) {
                embeddings[row.envelopeId] = emb.vector
                perEnvelopeModelLabel[row.envelopeId] = emb.modelLabel
                embeddedCandidates += ClusterCandidate(
                    id = row.envelopeId,
                    createdAtMillis = row.createdAtMillis,
                    domain = row.domain,
                    text = text
                )
            }
        }

        // (4) Bucket the embedded set into 4h sessions.
        val buckets = SimilarityEngine.bucket4h(embeddedCandidates)

        // (5) Evaluate every viable bucket; persist passing ones.
        val now = clock()
        var formed = 0
        var revDriftSkips = 0
        for ((anchor, members) in buckets) {
            if (members.size < SimilarityEngine.Thresholds.MIN_SIZE) continue

            // FR-038 per-bucket model-rev guard. If the embeddings in
            // this bucket disagree on modelLabel (e.g. router rolled to
            // a new build mid-run) we cannot legally compute cosine
            // across them. Skip with one bounded log line — never log
            // text, vectors, or hostnames (Principle XIV).
            val bucketLabels = members.mapNotNull { perEnvelopeModelLabel[it.id] }.toSet()
            if (bucketLabels.size > 1) {
                logI(
                    LOG_TAG,
                    "cluster_skip_model_rev_drift bucketAnchor=$anchor labels=${bucketLabels.size} members=${members.size}"
                )
                revDriftSkips += 1
                continue
            }
            // Stamp the cluster with the bucket's actual model label
            // (from the embedding response) — falls back to the local
            // currentModelLabel only when the bucket lookup is empty,
            // which can't happen post-guard but keeps the type total.
            val bucketModelLabel = bucketLabels.singleOrNull() ?: current

            val passes = try {
                SimilarityEngine.isCluster(members, embeddings)
            } catch (e: EmbeddingDimensionMismatch) {
                // FR-038: dimensionality mismatch is structural — abort
                // the run so the worker can return Result.failure().
                throw DimensionMismatchInRun(e)
            }
            if (!passes) continue

            val similarity = SimilarityEngine.averagePairwiseCosine(members, embeddings)
            val clusterId = idGen()
            val cluster = ClusterEntity(
                id = clusterId,
                clusterType = ClusterType.RESEARCH_SESSION,
                state = ClusterState.SURFACED,
                timeBucketStart = anchor,
                timeBucketEnd = anchor + SimilarityEngine.Thresholds.BUCKET_WIDTH_MS,
                similarityScore = similarity,
                modelLabel = bucketModelLabel,
                createdAt = now,
                stateChangedAt = now,
                dismissedAt = null
            )
            val memberRows = members.mapIndexed { idx, c ->
                ClusterMemberEntity(
                    clusterId = clusterId,
                    envelopeId = c.id,
                    memberIndex = idx
                )
            }

            // One transaction per cluster — if the next loop iteration
            // crashes the prior cluster is durably on disk.
            clusterDao.insertWithMembers(cluster, memberRows)
            auditLogDao.insert(
                auditWriter.build(
                    action = AuditAction.CLUSTER_FORMED,
                    description = "Cluster formed (${members.size} members, " +
                        "similarity=${"%.3f".format(similarity)})",
                    envelopeId = null,
                    extraJson = JSONObject().apply {
                        put("clusterId", clusterId)
                        put("memberCount", members.size)
                        put("similarityScore", similarity)
                        put("modelLabel", bucketModelLabel)
                        put("timeBucketStart", anchor)
                    }.toString()
                )
            )
            formed += 1
        }

        logI(
            LOG_TAG,
            "cluster_detection_done clusters_detected=$formed envelopes_scanned=${candidates.size} " +
                "embeddings=${embeddings.size} buckets=${buckets.size} rev_drift_skips=$revDriftSkips"
        )

        return finishCompleted(
            clustersFormed = formed,
            candidatesScanned = candidates.size,
            embeddingsObtained = embeddings.size,
            bucketsConsidered = buckets.size,
            bucketsSkippedRevDrift = revDriftSkips
        )
    }

    private suspend fun finishCompleted(
        clustersFormed: Int,
        candidatesScanned: Int,
        embeddingsObtained: Int,
        bucketsConsidered: Int,
        bucketsSkippedRevDrift: Int = 0
    ): Outcome.Completed {
        auditLogDao.insert(
            auditWriter.build(
                action = AuditAction.CONTINUATION_COMPLETED,
                description = "Cluster detection completed " +
                    "(formed=$clustersFormed, scanned=$candidatesScanned)",
                envelopeId = null,
                extraJson = JSONObject().apply {
                    put("type", ContinuationType.CLUSTER_DETECT.name)
                    put("outcome", "ok")
                    put("clustersFormed", clustersFormed)
                    put("candidatesScanned", candidatesScanned)
                    put("embeddingsObtained", embeddingsObtained)
                    put("bucketsConsidered", bucketsConsidered)
                    put("bucketsSkippedRevDrift", bucketsSkippedRevDrift)
                }.toString()
            )
        )
        return Outcome.Completed(
            clustersFormed = clustersFormed,
            candidatesScanned = candidatesScanned,
            embeddingsObtained = embeddingsObtained,
            bucketsConsidered = bucketsConsidered,
            bucketsSkippedRevDrift = bucketsSkippedRevDrift
        )
    }

    companion object {
        private const val LOG_TAG: String = "ClusterDetector"

        /**
         * 24-hour lookback per Phase 11 Block 4 cost discipline. The
         * worker runs daily; combined with [ClusterDao.findClusterCandidates]
         * excluding already-clustered envelopes, this bounds embed
         * cost to roughly one call per surviving envelope per pass
         * (≤2 calls in the worst overlap case at the bucket boundary).
         */
        const val DEFAULT_LOOKBACK_MILLIS: Long = 24L * 60L * 60L * 1000L

        /**
         * Floor at ~64 tokens of summary text. The cluster engine
         * embeds summaries (not raw OCR), so 64 tokens ≈ 256 chars
         * is the cheap-rule proxy until we wire real tokenization.
         * FR-026 requires "≥ 64 tokens of hydrated body" before a
         * capture is eligible for clustering.
         */
        const val DEFAULT_MIN_SUMMARY_LENGTH: Int = 256

        /**
         * Wraps [android.util.Log.i] so JVM unit tests (where
         * `android.util.Log` is the not-mocked stub) don't raise.
         * Production behaviour on-device is unchanged.
         */
        private fun logI(tag: String, message: String) {
            try {
                Log.i(tag, message)
            } catch (_: Throwable) {
                // Test JVM — `android.util.Log` is the unmocked Android stub.
            }
        }
    }
}
