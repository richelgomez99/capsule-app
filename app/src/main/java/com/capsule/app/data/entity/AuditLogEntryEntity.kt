package com.capsule.app.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.capsule.app.data.model.AuditAction

@Entity(
    tableName = "audit_log",
    indices = [
        Index("at"),
        Index("envelopeId"),
        Index("action")
    ]
)
data class AuditLogEntryEntity(
    @PrimaryKey val id: String,
    val at: Long,
    val action: AuditAction,
    val description: String,
    val envelopeId: String?,
    val extraJson: String?,
    // T025e: forward-compatible LLM provenance columns (nullable in v1, always null)
    val llmProvider: String? = null,
    val llmModel: String? = null,
    val promptDigestSha256: String? = null,
    val tokenCount: Int? = null
)
