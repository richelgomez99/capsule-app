package com.capsule.app.data.ipc

import android.os.Parcel
import android.os.Parcelable

/**
 * AIDL-carried view of an [com.capsule.app.data.entity.ActionProposalEntity].
 *
 * Only fields the UI needs to render the proposal chip + confirm sheet are
 * carried; `argsJson` and `schemaVersion` survive the trip so the executor
 * can re-validate at confirm time.
 *
 * Enums are carried as their `.name` so the AIDL surface stays stable across
 * Kotlin enum reorderings.
 */
data class ActionProposalParcel(
    val id: String,
    val envelopeId: String,
    val functionId: String,
    val schemaVersion: Int,
    val argsJson: String,
    val previewTitle: String,
    val previewSubtitle: String?,
    val confidence: Float,
    /** Mirrors [com.capsule.app.data.model.LlmProvenance].name */
    val provenance: String,
    /** Mirrors [com.capsule.app.data.model.ActionProposalState].name */
    val state: String,
    /** Mirrors [com.capsule.app.data.model.SensitivityScope].name */
    val sensitivityScope: String,
    val createdAtMillis: Long,
    val stateChangedAtMillis: Long
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString()!!,
        envelopeId = parcel.readString()!!,
        functionId = parcel.readString()!!,
        schemaVersion = parcel.readInt(),
        argsJson = parcel.readString()!!,
        previewTitle = parcel.readString()!!,
        previewSubtitle = parcel.readString(),
        confidence = parcel.readFloat(),
        provenance = parcel.readString()!!,
        state = parcel.readString()!!,
        sensitivityScope = parcel.readString()!!,
        createdAtMillis = parcel.readLong(),
        stateChangedAtMillis = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(envelopeId)
        parcel.writeString(functionId)
        parcel.writeInt(schemaVersion)
        parcel.writeString(argsJson)
        parcel.writeString(previewTitle)
        parcel.writeString(previewSubtitle)
        parcel.writeFloat(confidence)
        parcel.writeString(provenance)
        parcel.writeString(state)
        parcel.writeString(sensitivityScope)
        parcel.writeLong(createdAtMillis)
        parcel.writeLong(stateChangedAtMillis)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ActionProposalParcel> {
        override fun createFromParcel(parcel: Parcel) = ActionProposalParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<ActionProposalParcel>(size)
    }
}
