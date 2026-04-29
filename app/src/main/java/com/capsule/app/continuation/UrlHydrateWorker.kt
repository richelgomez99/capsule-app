package com.capsule.app.continuation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.capsule.app.ai.LlmProviderRouter
import com.capsule.app.ai.NanoSummariser
import com.capsule.app.data.ipc.IEnvelopeRepository
import com.capsule.app.net.CanonicalUrlHasher
import com.capsule.app.net.ipc.FetchResultParcel
import com.capsule.app.net.ipc.INetworkGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.URI

/**
 * T066 — URL hydrate worker. **SKELETON (Phase 5 Agent B slice).**
 *
 * What this class owns (Agent B):
 *   - WorkManager entry point + `runAttemptCount` / MAX_ATTEMPTS gating.
 *   - Binding [INetworkGateway] in the `:net` process
 *     (mirrors the ServiceConnection pattern used by
 *     [com.capsule.app.diary.BinderDiaryRepository]).
 *   - Calling [INetworkGateway.fetchPublicUrl] with a 10 s timeout
 *     (contract §4.1 step 3).
 *   - Running [NanoSummariser] against the returned readable HTML
 *     (contract §4.1 step 5c), degrading to `summaryModel="fallback"`.
 *   - Classifying `FetchResultParcel.errorKind` as retriable vs
 *     permanent (contract §4.1 step 4; §9 error taxonomy).
 *
 * What this class DOES NOT own (merge-zone / Agent A+merge):
 *   - Writing `ContinuationResultEntity` (needs `canonicalUrlHash` via
 *     `com.capsule.app.net.CanonicalUrlHasher` — Agent A scope).
 *   - Updating `ContinuationEntity.status` — Room transaction lives in
 *     `com.capsule.app.data.*` (merge zone).
 *   - Emitting `CONTINUATION_COMPLETED` / `CONTINUATION_FAILED` /
 *     `NETWORK_FETCH` audit rows — merge zone owns the
 *     `AuditLogWriter` transaction boundary.
 *
 * Those three side-effects are marked `TODO(merge-zone T066)` inside
 * [doWork] so the wiring slice is a pure additive change.
 *
 * See contracts/continuation-engine-contract.md §4.1 for the full step
 * list and §9 for the status / retry taxonomy.
 */
class UrlHydrateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val envelopeId = inputData.getString(ContinuationEngine.KEY_ENVELOPE_ID)
            ?: return Result.failure()
        val continuationId = inputData.getString(ContinuationEngine.KEY_CONTINUATION_ID)
            ?: return Result.failure()
        val url = inputData.getString(ContinuationEngine.KEY_URL)
            ?: return Result.failure()

        val outcome = runHydration(applicationContext, url)

        // T066 completion — write-back through the :ml repository binder.
        // NETWORK_FETCH is emitted upstream by NetworkGatewayImpl (T063);
        // here we only persist the hydration result + status transition +
        // CONTINUATION_COMPLETED audit row (all in one Room transaction).
        //
        // Only called on terminal outcomes — retriable failures return
        // `Result.retry()` below without touching Room, so the
        // ContinuationEntity stays PENDING until the next attempt.
        val writeBack: Boolean = when (outcome.classification) {
            Classification.SUCCESS,
            Classification.PERMANENT_FAILURE -> true
            Classification.RETRIABLE_FAILURE ->
                runAttemptCount + 1 >= ContinuationEngine.MAX_ATTEMPTS
        }
        if (writeBack) {
            val ok = outcome.classification == Classification.SUCCESS
            val canonicalUrl = outcome.fetch?.finalUrl ?: url
            val canonicalUrlHash = runCatching {
                CanonicalUrlHasher.hash(canonicalUrl)
            }.getOrNull()
            val domain = outcome.fetch?.canonicalHost
                ?: runCatching { URI(canonicalUrl).host }.getOrNull()
            try {
                val repo = repositoryBinder(applicationContext)
                repo?.completeUrlHydration(
                    continuationId,
                    envelopeId,
                    canonicalUrl,
                    canonicalUrlHash,
                    ok,
                    outcome.fetch?.title?.takeIf { ok },
                    domain?.takeIf { ok },
                    outcome.summary?.text?.takeIf { ok },
                    outcome.summaryModel,
                    outcome.failureReason
                )
            } catch (_: Throwable) {
                // Binder failure — let WorkManager retry the whole unit
                // rather than lose the write-back. If we've already spent
                // MAX_ATTEMPTS on the fetch, re-run is harmless: the
                // outcome is deterministic and the fetch is cached in the
                // network layer's host-cooldown state for repeat hosts.
                if (runAttemptCount + 1 < ContinuationEngine.MAX_ATTEMPTS) return Result.retry()
            }
        }

        return when (outcome.classification) {
            Classification.SUCCESS -> Result.success()
            Classification.PERMANENT_FAILURE -> Result.failure()
            Classification.RETRIABLE_FAILURE ->
                if (runAttemptCount + 1 >= ContinuationEngine.MAX_ATTEMPTS) Result.failure()
                else Result.retry()
        }
    }

    enum class Classification { SUCCESS, RETRIABLE_FAILURE, PERMANENT_FAILURE }

    data class HydrateOutcome(
        val classification: Classification,
        val fetch: FetchResultParcel?,
        val summary: NanoSummariser.Summary?,
        val summaryModel: String,
        val failureReason: String?
    )

    companion object {
        /**
         * Test seam — swap to a fake [INetworkGateway] factory in
         * [com.capsule.app.continuation.UrlHydrateWorkerTest] and
         * merge-zone integration tests. Production default binds the
         * `:net` service.
         */
        @Volatile
        internal var gatewayBinder: suspend (Context) -> INetworkGateway? = ::bindGatewayDefault

        /** Test seam for [NanoSummariser]. Default builds one over v1 Nano. */
        @Volatile
        internal var summariserFactory: (Context) -> NanoSummariser = { context ->
            NanoSummariser(LlmProviderRouter.createPreferLocal(context))
        }

        /**
         * T066 completion — binds [IEnvelopeRepository] in `:ml` so the
         * worker can write back from the default WorkManager process.
         * Swappable in tests to verify the write-back call without
         * spinning up the real service.
         */
        @Volatile
        internal var repositoryBinder: suspend (Context) -> IEnvelopeRepository? =
            ::bindRepositoryDefault

        /**
         * `errorKind` values from contracts/network-gateway-contract.md §4
         * that must NOT be retried — the URL is fundamentally unfetchable
         * and another attempt would produce the same error.
         */
        private val NON_RETRIABLE_ERRORS: Set<String> = setOf(
            "blocked_host",
            "invalid_url",
            "not_https",
            "unauthorized",
            "too_large",
            "redirect_loop"
        )

        /**
         * Pure hydration logic, extracted for testability.
         * Safe to call with a faked [gatewayBinder] / [summariserFactory].
         */
        internal suspend fun runHydration(
            context: Context,
            url: String
        ): HydrateOutcome {
            android.util.Log.i("UrlHydrate", "runHydration start url=$url")
            val gateway = gatewayBinder(context)
            if (gateway == null) {
                android.util.Log.w("UrlHydrate", "gateway bind returned null — will retry")
                return HydrateOutcome(
                    classification = Classification.RETRIABLE_FAILURE,
                    fetch = null,
                    summary = null,
                    summaryModel = NanoSummariser.FALLBACK_MODEL_LABEL,
                    failureReason = "gateway_unavailable"
                )
            }

            val fetch: FetchResultParcel = try {
                gateway.fetchPublicUrl(url, FETCH_TIMEOUT_MS)
            } catch (t: Throwable) {
                android.util.Log.w("UrlHydrate", "fetch threw ${t.javaClass.simpleName}: ${t.message}")
                return HydrateOutcome(
                    classification = Classification.RETRIABLE_FAILURE,
                    fetch = null,
                    summary = null,
                    summaryModel = NanoSummariser.FALLBACK_MODEL_LABEL,
                    failureReason = "gateway_throw:${t.javaClass.simpleName}"
                )
            }

            if (!fetch.ok) {
                val errorKind = fetch.errorKind.orEmpty()
                val permanent = errorKind in NON_RETRIABLE_ERRORS
                android.util.Log.i(
                    "UrlHydrate",
                    "fetch !ok errorKind=$errorKind permanent=$permanent finalUrl=${fetch.finalUrl}"
                )
                return HydrateOutcome(
                    classification = if (permanent) Classification.PERMANENT_FAILURE
                                     else Classification.RETRIABLE_FAILURE,
                    fetch = fetch,
                    summary = null,
                    summaryModel = NanoSummariser.FALLBACK_MODEL_LABEL,
                    failureReason = errorKind.ifBlank { "unknown" }
                )
            }

            android.util.Log.i(
                "UrlHydrate",
                "fetch ok title=${fetch.title?.take(60)} host=${fetch.canonicalHost} htmlLen=${fetch.readableHtml?.length}"
            )
            val summariser = summariserFactory(context)
            val slug = fetch.readableHtml.orEmpty()
            val summary = summariser.summarise(fetch.title, slug)
            android.util.Log.i(
                "UrlHydrate",
                "summary model=${summary?.model} text.len=${summary?.text?.length}"
            )

            return HydrateOutcome(
                classification = Classification.SUCCESS,
                fetch = fetch,
                summary = summary,
                summaryModel = summary?.model ?: NanoSummariser.FALLBACK_MODEL_LABEL,
                failureReason = null
            )
        }

        const val FETCH_TIMEOUT_MS: Long = 10_000L
        private const val BIND_TIMEOUT_MS: Long = 5_000L

        /**
         * Default production binder. Same `ServiceConnection` pattern
         * used by [com.capsule.app.diary.BinderDiaryRepository]; adapted
         * to the `INetworkGateway` binder signature.
         *
         * NOTE: the resulting binding is intentionally left alive for
         * the duration of the work — WorkManager kills the process on
         * cancellation which implicitly unbinds. A future merge-zone
         * refactor can promote this to a proper closeable handle.
         */
        private suspend fun bindGatewayDefault(context: Context): INetworkGateway? =
            withContext(Dispatchers.Main) {
                val deferred = CompletableDeferred<INetworkGateway?>()
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val stub = INetworkGateway.Stub.asInterface(service)
                        if (!deferred.isCompleted) deferred.complete(stub)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        // No-op: WorkManager will re-enqueue if the fetch
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

        /**
         * T066 completion default binder — mirrors [bindGatewayDefault]
         * for [IEnvelopeRepository]. Same tradeoffs: binding is leaked
         * for the duration of the work unit and reclaimed when WorkManager
         * kills the process.
         */
        private suspend fun bindRepositoryDefault(context: Context): IEnvelopeRepository? =
            withContext(Dispatchers.Main) {
                val deferred = CompletableDeferred<IEnvelopeRepository?>()
                val connection = object : ServiceConnection {
                    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                        val stub = IEnvelopeRepository.Stub.asInterface(service)
                        if (!deferred.isCompleted) deferred.complete(stub)
                    }

                    override fun onServiceDisconnected(name: ComponentName?) {
                        // No-op.
                    }
                }
                val intent = Intent("com.capsule.app.action.BIND_ENVELOPE_REPOSITORY").apply {
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
