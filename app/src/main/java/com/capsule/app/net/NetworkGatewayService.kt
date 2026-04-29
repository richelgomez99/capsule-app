package com.capsule.app.net

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.capsule.app.net.ipc.FetchResultParcel
import com.capsule.app.net.ipc.INetworkGateway
import com.capsule.app.net.ipc.LlmGatewayRequestParcel
import com.capsule.app.net.ipc.LlmGatewayResponseParcel

/**
 * Bound service running in :net process. Sole network egress point.
 * Principle VI — only this process holds INTERNET permission.
 *
 * Callers: :ml only (Continuation Engine).
 */
class NetworkGatewayService : Service() {

    private val impl: NetworkGatewayImpl by lazy {
        NetworkGatewayImpl(client = SafeOkHttpClient.build())
    }

    private val binder = object : INetworkGateway.Stub() {
        override fun fetchPublicUrl(url: String, timeoutMs: Long): FetchResultParcel =
            impl.fetchPublicUrl(url, timeoutMs)

        // Spec 013 (T013-008) — AIDL surface added; real handler wired in T013-012.
        // Until then this stub keeps the binder concrete and the build green; any
        // caller that beats T013-012 to the punch surfaces a clear error envelope.
        override fun callLlmGateway(request: LlmGatewayRequestParcel): LlmGatewayResponseParcel =
            LlmGatewayResponseParcel(
                """{"type":"error","requestId":"","code":"INTERNAL","message":"callLlmGateway not yet wired (T013-012)"}"""
            )
    }

    override fun onBind(intent: Intent?): IBinder = binder
}
