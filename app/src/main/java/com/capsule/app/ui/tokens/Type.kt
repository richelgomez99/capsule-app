package com.capsule.app.ui.tokens

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.capsule.app.R

/**
 * Quiet Almanac typography tokens.
 *
 * Phase 0 exposes the font families and Material 3 type factory only;
 * screen-level adoption is gated behind later visual-refit commits.
 */
data class CapsuleType(
    val displaySerif: FontFamily,
    val bodySans: FontFamily,
    val captionMono: FontFamily,
) {
    fun materialTypography(): Typography = Typography(
        displayLarge = TextStyle(
            fontFamily = displaySerif,
            fontWeight = FontWeight.Normal,
            fontSize = 32.sp,
            lineHeight = 38.sp,
            letterSpacing = 0.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = displaySerif,
            fontWeight = FontWeight.Normal,
            fontSize = 24.sp,
            lineHeight = 30.sp,
            letterSpacing = 0.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = displaySerif,
            fontWeight = FontWeight.Normal,
            fontSize = 20.sp,
            lineHeight = 26.sp,
            letterSpacing = 0.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = bodySans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = bodySans,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = captionMono,
            fontWeight = FontWeight.Normal,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.6.sp,
        ),
    )

    companion object {
        val QuietAlmanac = CapsuleType(
            displaySerif = FontFamily(
                Font(
                    resId = R.font.cormorant_garamond_regular,
                    weight = FontWeight.Normal,
                    style = FontStyle.Normal,
                ),
                Font(
                    resId = R.font.cormorant_garamond_italic,
                    weight = FontWeight.Normal,
                    style = FontStyle.Italic,
                ),
            ),
            bodySans = FontFamily(
                Font(
                    resId = R.font.inter_regular,
                    weight = FontWeight.Normal,
                    style = FontStyle.Normal,
                ),
            ),
            captionMono = FontFamily(
                Font(
                    resId = R.font.jetbrains_mono_regular,
                    weight = FontWeight.Normal,
                    style = FontStyle.Normal,
                ),
            ),
        )
    }
}