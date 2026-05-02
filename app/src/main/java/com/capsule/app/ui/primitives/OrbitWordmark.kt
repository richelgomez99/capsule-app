package com.capsule.app.ui.primitives

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capsule.app.ui.tokens.CapsulePalette
import com.capsule.app.ui.tokens.CapsuleType

@Composable
fun OrbitWordmark(
    modifier: Modifier = Modifier,
    height: Dp = 28.dp,
    ink: Color? = null,
    accent: Color? = null,
    mono: Boolean = false,
) {
    val palette = CapsulePalette.current(dark = isSystemInDarkTheme())
    val resolvedInk = ink ?: palette.ink
    val resolvedAccent = if (mono) resolvedInk else accent ?: palette.brandAccent
    val fontSize = with(LocalDensity.current) { height.toSp() }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OrbitMark(
            size = height * 1.15f,
            ink = resolvedInk,
            accent = resolvedAccent,
            mono = mono,
        )
        Text(
            text = buildAnnotatedString {
                append("Orbit")
                withStyle(SpanStyle(color = resolvedAccent)) {
                    append(".")
                }
            },
            modifier = Modifier.padding(start = height * 0.35f),
            color = resolvedInk,
            style = TextStyle(
                fontFamily = CapsuleType.QuietAlmanac.displaySerif,
                fontSize = fontSize,
                lineHeight = fontSize,
                fontWeight = FontWeight.Normal,
                letterSpacing = 0.sp,
            ),
        )
    }
}

@Preview(name = "orbit wordmark")
@Composable
private fun OrbitWordmarkPreview() {
    Box(modifier = Modifier.padding(16.dp)) {
        OrbitWordmark()
    }
}