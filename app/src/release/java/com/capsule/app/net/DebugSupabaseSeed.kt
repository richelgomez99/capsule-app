package com.capsule.app.net

import io.github.jan.supabase.SupabaseClient

/**
 * Spec 014 T014-019b — release no-op for the debug seed.
 *
 * The debug variant ships its own implementation under
 * `app/src/debug/java/com/capsule/app/net/DebugSupabaseSeed.kt` which does a
 * one-shot `signInWith(Email)` using credentials from `local.properties`.
 * Release builds compile this file instead — `seedIfNeeded` is a no-op.
 *
 * Constitution Principle XIV (no credential exfil): release builds also force
 * `BuildConfig.DEBUG_SUPABASE_EMAIL` / `DEBUG_SUPABASE_PASSWORD` to empty (see
 * `app/build.gradle.kts buildTypes.release`), so even if the debug file were
 * accidentally included it would short-circuit on the empty check.
 */
internal suspend fun seedIfNeeded(supabase: SupabaseClient) {
    // no-op in release
}
