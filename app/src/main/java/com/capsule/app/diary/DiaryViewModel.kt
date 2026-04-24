package com.capsule.app.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
        super.onCleared()
    }
}
