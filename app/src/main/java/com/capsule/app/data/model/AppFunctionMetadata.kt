package com.capsule.app.data.model

/**
 * Sensitivity classification for an AppFunction or a proposed action.
 *
 * - PUBLIC: no personal information involved (e.g., a generic calendar event title).
 * - PERSONAL: ties to the user's private context (to-dos, personal notes).
 * - SHARE_DELEGATED: user-initiated send to another app/person via system share-sheet;
 *   the receiving party then owns the data.
 *
 * The extractor refuses to propose a [PUBLIC]-scope action against an
 * envelope flagged `financial`/`credentials`/`medical` (see research.md §8).
 */
enum class SensitivityScope {
    PUBLIC,
    PERSONAL,
    SHARE_DELEGATED
}

/** Side effect classification for an AppFunction. */
enum class AppFunctionSideEffect {
    /** Fires an Android Intent into another app — Orbit cannot read the result back. */
    EXTERNAL_INTENT,
    /** Writes one or more rows to Orbit's own DB. */
    LOCAL_DB_WRITE
}

/**
 * Reversibility classification for an AppFunction.
 *
 * - REVERSIBLE_24H: Orbit can fully undo within 24h via DB row reversal.
 * - EXTERNAL_MANAGED: undo lives in the receiving app (Calendar, share target).
 *   Orbit's only undo affordance is the 5-second in-app "before-it-fires" cancel.
 * - NONE: irreversible from any surface.
 */
enum class Reversibility {
    REVERSIBLE_24H,
    EXTERNAL_MANAGED,
    NONE
}
