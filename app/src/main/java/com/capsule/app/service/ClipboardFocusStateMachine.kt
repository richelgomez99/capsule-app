package com.capsule.app.service

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.WindowManager
import com.capsule.app.overlay.CapturedContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ClipboardFocusState {
    IDLE,
    REQUESTING_FOCUS,
    READING_CLIPBOARD,
    RESTORING_FLAGS
}

/**
 * 4-state focus hack state machine for reading clipboard from an overlay window.
 *
 * Temporarily removes FLAG_NOT_FOCUSABLE to acquire focus, reads clipboard,
 * then restores the flag. 500ms hard timeout guard prevents permanent focus steal.
 */
class ClipboardFocusStateMachine(
    private val context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams,
    private val overlayView: android.view.View
) {
    companion object {
        private const val TAG = "ClipboardFocus"
        private const val TIMEOUT_MS = 500L
    }

    private val handler = Handler(Looper.getMainLooper())
    private val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

    private val _state = MutableStateFlow(ClipboardFocusState.IDLE)
    val state: StateFlow<ClipboardFocusState> = _state.asStateFlow()

    private var onResult: ((CapturedContent?) -> Unit)? = null
    private var onStateChanged: ((ClipboardFocusState) -> Unit)? = null

    private val timeoutRunnable = Runnable {
        Log.w(TAG, "Timeout hit — force-restoring flags from state ${_state.value}")
        restoreFlags()
        deliverResult(null)
    }

    fun setOnResultListener(listener: (CapturedContent?) -> Unit) {
        onResult = listener
    }

    fun setOnStateChangedListener(listener: (ClipboardFocusState) -> Unit) {
        onStateChanged = listener
    }

    fun requestClipboardRead() {
        if (_state.value != ClipboardFocusState.IDLE) {
            Log.w(TAG, "Rejected — current state: ${_state.value}")
            return
        }

        // Check clipboard metadata first (no toast)
        if (!clipboard.hasPrimaryClip()) {
            Log.d(TAG, "Clipboard empty")
            deliverResult(null)
            return
        }

        val description = clipboard.primaryClipDescription
        if (description?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) != true) {
            Log.d(TAG, "Non-text MIME: ${description?.getMimeType(0)}")
            deliverResult(null)
            return
        }

        // Transition: IDLE → REQUESTING_FOCUS
        transition(ClipboardFocusState.REQUESTING_FOCUS)
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        // Remove FLAG_NOT_FOCUSABLE to acquire focus
        layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        try {
            windowManager.updateViewLayout(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update layout for focus", e)
            handler.removeCallbacks(timeoutRunnable)
            restoreFlags()
            deliverResult(null)
            return
        }

        // Transition: REQUESTING_FOCUS → READING_CLIPBOARD
        transition(ClipboardFocusState.READING_CLIPBOARD)

        val result = try {
            val clip = clipboard.primaryClip
            val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()
            val isSensitive = description.extras
                ?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) ?: false

            if (text.isNullOrBlank()) {
                null
            } else {
                CapturedContent(
                    text = text,
                    sourcePackage = null,
                    timestamp = System.currentTimeMillis(),
                    isSensitive = isSensitive
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Clipboard read failed", e)
            null
        }

        handler.removeCallbacks(timeoutRunnable)
        restoreFlags()
        deliverResult(result)
    }

    private fun restoreFlags() {
        transition(ClipboardFocusState.RESTORING_FLAGS)
        layoutParams.flags = layoutParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try {
            windowManager.updateViewLayout(overlayView, layoutParams)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore flags", e)
        }
        transition(ClipboardFocusState.IDLE)
    }

    private fun transition(newState: ClipboardFocusState) {
        Log.d(TAG, "${_state.value} → $newState")
        _state.value = newState
        onStateChanged?.invoke(newState)
    }

    private fun deliverResult(content: CapturedContent?) {
        onResult?.invoke(content)
    }
}
