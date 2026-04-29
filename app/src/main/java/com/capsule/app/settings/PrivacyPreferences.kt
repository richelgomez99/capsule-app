package com.capsule.app.settings

import android.content.Context
import android.content.SharedPreferences

/**
 * T070 — user-facing privacy toggles backed by [SharedPreferences].
 *
 * v1 scope: only the "Pause continuations" switch. When paused,
 * [com.capsule.app.continuation.ContinuationEngine.enqueueSingle] no-ops
 * so no outbound network fetches are scheduled, and any in-flight
 * continuations are cancelled by calling [cancelAll] on pause.
 *
 * Lives in the default (`:ui`) process. Written only from the UI thread
 * via the Settings screen; read from the `:ml` process inside the engine
 * through a tiny multi-process `SharedPreferences` backed file. Multi-
 * process access is safe for a single boolean key (no read-modify-write
 * sequences), though not transactional — acceptable for a user-facing
 * toggle where a 50ms stale read is harmless.
 */
class PrivacyPreferences(context: Context) {

    private val prefs: SharedPreferences = context
        .applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var continuationsPaused: Boolean
        get() = prefs.getBoolean(KEY_CONTINUATIONS_PAUSED, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CONTINUATIONS_PAUSED, value).apply()
        }

    companion object {
        const val PREFS_NAME = "capsule_privacy_prefs"
        const val KEY_CONTINUATIONS_PAUSED = "continuations_paused"
    }
}
