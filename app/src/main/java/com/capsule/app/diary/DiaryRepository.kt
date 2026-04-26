package com.capsule.app.diary

import com.capsule.app.action.ipc.ActionExecuteRequestParcel
import com.capsule.app.action.ipc.ActionExecuteResultParcel
import com.capsule.app.data.ipc.ActionProposalParcel
import com.capsule.app.data.ipc.DayPageParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel
import kotlinx.coroutines.flow.Flow

/**
 * T049 seam — the diary VM's view of the envelope repository. The AIDL
 * `IEnvelopeRepository.Stub` uses a callback-based observer; this interface
 * flattens that to a cold [Flow] so the VM stays unit-testable without any
 * Android IPC plumbing. Production binding is owned by [DiaryActivity]
 * (T052) via an adapter that bridges `IEnvelopeObserver` → `Flow`.
 */
interface DiaryRepository {

    /**
     * Emits a [DayPageParcel] for [isoDate] whenever the underlying data
     * changes. The flow completes only when collection is cancelled.
     */
    fun observeDay(isoDate: String): Flow<DayPageParcel>

    /** Reassign an envelope's intent (US2 tap-to-reassign per T051). */
    suspend fun reassignIntent(envelopeId: String, newIntentName: String, reason: String?)

    /** Archive an envelope (out of the Diary, still on disk). */
    suspend fun archive(envelopeId: String)

    /** Soft-delete an envelope. */
    suspend fun delete(envelopeId: String)

    /** T069 — re-enqueue non-succeeded URL hydrations for an envelope. */
    suspend fun retryHydration(envelopeId: String)

    /** T055b — single-envelope fetch for the detail screen. */
    suspend fun getEnvelope(envelopeId: String): EnvelopeViewParcel

    /**
     * T056 — paginated list of ISO local dates (newest first) that have
     * at least one non-archived, non-deleted envelope. Backs the Diary's
     * `HorizontalPager` → `DiaryPagingSource` so backscroll skips empty
     * days.
     */
    suspend fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String>

    // ---- Spec 003 v1.1 — Orbit Actions (T053) -----------------------------

    /**
     * Live feed of action proposals attached to [envelopeId]. Emits the
     * full current set on every change so observers don't reconstruct
     * deltas (mirrors [observeDay]).
     */
    fun observeProposals(envelopeId: String): Flow<List<ActionProposalParcel>>

    /** Flips proposal `state` PROPOSED→CONFIRMED + audits ACTION_CONFIRMED. */
    suspend fun markProposalConfirmed(proposalId: String): Boolean

    /** Flips proposal `state` PROPOSED→DISMISSED + audits ACTION_DISMISSED. */
    suspend fun markProposalDismissed(proposalId: String): Boolean

    /** Dispatches the side effect via `:capture` IActionExecutor binder. */
    suspend fun executeAction(request: ActionExecuteRequestParcel): ActionExecuteResultParcel

    /** Best-effort cancel within the 5s undo window. Returns false past the window. */
    suspend fun cancelWithinUndoWindow(executionId: String): Boolean

    /** T064 (003 US2) — toggle one item on a derived to-do envelope. */
    suspend fun setTodoItemDone(envelopeId: String, itemIndex: Int, done: Boolean)
}
