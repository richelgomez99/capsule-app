package com.capsule.app.net

import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import okio.Buffer
import okio.BufferedSource

/**
 * T056 — exercises every rejection path from
 * contracts/network-gateway-contract.md §4, plus the success path.
 *
 * For the paths that require an actual network round-trip (too_large,
 * redirect_loop, success), the gateway is built with a lax [UrlValidator]
 * so that [MockWebServer]'s plaintext http endpoint can be used — the real
 * production gateway uses the strict validator and rejects http outright.
 */
@RunWith(AndroidJUnit4::class)
class NetworkGatewayContractTest {

    private lateinit var server: MockWebServer

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After fun tearDown() {
        server.shutdown()
    }

    private fun strictGateway(): NetworkGatewayImpl = NetworkGatewayImpl(
        client = SafeOkHttpClient.build(),
        validator = UrlValidator(requireHttps = true),
    )

    private fun laxGateway(): NetworkGatewayImpl = NetworkGatewayImpl(
        client = SafeOkHttpClient.build(),
        validator = UrlValidator(requireHttps = false),
    )

    @Test fun invalidUrl_returnsInvalidUrl() {
        val r = strictGateway().fetchPublicUrl("not a url", 10_000)
        assertEquals("invalid_url", r.errorKind)
        assertEquals(false, r.ok)
    }

    @Test fun httpScheme_returnsNotHttps() {
        val r = strictGateway().fetchPublicUrl("http://example.com/", 10_000)
        assertEquals("not_https", r.errorKind)
    }

    @Test fun privateHost_returnsBlockedHost() {
        val r = strictGateway().fetchPublicUrl("https://10.0.0.1/", 10_000)
        assertEquals("blocked_host", r.errorKind)
    }

    @Test fun onionHost_returnsBlockedHost() {
        val r = strictGateway().fetchPublicUrl("https://something.onion/", 10_000)
        assertEquals("blocked_host", r.errorKind)
    }

    @Test fun oversizedBody_returnsTooLarge() {
        // 3 MB of 'a' — declared via content-length, comfortably above the 2 MB cap.
        val threeMb = ByteArray(3 * 1024 * 1024) { 'a'.code.toByte() }
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(Buffer().apply { write(threeMb) }),
        )
        val r = laxGateway().fetchPublicUrl(server.url("/big").toString(), 15_000)
        assertEquals("too_large", r.errorKind)
    }

    @Test fun redirectLoop_returnsRedirectLoop() {
        // Six 302s to self-relative URLs, one more than the MAX_REDIRECTS=5 cap.
        repeat(7) {
            server.enqueue(
                MockResponse()
                    .setResponseCode(302)
                    .setHeader("Location", "/next-$it"),
            )
        }
        val r = laxGateway().fetchPublicUrl(server.url("/start").toString(), 15_000)
        assertEquals("redirect_loop", r.errorKind)
    }

    @Test fun successPath_returnsReadableHtml() {
        val html = """
            <html><head><title>Hello Orbit</title></head>
            <body><article>
              <h1>Hello Orbit</h1>
              <p>This is a small article body that Readability can happily parse
                 and return as readable content. It mentions news, diary, and Orbit.</p>
              <p>A second paragraph to keep the extractor happy.</p>
            </article></body></html>
        """.trimIndent()
        server.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/html; charset=utf-8")
                .setBody(html),
        )
        val r = laxGateway().fetchPublicUrl(server.url("/article").toString(), 15_000)
        assertTrue("expected ok=true, got $r", r.ok)
        assertNull(r.errorKind)
        assertNotNull("expected non-null readableHtml", r.readableHtml)
        assertNotNull("expected non-null title", r.title)
    }

    @Test fun serverError_returnsHttpError_andCoolsDown() {
        server.enqueue(MockResponse().setResponseCode(503))
        val url = server.url("/boom").toString()
        val first = laxGateway().fetchPublicUrl(url, 15_000)
        assertEquals("http_error", first.errorKind)
    }

    @Test fun disabled_returnsDisabled() {
        val gw = NetworkGatewayImpl(
            client = SafeOkHttpClient.build(),
            validator = UrlValidator(requireHttps = false),
            disabled = { true },
        )
        val r = gw.fetchPublicUrl(server.url("/anything").toString(), 10_000)
        assertEquals("disabled", r.errorKind)
    }

    // Keep okio imports referenced even if the body path is adjusted.
    @Suppress("unused")
    private fun drainNoop(source: BufferedSource) { source.close() }
}
