package com.capsule.app.audit

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.capsule.app.BuildConfig
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.model.AuditAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * T105 / T106 — dev-only broadcast-driven counter dump.
 *
 * Triggered by `adb shell am broadcast -a com.capsule.app.DEBUG_DUMP`
 * (see quickstart.md §Debug Dump). Writes to logcat under tag
 * "OrbitDebugDump". No-op on release builds — both the [BuildConfig.DEBUG]
 * guard here and the register-only-in-debug path in
 * [com.capsule.app.CapsuleApplication] prevent any production exposure.
 *
 * Counters pulled at dump time:
 *  - envelopes: total / archived / soft-deleted
 *  - continuations: per-status from `countByStatus()`
 *  - continuation success rate: completed / (completed + failed_*)
 *  - audit: last-24h row counts grouped by action
 *  - diary opens: persistent counter from [DebugCounters]
 */
class DebugDumpReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!BuildConfig.DEBUG) return
        if (intent?.action != ACTION) return
        val pending = goAsync()
        val appCtx = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                dump(appCtx)
            } catch (t: Throwable) {
                Log.w(TAG, "dump failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun dump(appCtx: Context) {
        val db = OrbitDatabase.getInstance(appCtx)
        val envelopes = db.intentEnvelopeDao()
        val continuations = db.continuationDao()
        val audit = db.auditLogDao()

        val total = envelopes.countAll()
        val archived = envelopes.countArchived()
        val deleted = envelopes.countDeleted()

        val statuses = continuations.countByStatus()
        val succeeded = statuses.firstOrNull { it.status == "COMPLETED" }?.n ?: 0
        val failed = statuses.filter { it.status.startsWith("FAILED") }.sumOf { it.n }
        val denom = succeeded + failed
        val rate = if (denom == 0) "n/a" else String.format("%.1f%%", 100.0 * succeeded / denom)

        val dayMillis = 24L * 60L * 60L * 1000L
        val now = System.currentTimeMillis()
        val last24 = audit.entriesForDay(now - dayMillis, now)
            .groupingBy { it.action }
            .eachCount()

        val diaryOpens = DebugCounters.diaryOpens(appCtx)

        val sb = StringBuilder()
        sb.appendLine("=== Orbit debug dump ===")
        sb.appendLine("envelopes: total=$total archived=$archived soft_deleted=$deleted")
        sb.append("continuations: ")
        if (statuses.isEmpty()) sb.append("(none)") else
            sb.append(statuses.joinToString(" ") { "${it.status}=${it.n}" })
        sb.appendLine()
        sb.appendLine("continuation_success_rate: $rate ($succeeded/$denom)")
        sb.appendLine("diary_opens: $diaryOpens")
        sb.appendLine("audit_last_24h:")
        if (last24.isEmpty()) sb.appendLine("  (none)") else
            AuditAction.entries.forEach { a ->
                val n = last24[a] ?: 0
                if (n > 0) sb.appendLine("  ${a.name}=$n")
            }
        Log.i(TAG, sb.toString())
    }

    companion object {
        const val ACTION = "com.capsule.app.DEBUG_DUMP"
        private const val TAG = "OrbitDebugDump"
    }
}
