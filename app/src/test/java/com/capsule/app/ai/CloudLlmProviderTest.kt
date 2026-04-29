package com.capsule.app.ai

import android.os.IBinder
import com.capsule.app.ai.gateway.LlmGatewayRequest
import com.capsule.app.ai.gateway.LlmGatewayResponse
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.net.ipc.INetworkGateway
import com.capsule.app.net.ipc.LlmGatewayRequestParcel
import com.capsule.app.net.ipc.LlmGatewayResponseParcel
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

/**
 * Spec 013 (T013-014) — unit tests for [CloudLlmProvider]'s asymmetric
 * error contract per data-model §1.3.
 *
 * Cases:
 *  - `embed()` returns `null` on every `Error.code`,
 *  - `summarize()` and `extractActions()` throw [IOException] on `Error`,
 *  - the remaining four methods throw on `Error`,
 *  - success path returns the expected typed result for each method.
 */
class CloudLlmProviderTest {

    private val codec = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val state = StateSnapshot(
        appCategory = AppCategory.OTHER,
        activityState = ActivityState.STILL,
        tzId = "UTC",
        hourLocal = 12,
        dayOfWeekLocal = 3,
    )

    /** Test gateway that always returns a canned response. */
    private class FakeGateway(
        private val responder: (LlmGatewayRequestParcel) -> LlmGatewayResponseParcel,
    ) : INetworkGateway {
        override fun fetchPublicUrl(url: String?, timeoutMs: Long) =
            error("fetchPublicUrl is not used in CloudLlmProviderTest")

        override fun callLlmGateway(request: LlmGatewayRequestParcel): LlmGatewayResponseParcel =
            responder(request)

        override fun asBinder(): IBinder =
            throw UnsupportedOperationException("test fake — asBinder unused")
    }

    private fun gatewayReturning(response: LlmGatewayResponse): FakeGateway = FakeGateway { _ ->
        LlmGatewayResponseParcel(
            codec.encodeToString(LlmGatewayResponse.serializer(), response),
        )
    }

    private fun errorParcel(code: String): LlmGatewayResponseParcel = LlmGatewayResponseParcel(
        codec.encodeToString(
            LlmGatewayResponse.serializer(),
            LlmGatewayResponse.Error("rid", code, "msg-$code"),
        ),
    )

    private val errorCodes = listOf(
        "NETWORK_UNAVAILABLE", "GATEWAY_5XX", "PROVIDER_5XX",
        "TIMEOUT", "MALFORMED_RESPONSE", "UNAUTHORIZED", "INTERNAL",
    )

    // --- embed() — returns null on every error code ---

    @Test
    fun embed_returns_null_on_every_error_code() = runTest {
        for (code in errorCodes) {
            val gateway = FakeGateway { _ -> errorParcel(code) }
            val result = CloudLlmProvider(gateway).embed("hello")
            assertNull("embed() must return null on $code", result)
        }
    }

    @Test
    fun embed_blank_input_returns_null_without_calling_gateway() = runTest {
        var calls = 0
        val gateway = FakeGateway { _ ->
            calls++
            errorParcel("INTERNAL")
        }
        assertNull(CloudLlmProvider(gateway).embed(""))
        assertNull(CloudLlmProvider(gateway).embed("   "))
        assertEquals(0, calls)
    }

    @Test
    fun embed_success_returns_typed_result() = runTest {
        val vector = floatArrayOf(0.1f, 0.2f, 0.3f)
        val gateway = gatewayReturning(
            LlmGatewayResponse.EmbedResponse("rid", vector, "model-x"),
        )
        val result = CloudLlmProvider(gateway).embed("hello")
        assertTrue(result != null)
        assertEquals("model-x", result!!.modelLabel)
        assertEquals(3, result.dimensionality)
        assertTrue(vector.contentEquals(result.vector))
    }

    // --- summarize() — throws IOException on Error ---

    @Test
    fun summarize_throws_io_exception_on_every_error_code() = runTest {
        for (code in errorCodes) {
            val gateway = FakeGateway { _ -> errorParcel(code) }
            try {
                CloudLlmProvider(gateway).summarize("hi", maxTokens = 32)
                fail("Expected IOException for code $code")
            } catch (e: IOException) {
                assertTrue(
                    "Message must include error code: ${e.message}",
                    (e.message ?: "").contains(code),
                )
            }
        }
    }

    @Test
    fun summarize_success_returns_summary_result() = runTest {
        val gateway = gatewayReturning(
            LlmGatewayResponse.SummarizeResponse("rid", "the summary", "model-s"),
        )
        val result = CloudLlmProvider(gateway).summarize("text", maxTokens = 32)
        assertEquals("the summary", result.text)
    }

    // --- extractActions() — throws IOException on Error ---

    @Test
    fun extractActions_throws_io_exception_on_every_error_code() = runTest {
        for (code in errorCodes) {
            val gateway = FakeGateway { _ -> errorParcel(code) }
            try {
                CloudLlmProvider(gateway).extractActions(
                    text = "hi",
                    contentType = "text/plain",
                    state = state,
                    registeredFunctions = emptyList(),
                    maxCandidates = 3,
                )
                fail("Expected IOException for code $code")
            } catch (e: IOException) {
                assertTrue(
                    "Message must include error code: ${e.message}",
                    (e.message ?: "").contains(code),
                )
            }
        }
    }

    @Test
    fun extractActions_success_returns_empty_candidates() = runTest {
        val gateway = gatewayReturning(
            LlmGatewayResponse.ExtractActionsResponse("rid", emptyList(), "model-e"),
        )
        val result = CloudLlmProvider(gateway).extractActions(
            text = "hi",
            contentType = "text/plain",
            state = state,
            registeredFunctions = emptyList(),
            maxCandidates = 3,
        )
        assertEquals(0, result.candidates.size)
    }

    // --- classifyIntent / generateDayHeader / scanSensitivity — throw on Error ---

    @Test
    fun classifyIntent_throws_io_exception_on_error() = runTest {
        val gateway = FakeGateway { _ -> errorParcel("INTERNAL") }
        try {
            CloudLlmProvider(gateway).classifyIntent("hi", "OTHER")
            fail("Expected IOException")
        } catch (_: IOException) {
        }
    }

    @Test
    fun classifyIntent_success_returns_typed_result() = runTest {
        val gateway = gatewayReturning(
            LlmGatewayResponse.ClassifyIntentResponse(
                requestId = "rid",
                intent = "WANT_IT",
                confidence = 0.9f,
                modelLabel = "model-i",
            ),
        )
        val result = CloudLlmProvider(gateway).classifyIntent("hi", "OTHER")
        assertEquals(0.9f, result.confidence, 0.0001f)
    }

    @Test
    fun generateDayHeader_throws_io_exception_on_error() = runTest {
        val gateway = FakeGateway { _ -> errorParcel("TIMEOUT") }
        try {
            CloudLlmProvider(gateway).generateDayHeader("2026-04-29", emptyList())
            fail("Expected IOException")
        } catch (_: IOException) {
        }
    }

    @Test
    fun generateDayHeader_success_returns_typed_result() = runTest {
        val gateway = gatewayReturning(
            LlmGatewayResponse.GenerateDayHeaderResponse("rid", "Today header", "model-d"),
        )
        val result = CloudLlmProvider(gateway).generateDayHeader("2026-04-29", emptyList())
        assertEquals("Today header", result.text)
    }

    @Test
    fun scanSensitivity_throws_io_exception_on_error() = runTest {
        val gateway = FakeGateway { _ -> errorParcel("UNAUTHORIZED") }
        try {
            CloudLlmProvider(gateway).scanSensitivity("hi")
            fail("Expected IOException")
        } catch (_: IOException) {
        }
    }

    @Test
    fun scanSensitivity_success_returns_typed_result() = runTest {
        val gateway = gatewayReturning(
            LlmGatewayResponse.ScanSensitivityResponse(
                requestId = "rid",
                tags = listOf("financial"),
                modelLabel = "model-ss",
            ),
        )
        val result = CloudLlmProvider(gateway).scanSensitivity("hi")
        assertTrue(result.flagsJson.contains("financial"))
    }
}
