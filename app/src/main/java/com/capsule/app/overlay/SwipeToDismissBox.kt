package com.capsule.app.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import kotlinx.coroutines.launch

/**
 * Lightweight horizontal swipe-to-dismiss wrapper for the post-capture
 * pills. If the user drags the pill past [thresholdDp] in either direction
 * (or flings it), [onDismiss] fires and the pill animates out. Smaller
 * drags snap back to zero.
 *
 * The gesture lives at the overlay window level rather than in each pill
 * so chip row, silent-wrap, undo, and confirmations all inherit identical
 * behaviour for free.
 */
@Composable
fun SwipeToDismissBox(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    thresholdDp: Int = 96,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val thresholdPx = with(density) { thresholdDp.dp.toPx() }
    val offsetX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var dismissed = remember { false }

    // Reset on recomposition when the hosted pill identity changes (the
    // caller keys recomposition via AnimatedContent). A fresh instance of
    // this composable starts with offset=0 by construction, so no manual
    // reset is needed — `Animatable(0f)` is the initial value.

    Box(
        modifier = modifier
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        scope.launch {
                            if (offsetX.value.absoluteValue > thresholdPx && !dismissed) {
                                dismissed = true
                                onDismiss()
                            } else {
                                offsetX.animateTo(0f)
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch { offsetX.animateTo(0f) }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch { offsetX.snapTo(offsetX.value + dragAmount) }
                    }
                )
            }
            .graphicsLayer {
                translationX = offsetX.value
                // Fade as the pill slides off; min 0.25 so it stays
                // visually anchored while partially swiped.
                alpha = (1f - (offsetX.value.absoluteValue / (thresholdPx * 2))).coerceIn(0.25f, 1f)
            }
    ) {
        content()
    }
}
