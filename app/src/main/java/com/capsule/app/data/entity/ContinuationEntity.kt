package com.capsule.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.capsule.app.data.model.ContinuationStatus
import com.capsule.app.data.model.ContinuationType

@Entity(
    tableName = "continuation",
    foreignKeys = [ForeignKey(
        entity = IntentEnvelopeEntity::class,
        parentColumns = ["id"],
        childColumns = ["envelopeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["envelopeId"]),
        Index(value = ["status"]),
        Index(value = ["type", "status"])
    ]
)
data class ContinuationEntity(
    @PrimaryKey val id: String,
    val envelopeId: String,
    val type: ContinuationType,
    val status: ContinuationStatus,
    val inputUrl: String?,
    val scheduledAt: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val attemptCount: Int = 0,
    val failureReason: String?
)
