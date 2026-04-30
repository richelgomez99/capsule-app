package com.capsule.app.cluster

import com.capsule.app.data.model.ClusterState

/**
 * Pure-Kotlin transition table for cluster lifecycle (T151).
 *
 * Spec sources: 012 §Cluster Lifecycle State Machine (FR-012-028..037)
 * and 002 amendment FR-026..FR-040. Forward path:
 *
 *   FORMING ─► SURFACED ─► TAPPED ─► ACTING ─► ACTED
 *
 * Branches:
 *
 *   SURFACED, TAPPED              ─[user dismiss / orphan]─► DISMISSED
 *   ACTING                        ─[Nano error/null/timeout]─► FAILED
 *   FAILED                        ─[retry, max 3]─► ACTING
 *   SURFACED                      ─[≥7 days idle]─► AGED_OUT
 *
 * Terminal states: ACTED, DISMISSED, AGED_OUT.
 *
 * The machine is *advisory*: persistence (ClusterRepository,
 * SoftDeleteRetentionWorker, etc.) is responsible for actually writing
 * the new state and any audit row. This module returns `null` for
 * invalid transitions so callers can distinguish "no-op, idempotent"
 * from "should write new state".
 *
 * User-driven dismiss is **NOT** routed through this machine — see
 * `ClusterRepository.markDismissed` (Block 9 FU#2). Per product call,
 * a user pressing Dismiss is always honoured regardless of current
 * state. Programmatic transitions (worker-driven) all go through here.
 */
object ClusterStateMachine {

    /**
     * Triggers that drive cluster transitions. Names mirror the
     * audit-action vocabulary so the machine and the audit log agree
     * on what happened.
     */
    enum class Trigger {
        /** Worker published the cluster card (FORMING → SURFACED). */
        SURFACE,

        /** User tapped the cluster card (SURFACED → TAPPED). */
        TAP,

        /** Summariser kicked off (TAPPED → ACTING, or FAILED → ACTING on retry). */
        START_ACTING,

        /** Summariser succeeded (ACTING → ACTED). */
        ACT_SUCCESS,

        /** Summariser failed validation, errored, or timed out (ACTING → FAILED). */
        ACT_FAIL,

        /**
         * Soft-delete cascade observed surviving members < minimum and
         * auto-dismissed the cluster (SURFACED/TAPPED → DISMISSED).
         */
        ORPHAN,

        /** SURFACED for ≥ 7 days without action (SURFACED → AGED_OUT). */
        AGE_OUT,
    }

    /**
     * Maximum FAILED → ACTING retry attempts before the cluster card
     * stays in FAILED with no further auto-retries (spec 002 FR-038).
     * Caller is responsible for tracking attempt count and refusing
     * to invoke [next] with [Trigger.START_ACTING] from FAILED past
     * this cap; the machine itself does not count attempts.
     */
    const val MAX_FAILED_ACTING_RETRIES = 3

    /**
     * Compute the next state for [current] given [trigger]. Returns
     * `null` when the transition is invalid (caller should treat as
     * a no-op and log a guard hit).
     *
     * Idempotency note: SURFACE on SURFACED is invalid (returns null),
     * not a re-emit. Workers should check current state before firing.
     */
    fun next(current: ClusterState, trigger: Trigger): ClusterState? = when (current) {
        ClusterState.FORMING -> when (trigger) {
            Trigger.SURFACE -> ClusterState.SURFACED
            else -> null
        }

        ClusterState.SURFACED -> when (trigger) {
            Trigger.TAP -> ClusterState.TAPPED
            Trigger.ORPHAN -> ClusterState.DISMISSED
            Trigger.AGE_OUT -> ClusterState.AGED_OUT
            else -> null
        }

        ClusterState.TAPPED -> when (trigger) {
            Trigger.START_ACTING -> ClusterState.ACTING
            Trigger.ORPHAN -> ClusterState.DISMISSED
            else -> null
        }

        ClusterState.ACTING -> when (trigger) {
            Trigger.ACT_SUCCESS -> ClusterState.ACTED
            Trigger.ACT_FAIL -> ClusterState.FAILED
            else -> null
        }

        ClusterState.FAILED -> when (trigger) {
            // Retry budget enforced by caller (see MAX_FAILED_ACTING_RETRIES).
            Trigger.START_ACTING -> ClusterState.ACTING
            else -> null
        }

        // Terminal states accept no triggers.
        ClusterState.ACTED,
        ClusterState.DISMISSED,
        ClusterState.AGED_OUT -> null
    }

    /** Convenience: is [state] terminal (no further worker-driven transitions)? */
    fun isTerminal(state: ClusterState): Boolean =
        state == ClusterState.ACTED ||
                state == ClusterState.DISMISSED ||
                state == ClusterState.AGED_OUT
}
