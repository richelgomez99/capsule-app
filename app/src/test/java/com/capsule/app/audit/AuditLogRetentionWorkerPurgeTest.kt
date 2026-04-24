package com.capsule.app.audit

import com.capsule.app.data.dao.AuditLogDao
import com.capsule.app.data.entity.AuditLogEntryEntity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T089 — verifies `AuditLogRetentionWorker.purge` forwards the cutoff to
 * `AuditLogDao.deleteOlderThan` and returns the affected row count.
 */
class AuditLogRetentionWorkerPurgeTest {

    private class FakeDao(var deleted: Int = 0, var seenCutoff: Long = 0L) : AuditLogDao {
        override suspend fun insert(entry: AuditLogEntryEntity) = Unit
        override suspend fun entriesForDay(startMillis: Long, endMillis: Long): List<AuditLogEntryEntity> = emptyList()
        override suspend fun entriesForEnvelope(envelopeId: String): List<AuditLogEntryEntity> = emptyList()
        override suspend fun countForDay(startMillis: Long, endMillis: Long, action: String): Int = 0
        override suspend fun deleteOlderThan(cutoffMillis: Long): Int {
            seenCutoff = cutoffMillis
            return deleted
        }
        override suspend fun deleteByEnvelopeId(envelopeId: String) = Unit
        override suspend fun listAll(): List<AuditLogEntryEntity> = emptyList()
    }

    @Test
    fun `purge forwards cutoff and returns deleted count`() = runTest {
        val dao = FakeDao(deleted = 42)
        val cutoff = 1_700_000_000_000L
        val n = AuditLogRetentionWorker.purge(dao, cutoff)
        assertEquals(42, n)
        assertEquals(cutoff, dao.seenCutoff)
    }

    @Test
    fun `retention window is 90 days in millis`() {
        val expected = 90L * 24L * 60L * 60L * 1000L
        assertEquals(expected, AuditLogRetentionWorker.retentionWindowMillis())
    }
}
