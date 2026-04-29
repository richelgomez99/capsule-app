package com.capsule.app.net

import com.capsule.app.BuildConfig
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
 *  - Gateway URL is sourced from `BuildConfig.CLOUD_GATEWAY_URL`
 *    (Spec 014 T014-017 / FR-014-016). Day-1 placeholder fallback lives
 *    in `app/build.gradle.kts` only — never inlined here.
 *
 * Retry-once direct-provider fallback (FR-013-008) and bearer-token
 * graceful-null handling (FR-013-009) are layered on by T013-010 and
 * T013-011 respectively.
 */
class LlmGatewayClient(
    private val client: OkHttpClient = SafeOkHttpClient.build(),
    private val gatewayUrl: String = DEFAULT_GATEWAY_URL,
    /**
     * FR-013-009 — graceful-null bearer-token provider. Day-1 stub
     * returns `null` because `AuthSessionStore` does not yet exist; when
     * it lands, this becomes `{ AuthSessionStore.getCurrentToken() }`.
     * A `null` or blank result MUST suppress the `Authorization` header
     * entirely (no `"Authorization: Bearer "` request gets sent).
     */
    private val tokenProvider: () -> String? = { null },
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
            val gatewayResult = postOnce(perCallClient, gatewayUrl, nestedBody, request)
            if (!gatewayResult.shouldRetryDirect()) return@withContext gatewayResult.response
            // FR-013-008 — retry-once direct-provider fallback per ADR-003.
            // Day-1 placeholder URLs; no real provider keys ship in the
            // binary, so this path will surface UNAUTHORIZED in practice.
            // Wired now so Block 5+ specs do not have to add it later.
            val directResult = postOnce(
                perCallClient,
                directProviderUrlFor(request),
                nestedBody,
                request,
            )
            if (directResult.response is LlmGatewayResponse.Error) {
                LlmGatewayResponse.Error(
                    requestId = request.requestId,
                    code = "PROVIDER_5XX",
                    message = "gateway+direct retry exhausted: ${directResult.response.message}",
                )
            } else {
                directResult.response
            }
        }

    /** One POST attempt against [url]; surfaces an [LlmGatewayResponse] never a throw. */
    private fun postOnce(
        client: OkHttpClient,
        url: String,
        body: String,
        original: LlmGatewayRequest,
    ): PostOutcome {
        val builder = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json; charset=utf-8")
            .header("X-Orbit-Request-Id", original.requestId)
            .header("X-Orbit-Model-Hint", modelFor(original))
            .post(body.toRequestBody(JSON_MEDIA_TYPE))
        // FR-013-009 — graceful-null bearer token. Suppress Authorization
        // header when provider returns null/blank; never throw NPE on the
        // no-auth path.
        val token = runCatching { tokenProvider() }.getOrNull()
        if (!token.isNullOrBlank()) {
            builder.header("Authorization", "Bearer $token")
        }
        val httpRequest = builder.build()
        return try {
            client.newCall(httpRequest).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.code in 500..599) {
                    PostOutcome(
                        response = LlmGatewayResponse.Error(
                            original.requestId,
                            "GATEWAY_5XX",
                            "HTTP ${response.code}",
                        ),
                        retryable = true,
                    )
                } else if (!response.isSuccessful) {
                    val code = if (response.code == 401 || response.code == 403) {
                        "UNAUTHORIZED"
                    } else {
                        "INTERNAL"
                    }
                    PostOutcome(
                        LlmGatewayResponse.Error(original.requestId, code, "HTTP ${response.code}"),
                        retryable = false,
                    )
                } else {
                    val parsed = unwrapHttpEnvelope(raw, original.requestId)
                    val retryable = parsed is LlmGatewayResponse.Error &&
                        (parsed.code == "GATEWAY_5XX" || parsed.code == "INTERNAL")
                    PostOutcome(parsed, retryable)
                }
            }
        } catch (e: SocketTimeoutException) {
            PostOutcome(
                LlmGatewayResponse.Error(original.requestId, "TIMEOUT", e.message ?: "timeout"),
                retryable = false,
            )
        } catch (e: UnknownHostException) {
            PostOutcome(
                LlmGatewayResponse.Error(original.requestId, "NETWORK_UNAVAILABLE", e.message ?: "dns"),
                retryable = false,
            )
        } catch (e: IOException) {
            PostOutcome(
                LlmGatewayResponse.Error(original.requestId, "NETWORK_UNAVAILABLE", e.message ?: "io"),
                retryable = false,
            )
        }
    }

    private data class PostOutcome(
        val response: LlmGatewayResponse,
        val retryable: Boolean,
    ) {
        fun shouldRetryDirect(): Boolean = retryable
    }

    private fun directProviderUrlFor(request: LlmGatewayRequest): String = when (request) {
        is LlmGatewayRequest.Embed -> DIRECT_OPENAI_EMBEDDINGS_URL
        else -> DIRECT_ANTHROPIC_MESSAGES_URL
    }

    /**
     * Wrap flat `{type, requestId, …fields}` into HTTP envelope
     * `{type, requestId, payload: {…fields}}` per envelope contract §3.4.
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
        // Sourced from BuildConfig (spec 014 T014-017). The Day-1 placeholder
        // string lives only in app/build.gradle.kts as a fallback default.
        @JvmField
        val DEFAULT_GATEWAY_URL: String = BuildConfig.CLOUD_GATEWAY_URL

        // Day-1 placeholder direct-provider URLs (FR-013-008 / ADR-003).
        // No real keys ship in the binary; the fallback path exists so
        // Phase 11 Block 5+ does not have to retrofit it.
        const val DIRECT_OPENAI_EMBEDDINGS_URL: String = "https://api.openai.com/v1/embeddings"
        const val DIRECT_ANTHROPIC_MESSAGES_URL: String = "https://api.anthropic.com/v1/messages"

        const val MODEL_EMBED: String = "openai/text-embedding-3-small"
        const val MODEL_SONNET: String = "anthropic/claude-sonnet-4-6"
        const val MODEL_HAIKU: String = "anthropic/claude-haiku-4-5"

        const val DEFAULT_TIMEOUT_MS: Long = 30_000L
        const val SUMMARIZE_TIMEOUT_MS: Long = 60_000L

        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
