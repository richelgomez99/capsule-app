package com.capsule.app.permission

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher

/**
 * Checks and requests SYSTEM_ALERT_WINDOW and POST_NOTIFICATIONS permissions.
 */
object OverlayPermissionHelper {

    fun canDrawOverlays(context: Context): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun buildOverlayPermissionIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        )
    }

    fun requestNotificationPermission(launcher: ActivityResultLauncher<String>) {
        launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
}
