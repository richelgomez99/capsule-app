package com.capsule.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.capsule.app.data.entity.ContinuationEntity

@Dao
interface ContinuationDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(continuation: ContinuationEntity)

    @Query("SELECT * FROM continuation WHERE id = :id")
    suspend fun getById(id: String): ContinuationEntity?

    @Query("SELECT * FROM continuation WHERE envelopeId = :envelopeId ORDER BY scheduledAt DESC")
    suspend fun getByEnvelopeId(envelopeId: String): List<ContinuationEntity>

    @Query("UPDATE continuation SET status = :status, startedAt = :startedAt, attemptCount = attemptCount + 1 WHERE id = :id")
    suspend fun markRunning(id: String, status: String, startedAt: Long)

    @Query("UPDATE continuation SET status = :status, completedAt = :completedAt, failureReason = :failureReason WHERE id = :id")
    suspend fun markCompleted(id: String, status: String, completedAt: Long, failureReason: String?)

    @Query("SELECT * FROM continuation WHERE status IN ('PENDING', 'FAILED_TRANSIENT') ORDER BY scheduledAt ASC")
    suspend fun pendingOrRetryable(): List<ContinuationEntity>

    /** T093 — full dump for user-initiated export. */
    @Query("SELECT * FROM continuation ORDER BY scheduledAt DESC")
    suspend fun listAll(): List<ContinuationEntity>

    /** T105 — per-status counts for DebugDumpReceiver. */
    @Query("SELECT status, COUNT(*) AS n FROM continuation GROUP BY status")
    suspend fun countByStatus(): List<StatusCount>
}

data class StatusCount(val status: String, val n: Int)
