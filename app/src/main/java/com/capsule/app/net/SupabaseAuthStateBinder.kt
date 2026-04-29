package com.capsule.app.net

/**
 * Spec 014 T014-018 — production [AuthStateBinder] implementation.
 *
 * **Deviation note (documented per implement-runbook):** the Supabase
 * Kotlin SDK (`io.github.jan-tennert.supabase:gotrue-kt`) is not yet in
 * the project's dependency graph (spec 013 shipped only the Postgres
 * provisioning + `LlmGatewayClient`; no Auth SDK landed). To keep `:net`
 * decoupled from the eventual SDK choice (FR-014-017), this binder
 * accepts a `sessionAccessTokenProvider` lambda. The adapter that
 * actually owns the Supabase `SupabaseClient` instance is responsible
 * for supplying that lambda, e.g.:
 *
 * ```
 * SupabaseAuthStateBinder { supabase.auth.currentSessionOrNull()?.accessToken }
 * ```
 *
 * The Supabase Kotlin SDK is responsible for refresh; this binder simply
 * surfaces whatever the SDK returns at call-time. When the SDK lands, no
 * change to `:net` is required — only the wiring at the call site.
 *
 * Returns `null` when:
 *  - the lambda throws (treated as "no usable session"),
 *  - the lambda returns `null` (no active session), or
 *  - the lambda returns blank (defensive normalisation).
 */
class SupabaseAuthStateBinder(
    private val sessionAccessTokenProvider: () -> String?,
) : AuthStateBinder {

    override suspend fun currentJwt(): String? =
        runCatching { sessionAccessTokenProvider() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
}
