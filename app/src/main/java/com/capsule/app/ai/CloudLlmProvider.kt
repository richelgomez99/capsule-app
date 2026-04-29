package com.capsule.app.ai

import com.capsule.app.ai.gateway.ActionProposalJson
import com.capsule.app.ai.gateway.AppFunctionSummaryJson
import com.capsule.app.ai.gateway.LlmGatewayRequest
import com.capsule.app.ai.gateway.LlmGatewayResponse
import com.capsule.app.ai.gateway.StateSnapshotJson
import com.capsule.app.ai.model.ActionCandidate
import com.capsule.app.ai.model.ActionExtractionResult
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.SensitivityScope
import com.capsule.app.net.ipc.INetworkGateway
import com.capsule.app.net.ipc.LlmGatewayRequestParcel
import com.capsule.app.net.ipc.LlmGatewayResponseParcel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.json.JSONArray
import java.io.IOException
import java.util.Locale
import java.util.UUID

/**
 * Spec 013 (FR-013-012, FR-013-013) — cloud-mode [LlmProvider] that
 * routes every request through the `:net`-process [INetworkGateway]
 * AIDL surface. No direct HTTP / OkHttp / Retrofit imports — Principle
 * VI (single network egress).
 *
 * Each method:
 *  1. generates a UUIDv4 `requestId`,
 *  2. builds the matching [LlmGatewayRequest] subtype,
 *  3. JSON-encodes into [LlmGatewayRequestParcel] via the shared
 *     [GATEWAY_JSON] codec (sealed-class discriminator `"type"`),
 *  4. calls [INetworkGateway.callLlmGateway] (blocking AIDL — wrapped
 *     in [Dispatchers.IO] to keep the suspend contract honest),
 *  5. decodes the [LlmGatewayResponseParcel] into [LlmGatewayResponse],
 *  6. returns the typed result on success, or applies the asymmetric
 *     error contract per data-model §1.3 on [LlmGatewayResponse.Error]:
 *       - [embed] → `null` (preserves Spec 002 graceful-degrade),
 *       - every other method → throws [IOException].
 *
 * Provenance is stamped as [LlmProvenance.OrbitManaged] using the
 * `modelLabel` returned by the gateway.
 */
class CloudLlmProvider(
    private val gateway: INetworkGateway,
) : LlmProvider {

    override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification {
        val requestId = newRequestId()
        val response = roundTrip(
            LlmGatewayRequest.ClassifyIntent(
                requestId = requestId,
                text = text,
                appCategory = appCategory,
            ),
        )
        if (response is LlmGatewayResponse.Error) throw response.toIOException()
        val ok = response as LlmGatewayResponse.ClassifyIntentResponse
        return IntentClassification(
            intent = decodeIntent(ok.intent),
            confidence = ok.confidence,
            provenance = LlmProvenance.OrbitManaged(ok.modelLabel),
        )
    }

    override suspend fun summarize(text: String, maxTokens: Int): SummaryResult {
        val requestId = newRequestId()
        val response = roundTrip(
            LlmGatewayRequest.Summarize(
                requestId = requestId,
                text = text,
                maxTokens = maxTokens,
            ),
        )
        if (response is LlmGatewayResponse.Error) throw response.toIOException()
        val ok = response as LlmGatewayResponse.SummarizeResponse
        return SummaryResult(
            text = ok.summary,
            generationLocale = Locale.getDefault().toLanguageTag(),
            provenance = LlmProvenance.OrbitManaged(ok.modelLabel),
        )
    }

    override suspend fun scanSensitivity(text: String): SensitivityResult {
        val requestId = newRequestId()
        val response = roundTrip(
            LlmGatewayRequest.ScanSensitivity(
                requestId = requestId,
                text = text,
            ),
        )
        if (response is LlmGatewayResponse.Error) throw response.toIOException()
        val ok = response as LlmGatewayResponse.ScanSensitivityResponse
        val flagsJson = JSONArray().apply { ok.tags.forEach { put(it) } }.toString()
        return SensitivityResult(
            flagsJson = flagsJson,
            provenance = LlmProvenance.OrbitManaged(ok.modelLabel),
        )
    }

    override suspend fun generateDayHeader(
        dayIsoDate: String,
        envelopeSummaries: List<String>,
    ): DayHeaderResult {
        val requestId = newRequestId()
        val response = roundTrip(
            LlmGatewayRequest.GenerateDayHeader(
                requestId = requestId,
                dayIsoDate = dayIsoDate,
                envelopeSummaries = envelopeSummaries,
            ),
        )
        if (response is LlmGatewayResponse.Error) throw response.toIOException()
        val ok = response as LlmGatewayResponse.GenerateDayHeaderResponse
        return DayHeaderResult(
            text = ok.header,
            generationLocale = Locale.getDefault().toLanguageTag(),
            provenance = LlmProvenance.OrbitManaged(ok.modelLabel),
        )
    }

    override suspend fun extractActions(
        text: String,
        contentType: String,
        state: StateSnapshot,
        registeredFunctions: List<AppFunctionSummary>,
        maxCandidates: Int,
    ): ActionExtractionResult {
        val requestId = newRequestId()
        val response = roundTrip(
            LlmGatewayRequest.ExtractActions(
                requestId = requestId,
                text = text,
                contentType = contentType,
                state = state.toJsonMirror(),
                registeredFunctions = registeredFunctions.map { it.toJsonMirror() },
                maxCandidates = maxCandidates,
            ),
        )
        if (response is LlmGatewayResponse.Error) throw response.toIOException()
        val ok = response as LlmGatewayResponse.ExtractActionsResponse
        return ActionExtractionResult(
            provenance = LlmProvenance.OrbitManaged(ok.modelLabel),
            candidates = ok.proposals.map { it.toCandidate() },
        )
    }

    override suspend fun embed(text: String): EmbeddingResult? {
        if (text.isBlank()) return null
        val requestId = newRequestId()
        val response = try {
            roundTrip(LlmGatewayRequest.Embed(requestId = requestId, text = text))
        } catch (_: Throwable) {
            // Spec 002 graceful-degrade: embed must never throw.
            return null
        }
        if (response is LlmGatewayResponse.Error) return null
        val ok = response as LlmGatewayResponse.EmbedResponse
        return EmbeddingResult(
            vector = ok.vector,
            modelLabel = ok.modelLabel,
            dimensionality = ok.vector.size,
        )
    }

    private suspend fun roundTrip(request: LlmGatewayRequest): LlmGatewayResponse =
        withContext(Dispatchers.IO) {
            val payload = GATEWAY_JSON.encodeToString(LlmGatewayRequest.serializer(), request)
            val parcel = gateway.callLlmGateway(LlmGatewayRequestParcel(payload))
            decodeResponse(parcel, request.requestId)
        }

    private fun decodeResponse(
        parcel: LlmGatewayResponseParcel,
        requestId: String,
    ): LlmGatewayResponse = try {
        GATEWAY_JSON.decodeFromString(LlmGatewayResponse.serializer(), parcel.payloadJson)
    } catch (t: Throwable) {
        LlmGatewayResponse.Error(
            requestId = requestId,
            code = "MALFORMED_RESPONSE",
            message = t.message ?: "response decode failed",
        )
    }

    private fun newRequestId(): String = UUID.randomUUID().toString()

    private fun StateSnapshot.toJsonMirror(): StateSnapshotJson = StateSnapshotJson(
        appCategory = appCategory.name,
        activityState = activityState.name,
        tzId = tzId,
        hourLocal = hourLocal,
        dayOfWeekLocal = dayOfWeekLocal,
    )

    private fun AppFunctionSummary.toJsonMirror(): AppFunctionSummaryJson =
        AppFunctionSummaryJson(
            functionId = functionId,
            schemaVersion = schemaVersion,
            displayName = displayName,
            description = description,
            argsSchemaJson = argsSchemaJson,
            sensitivityScope = sensitivityScope.name,
        )

    private fun ActionProposalJson.toCandidate(): ActionCandidate = ActionCandidate(
        functionId = functionId,
        schemaVersion = schemaVersion,
        argsJson = argsJson,
        previewTitle = previewTitle,
        previewSubtitle = previewSubtitle,
        confidence = confidence,
        sensitivityScope = decodeSensitivityScope(sensitivityScope),
    )

    private fun decodeIntent(raw: String): Intent =
        runCatching { Intent.valueOf(raw) }.getOrDefault(Intent.AMBIGUOUS)

    private fun decodeSensitivityScope(raw: String): SensitivityScope =
        runCatching { SensitivityScope.valueOf(raw) }.getOrDefault(SensitivityScope.PERSONAL)

    private fun LlmGatewayResponse.Error.toIOException(): IOException =
        IOException("$code: $message")

    companion object {
        internal val GATEWAY_JSON: Json = Json {
            classDiscriminator = "type"
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}
