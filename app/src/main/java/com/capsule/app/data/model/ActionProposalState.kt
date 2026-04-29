package com.capsule.app.data.model

/**
 * Lifecycle of an [com.capsule.app.data.entity.ActionProposalEntity].
 *
 * State machine:
 *  - PROPOSED → DISMISSED (terminal, user declined)
 *  - PROPOSED → CONFIRMED (user tapped Confirm; an [ActionExecutionOutcome]
 *    row is then written by `ActionExecutorService`)
 *  - PROPOSED | CONFIRMED → INVALIDATED (source envelope deleted, schema
 *    superseded, or user cancelled within the 5s undo window)
 *
 * See `specs/003-orbit-actions/data-model.md` §2.
 */
enum class ActionProposalState {
    PROPOSED,
    DISMISSED,
    CONFIRMED,
    INVALIDATED
}
