// IActionExecutor.aidl
package com.capsule.app.action.ipc;

import com.capsule.app.action.ipc.ActionExecuteRequestParcel;
import com.capsule.app.action.ipc.ActionExecuteResultParcel;

/**
 * AIDL surface exposed by `:capture` so the `:ui` process can dispatch a
 * confirmed action proposal. Lives in `:capture` (not `:ml`) because action
 * dispatch creates external `Intent`s that need a foreground UI process —
 * the `:ml` process is forbidden from launching activities and from holding
 * any network references.
 *
 * See `specs/003-orbit-actions/contracts/action-execution-contract.md`.
 */
interface IActionExecutor {
    /**
     * Validate the proposal's `argsJson` against the registered schema and,
     * if valid, dispatch the side effect (Intent or local DB write).
     * Synchronously returns dispatch outcome; the result row's terminal
     * outcome may still be updated later via the undo path.
     */
    ActionExecuteResultParcel execute(in ActionExecuteRequestParcel request);

    /**
     * Best-effort cancel of an action whose 5-second undo window has not
     * yet elapsed. Returns `true` when cancelled, `false` when already
     * past the window or already terminal. The `:capture` process is the
     * only one tracking pending Intents in-memory, hence the AIDL.
     */
    boolean cancelWithinUndoWindow(String executionId);
}
