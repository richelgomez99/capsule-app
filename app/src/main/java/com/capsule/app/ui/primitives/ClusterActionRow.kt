package com.capsule.app.ui.primitives

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capsule.app.ui.tokens.CapsulePalette

/**
 * **ClusterActionRow** — 1-3 inline action affordances rendered as
 * Geist 14 sp regular sentence-case in `--ink`, separated by 1 px
 * vertical hairline rules `│` (`--rule`), with 48 dp touch targets.
 *
 * Per spec 010 FR-010-020 (revised /autoplan 2026-04-26):
 *  - Geist 14 sp regular weight, sentence case
 *  - Content-column left-aligned (caller positions the row)
 *  - No Material button chrome (no ripple background, no rounded fill)
 *  - Vertical hairline rules between actions, NOT around the row
 *  - 48 dp tap target via `defaultMinSize`
 *
 * Each [Action] is a label + onClick. An optional [enabled] flag may
 * gate the action; disabled actions render in `--ink-faint` and do
 * not consume taps. The row is intentionally dumb — the parent
 * (`ClusterSuggestionCard`) drives state.
 */
@Immutable
data class ClusterAction(
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun ClusterActionRow(
    actions: List<ClusterAction>,
    modifier: Modifier = Modifier,
    inkColor: Color? = null,
    ruleColor: Color? = null,
    faintColor: Color? = null,
) {
    require(actions.size in 1..3) {
        "ClusterActionRow renders 1-3 actions per FR-010-020; got ${actions.size}"
    }
    val palette = CapsulePalette.current(dark = isSystemInDarkTheme())
    val ink = inkColor ?: palette.ink
    val rule = ruleColor ?: palette.rule
    val faint = faintColor ?: palette.inkFaint

    val labelStyle = TextStyle(
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        // No fontFamily binding here — Geist is wired at the theme
        // level. Primitive intentionally inherits.
    )

    Row(
        modifier = modifier
            .height(48.dp)
            .wrapContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
    ) {
        actions.forEachIndexed { idx, action ->
            if (idx > 0) {
                // 1 px hairline divider — thin slab in --rule.
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .padding(vertical = 12.dp)
                        .background(rule)
                )
            }
            ActionLabel(
                action = action,
                ink = ink,
                faint = faint,
                style = labelStyle,
            )
        }
    }
}

@Composable
private fun ActionLabel(
    action: ClusterAction,
    ink: Color,
    faint: Color,
    style: TextStyle,
) {
    val color = if (action.enabled) ink else faint
    val tapModifier = if (action.enabled) {
        Modifier.clickable(
            enabled = true,
            role = Role.Button,
            onClick = action.onClick,
        )
    } else {
        Modifier
    }
    Box(
        modifier = tapModifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = action.label,
            color = color,
            style = style,
            textAlign = TextAlign.Start,
        )
    }
}
