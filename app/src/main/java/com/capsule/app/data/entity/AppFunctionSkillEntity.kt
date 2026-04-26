package com.capsule.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.capsule.app.data.model.AppFunctionSideEffect
import com.capsule.app.data.model.Reversibility
import com.capsule.app.data.model.SensitivityScope

/**
 * Schema row for one registered Android AppFunction.
 *
 * Schema **mirrors spec 006's `skills` table verbatim** so v1.1+ sync is a
 * row-level mirror with no transform. Per data-model.md §4 the primary
 * key is `functionId` alone — schema bumps overwrite via upsert. The
 * unique index on `(functionId, schemaVersion)` documents the soft-
 * supersede semantics; downstream re-validation uses the version
 * recorded on the proposal at extraction time.
 *
 * See `specs/003-orbit-actions/data-model.md` §4 and
 * `specs/003-orbit-actions/contracts/appfunction-registry-contract.md`.
 */
@Entity(
    tableName = "appfunction_skill",
    indices = [
        Index(value = ["functionId", "schemaVersion"], unique = true),
        Index(value = ["appPackage"])
    ]
)
data class AppFunctionSkillEntity(
    /** Stable id, e.g., `com.capsule.app.action.calendar_insert`. */
    @PrimaryKey val functionId: String,
    /** Owning app — `com.capsule.app` for v1.1; spec 008 expands this. */
    val appPackage: String,
    val displayName: String,
    val description: String,
    /** Bumps on schema change. Lookups without a version pin pick the current row's value. */
    val schemaVersion: Int,
    /** JSON Schema for `argsJson` validation. */
    val argsSchemaJson: String,
    val sideEffects: AppFunctionSideEffect,
    val reversibility: Reversibility,
    val sensitivityScope: SensitivityScope,
    val registeredAt: Long,
    val updatedAt: Long
)
