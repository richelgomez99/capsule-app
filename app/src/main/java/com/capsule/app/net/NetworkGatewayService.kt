package com.capsule.app.net

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.capsule.app.net.ipc.FetchResultParcel
import com.capsule.app.net.ipc.INetworkGateway
import com.capsule.app.net.ipc.LlmGatewayRequestParcel
import com.capsule.app.net.ipc.LlmGatewayResponseParcel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Bound service running in :net process. Sole network egress point.
 * Principle VI — only this process holds INTERNET permission.
 *
 * Callers: :ml only (Continuation Engine).
 */
class NetworkGatewayService : Service() {

    private val serviceJob: Job = SupervisorJob()
    private val serviceScope: CoroutineScope = CoroutineScope(Dispatchers.IO + serviceJob)

    private val impl: NetworkGatewayImpl by lazy {
        NetworkGatewayImpl(
            client = SafeOkHttpClient.build(),
            appContext = applicationContext,
        )
    }

    private val binder = object : INetworkGateway.Stub() {
        override fun fetchPublicUrl(url: String, timeoutMs: Long): FetchResultParcel =
            impl.fetchPublicUrl(url, timeoutMs)

        // Spec 013 (T013-012) — wired to NetworkGatewayImpl.callLlmGateway,
        // which decodes the parcel, delegates to LlmGatewayClient, and
        // re-encodes the typed response (FR-013-011).
        override fun callLlmGateway(request: LlmGatewayRequestParcel): LlmGatewayResponseParcel =
            impl.callLlmGateway(request)
    }

    override fun onCreate() {
        super.onCreate()
        // T014-019b — fire-and-forget debug seed. Release builds compile a
        // no-op stub (DebugSupabaseSeed.kt at app/src/main/java/...). The
        // debug variant overrides the same FQN under app/src/debug/java/...
        // and performs a one-shot signInWith(Email) when DEBUG_SUPABASE_*
        // creds are present in local.properties.
        serviceScope.launch {
            val sb = impl.supabaseClient ?: return@launch
            try {
                seedIfNeeded(sb)
            } catch (t: Throwable) {
                Log.w(TAG, "debug seed threw: ${t.javaClass.simpleName}")
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private companion object {
        const val TAG: String = "NetworkGatewayService"
    }
}
