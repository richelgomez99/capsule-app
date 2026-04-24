package com.capsule.app.audit

import com.capsule.app.data.dao.AuditLogDao
import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.ipc.AuditEntryParcel
import com.capsule.app.data.ipc.IAuditLog
import kotlinx.coroutines.runBlocking
import java.time.LocalDate
import java.time.ZoneId

/**
 * T087 — in-process implementation of `IAuditLog.Stub`.
 *
 * Hosted inside the `:ml` process alongside the Room-backed audit DAO.
 * Results are bounded at 1000 rows per call (contracts/audit-log-contract.md §5);
 * the DAO already enforces this via `LIMIT 1000`.
 *
 * Caller threading: AIDL dispatches inbound calls on the service's binder
 * thread pool. DAO calls are `suspend`, so we bridge with `runBlocking` —
 * safe here because the binder thread is not the main thread.
 */
class AuditLogImpl(
    private val auditLogDao: AuditLogDao,
    private val zoneId: ZoneId = ZoneId.systemDefault()
) : IAuditLog.Stub() {

    override fun entriesForDay(isoDate: String): List<AuditEntryParcel> {
        val (start, end) = localDayBounds(isoDate) ?: return emptyList()
        return runBlocking {
            auditLogDao.entriesForDay(start, end).map { it.toParcel() }
        }
    }

    override fun entriesForEnvelope(envelopeId: String): List<AuditEntryParcel> {
        return runBlocking {
            auditLogDao.entriesForEnvelope(envelopeId)
                .take(1000)
                .map { it.toParcel() }
        }
    }

    override fun countForDay(isoDate: String, actionName: String): Int {
        val (start, end) = localDayBounds(isoDate) ?: return 0
        return runBlocking { auditLogDao.countForDay(start, end, actionName) }
    }

    private fun localDayBounds(isoDate: String): Pair<Long, Long>? {
        val date = runCatching { LocalDate.parse(isoDate) }.getOrNull() ?: return null
        val start = date.atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        return start to end
    }

    private fun AuditLogEntryEntity.toParcel(): AuditEntryParcel = AuditEntryParcel(
        id = id,
        atMillis = at,
        action = action.name,
        description = description,
        envelopeId = envelopeId,
        extraJson = extraJson
    )
}
