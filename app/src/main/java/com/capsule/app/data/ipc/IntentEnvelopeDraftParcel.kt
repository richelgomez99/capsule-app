package com.capsule.app.data.ipc

import android.os.Parcel
import android.os.Parcelable

data class IntentEnvelopeDraftParcel(
    val contentType: String,
    val textContent: String?,
    val imageUri: String?,
    val intent: String,
    val intentConfidence: Float?,
    val intentSource: String
) : Parcelable {

    constructor(parcel: Parcel) : this(
        contentType = parcel.readString()!!,
        textContent = parcel.readString(),
        imageUri = parcel.readString(),
        intent = parcel.readString()!!,
        intentConfidence = parcel.readValue(Float::class.java.classLoader) as? Float,
        intentSource = parcel.readString()!!
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(contentType)
        parcel.writeString(textContent)
        parcel.writeString(imageUri)
        parcel.writeString(intent)
        parcel.writeValue(intentConfidence)
        parcel.writeString(intentSource)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<IntentEnvelopeDraftParcel> {
        override fun createFromParcel(parcel: Parcel) = IntentEnvelopeDraftParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<IntentEnvelopeDraftParcel>(size)
    }
}
