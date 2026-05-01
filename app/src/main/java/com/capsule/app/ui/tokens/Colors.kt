package com.capsule.app.ui.tokens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/**
 * Capsule design tokens — palette layer.
 *
 * These are the low-level "ink" colors used by primitives. The two
 * palettes (light + dark) are mirrors of each other in lightness so
 * intent renders identically on both per spec 010.
 *
 * Token mapping to spec 010 design.md:
 *  - `--paper`            → page background
 *  - `--ink`              → primary text + drawn glyphs (wax seals)
 *  - `--ink-strong`       → emphasis text
 *  - `--ink-faint`        → captions, citations, timestamps (Berkeley Mono 10 sp)
 *  - `--rule`             → 1 px hairline dividers
 *  - `--brand-accent`     → Quiet Almanac amber accent (#e8b06a)
 *  - `--brand-accent-dim` → 16% amber wash
 *  - `--brand-accent-ink` → text/icon ink on amber fills
 *  - `--ink-accent-cluster` → AgentVoiceMark glyph **only** (FR-010-019,
 *    locked /autoplan 2026-04-26 — see [com.capsule.app.ui.primitives.AgentVoiceMark]).
 *    Lint detector `NoAgentVoiceMarkOutsideAgentSurfaces` enforces
 *    that this token is used exclusively inside the agent-voice
 *    surface allow-list.
 */
object CapsulePalette {

    data class Tokens(
        val paper: Color,
        val ink: Color,
        val inkStrong: Color,
        val inkFaint: Color,
        val rule: Color,
        val brandAccent: Color,
        val brandAccentDim: Color,
        val brandAccentInk: Color,
        val inkAccentCluster: Color,
    )

    val Light: Tokens = Tokens(
        paper = Color(0xFFFAF8F4),          // warm paper
        ink = Color(0xFF1A1A1A),            // near-black ink
        inkStrong = Color(0xFF000000),
        inkFaint = Color(0xFF8C8C8C),
        rule = Color(0xFFD9D6D0),
        brandAccent = Color(0xFFE8B06A),
        brandAccentDim = Color(0x29E8B06A),
        brandAccentInk = Color(0xFF1A1206),
        // --ink-accent-cluster: a muted indigo, distinguishable from
        // ink without shouting. Tonally compatible with the wax-seal
        // ink vocabulary per spec 010 D4.
        inkAccentCluster = Color(0xFF3D4A6B),
    )

    val Dark: Tokens = Tokens(
        paper = Color(0xFF121212),
        ink = Color(0xFFE8E6E0),
        inkStrong = Color(0xFFFFFFFF),
        inkFaint = Color(0xFF7C7A75),
        rule = Color(0xFF2A2A2A),
        brandAccent = Color(0xFFE8B06A),
        brandAccentDim = Color(0x29E8B06A),
        brandAccentInk = Color(0xFF1A1206),
        inkAccentCluster = Color(0xFF8FA3CC),
    )

    @Composable
    @ReadOnlyComposable
    fun current(dark: Boolean): Tokens = if (dark) Dark else Light
}
