package com.capsule.app.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "envelope_note",
    foreignKeys = [
        ForeignKey(
            entity = IntentEnvelopeEntity::class,
            parentColumns = ["id"],
            childColumns = ["envelopeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["envelopeId", "updatedAt"])]
)
data class EnvelopeNoteEntity(
    @PrimaryKey val id: String,
    val envelopeId: String,
    val text: String,
    val createdAt: Long,
    val updatedAt: Long?
)
