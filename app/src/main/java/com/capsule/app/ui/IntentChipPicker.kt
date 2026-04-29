package com.capsule.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.capsule.app.data.model.Intent

/**
 * T051 — reusable 4-chip intent picker used both by the overlay (via a
 * countdown wrapper) and by the diary (tap-to-reassign). No countdown, no
 * timeout — callers compose those behaviours around this if they need them.
 *
 * [currentIntent] is visually highlighted so the user can see which one is
 * the envelope's *current* intent before reassigning. Pass `null` for the
 * overlay (first-time assignment).
 */
@Composable
fun IntentChipPicker(
    onPick: (Intent) -> Unit,
    modifier: Modifier = Modifier,
    currentIntent: Intent? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        IntentChip(
            intent = Intent.WANT_IT,
            label = "Want it",
            icon = Icons.Filled.Favorite,
            selected = currentIntent == Intent.WANT_IT,
            modifier = Modifier.weight(1f),
            onTap = { onPick(Intent.WANT_IT) }
        )
        IntentChip(
            intent = Intent.REFERENCE,
            label = "Reference",
            icon = Icons.Filled.Bookmark,
            selected = currentIntent == Intent.REFERENCE,
            modifier = Modifier.weight(1f),
            onTap = { onPick(Intent.REFERENCE) }
        )
        IntentChip(
            intent = Intent.FOR_SOMEONE,
            label = "For someone",
            icon = Icons.AutoMirrored.Filled.Send,
            selected = currentIntent == Intent.FOR_SOMEONE,
            modifier = Modifier.weight(1f),
            onTap = { onPick(Intent.FOR_SOMEONE) }
        )
        IntentChip(
            intent = Intent.INTERESTING,
            label = "Interesting",
            icon = Icons.Filled.AutoAwesome,
            selected = currentIntent == Intent.INTERESTING,
            modifier = Modifier.weight(1f),
            onTap = { onPick(Intent.INTERESTING) }
        )
    }
}

@Composable
private fun IntentChip(
    intent: Intent,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        label = "chipPressScale"
    )
    val haptics = LocalHapticFeedback.current

    val containerColor = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = modifier
            .sizeIn(minHeight = 48.dp)
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
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}
