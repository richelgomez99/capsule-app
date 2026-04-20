package com.capsule.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource

@Entity(
    tableName = "intent_envelope",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["intent"]),
        Index(value = ["day_local"])
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
    val sharedContinuationResultId: String? = null
)
