package com.capsule.app.ai.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Spec 013 (FR-013-004) — provider-agnostic response envelope from
 * the `:net`-process AI Gateway. Mirrors [LlmGatewayRequest]'s
 * sealed-class JSON discriminator pattern.
 *
 * Every variant carries the originating `requestId` (UUIDv4 string)
 * for SC-009 latency attribution. Every non-error variant carries a
 * `modelLabel` opaque to the client (used by the cluster engine for
 * Spec 002 FR-038/039 label-version drift detection).
 */
@Serializable
sealed class LlmGatewayResponse {
    abstract val requestId: String

    @Serializable
    @SerialName("embed_response")
    class EmbedResponse(
        override val requestId: String,
        val vector: FloatArray,
        val modelLabel: String,
    ) : LlmGatewayResponse() {
        // FloatArray's default identity-based equals/hashCode would break
        // unit-test equality and round-trip parity (T013-015). Override
        // with content-based equality, mirroring [EmbeddingResult].
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is EmbedResponse) return false
            if (requestId != other.requestId) return false
            if (modelLabel != other.modelLabel) return false
            if (!vector.contentEquals(other.vector)) return false
            return true
        }

        override fun hashCode(): Int {
            var result = requestId.hashCode()
            result = 31 * result + vector.contentHashCode()
            result = 31 * result + modelLabel.hashCode()
            return result
        }

        override fun toString(): String =
            "EmbedResponse(requestId='$requestId', modelLabel='$modelLabel', vector=[${vector.size} floats])"
    }

    @Serializable
    @SerialName("summarize_response")
    data class SummarizeResponse(
        override val requestId: String,
        val summary: String,
        val modelLabel: String,
    ) : LlmGatewayResponse()

    @Serializable
    @SerialName("extract_actions_response")
    data class ExtractActionsResponse(
        override val requestId: String,
        val proposals: List<ActionProposalJson>,
        val modelLabel: String,
    ) : LlmGatewayResponse()

    @Serializable
    @SerialName("classify_intent_response")
    data class ClassifyIntentResponse(
        override val requestId: String,
        val intent: String,
        val confidence: Float,
        val modelLabel: String,
    ) : LlmGatewayResponse()

    @Serializable
    @SerialName("generate_day_header_response")
    data class GenerateDayHeaderResponse(
        override val requestId: String,
        val header: String,
        val modelLabel: String,
    ) : LlmGatewayResponse()

    @Serializable
    @SerialName("scan_sensitivity_response")
    data class ScanSensitivityResponse(
        override val requestId: String,
        val tags: List<String>,
        val modelLabel: String,
    ) : LlmGatewayResponse()

    /**
     * Day-1 `code` enumeration (open enum at the data-model level):
     * `NETWORK_UNAVAILABLE`, `GATEWAY_5XX`, `PROVIDER_5XX`, `TIMEOUT`,
     * `MALFORMED_RESPONSE`, `UNAUTHORIZED`, `INTERNAL`.
     * `CloudLlmProvider` translates these to either `null` (embed) or
     * `IOException` (all other methods) per data-model §1.3.
     */
    @Serializable
    @SerialName("error")
    data class Error(
        override val requestId: String,
        val code: String,
        val message: String,
    ) : LlmGatewayResponse()
}
