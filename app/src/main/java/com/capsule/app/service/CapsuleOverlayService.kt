package com.capsule.app.service

import android.app.AlarmManager
import android.app.ForegroundServiceStartNotAllowedException
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.model.AuditAction
import com.capsule.app.overlay.BubbleUI
import com.capsule.app.overlay.CaptureSheetUI
import com.capsule.app.overlay.DismissTargetMetrics
import com.capsule.app.overlay.DismissTargetUI
import com.capsule.app.overlay.EdgeSide
import com.capsule.app.overlay.ExpansionState
import com.capsule.app.overlay.OverlayViewModel
import com.capsule.app.overlay.PostCaptureOverlay
import com.capsule.app.overlay.PostCaptureUi
import com.capsule.app.capture.ScreenshotObserver
import com.capsule.app.data.ipc.IEnvelopeRepository
import com.capsule.app.data.ipc.EnvelopeRepositoryService
import com.capsule.app.ui.theme.CapsuleTheme
import android.provider.MediaStore
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class CapsuleOverlayService : LifecycleService() {

    companion object {
        const val ACTION_START_OVERLAY = "com.capsule.app.action.START_OVERLAY"
        const val ACTION_STOP_OVERLAY = "com.capsule.app.action.STOP_OVERLAY"
        const val ACTION_RESTART_OVERLAY = "com.capsule.app.action.RESTART_OVERLAY"
        private const val TAG = "CapsuleOverlay"
        private const val PREFS_NAME = "capsule_overlay_prefs"
        private const val DISMISS_TARGET_SIZE_DP = 72
        private const val DISMISS_TARGET_BOTTOM_MARGIN_DP = 48
        private const val DISMISS_TARGET_ACTIVATION_PADDING_DP = 20
        /** T042: unbind `:ml` repo after this many ms of the sheet being offscreen. */
        private const val REPO_IDLE_UNBIND_MS = 30_000L
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
    private var dismissTargetView: ComposeView? = null
    private var postCaptureView: ComposeView? = null

    // T042: bound `:ml` EnvelopeRepositoryService.
    @Volatile private var envelopeRepo: IEnvelopeRepository? = null
    private var repoBound: Boolean = false
    private var sealOrchestrator: CapsuleSealOrchestrator? = null
    // T074 (Phase 6 US4) — screenshot observer lifecycle.
    private var screenshotObserver: ScreenshotObserver? = null
    private val idleUnbindHandler = Handler(Looper.getMainLooper())
    private val idleUnbindRunnable = Runnable {
        Log.d(TAG, "REPO_IDLE_UNBIND — 30s offscreen, unbinding :ml")
        unbindFromRepo()
    }

    private val repoConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = IEnvelopeRepository.Stub.asInterface(service)
            envelopeRepo = binder
            Log.d(TAG, "EnvelopeRepositoryService connected")
            // Orchestrator already installed on VM with a provider closure;
            // nothing more to do here.
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w(TAG, "EnvelopeRepositoryService disconnected (unexpected)")
            envelopeRepo = null
        }

        override fun onBindingDied(name: ComponentName?) {
            Log.w(TAG, "EnvelopeRepositoryService binding died")
            envelopeRepo = null
            repoBound = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        notificationManager = ForegroundNotificationManager(this)
        healthMonitor = ServiceHealthMonitor(this)

        notificationManager.createNotificationChannel()
        healthMonitor.onServiceStarted()

        // T104 — write SERVICE_STARTED audit row (contracts/audit-log-contract.md §3).
        writeServiceAudit(AuditAction.SERVICE_STARTED, "Overlay service started")

        setupOverlay()

        // Android 15 (API 35) can throw ForegroundServiceStartNotAllowedException
        // if the service was not started from an allowed context. Catch and
        // surface via ServiceHealthMonitor; scheduleRestart to retry later.
        try {
            startForeground(
                ForegroundNotificationManager.NOTIFICATION_ID,
                notificationManager.buildNotification()
            )
            // T074 (Phase 6 US4) — register ScreenshotObserver once the
            // service is fully foregrounded. Observer lifecycle is tied to
            // onCreate/onDestroy per research.md §Screenshot Observation.
            // Uses a lazy binder lookup so the observer can seal even if
            // the overlay's own :ml bind is currently idle-unbound.
            registerScreenshotObserver()
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
        cancelIdleUnbind()
        unregisterScreenshotObserver()
        unbindFromRepo()
        removeOverlay()
        // If the service is being stopped by user toggle (not a kill), record it
        // so the health indicator reflects reality instead of staying ACTIVE.
        val userEnabled = prefs.getBoolean("service_enabled", false)
        if (!userEnabled) {
            healthMonitor.onServiceStopped()
        }
        // T104 — write SERVICE_STOPPED audit row. Fire-and-forget from
        // lifecycleScope so Room insert runs before the service is torn
        // down (scope is cancelled after super.onDestroy() returns).
        writeServiceAudit(AuditAction.SERVICE_STOPPED, "Overlay service stopped")
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
            // T043 (pragmatic scope): observability-only audit transitions.
            // Real AuditLog rows are closed-enum (AuditAction has no
            // CLIPBOARD_FOCUSED sentinel); Logcat tags unblock diagnostics
            // and let downstream tooling (bugreport, logcat dumps) see the
            // focus-hack lifecycle. Schema change tracked as follow-up.
            when (state) {
                ClipboardFocusState.REQUESTING_FOCUS ->
                    Log.d(TAG, "AUDIT | CLIPBOARD_FOCUSED")
                ClipboardFocusState.IDLE ->
                    Log.d(TAG, "AUDIT | CLIPBOARD_FOCUS_RELEASED")
                else -> Unit
            }
        }

        vm.onRequestClipboardRead = {
            // T042: ensure :ml is bound before the clipboard read resolves so
            // the seal path has a live binder by the time orchestrator runs.
            ensureRepoBound()
            cancelIdleUnbind()
            stateMachine.requestClipboardRead()
        }

        // T038/T042: install production orchestrator backed by the bound repo.
        // Uses a provider closure so transient disconnects don't require a
        // VM reset — the orchestrator simply surfaces Blocked("repo unbound").
        val orchestrator = CapsuleSealOrchestrator(
            appContext = applicationContext,
            repositoryProvider = { envelopeRepo }
        )
        sealOrchestrator = orchestrator
        vm.sealOrchestrator = orchestrator

        vm.onBubblePositionChanged = { x, y, edge ->
            prefs.edit()
                .putInt("bubble_x", x)
                .putInt("bubble_y", y)
                .putString("bubble_edge", edge.name)
                .apply()
        }

        vm.onDismissRequested = {
            prefs.edit().putBoolean("service_enabled", false).apply()
            stopSelf()
        }

        val screenHeight = displayMetrics.heightPixels

        lifecycleScope.launch {
            vm.bubbleState.collectLatest { bubbleState ->
                syncCollapsedOverlayPosition(view, params, bubbleState)

                if (bubbleState.isDismissTargetVisible) {
                    showDismissTarget(vm)
                } else {
                    hideDismissTarget()
                }

                // T042: when the sheet collapses, start the 30s idle timer
                // to unbind :ml. Any re-expand cancels the timer.
                if (bubbleState.expansion == ExpansionState.COLLAPSED) {
                    scheduleIdleUnbind()
                } else {
                    cancelIdleUnbind()
                }
            }
        }

        // Post-capture UI runs in its own dedicated window so the chip row,
        // silent-wrap pill, and undo pill are never clipped by the bubble's
        // WRAP_CONTENT window bounds. The window is added on demand the
        // first time state flips to non-None, and removed when it returns
        // to None.
        lifecycleScope.launch {
            vm.postCaptureUi.collectLatest { ui ->
                if (ui is PostCaptureUi.None) {
                    hidePostCaptureOverlay()
                } else {
                    showPostCaptureOverlay(vm)
                }
            }
        }

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
                            // Scrim must absorb taps outside the sheet so the
                            // user can dismiss. Clear NOT_TOUCH_MODAL.
                            params.flags = params.flags and
                                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL.inv()
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
                    // Full-screen scrim: tap outside the sheet discards the
                    // capture. Sheet is anchored near the top so it's always
                    // reachable regardless of where the bubble sits.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.35f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { vm.onDiscardCapture() }
                            )
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = 72.dp, start = 8.dp, end = 8.dp),
                            contentAlignment = Alignment.TopCenter
                        ) {
                            // Consume the tap so clicks on the sheet itself
                            // don't bubble up to the scrim's discard handler.
                            Box(
                                modifier = Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {}
                                )
                            ) {
                                CaptureSheetUI(
                                    content = capturedContent,
                                    isExpanded = true,
                                    onSave = { vm.onSaveCapture() },
                                    onDiscard = { vm.onDiscardCapture() }
                                )
                            }
                        }
                    }
                } else {
                    BubbleUI(
                        onTap = { vm.onBubbleTap() },
                        onDragStart = { vm.onBubbleDragStart() },
                        onDrag = { dx, dy ->
                            vm.onBubbleDrag(
                                dx = dx,
                                dy = dy,
                                screenWidth = screenWidth,
                                screenHeight = screenHeight,
                                bubbleSizePx = bubbleWidthPx,
                                dismissTargetMetrics = computeDismissTargetMetrics(
                                    screenWidth = screenWidth,
                                    screenHeight = screenHeight,
                                    bubbleSizePx = bubbleWidthPx
                                )
                            )
                        },
                        onDragEnd = { vm.onBubbleDragEnd(screenWidth, bubbleWidthPx) }
                    )
                }

                // Post-capture UX (chip row / silent pill / undo pill) lives
                // in its own dedicated window — see showPostCaptureOverlay()
                // below. Spawning a full-width window avoids the
                // chip-row-clipped-off-right-edge problem that happens when
                // the pill is hosted in the bubble's WRAP_CONTENT window.
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
        hideDismissTarget()
        hidePostCaptureOverlay()
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

    private fun syncCollapsedOverlayPosition(
        view: ComposeView,
        params: WindowManager.LayoutParams,
        bubbleState: com.capsule.app.overlay.BubbleState
    ) {
        if (bubbleState.expansion != ExpansionState.COLLAPSED) {
            return
        }
        if (params.width != WindowManager.LayoutParams.WRAP_CONTENT ||
            params.height != WindowManager.LayoutParams.WRAP_CONTENT) {
            return
        }

        if (params.x == bubbleState.x && params.y == bubbleState.y) {
            return
        }

        params.x = bubbleState.x
        params.y = bubbleState.y
        try {
            windowManager.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update overlay position", e)
        }
    }

    private fun computeDismissTargetMetrics(
        screenWidth: Int,
        screenHeight: Int,
        bubbleSizePx: Int
    ): DismissTargetMetrics {
        val density = resources.displayMetrics.density
        val targetSizePx = (DISMISS_TARGET_SIZE_DP * density).toInt()
        val bottomMarginPx = (DISMISS_TARGET_BOTTOM_MARGIN_DP * density).toInt()
        val activationPaddingPx = (DISMISS_TARGET_ACTIVATION_PADDING_DP * density).toInt()
        return DismissTargetMetrics(
            centerX = screenWidth / 2,
            centerY = screenHeight - bottomMarginPx - (targetSizePx / 2),
            activationRadiusPx = (targetSizePx / 2) + (bubbleSizePx / 2) + activationPaddingPx
        )
    }

    private fun showDismissTarget(vm: OverlayViewModel) {
        if (dismissTargetView != null) {
            return
        }

        val targetView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setContent {
                CapsuleTheme {
                    val bubbleState by vm.bubbleState.collectAsState()
                    DismissTargetUI(isActive = bubbleState.isOverDismissTarget)
                }
            }
        }

        val bottomMarginPx = (DISMISS_TARGET_BOTTOM_MARGIN_DP * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = bottomMarginPx
        }

        try {
            windowManager.addView(targetView, params)
            dismissTargetView = targetView
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show dismiss target", e)
        }
    }

    private fun hideDismissTarget() {
        val targetView = dismissTargetView ?: return
        try {
            windowManager.removeView(targetView)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide dismiss target", e)
        }
        dismissTargetView = null
    }

    /**
     * Mount the [PostCaptureOverlay] in its own full-width window anchored
     * to the bottom of the screen. The window is touchable (for chip taps +
     * undo) but does not absorb background touches thanks to
     * `FLAG_NOT_FOCUSABLE`. It spans `MATCH_PARENT` width with `WRAP_CONTENT`
     * height so the pill is always fully visible regardless of where the
     * bubble sits.
     */
    private fun showPostCaptureOverlay(vm: OverlayViewModel) {
        if (postCaptureView != null) return

        val targetView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(overlayLifecycleOwner)
            setViewTreeViewModelStoreOwner(overlayLifecycleOwner)
            setViewTreeSavedStateRegistryOwner(overlayLifecycleOwner)
            setContent {
                CapsuleTheme {
                    PostCaptureOverlay(viewModel = vm)
                }
            }
        }

        val bottomMarginPx = (24 * resources.displayMetrics.density).toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            // NOT_FOCUSABLE: keyboards in other apps still work.
            // LAYOUT_NO_LIMITS: allowed to extend behind system bars.
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            y = bottomMarginPx
        }

        try {
            windowManager.addView(targetView, params)
            postCaptureView = targetView
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show post-capture overlay", e)
        }
    }

    private fun hidePostCaptureOverlay() {
        val targetView = postCaptureView ?: return
        try {
            windowManager.removeView(targetView)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide post-capture overlay", e)
        }
        postCaptureView = null
    }

    // ---- T042: :ml EnvelopeRepositoryService bind/unbind lifecycle ----

    private fun ensureRepoBound() {
        if (repoBound) return
        val intent = Intent(this, EnvelopeRepositoryService::class.java)
        val ok = bindService(intent, repoConnection, Context.BIND_AUTO_CREATE)
        if (ok) {
            repoBound = true
            Log.d(TAG, "bindService(EnvelopeRepositoryService) requested")
        } else {
            Log.e(TAG, "bindService(EnvelopeRepositoryService) failed")
        }
    }

    private fun unbindFromRepo() {
        if (!repoBound) return
        try {
            unbindService(repoConnection)
            Log.d(TAG, "unbindService(EnvelopeRepositoryService)")
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "unbindService failed — service not bound", e)
        }
        repoBound = false
        envelopeRepo = null
    }

    private fun scheduleIdleUnbind() {
        idleUnbindHandler.removeCallbacks(idleUnbindRunnable)
        idleUnbindHandler.postDelayed(idleUnbindRunnable, REPO_IDLE_UNBIND_MS)
    }

    private fun cancelIdleUnbind() {
        idleUnbindHandler.removeCallbacks(idleUnbindRunnable)
    }

    /**
     * T074 (Phase 6 US4) — register a MediaStore observer on the user's
     * screenshot folder. Observer survives the 30 s idle unbind because
     * the seal path binds the `:ml` service on demand via
     * [ensureRepoBound] when a screenshot event fires.
     */
    private fun registerScreenshotObserver() {
        if (screenshotObserver != null) return
        val observer = ScreenshotObserver.create(
            context = applicationContext,
            repositoryProvider = {
                ensureRepoBound()
                envelopeRepo
            }
        )
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            /* notifyForDescendants = */ true,
            observer
        )
        screenshotObserver = observer
        Log.d(TAG, "ScreenshotObserver registered")
    }

    private fun unregisterScreenshotObserver() {
        val observer = screenshotObserver ?: return
        runCatching { contentResolver.unregisterContentObserver(observer) }
            .onFailure { Log.w(TAG, "unregisterContentObserver failed", it) }
        screenshotObserver = null
        Log.d(TAG, "ScreenshotObserver unregistered")
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
        val triggerAt = SystemClock.elapsedRealtime() + 3000
        // Android 12+ requires SCHEDULE_EXACT_ALARM permission for setExactAndAllowWhileIdle.
        // If the permission is not granted (or revoked by the user), fall back to the
        // inexact variant so we still recover after an OEM kill.
        val canUseExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        if (canUseExact) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            Log.w(TAG, "SCHEDULE_EXACT_ALARM not granted; using inexact restart alarm")
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    /**
     * T104 — writes a SERVICE_STARTED / SERVICE_STOPPED audit row directly
     * via Room (default process has direct DB access; contract §3 does not
     * require this to be cross-process). Fire-and-forget on IO.
     */
    private fun writeServiceAudit(action: AuditAction, description: String) {
        val appContext = applicationContext
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                val dao = OrbitDatabase.getInstance(appContext).auditLogDao()
                dao.insert(AuditLogWriter().build(action = action, description = description))
            }.onFailure { Log.w(TAG, "writeServiceAudit($action) failed", it) }
        }
    }
}
