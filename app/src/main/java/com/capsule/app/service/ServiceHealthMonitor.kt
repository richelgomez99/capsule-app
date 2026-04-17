package com.capsule.app.service

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ServiceHealthStatus {
    ACTIVE,
    DEGRADED,
    KILLED
}

data class ServiceHealth(
    val status: ServiceHealthStatus = ServiceHealthStatus.KILLED,
    val restartCount: Int = 0,
    val lastStartTimestamp: Long = 0L,
    val lastKillTimestamp: Long = 0L
)

/**
 * Tracks service health across kills and restarts.
 * DEGRADED: restarted within 5 minutes of a kill. Transitions to ACTIVE after 5 min stable.
 */
class ServiceHealthMonitor(context: Context) {

    companion object {
        private const val PREFS_NAME = "capsule_overlay_prefs"
        private const val KEY_RESTART_COUNT = "restart_count"
        private const val KEY_LAST_START_TS = "last_start_ts"
        private const val KEY_LAST_KILL_TS = "last_kill_ts"
        private const val DEGRADED_THRESHOLD_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var degradedJob: Job? = null

    private val _health = MutableStateFlow(readPersistedHealth())
    val health: StateFlow<ServiceHealth> = _health.asStateFlow()

    fun onServiceStarted() {
        val now = System.currentTimeMillis()
        val lastKill = prefs.getLong(KEY_LAST_KILL_TS, 0L)
        val currentCount = prefs.getInt(KEY_RESTART_COUNT, 0)

        val isRestart = lastKill > 0 && (now - lastKill) < DEGRADED_THRESHOLD_MS
        val newCount = if (isRestart) currentCount + 1 else currentCount
        val status = if (isRestart && newCount > 0) ServiceHealthStatus.DEGRADED else ServiceHealthStatus.ACTIVE

        prefs.edit()
            .putInt(KEY_RESTART_COUNT, newCount)
            .putLong(KEY_LAST_START_TS, now)
            .apply()

        _health.value = ServiceHealth(
            status = status,
            restartCount = newCount,
            lastStartTimestamp = now,
            lastKillTimestamp = lastKill
        )

        if (status == ServiceHealthStatus.DEGRADED) {
            scheduleDegradedToActive()
        }
    }

    fun onServiceKilled() {
        val now = System.currentTimeMillis()
        prefs.edit().putLong(KEY_LAST_KILL_TS, now).apply()
        degradedJob?.cancel()
        _health.value = _health.value.copy(
            status = ServiceHealthStatus.KILLED,
            lastKillTimestamp = now
        )
    }

    /**
     * Called when the user explicitly stops the service via toggle. Distinct from
     * onServiceKilled() in that this is not a restart trigger — we don't record
     * last_kill_ts, so the next onServiceStarted() will count as a clean start,
     * not a DEGRADED restart.
     */
    fun onServiceStopped() {
        degradedJob?.cancel()
        _health.value = _health.value.copy(status = ServiceHealthStatus.KILLED)
    }

    /**
     * Cancel the internal scope. Call from the service's onDestroy() to prevent
     * a coroutine leak if scheduleDegradedToActive() is still pending.
     */
    fun dispose() {
        degradedJob?.cancel()
        scope.cancel()
    }

    private fun scheduleDegradedToActive() {
        degradedJob?.cancel()
        degradedJob = scope.launch {
            delay(DEGRADED_THRESHOLD_MS)
            _health.value = _health.value.copy(status = ServiceHealthStatus.ACTIVE)
        }
    }

    private fun readPersistedHealth(): ServiceHealth {
        return ServiceHealth(
            status = ServiceHealthStatus.KILLED,
            restartCount = prefs.getInt(KEY_RESTART_COUNT, 0),
            lastStartTimestamp = prefs.getLong(KEY_LAST_START_TS, 0L),
            lastKillTimestamp = prefs.getLong(KEY_LAST_KILL_TS, 0L)
        )
    }
}
