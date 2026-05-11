package com.capsule.app.net

import com.capsule.app.net.ipc.FetchResultParcel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * Provider-specific metadata fetches for sites whose normal HTML responses are
 * hostile to local reader clients. The generic fetch path remains the default;
 * this class only claims URLs for known providers with stable public metadata
 * endpoints.
 */
class ProviderMetadataResolver(
    private val client: OkHttpClient,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val youtubeOEmbedEndpoint: String = YOUTUBE_OEMBED_ENDPOINT,
) {

    fun resolve(url: String): FetchResultParcel? {
        if (!isYouTubeUrl(url)) return null
        return resolveYouTube(url)
    }

    private fun resolveYouTube(url: String): FetchResultParcel {
        val now = clock()
        val canonicalHost = runCatching { URI(url).host?.lowercase() }.getOrNull()
        val endpoint = try {
            youtubeOEmbedEndpoint.toHttpUrl().newBuilder()
                .addQueryParameter("url", url)
                .addQueryParameter("format", "json")
                .build()
        } catch (_: IllegalArgumentException) {
            return failure(now, canonicalHost, "invalid_url", "bad YouTube oEmbed endpoint")
        }
        val request = Request.Builder()
            .url(endpoint)
            .get()
            .header("Accept", "application/json")
            .build()

        val response = try {
            client.newCall(request).execute()
        } catch (e: UnknownHostException) {
            return failure(clock(), canonicalHost, "dns_failure", e.message ?: "unknown host")
        } catch (e: SSLHandshakeException) {
            return failure(clock(), canonicalHost, "tls_failure", e.message ?: "tls handshake")
        } catch (e: SSLException) {
            return failure(clock(), canonicalHost, "tls_failure", e.message ?: "tls error")
        } catch (e: IOException) {
            return failure(clock(), canonicalHost, "unknown", e.message ?: "io error")
        }

        response.use { res ->
            if (res.code !in 200..299) {
                return failure(clock(), canonicalHost, "http_error", "youtube_oembed=${res.code}")
            }
            val body = res.body?.string().orEmpty()
            val metadata = runCatching { parseYouTubeMetadata(body) }.getOrNull()
                ?: return failure(clock(), canonicalHost, "unknown", "malformed YouTube oEmbed response")
            val title = metadata.title.takeIf { it.isNotBlank() }
                ?: return failure(clock(), canonicalHost, "unknown", "YouTube oEmbed missing title")
            val readable = buildReadableText(
                title = title,
                authorName = metadata.authorName,
                providerName = metadata.providerName,
            )
            return FetchResultParcel(
                ok = true,
                finalUrl = url,
                title = title,
                canonicalHost = canonicalHost ?: "youtube.com",
                readableHtml = readable,
                errorKind = null,
                errorMessage = null,
                fetchedAtMillis = clock(),
            )
        }
    }

    private fun parseYouTubeMetadata(body: String): YouTubeMetadata {
        val obj = JSON.parseToJsonElement(body).jsonObject
        return YouTubeMetadata(
            title = obj["title"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
            authorName = obj["author_name"]?.jsonPrimitive?.contentOrNull?.trim(),
            providerName = obj["provider_name"]?.jsonPrimitive?.contentOrNull?.trim(),
        )
    }

    private fun buildReadableText(
        title: String,
        authorName: String?,
        providerName: String?,
    ): String = listOfNotNull(
        title,
        authorName?.takeIf { it.isNotBlank() }?.let { "YouTube video by $it." },
        providerName?.takeIf { it.isNotBlank() }?.let { "Provider: $it." },
    ).joinToString(separator = "\n")

    private fun failure(
        at: Long,
        canonicalHost: String?,
        errorKind: String,
        message: String,
    ): FetchResultParcel = FetchResultParcel(
        ok = false,
        finalUrl = null,
        title = null,
        canonicalHost = canonicalHost,
        readableHtml = null,
        errorKind = errorKind,
        errorMessage = message,
        fetchedAtMillis = at,
    )

    private data class YouTubeMetadata(
        val title: String,
        val authorName: String?,
        val providerName: String?,
    )

    companion object {
        private const val YOUTUBE_OEMBED_ENDPOINT = "https://www.youtube.com/oembed"
        private val JSON = Json { ignoreUnknownKeys = true }

        internal fun isYouTubeUrl(url: String): Boolean {
            val host = runCatching { URI(url).host?.lowercase()?.trimEnd('.') }.getOrNull()
                ?: return false
            return host == "youtu.be" ||
                host == "youtube.com" ||
                host.endsWith(".youtube.com") ||
                host == "youtube-nocookie.com" ||
                host.endsWith(".youtube-nocookie.com")
        }
    }
}