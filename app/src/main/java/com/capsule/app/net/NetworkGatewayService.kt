package com.capsule.app.net

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.os.Process
import com.capsule.app.net.ipc.FetchResultParcel
import com.capsule.app.net.ipc.INetworkGateway

/**
 * Bound service running in :net process. Sole network egress point.
 * Principle VI — only this process holds INTERNET permission.
 *
 * Callers: :ml only (Continuation Engine).
 */
class NetworkGatewayService : Service() {

    private val binder = object : INetworkGateway.Stub() {

        override fun fetchPublicUrl(url: String, timeoutMs: Long): FetchResultParcel {
            // UID check per contracts/network-gateway-contract.md §2
            val callingUid = Binder.getCallingUid()
            if (callingUid != Process.myUid()) {
                return FetchResultParcel(
                    ok = false,
                    finalUrl = null,
                    title = null,
                    canonicalHost = null,
                    readableHtml = null,
                    errorKind = "unauthorized",
                    errorMessage = "Caller UID $callingUid is not allowed",
                    fetchedAtMillis = System.currentTimeMillis()
                )
            }
            // SafeOkHttpClient delegation will be fleshed out in US3
            TODO("Implemented in US3 — SafeOkHttpClient")
        }
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
