package com.capsule.app.ui.primitives

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.capsule.app.ui.tokens.CapsulePalette
import kotlin.math.cos
import kotlin.math.sin

/**
 * **AgentVoiceMark** — the ✦ six-pointed-star glyph that signals
 * "the agent is speaking." Locked /autoplan 2026-04-26 per spec 010
 * FR-010-019 + D4.
 *
 * Rendered via [Canvas] + [Path] (no font fallback) so the glyph is
 * pixel-stable across devices, identical mechanism to `WaxSeal`.
 *
 * **Reserved usage**: this composable may only be referenced from
 * files in the agent-voice surface allow-list. The lint detector
 * `NoAgentVoiceMarkOutsideAgentSurfaces` (issue id
 * `OrbitNoAgentVoiceMarkOutsideAgentSurfaces`) fails the build on any
 * other call site. The initial allow-list is `ClusterSuggestionCard.kt`.
 *
 * Color: [CapsulePalette.Tokens.brandAccent] per spec 015 LD-001.
 */
@Composable
fun AgentVoiceMark(
    modifier: Modifier = Modifier,
    size: TextUnit = 14.sp,
    tint: Color? = null,
) {
    val palette = CapsulePalette.current(dark = isSystemInDarkTheme())
    val color = tint ?: palette.brandAccent
    val sizeDp: Dp = with(LocalDensity.current) { size.toDp() }

    Canvas(modifier = modifier.size(sizeDp)) {
        drawSixPointStar(color)
    }
}

/**
 * Draw a six-pointed star (✦) — two interlocking equilateral
 * triangles approximated as a 12-vertex polygon with alternating
 * outer/inner radii. Stroke-free, ink-filled, oriented with the top
 * point straight up to match the Unicode glyph.
 */
private fun DrawScope.drawSixPointStar(color: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val outer = minOf(size.width, size.height) / 2f
    val inner = outer * 0.42f // tuned for ✦ visual balance

    val path = Path().apply {
        for (i in 0 until 12) {
            val isOuter = i % 2 == 0
            val r = if (isOuter) outer else inner
            val angle = (-Math.PI / 2.0) + (i * Math.PI / 6.0)
            val x = cx + (r * cos(angle)).toFloat()
            val y = cy + (r * sin(angle)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    drawPath(path = path, color = color)
}
