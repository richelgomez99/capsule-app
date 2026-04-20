package com.capsule.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.capsule.app.data.entity.IntentEnvelopeEntity
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
        ORDER BY createdAt DESC
        """
    )
    fun observeDay(dayLocal: String): Flow<List<IntentEnvelopeEntity>>

    @Query("UPDATE intent_envelope SET intent = :intent, intentSource = :intentSource, intentConfidence = :confidence, intentHistoryJson = :historyJson WHERE id = :id")
    suspend fun updateIntent(id: String, intent: String, intentSource: String, confidence: Float?, historyJson: String)

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
}
