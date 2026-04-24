package com.capsule.app.net

import android.os.Binder
import android.os.Process
import com.capsule.app.net.ipc.FetchResultParcel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/**
 * T063 — the concrete `fetchPublicUrl` implementation. Pulled out of
 * [NetworkGatewayService] so we can unit-test it without binding.
 *
 * Responsibilities (contracts/network-gateway-contract.md §4):
 *  - UID gate (`Binder.getCallingUid() == Process.myUid()`)
 *  - URL validation via [UrlValidator]
 *  - manual redirect following (max 5 hops, each must be https)
 *  - 2 MB body cap (hard fail with `too_large`)
 *  - domain cooldown on 5xx (§6)
 *  - Readability extraction via [ReadabilityExtractor]
 *  - audit callback via [auditSink] (no AIDL for IAuditLog yet — see
 *    contract §5; Continuation Engine supplies envelope id itself in v1).
 *
 * The AIDL binder (`INetworkGateway.Stub`) is wired in [NetworkGatewayService].
 */
class NetworkGatewayImpl(
    private val client: OkHttpClient,
    private val validator: UrlValidator = UrlValidator(requireHttps = true),
    private val extractor: ReadabilityExtractor = ReadabilityExtractor(),
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val callerUidProvider: () -> Int = { Binder.getCallingUid() },
    private val myUidProvider: () -> Int = { Process.myUid() },
    private val auditSink: AuditSink? = null,
    private val disabled: () -> Boolean = { false },
) {

    /** Minimal audit seam; a richer [com.capsule.app.audit] wiring lands via merge zone. */
    fun interface AuditSink {
        fun onFetch(ok: Boolean, canonicalHost: String?, errorKind: String?)
    }

    private val hostCooldown = HashMap<String, Long>()
    private val cooldownLock = Any()

    fun fetchPublicUrl(url: String, timeoutMs: Long): FetchResultParcel {
        val now = clock()

        // UID gate
        val caller = callerUidProvider()
        if (caller != myUidProvider()) {
            return failure(now, "unauthorized", "caller uid $caller is not allowed")
        }

        if (disabled()) {
            return failure(now, "disabled", "continuations disabled by user")
        }

        // Precondition: validate URL
        val validation = validator.validate(url)
        val (initialUri, _) = when (validation) {
            is UrlValidator.Validation.Valid -> validation.uri to validation.host
            is UrlValidator.Validation.Invalid ->
                return failure(now, validation.errorKind, validation.reason)
        }

        // Clamp timeout (§4 "≤ 15_000 clamped")
        val clampedTimeout = timeoutMs.coerceAtLeast(1_000L).coerceAtMost(15_000L)

        // Domain cooldown check (§6)
        val host = initialUri.host.lowercase()
        synchronized(cooldownLock) {
            val until = hostCooldown[host]
            if (until != null) {
                if (now < until) {
                    return finish(
                        host,
                        FetchResultParcel(
                            ok = false,
                            finalUrl = null,
                            title = null,
                            canonicalHost = host,
                            readableHtml = null,
                            errorKind = "http_error",
                            errorMessage = "host $host in cooldown",
                            fetchedAtMillis = now,
                        ),
                    )
                } else {
                    hostCooldown.remove(host)
                }
            }
        }

        val deadline = now + clampedTimeout

        // Manual redirect follow — up to 5 hops, each must be https.
        var currentUri: URI = initialUri
        var hops = 0
        while (true) {
            if (clock() > deadline) {
                return finish(host, failure(clock(), "timeout", "deadline exceeded before response"))
            }
            val request = Request.Builder()
                .url(currentUri.toString())
                .get()
                .build()

            val response: Response = try {
                client.newCall(request).execute()
            } catch (e: SocketTimeoutException) {
                return finish(host, failure(clock(), "timeout", e.message ?: "timeout"))
            } catch (e: UnknownHostException) {
                return finish(host, failure(clock(), "dns_failure", e.message ?: "unknown host"))
            } catch (e: SSLHandshakeException) {
                return finish(host, failure(clock(), "tls_failure", e.message ?: "tls handshake"))
            } catch (e: SSLException) {
                return finish(host, failure(clock(), "tls_failure", e.message ?: "tls error"))
            } catch (e: IOException) {
                return finish(host, failure(clock(), "unknown", e.message ?: "io error"))
            }

            val code = response.code
            if (code in 300..399) {
                val location = response.header("Location")
                response.close()
                if (location.isNullOrBlank()) {
                    return finish(host, failure(clock(), "http_error", "3xx without Location"))
                }
                hops += 1
                if (hops > MAX_REDIRECTS) {
                    return finish(host, failure(clock(), "redirect_loop", "more than $MAX_REDIRECTS hops"))
                }
                val nextUri = try {
                    currentUri.resolve(location)
                } catch (_: IllegalArgumentException) {
                    return finish(host, failure(clock(), "invalid_url", "bad redirect target"))
                }
                val nextValidation = validator.validate(nextUri.toString())
                when (nextValidation) {
                    is UrlValidator.Validation.Invalid -> {
                        // "Redirect to non-https ⇒ fail with not_https" — the validator's
                        // errorKind for non-https targets is already "not_https"; all other
                        // invalids collapse to their respective kinds.
                        return finish(host, failure(clock(), nextValidation.errorKind, nextValidation.reason))
                    }
                    is UrlValidator.Validation.Valid -> currentUri = nextValidation.uri
                }
                continue
            }

            // 2xx / 4xx / 5xx — terminal
            try {
                if (code in 500..599) {
                    synchronized(cooldownLock) {
                        hostCooldown[host] = clock() + HOST_COOLDOWN_MS
                    }
                    return finish(host, failure(clock(), "http_error", "5xx=$code"))
                }
                if (code !in 200..299) {
                    return finish(host, failure(clock(), "http_error", "$code"))
                }

                val body = response.body
                    ?: return finish(host, failure(clock(), "http_error", "empty body"))

                // Enforce 2 MB cap.
                val declared = body.contentLength()
                if (declared in (MAX_BODY_BYTES + 1L)..Long.MAX_VALUE) {
                    return finish(host, failure(clock(), "too_large", "content-length=$declared"))
                }

                val source = body.source()
                // `request(MAX_BODY_BYTES + 1)` reads up to that many bytes into the buffer
                // without consuming them; if the stream had more bytes available than the cap,
                // we reject rather than silently truncate (contract §4 says "Larger ⇒ too_large").
                source.request((MAX_BODY_BYTES + 1).toLong())
                val buffered = source.buffer
                if (buffered.size > MAX_BODY_BYTES) {
                    return finish(host, failure(clock(), "too_large", "body>$MAX_BODY_BYTES"))
                }
                val bytes = buffered.readByteArray()
                val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
                val rawHtml = String(bytes, charset)

                val finalUrlStr = currentUri.toString()
                val canonicalHost = currentUri.host.lowercase()
                val extracted = extractor.extract(finalUrlStr, rawHtml)

                return finish(
                    host,
                    FetchResultParcel(
                        ok = true,
                        finalUrl = finalUrlStr,
                        title = extracted.title,
                        canonicalHost = canonicalHost,
                        readableHtml = extracted.readableHtml,
                        errorKind = null,
                        errorMessage = null,
                        fetchedAtMillis = clock(),
                    ),
                )
            } finally {
                response.close()
            }
        }
    }

    private fun finish(host: String?, result: FetchResultParcel): FetchResultParcel {
        auditSink?.onFetch(result.ok, host ?: result.canonicalHost, result.errorKind)
        return result
    }

    private fun failure(at: Long, errorKind: String, message: String): FetchResultParcel =
        FetchResultParcel(
            ok = false,
            finalUrl = null,
            title = null,
            canonicalHost = null,
            readableHtml = null,
            errorKind = errorKind,
            errorMessage = message,
            fetchedAtMillis = at,
        )

    companion object {
        internal const val MAX_REDIRECTS: Int = 5
        internal const val MAX_BODY_BYTES: Int = 2 * 1024 * 1024
        internal const val HOST_COOLDOWN_MS: Long = 60_000L
    }
}
