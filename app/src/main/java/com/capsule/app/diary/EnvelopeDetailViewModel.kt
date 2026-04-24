package com.capsule.app.diary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capsule.app.data.ipc.AuditEntryParcel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray

/**
 * T055b/d — view model for [com.capsule.app.diary.ui.EnvelopeDetailScreen].
 *
 * Holds a single [EnvelopeDetailUiState] that the screen observes. Loads
 * the envelope on init + on [refresh] (called from onResume). Reassign /
 * archive / delete delegate to [DiaryRepository]; archive + delete also
 * flip a one-shot [finished] flag so the activity can `finish()`.
 *
 * [auditProvider] is an optional seam — the detail screen hides the
 * Audit trail section when this returns an empty list or when the
 * provider is null. Production wiring injects the `IAuditLog` binder
 * once T087/T088 lands; until then the detail screen shows everything
 * else and silently hides that section.
 */
class EnvelopeDetailViewModel(
    private val envelopeId: String,
    private val repository: DiaryRepository,
    private val auditProvider: (suspend (String) -> List<AuditEntryParcel>)? = null,
    scopeOverride: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow<EnvelopeDetailUiState>(EnvelopeDetailUiState.Loading)
    val state: StateFlow<EnvelopeDetailUiState> = _state.asStateFlow()

    private val _finished = MutableStateFlow(false)
    val finished: StateFlow<Boolean> = _finished.asStateFlow()

    init {
        refresh()
    }

    /** Re-load on resume (an Archive/Delete from a notification etc. may have mutated us). */
    fun refresh() {
        scope.launch {
            _state.value = EnvelopeDetailUiState.Loading
            val loaded = runCatching { repository.getEnvelope(envelopeId) }
            loaded.onFailure { e ->
                _state.value = EnvelopeDetailUiState.Error(
                    e.message ?: e::class.java.simpleName
                )
                return@launch
            }
            val envelope = loaded.getOrThrow()
            val audit = auditProvider?.let { provider ->
                runCatching { provider(envelopeId) }.getOrDefault(emptyList())
            } ?: emptyList()
            _state.value = EnvelopeDetailUiState.Ready(
                envelope = envelope,
                intentHistory = parseIntentHistory(envelope.intentHistoryJson),
                auditTrail = audit
            )
        }
    }

    fun onReassignIntent(newIntentName: String) {
        scope.launch {
            runCatching { repository.reassignIntent(envelopeId, newIntentName, "DIARY_REASSIGN") }
            refresh()
        }
    }

    fun onArchive() {
        scope.launch {
            runCatching { repository.archive(envelopeId) }
            _finished.value = true
        }
    }

    fun onDelete() {
        scope.launch {
            runCatching { repository.delete(envelopeId) }
            _finished.value = true
        }
    }

    fun onRetryHydration() {
        scope.launch {
            runCatching { repository.retryHydration(envelopeId) }
            refresh()
        }
    }

    private fun parseIntentHistory(json: String): List<IntentHistoryRow> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        IntentHistoryRow(
                            intent = obj.optString("intent", ""),
                            source = obj.optString("source", ""),
                            atMillis = obj.optLong("at", 0L)
                        )
                    )
                }
            }.sortedBy { it.atMillis }
        }.getOrDefault(emptyList())
    }
}
