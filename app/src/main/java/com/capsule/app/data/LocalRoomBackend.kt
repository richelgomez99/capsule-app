package com.capsule.app.data

import com.capsule.app.data.dao.IntentEnvelopeDao
import com.capsule.app.data.entity.IntentEnvelopeEntity
import kotlinx.coroutines.flow.Flow

/**
 * Sole v1 [EnvelopeStorageBackend] — delegates directly to Room DAOs
 * backed by an encrypted SQLCipher database.
 */
class LocalRoomBackend(
    private val dao: IntentEnvelopeDao
) : EnvelopeStorageBackend {

    override suspend fun seal(envelope: IntentEnvelopeEntity) {
        dao.insert(envelope)
    }

    override fun observeDay(dayLocal: String): Flow<List<IntentEnvelopeEntity>> {
        return dao.observeDay(dayLocal)
    }

    override suspend fun getEnvelope(id: String): IntentEnvelopeEntity? {
        return dao.getById(id)
    }

    override suspend fun updateIntent(
        id: String,
        intent: String,
        intentSource: String,
        confidence: Float?,
        historyJson: String
    ) {
        dao.updateIntent(id, intent, intentSource, confidence, historyJson)
    }

    override suspend fun archive(id: String) {
        dao.archive(id)
    }

    override suspend fun softDelete(id: String, deletedAt: Long) {
        dao.softDelete(id, deletedAt)
    }

    override suspend fun restoreFromTrash(id: String) {
        dao.restoreFromTrash(id)
    }

    override suspend fun countAll(): Int {
        return dao.countAll()
    }

    override suspend fun countArchived(): Int {
        return dao.countArchived()
    }

    override suspend fun countDeleted(): Int {
        return dao.countDeleted()
    }
}
