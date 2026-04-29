package com.capsule.app.ai

import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.data.EnvelopeStorageBackend
import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.entity.ContinuationEntity
import com.capsule.app.data.entity.ContinuationResultEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.model.Intent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T036b — covers the 2×2 truth table plus archived / soft-deleted / >30d
 * exclusions for [SilentWrapPredicate].
 */
class SilentWrapPredicateTest {

    private fun predict(intent: Intent, confidence: Float) = IntentClassification(
        intent = intent,
        confidence = confidence,
        provenance = LlmProvenance.LocalNano
    )

    @Test
    fun highConfidence_andPriorMatch_silentWraps() = runTest {
        val backend = FakeBackend(priorExists = true)
        val predicate = SilentWrapPredicate(backend)

        val decision = predicate.evaluate(predict(Intent.WANT_IT, 0.85f), "BROWSER")

        assertTrue(decision is SilentWrapPredicate.SilentWrapDecision.SilentWrap)
        assertEquals(Intent.WANT_IT, (decision as SilentWrapPredicate.SilentWrapDecision.SilentWrap).intent)
    }

    @Test
    fun highConfidence_noPriorMatch_showsChipRow() = runTest {
        val backend = FakeBackend(priorExists = false)
        val predicate = SilentWrapPredicate(backend)

        val decision = predicate.evaluate(predict(Intent.WANT_IT, 0.85f), "BROWSER")

        assertEquals(SilentWrapPredicate.SilentWrapDecision.ShowChipRow, decision)
    }

    @Test
    fun lowConfidence_andPriorMatch_showsChipRow() = runTest {
        val backend = FakeBackend(priorExists = true)
        val predicate = SilentWrapPredicate(backend)

        val decision = predicate.evaluate(predict(Intent.WANT_IT, 0.60f), "BROWSER")

        assertEquals(SilentWrapPredicate.SilentWrapDecision.ShowChipRow, decision)
    }

    @Test
    fun lowConfidence_noPriorMatch_showsChipRow() = runTest {
        val backend = FakeBackend(priorExists = false)
        val predicate = SilentWrapPredicate(backend)

        val decision = predicate.evaluate(predict(Intent.WANT_IT, 0.10f), "BROWSER")

        assertEquals(SilentWrapPredicate.SilentWrapDecision.ShowChipRow, decision)
    }

    @Test
    fun ambiguousIntent_neverSilentWraps_evenWithPrior() = runTest {
        val backend = FakeBackend(priorExists = true)
        val predicate = SilentWrapPredicate(backend)

        val decision = predicate.evaluate(predict(Intent.AMBIGUOUS, 0.99f), "BROWSER")

        assertEquals(SilentWrapPredicate.SilentWrapDecision.ShowChipRow, decision)
    }

    @Test
    fun confidenceAtExactlyThreshold_isSilentWrap() = runTest {
        val backend = FakeBackend(priorExists = true)
        val predicate = SilentWrapPredicate(backend)

        val decision = predicate.evaluate(predict(Intent.REFERENCE, 0.70f), "BROWSER")

        assertTrue(decision is SilentWrapPredicate.SilentWrapDecision.SilentWrap)
    }

    /**
     * The backend implementation is responsible for filtering archived,
     * soft-deleted, and >30d priors via its SQL. We verify the predicate
     * trusts that contract: when `priorExists=false` (backend returns no
     * qualifying prior for any reason), the predicate shows the chip row
     * regardless of confidence.
     */
    @Test
    fun archivedSoftDeletedAndExpiredPriors_areExcluded_byContract() = runTest {
        val backend = FakeBackend(priorExists = false)
        val predicate = SilentWrapPredicate(backend)

        val decision = predicate.evaluate(predict(Intent.WANT_IT, 0.95f), "BROWSER")

        assertEquals(SilentWrapPredicate.SilentWrapDecision.ShowChipRow, decision)
    }

    // ---- Fake backend ----

    private class FakeBackend(private val priorExists: Boolean) : EnvelopeStorageBackend {
        override suspend fun existsNonArchivedNonDeletedInLast30Days(
            appCategory: String,
            intent: String,
            nowMillis: Long
        ): Boolean = priorExists

        // Unused by this test — throw to fail loudly if misrouted.
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
        override suspend fun hardDeleteTransaction(id: String, auditEntry: AuditLogEntryEntity) = error("unused")
        override suspend fun listIdsSoftDeletedBefore(cutoffMillis: Long): List<String> = emptyList()
        override suspend fun undoSealTransaction(envelopeId: String) = error("unused")
        override fun observeDay(dayLocal: String): Flow<List<IntentEnvelopeEntity>> = emptyFlow()
        override fun observeDayWithResults(dayLocal: String): Flow<List<com.capsule.app.data.entity.IntentEnvelopeWithResults>> = emptyFlow()
        override suspend fun getEnvelope(id: String): IntentEnvelopeEntity? = null
        override suspend fun listSoftDeletedWithinDays(days: Int, nowMillis: Long): List<IntentEnvelopeEntity> = emptyList()
        override suspend fun countSoftDeletedWithinDays(days: Int, nowMillis: Long): Int = 0
        override suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String> = emptyList()
        override suspend fun findContinuationResultByCanonicalUrlHash(hash: String): ContinuationResultEntity? = null
        override suspend fun getLatestResultForEnvelope(envelopeId: String, sharedResultId: String?): ContinuationResultEntity? = null
        override suspend fun listRetryableContinuations(envelopeId: String): List<ContinuationEntity> = emptyList()
        override suspend fun completeUrlHydrationTransaction(
            continuationId: String,
            result: ContinuationResultEntity?,
            newStatus: com.capsule.app.data.model.ContinuationStatus,
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
            envelope: IntentEnvelopeEntity,
            auditEntry: AuditLogEntryEntity
        ): Boolean = error("unused")
        override suspend fun listRegularEnvelopesInWindow(
            windowStartDayLocal: String,
            windowEndDayLocalInclusive: String,
            limit: Int
        ): List<IntentEnvelopeEntity> = emptyList()
        override suspend fun cascadeDigestInvalidation(
            deletedEnvelopeId: String,
            now: Long,
            auditFor: (String) -> AuditLogEntryEntity
        ): List<String> = emptyList()
    }
}
