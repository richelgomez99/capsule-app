package com.capsule.app.overlay

import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

/**
 * Centralized motion tokens for the overlay — one "feel" across bubble,
 * chip row, silent-wrap pill, and undo pill. Based on Material 3 Expressive
 * + Apple's iOS 19 "Liquid Glass" timing language (April 2026 benchmark).
 *
 * Rules of thumb:
 *   - Enter: spring with subtle overshoot — content arrives with character.
 *   - Exit: linear-ish ease-out under 200 ms — snappy, never lingering.
 *   - Haptic: paired with the *start* of the motion, not the end.
 *   - Reduce-motion: callers swap in [ReduceMotionTween] when
 *     `Settings.Global.ANIMATOR_DURATION_SCALE == 0f`.
 */
object OverlayMotion {

    // ── Duration tokens (ms) ────────────────────────────────────────

    /** Standard enter duration. Apple HIG: 240 ms is the top of "snappy". */
    const val ENTER_MS: Int = 220

    /** Standard exit duration. Keep < 200 ms so dismiss feels instant. */
    const val EXIT_MS: Int = 160

    /** Chip-row auto-dismiss countdown. Originally 2s per FR-004; bumped
     *  to 5s based on real-device feedback — 2s is too short for users to
     *  parse the preview AND pick a chip without pressure. */
    const val CHIP_COUNTDOWN_MS: Long = 5_000L

    /** Silent-wrap pill on-screen duration. Bumped from 2s → 4s so the
     *  "Saved · Undo" affordance is actually reachable. */
    const val SILENT_WRAP_PILL_MS: Long = 4_000L

    /** Undo window (FR-004 / FR-020). Bumped from 10s → 15s so users have
     *  time to look away and come back. */
    const val UNDO_WINDOW_MS: Long = 15_000L

    /** Chip press scale animation. */
    const val CHIP_PRESS_MS: Int = 120

    /** Removed-confirmation pill. */
    const val REMOVED_CONFIRMATION_MS: Long = 1_500L

    // ── Spring tokens ───────────────────────────────────────────────

    /**
     * Enter spring. `dampingRatio=0.8` gives a single subtle overshoot —
     * enough character to feel alive, not so much it looks like a toy.
     * `stiffness=500` lands at ~220 ms to equilibrium.
     */
    fun <T> enterSpring() = spring<T>(
        dampingRatio = 0.80f,
        stiffness = Spring.StiffnessMedium // 1500f — snappier than default
    )

    /**
     * Chip press spring. Very fast, critical-damped — presses must feel
     * like a button, not a trampoline.
     */
    fun <T> pressSpring() = spring<T>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessHigh
    )

    // ── Easing-based tokens ─────────────────────────────────────────

    /** Enter tween fallback (reduce-motion, or inside `updateTransition`). */
    fun <T> enterTween() = tween<T>(
        durationMillis = ENTER_MS,
        easing = FastOutSlowInEasing
    )

    /** Exit tween — snappy. */
    fun <T> exitTween() = tween<T>(
        durationMillis = EXIT_MS,
        easing = FastOutLinearInEasing
    )

    /** Reduce-motion replacement for both enter and exit. */
    fun <T> reduceMotionTween() = tween<T>(durationMillis = 100)
}
