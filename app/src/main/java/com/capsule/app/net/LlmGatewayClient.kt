package com.capsule.app.net

import com.capsule.app.ai.gateway.LlmGatewayRequest
import com.capsule.app.ai.gateway.LlmGatewayResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * Spec 013 (FR-013-006/007/010) — `:net`-process AI Gateway HTTP client.
 *
 * Responsibilities:
 *  - Pick the destination model based on [LlmGatewayRequest] subtype
 *    (FR-013-006). Embed → text-embedding-3-small, summarise/extract/
 *    day-header → claude-sonnet-4-6, classify-intent / scan-sensitivity →
 *    claude-haiku-4-5.
 *  - Wrap the FLAT sealed-class JSON `{type, requestId, …fields}` into
 *    the NESTED HTTP envelope `{type, requestId, payload: {…fields}}`
 *    before POST (per envelope contract §3.4 / FR-013-007). Unwrap the
 *    response back into flat sealed-class JSON.
 *  - Apply 30s default timeout, 60s for `Summarize` (FR-013-010).
 *  - Day-1 placeholder URL `https://gateway.example.invalid/llm`
 *    (RFC-2606 reserved). Real URL lands when the Edge Function deploys.
 *
 * Retry-once direct-provider fallback (FR-013-008) and bearer-token
 * graceful-null handling (FR-013-009) are layered on by T013-010 and
 * T013-011 respectively.
 */
class LlmGatewayClient(
    private val client: OkHttpClient = SafeOkHttpClient.build(),
    private val gatewayUrl: String = DEFAULT_GATEWAY_URL,
) {

    private val jsonCodec: Json = Json {
        classDiscriminator = "type"
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /**
     * Single suspending entry point. Always returns a [LlmGatewayResponse]
     * — never throws across the AIDL boundary. Network / timeout / parse
     * failures collapse to [LlmGatewayResponse.Error].
     */
    suspend fun call(request: LlmGatewayRequest): LlmGatewayResponse =
        withContext(Dispatchers.IO) {
            val timeoutMs = timeoutFor(request)
            val perCallClient = client.newBuilder()
                .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .build()
            val nestedBody = wrapToHttpEnvelope(request)
            val httpRequest = Request.Builder()
                .url(gatewayUrl)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("X-Orbit-Request-Id", request.requestId)
                .header("X-Orbit-Model-Hint", modelFor(request))
                .post(nestedBody.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            executeOrError(perCallClient, httpRequest, request)
        }

    private fun executeOrError(
        client: OkHttpClient,
        httpRequest: Request,
        original: LlmGatewayRequest,
    ): LlmGatewayResponse = try {
        client.newCall(httpRequest).execute().use { response ->
            val raw = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                LlmGatewayResponse.Error(
                    requestId = original.requestId,
                    code = if (response.code in 500..599) "GATEWAY_5XX" else "INTERNAL",
                    message = "HTTP ${response.code}",
                )
            } else {
                unwrapHttpEnvelope(raw, original.requestId)
            }
        }
    } catch (e: SocketTimeoutException) {
        LlmGatewayResponse.Error(original.requestId, "TIMEOUT", e.message ?: "timeout")
    } catch (e: UnknownHostException) {
        LlmGatewayResponse.Error(original.requestId, "NETWORK_UNAVAILABLE", e.message ?: "dns")
    } catch (e: IOException) {
        LlmGatewayResponse.Error(original.requestId, "NETWORK_UNAVAILABLE", e.message ?: "io")
    }

    /**
     * Wrap flat `{type, requestId, …fields}` into HTTP envelope
     * `{type, requestId, payload: {…fields}}` per envelope contract §3.4.
     * The `payload` strips the discriminator + requestId from the flat
     * form and keeps the remaining fields verbatim.
     */
    private fun wrapToHttpEnvelope(request: LlmGatewayRequest): String {
        val flat = jsonCodec.encodeToJsonElement(LlmGatewayRequest.serializer(), request).jsonObject
        val payload = JsonObject(flat.filterKeys { it != "type" && it != "requestId" })
        val envelope = buildJsonObject {
            put("type", flat["type"]!!)
            put("requestId", request.requestId)
            put("payload", payload)
        }
        return jsonCodec.encodeToString(JsonObject.serializer(), envelope)
    }

    /**
     * Unwrap HTTP envelope `{type, requestId, ok, data | error}` into
     * the corresponding flat sealed-class JSON, then decode to
     * [LlmGatewayResponse]. Malformed → [LlmGatewayResponse.Error].
     */
    private fun unwrapHttpEnvelope(raw: String, requestId: String): LlmGatewayResponse = try {
        val outer = jsonCodec.parseToJsonElement(raw).jsonObject
        val typeValue = outer["type"]
            ?: return LlmGatewayResponse.Error(requestId, "MALFORMED_RESPONSE", "missing type")
        val ok = outer["ok"]?.toString()?.equals("true", ignoreCase = true) ?: false
        if (!ok || outer.containsKey("error")) {
            val err = outer["error"]?.jsonObject
            val code = err?.get("code")?.toString()?.trim('"') ?: "INTERNAL"
            val msg = err?.get("message")?.toString()?.trim('"') ?: "unknown"
            LlmGatewayResponse.Error(requestId, code, msg)
        } else {
            val data = outer["data"]?.jsonObject ?: JsonObject(emptyMap())
            val flat = buildJsonObject {
                put("type", typeValue)
                put("requestId", requestId)
                data.forEach { (k, v) -> put(k, v) }
            }
            jsonCodec.decodeFromJsonElement(LlmGatewayResponse.serializer(), flat)
        }
    } catch (e: Throwable) {
        LlmGatewayResponse.Error(requestId, "MALFORMED_RESPONSE", e.message ?: "parse")
    }

    private fun timeoutFor(request: LlmGatewayRequest): Long = when (request) {
        is LlmGatewayRequest.Summarize -> SUMMARIZE_TIMEOUT_MS
        else -> DEFAULT_TIMEOUT_MS
    }

    private fun modelFor(request: LlmGatewayRequest): String = when (request) {
        is LlmGatewayRequest.Embed -> MODEL_EMBED
        is LlmGatewayRequest.Summarize,
        is LlmGatewayRequest.ExtractActions,
        is LlmGatewayRequest.GenerateDayHeader -> MODEL_SONNET
        is LlmGatewayRequest.ClassifyIntent,
        is LlmGatewayRequest.ScanSensitivity -> MODEL_HAIKU
    }

    companion object {
        const val DEFAULT_GATEWAY_URL: String = "https://gateway.example.invalid/llm"

        const val MODEL_EMBED: String = "openai/text-embedding-3-small"
        const val MODEL_SONNET: String = "anthropic/claude-sonnet-4-6"
        const val MODEL_HAIKU: String = "anthropic/claude-haiku-4-5"

        const val DEFAULT_TIMEOUT_MS: Long = 30_000L
        const val SUMMARIZE_TIMEOUT_MS: Long = 60_000L

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
