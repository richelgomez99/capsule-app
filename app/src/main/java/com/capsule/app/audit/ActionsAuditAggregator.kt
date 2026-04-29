package com.capsule.app.audit

import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.model.AuditAction

/**
 * T096 — pure aggregation of action-related audit rows for the
 * "What Orbit did today" surface in the audit log viewer.
 *
 * Stateless and deterministic — input is the raw row list (ordered
 * however the DAO returns them), output is a structured summary keyed
 * by the action category. UI layers (spec 010 finalises typography)
 * format the resulting buckets.
 *
 * Per data-model.md §6 closing paragraph:
 *  - Group ACTION_PROPOSED → ACTION_CONFIRMED → ACTION_EXECUTED rows
 *    by `extraJson.proposalId` so a single proposal lifecycle renders
 *    as one entry rather than 3-4 separate rows.
 *  - Aggregate ACTION_DISMISSED, ACTION_FAILED counts by reason.
 *  - Surface DIGEST_GENERATED + DIGEST_SKIPPED as standalone entries.
 */
object ActionsAuditAggregator {

    /** A summarised view of action-related audit rows over a window. */
    data class Summary(
        val proposalLifecycles: List<ProposalLifecycle>,
        val dismissalReasons: Map<String, Int>,
        val failureReasons: Map<String, Int>,
        val digestRuns: List<DigestRun>,
        val invalidations: Int,
    )

    /**
     * One end-to-end proposal lifecycle. `terminal` carries the final
     * outcome we observed — typically ACTION_EXECUTED, ACTION_DISMISSED,
     * or ACTION_FAILED. `null` means the lifecycle is still open
     * (proposed but not yet confirmed/dismissed at the window edge).
     */
    data class ProposalLifecycle(
        val proposalId: String,
        val functionId: String?,
        val firstSeenAt: Long,
        val terminal: AuditAction?,
        val terminalReason: String?,
    )

    data class DigestRun(
        val at: Long,
        val action: AuditAction,
        val reason: String?,
        val envelopeCount: Int?,
    )

    fun aggregate(rows: List<AuditLogEntryEntity>): Summary {
        val byProposal = LinkedHashMap<String, MutableList<AuditLogEntryEntity>>()
        val dismissals = mutableMapOf<String, Int>()
        val failures = mutableMapOf<String, Int>()
        val digestRuns = mutableListOf<DigestRun>()
        var invalidations = 0

        for (row in rows.sortedBy { it.at }) {
            val extras = parseExtras(row.extraJson)
            val pid = extras["proposalId"]
            when (row.action) {
                AuditAction.ACTION_PROPOSED,
                AuditAction.ACTION_CONFIRMED,
                AuditAction.ACTION_EXECUTED -> {
                    if (pid != null) byProposal.getOrPut(pid) { mutableListOf() }.add(row)
                }
                AuditAction.ACTION_DISMISSED -> {
                    if (pid != null) byProposal.getOrPut(pid) { mutableListOf() }.add(row)
                    val reason = extras["reason"] ?: "user_swipe"
                    dismissals[reason] = (dismissals[reason] ?: 0) + 1
                }
                AuditAction.ACTION_FAILED -> {
                    if (pid != null) byProposal.getOrPut(pid) { mutableListOf() }.add(row)
                    val reason = extras["reason"] ?: "unknown"
                    failures[reason] = (failures[reason] ?: 0) + 1
                }
                AuditAction.DIGEST_GENERATED,
                AuditAction.DIGEST_SKIPPED -> {
                    val count = extras["envelopeCount"]?.toIntOrNull()
                    digestRuns.add(DigestRun(row.at, row.action, extras["reason"], count))
                }
                AuditAction.ENVELOPE_INVALIDATED -> invalidations += 1
                else -> Unit
            }
        }

        val lifecycles = byProposal.map { (proposalId, entries) ->
            val sorted = entries.sortedBy { it.at }
            val first = sorted.first()
            val terminalRow = sorted.lastOrNull { it.action != AuditAction.ACTION_PROPOSED }
            val terminal = terminalRow?.action
            val terminalReason = terminalRow?.let { parseExtras(it.extraJson)["reason"] }
            val functionId = sorted
                .asSequence()
                .mapNotNull { parseExtras(it.extraJson)["functionId"] }
                .firstOrNull()
            ProposalLifecycle(
                proposalId = proposalId,
                functionId = functionId,
                firstSeenAt = first.at,
                terminal = terminal,
                terminalReason = terminalReason
            )
        }

        return Summary(
            proposalLifecycles = lifecycles,
            dismissalReasons = dismissals.toMap(),
            failureReasons = failures.toMap(),
            digestRuns = digestRuns.toList(),
            invalidations = invalidations
        )
    }

    /**
     * Lightweight flat parser — avoids pulling org.json onto unit-test
     * classpaths. Handles top-level string/number values only, which is
     * the entire contract surface of the action audit extraJson rows.
     */
    private fun parseExtras(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        // Optimistically use org.json — Android brings it for free, JVM
        // tests have it on the kotlin-test classpath via :app's deps.
        return runCatching {
            val obj = org.json.JSONObject(json)
            buildMap {
                val it = obj.keys()
                while (it.hasNext()) {
                    val k = it.next()
                    val v = obj.opt(k)
                    if (v != null) put(k, v.toString())
                }
            }
        }.getOrDefault(emptyMap())
    }
}
