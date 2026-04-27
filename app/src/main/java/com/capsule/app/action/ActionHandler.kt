package com.capsule.app.action

import android.content.Context
import com.capsule.app.data.ipc.AppFunctionSummaryParcel
import com.capsule.app.data.ipc.IEnvelopeRepository

/**
 * Outcome of dispatching one action proposal. Mirrors
 * [com.capsule.app.data.model.ActionExecutionOutcome] but adds a
 * `latencyMs` measurement the executor service captures around the
 * handler call.
 */
sealed class HandlerResult {
    /** External-Intent skill: dispatched to OS. Terminal happy state for v1.1. */
    data class Dispatched(val latencyMs: Long, val info: String? = null) : HandlerResult()

    /** Local-write skill: the side effect completed in-process. */
    data class Success(val latencyMs: Long, val info: String? = null) : HandlerResult()

    /** User cancelled (e.g., via undo window). */
    data class Cancelled(val latencyMs: Long, val reason: String) : HandlerResult()

    /** Hard failure — `reason` is a stable code (e.g., `intent_resolve_failed`). */
    data class Failed(val latencyMs: Long, val reason: String, val cause: Throwable? = null) : HandlerResult()
}

/**
 * Pure SAM-style handler for one [com.capsule.app.action.AppFunction].
 *
 * Lives in the `:capture` process. MUST NOT touch the network and MUST
 * NOT bind any class from `com.capsule.app.net.*` (enforced by the
 * `OrbitNoHttpClientOutsideNet` lint rule).
 *
 * The executor service has already:
 *  - re-validated `argsJson` against the registered schema
 *  - confirmed the proposal is still in `PROPOSED` state
 *  - written the seed `action_execution` row at `outcome=PENDING`
 *
 * Handlers are responsible only for the side effect. Recording the
 * terminal outcome in the DB is the executor service's job; handlers
 * just return a [HandlerResult].
 */
interface ActionHandler {
    suspend fun handle(
        context: Context,
        skill: AppFunctionSummaryParcel,
        argsJson: String,
        repository: IEnvelopeRepository? = null
    ): HandlerResult
}
