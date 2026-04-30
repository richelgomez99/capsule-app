package com.capsule.app.data

import com.capsule.app.RuntimeFlags
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.cluster.ClusterStateMachine
import com.capsule.app.data.dao.AuditLogDao
import com.capsule.app.data.dao.ClusterDao
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.ClusterState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * Repository surface for cluster reads (spec 002 Phase 11 Block 5 / T133).
 *
 * Wraps [ClusterDao.observeSurfaced] with two read-side gates that the
 * DAO can't easily express:
 *
 *  1. **`RuntimeFlags.clusterEmitEnabled`** — runtime kill switch.
 *     When `false`, the repository emits an empty list regardless of
 *     what's stored. Lets the user (and the demo-day stage owner)
 *     hide all clusters without having to mutate the database.
 *
 *  2. **`RuntimeFlags.clusterModelLabelLock`** — per-row label gate
 *     (FR-030 read-side defence-in-depth). The worker already refuses
 *     to *write* clusters whose modelLabel doesn't match the lock; we
 *     filter the same way on read so a lock change after the fact
 *     instantly hides drifted rows.
 *
 * Surviving-member-count ≥ 3 (FR-037, "the card never lies") is
 * enforced inside [ClusterDao.observeSurfaced]; we don't second-guess
 * the DAO. State filter (only SURFACED/TAPPED/ACTING/ACTED/FAILED) is
 * also DAO-side.
 *
 * Constructor takes the DAO directly so JVM tests can drive an
 * in-memory Room without any service-layer plumbing. Production
 * wiring lives in `EnvelopeRepositoryService` (T135).
 */
class ClusterRepository(
    private val clusterDao: ClusterDao,
    private val auditLogDao: AuditLogDao? = null,
    private val auditWriter: AuditLogWriter = AuditLogWriter(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Observable list of cluster cards eligible for surfacing right
     * now. Emits whenever the underlying `cluster` / `cluster_member`
     * / `intent_envelope` rows change.
     *
     * Empty list when [RuntimeFlags.clusterEmitEnabled] is `false`.
     */
    fun observeSurfaced(): Flow<List<ClusterCardModel>> =
        clusterDao.observeSurfaced().map { rows ->
            // Block 10 review FU#2: emit when EITHER the user-facing
            // kill switch is on OR the debug stage-demo override is on.
            // Production default is `clusterEmitEnabled = false` so a
            // user has to opt in (or the demo device flips
            // `devClusterForceEmit`) before any cluster reaches the UI.
            if (!RuntimeFlags.clusterEmitEnabled && !RuntimeFlags.devClusterForceEmit) {
                return@map emptyList()
            }
            val lock = RuntimeFlags.clusterModelLabelLock
            rows
                .asSequence()
                .filter { it.cluster.modelLabel == lock }
                .map { row ->
                    row.cluster.toCardModel(
                        members = row.members.map { it.member }
                    )
                }
                .toList()
        }

    /**
     * Phase 11 Block 10 (T148 review FU#2) — user-driven dismiss for
     * the diary cluster card. Transitions the cluster row to
     * [ClusterState.DISMISSED], stamps `dismissedAt = now`, and writes
     * a `CLUSTER_DISMISSED` audit row with the prior state for
     * forensics. Idempotent: a no-op (returns `false`) when the
     * cluster is already terminal (DISMISSED / AGED_OUT / ACTED) or
     * the row no longer exists. Returns `true` only when this call
     * actually transitioned a row.
     *
     * The transition itself is intentionally optimistic: we don't ask
     * the [ClusterStateMachine] (T151) for permission. User dismiss is
     * always allowed regardless of current state — the only paths that
     * matter for the user are "this card is gone now" + audit row.
     */
    suspend fun markDismissed(clusterId: String): Boolean {
        val current = clusterDao.byId(clusterId) ?: return false
        // Already terminal — nothing to do, but don't write a duplicate audit.
        if (current.state == ClusterState.DISMISSED ||
            current.state == ClusterState.AGED_OUT ||
            current.state == ClusterState.ACTED
        ) {
            return false
        }
        val now = clock()
        clusterDao.updateState(
            id = clusterId,
            newState = ClusterState.DISMISSED.name,
            stateChangedAt = now,
            dismissedAt = now
        )
        auditLogDao?.insert(
            auditWriter.build(
                action = AuditAction.CLUSTER_DISMISSED,
                description = "Cluster dismissed by user",
                envelopeId = null,
                extraJson = JSONObject().apply {
                    put("clusterId", clusterId)
                    put("priorState", current.state.name)
                    put("trigger", "user_dismiss")
                }.toString()
            )
        )
        return true
    }

    /**
     * Phase 11 Block 10 (T152) — persist ACTING **before** Nano
     * inference begins. Spec 002 FR-035 requires the on-disk state to
     * already be ACTING when the summariser is invoked, so a crash
     * mid-inference leaves a forensics trail rather than a stuck
     * TAPPED row.
     *
     * Routes through [ClusterStateMachine] so we get the same
     * 'invalid trigger from current state' semantics as the worker
     * paths (e.g. you can't transition ACTED → ACTING). Returns the
     * resulting [ClusterState] on success or `null` if the cluster
     * doesn't exist / the transition is invalid.
     *
     * Audit: writes a `CLUSTER_ACTING` row with priorState +
     * triggeredBy=summariser_start.
     */
    suspend fun transitionToActing(clusterId: String): ClusterState? {
        val current = clusterDao.byId(clusterId) ?: return null
        val nextState = ClusterStateMachine.next(
            current.state,
            ClusterStateMachine.Trigger.START_ACTING
        ) ?: return null
        val now = clock()
        clusterDao.updateState(
            id = clusterId,
            newState = nextState.name,
            stateChangedAt = now,
            dismissedAt = null
        )
        auditLogDao?.insert(
            auditWriter.build(
                action = AuditAction.CLUSTER_ACTING,
                description = "Cluster transitioning to ACTING (summariser start)",
                envelopeId = null,
                extraJson = JSONObject().apply {
                    put("clusterId", clusterId)
                    put("priorState", current.state.name)
                    put("triggeredBy", "summariser_start")
                }.toString()
            )
        )
        return nextState
    }

    /**
     * Phase 11 Block 13 (T163) — terminal happy-path transition. Called
     * by [ClusterSummarizeDelegate] AFTER the derived envelope has been
     * persisted by `EnvelopeStorageBackend.insertClusterSummaryTransaction`.
     * Routes through [ClusterStateMachine.Trigger.ACT_SUCCESS] so the
     * ACTING → ACTED contract is enforced (callers cannot skip the
     * ACTING write that establishes the forensics trail).
     *
     * Audit: writes [AuditAction.CLUSTER_ACTED] with `derivedEnvelopeId`
     * so spec 012 readers can join the cluster row to the derived
     * envelope without a second query.
     */
    suspend fun transitionToActed(
        clusterId: String,
        derivedEnvelopeId: String
    ): ClusterState? {
        val current = clusterDao.byId(clusterId) ?: return null
        val nextState = ClusterStateMachine.next(
            current.state,
            ClusterStateMachine.Trigger.ACT_SUCCESS
        ) ?: return null
        val now = clock()
        clusterDao.updateState(
            id = clusterId,
            newState = nextState.name,
            stateChangedAt = now,
            dismissedAt = null
        )
        auditLogDao?.insert(
            auditWriter.build(
                action = AuditAction.CLUSTER_ACTED,
                description = "Cluster summarised → DERIVED envelope created",
                envelopeId = derivedEnvelopeId,
                extraJson = JSONObject().apply {
                    put("clusterId", clusterId)
                    put("priorState", current.state.name)
                    put("derivedEnvelopeId", derivedEnvelopeId)
                    put("triggeredBy", "summariser_success")
                }.toString()
            )
        )
        return nextState
    }

    /**
     * Phase 11 Block 13 (T163) — terminal failure transition. Called by
     * [ClusterSummarizeDelegate] when the summariser returns null
     * (validation failed, citations rejected, prompt sanitiser refused,
     * Nano errored). Routes through [ClusterStateMachine.Trigger.ACT_FAIL]
     * so the cluster lands in FAILED — eligible for one of up to
     * [ClusterStateMachine.MAX_FAILED_ACTING_RETRIES] retries via the
     * existing FAILED → ACTING path; the chip re-appears via the
     * UI's `observeSurfaced` filter (spec 002 FR-038).
     *
     * Audit: writes [AuditAction.CLUSTER_FAILED] with `reason` so the
     * forensics trail captures *why* (e.g. `summary_failed`,
     * `cluster_not_found`, `member_count_below_minimum`).
     */
    suspend fun transitionToFailed(
        clusterId: String,
        reason: String
    ): ClusterState? {
        val current = clusterDao.byId(clusterId) ?: return null
        val nextState = ClusterStateMachine.next(
            current.state,
            ClusterStateMachine.Trigger.ACT_FAIL
        ) ?: return null
        val now = clock()
        clusterDao.updateState(
            id = clusterId,
            newState = nextState.name,
            stateChangedAt = now,
            dismissedAt = null
        )
        auditLogDao?.insert(
            auditWriter.build(
                action = AuditAction.CLUSTER_FAILED,
                description = "Cluster summarisation failed",
                envelopeId = null,
                extraJson = JSONObject().apply {
                    put("clusterId", clusterId)
                    put("priorState", current.state.name)
                    put("reason", reason)
                }.toString()
            )
        )
        return nextState
    }
}
