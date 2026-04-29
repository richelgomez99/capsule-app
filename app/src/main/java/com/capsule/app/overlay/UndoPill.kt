package com.capsule.app.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.capsule.app.data.model.Intent
import kotlinx.coroutines.delay

/**
 * The 10-second undo affordance anchored to the bubble's edge. Unlike
 * [SilentWrapPill] (which is informational), this pill is always-undoable
 * and is shown after BOTH paths (silent wrap or chip-row seal) for the full
 * undo window.
 *
 * UX contract:
 *  - Enters with scale+fade, not slide, so it reads as "attached to bubble".
 *  - Countdown visualized as a **ring drained clockwise** around a small
 *    icon — richer texture than a bar, suits the "always here if you need
 *    me" role.
 *  - Tap → firm haptic + [onUndo]. After [UNDO_WINDOW_MS] → [onExpire]
 *    (fades out).
 */
@Composable
fun UndoPill(
    intent: Intent,
    onUndo: () -> Unit,
    onExpire: () -> Unit,
    modifier: Modifier = Modifier,
    windowMillis: Long = OverlayMotion.UNDO_WINDOW_MS
) {
    val haptics = LocalHapticFeedback.current

    var target by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = windowMillis.toInt(), easing = LinearEasing),
        label = "undoPillProgress"
    )

    LaunchedEffect(Unit) {
        target = 1f
        delay(windowMillis)
        onExpire()
    }

    AnimatedVisibility(
        visible = true,
        enter = scaleIn(OverlayMotion.enterTween(), initialScale = 0.8f) + fadeIn(OverlayMotion.enterTween()),
        exit = scaleOut(OverlayMotion.exitTween(), targetScale = 0.85f) + fadeOut(OverlayMotion.exitTween()),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(6.dp)
                .sizeIn(minHeight = 44.dp)
                .semantics { contentDescription = "Undo saving as ${intent.label()}" }
                .clip(RoundedCornerShape(22.dp))
                .clickable {
                    haptics.performHapticFeedback(
                        androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                    )
                    onUndo()
                },
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CountdownRing(
                    progress = progress,
                    ringColor = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Undo,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = "Undo",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

/**
 * Shown for ~900 ms after a successful undo. Tiny, calm, no action.
 */
@Composable
fun RemovedConfirmationPill(
    onExpire: () -> Unit,
    modifier: Modifier = Modifier,
    visibleMillis: Long = OverlayMotion.REMOVED_CONFIRMATION_MS
) {
    LaunchedEffect(Unit) {
        delay(visibleMillis)
        onExpire()
    }
    AnimatedVisibility(
        visible = true,
        enter = scaleIn(OverlayMotion.enterTween(), initialScale = 0.8f) + fadeIn(OverlayMotion.enterTween()),
        exit = fadeOut(OverlayMotion.exitTween()),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.padding(6.dp),
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.inverseSurface
        ) {
            Text(
                text = "Removed",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

/**
 * Shown if the user taps undo AFTER the 10-second window has passed.
 */
@Composable
fun AlreadyInDiaryPill(
    onExpire: () -> Unit,
    modifier: Modifier = Modifier,
    visibleMillis: Long = 1_600L
) {
    LaunchedEffect(Unit) {
        delay(visibleMillis)
        onExpire()
    }
    AnimatedVisibility(
        visible = true,
        enter = fadeIn(OverlayMotion.enterTween()),
        exit = fadeOut(OverlayMotion.exitTween()),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier.padding(6.dp),
            tonalElevation = 4.dp,
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.inverseSurface
        ) {
            Text(
                text = "Already in your Diary",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.inverseOnSurface
            )
        }
    }
}

@Composable
private fun CountdownRing(
    progress: Float,
    ringColor: androidx.compose.ui.graphics.Color,
    trackColor: androidx.compose.ui.graphics.Color,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier.size(22.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val stroke = 2.5.dp.toPx()
            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke)
            )
            // Drain arc (sweeps clockwise as progress rises 0→1).
            drawArc(
                color = ringColor,
                startAngle = -90f,
                sweepAngle = 360f * (1f - progress.coerceIn(0f, 1f)),
                useCenter = false,
                topLeft = Offset(stroke / 2f, stroke / 2f),
                size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(width = stroke)
            )
        }
        content()
    }
}
