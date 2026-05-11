package com.capsule.app.data.ipc

import android.os.Parcel
import android.os.Parcelable

/** Result of a seal attempt crossing the :capture -> :ml binder boundary. */
data class SealResultParcel(
    val status: String,
    val envelopeId: String,
    val matchedBy: String?
) : Parcelable {

    constructor(parcel: Parcel) : this(
        status = parcel.readString()!!,
        envelopeId = parcel.readString()!!,
        matchedBy = parcel.readString()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(status)
        parcel.writeString(envelopeId)
        parcel.writeString(matchedBy)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<SealResultParcel> {
        const val STATUS_CREATED = "CREATED"
        const val STATUS_ALREADY_SAVED = "ALREADY_SAVED"

        const val MATCHED_BY_CANONICAL_URL = "CANONICAL_URL"
        const val MATCHED_BY_EXACT_TEXT = "EXACT_TEXT"

        fun created(envelopeId: String): SealResultParcel = SealResultParcel(
            status = STATUS_CREATED,
            envelopeId = envelopeId,
            matchedBy = null
        )

        fun alreadySaved(existingEnvelopeId: String, matchedBy: String): SealResultParcel =
            SealResultParcel(
                status = STATUS_ALREADY_SAVED,
                envelopeId = existingEnvelopeId,
                matchedBy = matchedBy
            )

        override fun createFromParcel(parcel: Parcel) = SealResultParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<SealResultParcel>(size)
    }
}
