package com.capsule.app.data.ipc

import android.os.Parcel
import android.os.Parcelable

/**
 * AIDL-carried view of an [com.capsule.app.data.entity.AppFunctionSkillEntity].
 *
 * Returned by `IEnvelopeRepository.lookupAppFunction` /
 * `IEnvelopeRepository.listAppFunctions`. Enum fields are carried as `.name`.
 */
data class AppFunctionSummaryParcel(
    val functionId: String,
    val appPackage: String,
    val displayName: String,
    val description: String,
    val schemaVersion: Int,
    val argsSchemaJson: String,
    /** [com.capsule.app.data.model.AppFunctionSideEffect].name */
    val sideEffects: String,
    /** [com.capsule.app.data.model.Reversibility].name */
    val reversibility: String,
    /** [com.capsule.app.data.model.SensitivityScope].name */
    val sensitivityScope: String,
    val registeredAtMillis: Long,
    val updatedAtMillis: Long
) : Parcelable {

    constructor(parcel: Parcel) : this(
        functionId = parcel.readString()!!,
        appPackage = parcel.readString()!!,
        displayName = parcel.readString()!!,
        description = parcel.readString()!!,
        schemaVersion = parcel.readInt(),
        argsSchemaJson = parcel.readString()!!,
        sideEffects = parcel.readString()!!,
        reversibility = parcel.readString()!!,
        sensitivityScope = parcel.readString()!!,
        registeredAtMillis = parcel.readLong(),
        updatedAtMillis = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(functionId)
        parcel.writeString(appPackage)
        parcel.writeString(displayName)
        parcel.writeString(description)
        parcel.writeInt(schemaVersion)
        parcel.writeString(argsSchemaJson)
        parcel.writeString(sideEffects)
        parcel.writeString(reversibility)
        parcel.writeString(sensitivityScope)
        parcel.writeLong(registeredAtMillis)
        parcel.writeLong(updatedAtMillis)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AppFunctionSummaryParcel> {
        override fun createFromParcel(parcel: Parcel) = AppFunctionSummaryParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<AppFunctionSummaryParcel>(size)
    }
}
