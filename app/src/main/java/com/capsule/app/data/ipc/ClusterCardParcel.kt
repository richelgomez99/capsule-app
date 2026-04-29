package com.capsule.app.data.ipc

import android.os.Parcel
import android.os.Parcelable

/**
 * Cross-process projection of [com.capsule.app.data.ClusterCardModel]
 * (spec 002 Phase 11 Block 5 / T134). Carried over the
 * [IEnvelopeRepository.observeClusters] AIDL surface to the UI process.
 *
 * Member envelope ids are passed as a `String[]` and `int[]` pair —
 * Parcel arrays are cheaper than a list-of-parcelable here because
 * each member is just two scalars. Order matches the source list
 * (the repository sorts by `memberIndex` before parcelling).
 *
 * `state` is passed as a string so the parcel doesn't have to track
 * `ClusterState` enum ordinals; consumer maps via `valueOf`.
 */
data class ClusterCardParcel(
    val clusterId: String,
    val state: String,
    val timeBucketStart: Long,
    val timeBucketEnd: Long,
    val modelLabel: String,
    val memberEnvelopeIds: List<String>,
    val memberIndices: List<Int>
) : Parcelable {

    constructor(parcel: Parcel) : this(
        clusterId = parcel.readString()!!,
        state = parcel.readString()!!,
        timeBucketStart = parcel.readLong(),
        timeBucketEnd = parcel.readLong(),
        modelLabel = parcel.readString()!!,
        memberEnvelopeIds = mutableListOf<String>().also { parcel.readStringList(it) },
        memberIndices = parcel.createIntArray()!!.toList()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(clusterId)
        parcel.writeString(state)
        parcel.writeLong(timeBucketStart)
        parcel.writeLong(timeBucketEnd)
        parcel.writeString(modelLabel)
        parcel.writeStringList(memberEnvelopeIds)
        parcel.writeIntArray(memberIndices.toIntArray())
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<ClusterCardParcel> {
        override fun createFromParcel(parcel: Parcel) = ClusterCardParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<ClusterCardParcel>(size)
    }
}
