package com.capsule.app.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
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

        startForeground(
            ForegroundNotificationManager.NOTIFICATION_ID,
            notificationManager.buildNotification()
        )
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
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    private fun setupOverlay() {
        overlayLifecycleOwner = OverlayLifecycleOwner()
        overlayLifecycleOwner.onCreate()

        // Restore persisted position
        val restoredX = prefs.getInt("bubble_x", 0)
        val restoredY = prefs.getInt("bubble_y", 100)
        val restoredEdge = try {
            EdgeSide.valueOf(prefs.getString("bubble_edge", "LEFT") ?: "LEFT")
        } catch (_: Exception) {
            EdgeSide.LEFT
        }

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

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val screenHeight = displayMetrics.heightPixels

        view.setContent {
            CapsuleTheme {
                val bubbleState by vm.bubbleState.collectAsState()
                val capturedContent by vm.capturedContent.collectAsState()

                Column(modifier = Modifier.wrapContentSize()) {
                    BubbleUI(
                        onTap = { vm.onBubbleTap() },
                        onDragStart = { vm.onBubbleDragStart() },
                        onDrag = { dx, dy -> vm.onBubbleDrag(dx, dy, screenWidth, screenHeight) },
                        onDragEnd = { vm.onBubbleDragEnd(screenWidth) }
                    )

                    CaptureSheetUI(
                        content = capturedContent,
                        isExpanded = bubbleState.expansion == ExpansionState.EXPANDED,
                        onSave = { vm.onSaveCapture() },
                        onDiscard = { vm.onDiscardCapture() }
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
