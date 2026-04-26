package com.capsule.app.data.model

/**
 * Terminal outcome of an [com.capsule.app.data.entity.ActionExecutionEntity].
 *
 * - PENDING: row inserted, dispatch not yet attempted (transient).
 * - DISPATCHED: Intent fired successfully. v1.1 terminal state for external
 *   intents (we don't request `READ_CALENDAR`/`WRITE_CALENDAR` so we can't
 *   read back system Calendar completion — see research.md §4).
 * - SUCCESS: local-only side effect completed (currently used by `todo_add`
 *   target=local where we own the write).
 * - FAILED: dispatch raised `ActivityNotFoundException`, schema mismatch,
 *   or any other terminal failure. `outcomeReason` is populated.
 * - USER_CANCELLED: user tapped the 5s undo affordance before the cleanup
 *   worker fired. Reachable only inside the undo window.
 */
enum class ActionExecutionOutcome {
    PENDING,
    DISPATCHED,
    SUCCESS,
    FAILED,
    USER_CANCELLED
}
