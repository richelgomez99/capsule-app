package com.capsule.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.IntentEnvelopeWithResults
import kotlinx.coroutines.flow.Flow

@Dao
interface IntentEnvelopeDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(envelope: IntentEnvelopeEntity)

    @Query("SELECT * FROM intent_envelope WHERE id = :id")
    suspend fun getById(id: String): IntentEnvelopeEntity?

    @Query(
        """
        SELECT * FROM intent_envelope
        WHERE day_local = :dayLocal
          AND deletedAt IS NULL
          AND isArchived = 0
        ORDER BY CASE kind WHEN 'DIGEST' THEN 0 ELSE 1 END ASC, createdAt DESC
        """
    )
    fun observeDay(dayLocal: String): Flow<List<IntentEnvelopeEntity>>

    /**
     * Day view enriched with every [com.capsule.app.data.entity.ContinuationResultEntity]
     * linked to each envelope. Room's `@Transaction` + `@Relation` machinery
     * tracks both tables, so URL hydration write-backs re-emit the flow and
     * the Diary UI picks up the hydrated title/domain/summary without a
     * manual refresh.
     */
    @Transaction
    @Query(
        """
        SELECT * FROM intent_envelope
        WHERE day_local = :dayLocal
          AND deletedAt IS NULL
          AND isArchived = 0
        ORDER BY CASE kind WHEN 'DIGEST' THEN 0 ELSE 1 END ASC, createdAt DESC
        """
    )
    fun observeDayWithResults(dayLocal: String): Flow<List<IntentEnvelopeWithResults>>

    @Query("UPDATE intent_envelope SET intent = :intent, intentSource = :intentSource, intentConfidence = :confidence, intentHistoryJson = :historyJson WHERE id = :id")
    suspend fun updateIntent(id: String, intent: String, intentSource: String, confidence: Float?, historyJson: String)

    /**
     * Late dedupe patch — when `completeUrlHydration` finds an existing
     * [com.capsule.app.data.entity.ContinuationResultEntity] at the same
     * canonical URL hash (because two envelopes for the same URL were
     * sealed concurrently, before the first's worker finished), we flip
     * the second envelope's pointer to the winning result instead of
     * inserting a duplicate that would violate the unique index.
     */
    @Query("UPDATE intent_envelope SET sharedContinuationResultId = :resultId WHERE id = :id")
    suspend fun updateSharedContinuationResultId(id: String, resultId: String)

    /**
     * Spec 003 v1.1 / T064 — replaces the JSON blob carried by a
     * derived to-do envelope. Used when the user toggles a checkbox in
     * `EnvelopeCard`. No-op (returns 0) when the row has no
     * `todoMetaJson` to overwrite.
     */
    @Query("UPDATE intent_envelope SET todoMetaJson = :json WHERE id = :id")
    suspend fun updateTodoMetaJson(id: String, json: String): Int

    @Query("UPDATE intent_envelope SET isArchived = 1 WHERE id = :id")
    suspend fun archive(id: String)

    @Query("UPDATE intent_envelope SET deletedAt = :deletedAt, isDeleted = 1 WHERE id = :id")
    suspend fun softDelete(id: String, deletedAt: Long)

    @Query("UPDATE intent_envelope SET deletedAt = NULL, isDeleted = 0 WHERE id = :id")
    suspend fun restoreFromTrash(id: String)

    @Query(
        """
        SELECT * FROM intent_envelope
        WHERE deletedAt IS NOT NULL
          AND deletedAt > :cutoffMillis
        ORDER BY deletedAt DESC
        """
    )
    suspend fun listSoftDeletedWithinDays(cutoffMillis: Long): List<IntentEnvelopeEntity>

    @Query("SELECT COUNT(*) FROM intent_envelope WHERE deletedAt IS NOT NULL AND deletedAt > :cutoffMillis")
    suspend fun countSoftDeletedWithinDays(cutoffMillis: Long): Int

    @Query("DELETE FROM intent_envelope WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffMillis")
    suspend fun hardPurgeWhereDeletedBefore(cutoffMillis: Long)

    /** T089a — ids to purge so the retention worker can audit each purge. */
    @Query("SELECT id FROM intent_envelope WHERE deletedAt IS NOT NULL AND deletedAt < :cutoffMillis")
    suspend fun listIdsSoftDeletedBefore(cutoffMillis: Long): List<String>

    @Query("DELETE FROM intent_envelope WHERE id = :id")
    suspend fun hardDelete(id: String)

    @Query("SELECT COUNT(*) FROM intent_envelope")
    suspend fun countAll(): Int

    @Query("SELECT COUNT(*) FROM intent_envelope WHERE isArchived = 1")
    suspend fun countArchived(): Int

    @Query("SELECT COUNT(*) FROM intent_envelope WHERE isDeleted = 1")
    suspend fun countDeleted(): Int

    @Query(
        """
        SELECT EXISTS(
            SELECT 1 FROM intent_envelope
            WHERE appCategory = :appCategory
              AND intent = :intent
              AND deletedAt IS NULL
              AND isArchived = 0
              AND createdAt > :cutoffMillis
        )
        """
    )
    suspend fun existsNonArchivedNonDeletedInLast30Days(appCategory: String, intent: String, cutoffMillis: Long): Boolean

    @Query(
        """
        SELECT DISTINCT day_local FROM intent_envelope
        WHERE deletedAt IS NULL AND isArchived = 0
        ORDER BY day_local DESC
        LIMIT :limit OFFSET :offset
        """
    )
    suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String>

    /** T093 — full dump for user-initiated export. */
    @Query("SELECT * FROM intent_envelope ORDER BY createdAt DESC")
    suspend fun listAll(): List<IntentEnvelopeEntity>

    /**
     * T074 / 003 US3 — input window for [com.capsule.app.ai.DigestComposer].
     * Excludes soft-deleted, archived, AMBIGUOUS-intent, and any non-REGULAR
     * row (DIGEST/DERIVED) per weekly-digest-contract.md §4. Bounded by
     * [limit] so a particularly noisy week can't run away with the prompt.
     */
    @Query(
        """
        SELECT * FROM intent_envelope
        WHERE day_local >= :windowStartDayLocal
          AND day_local <= :windowEndDayLocalInclusive
          AND deletedAt IS NULL
          AND isArchived = 0
          AND intent != 'AMBIGUOUS'
          AND kind = 'REGULAR'
        ORDER BY createdAt DESC
        LIMIT :limit
        """
    )
    suspend fun listRegularEnvelopesInWindow(
        windowStartDayLocal: String,
        windowEndDayLocalInclusive: String,
        limit: Int
    ): List<IntentEnvelopeEntity>

    /**
     * T075 — find non-deleted DIGEST envelopes whose
     * `derivedFromEnvelopeIdsJson` array contains the given source id.
     *
     * The provenance column is stored as a JSON array string like
     * `["a","b","c"]`, so we LIKE on the quoted form `"<id>"` to avoid
     * matching ids that share a prefix. Caller still parses the JSON
     * to confirm — this is a coarse first pass for performance.
     */
    @Query(
        """
        SELECT * FROM intent_envelope
        WHERE kind = 'DIGEST'
          AND deletedAt IS NULL
          AND derivedFromEnvelopeIdsJson IS NOT NULL
          AND derivedFromEnvelopeIdsJson LIKE :quotedIdPattern
        """
    )
    suspend fun listDigestsReferencing(quotedIdPattern: String): List<IntentEnvelopeEntity>
}
