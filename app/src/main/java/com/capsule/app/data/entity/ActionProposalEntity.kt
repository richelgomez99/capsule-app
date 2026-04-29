package com.capsule.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.capsule.app.data.model.ActionProposalState
import com.capsule.app.data.model.LlmProvenance
import com.capsule.app.data.model.SensitivityScope

/**
 * One candidate action extracted from an envelope by the
 * `ACTION_EXTRACT` continuation. v1.1: `calendar_insert`, `todo_add`,
 * or `share` (the three seeded AppFunctions).
 *
 * State machine — see `[ActionProposalState]`.
 *
 * Cascade-deleted with the source envelope (Principle XII). The composite
 * unique index `(envelopeId, functionId)` prevents the extractor from
 * producing duplicate proposals when re-run for the same envelope.
 *
 * See `specs/003-orbit-actions/data-model.md` §2.
 */
@Entity(
    tableName = "action_proposal",
    foreignKeys = [
        ForeignKey(
            entity = IntentEnvelopeEntity::class,
            parentColumns = ["id"],
            childColumns = ["envelopeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["envelopeId"]),
        Index(value = ["state"]),
        Index(value = ["createdAt"]),
        Index(value = ["envelopeId", "functionId"], unique = true)
    ]
)
data class ActionProposalEntity(
    @PrimaryKey val id: String,
    /** FK → `intent_envelope.id`. Cascade-delete. */
    val envelopeId: String,
    /** FK → `appfunction_skill.functionId`. Not enforced via SQL FK because schemas can be soft-superseded. */
    val functionId: String,
    /** The exact `schemaVersion` of the AppFunction at extraction time. Used for re-validation at execute. */
    val schemaVersion: Int,
    /** Args validated against the function's schema. Persisted as compact JSON string. */
    val argsJson: String,
    /** Human-readable summary used for the chip ("Add to calendar — Flight UA437"). */
    val previewTitle: String,
    /** Optional second line for the preview card. */
    val previewSubtitle: String?,
    /** Extractor confidence in [0, 1]. Below the 0.55 floor → not persisted. */
    val confidence: Float,
    val provenance: LlmProvenance,
    val state: ActionProposalState,
    val sensitivityScope: SensitivityScope,
    val createdAt: Long,
    val stateChangedAt: Long
)
