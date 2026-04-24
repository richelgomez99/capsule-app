package com.capsule.app.overlay

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.capsule.app.data.model.Intent
import kotlinx.coroutines.delay

/**
 * The 4-chip row shown when the silent-wrap predicate returned `ShowChipRow`.
 *
 * UX contract (spec.md FR-004, research.md §Chip Row UX, April 2026 polish):
 *  - 4 chips horizontally: Want it, Reference, For someone, Interesting.
 *  - 2-second hairline countdown bar drains left-to-right across the top.
 *  - Tap: firm haptic + chip scales to 0.94 + invoke [onChipTap] (which
 *    collapses and seals via USER_CHIP).
 *  - Timeout: silent [Intent.AMBIGUOUS] seal via [onTimeout].
 *  - At T-500 ms, a subtle tick haptic hints at the impending auto-dismiss.
 *  - Layout respects min 48dp touch targets per Material 3 accessibility.
 */
@Composable
fun ChipRow(
    previewText: String,
    onChipTap: (Intent) -> Unit,
    onTimeout: () -> Unit,
    modifier: Modifier = Modifier,
    countdownMillis: Long = OverlayMotion.CHIP_COUNTDOWN_MS
) {
    val haptics = LocalHapticFeedback.current

    // Countdown progress: 1.0 → 0.0 over `countdownMillis`.
    var elapsed by remember { mutableFloatStateOf(0f) }
    val progress by animateFloatAsState(
        targetValue = elapsed,
        animationSpec = tween(durationMillis = countdownMillis.toInt(), easing = LinearEasing),
        label = "chipRowCountdown"
    )

    LaunchedEffect(Unit) {
        elapsed = 1f
        // Warning tick at T-500ms
        delay(countdownMillis - 500L)
        // Note: HapticFeedback in Compose is limited; lean on the VM for
        // the real tick via HapticFeedbackConstants.CLOCK_TICK. This
        // composable only triggers the terminal timeout.
        delay(500L)
        onTimeout()
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.padding(top = 2.dp)) {
            // Hairline countdown bar (drains left → right as `progress` rises).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction = 1f - progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Preview — single line, ellipsized, soft foreground.
            Text(
                text = previewText.take(80),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Spacer(Modifier.height(10.dp))

            // 4 chips, equal width, equal spacing.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IntentChip(
                    intent = Intent.WANT_IT,
                    label = "Want it",
                    icon = Icons.Filled.Favorite,
                    modifier = Modifier.weight(1f),
                    onTap = { onChipTap(Intent.WANT_IT) }
                )
                IntentChip(
                    intent = Intent.REFERENCE,
                    label = "Reference",
                    icon = Icons.Filled.Bookmark,
                    modifier = Modifier.weight(1f),
                    onTap = { onChipTap(Intent.REFERENCE) }
                )
                IntentChip(
                    intent = Intent.FOR_SOMEONE,
                    label = "For someone",
                    icon = Icons.AutoMirrored.Filled.Send,
                    modifier = Modifier.weight(1f),
                    onTap = { onChipTap(Intent.FOR_SOMEONE) }
                )
                IntentChip(
                    intent = Intent.INTERESTING,
                    label = "Interesting",
                    icon = Icons.Filled.AutoAwesome,
                    modifier = Modifier.weight(1f),
                    onTap = { onChipTap(Intent.INTERESTING) }
                )
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun IntentChip(
    intent: Intent,
    label: String,
    icon: ImageVector,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = OverlayMotion.pressSpring(),
        label = "chipPressScale"
    )
    val haptics = LocalHapticFeedback.current

    val containerColor = MaterialTheme.colorScheme.secondaryContainer
    val contentColor = MaterialTheme.colorScheme.onSecondaryContainer

    Surface(
        modifier = modifier
            .sizeIn(minHeight = 48.dp) // a11y touch target
            .wrapContentHeight()
            .scale(scale)
            .semantics { contentDescription = "$label intent" }
            .clip(RoundedCornerShape(14.dp))
            .clickable(
                interactionSource = interaction,
                indication = null
            ) {
                haptics.performHapticFeedback(
                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress
                )
                onTap()
            },
        color = containerColor,
        tonalElevation = 1.dp,
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null, // label provides it
                tint = contentColor,
                modifier = Modifier.sizeIn(minWidth = 20.dp, minHeight = 20.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
