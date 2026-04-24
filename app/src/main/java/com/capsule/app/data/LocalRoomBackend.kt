package com.capsule.app.data

import androidx.room.withTransaction
import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.entity.ContinuationEntity
import com.capsule.app.data.entity.ContinuationResultEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.IntentEnvelopeWithResults
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.TimeUnit

/**
 * Sole v1 [EnvelopeStorageBackend] — delegates directly to Room DAOs
 * backed by an encrypted SQLCipher database. All `*Transaction` operations
 * run under a single Room transaction so envelope mutations and their audit
 * rows land atomically (audit-log-contract.md §6).
 */
class LocalRoomBackend(
    private val database: OrbitDatabase
) : EnvelopeStorageBackend {

    private val envelopeDao = database.intentEnvelopeDao()
    private val continuationDao = database.continuationDao()
    private val continuationResultDao = database.continuationResultDao()
    private val auditDao = database.auditLogDao()

    override suspend fun sealTransaction(
        envelope: IntentEnvelopeEntity,
        continuations: List<ContinuationEntity>,
        auditEntries: List<AuditLogEntryEntity>
    ) {
        database.withTransaction {
            envelopeDao.insert(envelope)
            continuations.forEach { continuationDao.insert(it) }
            auditEntries.forEach { auditDao.insert(it) }
        }
    }

    override suspend fun reassignIntentTransaction(
        id: String,
        newIntent: String,
        intentSource: String,
        confidence: Float?,
        historyJson: String,
        auditEntry: AuditLogEntryEntity
    ) {
        database.withTransaction {
            envelopeDao.updateIntent(id, newIntent, intentSource, confidence, historyJson)
            auditDao.insert(auditEntry)
        }
    }

    override suspend fun archiveTransaction(id: String, auditEntry: AuditLogEntryEntity) {
        database.withTransaction {
            envelopeDao.archive(id)
            auditDao.insert(auditEntry)
        }
    }

    override suspend fun softDeleteTransaction(
        id: String,
        deletedAt: Long,
        auditEntry: AuditLogEntryEntity
    ) {
        database.withTransaction {
            envelopeDao.softDelete(id, deletedAt)
            auditDao.insert(auditEntry)
        }
    }

    override suspend fun restoreFromTrashTransaction(
        id: String,
        auditEntry: AuditLogEntryEntity
    ) {
        database.withTransaction {
            envelopeDao.restoreFromTrash(id)
            auditDao.insert(auditEntry)
        }
    }

    override suspend fun hardDeleteTransaction(id: String, auditEntry: AuditLogEntryEntity) {
        database.withTransaction {
            // Write audit row FIRST so the cascade below doesn't also wipe it.
            auditDao.insert(auditEntry)
            envelopeDao.hardDelete(id) // cascades continuations + results via FK
        }
    }

    override suspend fun listIdsSoftDeletedBefore(cutoffMillis: Long): List<String> =
        envelopeDao.listIdsSoftDeletedBefore(cutoffMillis)

    override suspend fun undoSealTransaction(envelopeId: String) {
        database.withTransaction {
            // Retract the capture entirely — including its audit trail — because
            // undo means "this never happened". Continuations + results cascade
            // via FK; audit rows are deleted explicitly.
            auditDao.deleteByEnvelopeId(envelopeId)
            envelopeDao.hardDelete(envelopeId)
        }
    }

    override fun observeDay(dayLocal: String): Flow<List<IntentEnvelopeEntity>> =
        envelopeDao.observeDay(dayLocal)

    override fun observeDayWithResults(dayLocal: String): Flow<List<IntentEnvelopeWithResults>> =
        envelopeDao.observeDayWithResults(dayLocal)

    override suspend fun getEnvelope(id: String): IntentEnvelopeEntity? =
        envelopeDao.getById(id)

    override suspend fun listSoftDeletedWithinDays(
        days: Int,
        nowMillis: Long
    ): List<IntentEnvelopeEntity> {
        val cutoff = nowMillis - TimeUnit.DAYS.toMillis(days.toLong())
        return envelopeDao.listSoftDeletedWithinDays(cutoff)
    }

    override suspend fun countSoftDeletedWithinDays(days: Int, nowMillis: Long): Int {
        val cutoff = nowMillis - TimeUnit.DAYS.toMillis(days.toLong())
        return envelopeDao.countSoftDeletedWithinDays(cutoff)
    }

    override suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String> =
        envelopeDao.distinctDayLocalsWithContent(limit, offset)

    override suspend fun existsNonArchivedNonDeletedInLast30Days(
        appCategory: String,
        intent: String,
        nowMillis: Long
    ): Boolean {
        val cutoff = nowMillis - TimeUnit.DAYS.toMillis(30L)
        return envelopeDao.existsNonArchivedNonDeletedInLast30Days(appCategory, intent, cutoff)
    }

    override suspend fun findContinuationResultByCanonicalUrlHash(
        hash: String
    ): ContinuationResultEntity? = continuationResultDao.findByCanonicalUrlHash(hash)

    override suspend fun getLatestResultForEnvelope(
        envelopeId: String,
        sharedResultId: String?
    ): ContinuationResultEntity? {
        if (sharedResultId != null) {
            continuationResultDao.findById(sharedResultId)?.let { return it }
        }
        return continuationResultDao.getByEnvelopeId(envelopeId)
            .maxByOrNull { it.producedAt }
    }

    override suspend fun listRetryableContinuations(
        envelopeId: String
    ): List<ContinuationEntity> =
        continuationDao.getByEnvelopeId(envelopeId)
            .filter { it.status != com.capsule.app.data.model.ContinuationStatus.SUCCEEDED }

    override suspend fun completeUrlHydrationTransaction(
        continuationId: String,
        result: ContinuationResultEntity?,
        newStatus: com.capsule.app.data.model.ContinuationStatus,
        completedAt: Long,
        failureReason: String?,
        auditEntry: AuditLogEntryEntity,
        envelopeIdForDedupe: String?,
        dedupeExistingResultId: String?
    ) {
        database.withTransaction {
            when {
                dedupeExistingResultId != null && envelopeIdForDedupe != null -> {
                    // Late dedupe path: do NOT insert `result` (would
                    // violate unique canonicalUrlHash index). Point the
                    // envelope at the winning result instead.
                    envelopeDao.updateSharedContinuationResultId(
                        id = envelopeIdForDedupe,
                        resultId = dedupeExistingResultId
                    )
                }
                result != null -> {
                    continuationResultDao.insert(result)
                }
            }
            continuationDao.markCompleted(
                id = continuationId,
                status = newStatus.name,
                completedAt = completedAt,
                failureReason = failureReason
            )
            auditDao.insert(auditEntry)
        }
    }

    override suspend fun seedScreenshotHydrationsTransaction(
        continuations: List<ContinuationEntity>,
        auditEntries: List<AuditLogEntryEntity>
    ) {
        database.withTransaction {
            for (c in continuations) continuationDao.insert(c)
            for (a in auditEntries) auditDao.insert(a)
        }
    }

    override suspend fun countAll(): Int = envelopeDao.countAll()

    override suspend fun countArchived(): Int = envelopeDao.countArchived()

    override suspend fun countDeleted(): Int = envelopeDao.countDeleted()
}
