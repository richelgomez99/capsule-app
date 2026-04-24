package com.capsule.app.data.ipc

import android.os.Parcel
import android.os.Parcelable

data class IntentEnvelopeDraftParcel(
    val contentType: String,
    val textContent: String?,
    val imageUri: String?,
    val intent: String,
    val intentConfidence: Float?,
    val intentSource: String,
    /**
     * T037c — per-type count of redactions applied by [com.capsule.app.capture.SensitivityScrubber]
     * in the `:capture` process before this parcel was built. When non-empty,
     * `EnvelopeRepositoryImpl.seal` writes one `CAPTURE_SCRUBBED` audit row
     * carrying only the counts (never the redacted values).
     */
    val redactionCountByType: Map<String, Int> = emptyMap()
) : Parcelable {

    constructor(parcel: Parcel) : this(
        contentType = parcel.readString()!!,
        textContent = parcel.readString(),
        imageUri = parcel.readString(),
        intent = parcel.readString()!!,
        intentConfidence = parcel.readValue(Float::class.java.classLoader) as? Float,
        intentSource = parcel.readString()!!,
        redactionCountByType = readStringIntMap(parcel)
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(contentType)
        parcel.writeString(textContent)
        parcel.writeString(imageUri)
        parcel.writeString(intent)
        parcel.writeValue(intentConfidence)
        parcel.writeString(intentSource)
        parcel.writeInt(redactionCountByType.size)
        for ((k, v) in redactionCountByType) {
            parcel.writeString(k)
            parcel.writeInt(v)
        }
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<IntentEnvelopeDraftParcel> {
        override fun createFromParcel(parcel: Parcel) = IntentEnvelopeDraftParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<IntentEnvelopeDraftParcel>(size)

        private fun readStringIntMap(parcel: Parcel): Map<String, Int> {
            val size = parcel.readInt()
            if (size <= 0) return emptyMap()
            val out = LinkedHashMap<String, Int>(size)
            repeat(size) {
                val k = parcel.readString() ?: return@repeat
                val v = parcel.readInt()
                out[k] = v
            }
            return out
        }
    }
}
