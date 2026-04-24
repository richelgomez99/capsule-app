package com.capsule.app.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
}
