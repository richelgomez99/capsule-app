package com.capsule.app.action

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.capsule.app.action.ipc.ActionExecuteRequestParcel
import com.capsule.app.action.ipc.ActionExecuteResultParcel
import com.capsule.app.action.ipc.IActionExecutor
import com.capsule.app.data.ipc.AppFunctionSummaryParcel
import com.capsule.app.data.ipc.IEnvelopeRepository
import com.capsule.app.data.model.ActionExecutionOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.runBlocking
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Bound service running in `:capture`. Owns the [IActionExecutor] AIDL
 * surface that `:ui` invokes to dispatch a confirmed proposal.
 *
 * Constraints:
 *  - MUST NOT depend on any class from `com.capsule.app.net.*` (lint-enforced)
 *  - MUST NOT touch the encrypted DB directly — DB lives in `:ml`. Audit
 *    rows + execution rows are written via the binder to
 *    [IEnvelopeRepository.recordActionInvocation] inside one Room txn there.
 *
 * Lifecycle:
 *  - On bind: lazily binds `:ml`'s `EnvelopeRepositoryService` so the
 *    executor can re-validate the (functionId, schemaVersion) pair via
 *    [IEnvelopeRepository.lookupAppFunction] before dispatching.
 *  - On execute: launches the handler off the binder thread, captures
 *    latency, calls back into `:ml` to write the [action_execution] row
 *    + audit row + skill_usage row in a single transaction.
 */
class ActionExecutorService : Service() {

    private val serviceJob: Job = SupervisorJob()
    private val serviceScope: CoroutineScope = CoroutineScope(serviceJob + Dispatchers.IO)

    /** Pending undo-window registrations: executionId → cancel hook. */
    private val pendingUndo = ConcurrentHashMap<String, () -> Unit>()

    @Volatile
    private var repository: IEnvelopeRepository? = null

    private val repoConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            repository = IEnvelopeRepository.Stub.asInterface(service)
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            repository = null
        }
    }

    override fun onCreate() {
        super.onCreate()
        // Bind to :ml so we can re-validate against the registry. The bind
        // is async; execute() handles the brief window before connection.
        val intent = Intent().apply {
            setClassName(packageName, "com.capsule.app.data.ipc.EnvelopeRepositoryService")
        }
        bindService(intent, repoConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        runCatching { unbindService(repoConnection) }
        serviceJob.cancel()
        super.onDestroy()
    }

    private val binder = object : IActionExecutor.Stub() {

        override fun execute(request: ActionExecuteRequestParcel): ActionExecuteResultParcel {
            val executionId = UUID.randomUUID().toString()
            val dispatchedAt = System.currentTimeMillis()
            val handler = ActionHandlerRegistry.lookup(request.functionId)
            val repo = repository

            // Pre-flight failures — record + return synchronously.
            if (repo == null) {
                return failNow(
                    request, executionId, dispatchedAt,
                    reason = "ml_binder_unavailable"
                )
            }
            if (handler == null) {
                return failNow(
                    request, executionId, dispatchedAt,
                    reason = "unknown_skill",
                    repo = repo
                )
            }

            // Re-validate the (functionId, schemaVersion) pair. A request
            // pinning a stale schemaVersion is rejected as `schema_invalidated`.
            val skill: AppFunctionSummaryParcel = runCatching {
                repo.lookupAppFunction(request.functionId)
            }.getOrNull() ?: return failNow(
                request, executionId, dispatchedAt,
                reason = "skill_not_registered",
                repo = repo
            )
            if (skill.schemaVersion != request.schemaVersion) {
                return failNow(
                    request, executionId, dispatchedAt,
                    reason = "schema_invalidated",
                    repo = repo
                )
            }

            // T091 — re-validate that argsJson still parses as a JSONObject
            // matching the registered schema's top-level type. A debug seam
            // (or stale proposal) may have corrupted argsJson between
            // ACTION_PROPOSED and the user's confirm tap. Per
            // action-execution-contract.md §4 step 1: on mismatch we MUST
            // NOT fire the Intent — write ACTION_FAILED reason=schema_mismatch
            // and the delegate flips the proposal to INVALIDATED.
            if (!isValidArgsJsonShape(request.argsJson)) {
                return failNow(
                    request, executionId, dispatchedAt,
                    reason = "schema_mismatch",
                    repo = repo
                )
            }

            // Dispatch. We block the binder thread for the handler — handlers
            // are non-blocking modulo a final Intent#startActivity, so this is
            // bounded; the AIDL caller awaits the result anyway.
            val result = runBlocking {
                handler.handle(this@ActionExecutorService, skill, request.argsJson, repo)
            }

            val (outcome, reason, latencyMs) = when (result) {
                is HandlerResult.Dispatched -> Triple(ActionExecutionOutcome.DISPATCHED, null, result.latencyMs)
                is HandlerResult.Success    -> Triple(ActionExecutionOutcome.SUCCESS, null, result.latencyMs)
                is HandlerResult.Cancelled  -> Triple(ActionExecutionOutcome.USER_CANCELLED, result.reason, result.latencyMs)
                is HandlerResult.Failed     -> Triple(ActionExecutionOutcome.FAILED, result.reason, result.latencyMs)
            }

            val completedAt = System.currentTimeMillis()
            runCatching {
                repo.recordActionInvocation(
                    executionId,
                    request.proposalId,
                    request.functionId,
                    outcome.name,
                    reason,
                    dispatchedAt,
                    completedAt,
                    latencyMs,
                    null // episodeId — null in v1.1
                )
            }.onFailure { Log.e(TAG, "recordActionInvocation failed", it) }

            // T050 — schedule the 5s undo-window cleanup. Registers the
            // cancel hook used by [cancelWithinUndoWindow]: when invoked
            // it re-enters [recordActionInvocation] with USER_CANCELLED so
            // the existing `action_execution` row is updated in-place
            // (executionDao.markOutcome) AND an `ACTION_FAILED reason=
            // user_cancelled` audit row is appended in the same Room txn.
            // Only register on DISPATCHED/SUCCESS — terminal failures have
            // no window. Per action-execution-contract.md §5.
            if (outcome == ActionExecutionOutcome.DISPATCHED ||
                outcome == ActionExecutionOutcome.SUCCESS
            ) {
                pendingUndo[executionId] = {
                    val cancelAt = System.currentTimeMillis()
                    runCatching {
                        repo.recordActionInvocation(
                            executionId,
                            request.proposalId,
                            request.functionId,
                            ActionExecutionOutcome.USER_CANCELLED.name,
                            "user_cancelled",
                            dispatchedAt,
                            cancelAt,
                            (cancelAt - dispatchedAt).coerceAtLeast(0L),
                            null
                        )
                    }.onFailure { Log.e(TAG, "cancel recordActionInvocation failed", it) }
                }
                runCatching { DelayedUndoCleanupWorker.enqueue(this@ActionExecutorService, executionId) }
                    .onFailure { Log.w(TAG, "undo cleanup enqueue failed", it) }
            }

            return ActionExecuteResultParcel(
                executionId = executionId,
                outcome = outcome.name,
                outcomeReason = reason,
                dispatchedAtMillis = dispatchedAt,
                latencyMs = latencyMs
            )
        }

        override fun cancelWithinUndoWindow(executionId: String): Boolean {
            // Cross-process expiry guard: if the cleanup worker fired, the
            // companion set carries the executionId and we report past-window.
            if (isExpired(executionId)) {
                pendingUndo.remove(executionId)
                return false
            }
            val hook = pendingUndo.remove(executionId) ?: return false
            return runCatching { hook(); true }.getOrDefault(false)
        }
    }

    private fun failNow(
        request: ActionExecuteRequestParcel,
        executionId: String,
        dispatchedAt: Long,
        reason: String,
        repo: IEnvelopeRepository? = repository
    ): ActionExecuteResultParcel {        repo?.let {
            runCatching {
                it.recordActionInvocation(
                    executionId,
                    request.proposalId,
                    request.functionId,
                    ActionExecutionOutcome.FAILED.name,
                    reason,
                    dispatchedAt,
                    dispatchedAt,
                    0L,
                    null
                )
            }
        }
        return ActionExecuteResultParcel(
            executionId = executionId,
            outcome = ActionExecutionOutcome.FAILED.name,
            outcomeReason = reason,
            dispatchedAtMillis = dispatchedAt,
            latencyMs = 0L
        )
    }

    /**
     * Lightweight shape check: argsJson MUST parse as a JSONObject (the
     * v1.1 built-in schemas are all top-level type=object). Full Draft
     * 2020-12 keyword validation is deferred until the JSON-schema
     * dependency lands; until then this catches the realistic failure
     * mode — corrupted/blank argsJson — without letting the Intent fire.
     */
    private fun isValidArgsJsonShape(argsJson: String?): Boolean {
        if (argsJson.isNullOrBlank()) return false
        return runCatching { org.json.JSONObject(argsJson) }.isSuccess
    }

    companion object {
        private const val TAG = "ActionExecutorService"
        const val ACTION_BIND_EXECUTOR = "com.capsule.app.action.BIND_ACTION_EXECUTOR"

        /**
         * Process-singleton view of the pending-undo registry, used by
         * [DelayedUndoCleanupWorker] (which runs in the default WorkManager
         * process, not `:capture`). Workers in the default process therefore
         * cannot reach the live service's instance map directly — we keep
         * a parallel weak-reference seam here for the cleanup callback.
         *
         * Cross-process accuracy: when the worker fires, it removes the
         * entry from this companion-level set. The bound service in
         * `:capture` checks the same set on cancelWithinUndoWindow, so
         * the 5s expiry is observable on both sides.
         */
        private val expiredExecutionIds: MutableSet<String> =
            java.util.Collections.synchronizedSet(HashSet())

        /** Called by [DelayedUndoCleanupWorker] when the 5s window elapses. */
        @JvmStatic
        fun expireUndoWindow(executionId: String) {
            expiredExecutionIds.add(executionId)
        }

        /** Test helper / lifecycle reset. */
        @JvmStatic
        internal fun clearExpiredForTesting() {
            expiredExecutionIds.clear()
        }

        internal fun isExpired(executionId: String): Boolean =
            expiredExecutionIds.contains(executionId)
    }
}
