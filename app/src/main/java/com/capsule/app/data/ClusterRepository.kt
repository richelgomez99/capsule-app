package com.capsule.app.data

import com.capsule.app.RuntimeFlags
import com.capsule.app.data.dao.ClusterDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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
    private val clusterDao: ClusterDao
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
            if (!RuntimeFlags.clusterEmitEnabled) return@map emptyList()
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
}
