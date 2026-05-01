package com.capsule.app.ui.primitives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.capsule.app.ui.tokens.CapsulePalette

@Composable
fun OrbitMark(
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    ink: Color? = null,
    accent: Color? = null,
    mono: Boolean = false,
) {
    val palette = CapsulePalette.current(dark = isSystemInDarkTheme())
    val resolvedInk = ink ?: palette.ink
    val resolvedAccent = if (mono) resolvedInk else accent ?: palette.brandAccent

    Canvas(modifier = modifier.size(size)) {
        val scale = minOf(this.size.width, this.size.height) / ViewportSize
        fun s(value: Float): Float = value * scale

        val ellipsePath = Path().apply {
            addOval(
                Rect(
                    offset = Offset(s(6f), s(19f)),
                    size = Size(s(52f), s(26f)),
                ),
            )
        }

        rotate(degrees = -22f, pivot = Offset(s(32f), s(32f))) {
            drawPath(
                path = ellipsePath,
                color = resolvedInk,
                alpha = if (mono) 0.4f else 0.55f,
                style = Stroke(width = s(1.4f)),
            )
        }
        drawCircle(
            color = resolvedAccent,
            radius = s(3f),
            center = Offset(s(55f), s(22f)),
        )
        drawCircle(
            color = resolvedInk,
            radius = s(6.5f),
            center = Offset(s(32f), s(32f)),
        )
    }
}

private const val ViewportSize = 64f

@Preview(name = "orbit mark")
@Composable
private fun OrbitMarkPreview() {
    Box(modifier = Modifier.padding(16.dp)) {
        OrbitMark()
    }
}