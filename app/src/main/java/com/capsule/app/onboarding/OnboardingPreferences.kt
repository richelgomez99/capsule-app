package com.capsule.app.onboarding

import android.content.Context
import androidx.core.content.edit

/**
 * T101 / T103 / T103a — onboarding + reduced-mode state.
 *
 * Kept intentionally small — just two booleans. Ongoing per-permission
 * state (granted / not) is always re-read from the platform (not cached
 * here) so revocations outside Orbit are detected on resume.
 */
class OnboardingPreferences(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once the user has progressed through the onboarding flow at least once. */
    var completed: Boolean
        get() = prefs.getBoolean(KEY_COMPLETED, false)
        set(value) = prefs.edit { putBoolean(KEY_COMPLETED, value) }

    /**
     * T103a — set to `true` when the user declines either
     * `SYSTEM_ALERT_WINDOW` or `POST_NOTIFICATIONS` twice. While in this
     * mode the overlay service is not started and the launcher routes
     * to `ReducedModeActivity`. Cleared automatically on resume when
     * both permissions become available.
     */
    var reducedMode: Boolean
        get() = prefs.getBoolean(KEY_REDUCED_MODE, false)
        set(value) = prefs.edit { putBoolean(KEY_REDUCED_MODE, value) }

    companion object {
        private const val PREFS = "orbit.onboarding"
        private const val KEY_COMPLETED = "onboarding.completed"
        private const val KEY_REDUCED_MODE = "orbit.reducedMode"
    }
}
