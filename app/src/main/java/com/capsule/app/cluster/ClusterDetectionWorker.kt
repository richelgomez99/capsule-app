package com.capsule.app.cluster

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.LlmProviderRouter
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.net.ipc.INetworkGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * T129 / Phase 11 Block 4 — daily cluster-detection background scan.
 *
 * Runs at 03:00 local on charger + UNMETERED + battery-not-low (Principle
 * IV). The worker itself only:
 *  1. binds the `:net` [INetworkGateway] (Principle II — embedding RPC
 *     egress goes through the single networked process; the default
 *     process never opens sockets),
 *  2. resolves an [LlmProvider] via [LlmProviderRouter.create]
 *     (cloud-by-default; local Nano under [com.capsule.app.RuntimeFlags.useLocalAi]),
 *  3. delegates to [ClusterDetector.detect],
 *  4. maps the outcome to the right WorkManager [Result].
 *
 * **Result mapping** (Phase 11 Block 4 contract):
 *  - [ClusterDetector.Outcome.Skipped]   → [Result.success] (silent
 *    skip per FR-030 / FR-038 — the audit row is the only signal).
 *  - [ClusterDetector.Outcome.Completed] → [Result.success].
 *  - [ClusterDetector.DimensionMismatchInRun] → [Result.failure] —
 *    structural / schema error per FR-038, retrying cannot fix.
 *  - Failure to bind [INetworkGateway], or any other [Throwable]
 *    escaping [ClusterDetector.detect] → [Result.retry] (transient
 *    embedding / network / Room contention).
 *  - "All embeds returned null while scanning ≥1 candidate" → also
 *    [Result.retry], because the realistic cause is `:net` being
 *    momentarily unavailable rather than every envelope being blank.
 *
 * **Bounded logging** (Principle XIV): never logs envelope text,
 * embedding vectors, domains, or hostnames. Only counts and request
 * IDs.
 *
 * **Idempotency**: enforced one layer down by
 * [com.capsule.app.data.dao.ClusterDao.findClusterCandidates], which
 * excludes envelope ids already attached to a cluster_member row. A
 * re-run on the same window is a no-op without any worker-level lock.
 */
class ClusterDetectionWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val gateway: INetworkGateway? = bindNetworkGateway(applicationContext)
        if (gateway == null) {
            // The :net process didn't come up in time — that's a
            // transient condition (boot race, low-memory rebind).
            // WorkManager will back off and retry.
            Log.i(LOG_TAG, "cluster_detection_bind_failed")
            return Result.retry()
        }

        val db = OrbitDatabase.getInstance(applicationContext)
        val llm: LlmProvider = providerFactory(applicationContext, gateway)
        val detector = ClusterDetector(
            clusterDao = db.clusterDao(),
            auditLogDao = db.auditLogDao(),
            llm = llm,
            auditWriter = AuditLogWriter()
        )

        return try {
            when (val outcome = detector.detect()) {
                is ClusterDetector.Outcome.Skipped -> Result.success()
                is ClusterDetector.Outcome.Completed -> {
                    val transientEmbedFailure =
                        outcome.candidatesScanned > 0 && outcome.embeddingsObtained == 0
                    if (transientEmbedFailure) {
                        Log.i(
                            LOG_TAG,
                            "cluster_detection_transient_embed_failure scanned=${outcome.candidatesScanned}"
                        )
                        Result.retry()
                    } else {
                        Result.success()
                    }
                }
            }
        } catch (e: ClusterDetector.DimensionMismatchInRun) {
            // FR-038: dimensionality mismatch is permanent — retrying
            // cannot fix a schema/version mismatch.
            Log.w(LOG_TAG, "cluster_detection_dimension_mismatch")
            Result.failure()
        } catch (t: Throwable) {
            Log.w(LOG_TAG, "cluster_detection_throw kind=${t.javaClass.simpleName}")
            Result.retry()
        }
    }

    companion object {
        const val UNIQUE_WORK_NAME: String = "orbit.cluster_detection"
        private const val LOG_TAG: String = "ClusterDetectionWorker"
        private const val BIND_TIMEOUT_MS: Long = 5_000L

        /**
         * Test seam — overridable to inject a fake [LlmProvider] +
         * skip the [INetworkGateway] bind. Production resolves through
         * [LlmProviderRouter.create] which honours
         * [com.capsule.app.RuntimeFlags.useLocalAi].
         */
        @Volatile
        var providerFactory: (Context, INetworkGateway) -> LlmProvider =
            { ctx, gw -> LlmProviderRouter.create(ctx, gw) }

        /**
         * Test seam — overridable to inject a fake [INetworkGateway]
         * without binding the real `:net` service. Default binds via
         * the same `BIND_NETWORK_GATEWAY` intent the rest of the app
         * uses (see [com.capsule.app.continuation.UrlHydrateWorker]).
         */
        @Volatile
        var gatewayBinder: suspend (Context) -> INetworkGateway? = ::bindNetworkGatewayDefault

        private suspend fun bindNetworkGateway(context: Context): INetworkGateway? =
            gatewayBinder(context)

        private suspend fun bindNetworkGatewayDefault(context: Context): INetworkGateway? =
            withContext(Dispatchers.Main) {
                val deferred = CompletableDeferred<INetworkGateway?>()
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val stub = INetworkGateway.Stub.asInterface(service)
                        if (!deferred.isCompleted) deferred.complete(stub)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        // No-op: WorkManager will re-enqueue if the embed
                        // call itself throws on the dead binder.
                    }
                }
                val intent = Intent("com.capsule.app.action.BIND_NETWORK_GATEWAY").apply {
                    `package` = context.packageName
                }
                val bound = try {
                    context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
                } catch (_: SecurityException) {
                    false
                }
                if (!bound) {
                    runCatching { context.unbindService(connection) }
                    return@withContext null
                }
                withTimeoutOrNull(BIND_TIMEOUT_MS) { deferred.await() }
            }
    }
}
