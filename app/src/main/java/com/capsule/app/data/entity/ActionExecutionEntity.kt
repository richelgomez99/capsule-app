package com.capsule.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.capsule.app.data.model.ActionExecutionOutcome

/**
 * One execution attempt of a confirmed [ActionProposalEntity].
 * v1.1: created by `ActionExecutorService` when the user taps Confirm.
 *
 * The `outcome` column transitions PENDING → DISPATCHED|SUCCESS|FAILED|USER_CANCELLED.
 * `dispatchedAt` is set when the row is inserted; `completedAt`/`latencyMs` are
 * filled in when the outcome resolves.
 *
 * Cascade-deleted with its proposal. `episodeId` is null in v1.1 and is
 * back-filled when spec 006 ships an `episodes` table.
 *
 * See `specs/003-orbit-actions/data-model.md` §3.
 */
@Entity(
    tableName = "action_execution",
    foreignKeys = [
        ForeignKey(
            entity = ActionProposalEntity::class,
            parentColumns = ["id"],
            childColumns = ["proposalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["proposalId"]),
        Index(value = ["outcome"]),
        Index(value = ["dispatchedAt"])
    ]
)
data class ActionExecutionEntity(
    @PrimaryKey val id: String,
    val proposalId: String,
    /** Denormalised from the proposal for fast skill aggregations. */
    val functionId: String,
    val outcome: ActionExecutionOutcome,
    /** Free-text reason on FAILED / USER_CANCELLED. */
    val outcomeReason: String?,
    val dispatchedAt: Long,
    val completedAt: Long?,
    val latencyMs: Long?,
    /** Forward-compat for spec 006 episodes; null in v1.1 standalone. */
    val episodeId: String?
)
