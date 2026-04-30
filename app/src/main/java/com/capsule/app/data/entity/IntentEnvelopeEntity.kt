package com.capsule.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.EnvelopeKind
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource

@Entity(
    tableName = "intent_envelope",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["intent"]),
        Index(value = ["day_local"]),
        // 003 v1.1: speeds up the Diary's `kind = 'DIGEST'` filter and the
        // per-day rendering query that orders by kind ascending then time.
        Index(value = ["kind", "day_local"])
    ]
)
data class IntentEnvelopeEntity(
    @PrimaryKey val id: String,
    val contentType: ContentType,
    val textContent: String?,
    val imageUri: String?,
    val textContentSha256: String?,
    val intent: Intent,
    val intentConfidence: Float?,
    val intentSource: IntentSource,
    val intentHistoryJson: String,
    @Embedded val state: StateSnapshot,
    val createdAt: Long,
    @ColumnInfo(name = "day_local") val dayLocal: String,
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val deletedAt: Long? = null,
    val sharedContinuationResultId: String? = null,
    // 003 v1.1 additions — see specs/003-orbit-actions/data-model.md §1.
    /** Discriminator: REGULAR (default), DIGEST (weekly summary), DERIVED (forward-compat). */
    val kind: EnvelopeKind = EnvelopeKind.REGULAR,
    /** JSON array of envelope ids this DIGEST/DERIVED row summarises; null for REGULAR. */
    val derivedFromEnvelopeIdsJson: String? = null,
    /**
     * To-do payload when this envelope was created via the `todo_add`
     * AppFunction with target=local. Shape:
     * `{"items":[{"text":"…","done":false,"dueEpochMillis":null}],"derivedFromProposalId":"…"}`.
     * Null on every other envelope. See specs/003-orbit-actions/tasks.md T063.
     */
    val todoMetaJson: String? = null,
    /**
     * Spec 002 Phase 11 Block 13 / spec 012 FR-012-011 — the AppFunction
     * `function_id` that produced this DERIVED row, e.g.
     * `"cluster_summarize"`. Null for REGULAR + DIGEST and forward-compat
     * for any spec 012 derived path that lands later. Carries the
     * `function_id` semantic from spec 012 line 126 (`derived_via TEXT`)
     * — we use camelCase here to match the rest of `intent_envelope`.
     */
    val derivedVia: String? = null
)

