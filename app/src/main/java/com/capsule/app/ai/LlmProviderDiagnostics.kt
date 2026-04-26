package com.capsule.app.ai

/**
 * Thrown by [NanoLlmProvider] when on-device Gemini Nano is unavailable.
 *
 * In production this would be raised by the AICore SDK on hardware/eligibility
 * issues. For development we expose a debug-only diagnostic seam in
 * [LlmProviderDiagnostics] that forces the same exception so downstream
 * negative paths (quickstart §6 N2) can be exercised without de-provisioning
 * the device.
 */
class NanoUnavailableException(
    message: String = "Gemini Nano (AICore) is unavailable",
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * Process-wide diagnostic flags for [LlmProvider] implementations.
 *
 * Production code MUST NOT set [forceNanoUnavailable]; the only writer is the
 * debug-build `DiagnosticsActivity` (see `app/src/debug/...`). The check is
 * intentionally cheap (single volatile read) so we don't gate it behind a
 * `BuildConfig.DEBUG` check at the read site — the security model relies on
 * no production code path *writing* the flag.
 *
 * spec/003 T097 — see quickstart §6 N2 (Force Nano UNAVAILABLE toggle).
 */
object LlmProviderDiagnostics {
    @Volatile
    @JvmStatic
    var forceNanoUnavailable: Boolean = false
}
