package com.capsule.app.net.ipc

import android.os.Parcel
import android.os.Parcelable

data class FetchResultParcel(
    val ok: Boolean,
    val finalUrl: String?,
    val title: String?,
    val canonicalHost: String?,
    val readableHtml: String?,
    val errorKind: String?,
    val errorMessage: String?,
    val fetchedAtMillis: Long
) : Parcelable {

    constructor(parcel: Parcel) : this(
        ok = parcel.readInt() != 0,
        finalUrl = parcel.readString(),
        title = parcel.readString(),
        canonicalHost = parcel.readString(),
        readableHtml = parcel.readString(),
        errorKind = parcel.readString(),
        errorMessage = parcel.readString(),
        fetchedAtMillis = parcel.readLong()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeInt(if (ok) 1 else 0)
        parcel.writeString(finalUrl)
        parcel.writeString(title)
        parcel.writeString(canonicalHost)
        parcel.writeString(readableHtml)
        parcel.writeString(errorKind)
        parcel.writeString(errorMessage)
        parcel.writeLong(fetchedAtMillis)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<FetchResultParcel> {
        override fun createFromParcel(parcel: Parcel) = FetchResultParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<FetchResultParcel>(size)
    }
}
