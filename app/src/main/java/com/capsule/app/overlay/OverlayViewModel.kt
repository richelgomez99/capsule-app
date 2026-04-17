package com.capsule.app.overlay

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capsule.app.service.ClipboardFocusState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class OverlayViewModel : ViewModel() {

    companion object {
        private const val TAG = "OverlayVM"
        /** SC-003: edge-snap animation must complete within 200 ms. */
        private const val SNAP_ANIMATION_DURATION_MS = 180L
        /** Number of frames in the snap animation. 60 fps → 12 frames for 200 ms. */
        private const val SNAP_ANIMATION_FRAMES = 12
        private const val BUBBLE_SIZE_DP = 56
    }

    private var snapAnimationJob: Job? = null

    private val _bubbleState = MutableStateFlow(BubbleState())
    val bubbleState: StateFlow<BubbleState> = _bubbleState.asStateFlow()

    private val _capturedContent = MutableStateFlow<CapturedContent?>(null)
    val capturedContent: StateFlow<CapturedContent?> = _capturedContent.asStateFlow()

    private val _clipboardFocusState = MutableStateFlow(ClipboardFocusState.IDLE)
    val clipboardFocusState: StateFlow<ClipboardFocusState> = _clipboardFocusState.asStateFlow()

    /** Callback set by service to trigger the clipboard focus hack. */
    var onRequestClipboardRead: (() -> Unit)? = null

    /** Callback set by service to persist bubble position. */
    var onBubblePositionChanged: ((x: Int, y: Int, edge: EdgeSide) -> Unit)? = null

    // --- User Actions ---

    fun onBubbleTap() {
        val current = _bubbleState.value
        Log.d(TAG, "onBubbleTap — expansion=${current.expansion}")
        if (current.expansion == ExpansionState.COLLAPSED) {
            Log.d(TAG, "Requesting clipboard read")
            onRequestClipboardRead?.invoke()
        } else {
            Log.d(TAG, "Collapsing from tap")
            _capturedContent.value = null
            _bubbleState.value = current.copy(expansion = ExpansionState.COLLAPSED)
        }
    }

    fun onBubbleDragStart() {
        _bubbleState.value = _bubbleState.value.copy(isDragging = true)
    }

    fun onBubbleDrag(dx: Int, dy: Int, screenWidth: Int, screenHeight: Int) {
        val current = _bubbleState.value
        // Assume square bubble sized to BUBBLE_SIZE_DP; the service passes screen
        // dimensions in pixels so we need the bubble size in pixels too. Use a
        // conservative fallback (density 2.75 ≈ 154 px) when density unknown;
        // in practice the service calls onBubbleDragEnd with the real bubbleWidthPx
        // so the final snap position is accurate even if mid-drag clamp is loose.
        val bubbleWidthEstimate = (BUBBLE_SIZE_DP * 2.75).toInt()
        val newX = (current.x + dx).coerceIn(0, (screenWidth - bubbleWidthEstimate).coerceAtLeast(0))
        val newY = (current.y + dy).coerceIn(0, (screenHeight - bubbleWidthEstimate).coerceAtLeast(0))
        _bubbleState.value = current.copy(x = newX, y = newY)
    }

    fun onBubbleDragEnd(screenWidth: Int, bubbleWidthPx: Int) {
        val current = _bubbleState.value
        val edgeSide = if (current.x + bubbleWidthPx / 2 < screenWidth / 2) EdgeSide.LEFT else EdgeSide.RIGHT
        val targetX = if (edgeSide == EdgeSide.LEFT) 0 else (screenWidth - bubbleWidthPx)
        val startX = current.x

        // End drag immediately so BubbleUI knows the gesture is over.
        _bubbleState.value = current.copy(isDragging = false, edgeSide = edgeSide)

        // SC-003: animate edge-snap <=200 ms. Cancel any in-flight snap first so
        // rapid successive drags don't pile up overlapping animations.
        snapAnimationJob?.cancel()
        snapAnimationJob = viewModelScope.launch {
            val frameDelay = SNAP_ANIMATION_DURATION_MS / SNAP_ANIMATION_FRAMES
            for (frame in 1..SNAP_ANIMATION_FRAMES) {
                val progress = frame.toFloat() / SNAP_ANIMATION_FRAMES
                // Ease-out quad for a natural snap feel.
                val eased = 1f - (1f - progress) * (1f - progress)
                val interpolatedX = (startX + (targetX - startX) * eased).roundToInt()
                _bubbleState.value = _bubbleState.value.copy(x = interpolatedX)
                delay(frameDelay)
            }
            // Ensure we land exactly on the edge.
            val finalState = _bubbleState.value.copy(x = targetX)
            _bubbleState.value = finalState
            onBubblePositionChanged?.invoke(finalState.x, finalState.y, finalState.edgeSide)
        }
    }

    override fun onCleared() {
        snapAnimationJob?.cancel()
        super.onCleared()
    }

    fun onSaveCapture() {
        val content = _capturedContent.value ?: return
        Log.d(
            "CapsuleCapture",
            "SAVED | text=${content.text} | source=${content.sourcePackage} " +
                "| ts=${content.timestamp} | sensitive=${content.isSensitive}"
        )
        _capturedContent.value = null
        _bubbleState.value = _bubbleState.value.copy(expansion = ExpansionState.COLLAPSED)
    }

    fun onDiscardCapture() {
        _capturedContent.value = null
        _bubbleState.value = _bubbleState.value.copy(expansion = ExpansionState.COLLAPSED)
    }

    // --- Service Callbacks ---

    fun onClipboardReadResult(content: CapturedContent?) {
        Log.d(TAG, "onClipboardReadResult — content=${if (content != null) "'${content.text.take(30)}...'" else "null"}")
        _capturedContent.value = content
        _bubbleState.value = _bubbleState.value.copy(
            expansion = if (content != null) ExpansionState.EXPANDED else ExpansionState.COLLAPSED
        )
    }

    fun onFocusStateChanged(state: ClipboardFocusState) {
        _clipboardFocusState.value = state
    }

    fun restorePosition(x: Int, y: Int, edgeSide: EdgeSide) {
        _bubbleState.value = _bubbleState.value.copy(x = x, y = y, edgeSide = edgeSide)
    }
}
