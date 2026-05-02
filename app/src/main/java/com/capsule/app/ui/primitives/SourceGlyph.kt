package com.capsule.app.ui.primitives

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capsule.app.ui.tokens.CapsulePalette
import com.capsule.app.ui.tokens.CapsuleType

@Immutable
enum class SourceGlyphKind(val label: String) {
    safari("S"),
    twitter("X"),
    podcasts("♪"),
    notes("✎"),
    instagram("◎"),
    sms("✉"),
    chrome("C"),
    gmail("M"),
    photos("◐"),
    youtube("▶"),
    nyt("T"),
    substack("S"),
    files("◫"),
    tiktok("T"),
    url("↗"),
    share("↗"),
}

@Composable
fun SourceGlyph(
    kind: SourceGlyphKind,
    modifier: Modifier = Modifier,
    size: Dp = 22.dp,
) {
    val palette = CapsulePalette.current(dark = isSystemInDarkTheme())
    val fill = kind.fill(palette)
    val backgroundModifier = when (fill) {
        is SourceGlyphFill.Solid -> Modifier.background(fill.color, CircleShape)
        is SourceGlyphFill.Gradient -> Modifier.drawWithContent {
            drawCircle(brush = fill.brush)
            drawContent()
        }
    }

    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .then(backgroundModifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = kind.label,
            color = Color.White,
            style = TextStyle(
                fontFamily = CapsuleType.QuietAlmanac.bodySans,
                fontSize = (size.value * 0.5f).sp,
                lineHeight = (size.value * 0.5f).sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.sp,
            ),
        )
    }
}

private sealed interface SourceGlyphFill {
    data class Solid(val color: Color) : SourceGlyphFill
    data class Gradient(val brush: Brush) : SourceGlyphFill
}

private fun SourceGlyphKind.fill(palette: CapsulePalette.Tokens): SourceGlyphFill = when (this) {
    SourceGlyphKind.safari -> SourceGlyphFill.Solid(Color(0xFF1D6FE6))
    SourceGlyphKind.twitter -> SourceGlyphFill.Solid(Color(0xFF000000))
    SourceGlyphKind.podcasts -> SourceGlyphFill.Solid(Color(0xFF7C3AFF))
    SourceGlyphKind.notes -> SourceGlyphFill.Solid(Color(0xFFFBBF24))
    SourceGlyphKind.instagram -> SourceGlyphFill.Gradient(
        Brush.linearGradient(
            listOf(
                Color(0xFF833AB4),
                Color(0xFFE1306C),
                Color(0xFFF77737),
                Color(0xFFFCAF45),
            ),
        ),
    )
    SourceGlyphKind.sms -> SourceGlyphFill.Solid(Color(0xFF34C759))
    SourceGlyphKind.chrome -> SourceGlyphFill.Solid(Color(0xFF1A73E8))
    SourceGlyphKind.gmail -> SourceGlyphFill.Solid(Color(0xFFEA4335))
    SourceGlyphKind.photos -> SourceGlyphFill.Solid(Color(0xFFFBBF24))
    SourceGlyphKind.youtube -> SourceGlyphFill.Solid(Color(0xFFFF0000))
    SourceGlyphKind.nyt -> SourceGlyphFill.Solid(Color(0xFF000000))
    SourceGlyphKind.substack -> SourceGlyphFill.Solid(Color(0xFFFF6719))
    SourceGlyphKind.files -> SourceGlyphFill.Solid(Color(0xFF34C759))
    SourceGlyphKind.tiktok -> SourceGlyphFill.Solid(Color(0xFFFE2C55))
    SourceGlyphKind.url -> SourceGlyphFill.Solid(palette.inkFaint)
    SourceGlyphKind.share -> SourceGlyphFill.Solid(palette.inkFaint)
}

@Preview(name = "sources - light")
@Preview(name = "sources - dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun SourceGlyphPreview() {
    Row(
        modifier = Modifier.padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        SourceGlyphKind.entries.forEach { kind ->
            SourceGlyph(kind = kind)
        }
    }
}