package com.capsule.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.capsule.app.data.model.ActionExecutionOutcome

/**
 * One usage row per terminal [ActionExecutionEntity] outcome. The Orbit
 * Agent (spec 008) consumes aggregations of this table as planner
 * heuristics (prefer skills with high success rates, defer skills with
 * frequent user cancellations).
 *
 * Schema **mirrors spec 006's `skill_usage` table verbatim**.
 *
 * The `executionId` cascades on action_execution delete, which itself
 * cascades on action_proposal delete, which cascades on intent_envelope
 * delete — so deleting a source envelope tears down its full action
 * lineage. The `skillId` FK has no cascade because skills are never
 * deleted (only superseded).
 *
 * See `specs/003-orbit-actions/data-model.md` §5.
 */
@Entity(
    tableName = "skill_usage",
    foreignKeys = [
        ForeignKey(
            entity = AppFunctionSkillEntity::class,
            parentColumns = ["functionId"],
            childColumns = ["skillId"]
        ),
        ForeignKey(
            entity = ActionExecutionEntity::class,
            parentColumns = ["id"],
            childColumns = ["executionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["skillId"]),
        Index(value = ["executionId"]),
        Index(value = ["invokedAt"]),
        Index(value = ["outcome"])
    ]
)
data class SkillUsageEntity(
    @PrimaryKey val id: String,
    /** FK → `appfunction_skill.functionId`. */
    val skillId: String,
    /** FK → `action_execution.id`. */
    val executionId: String,
    /** Denormalised for fast group-by without a 3-table join. */
    val proposalId: String,
    /** Forward-compat for spec 006 episodes; null in v1.1 standalone. */
    val episodeId: String?,
    /** Mirrors [ActionExecutionEntity.outcome] at the moment usage was recorded. */
    val outcome: ActionExecutionOutcome,
    val latencyMs: Long,
    val invokedAt: Long
)
