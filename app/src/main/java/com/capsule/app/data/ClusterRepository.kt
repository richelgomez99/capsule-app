package com.capsule.app.data

import com.capsule.app.RuntimeFlags
import com.capsule.app.audit.AuditLogWriter
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
}
