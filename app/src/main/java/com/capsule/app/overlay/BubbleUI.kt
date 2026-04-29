package com.capsule.app.overlay

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * Drag threshold in pixels. Movement below this is considered a tap.
 * Using a generous threshold to distinguish tap from drag reliably.
 */
private const val DRAG_THRESHOLD_PX = 10f

@Composable
fun BubbleUI(
    onTap: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (dx: Int, dy: Int) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableStateOf(false) }

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        FloatingActionButton(
            onClick = { /* Handled via pointerInput below to distinguish tap vs drag */ },
            modifier = Modifier
                .size(56.dp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var totalDrag = Offset.Zero
                        var dragStarted = false

                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break

                            if (change.pressed) {
                                val delta = change.positionChange()
                                totalDrag += delta

                                // Check if drag threshold exceeded
                                if (!dragStarted && (abs(totalDrag.x) > DRAG_THRESHOLD_PX || abs(totalDrag.y) > DRAG_THRESHOLD_PX)) {
                                    dragStarted = true
                                    isDragging = true
                                    onDragStart()
                                }

                                if (dragStarted) {
                                    change.consume()
                                    onDrag(delta.x.toInt(), delta.y.toInt())
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Pointer released
                        if (dragStarted) {
                            isDragging = false
                            onDragEnd()
                        } else {
                            // It was a tap (no significant movement)
                            onTap()
                        }
                    }
                },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ) {
            Icon(
                imageVector = Icons.Default.ContentPaste,
                contentDescription = "Capture clipboard"
            )
        }
    }
}

@Composable
fun DismissTargetUI(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.error
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onError
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val elevation = if (isActive) {
        FloatingActionButtonDefaults.elevation(defaultElevation = 10.dp)
    } else {
        FloatingActionButtonDefaults.elevation(defaultElevation = 4.dp)
    }

    FloatingActionButton(
        onClick = { },
        modifier = modifier.size(72.dp),
        shape = CircleShape,
        containerColor = containerColor,
        contentColor = contentColor,
        elevation = elevation
    ) {
        Icon(
            imageVector = Icons.Default.Close,
            contentDescription = "Dismiss overlay",
            tint = contentColor
        )
    }
}
