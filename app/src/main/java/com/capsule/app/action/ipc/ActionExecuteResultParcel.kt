package com.capsule.app.action.ipc

import android.os.Parcel
import android.os.Parcelable

/**
 * Synchronous response from [com.capsule.app.action.ipc.IActionExecutor.execute].
 *
 * - [outcome] mirrors [com.capsule.app.data.model.ActionExecutionOutcome] `.name`.
 *   `DISPATCHED` is the terminal happy state for `EXTERNAL_INTENT` skills (the
 *   target app owns the rest of the lifecycle); `SUCCESS` for `LOCAL_DB_WRITE`.
 * - [outcomeReason] carries a short error code for `FAILED` (e.g.,
 *   `schema_invalidated`, `intent_resolve_failed`, `runtime_error`).
 * - [executionId] is the UUID of the persisted [com.capsule.app.data.entity.ActionExecutionEntity]
 *   row; the UI uses it as the handle for `cancelWithinUndoWindow`.
 */
data class ActionExecuteResultParcel(
    val executionId: String,
    val outcome: String,
    val outcomeReason: String?,
    val dispatchedAtMillis: Long,
    val latencyMs: Long
) : Parcelable {

    constructor(parcel: Parcel) : this(
        executionId = parcel.readString()!!,
        outcome = parcel.readString()!!,
        outcomeReason = parcel.readString(),
        dispatchedAtMillis = parcel.readLong(),
        latencyMs = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(executionId)
        parcel.writeString(outcome)
        parcel.writeString(outcomeReason)
        parcel.writeLong(dispatchedAtMillis)
        parcel.writeLong(latencyMs)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ActionExecuteResultParcel> {
        override fun createFromParcel(parcel: Parcel) = ActionExecuteResultParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<ActionExecuteResultParcel>(size)
    }
}
