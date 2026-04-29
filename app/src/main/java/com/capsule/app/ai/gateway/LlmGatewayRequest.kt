package com.capsule.app.ai.gateway

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Spec 013 (FR-013-003) — provider-agnostic request envelope for the
 * `:net`-process AI Gateway client. Crosses AIDL as JSON-in-String
 * (see [com.capsule.app.net.ipc.LlmGatewayRequestParcel]).
 *
 * Wire format: kotlinx.serialization JSON, UTF-8.
 * Discriminator: `"type"` (matches [contracts/llm-gateway-envelope-contract.md]).
 *
 * `requestId` is a UUIDv4 string generated at the `CloudLlmProvider`
 * boundary, propagated end-to-end, and echoed in [LlmGatewayResponse]
 * for SC-009 latency attribution.
 */
@Serializable
sealed class LlmGatewayRequest {
    abstract val requestId: String

    @Serializable
    @SerialName("embed")
    data class Embed(
        override val requestId: String,
        val text: String,
    ) : LlmGatewayRequest()

    @Serializable
    @SerialName("summarize")
    data class Summarize(
        override val requestId: String,
        val text: String,
        val maxTokens: Int,
    ) : LlmGatewayRequest()

    @Serializable
    @SerialName("extract_actions")
    data class ExtractActions(
        override val requestId: String,
        val text: String,
        val contentType: String,
        val state: StateSnapshotJson,
        val registeredFunctions: List<AppFunctionSummaryJson>,
        val maxCandidates: Int = 3,
    ) : LlmGatewayRequest()

    @Serializable
    @SerialName("classify_intent")
    data class ClassifyIntent(
        override val requestId: String,
        val text: String,
        val appCategory: String,
    ) : LlmGatewayRequest()

    @Serializable
    @SerialName("generate_day_header")
    data class GenerateDayHeader(
        override val requestId: String,
        val dayIsoDate: String,
        val envelopeSummaries: List<String>,
    ) : LlmGatewayRequest()

    @Serializable
    @SerialName("scan_sensitivity")
    data class ScanSensitivity(
        override val requestId: String,
        val text: String,
    ) : LlmGatewayRequest()
}

// ---------------------------------------------------------------------------
// Day-1 inline DTO mirrors (T013-003). T013-005 moves these to
// `GatewayDtos.kt` as the dedicated `@Serializable` mirror file. The
// inline declarations exist solely so T013-003 compiles green; T013-005
// is a structural move with the same field shapes.
// ---------------------------------------------------------------------------

@Serializable
data class StateSnapshotJson(
    val appCategory: String,
    val activityState: String,
    val tzId: String,
    val hourLocal: Int,
    val dayOfWeekLocal: Int,
)

@Serializable
data class AppFunctionSummaryJson(
    val functionId: String,
    val schemaVersion: Int,
    val displayName: String,
    val description: String,
    val argsSchemaJson: String,
    val sensitivityScope: String,
)

@Serializable
data class ActionProposalJson(
    val functionId: String,
    val schemaVersion: Int,
    val argsJson: String,
    val previewTitle: String,
    val previewSubtitle: String? = null,
    val confidence: Float,
    val sensitivityScope: String,
)
