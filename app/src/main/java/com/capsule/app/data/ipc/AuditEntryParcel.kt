package com.capsule.app.data.ipc

import android.os.Parcel
import android.os.Parcelable

data class AuditEntryParcel(
    val id: String,
    val atMillis: Long,
    val action: String,
    val description: String,
    val envelopeId: String?,
    val extraJson: String?
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString()!!,
        atMillis = parcel.readLong(),
        action = parcel.readString()!!,
        description = parcel.readString()!!,
        envelopeId = parcel.readString(),
        extraJson = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeLong(atMillis)
        parcel.writeString(action)
        parcel.writeString(description)
        parcel.writeString(envelopeId)
        parcel.writeString(extraJson)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<AuditEntryParcel> {
        override fun createFromParcel(parcel: Parcel) = AuditEntryParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<AuditEntryParcel>(size)
    }
}
