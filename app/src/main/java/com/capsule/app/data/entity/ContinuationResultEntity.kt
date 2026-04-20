package com.capsule.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "continuation_result",
    foreignKeys = [
        ForeignKey(
            entity = ContinuationEntity::class,
            parentColumns = ["id"],
            childColumns = ["continuationId"],
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
        Index("continuationId"),
        Index("envelopeId"),
        Index(value = ["canonicalUrlHash"], unique = true)
    ]
)
data class ContinuationResultEntity(
    @PrimaryKey val id: String,
    val continuationId: String,
    val envelopeId: String,
    val producedAt: Long,
    val title: String?,
    val domain: String?,
    val canonicalUrl: String?,
    val canonicalUrlHash: String?,
    val excerpt: String?,
    val summary: String?,
    val summaryModel: String?
)
