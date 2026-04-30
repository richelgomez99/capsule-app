package com.capsule.app.diary

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.capsule.app.action.ActionExecutorService
import com.capsule.app.action.ipc.ActionExecuteRequestParcel
import com.capsule.app.action.ipc.ActionExecuteResultParcel
import com.capsule.app.action.ipc.IActionExecutor
import com.capsule.app.data.ClusterCardModel
import com.capsule.app.data.ClusterMemberRef
import com.capsule.app.data.ipc.ActionProposalParcel
import com.capsule.app.data.ipc.ClusterCardParcel
import com.capsule.app.data.ipc.DayPageParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel
import com.capsule.app.data.ipc.IActionProposalObserver
import com.capsule.app.data.ipc.IClusterObserver
import com.capsule.app.data.ipc.IEnvelopeObserver
import com.capsule.app.data.ipc.IEnvelopeRepository
import com.capsule.app.data.model.ClusterState
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

    // T054 — separate :capture IActionExecutor binding. Held independently
    // from the :ml binder because the two services live in different
    // processes and have independent ServiceConnection lifecycles.
    private var executorBinder: IActionExecutor? = null
    private var executorConnection: ServiceConnection? = null
    private var executorReadyDeferred: CompletableDeferred<IActionExecutor>? = null

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
        val conn = connection
        if (conn != null) {
            runCatching { context.unbindService(conn) }
            connection = null
            binder = null
            readyDeferred = null
        }
        val execConn = executorConnection
        if (execConn != null) {
            runCatching { context.unbindService(execConn) }
            executorConnection = null
            executorBinder = null
            executorReadyDeferred = null
        }
    }

    /**
     * T054 — bind the `:capture` `IActionExecutor`. Called lazily on the
     * first action confirm; production callers may pre-warm in
     * `DiaryActivity.onCreate`. Idempotent like [connect].
     */
    suspend fun connectExecutor(): IActionExecutor = withContext(Dispatchers.Main) {
        executorBinder?.let { return@withContext it }

        val deferred = CompletableDeferred<IActionExecutor>()
        executorReadyDeferred = deferred
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val stub = IActionExecutor.Stub.asInterface(service)
                executorBinder = stub
                deferred.complete(stub)
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                executorBinder = null
            }
        }
        executorConnection = conn
        val intent = Intent(ActionExecutorService.ACTION_BIND_EXECUTOR).apply {
            `package` = context.packageName
        }
        val bound = context.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        if (!bound) {
            executorConnection = null
            throw IllegalStateException("bindService(ActionExecutorService) failed")
        }
        deferred.await()
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

    // ---- Spec 003 v1.1 — Orbit Actions (T053) -----------------------------

    override fun observeProposals(envelopeId: String): Flow<List<ActionProposalParcel>> =
        callbackFlow {
            val repo = connect()
            val observer = object : IActionProposalObserver.Stub() {
                override fun onProposals(proposals: MutableList<ActionProposalParcel>?) {
                    trySend(proposals?.toList() ?: emptyList())
                }
            }
            repo.observeProposalsForEnvelope(envelopeId, observer)
            awaitClose {
                runCatching { repo.stopObservingProposals(observer) }
            }
        }

    override suspend fun markProposalConfirmed(proposalId: String): Boolean {
        val repo = connect()
        return withContext(Dispatchers.IO) { repo.markProposalConfirmed(proposalId) }
    }

    override suspend fun markProposalDismissed(proposalId: String): Boolean {
        val repo = connect()
        return withContext(Dispatchers.IO) { repo.markProposalDismissed(proposalId) }
    }

    override suspend fun executeAction(
        request: ActionExecuteRequestParcel
    ): ActionExecuteResultParcel {
        val exec = connectExecutor()
        return withContext(Dispatchers.IO) { exec.execute(request) }
    }

    override suspend fun cancelWithinUndoWindow(executionId: String): Boolean {
        val exec = connectExecutor()
        return withContext(Dispatchers.IO) { exec.cancelWithinUndoWindow(executionId) }
    }

    override suspend fun setTodoItemDone(envelopeId: String, itemIndex: Int, done: Boolean) {
        val repo = connect()
        withContext(Dispatchers.IO) { repo.setTodoItemDone(envelopeId, itemIndex, done) }
    }

    // ---- Spec 002 Phase 11 Block 9 — Cluster surface (T148) ---------------

    override fun observeClusters(): Flow<List<ClusterCardModel>> = callbackFlow {
        val repo = connect()
        val observer = object : IClusterObserver.Stub() {
            override fun onClustersChanged(clusters: MutableList<ClusterCardParcel>?) {
                val mapped = clusters?.map { it.toModel() } ?: emptyList()
                trySend(mapped)
            }
        }
        repo.observeClusters(observer)
        awaitClose {
            runCatching { repo.stopObservingClusters(observer) }
        }
    }

    private fun ClusterCardParcel.toModel(): ClusterCardModel {
        val members = memberEnvelopeIds.zip(memberIndices) { id, idx ->
            ClusterMemberRef(envelopeId = id, memberIndex = idx)
        }
        return ClusterCardModel(
            clusterId = clusterId,
            state = ClusterState.valueOf(state),
            timeBucketStart = timeBucketStart,
            timeBucketEnd = timeBucketEnd,
            modelLabel = modelLabel,
            members = members
        )
    }

    // Phase 11 Block 10 (T148 review FU#2) — user-driven dismiss.
    override suspend fun dismissCluster(clusterId: String): Boolean {
        val repo = connect()
        return withContext(Dispatchers.IO) { repo.markClusterDismissed(clusterId) }
    }
}
