package com.capsule.app.net

import com.capsule.app.ai.gateway.LlmGatewayRequest
import com.capsule.app.ai.gateway.LlmGatewayResponse
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Spec 014 T014-019 \u2014 verifies `LlmGatewayClient` enforces the
 * `AuthStateBinder` contract:
 *
 *  1. null/blank JWT \u2192 `Error(UNAUTHORIZED)`, network NEVER touched.
 *  2. non-null JWT  \u2192 outbound request carries `Authorization: Bearer <jwt>`.
 */
class LlmGatewayClientAuthTest {

    private val req = LlmGatewayRequest.Embed(requestId = REQ_ID, text = "hello")

    @Test
    fun nullJwtShortCircuitsToUnauthorizedWithoutNetwork() = runTest {
        val callCount = AtomicInteger(0)
        val countingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                callCount.incrementAndGet()
                throw AssertionError("HTTP client must not be invoked when JWT is null")
            })
            .build()
        val binder = FakeAuthStateBinder(initialJwt = null)
        val client = LlmGatewayClient(
            client = countingClient,
            gatewayUrl = "https://unused.invalid/llm",
            authStateBinder = binder,
        )

        val response = client.call(req)

        assertTrue(
            "expected Error but got $response",
            response is LlmGatewayResponse.Error,
        )
        val error = response as LlmGatewayResponse.Error
        assertEquals("UNAUTHORIZED", error.code)
        assertEquals(REQ_ID, error.requestId)
        assertEquals("expected zero HTTP calls", 0, callCount.get())
        assertEquals("expected one binder query", 1, binder.callCount)
    }

    @Test
    fun blankJwtShortCircuitsToUnauthorizedWithoutNetwork() = runTest {
        val callCount = AtomicInteger(0)
        val countingClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                callCount.incrementAndGet()
                throw AssertionError("HTTP client must not be invoked when JWT is blank")
            })
            .build()
        val binder = FakeAuthStateBinder(initialJwt = "   ")
        val client = LlmGatewayClient(
            client = countingClient,
            gatewayUrl = "https://unused.invalid/llm",
            authStateBinder = binder,
        )

        val response = client.call(req)

        assertTrue(response is LlmGatewayResponse.Error)
        assertEquals("UNAUTHORIZED", (response as LlmGatewayResponse.Error).code)
        assertEquals(0, callCount.get())
    }

    @Test
    fun presentJwtStampsAuthorizationBearerHeader() = runTest {
        val captured = AtomicReference<String?>(null)
        val cannedBody = """{"type":"embed","ok":true,"data":{"vector":[0.1],"modelLabel":"openai/text-embedding-3-small","tokensIn":1,"tokensOut":0}}"""
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                captured.set(chain.request().header("Authorization"))
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(cannedBody.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        val jwt = "eyJhbGciOiJIUzI1NiJ9.payload.sig"
        val binder = FakeAuthStateBinder(initialJwt = jwt)
        val client = LlmGatewayClient(
            client = mockClient,
            gatewayUrl = "https://gateway.test.invalid/llm",
            authStateBinder = binder,
        )

        client.call(req)

        val header = captured.get()
        assertNotNull("Authorization header must be set when JWT is present", header)
        assertEquals("Bearer $jwt", header)
        assertEquals(1, binder.callCount)
    }

    @Test
    fun presentJwtStampsHeaderEvenWhenUpstreamFails() = runTest {
        // Even on upstream 401, the Authorization header must have been
        // sent (proves the bearer-token wiring is active end-to-end).
        val captured = AtomicReference<String?>(null)
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                captured.set(chain.request().header("Authorization"))
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(401)
                    .message("Unauthorized")
                    .body("".toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()
        val jwt = "test.jwt.value"
        val client = LlmGatewayClient(
            client = mockClient,
            gatewayUrl = "https://gateway.test.invalid/llm",
            authStateBinder = FakeAuthStateBinder(initialJwt = jwt),
        )

        val response = client.call(req)

        assertEquals("Bearer $jwt", captured.get())
        assertTrue(response is LlmGatewayResponse.Error)
        assertEquals("UNAUTHORIZED", (response as LlmGatewayResponse.Error).code)
    }

    @Test
    fun defaultBinderIsNoSession() = runTest {
        // Sanity: the default constructor produces UNAUTHORIZED, proving
        // NoSessionAuthStateBinder is the wired default.
        val mockClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { _ ->
                throw AssertionError("must not call network with default no-session binder")
            })
            .build()
        val client = LlmGatewayClient(
            client = mockClient,
            gatewayUrl = "https://gateway.test.invalid/llm",
        )

        val response = client.call(req)

        assertTrue(response is LlmGatewayResponse.Error)
        assertEquals("UNAUTHORIZED", (response as LlmGatewayResponse.Error).code)
        assertNull("no message-leak", null)
    }

    private companion object {
        const val REQ_ID = "11111111-2222-3333-4444-555555555555"
    }
}
