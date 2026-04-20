package com.capsule.app.data

import com.capsule.app.data.entity.IntentEnvelopeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Principle X — Storage Sovereignty.
 *
 * All persistence MUST go through this interface. The sole v1 implementation
 * is [LocalRoomBackend] (encrypted on-device Room). Future backends
 * (Orbit Cloud, BYOC) will implement this same interface.
 */
interface EnvelopeStorageBackend {

    suspend fun seal(envelope: IntentEnvelopeEntity)

    fun observeDay(dayLocal: String): Flow<List<IntentEnvelopeEntity>>

    suspend fun getEnvelope(id: String): IntentEnvelopeEntity?

    suspend fun updateIntent(
        id: String,
        intent: String,
        intentSource: String,
        confidence: Float?,
        historyJson: String
    )

    suspend fun archive(id: String)

    suspend fun softDelete(id: String, deletedAt: Long)

    suspend fun restoreFromTrash(id: String)

    suspend fun countAll(): Int

    suspend fun countArchived(): Int

    suspend fun countDeleted(): Int
}
