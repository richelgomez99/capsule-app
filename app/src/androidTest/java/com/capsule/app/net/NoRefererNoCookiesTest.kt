package com.capsule.app.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.CookieJar
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T057 — verifies the "no secrets leak" invariants of
 * contracts/network-gateway-contract.md §4:
 *   - `CookieJar.NO_COOKIES` on the OkHttpClient
 *   - outbound request contains no `Referer` header
 *   - outbound request uses the fixed `User-Agent: Orbit/1.0 ...`
 */
@RunWith(AndroidJUnit4::class)
class NoRefererNoCookiesTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test fun cookieJar_isNoCookies() {
        val client = SafeOkHttpClient.build()
        assertSame(CookieJar.NO_COOKIES, client.cookieJar)
    }

    @Test fun outgoingRequest_hasNoRefererAndFixedUserAgent() {
        server.enqueue(MockResponse().setResponseCode(200).setBody("<html></html>"))

        val gateway = NetworkGatewayImpl(
            client = SafeOkHttpClient.build(),
            validator = UrlValidator(requireHttps = false),
        )
        // Simulate a caller that has (incorrectly) set a Referer — our interceptor
        // must strip it. We do this by directly driving the client under test: the
        // gateway builds its own Request so to verify stripping we simulate a
        // rogue caller by using the client directly with a Referer header.
        gateway.fetchPublicUrl(server.url("/p").toString(), 10_000)

        val recorded = server.takeRequest()
        assertNull("Referer must never be sent", recorded.getHeader("Referer"))
        assertNull("Cookie must never be sent", recorded.getHeader("Cookie"))
        assertEquals(SafeOkHttpClient.USER_AGENT, recorded.getHeader("User-Agent"))
    }

    @Test fun rogueCallerReferer_isStrippedByInterceptor() {
        // Prove that even if something further up the stack tried to set a Referer,
        // the SafeOkHttpClient interceptor strips it before it hits the wire.
        server.enqueue(MockResponse().setResponseCode(200))
        val client = SafeOkHttpClient.build()
        val req = okhttp3.Request.Builder()
            .url(server.url("/p"))
            .header("Referer", "https://leak.example")
            .header("Cookie", "session=leak")
            .build()
        client.newCall(req).execute().close()

        val recorded = server.takeRequest()
        assertNull(recorded.getHeader("Referer"))
        assertNull(recorded.getHeader("Cookie"))
        assertTrue(recorded.getHeader("User-Agent") == SafeOkHttpClient.USER_AGENT)
    }
}
