package com.capsule.app.diary

import com.capsule.app.data.ipc.AuditEntryParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel

/**
 * T055a/d — view model state for [com.capsule.app.diary.ui.EnvelopeDetailScreen].
 *
 * Loading and Error are exhaustive; once Ready emits, audit + history are
 * already split out so the Compose layer doesn't reparse `intentHistoryJson`
 * on every recomposition.
 */
sealed interface EnvelopeDetailUiState {
    data object Loading : EnvelopeDetailUiState
    data class Error(val message: String) : EnvelopeDetailUiState
    data class Ready(
        val envelope: EnvelopeViewParcel,
        val intentHistory: List<IntentHistoryRow>,
        val auditTrail: List<AuditEntryParcel>
    ) : EnvelopeDetailUiState
}

/** One parsed entry from `intentHistoryJson`. */
data class IntentHistoryRow(
    val intent: String,
    val source: String,
    val atMillis: Long
)
