package com.capsule.app.service

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.capsule.app.overlay.BubbleUI
import com.capsule.app.overlay.CaptureSheetUI
import com.capsule.app.overlay.EdgeSide
import com.capsule.app.overlay.ExpansionState
import com.capsule.app.overlay.OverlayViewModel
import com.capsule.app.ui.theme.CapsuleTheme

class CapsuleOverlayService : LifecycleService() {

    companion object {
        const val ACTION_START_OVERLAY = "com.capsule.app.action.START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.capsule.app.action.STOP_OVERLAY"
        const val ACTION_RESTART_OVERLAY = "com.capsule.app.action.RESTART_OVERLAY"
        private const val TAG = "CapsuleOverlay"
        private const val PREFS_NAME = "capsule_overlay_prefs"
    }

    private lateinit var windowManager: WindowManager
    private lateinit var overlayLifecycleOwner: OverlayLifecycleOwner
    private lateinit var notificationManager: ForegroundNotificationManager
    private lateinit var healthMonitor: ServiceHealthMonitor
    private lateinit var prefs: SharedPreferences
    private var composeView: ComposeView? = null
    private var overlayParams: WindowManager.LayoutParams? = null
    private var clipboardStateMachine: ClipboardFocusStateMachine? = null
    private var viewModel: OverlayViewModel? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        notificationManager = ForegroundNotificationManager(this)
        healthMonitor = ServiceHealthMonitor(this)

        notificationManager.createNotificationChannel()
        healthMonitor.onServiceStarted()

        setupOverlay()

        // Android 15 (API 35) can throw ForegroundServiceStartNotAllowedException
        // if the service was not started from an allowed context. Catch and
        // surface via ServiceHealthMonitor; scheduleRestart to retry later.
        try {
            startForeground(
                ForegroundNotificationManager.NOTIFICATION_ID,
                notificationManager.buildNotification()
            )
        } catch (e: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                e is ForegroundServiceStartNotAllowedException) {
                Log.e(TAG, "ForegroundServiceStartNotAllowed — scheduling retry", e)
            } else {
                Log.e(TAG, "startForeground failed", e)
            }
            healthMonitor.onServiceKilled()
            scheduleRestart()
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        when (intent?.action) {
            ACTION_STOP_OVERLAY -> {
                Log.d(TAG, "STOP_OVERLAY received")
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_RESTART_OVERLAY -> {
                Log.d(TAG, "RESTART_OVERLAY received")
            }
            ACTION_START_OVERLAY -> {
                Log.d(TAG, "START_OVERLAY received")
            }
        }

        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved — scheduling restart")
        healthMonitor.onServiceKilled()
        scheduleRestart()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        removeOverlay()
        // If the service is being stopped by user toggle (not a kill), record it
        // so the health indicator reflects reality instead of staying ACTIVE.
        val userEnabled = prefs.getBoolean("service_enabled", false)
        if (!userEnabled) {
            healthMonitor.onServiceStopped()
        }
        healthMonitor.dispose()
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun setupOverlay() {
        overlayLifecycleOwner = OverlayLifecycleOwner()
        overlayLifecycleOwner.onCreate()

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        // Bubble size is 56dp, convert to pixels
        val bubbleWidthPx = (56 * displayMetrics.density).toInt()

        // Restore persisted position, clamping to valid range
        val savedX = prefs.getInt("bubble_x", 0)
        val restoredY = prefs.getInt("bubble_y", 100)
        val restoredEdge = try {
            EdgeSide.valueOf(prefs.getString("bubble_edge", "LEFT") ?: "LEFT")
        } catch (_: Exception) {
            EdgeSide.LEFT
        }
        // Ensure bubble is visible: clamp x to [0, screenWidth - bubbleWidth]
        val maxX = screenWidth - bubbleWidthPx
        val restoredX = savedX.coerceIn(0, maxX)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = restoredX
            y = restoredY
        }
        overlayParams = params

        val view = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
        }
        composeView = view

        // Create ViewModel via the overlay lifecycle owner's ViewModelStore
        val vm = ViewModelProvider(overlayLifecycleOwner)[OverlayViewModel::class.java]
        viewModel = vm
        vm.restorePosition(restoredX, restoredY, restoredEdge)

        // Set up clipboard state machine
        val stateMachine = ClipboardFocusStateMachine(
            context = this,
            windowManager = windowManager,
            layoutParams = params,
            overlayView = view
        )
        clipboardStateMachine = stateMachine

        stateMachine.setOnResultListener { content ->
            vm.onClipboardReadResult(content)
        }
        stateMachine.setOnStateChangedListener { state ->
            vm.onFocusStateChanged(state)
        }

        vm.onRequestClipboardRead = {
            stateMachine.requestClipboardRead()
        }

        vm.onBubblePositionChanged = { x, y, edge ->
            prefs.edit()
                .putInt("bubble_x", x)
                .putInt("bubble_y", y)
                .putString("bubble_edge", edge.name)
                .apply()
            // Also update the window position
            params.x = x
            params.y = y
            try {
                windowManager.updateViewLayout(view, params)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update overlay position", e)
            }
        }

        val screenHeight = displayMetrics.heightPixels

        view.setContent {
            CapsuleTheme {
                val bubbleState by vm.bubbleState.collectAsState()
                val capturedContent by vm.capturedContent.collectAsState()
                val isExpanded = bubbleState.expansion == ExpansionState.EXPANDED

                // Resize overlay window in a LaunchedEffect keyed on expansion state.
                // This guarantees the resize fires exactly once per transition, after
                // composition settles, rather than racing against it.
                //
                // On COLLAPSE we also reset the clipboard state machine — this is what
                // makes the second tap reliable. Without it, any mid-flight SM state
                // left over from the prior capture can block subsequent requestClipboardRead() calls.
                LaunchedEffect(isExpanded) {
                    try {
                        if (isExpanded) {
                            params.width = WindowManager.LayoutParams.MATCH_PARENT
                            params.height = WindowManager.LayoutParams.MATCH_PARENT
                            params.x = 0
                            params.y = 0
                            params.flags = params.flags or
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        } else {
                            params.width = WindowManager.LayoutParams.WRAP_CONTENT
                            params.height = WindowManager.LayoutParams.WRAP_CONTENT
                            val bs = vm.bubbleState.value
                            params.x = bs.x
                            params.y = bs.y
                            params.flags = params.flags and
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
                            // Critical: drop any stale SM state so the next tap can read.
                            clipboardStateMachine?.resetToIdle()
                        }
                        windowManager.updateViewLayout(view, params)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to resize overlay for expansion=$isExpanded", e)
                    }
                }

                if (isExpanded) {
                    Column(
                        modifier = Modifier
                            .wrapContentSize()
                            .padding(top = with(LocalDensity.current) {
                                (vm.bubbleState.value.y.toDp() + 60.dp)
                            })
                    ) {
                        CaptureSheetUI(
                            content = capturedContent,
                            isExpanded = true,
                            onSave = { vm.onSaveCapture() },
                            onDiscard = { vm.onDiscardCapture() }
                        )
                    }
                } else {
                    BubbleUI(
                        onTap = { vm.onBubbleTap() },
                        onDragStart = { vm.onBubbleDragStart() },
                        onDrag = { dx, dy -> vm.onBubbleDrag(dx, dy, screenWidth, screenHeight) },
                        onDragEnd = { vm.onBubbleDragEnd(screenWidth, bubbleWidthPx) }
                    )
                }
            }
        }

        try {
            windowManager.addView(view, params)
            overlayLifecycleOwner.onStart()
            overlayLifecycleOwner.onResume()
            Log.d(TAG, "Overlay added to WindowManager")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to add overlay", e)
            stopSelf()
        }
    }

    private fun removeOverlay() {
        composeView?.let { view ->
            overlayLifecycleOwner.onPause()
            overlayLifecycleOwner.onStop()
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to remove overlay view", e)
            }
            overlayLifecycleOwner.onDestroy()
        }
        composeView = null
        clipboardStateMachine = null
        viewModel = null
    }

    private fun scheduleRestart() {
        val restartIntent = Intent(this, RestartReceiver::class.java).apply {
            action = ACTION_RESTART_OVERLAY
        }
        val pendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            restartIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 3000,
            pendingIntent
        )
    }
}
