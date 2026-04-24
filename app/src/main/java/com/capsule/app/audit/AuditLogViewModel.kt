package com.capsule.app.audit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capsule.app.data.ipc.AuditEntryParcel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * T091 — state for the "What Orbit did today" audit-log viewer.
 */
sealed interface AuditLogUiState {
    data object Loading : AuditLogUiState
    data class Error(val message: String) : AuditLogUiState
    data class Ready(
        val selectedDay: LocalDate,
        val entries: List<AuditEntryParcel>,
        val groupCounts: Map<String, Int>
    ) : AuditLogUiState
}

/**
 * Minimal provider abstraction so the VM stays JVM-testable.
 */
interface AuditLogProvider {
    suspend fun entriesForDay(isoDate: String): List<AuditEntryParcel>
}

class AuditLogViewModel(
    private val provider: AuditLogProvider,
    private val today: () -> LocalDate = LocalDate::now,
    scopeOverride: CoroutineScope? = null
) : ViewModel() {

    private val scope: CoroutineScope = scopeOverride ?: viewModelScope

    private val _state = MutableStateFlow<AuditLogUiState>(AuditLogUiState.Loading)
    val state: StateFlow<AuditLogUiState> = _state.asStateFlow()

    private var currentDay: LocalDate = today()

    init {
        load(currentDay)
    }

    fun selectDay(day: LocalDate) {
        currentDay = day
        load(day)
    }

    fun refresh() {
        load(currentDay)
    }

    private fun load(day: LocalDate) {
        scope.launch {
            _state.value = AuditLogUiState.Loading
            runCatching { provider.entriesForDay(day.toString()) }
                .onSuccess { rows ->
                    _state.value = AuditLogUiState.Ready(
                        selectedDay = day,
                        entries = rows,
                        groupCounts = rows.groupingBy { it.action }.eachCount()
                    )
                }
                .onFailure { e ->
                    _state.value = AuditLogUiState.Error(
                        e.message ?: e::class.java.simpleName
                    )
                }
        }
    }
}
