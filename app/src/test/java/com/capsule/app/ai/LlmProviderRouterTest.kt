package com.capsule.app.ai

import android.os.IBinder
import com.capsule.app.RuntimeFlags
import com.capsule.app.net.ipc.INetworkGateway
import com.capsule.app.net.ipc.LlmGatewayRequestParcel
import com.capsule.app.net.ipc.LlmGatewayResponseParcel
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Spec 013 (T013-017 / SC-004) — covers the [LlmProviderRouter]
 * resolution rules. Tests target the pure [LlmProviderRouter.resolve]
 * entry point so the unit suite stays Android-free.
 *
 * Cases:
 *  (a) default (`useLocalAi = false`) → returns [CloudLlmProvider].
 *  (b) `useLocalAi = true` + Nano-capable hardware = `false` (the Day-1
 *      stub) → still returns [CloudLlmProvider] (acceptance scenario 3).
 *  (c) `useLocalAi = false` + `networkGateway = null` → throws an
 *      [IllegalStateException] whose message names the missing
 *      dependency.
 */
class LlmProviderRouterTest {

    private class FakeGateway : INetworkGateway {
        override fun fetchPublicUrl(url: String?, timeoutMs: Long) =
            error("not used")

        override fun callLlmGateway(request: LlmGatewayRequestParcel): LlmGatewayResponseParcel =
            error("not used")

        override fun asBinder(): IBinder = throw UnsupportedOperationException("test fake")
    }

    @After
    fun tearDown() {
        RuntimeFlags.useLocalAi = false
    }

    @Test
    fun default_resolution_returns_cloud_provider() {
        val provider = LlmProviderRouter.resolve(
            useLocalAi = false,
            hasNanoCapableHardware = false,
            networkGateway = FakeGateway(),
        )
        assertNotNull(provider)
        assertTrue(
            "Default resolution must be CloudLlmProvider, was ${provider::class.java.simpleName}",
            provider is CloudLlmProvider,
        )
    }

    @Test
    fun use_local_ai_without_nano_capable_hardware_falls_through_to_cloud() {
        val provider = LlmProviderRouter.resolve(
            useLocalAi = true,
            hasNanoCapableHardware = false,
            networkGateway = FakeGateway(),
        )
        assertTrue(
            "useLocalAi=true + no Nano hardware must still return CloudLlmProvider, " +
                "was ${provider::class.java.simpleName}",
            provider is CloudLlmProvider,
        )
    }

    @Test
    fun cloud_branch_with_null_gateway_throws_illegal_state_naming_dependency() {
        try {
            LlmProviderRouter.resolve(
                useLocalAi = false,
                hasNanoCapableHardware = false,
                networkGateway = null,
            )
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            val message = e.message ?: ""
            assertTrue(
                "Message must name the missing dependency (networkGateway): $message",
                message.contains("networkGateway"),
            )
        }
    }

    @Test
    fun use_local_ai_with_nano_capable_hardware_returns_nano_provider() {
        val provider = LlmProviderRouter.resolve(
            useLocalAi = true,
            hasNanoCapableHardware = true,
            networkGateway = null,
        )
        assertTrue(
            "useLocalAi=true + Nano hardware=true must return NanoLlmProvider, " +
                "was ${provider::class.java.simpleName}",
            provider is NanoLlmProvider,
        )
    }
}
