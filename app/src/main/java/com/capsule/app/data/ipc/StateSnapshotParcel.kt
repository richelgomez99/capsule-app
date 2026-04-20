package com.capsule.app.data.ipc

import android.os.Parcel
import android.os.Parcelable

data class StateSnapshotParcel(
    val appCategory: String,
    val activityState: String,
    val tzId: String,
    val hourLocal: Int,
    val dayOfWeekLocal: Int
) : Parcelable {

    constructor(parcel: Parcel) : this(
        appCategory = parcel.readString()!!,
        activityState = parcel.readString()!!,
        tzId = parcel.readString()!!,
        hourLocal = parcel.readInt(),
        dayOfWeekLocal = parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(appCategory)
        parcel.writeString(activityState)
        parcel.writeString(tzId)
        parcel.writeInt(hourLocal)
        parcel.writeInt(dayOfWeekLocal)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<StateSnapshotParcel> {
        override fun createFromParcel(parcel: Parcel) = StateSnapshotParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<StateSnapshotParcel>(size)
    }
}
