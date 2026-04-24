package com.capsule.app.settings

import com.capsule.app.data.ipc.EnvelopeViewParcel

/**
 * T091a — UI state for [TrashScreen].
 *
 * Rows list soft-deleted envelopes newest-deleted first (time-of-delete
 * is the envelope's `createdAtMillis` since `deletedAt` is not on the
 * parcel surface — the AIDL list is already sorted server-side).
 */
sealed interface TrashUiState {
    data object Loading : TrashUiState
    data class Error(val message: String) : TrashUiState
    data class Ready(
        val envelopes: List<EnvelopeViewParcel>,
        val retentionDays: Int
    ) : TrashUiState
}
