package com.capsule.app.diary

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.capsule.app.data.ipc.DayPageParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel
import com.capsule.app.data.ipc.IEnvelopeObserver
import com.capsule.app.data.ipc.IEnvelopeRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * T052 — production adapter that turns the AIDL binder
 * [IEnvelopeRepository] into a [DiaryRepository] the VM understands.
 *
 * Ownership: one instance per [android.app.Activity]. The activity owns
 * the [ServiceConnection] lifecycle; this class only wraps a bound binder
 * with a `Flow` surface. Not thread-safe across binds — the activity is
 * expected to call `connect()` in `onStart()` and `disconnect()` in
 * `onStop()`.
 *
 * Why it lives in `:ui` and not in the VM: the VM has to stay JVM-testable
 * (no `Context`, no `IBinder`). See `DiaryViewModelTest` — it uses a
 * `FakeRepo` that implements [DiaryRepository] directly.
 */
class BinderDiaryRepository(
    private val context: Context
) : DiaryRepository {

    private val lock = Mutex()
    private var binder: IEnvelopeRepository? = null
    private var connection: ServiceConnection? = null
    private var readyDeferred: CompletableDeferred<IEnvelopeRepository>? = null

    /**
     * Bind to [com.capsule.app.data.ipc.EnvelopeRepositoryService] in the
     * `:ml` process. Suspends until the binder is handed back. Safe to
     * call repeatedly — second call is a no-op while still connected.
     */
    suspend fun connect(): IEnvelopeRepository = withContext(Dispatchers.Main) {
        binder?.let { return@withContext it }

        val deferred = CompletableDeferred<IEnvelopeRepository>()
        readyDeferred = deferred
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

    /** Unbind. Safe if not bound. */
    fun disconnect() {
        val conn = connection ?: return
        runCatching { context.unbindService(conn) }
        connection = null
        binder = null
        readyDeferred = null
    }

    override fun observeDay(isoDate: String): Flow<DayPageParcel> = callbackFlow {
        val repo = connect()
        val observer = object : IEnvelopeObserver.Stub() {
            override fun onDayLoaded(page: DayPageParcel) {
                trySend(page)
            }
        }
        repo.observeDay(isoDate, observer)
        awaitClose {
            runCatching { repo.stopObserving(observer) }
        }
    }

    override suspend fun reassignIntent(envelopeId: String, newIntentName: String, reason: String?) {
        val repo = connect()
        withContext(Dispatchers.IO) {
            repo.reassignIntent(envelopeId, newIntentName, reason)
        }
    }

    override suspend fun archive(envelopeId: String) {
        val repo = connect()
        withContext(Dispatchers.IO) { repo.archive(envelopeId) }
    }

    override suspend fun delete(envelopeId: String) {
        val repo = connect()
        withContext(Dispatchers.IO) { repo.delete(envelopeId) }
    }

    override suspend fun retryHydration(envelopeId: String) {
        val repo = connect()
        withContext(Dispatchers.IO) { repo.retryHydration(envelopeId) }
    }

    override suspend fun getEnvelope(envelopeId: String): EnvelopeViewParcel {
        val repo = connect()
        return withContext(Dispatchers.IO) { repo.getEnvelope(envelopeId) }
    }

    override suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String> {
        val repo = connect()
        return withContext(Dispatchers.IO) {
            repo.distinctDayLocalsWithContent(limit, offset) ?: emptyList()
        }
    }
}
