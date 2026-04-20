package com.capsule.app.overlay

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
