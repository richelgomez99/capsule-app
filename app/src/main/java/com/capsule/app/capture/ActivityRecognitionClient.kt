package com.capsule.app.capture

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import com.capsule.app.data.model.ActivityState
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient as GmsActivityRecognitionClient
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import android.app.PendingIntent

/**
 * T081 (Phase 7 US5) — thin wrapper over Google Play Services
 * [GmsActivityRecognitionClient] that requests transitions for the
 * five [ActivityState] values Orbit cares about and pushes each
 * observed transition into the process-local [ActivityStateCache]
 * (read by [StateSnapshotCollector] at capture time).
 *
 * ## Graceful degrade (FR-US5 / research.md §State Signal Collection)
 *
 * | Condition | Behaviour |
 * |---|---|
 * | `ACTIVITY_RECOGNITION` permission not granted on API 29+ | [start] is a silent no-op; [ActivityStateCache] stays at [ActivityState.STILL] default |
 * | Play Services unavailable | [start] still registers the broadcast receiver; cache stays at default |
 * | No transition observed yet | [ActivityStateCache.current] returns [ActivityState.STILL] |
 *
 * Capture never fails because Activity Recognition is missing.
 *
 * ## Process model
 *
 * Designed to be constructed once from the `:capture` process at
 * [com.capsule.app.service.CapsuleOverlayService] startup. [stop] is
 * idempotent and safe to call from `onDestroy`.
 *
 * Not wired into the overlay service in this slice — that wiring lives
 * in a later integration task. Exposed as a standalone class so tests
 * (both unit and instrumented) can exercise [handleResult] directly
 * with synthetic [ActivityTransitionResult] payloads without touching
 * Play Services at all.
 */
class ActivityRecognitionClient(
    private val context: Context,
    private val cache: ActivityStateCache = ActivityStateCache,
    private val clientFactory: (Context) -> GmsActivityRecognitionClient = {
        ActivityRecognition.getClient(it)
    }
) {

    private var pendingIntent: PendingIntent? = null

    /**
     * Begin requesting activity transitions. Silent no-op if the
     * runtime permission is missing on API 29+. On any Play Services
     * error the request simply never completes — Orbit continues with
     * the cache's default [ActivityState.STILL].
     */
    fun start() {
        if (!hasPermission()) return

        val transitions = listOf(
            DetectedActivity.STILL,
            DetectedActivity.WALKING,
            DetectedActivity.RUNNING,
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE
        ).flatMap { activity ->
            listOf(
                ActivityTransition.Builder()
                    .setActivityType(activity)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            )
        }

        val request = ActivityTransitionRequest(transitions)
        val pi = getOrCreatePendingIntent()

        // Lint cannot see through `clientFactory` + our own `hasPermission()`
        // guard above, so we explicitly catch SecurityException to satisfy
        // the MissingPermission check. Any other failure (no Play Services,
        // rate limit, etc.) is silently swallowed — graceful degrade per
        // research.md. Capture never fails because AR is missing.
        try {
            clientFactory(context).requestActivityTransitionUpdates(request, pi)
        } catch (_: SecurityException) {
            // Permission revoked mid-flight — stay on default STILL.
        } catch (_: Throwable) {
            // Play Services unavailable or any other transient failure.
        }
    }

    /** Stop receiving transition updates. Idempotent. */
    fun stop() {
        val pi = pendingIntent ?: return
        try {
            clientFactory(context).removeActivityTransitionUpdates(pi)
        } catch (_: SecurityException) {
            // Permission already revoked — nothing to do.
        } catch (_: Throwable) {
            // Play Services unavailable — swallow.
        }
        pi.cancel()
        pendingIntent = null
    }

    /**
     * Consume an [ActivityTransitionResult] (typically from a broadcast
     * receiver) and update [ActivityStateCache] with the most recent
     * ENTER transition. Visible-for-testing.
     */
    internal fun handleResult(result: ActivityTransitionResult) {
        val latest = result.transitionEvents
            .filter { it.transitionType == ActivityTransition.ACTIVITY_TRANSITION_ENTER }
            .maxByOrNull { it.elapsedRealTimeNanos }
            ?: return
        cache.update(latest.activityType.toActivityState())
    }

    private fun hasPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return context.checkSelfPermission(
            "android.permission.ACTIVITY_RECOGNITION"
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun getOrCreatePendingIntent(): PendingIntent {
        pendingIntent?.let { return it }
        val intent = Intent(ACTION_TRANSITIONS).setPackage(context.packageName)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pi = PendingIntent.getBroadcast(context, 0, intent, flags)
        pendingIntent = pi
        return pi
    }

    companion object {
        /** Broadcast action used by the transition PendingIntent. */
        const val ACTION_TRANSITIONS = "com.capsule.app.capture.ACTIVITY_TRANSITIONS"

        /** Intent filter a receiver can register with. */
        fun intentFilter(): IntentFilter = IntentFilter(ACTION_TRANSITIONS)

        private fun Int.toActivityState(): ActivityState = when (this) {
            DetectedActivity.STILL -> ActivityState.STILL
            DetectedActivity.WALKING -> ActivityState.WALKING
            DetectedActivity.RUNNING -> ActivityState.RUNNING
            DetectedActivity.IN_VEHICLE -> ActivityState.IN_VEHICLE
            DetectedActivity.ON_BICYCLE -> ActivityState.ON_BICYCLE
            else -> ActivityState.UNKNOWN
        }
    }
}
