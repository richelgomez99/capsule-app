package com.capsule.app.data.ipc

import android.os.Parcel
import android.os.Parcelable

/**
 * AIDL-carried view of an envelope for the diary UI.
 *
 * Exposes the subset of [com.capsule.app.data.entity.IntentEnvelopeEntity]
 * that the UI process needs, including the `StateSnapshot` fields
 * (`appCategory`, `activityState`, `hourLocal`, `dayOfWeekLocal`) which
 * drive thread grouping (T047) and the "from {app} · {activity} · {time}"
 * card subtitle (T051).
 *
 * Continuation-derived fields (`title`, `domain`, `excerpt`, `summary`) are
 * populated only after US3 runs — null on fresh seals.
 */
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
    val summary: String?,
    val appCategory: String,
    val activityState: String,
    val hourLocal: Int,
    val dayOfWeekLocal: Int,
    /** T055a — raw `intentHistoryJson` for the detail screen. "[]" on legacy rows. */
    val intentHistoryJson: String = "[]",
    /** T055a — canonical URL from a successful hydration; null if not hydrated. */
    val canonicalUrl: String? = null,
    /** T091a — when soft-deleted; null on live envelopes. */
    val deletedAtMillis: Long? = null,
    /** T064 (003 US2) — derived to-do JSON: `{items:[…], derivedFromProposalId}`. Null for non-todo envelopes. */
    val todoMetaJson: String? = null
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
        summary = parcel.readString(),
        appCategory = parcel.readString()!!,
        activityState = parcel.readString()!!,
        hourLocal = parcel.readInt(),
        dayOfWeekLocal = parcel.readInt(),
        intentHistoryJson = parcel.readString() ?: "[]",
        canonicalUrl = parcel.readString(),
        deletedAtMillis = parcel.readLong().takeIf { it != 0L },
        todoMetaJson = parcel.readString()
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
        parcel.writeString(appCategory)
        parcel.writeString(activityState)
        parcel.writeInt(hourLocal)
        parcel.writeInt(dayOfWeekLocal)
        parcel.writeString(intentHistoryJson)
        parcel.writeString(canonicalUrl)
        parcel.writeLong(deletedAtMillis ?: 0L)
        parcel.writeString(todoMetaJson)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<EnvelopeViewParcel> {
        override fun createFromParcel(parcel: Parcel) = EnvelopeViewParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<EnvelopeViewParcel>(size)
    }
}
