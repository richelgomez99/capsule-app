package com.capsule.app.ai.gateway

import com.capsule.app.net.ipc.LlmGatewayRequestParcel
import com.capsule.app.net.ipc.LlmGatewayResponseParcel
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Spec 013 (T013-015) — round-trip parity for the gateway parcels.
 *
 * The parcels are intentionally minimal (single `payloadJson: String`
 * field), so the production-equivalent round-trip is JSON-encode →
 * wrap-in-parcel → read `payloadJson` → JSON-decode → assert structural
 * equality. The Android `Parcel` layer is a faithful `String`
 * serializer; testing it would require Robolectric and would only prove
 * what the platform already guarantees. This unit test instead verifies
 * the same end-to-end path that ships in production: kotlinx.serialization
 * round-trip parity for every sealed-class subtype, plus the
 * `EmbedResponse` content-based `equals`/`hashCode` contract that the
 * cluster engine relies on (FR-038/039 + T013-004 acceptance).
 */
class LlmGatewayParcelRoundTripTest {

    private val codec = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val state = StateSnapshotJson(
        appCategory = "OTHER",
        activityState = "STILL",
        tzId = "UTC",
        hourLocal = 12,
        dayOfWeekLocal = 3,
    )

    private val function = AppFunctionSummaryJson(
        functionId = "fn.1",
        schemaVersion = 1,
        displayName = "fn",
        description = "desc",
        argsSchemaJson = "{}",
        sensitivityScope = "PERSONAL",
    )

    private val proposal = ActionProposalJson(
        functionId = "fn.1",
        schemaVersion = 1,
        argsJson = "{}",
        previewTitle = "title",
        previewSubtitle = null,
        confidence = 0.9f,
        sensitivityScope = "PERSONAL",
    )

    // --- Request round-trip per subtype ---

    private fun roundTripRequest(original: LlmGatewayRequest) {
        val parcel = LlmGatewayRequestParcel(
            codec.encodeToString(LlmGatewayRequest.serializer(), original),
        )
        val decoded = codec.decodeFromString(LlmGatewayRequest.serializer(), parcel.payloadJson)
        assertEquals(original, decoded)
    }

    @Test fun embed_request_round_trip() = roundTripRequest(
        LlmGatewayRequest.Embed(requestId = "rid-e", text = "hello"),
    )

    @Test fun summarize_request_round_trip() = roundTripRequest(
        LlmGatewayRequest.Summarize(requestId = "rid-s", text = "hello", maxTokens = 32),
    )

    @Test fun extract_actions_request_round_trip() = roundTripRequest(
        LlmGatewayRequest.ExtractActions(
            requestId = "rid-ea",
            text = "x",
            contentType = "text/plain",
            state = state,
            registeredFunctions = listOf(function),
            maxCandidates = 3,
        ),
    )

    @Test fun classify_intent_request_round_trip() = roundTripRequest(
        LlmGatewayRequest.ClassifyIntent(requestId = "rid-c", text = "x", appCategory = "OTHER"),
    )

    @Test fun generate_day_header_request_round_trip() = roundTripRequest(
        LlmGatewayRequest.GenerateDayHeader(
            requestId = "rid-d",
            dayIsoDate = "2026-04-29",
            envelopeSummaries = listOf("a", "b"),
        ),
    )

    @Test fun scan_sensitivity_request_round_trip() = roundTripRequest(
        LlmGatewayRequest.ScanSensitivity(requestId = "rid-ss", text = "x"),
    )

    // --- Response round-trip per variant ---

    private fun roundTripResponse(original: LlmGatewayResponse) {
        val parcel = LlmGatewayResponseParcel(
            codec.encodeToString(LlmGatewayResponse.serializer(), original),
        )
        val decoded = codec.decodeFromString(LlmGatewayResponse.serializer(), parcel.payloadJson)
        assertEquals(original, decoded)
    }

    @Test fun summarize_response_round_trip() = roundTripResponse(
        LlmGatewayResponse.SummarizeResponse("rid", "summary", "model"),
    )

    @Test fun extract_actions_response_round_trip() = roundTripResponse(
        LlmGatewayResponse.ExtractActionsResponse("rid", listOf(proposal), "model"),
    )

    @Test fun classify_intent_response_round_trip() = roundTripResponse(
        LlmGatewayResponse.ClassifyIntentResponse("rid", "WANT_IT", 0.9f, "model"),
    )

    @Test fun generate_day_header_response_round_trip() = roundTripResponse(
        LlmGatewayResponse.GenerateDayHeaderResponse("rid", "header", "model"),
    )

    @Test fun scan_sensitivity_response_round_trip() = roundTripResponse(
        LlmGatewayResponse.ScanSensitivityResponse("rid", listOf("financial"), "model"),
    )

    @Test fun error_response_round_trip() = roundTripResponse(
        LlmGatewayResponse.Error("rid", "INTERNAL", "msg"),
    )

    // --- EmbedResponse content-based equals / hashCode ---

    @Test fun embed_response_round_trip_preserves_vector() {
        val original = LlmGatewayResponse.EmbedResponse(
            requestId = "rid",
            vector = floatArrayOf(0.1f, 0.2f, 0.3f),
            modelLabel = "model",
        )
        val parcel = LlmGatewayResponseParcel(
            codec.encodeToString(LlmGatewayResponse.serializer(), original),
        )
        val decoded = codec.decodeFromString(LlmGatewayResponse.serializer(), parcel.payloadJson)
                as LlmGatewayResponse.EmbedResponse
        assertTrue(original.vector.contentEquals(decoded.vector))
        assertEquals(original, decoded)
        assertEquals(original.hashCode(), decoded.hashCode())
    }

    @Test fun embed_response_equality_is_content_based() {
        val a = LlmGatewayResponse.EmbedResponse(
            requestId = "rid",
            vector = floatArrayOf(0.1f, 0.2f),
            modelLabel = "model",
        )
        val b = LlmGatewayResponse.EmbedResponse(
            requestId = "rid",
            vector = floatArrayOf(0.1f, 0.2f),
            modelLabel = "model",
        )
        val different = LlmGatewayResponse.EmbedResponse(
            requestId = "rid",
            vector = floatArrayOf(0.1f, 0.3f),
            modelLabel = "model",
        )
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, different)
    }
}
