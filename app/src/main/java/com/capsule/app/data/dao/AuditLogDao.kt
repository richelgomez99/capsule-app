package com.capsule.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.capsule.app.data.entity.AuditLogEntryEntity

@Dao
interface AuditLogDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: AuditLogEntryEntity)

    @Query("SELECT * FROM audit_log WHERE at >= :startMillis AND at < :endMillis ORDER BY at DESC LIMIT 1000")
    suspend fun entriesForDay(startMillis: Long, endMillis: Long): List<AuditLogEntryEntity>

    @Query("SELECT * FROM audit_log WHERE envelopeId = :envelopeId ORDER BY at DESC")
    suspend fun entriesForEnvelope(envelopeId: String): List<AuditLogEntryEntity>

    @Query("SELECT COUNT(*) FROM audit_log WHERE at >= :startMillis AND at < :endMillis AND action = :action")
    suspend fun countForDay(startMillis: Long, endMillis: Long, action: String): Int

    @Query("DELETE FROM audit_log WHERE at < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}
