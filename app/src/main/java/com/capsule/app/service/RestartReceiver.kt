package com.capsule.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log

/**
 * Receives RESTART_OVERLAY from AlarmManager and BOOT_COMPLETED.
 * Only restarts the service if the user had it enabled and overlay permission is granted.
 */
class RestartReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "RestartReceiver"
        private const val PREFS_NAME = "capsule_overlay_prefs"
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received: ${intent.action}")

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val serviceEnabled = prefs.getBoolean("service_enabled", false)

        if (!serviceEnabled) {
            Log.d(TAG, "Service not enabled by user — skipping restart")
            return
        }

        if (!Settings.canDrawOverlays(context)) {
            Log.w(TAG, "Overlay permission revoked — skipping restart")
            return
        }

        val serviceIntent = Intent(context, CapsuleOverlayService::class.java).apply {
            action = CapsuleOverlayService.ACTION_RESTART_OVERLAY
        }
        try {
            context.startForegroundService(serviceIntent)
            Log.d(TAG, "Service restart initiated")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart service", e)
        }
    }
}
