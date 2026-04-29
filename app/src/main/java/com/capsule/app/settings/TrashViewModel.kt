package com.capsule.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capsule.app.data.ipc.EnvelopeViewParcel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * T091a — the Trash screen's provider. Abstracts the three AIDL calls
 * (`listSoftDeletedWithinDays`, `restoreFromTrash`, `hardDelete`) so the
 * VM stays JVM-testable.
 */
interface TrashRepository {
    suspend fun listSoftDeleted(days: Int): List<EnvelopeViewParcel>
    suspend fun restore(envelopeId: String)
    suspend fun hardPurge(envelopeId: String)
}

class TrashViewModel(
    private val repository: TrashRepository,
    private val retentionDays: Int = 30,
    scopeOverride: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow<TrashUiState>(TrashUiState.Loading)
    val state: StateFlow<TrashUiState> = _state.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        scope.launch {
            _state.value = TrashUiState.Loading
            runCatching { repository.listSoftDeleted(retentionDays) }
                .onSuccess { list ->
                    _state.value = TrashUiState.Ready(list, retentionDays)
                }
                .onFailure { e ->
                    _state.value = TrashUiState.Error(
                        e.message ?: e::class.java.simpleName
                    )
                }
        }
    }

    fun onRestore(envelopeId: String) {
        scope.launch {
            runCatching { repository.restore(envelopeId) }
            refresh()
        }
    }

    fun onPurge(envelopeId: String) {
        scope.launch {
            runCatching { repository.hardPurge(envelopeId) }
            refresh()
        }
    }
}
