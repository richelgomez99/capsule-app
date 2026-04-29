package com.capsule.app.net

import okhttp3.CookieJar
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * T060 — factory for the single OkHttpClient used by [NetworkGatewayImpl].
 *
 * Hard-coded to contracts/network-gateway-contract.md §4 request policy:
 * - [CookieJar.NO_COOKIES]
 * - no OkHttp cache
 * - redirects disabled at the OkHttp layer (NetworkGatewayImpl follows manually
 *   to enforce "each hop must be https" + redirect-loop detection)
 * - fixed `User-Agent: Orbit/1.0 (Android; local-first reader)`
 * - `Referer` header stripped as belt-and-braces (OkHttp does not set it)
 * - 10s connect/read/write timeouts; [NetworkGatewayImpl] also applies an
 *   overall call budget (capped at 15s per contract §4).
 * - Dispatcher: max 4 concurrent calls / 2 per host (§6).
 */
object SafeOkHttpClient {

    const val USER_AGENT: String = "Orbit/1.0 (Android; local-first reader)"

    fun build(): OkHttpClient {
        val dispatcher = Dispatcher().apply {
            maxRequests = 4
            maxRequestsPerHost = 2
        }
        return OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .cookieJar(CookieJar.NO_COOKIES)
            .cache(null)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val stripped = chain.request().newBuilder()
                    .removeHeader("Referer")
                    .removeHeader("Cookie")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "en")
                    .build()
                chain.proceed(stripped)
            }
            .build()
    }
}
