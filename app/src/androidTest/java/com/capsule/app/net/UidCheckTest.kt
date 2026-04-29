package com.capsule.app.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.net.ipc.LlmGatewayRequestParcel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T058 — verifies the UID gate from contracts/network-gateway-contract.md §2.
 *
 * A proper cross-UID test requires a separate test APK bound to a second
 * process; see the Phase 10 quickstart §3 for that. This test exercises the
 * reject branch via the [NetworkGatewayImpl.callerUidProvider] seam, which is
 * the same check `Binder.getCallingUid()` feeds in production.
 */
@RunWith(AndroidJUnit4::class)
class UidCheckTest {

    @Test fun foreignUid_isRejected() {
        val gateway = NetworkGatewayImpl(
            client = SafeOkHttpClient.build(),
            callerUidProvider = { 99_999 },
            myUidProvider = { 10_042 },
        )
        val r = gateway.fetchPublicUrl("https://example.com/", 10_000)
        assertFalse(r.ok)
        assertEquals("unauthorized", r.errorKind)
    }

    @Test fun sameUid_passesUidGate() {
        // Validator will still reject the bogus URL before any network I/O, but
        // the rejection reason must NOT be "unauthorized" — proving the UID gate
        // accepted the caller.
        val gateway = NetworkGatewayImpl(
            client = SafeOkHttpClient.build(),
            callerUidProvider = { 10_042 },
            myUidProvider = { 10_042 },
        )
        val r = gateway.fetchPublicUrl("not a url", 10_000)
        assertEquals("invalid_url", r.errorKind)
    }

    // ─── Spec 014 hotfix (PR #1 review item 13) ────────────────────────
    // The callLlmGateway AIDL boundary duplicates the UID gate from
    // fetchPublicUrl. A regression dropping that gate would not be
    // caught by existing tests. Lock down both branches at the binder
    // layer.

    @Test fun callLlmGateway_foreignUid_isRejectedBeforeNetwork() {
        val gateway = NetworkGatewayImpl(
            client = SafeOkHttpClient.build(),
            callerUidProvider = { 99_999 },
            myUidProvider = { 10_042 },
        )
        // Valid Embed payload — the gate must reject before this is
        // decoded or sent across the network.
        val payload =
            """{"type":"embed","requestId":"11111111-2222-3333-4444-555555555555","text":"hello"}"""
        val r = gateway.callLlmGateway(LlmGatewayRequestParcel(payload))
        val parsed = Json.parseToJsonElement(r.payloadJson)
        // Response shape: {"type":"error","requestId":"","code":"UNAUTHORIZED","message":...}
        val obj = parsed.toString()
        assertTrue(
            "expected UNAUTHORIZED in foreign-uid response, got $obj",
            obj.contains("\"code\":\"UNAUTHORIZED\""),
        )
    }

    @Test fun callLlmGateway_sameUid_doesNotShortCircuitOnUid() {
        // Caller UID matches → the UID gate passes. A garbage payload
        // then fails decode and returns MALFORMED_RESPONSE. The point
        // is that the rejection reason is NOT "UNAUTHORIZED" — proving
        // the binder-layer UID gate accepted the caller.
        val gateway = NetworkGatewayImpl(
            client = SafeOkHttpClient.build(),
            callerUidProvider = { 10_042 },
            myUidProvider = { 10_042 },
        )
        val r = gateway.callLlmGateway(LlmGatewayRequestParcel("not json"))
        assertFalse(
            "UID gate must accept same-uid caller; got ${r.payloadJson}",
            r.payloadJson.contains("\"code\":\"UNAUTHORIZED\""),
        )
    }
}
