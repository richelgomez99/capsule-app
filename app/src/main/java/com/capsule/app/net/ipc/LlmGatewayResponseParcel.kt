package com.capsule.app.net.ipc

import android.os.Parcel
import android.os.Parcelable

/**
 * Spec 013 (data-model §2.2) — AIDL parcel for the response of
 * [INetworkGateway.callLlmGateway].
 *
 * Single `payloadJson` field carries the
 * `kotlinx.serialization.Json.encodeToString(LlmGatewayResponse)` output
 * (success variants or `LlmGatewayResponse.Error`). Same minimal
 * Parcelable pattern as [LlmGatewayRequestParcel] / [FetchResultParcel].
 */
data class LlmGatewayResponseParcel(
    val payloadJson: String,
) : Parcelable {

    constructor(parcel: Parcel) : this(
        payloadJson = requireNotNull(parcel.readString()) { "payloadJson MUST be non-null" },
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(payloadJson)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<LlmGatewayResponseParcel> {
        override fun createFromParcel(parcel: Parcel) = LlmGatewayResponseParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<LlmGatewayResponseParcel>(size)
    }
}
