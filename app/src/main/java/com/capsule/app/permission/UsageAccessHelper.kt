package com.capsule.app.permission

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings

/**
 * Checks and requests the `PACKAGE_USAGE_STATS` "special permission"
 * Android requires before [android.app.usage.UsageStatsManager] returns
 * any foreground-event data.
 *
 * Without this permission, [com.capsule.app.capture.StateSnapshotCollector]
 * falls back to [com.capsule.app.data.model.AppCategory.UNKNOWN_SOURCE] and
 * every captured envelope is threaded under "Unknown source" in the Diary.
 */
object UsageAccessHelper {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return false
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun buildUsageAccessIntent(): Intent {
        // Android doesn't allow direct per-app routing for usage access —
        // this is the system-wide list where the user picks Orbit and
        // flips the toggle.
        return Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
