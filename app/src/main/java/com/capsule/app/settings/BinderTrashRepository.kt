package com.capsule.app.settings

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.capsule.app.data.ipc.EnvelopeViewParcel
import com.capsule.app.data.ipc.IEnvelopeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * T091a — binds [com.capsule.app.data.ipc.EnvelopeRepositoryService] in
 * the `:ml` process and exposes the three trash operations. Mirrors
 * [com.capsule.app.diary.BinderDiaryRepository] but keeps the surface
 * narrow (list/restore/purge only). One instance per Activity; bind in
 * `onCreate`, unbind in `onDestroy`.
 */
class BinderTrashRepository(
    private val context: Context
) : TrashRepository {

    private var binder: IEnvelopeRepository? = null
    private var connection: ServiceConnection? = null

    suspend fun connect(): IEnvelopeRepository = withContext(Dispatchers.Main) {
        binder?.let { return@withContext it }
        val deferred = CompletableDeferred<IEnvelopeRepository>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val stub = IEnvelopeRepository.Stub.asInterface(service)
                binder = stub
                deferred.complete(stub)
            }
            override fun onServiceDisconnected(name: ComponentName?) {
                binder = null
            }
        }
        connection = conn
        val intent = Intent("com.capsule.app.action.BIND_ENVELOPE_REPOSITORY").apply {
            `package` = context.packageName
        }
        val bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        if (!bound) {
            connection = null
            throw IllegalStateException("bindService(EnvelopeRepositoryService) failed")
        }
        deferred.await()
    }

    fun disconnect() {
        val conn = connection ?: return
        runCatching { context.unbindService(conn) }
        connection = null
        binder = null
    }

    override suspend fun listSoftDeleted(days: Int): List<EnvelopeViewParcel> {
        val repo = connect()
        return withContext(Dispatchers.IO) {
            repo.listSoftDeletedWithinDays(days) ?: emptyList()
        }
    }

    override suspend fun restore(envelopeId: String) {
        val repo = connect()
        withContext(Dispatchers.IO) { repo.restoreFromTrash(envelopeId) }
    }

    override suspend fun hardPurge(envelopeId: String) {
        val repo = connect()
        withContext(Dispatchers.IO) { repo.hardDelete(envelopeId) }
    }
}
