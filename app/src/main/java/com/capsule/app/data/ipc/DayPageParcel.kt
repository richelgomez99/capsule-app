package com.capsule.app.data.ipc

import android.os.Parcel
import android.os.Parcelable

data class DayPageParcel(
    val isoDate: String,
    val envelopes: List<EnvelopeViewParcel>
) : Parcelable {

    constructor(parcel: Parcel) : this(
        isoDate = parcel.readString()!!,
        envelopes = mutableListOf<EnvelopeViewParcel>().also {
            parcel.readTypedList(it, EnvelopeViewParcel.CREATOR)
        }
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(isoDate)
        parcel.writeTypedList(envelopes)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<DayPageParcel> {
        override fun createFromParcel(parcel: Parcel) = DayPageParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<DayPageParcel>(size)
    }
}
