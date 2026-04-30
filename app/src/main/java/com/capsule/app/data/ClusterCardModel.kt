package com.capsule.app.data

import com.capsule.app.data.entity.ClusterEntity
import com.capsule.app.data.entity.ClusterMemberEntity
import com.capsule.app.data.model.ClusterState

/**
 * In-process projection of a cluster row + its surviving members shaped
 * for the Diary cluster-card slot (spec 002 Phase 11 Block 5 / T133).
 *
 * Carries only the fields the cluster card actually needs to render —
 * see spec 010 FR-010-024 for the six visual states. Member text
 * (titles, domains) is reused from the existing envelope flow on the
 * UI side; the card only needs the ordered envelope ids so it can
 * cross-reference (cite) the envelopes that produced it.
 *
 * The list is intentionally narrow: no embeddings, no summary, no
 * audit metadata. Block 6 ([com.capsule.app.ai.ClusterSummariser])
 * adds a separate `summary: ClusterSummary?` slot when it lands.
 */
data class ClusterCardModel(
    val clusterId: String,
    val state: ClusterState,
    val timeBucketStart: Long,
    val timeBucketEnd: Long,
    val modelLabel: String,
    val members: List<ClusterMemberRef>
)

/**
 * Stable reference into a cluster's member set. `memberIndex` is the
 * write-time order from [ClusterMemberEntity.memberIndex] so consumers
 * can render citations in deterministic order regardless of which
 * envelope row joins back first.
 */
data class ClusterMemberRef(
    val envelopeId: String,
    val memberIndex: Int
)

internal fun ClusterEntity.toCardModel(members: List<ClusterMemberEntity>): ClusterCardModel =
    ClusterCardModel(
        clusterId = id,
        state = state,
        timeBucketStart = timeBucketStart,
        timeBucketEnd = timeBucketEnd,
        modelLabel = modelLabel,
        members = members
            .sortedBy { it.memberIndex }
            .map { ClusterMemberRef(envelopeId = it.envelopeId, memberIndex = it.memberIndex) }
    )
