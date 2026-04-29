package com.capsule.app.continuation

import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.EnvelopeStorageBackend
import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.entity.ContinuationEntity
import com.capsule.app.data.entity.ContinuationResultEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.IntentEnvelopeWithResults
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.ContinuationStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoftDeleteRetentionWorkerPurgeTest {

    private class FakeBackend(private val oldIds: List<String>) : EnvelopeStorageBackend {
        val hardDeleted = mutableListOf<Pair<String, AuditLogEntryEntity>>()

        override suspend fun listIdsSoftDeletedBefore(cutoffMillis: Long): List<String> = oldIds
        override suspend fun hardDeleteTransaction(id: String, auditEntry: AuditLogEntryEntity) {
            hardDeleted += id to auditEntry
        }

        // --- unused ---
        override suspend fun sealTransaction(
            envelope: IntentEnvelopeEntity,
            continuations: List<ContinuationEntity>,
            auditEntries: List<AuditLogEntryEntity>
        ) = error("unused")
        override suspend fun reassignIntentTransaction(
            id: String, newIntent: String, intentSource: String,
            confidence: Float?, historyJson: String, auditEntry: AuditLogEntryEntity
        ) = error("unused")
        override suspend fun archiveTransaction(id: String, auditEntry: AuditLogEntryEntity) = error("unused")
        override suspend fun softDeleteTransaction(id: String, deletedAt: Long, auditEntry: AuditLogEntryEntity) = error("unused")
        override suspend fun restoreFromTrashTransaction(id: String, auditEntry: AuditLogEntryEntity) = error("unused")
        override suspend fun undoSealTransaction(envelopeId: String) = error("unused")
        override fun observeDay(dayLocal: String): Flow<List<IntentEnvelopeEntity>> = emptyFlow()
        override fun observeDayWithResults(dayLocal: String): Flow<List<IntentEnvelopeWithResults>> = emptyFlow()
        override suspend fun getEnvelope(id: String): IntentEnvelopeEntity? = null
        override suspend fun listSoftDeletedWithinDays(days: Int, nowMillis: Long): List<IntentEnvelopeEntity> = emptyList()
        override suspend fun countSoftDeletedWithinDays(days: Int, nowMillis: Long): Int = 0
        override suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String> = emptyList()
        override suspend fun existsNonArchivedNonDeletedInLast30Days(
            appCategory: String, intent: String, nowMillis: Long
        ): Boolean = false
        override suspend fun findContinuationResultByCanonicalUrlHash(hash: String): ContinuationResultEntity? = null
        override suspend fun getLatestResultForEnvelope(envelopeId: String, sharedResultId: String?): ContinuationResultEntity? = null
        override suspend fun listRetryableContinuations(envelopeId: String): List<ContinuationEntity> = emptyList()
        override suspend fun completeUrlHydrationTransaction(
            continuationId: String,
            result: ContinuationResultEntity?,
            newStatus: ContinuationStatus,
            completedAt: Long,
            failureReason: String?,
            auditEntry: AuditLogEntryEntity,
            envelopeIdForDedupe: String?,
            dedupeExistingResultId: String?
        ) = error("unused")
        override suspend fun seedScreenshotHydrationsTransaction(
            continuations: List<ContinuationEntity>,
            auditEntries: List<AuditLogEntryEntity>
        ) = error("unused")
        override suspend fun countAll(): Int = 0
        override suspend fun countArchived(): Int = 0
        override suspend fun countDeleted(): Int = 0
        override suspend fun insertDigestTransaction(
            envelope: com.capsule.app.data.entity.IntentEnvelopeEntity,
            auditEntry: AuditLogEntryEntity
        ): Boolean = error("unused")
        override suspend fun listRegularEnvelopesInWindow(
            windowStartDayLocal: String,
            windowEndDayLocalInclusive: String,
            limit: Int
        ): List<com.capsule.app.data.entity.IntentEnvelopeEntity> = emptyList()
        override suspend fun cascadeDigestInvalidation(
            deletedEnvelopeId: String,
            now: Long,
            auditFor: (String) -> AuditLogEntryEntity
        ): List<String> = emptyList()
    }

    @Test
    fun `purges every soft-deleted id with retention reason`() = runTest {
        val backend = FakeBackend(listOf("a", "b", "c"))
        val writer = AuditLogWriter(clock = { 5_000L }, idGen = { "audit-id" })

        val purged = SoftDeleteRetentionWorker.purge(backend, writer, cutoffMillis = 1_000L)

        assertEquals(3, purged)
        assertEquals(listOf("a", "b", "c"), backend.hardDeleted.map { it.first })
        backend.hardDeleted.forEach { (id, audit) ->
            assertEquals(AuditAction.ENVELOPE_HARD_PURGED, audit.action)
            assertEquals(id, audit.envelopeId)
            assertTrue(
                "extraJson carries retention reason: ${audit.extraJson}",
                audit.extraJson?.contains("\"reason\":\"retention\"") == true
            )
        }
    }

    @Test
    fun `no-op when nothing has expired`() = runTest {
        val backend = FakeBackend(emptyList())
        val writer = AuditLogWriter()

        val purged = SoftDeleteRetentionWorker.purge(backend, writer, cutoffMillis = 0L)

        assertEquals(0, purged)
        assertTrue(backend.hardDeleted.isEmpty())
    }
}
