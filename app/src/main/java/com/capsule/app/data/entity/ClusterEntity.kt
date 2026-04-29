package com.capsule.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.capsule.app.data.model.ClusterState
import com.capsule.app.data.model.ClusterType

/**
 * A cluster is an agent-detected grouping of intent envelopes that share
 * a temporal + topical signature (spec 002 amendment §FR-026..FR-040).
 *
 * Clusters are NOT derivatives of [IntentEnvelopeEntity] — they are their
 * own first-class entity. Member envelopes are linked via
 * [ClusterMemberEntity] with FK CASCADE so that envelope hard-deletes
 * cleanly remove cluster membership rows.
 *
 * Schema designed for v1.1 expansion: `clusterType` is open-ended; new
 * cluster types add via constraint relaxation, not migration.
 *
 * Per Principle IX (LLM Sovereignty): every cluster row carries the
 * [modelLabel] of the Nano build that produced its embeddings, so the
 * `ClusterDetectionWorker` modelLabel-gate (FR-030) can refuse to surface
 * clusters whose embeddings predate a firmware-pinned build.
 */
@Entity(
    tableName = "cluster",
    indices = [
        Index(value = ["state"], name = "idx_cluster_state"),
        Index(value = ["timeBucketStart", "timeBucketEnd"], name = "idx_cluster_time_bucket")
    ]
)
data class ClusterEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "cluster_type") val clusterType: ClusterType,
    val state: ClusterState,
    val timeBucketStart: Long,
    val timeBucketEnd: Long,
    val similarityScore: Float,
    @ColumnInfo(name = "model_label") val modelLabel: String,
    val createdAt: Long,
    val stateChangedAt: Long,
    val dismissedAt: Long? = null
)
