package com.capsule.app.data

import com.capsule.app.ai.ClusterSummariser
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.EnvelopeKind
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

/**
 * Spec 002 Phase 11 Block 13 / T163 — `:ml`-side delegate for the
 * `cluster.summarize` AppFunction. Mirrors [WeeklyDigestDelegate]'s
 * structure so the two derived-envelope paths stay shaped identically:
 * read corpus → compose → backend insert (atomic with audit) → state
 * transition → outcome string.
 *
 * Lifecycle:
 *  1. Caller (the `cluster.summarize` handler dispatched by
 *     [com.capsule.app.action.ActionHandlerRegistry]) invokes
 *     [summarizeCluster] over the binder with a [clusterId].
 *  2. We resolve the cluster + members via [ClusterRepository] /
 *     [com.capsule.app.data.dao.ClusterDao.byClusterIdWithMembers].
 *     Missing → `"FAILED:cluster_not_found"`.
 *  3. We transition SURFACED/TAPPED/FAILED → ACTING **before** the
 *     summariser runs (FR-035 forensics rule). Failure to transition
 *     (e.g. cluster already ACTED) → `"FAILED:invalid_state"`.
 *  4. Summariser returns `ClusterSummary?`. Null → transitionToFailed
 *     with `reason=summary_failed` → `"FAILED:summary_failed"`.
 *  5. On success: build a DERIVED envelope with `kind=DERIVED`,
 *     `derivedVia="cluster_summarize"` (spec 012 FR-012-011),
 *     `derivedFromEnvelopeIdsJson=member ids`, insert atomically with
 *     a `CLUSTER_SUMMARY_GENERATED` audit row, then transition cluster
 *     to ACTED (which writes a second `CLUSTER_ACTED` audit row).
 *     Outcome: `"GENERATED:<envelopeId>"`.
 *
 * Outcome strings match [WeeklyDigestDelegate.runWeeklyDigest] so the
 * AIDL surface stays uniform; callers parse `GENERATED:`/`SKIPPED:`/
 * `FAILED:` prefixes.
 */
class ClusterSummarizeDelegate(
    private val database: OrbitDatabase,
    private val backend: EnvelopeStorageBackend,
    private val clusterRepository: ClusterRepository,
    private val summariser: ClusterSummariser,
    private val auditWriter: AuditLogWriter,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val zoneIdProvider: () -> ZoneId = { ZoneId.systemDefault() }
) {

    private val clusterDao = database.clusterDao()
    private val auditDao = database.auditLogDao()

    suspend fun summarizeCluster(clusterId: String): String {
        val cwm = clusterDao.byClusterIdWithMembers(clusterId)
            ?: return "FAILED:cluster_not_found"

        // Forensics rule (FR-035): persist ACTING before Nano runs.
        clusterRepository.transitionToActing(clusterId)
            ?: return "FAILED:invalid_state"

        val summary = try {
            summariser.summarise(cwm)
        } catch (t: Throwable) {
            clusterRepository.transitionToFailed(clusterId, reason = "summariser_threw")
            return "FAILED:summariser_threw"
        }

        if (summary == null) {
            clusterRepository.transitionToFailed(clusterId, reason = "summary_failed")
            return "FAILED:summary_failed"
        }

        val now = clock()
        val zone = zoneIdProvider()
        val envelope = buildDerivedEntity(
            clusterId = clusterId,
            modelLabel = summary.model,
            bullets = summary.bullets,
            citations = summary.citations,
            zone = zone,
            now = now
        )

        val auditExtras = JSONObject().apply {
            put("clusterId", clusterId)
            put("derivedEnvelopeId", envelope.id)
            put("memberCount", summary.citations.size)
            put("modelLabel", summary.model)
            put("derivedVia", DERIVED_VIA)
        }
        val insertAudit = auditWriter.build(
            action = AuditAction.CLUSTER_SUMMARY_GENERATED,
            description = "Cluster summary derived envelope inserted",
            envelopeId = envelope.id,
            extraJson = auditExtras.toString()
        )

        try {
            backend.insertClusterSummaryTransaction(envelope, insertAudit)
        } catch (t: Throwable) {
            clusterRepository.transitionToFailed(clusterId, reason = "insert_failed")
            return "FAILED:insert_failed"
        }

        clusterRepository.transitionToActed(
            clusterId = clusterId,
            derivedEnvelopeId = envelope.id
        ) ?: run {
            // Should not happen — ACTING is the only state ACT_SUCCESS
            // is valid from, and we just transitioned there. Log a
            // defensive audit row but still return success to the
            // caller because the derived envelope IS persisted.
            writeDefensiveAudit(clusterId, envelope.id)
        }

        return "GENERATED:${envelope.id}"
    }

    private fun buildDerivedEntity(
        clusterId: String,
        modelLabel: String,
        bullets: List<String>,
        citations: Set<String>,
        zone: ZoneId,
        now: Long
    ): IntentEnvelopeEntity {
        // derivedFromEnvelopeIdsJson preserves member order via the
        // citations Set's iteration order (LinkedHashSet semantics from
        // the summariser's filter). Spec 012 readers parse this via the
        // same JSON tokenizer used by `cascadeDigestInvalidation`.
        val derivedJson = JSONArray().also { arr ->
            citations.forEach { arr.put(it) }
        }.toString()

        // textContent: bullets joined with `\n` so the diary card can
        // render them verbatim without re-parsing the LLM output.
        val joined = bullets.joinToString(separator = "\n")

        val nowZdt: ZonedDateTime = java.time.Instant.ofEpochMilli(now).atZone(zone)
        val syntheticState = StateSnapshot(
            appCategory = AppCategory.OTHER,
            activityState = ActivityState.UNKNOWN,
            tzId = zone.id,
            hourLocal = nowZdt.hour,
            dayOfWeekLocal = nowZdt.dayOfWeek.value
        )

        return IntentEnvelopeEntity(
            id = UUID.randomUUID().toString(),
            contentType = ContentType.TEXT,
            textContent = joined,
            imageUri = null,
            textContentSha256 = sha256(joined),
            intent = Intent.REFERENCE,
            intentConfidence = null,
            intentSource = IntentSource.AUTO_AMBIGUOUS,
            intentHistoryJson = "[]",
            state = syntheticState,
            createdAt = now,
            dayLocal = LocalDate.now(zone).toString(),
            kind = EnvelopeKind.DERIVED,
            derivedFromEnvelopeIdsJson = derivedJson,
            todoMetaJson = null,
            derivedVia = DERIVED_VIA
        )
    }

    private suspend fun writeDefensiveAudit(clusterId: String, envelopeId: String) {
        auditDao.insert(
            auditWriter.build(
                action = AuditAction.CLUSTER_SUMMARY_GENERATED,
                description = "Cluster ACT_SUCCESS rejected; derived envelope persisted",
                envelopeId = envelopeId,
                extraJson = JSONObject().apply {
                    put("clusterId", clusterId)
                    put("derivedEnvelopeId", envelopeId)
                    put("note", "act_success_rejected_after_persist")
                }.toString()
            )
        )
    }

    private fun sha256(text: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(text.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        /** Spec 012 FR-012-011 sentinel value. */
        const val DERIVED_VIA = "cluster_summarize"
    }
}
