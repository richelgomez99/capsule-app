package com.capsule.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

/**
 * Junction row linking a [ClusterEntity] to one of its constituent
 * [IntentEnvelopeEntity] members (spec 002 amendment §FR-026..FR-040).
 *
 * Composite primary key on (clusterId, envelopeId). FK CASCADE on BOTH
 * parent tables is critical:
 *
 * - When a cluster is hard-deleted, all member rows go.
 * - When an envelope is hard-deleted (purge or `EnvelopePurgeWorker`),
 *   its cluster-member rows go too. The remaining count drives the
 *   FR-038 orphan-DISMISS rule (cluster auto-dismisses when surviving
 *   members fall below 3); that count check lives in app code (Room
 *   aggregations don't compose with FK triggers).
 */
@Entity(
    tableName = "cluster_member",
    primaryKeys = ["clusterId", "envelopeId"],
    foreignKeys = [
        ForeignKey(
            entity = ClusterEntity::class,
            parentColumns = ["id"],
            childColumns = ["clusterId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = IntentEnvelopeEntity::class,
            parentColumns = ["id"],
            childColumns = ["envelopeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["clusterId"]),
        Index(value = ["envelopeId"], name = "idx_cluster_member_envelope")
    ]
)
data class ClusterMemberEntity(
    val clusterId: String,
    val envelopeId: String,
    val memberIndex: Int
)
