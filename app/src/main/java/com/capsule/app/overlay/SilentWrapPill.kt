package com.capsule.app.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.capsule.app.data.model.Intent
import kotlinx.coroutines.delay

/**
 * The "magic moment" pill shown when [com.capsule.app.ai.SilentWrapPredicate]
 * returned `SilentWrap(intent)`. The seal has ALREADY happened — this pill is
 * informational + undoable.
 *
 * UX contract (April 2026 polish):
 *  - Slides in horizontally from the bubble's edge (not center-screen).
 *  - Frosted surface (92 %) with tonal elevation — glass effect without
 *    relying on `RenderEffect.blur` which is flaky on overlay windows.
 *  - Label: "Saved as {intent} · Undo" with a subtle check-circle glyph.
 *  - 2-second draining bar as the pill's top edge.
 *  - Tap "Undo" → [onUndo]; after timeout → [onExpire].
 */
@Composable
fun SilentWrapPill(
    intent: Intent,
    onUndo: () -> Unit,
    onExpire: () -> Unit,
    modifier: Modifier = Modifier,
    visibleMillis: Long = OverlayMotion.SILENT_WRAP_PILL_MS,
    fromLeadingEdge: Boolean = true
) {
    val haptics = LocalHapticFeedback.current

    var target by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = visibleMillis.toInt(), easing = LinearEasing),
        label = "silentWrapProgress"
    )

    LaunchedEffect(Unit) {
        target = 1f
        delay(visibleMillis)
        onExpire()
    }

    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(
            initialOffsetX = { full -> if (fromLeadingEdge) -full else full },
            animationSpec = OverlayMotion.enterTween()
        ) + fadeIn(OverlayMotion.enterTween()),
        exit = slideOutHorizontally(
            targetOffsetX = { full -> if (fromLeadingEdge) -full else full },
            animationSpec = OverlayMotion.exitTween()
        ) + fadeOut(OverlayMotion.exitTween()),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .padding(8.dp)
                .sizeIn(minHeight = 52.dp, maxWidth = 340.dp)
                .semantics { contentDescription = "Saved as ${intent.label()}. Tap to undo." },
            tonalElevation = 6.dp,
            shadowElevation = 12.dp,
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
        ) {
            Column {
                // Drain bar — top edge, matches bubble edge side visually.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp)
                        .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction = 1f - progress.coerceIn(0f, 1f))
                            .height(2.dp)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                    )
                    Text(
                        text = "Saved as ${intent.label()}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Spacer(Modifier.width(4.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .sizeIn(minHeight = 36.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                haptics.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove
                                )
                                onUndo()
                            }
                    ) {
                        Text(
                            text = "Undo",
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

/** Human label for an [Intent] used in UI pills/toasts. */
internal fun Intent.label(): String = when (this) {
    Intent.WANT_IT -> "Want it"
    Intent.REFERENCE -> "Reference"
    Intent.FOR_SOMEONE -> "For someone"
    Intent.INTERESTING -> "Interesting"
    Intent.AMBIGUOUS -> "Ambiguous"
}
