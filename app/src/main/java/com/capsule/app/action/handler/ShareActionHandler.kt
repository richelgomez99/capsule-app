package com.capsule.app.action.handler

import android.content.Context
import com.capsule.app.action.ActionHandler
import com.capsule.app.action.HandlerResult
import com.capsule.app.data.ipc.AppFunctionSummaryParcel
import com.capsule.app.data.ipc.IEnvelopeRepository

/**
 * v1.1 negative-path: `share.delegate` is intentionally refused.
 *
 * Sharing envelope content out to other apps requires a SHARE_DELEGATED
 * sensitivity contract that the Orbit Agent (spec 008) mediates. Until
 * then this handler always returns `FAILED` with a stable reason so the
 * UI can render the "Share-out is disabled in v1.1" toast (T086).
 */
class ShareActionHandler : ActionHandler {
    override suspend fun handle(
        context: Context,
        skill: AppFunctionSummaryParcel,
        argsJson: String,
        repository: IEnvelopeRepository?
    ): HandlerResult = HandlerResult.Failed(
        latencyMs = 0L,
        reason = "share_delegate_disabled_v1_1"
    )
}
