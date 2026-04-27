package com.capsule.app.data.dao

import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Relation
import androidx.room.Transaction
import com.capsule.app.data.entity.ClusterEntity
import com.capsule.app.data.entity.ClusterMemberEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for [ClusterEntity] + [ClusterMemberEntity] (spec 002 amendment
 * Phase 11 T119).
 *
 * The `observeSurfaced(...)` query enforces FR-037 ("the card never
 * lies"): a cluster only emits to the UI when its surviving-member
 * count is ≥ 3, so a cluster whose envelopes have been deleted past
 * the orphan threshold disappears even before the next
 * `SoftDeleteRetentionWorker` pass dismisses it.
 */
@Dao
interface ClusterDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCluster(cluster: ClusterEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMembers(members: List<ClusterMemberEntity>)

    @Transaction
    suspend fun insertWithMembers(cluster: ClusterEntity, members: List<ClusterMemberEntity>) {
        insertCluster(cluster)
        if (members.isNotEmpty()) insertMembers(members)
    }

    /**
     * State transition. Used by [ClusterStateMachine] callers — pass
     * the full new state plus the wall-clock instant the transition
     * happened. The optional `dismissedAt` is set when transitioning
     * to DISMISSED/AGED_OUT; null otherwise.
     */
    @Query(
        """
        UPDATE cluster
        SET state = :newState,
            stateChangedAt = :stateChangedAt,
            dismissedAt = :dismissedAt
        WHERE id = :id
        """
    )
    suspend fun updateState(
        id: String,
        newState: String,
        stateChangedAt: Long,
        dismissedAt: Long?
    )

    @Query("SELECT * FROM cluster WHERE id = :id")
    suspend fun byId(id: String): ClusterEntity?

    /**
     * Single-cluster fetch with hydrated members + envelopes for the
     * Summarize handler.
     */
    @Transaction
    @Query("SELECT * FROM cluster WHERE id = :id")
    suspend fun byClusterIdWithMembers(id: String): ClusterWithMembers?

    /**
     * Surfaceable clusters for today's diary view. Filters:
     *  1. State ∈ {SURFACED, TAPPED, ACTING, ACTED, FAILED} — the
     *     5 states the UI may render. FORMING is internal;
     *     DISMISSED / AGED_OUT are terminal-hidden.
     *  2. Surviving (non-deleted, non-archived) member count ≥ 3
     *     per FR-037.
     *
     * `dayLocalIso` filters to today's date in local time so the
     * Diary scrolls correctly across day boundaries.
     */
    @Transaction
    @Query(
        """
        SELECT c.* FROM cluster c
        WHERE c.state IN ('SURFACED','TAPPED','ACTING','ACTED','FAILED')
          AND (
            SELECT COUNT(*) FROM cluster_member cm
            INNER JOIN intent_envelope e ON e.id = cm.envelopeId
            WHERE cm.clusterId = c.id
              AND e.isDeleted = 0
              AND e.isArchived = 0
          ) >= 3
        ORDER BY c.timeBucketEnd DESC
        """
    )
    fun observeSurfaced(): Flow<List<ClusterWithMembers>>

    /**
     * Orphan-cleanup batch (T153) — returns clusters in non-terminal
     * states whose surviving-member count has fallen below 3, so the
     * `SoftDeleteRetentionWorker` can DISMISS them with reason=orphaned.
     */
    @Query(
        """
        SELECT c.id FROM cluster c
        WHERE c.state IN ('SURFACED','TAPPED','ACTING','ACTED','FAILED')
          AND (
            SELECT COUNT(*) FROM cluster_member cm
            INNER JOIN intent_envelope e ON e.id = cm.envelopeId
            WHERE cm.clusterId = c.id
              AND e.isDeleted = 0
              AND e.isArchived = 0
          ) < 3
        """
    )
    suspend fun findOrphaned(): List<String>

    /** T093/T105 dump for debug + export. */
    @Query("SELECT * FROM cluster ORDER BY createdAt DESC")
    suspend fun listAll(): List<ClusterEntity>

    @Query("SELECT state, COUNT(*) AS n FROM cluster GROUP BY state")
    suspend fun countByState(): List<ClusterStateCount>
}

data class ClusterStateCount(val state: String, val n: Int)

/**
 * Materialised projection: one cluster + the (cluster_member, envelope)
 * pairs that compose it. Members already filtered upstream by the
 * surviving-member-count gate; consumers must still skip any member
 * whose `envelope.isDeleted == true` if they observe between gate
 * passes.
 */
data class ClusterWithMembers(
    @Embedded val cluster: ClusterEntity,
    @Relation(
        entity = ClusterMemberEntity::class,
        parentColumn = "id",
        entityColumn = "clusterId"
    )
    val members: List<ClusterMemberWithEnvelope>
)

data class ClusterMemberWithEnvelope(
    @Embedded val member: ClusterMemberEntity,
    @Relation(
        parentColumn = "envelopeId",
        entityColumn = "id"
    )
    val envelope: IntentEnvelopeEntity?
)
