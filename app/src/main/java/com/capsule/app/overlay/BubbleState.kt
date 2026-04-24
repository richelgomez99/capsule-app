package com.capsule.app.overlay

import com.capsule.app.data.model.Intent

/**
 * Bubble position, expansion, and drag state.
 * Persisted fields: x, y, edgeSide (SharedPreferences).
 */
data class BubbleState(
    val x: Int = 0,
    val y: Int = 100,
    val expansion: ExpansionState = ExpansionState.COLLAPSED,
    val isDragging: Boolean = false,
    val edgeSide: EdgeSide = EdgeSide.LEFT,
    val isDismissTargetVisible: Boolean = false,
    val isOverDismissTarget: Boolean = false
)

enum class ExpansionState {
    COLLAPSED,
    EXPANDED
}

enum class EdgeSide {
    LEFT, RIGHT
}

/**
 * Content captured from the clipboard on user tap.
 * Phase 1: "save" writes to Logcat only — no database.
 */
data class CapturedContent(
    val text: String,
    val sourcePackage: String?,
    val timestamp: Long,
    val isSensitive: Boolean
)

data class DismissTargetMetrics(
    val centerX: Int,
    val centerY: Int,
    val activationRadiusPx: Int
)

/**
 * Post-capture orchestration state — the three moments after a seal has been
 * triggered (spec 002 US1 / FR-004). Mutually exclusive: at most one of these
 * is visible at any time.
 *
 *  - [None]: no post-capture UI is showing.
 *  - [ChipRow]: AI was unsure → user picks one of 4 intents, 2s countdown,
 *    auto-dismiss → silent seal with [Intent.AMBIGUOUS].
 *  - [SilentWrapPill]: AI was confident + had precedent → seal already
 *    happened; show "Saved as {intent} · Undo" pill for 2s.
 *  - [UndoPill]: 10-second undo affordance anchored to the bubble edge.
 *    Visible whenever a seal is in its undo window.
 *  - [RemovedConfirmation]: shown for ~900 ms after a successful undo.
 */
sealed class PostCaptureUi {
    data object None : PostCaptureUi()

    data class ChipRow(
        val previewText: String,
        val startedAtMillis: Long
    ) : PostCaptureUi()

    data class SilentWrapPill(
        val intent: Intent,
        val envelopeId: String,
        val startedAtMillis: Long
    ) : PostCaptureUi()

    data class UndoPill(
        val envelopeId: String,
        val intent: Intent,
        val sealedAtMillis: Long,
        val sealedIntentSource: String
    ) : PostCaptureUi()

    data object RemovedConfirmation : PostCaptureUi()

    data object AlreadyInDiary : PostCaptureUi()
}

