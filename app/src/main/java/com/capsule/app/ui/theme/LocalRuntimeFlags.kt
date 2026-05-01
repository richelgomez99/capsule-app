package com.capsule.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf

@Immutable
data class RuntimeFlagValues(
    val useNewVisualLanguage: Boolean = false,
)

val LocalRuntimeFlags = staticCompositionLocalOf { RuntimeFlagValues() }