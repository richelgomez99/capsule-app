package com.capsule.app.net.ipc

import android.os.Parcel
import android.os.Parcelable

/**
 * Spec 013 (data-model §2.1) — AIDL parcel for [INetworkGateway.callLlmGateway].
 *
 * Single `payloadJson` field carries the
 * `kotlinx.serialization.Json.encodeToString(LlmGatewayRequest)` output.
 * JSON-in-String is the canonical Android workaround for sealed-class
 * Parcelable with N variants (per-field Parcelable would require either
 * polymorphic Parcelable or a brittle nullable-everywhere schema).
 *
 * Mirrors [FetchResultParcel]'s manual `writeToParcel` + `CREATOR`
 * pattern (no Kotlin Parcelize — the codebase keeps the parcel layer
 * dependency-free for `:net` IPC reasons).
 */
data class LlmGatewayRequestParcel(
    val payloadJson: String,
) : Parcelable {

    constructor(parcel: Parcel) : this(
        payloadJson = requireNotNull(parcel.readString()) { "payloadJson MUST be non-null" },
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(payloadJson)
    }

    override fun describeContents(): Int = 0

    companion object CREATOR : Parcelable.Creator<LlmGatewayRequestParcel> {
        override fun createFromParcel(parcel: Parcel) = LlmGatewayRequestParcel(parcel)
        override fun newArray(size: Int) = arrayOfNulls<LlmGatewayRequestParcel>(size)
    }
}
