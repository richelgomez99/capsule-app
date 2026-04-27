package com.capsule.app.data.model

/**
 * Cluster lifecycle state per spec 012 §Cluster Lifecycle State Machine
 * (FR-012-028..FR-012-037) and spec 002 amendment §FR-026..FR-040.
 *
 * Forward path: FORMING → SURFACED → TAPPED → ACTING → ACTED.
 * Branches: ACTING → FAILED (Nano error/null/timeout); SURFACED/TAPPED →
 * DISMISSED (user dismiss or orphan-cleanup); SURFACED → AGED_OUT
 * (>7 days without action).
 *
 * The card surfaces only when state ∈ {SURFACED, TAPPED, ACTING, ACTED,
 * FAILED} per [ClusterDao] filter; FORMING is a transient pre-publish
 * state, DISMISSED/AGED_OUT are terminal.
 */
enum class ClusterState {
    FORMING,
    SURFACED,
    TAPPED,
    ACTING,
    ACTED,
    FAILED,
    DISMISSED,
    AGED_OUT
}
