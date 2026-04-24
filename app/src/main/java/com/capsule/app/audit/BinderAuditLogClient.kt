package com.capsule.app.audit

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.capsule.app.data.ipc.AuditEntryParcel
import com.capsule.app.data.ipc.EnvelopeRepositoryService
import com.capsule.app.data.ipc.IAuditLog
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * T088 — bound-client wrapper that reaches the audit-log surface exposed by
 * [EnvelopeRepositoryService] via action [EnvelopeRepositoryService.ACTION_BIND_AUDIT_LOG].
 */
class BinderAuditLogClient(
    private val context: Context
) {
    private var binder: IAuditLog? = null
    private var connection: ServiceConnection? = null

    suspend fun connect(): IAuditLog = withContext(Dispatchers.Main) {
        binder?.let { return@withContext it }
        val deferred = CompletableDeferred<IAuditLog>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val stub = IAuditLog.Stub.asInterface(service)
                binder = stub
                deferred.complete(stub)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
            }
        }
        connection = conn
        val intent = Intent(EnvelopeRepositoryService.ACTION_BIND_AUDIT_LOG).apply {
            `package` = context.packageName
        }
        val bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        if (!bound) {
            connection = null
            throw IllegalStateException("bindService(AuditLog) failed")
        }
        deferred.await()
    }

    fun disconnect() {
        val conn = connection ?: return
        runCatching { context.unbindService(conn) }
        connection = null
        binder = null
    }

    suspend fun entriesForDay(isoDate: String): List<AuditEntryParcel> {
        val log = connect()
        return withContext(Dispatchers.IO) {
            log.entriesForDay(isoDate) ?: emptyList()
        }
    }
}
