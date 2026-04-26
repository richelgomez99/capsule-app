package com.capsule.app.action.ipc

import android.os.Parcel
import android.os.Parcelable

/**
 * Confirm-sheet payload posted from `:ui` to the `:capture` IActionExecutor.
 *
 * The `:ui` process has just resolved the proposal + skill row from `:ml`
 * for confirm-sheet rendering, so it includes them in the request to spare
 * the executor a second IPC round-trip back to `:ml`. The executor still
 * re-validates the `(functionId, schemaVersion)` pair against its own
 * cached registry view before dispatch (compromise-resistance: a malicious
 * `:ui` cannot escalate to a `functionId` that isn't registered).
 *
 * For v1.2's third-party-app expansion (spec 008) this becomes
 * `proposalId`-only and the executor reads the row from `:ml` directly.
 */
data class ActionExecuteRequestParcel(
    val proposalId: String,
    val envelopeId: String,
    val functionId: String,
    val schemaVersion: Int,
    val argsJson: String,
    /** [com.capsule.app.data.model.SensitivityScope].name */
    val sensitivityScope: String,
    val confirmedAtMillis: Long,
    /** True when the user opted in to the 5-second undo window. */
    val withUndo: Boolean
) : Parcelable {

    constructor(parcel: Parcel) : this(
        proposalId = parcel.readString()!!,
        envelopeId = parcel.readString()!!,
        functionId = parcel.readString()!!,
        schemaVersion = parcel.readInt(),
        argsJson = parcel.readString()!!,
        sensitivityScope = parcel.readString()!!,
        confirmedAtMillis = parcel.readLong(),
        withUndo = parcel.readInt() != 0
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(proposalId)
        parcel.writeString(envelopeId)
        parcel.writeString(functionId)
        parcel.writeInt(schemaVersion)
        parcel.writeString(argsJson)
        parcel.writeString(sensitivityScope)
        parcel.writeLong(confirmedAtMillis)
        parcel.writeInt(if (withUndo) 1 else 0)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ActionExecuteRequestParcel> {
        override fun createFromParcel(parcel: Parcel) = ActionExecuteRequestParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<ActionExecuteRequestParcel>(size)
    }
}
