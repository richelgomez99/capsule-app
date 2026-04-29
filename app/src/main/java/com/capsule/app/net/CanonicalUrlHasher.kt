package com.capsule.app.net

import java.net.URI
import java.net.URISyntaxException
import java.security.MessageDigest
import java.util.Locale

/**
 * T066a — pure URL canonicalizer + SHA-256 hasher used for continuation dedupe.
 *
 * `hash(rawUrl)` returns `sha256(canonicalize(rawUrl))` as lowercase hex.
 *
 * Canonicalization rules (see tasks.md T066a / spec.md Clarification Q2):
 * - scheme/host lowercased
 * - fragment stripped
 * - query params named `utm_*`, `fbclid`, `gclid` stripped
 * - remaining query params sorted lexicographically by name, preserving value
 * - trailing `/` on the path stripped (unless path is empty or `/`)
 * - default ports (80/443) stripped
 *
 * The function is intentionally tolerant — malformed URLs still produce a stable
 * hash of the raw input, so the pure-function contract "same input ⇒ same output"
 * holds even when validation has already rejected the URL.
 */
object CanonicalUrlHasher {

    private val TRACKING_PARAM_EXACT: Set<String> = setOf("fbclid", "gclid")
    private const val TRACKING_PARAM_PREFIX: String = "utm_"

    fun hash(rawUrl: String): String {
        val canonical = canonicalize(rawUrl)
        return sha256Hex(canonical)
    }

    internal fun canonicalize(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        val uri: URI = try {
            URI(trimmed)
        } catch (_: URISyntaxException) {
            return trimmed.lowercase(Locale.ROOT)
        }

        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return trimmed.lowercase(Locale.ROOT)
        val host = uri.host?.lowercase(Locale.ROOT) ?: return trimmed.lowercase(Locale.ROOT)

        val port = uri.port
        val portPart = when {
            port == -1 -> ""
            scheme == "https" && port == 443 -> ""
            scheme == "http" && port == 80 -> ""
            else -> ":$port"
        }

        val rawPath = uri.rawPath.orEmpty()
        val path = when {
            rawPath.isEmpty() -> ""
            rawPath == "/" -> "/"
            rawPath.endsWith('/') -> rawPath.trimEnd('/')
            else -> rawPath
        }

        val queryPart = canonicalQuery(uri.rawQuery)

        return buildString {
            append(scheme)
            append("://")
            append(host)
            append(portPart)
            append(path)
            if (queryPart.isNotEmpty()) {
                append('?')
                append(queryPart)
            }
        }
    }

    private fun canonicalQuery(rawQuery: String?): String {
        if (rawQuery.isNullOrEmpty()) return ""
        val pairs = rawQuery.split('&')
            .mapNotNull { piece ->
                if (piece.isEmpty()) return@mapNotNull null
                val idx = piece.indexOf('=')
                val name = if (idx < 0) piece else piece.substring(0, idx)
                val value = if (idx < 0) null else piece.substring(idx + 1)
                val lowerName = name.lowercase(Locale.ROOT)
                if (lowerName.startsWith(TRACKING_PARAM_PREFIX)) return@mapNotNull null
                if (lowerName in TRACKING_PARAM_EXACT) return@mapNotNull null
                name to value
            }
            .sortedWith(compareBy({ it.first }, { it.second ?: "" }))

        return pairs.joinToString("&") { (n, v) ->
            if (v == null) n else "$n=$v"
        }
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
