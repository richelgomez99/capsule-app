package com.capsule.app.diary.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capsule.app.ui.primitives.AgentVoiceMark
import com.capsule.app.ui.primitives.ClusterAction
import com.capsule.app.ui.primitives.ClusterActionRow
import com.capsule.app.ui.tokens.CapsulePalette

/**
 * **ClusterSuggestionCard** — Phase 11 Block 8 / T145.
 *
 * Stateless presentational composable that renders the v1
 * cluster-suggestion surface in the Diary, per spec 010 FR-010-018
 * through FR-010-024. Layout, typography hierarchy, and structural
 * rhythm follow the design canvas (`orbit-screen-diary.jsx`); colors
 * + fonts come from the existing token / theme layer (no amber, no
 * Cormorant — the visual-refit branch handles that sweep).
 *
 * The card is dumb — the parent (DiaryViewModel + DiaryScreen,
 * Block 9 / T148-T149) maps the data-layer [com.capsule.app.data.ClusterCardModel]
 * + cluster lifecycle events into a [ClusterSuggestionCardState] and
 * passes it in. The card renders + emits intent callbacks; it never
 * reaches into the repository or the summariser itself.
 *
 * Reserved primitives composed:
 *  - [AgentVoiceMark] — the ✦ glyph (lint allow-list includes this file).
 *  - [ClusterActionRow] — Geist 14 sp action labels with `│` hairlines.
 *
 * Reduce-motion: the ACTING ellipsis cycle is the only animation; it
 * is suppressed when [reduceMotion] is true (FR-010-024 + design.md
 * §7) and replaced with a static "…" so the user still gets a
 * "thinking" signal without directional motion.
 */
@Composable
fun ClusterSuggestionCard(
    state: ClusterSuggestionCardState,
    onSummarize: () -> Unit,
    onOpenAll: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    reduceMotion: Boolean = false,
) {
    val palette = CapsulePalette.current(dark = isSystemInDarkTheme())

    when (state) {
        is ClusterSuggestionCardState.DismissedTrace -> DismissedTraceRow(
            label = state.label,
            palette = palette,
            modifier = modifier,
        )
        else -> CardSurface(
            state = state,
            palette = palette,
            reduceMotion = reduceMotion,
            onSummarize = onSummarize,
            onOpenAll = onOpenAll,
            onDismiss = onDismiss,
            onRetry = onRetry,
            modifier = modifier,
        )
    }
}

// ----------------------------------------------------------------------
// State model
// ----------------------------------------------------------------------

/**
 * UI projection of a cluster-suggestion card per spec 010 FR-010-024.
 * Six render variants — the data layer's [com.capsule.app.data.ClusterCardModel]
 * is mapped into one of these by Block 9's ViewModel.
 *
 * `headerLabel` (e.g. "Research session · 4 captures") and
 * `timeRangeLabel` (e.g. "Sat 9:14a → 11:42a") are pre-formatted by
 * the caller — the card stays locale-agnostic.
 *
 * `sourceCategories` is the dedupe-ordered list of source app
 * categories drawn as the source-glyph row; the card maps each to a
 * single-letter monogram fallback (visual-refit branch will swap in
 * brand glyphs).
 */
sealed class ClusterSuggestionCardState {

    abstract val headerLabel: String
    abstract val timeRangeLabel: String
    abstract val sourceCategories: List<String>

    /**
     * Default render — body italic prompt + Summarize / Open all / Dismiss.
     */
    @Immutable
    data class Surfaced(
        override val headerLabel: String,
        override val timeRangeLabel: String,
        override val sourceCategories: List<String>,
        val bodyText: String,
    ) : ClusterSuggestionCardState()

    /**
     * Mid-flight summarisation — action row replaced by italic ellipsis.
     */
    @Immutable
    data class Acting(
        override val headerLabel: String,
        override val timeRangeLabel: String,
        override val sourceCategories: List<String>,
        val bodyText: String,
    ) : ClusterSuggestionCardState()

    /**
     * Summarisation succeeded — body becomes bullets, citation foot
     * lists envelope ids in Berkeley Mono 10 sp `--ink-faint`.
     */
    @Immutable
    data class Acted(
        override val headerLabel: String,
        override val timeRangeLabel: String,
        override val sourceCategories: List<String>,
        val bullets: List<String>,
        val citations: List<String>,
    ) : ClusterSuggestionCardState() {
        init {
            require(bullets.isNotEmpty()) { "Acted state requires at least one bullet" }
        }
    }

    /**
     * Nano returned error / null / timeout — body replaced by an
     * apologetic line; action row becomes a single ↻ retry. After
     * [MAX_RETRIES] retries, the action row collapses to a mono dim
     * "Retried…" foot.
     */
    @Immutable
    data class Failed(
        override val headerLabel: String,
        override val timeRangeLabel: String,
        override val sourceCategories: List<String>,
        val retryCount: Int,
    ) : ClusterSuggestionCardState() {
        val retryExhausted: Boolean get() = retryCount >= MAX_RETRIES
    }

    /**
     * Cluster is >6 h old when the user opens Orbit — wraps a Surfaced
     * payload and adds a mono `· 9:14A` timestamp to the action row's
     * right margin.
     */
    @Immutable
    data class Stale(
        val inner: Surfaced,
        val timestampLabel: String,
    ) : ClusterSuggestionCardState() {
        override val headerLabel: String get() = inner.headerLabel
        override val timeRangeLabel: String get() = inner.timeRangeLabel
        override val sourceCategories: List<String> get() = inner.sourceCategories
    }

    /**
     * One or more constituent URL captures never hydrated. Body
     * soft-degrades to `N of M captures synthesized…`. The card never
     * lies about coverage (FR-010-024).
     */
    @Immutable
    data class SlowNetwork(
        override val headerLabel: String,
        override val timeRangeLabel: String,
        override val sourceCategories: List<String>,
        val syntCount: Int,
        val totalCount: Int,
    ) : ClusterSuggestionCardState() {
        init {
            require(syntCount in 0..totalCount) { "syntCount must be in 0..totalCount" }
            require(totalCount >= 1) { "totalCount must be ≥1" }
        }

        val bodyText: String get() =
            "$syntCount of $totalCount captures synthesized. " +
                "The ${totalCount - syntCount} couldn't be reached."
    }

    /**
     * Post-dismissal placeholder — single mono line at the same Diary
     * position so the dismissal is honestly auditable for the day.
     */
    @Immutable
    data class DismissedTrace(
        val label: String,
        override val headerLabel: String = "",
        override val timeRangeLabel: String = "",
        override val sourceCategories: List<String> = emptyList(),
    ) : ClusterSuggestionCardState()

    companion object {
        const val MAX_RETRIES: Int = 3
    }
}

// ----------------------------------------------------------------------
// Internal renderers
// ----------------------------------------------------------------------

@Composable
private fun CardSurface(
    state: ClusterSuggestionCardState,
    palette: CapsulePalette.Tokens,
    reduceMotion: Boolean,
    onSummarize: () -> Unit,
    onOpenAll: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(
                width = 1.dp,
                color = palette.rule,
                shape = RoundedCornerShape(18.dp),
            )
            .background(palette.paper)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag(TestTag.CARD)
            .semantics { contentDescription = "Cluster suggestion card" },
    ) {
        // Header row: ✦ + headerLabel
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            AgentVoiceMark(size = 14.sp)
            Text(
                text = state.headerLabel,
                style = TextStyle(
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 1.2.sp,
                    color = palette.inkAccentCluster,
                ),
                modifier = Modifier.testTag(TestTag.HEADER_LABEL),
            )
        }

        Spacer(Modifier.height(14.dp))

        // Body — varies by state
        BodyContent(state = state, palette = palette)

        Spacer(Modifier.height(16.dp))

        // Source-glyph row + time range
        SourceRow(
            sources = state.sourceCategories,
            timeRangeLabel = state.timeRangeLabel,
            palette = palette,
        )

        Spacer(Modifier.height(18.dp))

        // Action row — varies by state
        ActionContent(
            state = state,
            palette = palette,
            reduceMotion = reduceMotion,
            onSummarize = onSummarize,
            onOpenAll = onOpenAll,
            onDismiss = onDismiss,
            onRetry = onRetry,
        )

        // Citation foot for ACTED only — Berkeley Mono 10 sp --ink-faint
        if (state is ClusterSuggestionCardState.Acted) {
            Spacer(Modifier.height(12.dp))
            CitationFoot(citations = state.citations, palette = palette)
        }
    }
}

@Composable
private fun BodyContent(
    state: ClusterSuggestionCardState,
    palette: CapsulePalette.Tokens,
) {
    val serifItalic = TextStyle(
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        // Newsreader serif is wired at theme level; primitive inherits.
        color = palette.ink,
    )

    when (state) {
        is ClusterSuggestionCardState.Surfaced ->
            Text(
                text = state.bodyText,
                style = serifItalic,
                modifier = Modifier.testTag(TestTag.BODY),
            )

        is ClusterSuggestionCardState.Acting ->
            Text(
                text = state.bodyText,
                style = serifItalic,
                modifier = Modifier.testTag(TestTag.BODY),
            )

        is ClusterSuggestionCardState.Acted ->
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag(TestTag.BODY),
            ) {
                state.bullets.forEach { bullet ->
                    Text(
                        text = "• $bullet",
                        style = serifItalic.copy(fontSize = 16.sp, lineHeight = 22.sp),
                    )
                }
            }

        is ClusterSuggestionCardState.Failed ->
            Text(
                text = "Orbit couldn't reach all captures. Try again?",
                style = serifItalic.copy(fontSize = 16.sp),
                modifier = Modifier.testTag(TestTag.BODY),
            )

        is ClusterSuggestionCardState.Stale ->
            Text(
                text = state.inner.bodyText,
                style = serifItalic,
                modifier = Modifier.testTag(TestTag.BODY),
            )

        is ClusterSuggestionCardState.SlowNetwork ->
            Text(
                text = state.bodyText,
                style = serifItalic,
                modifier = Modifier.testTag(TestTag.BODY),
            )

        is ClusterSuggestionCardState.DismissedTrace -> Unit
    }
}

@Composable
private fun ActionContent(
    state: ClusterSuggestionCardState,
    palette: CapsulePalette.Tokens,
    reduceMotion: Boolean,
    onSummarize: () -> Unit,
    onOpenAll: () -> Unit,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        when (state) {
            is ClusterSuggestionCardState.Surfaced,
            is ClusterSuggestionCardState.Acted,
            is ClusterSuggestionCardState.SlowNetwork -> ClusterActionRow(
                actions = listOf(
                    ClusterAction(label = "Summarize", onClick = onSummarize),
                    ClusterAction(label = "Open all", onClick = onOpenAll),
                    ClusterAction(label = "Dismiss", onClick = onDismiss),
                ),
                modifier = Modifier.testTag(TestTag.ACTION_ROW),
            )

            is ClusterSuggestionCardState.Acting -> ActingEllipsis(
                palette = palette,
                reduceMotion = reduceMotion,
            )

            is ClusterSuggestionCardState.Failed -> {
                if (state.retryExhausted) {
                    Text(
                        text = "Retried. Try again later, or open captures individually.",
                        style = monoFootStyle(palette),
                        modifier = Modifier.testTag(TestTag.RETRY_EXHAUSTED),
                    )
                } else {
                    ClusterActionRow(
                        actions = listOf(
                            ClusterAction(label = "↻ Retry", onClick = onRetry),
                            ClusterAction(label = "Dismiss", onClick = onDismiss),
                        ),
                        modifier = Modifier.testTag(TestTag.ACTION_ROW),
                    )
                }
            }

            is ClusterSuggestionCardState.Stale -> ClusterActionRow(
                actions = listOf(
                    ClusterAction(label = "Summarize", onClick = onSummarize),
                    ClusterAction(label = "Open all", onClick = onOpenAll),
                    ClusterAction(label = "Dismiss", onClick = onDismiss),
                ),
                modifier = Modifier.testTag(TestTag.ACTION_ROW),
            )

            is ClusterSuggestionCardState.DismissedTrace -> Unit
        }

        // Stale timestamp marker pinned to the action row's right edge
        if (state is ClusterSuggestionCardState.Stale) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = "· ${state.timestampLabel}",
                    style = monoFootStyle(palette),
                    modifier = Modifier.testTag(TestTag.STALE_TIMESTAMP),
                )
            }
        }
    }
}

@Composable
private fun ActingEllipsis(
    palette: CapsulePalette.Tokens,
    reduceMotion: Boolean,
) {
    val style = TextStyle(
        fontSize = 14.sp,
        fontStyle = FontStyle.Italic,
        fontWeight = FontWeight.Normal,
        color = palette.inkFaint,
    )

    if (reduceMotion) {
        Text(
            text = "…",
            style = style,
            modifier = Modifier
                .height(48.dp)
                .testTag(TestTag.ACTING_ELLIPSIS),
        )
        return
    }

    // 600 ms-per-step cycle: ".", "..", "..."
    val infinite = rememberInfiniteTransition(label = "acting-ellipsis")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "acting-ellipsis-phase",
    )
    val dots = ".".repeat((phase.toInt() % 3) + 1)
    Text(
        text = dots,
        style = style,
        modifier = Modifier
            .height(48.dp)
            .testTag(TestTag.ACTING_ELLIPSIS),
    )
}

@Composable
private fun SourceRow(
    sources: List<String>,
    timeRangeLabel: String,
    palette: CapsulePalette.Tokens,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sources.take(MAX_SOURCE_GLYPHS).forEach { category ->
                SourceGlyphMonogram(category = category, palette = palette)
            }
            if (sources.size > MAX_SOURCE_GLYPHS) {
                Text(
                    text = "+${sources.size - MAX_SOURCE_GLYPHS}",
                    style = monoFootStyle(palette),
                    modifier = Modifier.testTag(TestTag.SOURCE_OVERFLOW),
                )
            }
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Text(
                text = timeRangeLabel,
                style = monoFootStyle(palette).copy(letterSpacing = 1.2.sp),
                modifier = Modifier.testTag(TestTag.TIME_RANGE),
            )
        }
    }
}

/**
 * Single-letter monogram fallback for a source category — the
 * visual-refit branch will replace this with brand glyphs (twitter,
 * safari, etc.). Stays in `--ink` on the paper so it reads as a
 * subtle source-mark, not a brand badge.
 */
@Composable
private fun SourceGlyphMonogram(
    category: String,
    palette: CapsulePalette.Tokens,
) {
    val mono = category.firstOrNull()?.uppercaseChar()?.toString() ?: "?"
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(width = 1.dp, color = palette.rule, shape = RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = mono,
            style = TextStyle(
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Normal,
                color = palette.inkFaint,
            ),
        )
    }
}

@Composable
private fun CitationFoot(
    citations: List<String>,
    palette: CapsulePalette.Tokens,
) {
    if (citations.isEmpty()) return
    Text(
        text = citations.joinToString(separator = " · ") { "[$it]" },
        style = monoFootStyle(palette),
        modifier = Modifier.testTag(TestTag.CITATION_FOOT),
    )
}

@Composable
private fun DismissedTraceRow(
    label: String,
    palette: CapsulePalette.Tokens,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp)
            .testTag(TestTag.DISMISSED_TRACE),
    ) {
        Text(
            text = label,
            style = monoFootStyle(palette),
        )
    }
}

@Composable
private fun monoFootStyle(palette: CapsulePalette.Tokens): TextStyle =
    TextStyle(
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.6.sp,
        color = palette.inkFaint,
    )

@Suppress("unused")
private val LocalContentColorRef = LocalContentColor // keep import live for future palette swap

// ----------------------------------------------------------------------
// Test tags
// ----------------------------------------------------------------------

/**
 * Stable test tags exposed for instrumented + JVM tests. Centralised
 * so test code never reaches into private internals.
 */
object ClusterSuggestionCardTestTags {
    const val CARD = "cluster-suggestion-card"
    const val HEADER_LABEL = "cluster-suggestion-header"
    const val BODY = "cluster-suggestion-body"
    const val ACTION_ROW = "cluster-suggestion-actions"
    const val ACTING_ELLIPSIS = "cluster-suggestion-acting"
    const val RETRY_EXHAUSTED = "cluster-suggestion-retry-exhausted"
    const val STALE_TIMESTAMP = "cluster-suggestion-stale-ts"
    const val SOURCE_OVERFLOW = "cluster-suggestion-source-overflow"
    const val TIME_RANGE = "cluster-suggestion-time-range"
    const val CITATION_FOOT = "cluster-suggestion-citations"
    const val DISMISSED_TRACE = "cluster-suggestion-dismissed-trace"
}

private typealias TestTag = ClusterSuggestionCardTestTags

private const val MAX_SOURCE_GLYPHS: Int = 4
