package com.capsule.app.net

import android.util.Log
import com.capsule.app.BuildConfig
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email

/**
 * Spec 014 T014-019b — debug-variant one-shot Supabase auth seed.
 *
 * On every `:net` process start, if a Supabase session is **not** already
 * present and `BuildConfig.DEBUG_SUPABASE_EMAIL` / `_PASSWORD` are populated
 * from `local.properties`, this performs a `signInWith(Email)`. The resulting
 * session is persisted by [EncryptedSessionManager] so subsequent process
 * starts skip the sign-in and rely on the SDK's auto-refresh.
 *
 * Logging contract (Constitution Principle XIV — bounded observation):
 *  - On success we log `session seeded for <domain>` (NEVER the local-part).
 *  - On failure we log `signin_failed code=<class>` (NEVER credential bytes).
 *
 * Release builds compile the no-op stub in `app/src/release/java/...`.
 */
internal suspend fun seedIfNeeded(supabase: SupabaseClient) {
    val email = BuildConfig.DEBUG_SUPABASE_EMAIL
    val password = BuildConfig.DEBUG_SUPABASE_PASSWORD
    if (email.isBlank() || password.isBlank()) {
        Log.w(
            TAG,
            "debug supabase creds not set in local.properties; skipping seed " +
                "(populate supabase.debug.email / supabase.debug.password)",
        )
        return
    }
    if (supabase.auth.currentSessionOrNull() != null) {
        return
    }
    val domain = email.substringAfter('@', missingDelimiterValue = "<unknown>")
    try {
        supabase.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        Log.i(TAG, "session seeded for $domain")
    } catch (t: Throwable) {
        Log.w(TAG, "signin_failed code=${t.javaClass.simpleName}")
    }
}

private const val TAG: String = "DebugSupabaseSeed"
