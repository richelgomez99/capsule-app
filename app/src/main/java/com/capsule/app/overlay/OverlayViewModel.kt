package com.capsule.app.overlay

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import com.capsule.app.service.ClipboardFocusState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * The seam between the overlay UI and the `:ml` process. Implemented by a
 * binder-owning host (typically [com.capsule.app.service.CapsuleOverlayService])
 * in production; fakeable in tests.
 *
 * All methods suspend so the VM doesn't care whether the IPC is already-bound
 * or needs to wait on `bindService`.
 */
interface SealOrchestrator {

    /** Run pre-seal logic (scrubber, intent predictor, silent-wrap predicate) and seal. */
    suspend fun captureAndSeal(content: CapturedContent): SealOutcome

    /**
     * Seal with a user-provided intent decision (from the chip row or timeout).
     * Skips the silent-wrap gate entirely; runs scrub + state snapshot + seal.
     *
     * [intent] = [Intent.AMBIGUOUS] + [source] = [IntentSource.AUTO_AMBIGUOUS]
     * is the chip-row-timeout path. Any other intent with [IntentSource.USER_CHIP]
     * is the chip-row-tapped path.
     */
    suspend fun sealWithChoice(
        content: CapturedContent,
        intent: Intent,
        source: IntentSource
    ): SealOutcome

    /** Best-effort undo within the 10s window. */
    suspend fun undo(envelopeId: String): UndoOutcome
}

/** Result of a single capture-and-seal orchestration. */
sealed class SealOutcome {
    /** User-chip path — show the chip row and seal after user picks. */
    data class RequiresChipRow(val previewText: String) : SealOutcome()

    /** Silent-wrap path — seal already happened; show pill + undo affordance. */
    data class Silent(val envelopeId: String, val intent: Intent) : SealOutcome()

    /** Auto-ambiguous seal (chip row timed out) — show undo pill only. */
    data class AutoAmbiguous(val envelopeId: String) : SealOutcome()

    /** Explicit user-chip seal happened — show undo pill. */
    data class UserChip(val envelopeId: String, val intent: Intent) : SealOutcome()

    /** Capture was blocked (e.g., scrubbed to empty, user permission denied). */
    data class Blocked(val reason: String) : SealOutcome()
}

sealed class UndoOutcome {
    data object Removed : UndoOutcome()
    data object AlreadyInDiary : UndoOutcome()
    data class Failed(val reason: String) : UndoOutcome()
}

class OverlayViewModel : ViewModel() {

    companion object {
        private const val TAG = "OverlayVM"
        /** SC-003: edge-snap animation must complete within 200 ms. */
        private const val SNAP_ANIMATION_DURATION_MS = 180L
        /** Number of frames in the snap animation. 60 fps → 12 frames for 200 ms. */
        private const val SNAP_ANIMATION_FRAMES = 12
    }

    private var snapAnimationJob: Job? = null
    private var postCaptureJob: Job? = null

    private val _bubbleState = MutableStateFlow(BubbleState())
    val bubbleState: StateFlow<BubbleState> = _bubbleState.asStateFlow()

    private val _capturedContent = MutableStateFlow<CapturedContent?>(null)
    val capturedContent: StateFlow<CapturedContent?> = _capturedContent.asStateFlow()

    private val _clipboardFocusState = MutableStateFlow(ClipboardFocusState.IDLE)
    val clipboardFocusState: StateFlow<ClipboardFocusState> = _clipboardFocusState.asStateFlow()

    /** Post-capture UI state (chip row / silent pill / undo pill / confirmations). */
    private val _postCaptureUi = MutableStateFlow<PostCaptureUi>(PostCaptureUi.None)
    val postCaptureUi: StateFlow<PostCaptureUi> = _postCaptureUi.asStateFlow()

    /** Callback set by service to trigger the clipboard focus hack. */
    var onRequestClipboardRead: (() -> Unit)? = null

    /** Callback set by service to persist bubble position. */
    var onBubblePositionChanged: ((x: Int, y: Int, edge: EdgeSide) -> Unit)? = null

    /** Callback set by service when the user dismisses the overlay via drag target. */
    var onDismissRequested: (() -> Unit)? = null

    /** IPC seam; service installs a real impl on bind, null when not bound. */
    var sealOrchestrator: SealOrchestrator? = null

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
        snapAnimationJob?.cancel()
        _bubbleState.value = _bubbleState.value.copy(
            isDragging = true,
            isDismissTargetVisible = true,
            isOverDismissTarget = false
        )
    }

    fun onBubbleDrag(
        dx: Int,
        dy: Int,
        screenWidth: Int,
        screenHeight: Int,
        bubbleSizePx: Int,
        dismissTargetMetrics: DismissTargetMetrics?
    ) {
        val current = _bubbleState.value
        val newX = (current.x + dx).coerceIn(0, (screenWidth - bubbleSizePx).coerceAtLeast(0))
        val newY = (current.y + dy).coerceIn(0, (screenHeight - bubbleSizePx).coerceAtLeast(0))
        val bubbleCenterX = newX + (bubbleSizePx / 2)
        val bubbleCenterY = newY + (bubbleSizePx / 2)
        val isOverDismissTarget = dismissTargetMetrics?.let { target ->
            hypot(
                (bubbleCenterX - target.centerX).toDouble(),
                (bubbleCenterY - target.centerY).toDouble()
            ) <= target.activationRadiusPx.toDouble()
        } ?: false

        _bubbleState.value = current.copy(
            x = newX,
            y = newY,
            isOverDismissTarget = isOverDismissTarget
        )
    }

    fun onBubbleDragEnd(screenWidth: Int, bubbleWidthPx: Int) {
        val current = _bubbleState.value

        if (current.isOverDismissTarget) {
            _bubbleState.value = current.copy(
                isDragging = false,
                isDismissTargetVisible = false,
                isOverDismissTarget = false
            )
            onDismissRequested?.invoke()
            return
        }

        val edgeSide = if (current.x + bubbleWidthPx / 2 < screenWidth / 2) EdgeSide.LEFT else EdgeSide.RIGHT
        val targetX = if (edgeSide == EdgeSide.LEFT) 0 else (screenWidth - bubbleWidthPx)
        val startX = current.x

        // End drag immediately so BubbleUI knows the gesture is over.
        _bubbleState.value = current.copy(
            isDragging = false,
            edgeSide = edgeSide,
            isDismissTargetVisible = false,
            isOverDismissTarget = false
        )

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
        postCaptureJob?.cancel()
        super.onCleared()
    }

    /**
     * Kick off the full seal orchestration (scrubber → predictor → predicate
     * → seal). Called when the user takes an explicit action in the sheet
     * (legacy button), or automatically when [onClipboardReadResult] fires.
     */
    fun onSaveCapture() {
        val content = _capturedContent.value ?: return
        _capturedContent.value = null
        _bubbleState.value = _bubbleState.value.copy(expansion = ExpansionState.COLLAPSED)
        launchSealOrchestration(content)
    }

    fun onDiscardCapture() {
        _capturedContent.value = null
        pendingChipContent = null
        _bubbleState.value = _bubbleState.value.copy(expansion = ExpansionState.COLLAPSED)
        _postCaptureUi.value = PostCaptureUi.None
    }

    // --- Post-capture orchestration ---

    /**
     * Stashes the full scrubbed content between [launchSealOrchestration]
     * returning [SealOutcome.RequiresChipRow] and the user tapping a chip
     * (or the 2s timeout). Without this, [onChipTapped] / [onChipRowTimeout]
     * would have no text to seal and would blocked-empty in the orchestrator.
     */
    private var pendingChipContent: CapturedContent? = null

    private fun launchSealOrchestration(content: CapturedContent) {
        postCaptureJob?.cancel()
        postCaptureJob = viewModelScope.launch {
            val orchestrator = sealOrchestrator
            if (orchestrator == null) {
                Log.w(TAG, "No SealOrchestrator bound — legacy logcat save only")
                Log.d(
                    "CapsuleCapture",
                    "SAVED | text=${content.text} | source=${content.sourcePackage} " +
                        "| ts=${content.timestamp} | sensitive=${content.isSensitive}"
                )
                return@launch
            }
            val outcome = runCatching { orchestrator.captureAndSeal(content) }
                .getOrElse { t ->
                    Log.e(TAG, "Seal orchestration failed", t)
                    SealOutcome.Blocked(t.message ?: "unknown")
                }
            if (outcome is SealOutcome.RequiresChipRow) {
                pendingChipContent = content
            }
            handleSealOutcome(outcome, content)
        }
    }

    private fun handleSealOutcome(outcome: SealOutcome, content: CapturedContent) {
        when (outcome) {
            is SealOutcome.RequiresChipRow -> {
                _postCaptureUi.value = PostCaptureUi.ChipRow(
                    previewText = outcome.previewText.ifBlank { content.text },
                    startedAtMillis = System.currentTimeMillis()
                )
            }
            is SealOutcome.Silent -> {
                _postCaptureUi.value = PostCaptureUi.SilentWrapPill(
                    intent = outcome.intent,
                    envelopeId = outcome.envelopeId,
                    startedAtMillis = System.currentTimeMillis()
                )
            }
            is SealOutcome.UserChip -> {
                _postCaptureUi.value = PostCaptureUi.UndoPill(
                    envelopeId = outcome.envelopeId,
                    intent = outcome.intent,
                    sealedAtMillis = System.currentTimeMillis(),
                    sealedIntentSource = IntentSource.USER_CHIP.name
                )
            }
            is SealOutcome.AutoAmbiguous -> {
                _postCaptureUi.value = PostCaptureUi.UndoPill(
                    envelopeId = outcome.envelopeId,
                    intent = Intent.AMBIGUOUS,
                    sealedAtMillis = System.currentTimeMillis(),
                    sealedIntentSource = IntentSource.AUTO_AMBIGUOUS.name
                )
            }
            is SealOutcome.Blocked -> {
                // Soft fail — nothing user-visible beyond keeping the sheet closed.
                _postCaptureUi.value = PostCaptureUi.None
            }
        }
    }

    /** Invoked when the user taps a chip in the chip row. */
    fun onChipTapped(intent: Intent) {
        val content = pendingChipContent ?: run {
            Log.w(TAG, "onChipTapped but no pendingChipContent — ignoring")
            _postCaptureUi.value = PostCaptureUi.None
            return
        }
        pendingChipContent = null
        postCaptureJob?.cancel()
        postCaptureJob = viewModelScope.launch {
            val orchestrator = sealOrchestrator ?: return@launch
            val outcome = runCatching {
                orchestrator.sealWithChoice(content, intent, IntentSource.USER_CHIP)
            }.getOrElse {
                Log.e(TAG, "sealWithChoice (USER_CHIP) failed", it)
                SealOutcome.Blocked(it.message ?: "unknown")
            }
            handleSealOutcome(outcome, content)
        }
    }

    /** Invoked when the chip row auto-dismisses (2s timeout). */
    fun onChipRowTimeout() {
        val content = pendingChipContent ?: run {
            _postCaptureUi.value = PostCaptureUi.None
            return
        }
        pendingChipContent = null
        postCaptureJob?.cancel()
        postCaptureJob = viewModelScope.launch {
            val orchestrator = sealOrchestrator ?: run {
                _postCaptureUi.value = PostCaptureUi.None
                return@launch
            }
            val outcome = runCatching {
                orchestrator.sealWithChoice(content, Intent.AMBIGUOUS, IntentSource.AUTO_AMBIGUOUS)
            }.getOrElse {
                Log.e(TAG, "sealWithChoice (AUTO_AMBIGUOUS) failed", it)
                SealOutcome.Blocked(it.message ?: "unknown")
            }
            handleSealOutcome(outcome, content)
        }
    }

    /** Silent pill tapped for undo, OR undo pill tapped. */
    fun onUndoTapped(envelopeId: String) {
        postCaptureJob?.cancel()
        postCaptureJob = viewModelScope.launch {
            val orchestrator = sealOrchestrator ?: run {
                _postCaptureUi.value = PostCaptureUi.None
                return@launch
            }
            val outcome = runCatching { orchestrator.undo(envelopeId) }
                .getOrElse { UndoOutcome.Failed(it.message ?: "unknown") }
            when (outcome) {
                UndoOutcome.Removed -> _postCaptureUi.value = PostCaptureUi.RemovedConfirmation
                UndoOutcome.AlreadyInDiary -> _postCaptureUi.value = PostCaptureUi.AlreadyInDiary
                is UndoOutcome.Failed -> _postCaptureUi.value = PostCaptureUi.AlreadyInDiary
            }
        }
    }

    /** The silent-wrap pill finished its 2s display without being tapped. */
    fun onSilentWrapPillExpired() {
        val current = _postCaptureUi.value
        if (current is PostCaptureUi.SilentWrapPill) {
            // Transition into the 10s undo window; the silent pill counted
            // against that window, so the remainder here is 10s - 2s = 8s.
            // Simplification for v1: full 10s window from pill-expire moment.
            _postCaptureUi.value = PostCaptureUi.UndoPill(
                envelopeId = current.envelopeId,
                intent = current.intent,
                sealedAtMillis = current.startedAtMillis,
                sealedIntentSource = IntentSource.PREDICTED_SILENT.name
            )
        }
    }

    /** The undo pill finished its 10s window without being tapped. */
    fun onUndoPillExpired() {
        _postCaptureUi.value = PostCaptureUi.None
    }

    /** The "Removed" / "Already in Diary" confirmation finished. */
    fun onConfirmationExpired() {
        _postCaptureUi.value = PostCaptureUi.None
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

