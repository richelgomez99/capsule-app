package com.capsule.app.audit

import android.content.Context
import androidx.core.content.edit

/**
 * T105 — local-only dev counters. Prefs-backed because the signal we
 * care about (diary opens) has no natural audit-row representation and
 * we don't want to pollute the audit table with dev-only noise.
 *
 * All mutators short-circuit to a no-op when `BuildConfig.DEBUG` is
 * false, so release builds pay nothing at call sites.
 *
 * Envelope + continuation counters are NOT stored here — they are
 * derived at dump time from the DB (see [DebugDumpReceiver]).
 */
object DebugCounters {

    private const val PREFS = "orbit.debug_counters"
    private const val KEY_DIARY_OPENS = "diary_opens"

    fun incDiaryOpen(context: Context) {
        if (!com.capsule.app.BuildConfig.DEBUG) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit { putInt(KEY_DIARY_OPENS, prefs.getInt(KEY_DIARY_OPENS, 0) + 1) }
    }

    fun diaryOpens(context: Context): Int =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_DIARY_OPENS, 0)
}
