package com.capsule.app.data.ipc

import android.os.Parcel
import android.os.Parcelable

data class EnvelopeViewParcel(
    val id: String,
    val contentType: String,
    val textContent: String?,
    val imageUri: String?,
    val intent: String,
    val intentSource: String,
    val createdAtMillis: Long,
    val dayLocal: String,
    val isArchived: Boolean,
    val title: String?,
    val domain: String?,
    val excerpt: String?,
    val summary: String?
) : Parcelable {

    constructor(parcel: Parcel) : this(
        id = parcel.readString()!!,
        contentType = parcel.readString()!!,
        textContent = parcel.readString(),
        imageUri = parcel.readString(),
        intent = parcel.readString()!!,
        intentSource = parcel.readString()!!,
        createdAtMillis = parcel.readLong(),
        dayLocal = parcel.readString()!!,
        isArchived = parcel.readInt() != 0,
        title = parcel.readString(),
        domain = parcel.readString(),
        excerpt = parcel.readString(),
        summary = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(contentType)
        parcel.writeString(textContent)
        parcel.writeString(imageUri)
        parcel.writeString(intent)
        parcel.writeString(intentSource)
        parcel.writeLong(createdAtMillis)
        parcel.writeString(dayLocal)
        parcel.writeInt(if (isArchived) 1 else 0)
        parcel.writeString(title)
        parcel.writeString(domain)
        parcel.writeString(excerpt)
        parcel.writeString(summary)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<EnvelopeViewParcel> {
        override fun createFromParcel(parcel: Parcel) = EnvelopeViewParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<EnvelopeViewParcel>(size)
    }
}
