package com.capsule.app.diary

import com.capsule.app.data.ClusterCardModel

/**
 * T049 — immutable UI state for the diary screen (per spec.md §US2 and
 * tasks.md T049: "loading/ready/empty/error").
 *
 * Keeping the states as a sealed class lets Compose `when (state)` branches
 * stay exhaustive and lets tests assert on shape directly.
 */
sealed class DayUiState {
    abstract val isoDate: String

    /** Initial bind, or re-subscribing after [isoDate] changes. */
    data class Loading(override val isoDate: String) : DayUiState()

    /** Repository returned an empty day page. UI renders the empty-day copy (T054). */
    data class Empty(override val isoDate: String) : DayUiState()

    /**
     * Repository emitted envelopes; [ThreadGrouper] + [DayHeaderGenerator]
     * have been applied. Threads and header are derived values, recomputed
     * whenever the upstream page changes.
     *
     * [clusters] (Phase 11 Block 9 / T148) carries any cluster cards
     * whose `timeBucketStart` falls on [isoDate] in the device's local
     * time zone. Empty on non-cluster days.
     */
    data class Ready(
        override val isoDate: String,
        val header: String,
        val generationLocale: String,
        val threads: List<DiaryThread>,
        val clusters: List<ClusterCardModel> = emptyList()
    ) : DayUiState()

    /** Terminal error (e.g., service unbound, crash). [message] is for logs only; UI shows a generic retry state. */
    data class Error(override val isoDate: String, val message: String) : DayUiState()
}
