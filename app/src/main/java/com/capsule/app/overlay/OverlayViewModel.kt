package com.capsule.app.overlay

import android.util.Log
import androidx.lifecycle.ViewModel
import com.capsule.app.service.ClipboardFocusState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class OverlayViewModel : ViewModel() {

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
        if (current.expansion == ExpansionState.COLLAPSED) {
            onRequestClipboardRead?.invoke()
        } else {
            _capturedContent.value = null
            _bubbleState.value = current.copy(expansion = ExpansionState.COLLAPSED)
        }
    }

    fun onBubbleDragStart() {
        _bubbleState.value = _bubbleState.value.copy(isDragging = true)
    }

    fun onBubbleDrag(dx: Int, dy: Int, screenWidth: Int, screenHeight: Int) {
        val current = _bubbleState.value
        val newX = (current.x + dx).coerceIn(0, screenWidth)
        val newY = (current.y + dy).coerceIn(0, screenHeight)
        _bubbleState.value = current.copy(x = newX, y = newY)
    }

    fun onBubbleDragEnd(screenWidth: Int) {
        val current = _bubbleState.value
        val edgeSide = if (current.x < screenWidth / 2) EdgeSide.LEFT else EdgeSide.RIGHT
        val snapX = if (edgeSide == EdgeSide.LEFT) 0 else screenWidth
        val newState = current.copy(
            x = snapX,
            isDragging = false,
            edgeSide = edgeSide
        )
        _bubbleState.value = newState
        onBubblePositionChanged?.invoke(newState.x, newState.y, newState.edgeSide)
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
