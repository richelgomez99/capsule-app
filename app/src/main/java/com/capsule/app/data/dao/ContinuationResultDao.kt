package com.capsule.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.capsule.app.data.entity.ContinuationResultEntity

@Dao
interface ContinuationResultDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(result: ContinuationResultEntity)

    @Query("SELECT * FROM continuation_result WHERE envelopeId = :envelopeId ORDER BY producedAt DESC")
    suspend fun getByEnvelopeId(envelopeId: String): List<ContinuationResultEntity>

    @Query("SELECT * FROM continuation_result WHERE id = :id LIMIT 1")
    suspend fun findById(id: String): ContinuationResultEntity?

    @Query("SELECT * FROM continuation_result WHERE canonicalUrlHash = :hash LIMIT 1")
    suspend fun findByCanonicalUrlHash(hash: String): ContinuationResultEntity?

    /** T093 — full dump for user-initiated export. */
    @Query("SELECT * FROM continuation_result ORDER BY producedAt DESC")
    suspend fun listAll(): List<ContinuationResultEntity>
}
