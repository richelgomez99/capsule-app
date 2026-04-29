package com.capsule.app.net

/**
 * Spec 014 T014-018 (FR-014-017) — `:net`-scoped seam that exposes the
 * current Supabase access token (a JWT) without leaking the Supabase SDK
 * surface into `:net`. `LlmGatewayClient` calls [currentJwt] before each
 * request:
 *
 *  - non-null, non-blank → stamped as `Authorization: Bearer <jwt>`
 *  - null/blank          → request short-circuits to
 *                          `LlmGatewayResponse.Error(UNAUTHORIZED)` and
 *                          never crosses the network (spec 014 §FR-014-017,
 *                          T014-019).
 *
 * The implementation is responsible for handling Supabase session refresh
 * (the SDK does this internally); callers MUST treat each invocation as
 * "give me a fresh, non-expired token or null".
 */
interface AuthStateBinder {
    suspend fun currentJwt(): String?
}

/**
 * Default no-session binder used when no Supabase session has been wired
 * yet (e.g. very early app boot, or unit tests that do not exercise auth).
 * Always returns `null`, which causes `LlmGatewayClient` to fail-fast with
 * `Error(UNAUTHORIZED)` per FR-014-017.
 */
object NoSessionAuthStateBinder : AuthStateBinder {
    override suspend fun currentJwt(): String? = null
}
