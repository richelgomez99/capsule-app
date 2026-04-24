package com.capsule.app.data

import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.entity.ContinuationEntity
import com.capsule.app.data.entity.ContinuationResultEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.IntentEnvelopeWithResults
import kotlinx.coroutines.flow.Flow

/**
 * Principle X — Storage Sovereignty.
 *
 * All persistence MUST go through this interface. The sole v1 implementation
 * is [LocalRoomBackend] (encrypted on-device Room). Future backends
 * (Orbit Cloud v1.1, BYOC v1.3) will implement this same interface.
 *
 * Every composite write is exposed as a single `*Transaction` call so the
 * backend — not the caller — owns atomicity. This keeps the AIDL surface
 * backend-agnostic per contracts/envelope-repository-contract.md §2.1.
 */
interface EnvelopeStorageBackend {

    // ---- Composite atomic writes ----

    /**
     * Atomically writes the envelope, its pending continuations, and any audit
     * rows (e.g. ENVELOPE_CREATED, CAPTURE_SCRUBBED, URL_DEDUPE_HIT) in a single
     * transaction. Per audit-log-contract.md §6 / data-model.md §8.
     */
    suspend fun sealTransaction(
        envelope: IntentEnvelopeEntity,
        continuations: List<ContinuationEntity>,
        auditEntries: List<AuditLogEntryEntity>
    )

    suspend fun reassignIntentTransaction(
        id: String,
        newIntent: String,
        intentSource: String,
        confidence: Float?,
        historyJson: String,
        auditEntry: AuditLogEntryEntity
    )

    suspend fun archiveTransaction(id: String, auditEntry: AuditLogEntryEntity)

    suspend fun softDeleteTransaction(
        id: String,
        deletedAt: Long,
        auditEntry: AuditLogEntryEntity
    )

    suspend fun restoreFromTrashTransaction(id: String, auditEntry: AuditLogEntryEntity)

    /**
     * Hard-deletes an envelope (user purge or retention worker). Cascades
     * continuations/results via FK. Audit row is written in the same txn.
     */
    suspend fun hardDeleteTransaction(id: String, auditEntry: AuditLogEntryEntity)

    /**
     * T089a — returns ids of envelopes whose `deletedAt` is older than
     * [cutoffMillis]. The retention worker hard-purges each via
     * [hardDeleteTransaction] so every purge carries its own audit row.
     */
    suspend fun listIdsSoftDeletedBefore(cutoffMillis: Long): List<String>

    /**
     * Undo window support. Hard-deletes the envelope + its continuations + any
     * audit rows tied to it. No audit row is emitted — the capture is being
     * retracted. See contracts/envelope-repository-contract.md §4 (`undo`).
     */
    suspend fun undoSealTransaction(envelopeId: String)

    // ---- Read paths ----

    fun observeDay(dayLocal: String): Flow<List<IntentEnvelopeEntity>>

    /**
     * Day view enriched with every hydrated [ContinuationResultEntity] per envelope.
     * Diary consumers should prefer this over [observeDay] so URL hydration write-backs
     * surface title/domain/summary without a manual refresh.
     */
    fun observeDayWithResults(dayLocal: String): Flow<List<IntentEnvelopeWithResults>>

    suspend fun getEnvelope(id: String): IntentEnvelopeEntity?

    suspend fun listSoftDeletedWithinDays(days: Int, nowMillis: Long): List<IntentEnvelopeEntity>

    suspend fun countSoftDeletedWithinDays(days: Int, nowMillis: Long): Int

    suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String>

    /**
     * Silent-wrap predicate backing query (T036a). Returns true iff a non-archived,
     * non-deleted envelope exists in the last 30 days matching both `appCategory`
     * and `intent`.
     */
    suspend fun existsNonArchivedNonDeletedInLast30Days(
        appCategory: String,
        intent: String,
        nowMillis: Long
    ): Boolean

    // ---- URL dedupe ----

    suspend fun findContinuationResultByCanonicalUrlHash(hash: String): ContinuationResultEntity?

    /**
     * T055a — detail-screen helper. Returns the most recent
     * [ContinuationResultEntity] for [envelopeId], preferring [sharedResultId]
     * (dedupe-shared row) when supplied and present. Null if no hydration
     * has succeeded yet.
     */
    suspend fun getLatestResultForEnvelope(
        envelopeId: String,
        sharedResultId: String?
    ): ContinuationResultEntity?

    /**
     * T069 follow-up — read non-succeeded continuation rows for an envelope
     * so the repository can re-enqueue them via `ContinuationEngine`.
     */
    suspend fun listRetryableContinuations(envelopeId: String): List<ContinuationEntity>

    /**
     * T066 completion — write-back from [com.capsule.app.continuation.UrlHydrateWorker].
     *
     * Atomically: (1) optional [ContinuationResultEntity] insert on success;
     * (2) [ContinuationEntity.status] transition to SUCCEEDED / FAILED_PERMANENT;
     * (3) CONTINUATION_COMPLETED audit row. Audit atomicity per
     * audit-log-contract.md §6.
     *
     * `result` must be non-null iff we are recording a successful fetch.
     */
    suspend fun completeUrlHydrationTransaction(
        continuationId: String,
        result: ContinuationResultEntity?,
        newStatus: com.capsule.app.data.model.ContinuationStatus,
        completedAt: Long,
        failureReason: String?,
        auditEntry: AuditLogEntryEntity,
        /**
         * Late dedupe — when non-null, the worker fetched a URL whose
         * canonical hash already has a [ContinuationResultEntity] in the
         * table (concurrent seal of the same URL). Instead of inserting
         * `result` (which would collide with the unique index on
         * `canonicalUrlHash`), repoint [envelopeIdForDedupe] at
         * [dedupeExistingResultId] and skip the result insert.
         */
        envelopeIdForDedupe: String? = null,
        dedupeExistingResultId: String? = null
    )

    /**
     * T076 (Phase 6 US4) — Screenshot OCR hydration write-back.
     *
     * Atomically inserts N PENDING [ContinuationEntity] rows (one per
     * extracted URL) + the audit rows (one `INFERENCE_RUN` summary row
     * + one `URL_DEDUPE_HIT` row per cache hit). Same transactional
     * contract as [sealTransaction] but without an envelope insert
     * because the IMAGE envelope was sealed earlier.
     */
    suspend fun seedScreenshotHydrationsTransaction(
        continuations: List<ContinuationEntity>,
        auditEntries: List<AuditLogEntryEntity>
    )

    // ---- Diagnostics ----

    suspend fun countAll(): Int

    suspend fun countArchived(): Int

    suspend fun countDeleted(): Int
}
