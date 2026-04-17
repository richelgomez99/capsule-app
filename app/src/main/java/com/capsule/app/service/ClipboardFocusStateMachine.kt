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
        private const val TIMEOUT_MS = 1000L
        /** Delay after removing FLAG_NOT_FOCUSABLE to let WindowManager grant focus. */
        private const val FOCUS_SETTLE_DELAY_MS = 150L
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

    /**
     * Hard reset the state machine back to IDLE. Called when the capture sheet
     * collapses to guarantee the next tap can initiate a fresh clipboard read.
     * Cancels any pending timeout, restores FLAG_NOT_FOCUSABLE, and clears state.
     *
     * This is the key fix for the "second tap does nothing" bug: without it,
     * any mid-flight or error state left in the SM blocks subsequent reads.
     */
    fun resetToIdle() {
        handler.removeCallbacks(timeoutRunnable)
        if (_state.value != ClipboardFocusState.IDLE) {
            Log.d(TAG, "resetToIdle — forcing ${_state.value} → IDLE")
            // Ensure FLAG_NOT_FOCUSABLE is restored so the overlay doesn't
            // steal input from the underlying app on next tap.
            if (layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE == 0) {
                layoutParams.flags = layoutParams.flags or
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                try {
                    windowManager.updateViewLayout(overlayView, layoutParams)
                } catch (e: Exception) {
                    Log.e(TAG, "resetToIdle flag-restore failed (view not attached?)", e)
                }
            }
            transition(ClipboardFocusState.IDLE)
        }
    }

    fun requestClipboardRead() {
        if (_state.value != ClipboardFocusState.IDLE) {
            Log.w(TAG, "Rejected — current state: ${_state.value}. Forcing reset.")
            // Defensive: if something left us stuck, recover instead of no-op.
            resetToIdle()
            if (_state.value != ClipboardFocusState.IDLE) return
        }

        Log.d(TAG, "requestClipboardRead — starting focus hack")

        // Transition: IDLE → REQUESTING_FOCUS
        transition(ClipboardFocusState.REQUESTING_FOCUS)
        handler.postDelayed(timeoutRunnable, TIMEOUT_MS)

        // Remove FLAG_NOT_FOCUSABLE to acquire focus BEFORE checking clipboard.
        // On Android 13+, clipboard access requires the calling window to have focus.
        layoutParams.flags = layoutParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        try {
            windowManager.updateViewLayout(overlayView, layoutParams)
            Log.d(TAG, "FLAG_NOT_FOCUSABLE removed — requesting focus")
            // Request focus and ensure the view processes the layout change
            overlayView.requestFocus()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update layout for focus", e)
            handler.removeCallbacks(timeoutRunnable)
            restoreFlags()
            deliverResult(null)
            return
        }

        // Delay the clipboard read to allow the WindowManager to actually grant focus.
        // handler.post (~0ms) is NOT enough — the WM needs real time to process the
        // flag change and grant input focus to this window. Without this delay,
        // clipboard reads succeed on the first call (fresh window) but fail on
        // subsequent calls because focus hasn't been re-established yet.
        handler.postDelayed({
            readClipboardAfterFocus()
        }, FOCUS_SETTLE_DELAY_MS)
    }

    private fun readClipboardAfterFocus() {
        // Transition: REQUESTING_FOCUS → READING_CLIPBOARD
        transition(ClipboardFocusState.READING_CLIPBOARD)

        val result = try {
            // Now that we have focus, check clipboard availability
            if (!clipboard.hasPrimaryClip()) {
                Log.d(TAG, "Clipboard empty")
                null
            } else {
                val description = clipboard.primaryClipDescription
                // Accept text/plain, text/html, and any other text/* MIME types.
                // Chrome and many apps copy as text/html. coerceToText() handles
                // converting HTML and other formats to plain text.
                val isTextType = description?.hasMimeType("text/*") == true
                if (!isTextType) {
                    Log.d(TAG, "Non-text MIME: ${description?.getMimeType(0)}")
                    null
                } else {
                    Log.d(TAG, "MIME accepted: ${description?.getMimeType(0)}")
                    val clip = clipboard.primaryClip
                    val text = clip?.getItemAt(0)?.coerceToText(context)?.toString()
                    val isSensitive = description.extras
                        ?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) ?: false

                    if (text.isNullOrBlank()) {
                        Log.d(TAG, "Clipboard text blank")
                        null
                    } else {
                        Log.d(TAG, "Clipboard read OK: ${text.take(50)}...")
                        CapturedContent(
                            text = text,
                            sourcePackage = null,
                            timestamp = System.currentTimeMillis(),
                            isSensitive = isSensitive
                        )
                    }
                }
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
