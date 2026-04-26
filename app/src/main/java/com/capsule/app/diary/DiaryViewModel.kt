package com.capsule.app.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capsule.app.action.ipc.ActionExecuteRequestParcel
import com.capsule.app.data.ipc.ActionProposalParcel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

/**
 * T049 — diary screen VM. Wires the repository observer to the pure-logic
 * [ThreadGrouper] (T047) and [DayHeaderGenerator] (T048), exposing the
 * result as [DayUiState].
 *
 * Deliberately **does not** own the AIDL binding — [DiaryRepository] is
 * an injected seam so JVM unit tests can substitute a fake. The AIDL
 * adapter lives in the `:ui` process host (T052 `DiaryActivity`).
 *
 * [scope] is injected (rather than always using `viewModelScope`) so the
 * test harness can pass its own `TestScope` and tick the dispatcher deterministically.
 */
class DiaryViewModel(
    private val repository: DiaryRepository,
    private val threadGrouper: ThreadGrouper,
    private val dayHeaderGenerator: DayHeaderGenerator,
    scopeOverride: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow<DayUiState>(DayUiState.Loading(isoDate = ""))
    val state: StateFlow<DayUiState> = _state.asStateFlow()

    private var currentJob: Job? = null
    private var currentIsoDate: String? = null

    /**
     * Subscribe to [isoDate]. Cancels any in-flight subscription and emits
     * [DayUiState.Loading] before the first upstream page arrives.
     *
     * Idempotent: calling with the same [isoDate] twice is a no-op.
     */
    fun observe(isoDate: String) {
        if (currentIsoDate == isoDate && currentJob?.isActive == true) return

        currentJob?.cancel()
        currentIsoDate = isoDate
        _state.value = DayUiState.Loading(isoDate)

        currentJob = scope.launch {
            repository.observeDay(isoDate)
                .catch { e ->
                    _state.value = DayUiState.Error(
                        isoDate = isoDate,
                        message = e.message ?: e::class.java.simpleName
                    )
                }
                .collect { page ->
                    _state.value = reduce(isoDate, page.envelopes)
                }
        }
    }

    /** Tap-to-reassign (T051). Errors are swallowed — UI stays optimistic. */
    fun onReassignIntent(envelopeId: String, newIntentName: String, reason: String? = null) {
        scope.launch {
            runCatching { repository.reassignIntent(envelopeId, newIntentName, reason) }
        }
    }

    fun onArchive(envelopeId: String) {
        scope.launch { runCatching { repository.archive(envelopeId) } }
    }

    fun onDelete(envelopeId: String) {
        scope.launch { runCatching { repository.delete(envelopeId) } }
    }

    /** T069 — user tapped the “Couldn’t enrich this link” retry affordance. */
    fun onRetryHydration(envelopeId: String) {
        scope.launch { runCatching { repository.retryHydration(envelopeId) } }
    }

    // ---- Spec 003 v1.1 — Orbit Actions (T053) -----------------------------

    /**
     * Live proposals for [envelopeId]. Hot subscriptions are owned by the
     * caller composable's lifecycle (via `LaunchedEffect` + `collect`),
     * so the VM stays a thin passthrough — no per-envelope cache to leak.
     */
    fun observeProposals(envelopeId: String): Flow<List<ActionProposalParcel>> =
        repository.observeProposals(envelopeId)

    private val _undoState = MutableStateFlow<UndoToastState?>(null)
    /**
     * Active 5s undo toast, or `null` when the window has expired or the
     * action was cancelled. UI clears its own toast when this drops to
     * `null`.
     */
    val undoState: StateFlow<UndoToastState?> = _undoState.asStateFlow()

    private var undoExpiryJob: Job? = null

    /**
     * Confirm a proposal — flips state to CONFIRMED, dispatches the side
     * effect via the `:capture` executor, opens the 5s undo window. The
     * VM swallows IPC errors and surfaces the result via [undoState].
     *
     * [editedArgsJson] lets the preview card pass user-edited fields
     * (title, start, end, location, notes, tzId) while keeping the
     * proposal row's original args intact for forensics. When `null`,
     * the original [ActionProposalParcel.argsJson] is used.
     */
    fun onConfirmProposal(
        proposal: ActionProposalParcel,
        editedArgsJson: String? = null
    ) {
        scope.launch {
            runCatching {
                repository.markProposalConfirmed(proposal.id)
                val request = ActionExecuteRequestParcel(
                    proposalId = proposal.id,
                    envelopeId = proposal.envelopeId,
                    functionId = proposal.functionId,
                    schemaVersion = proposal.schemaVersion,
                    argsJson = editedArgsJson ?: proposal.argsJson,
                    sensitivityScope = proposal.sensitivityScope,
                    confirmedAtMillis = System.currentTimeMillis(),
                    withUndo = true
                )
                val result = repository.executeAction(request)
                openUndoToast(
                    UndoToastState(
                        executionId = result.executionId,
                        previewTitle = proposal.previewTitle,
                        outcome = result.outcome,
                        outcomeReason = result.outcomeReason
                    )
                )
            }
        }
    }

    /** Dismiss before execute → ACTION_DISMISSED audit; no executor call. */
    fun onDismissProposal(proposalId: String) {
        scope.launch { runCatching { repository.markProposalDismissed(proposalId) } }
    }

    /** User tapped the toast Undo affordance within the 5s window. */
    fun onUndoExecution(executionId: String) {
        scope.launch {
            runCatching { repository.cancelWithinUndoWindow(executionId) }
            // Dismiss the toast immediately regardless of cancel outcome —
            // either the cancel succeeded (toast no longer relevant) or
            // the window already expired (no point keeping the toast).
            if (_undoState.value?.executionId == executionId) {
                undoExpiryJob?.cancel()
                _undoState.value = null
            }
        }
    }

    /** Manually clear the toast (e.g. user swiped it away). */
    fun onUndoToastDismissed() {
        undoExpiryJob?.cancel()
        _undoState.value = null
    }

    /**
     * T064 (003 US2) — toggle a `done` item on a derived to-do envelope.
     * Fire-and-forget; the underlying `setTodoItemDone` is idempotent
     * and writes through the `:ml` binder which re-emits the envelope
     * view downstream.
     */
    fun onToggleTodoItem(envelopeId: String, itemIndex: Int, done: Boolean) {
        scope.launch {
            runCatching { repository.setTodoItemDone(envelopeId, itemIndex, done) }
        }
    }

    private fun openUndoToast(state: UndoToastState) {
        undoExpiryJob?.cancel()
        _undoState.value = state
        undoExpiryJob = scope.launch {
            delay(UNDO_WINDOW_MILLIS)
            // Only clear if the same toast is still visible — avoids
            // racing a fresh toast that arrived after this one.
            if (_undoState.value?.executionId == state.executionId) {
                _undoState.value = null
            }
        }
    }

    /** UI-facing snapshot of the active undo window. */
    data class UndoToastState(
        val executionId: String,
        val previewTitle: String,
        val outcome: String,
        val outcomeReason: String?
    )

    private companion object {
        /** Per action-execution-contract.md §5: 5 s undo window. */
        const val UNDO_WINDOW_MILLIS = 5_000L
    }

    private suspend fun reduce(
        isoDate: String,
        envelopes: List<com.capsule.app.data.ipc.EnvelopeViewParcel>
    ): DayUiState {
        if (envelopes.isEmpty()) return DayUiState.Empty(isoDate)

        val threads = threadGrouper.group(envelopes)
        val header = dayHeaderGenerator.generate(isoDate, envelopes)
        return DayUiState.Ready(
            isoDate = isoDate,
            header = header.text,
            generationLocale = header.generationLocale,
            threads = threads
        )
    }

    override fun onCleared() {
        currentJob?.cancel()
        undoExpiryJob?.cancel()
        super.onCleared()
    }
}
