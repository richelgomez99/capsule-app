package com.capsule.app.diary.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capsule.app.ui.primitives.ClusterAction
import com.capsule.app.ui.primitives.ClusterActionRow
import com.capsule.app.ui.primitives.MonoLabel
import com.capsule.app.ui.primitives.SourceGlyph
import com.capsule.app.ui.primitives.SourceGlyphKind
import com.capsule.app.ui.tokens.CapsulePalette
import com.capsule.app.ui.tokens.CapsuleType
import java.util.Locale

@Immutable
data class ClusterDetailState(
    val sessionName: String,
    val title: String,
    val heroText: String,
    val heroAccent: String,
    val timeRangeLabel: String,
    val sourceCategories: List<String>,
    val bullets: List<ClusterDetailBullet>,
    val calendarProposal: ClusterCalendarProposal? = null,
    val provenanceLabel: String,
)

@Immutable
data class ClusterDetailBullet(
    val body: String,
    val citations: List<ClusterCitation>,
)

@Immutable
data class ClusterCitation(
    val sourceCategory: String,
    val label: String,
)

@Immutable
data class ClusterCalendarProposal(
    val headline: String,
    val accent: String,
    val caption: String,
)

@Composable
fun ClusterDetailScreen(
    state: ClusterDetailState,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onAddCalendarBlock: () -> Unit,
    onEditCalendarBlock: () -> Unit,
    onDismissCalendarBlock: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = CapsulePalette.current(dark = isSystemInDarkTheme())

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ClusterDetailColors.BgDeep)
            .testTag(ClusterDetailScreenTestTags.SCREEN),
    ) {
        ClusterDetailTopBar(
            state = state,
            palette = palette,
            onBack = onBack,
            onMore = onMore,
        )
        Hairline(palette)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            MonoLabel(
                text = "// what you've been circling",
                color = ClusterDetailColors.CreamDim,
            )
            HeroQuestion(
                text = state.heroText,
                accent = state.heroAccent,
                palette = palette,
                modifier = Modifier.testTag(ClusterDetailScreenTestTags.HERO),
            )
            SourceMetaRow(
                sources = state.sourceCategories,
                timeRangeLabel = state.timeRangeLabel,
                palette = palette,
            )
            BulletCard(
                bullets = state.bullets,
                palette = palette,
            )
            state.calendarProposal?.let { proposal ->
                CalendarProposalCard(
                    proposal = proposal,
                    palette = palette,
                    onAdd = onAddCalendarBlock,
                    onEdit = onEditCalendarBlock,
                    onDismiss = onDismissCalendarBlock,
                )
            }
            MonoLabel(
                text = state.provenanceLabel,
                color = ClusterDetailColors.CreamFaint,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }
}

@Composable
private fun ClusterDetailTopBar(
    state: ClusterDetailState,
    palette: CapsulePalette.Tokens,
    onBack: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButtonGlyph(label = "<", palette = palette, onClick = onBack)
        Column(modifier = Modifier.weight(1f)) {
            MonoLabel(text = state.sessionName, color = ClusterDetailColors.CreamDim, size = 9.sp)
            Text(
                text = state.title,
                style = TextStyle(
                    fontFamily = CapsuleType.QuietAlmanac.bodySans,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                    color = ClusterDetailColors.Cream,
                ),
            )
        }
        TextButtonGlyph(label = "...", palette = palette, onClick = onMore)
    }
}

@Composable
private fun TextButtonGlyph(
    label: String,
    palette: CapsulePalette.Tokens,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = TextStyle(
                fontFamily = CapsuleType.QuietAlmanac.captionMono,
                fontSize = 16.sp,
                lineHeight = 18.sp,
                color = ClusterDetailColors.Cream,
                letterSpacing = 0.sp,
            ),
        )
    }
}

@Composable
private fun HeroQuestion(
    text: String,
    accent: String,
    palette: CapsulePalette.Tokens,
    modifier: Modifier = Modifier,
) {
    val accentStart = text.lowercase(Locale.ROOT).indexOf(accent.lowercase(Locale.ROOT))
    val hero = buildAnnotatedString {
        if (accentStart < 0 || accent.isBlank()) {
            append(text)
            return@buildAnnotatedString
        }
        append(text.substring(0, accentStart))
        withStyle(
            SpanStyle(
                color = ClusterDetailColors.Accent,
                fontStyle = FontStyle.Italic,
            ),
        ) {
            append(text.substring(accentStart, accentStart + accent.length))
        }
        append(text.substring(accentStart + accent.length))
    }

    Text(
        text = hero,
        modifier = modifier,
        style = TextStyle(
            fontFamily = CapsuleType.QuietAlmanac.displaySerif,
            fontSize = 26.sp,
            lineHeight = 31.sp,
            fontWeight = FontWeight.Normal,
            letterSpacing = 0.sp,
            color = ClusterDetailColors.Cream,
        ),
    )
}

@Composable
private fun SourceMetaRow(
    sources: List<String>,
    timeRangeLabel: String,
    palette: CapsulePalette.Tokens,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            sources.take(5).forEach { source ->
                SourceGlyph(kind = source.toSourceGlyphKind())
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        MonoLabel(text = timeRangeLabel, color = ClusterDetailColors.CreamFaint)
    }
}

@Composable
private fun BulletCard(
    bullets: List<ClusterDetailBullet>,
    palette: CapsulePalette.Tokens,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ClusterDetailColors.Rule, RoundedCornerShape(16.dp))
            .background(ClusterDetailColors.Panel)
            .padding(horizontal = 18.dp, vertical = 20.dp)
            .testTag(ClusterDetailScreenTestTags.BULLETS),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(ClusterDetailColors.Green),
            )
            MonoLabel(text = "Three things you found", color = ClusterDetailColors.Green)
        }
        bullets.forEachIndexed { index, bullet ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
            ) {
                MonoLabel(
                    text = (index + 1).toString().padStart(2, '0'),
                    color = ClusterDetailColors.Accent,
                    size = 11.sp,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = bullet.body,
                        style = TextStyle(
                            fontFamily = CapsuleType.QuietAlmanac.bodySans,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = ClusterDetailColors.Cream,
                            letterSpacing = 0.sp,
                        ),
                    )
                    CitationRow(citations = bullet.citations, palette = palette)
                }
            }
        }
    }
}

@Composable
private fun CitationRow(
    citations: List<ClusterCitation>,
    palette: CapsulePalette.Tokens,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        citations.take(3).forEach { citation ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .border(1.dp, ClusterDetailColors.Rule, RoundedCornerShape(999.dp))
                    .background(ClusterDetailColors.PanelHi)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SourceGlyph(kind = citation.sourceCategory.toSourceGlyphKind(), size = 16.dp)
                MonoLabel(text = citation.label, color = ClusterDetailColors.CreamDim, size = 9.sp)
            }
        }
    }
}

@Composable
private fun CalendarProposalCard(
    proposal: ClusterCalendarProposal,
    palette: CapsulePalette.Tokens,
    onAdd: () -> Unit,
    onEdit: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, ClusterDetailColors.Accent.copy(alpha = 0.33f), RoundedCornerShape(16.dp))
            .background(Color.Transparent)
            .padding(18.dp)
            .testTag(ClusterDetailScreenTestTags.CALENDAR_PROPOSAL),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MonoLabel(text = proposal.caption, color = ClusterDetailColors.CreamFaint)
        CalendarHeadline(
            text = proposal.headline,
            accent = proposal.accent,
        )
        ClusterActionRow(
            actions = listOf(
                ClusterAction(label = "Block it", onClick = onAdd),
                ClusterAction(label = "Move it", onClick = onEdit),
                ClusterAction(label = "Not now", onClick = onDismiss),
            ),
            inkColor = ClusterDetailColors.Cream,
            ruleColor = ClusterDetailColors.RuleHi,
            faintColor = ClusterDetailColors.CreamDim,
        )
    }
}

@Composable
private fun CalendarHeadline(
    text: String,
    accent: String,
) {
    val accentStart = text.lowercase(Locale.ROOT).indexOf(accent.lowercase(Locale.ROOT))
    val headline = buildAnnotatedString {
        if (accentStart < 0 || accent.isBlank()) {
            append(text)
            return@buildAnnotatedString
        }
        append(text.substring(0, accentStart))
        withStyle(
            SpanStyle(
                color = ClusterDetailColors.Accent,
                fontStyle = FontStyle.Italic,
            ),
        ) {
            append(text.substring(accentStart, accentStart + accent.length))
        }
        append(text.substring(accentStart + accent.length))
    }

    Text(
        text = headline,
        style = TextStyle(
            fontFamily = CapsuleType.QuietAlmanac.displaySerif,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            color = ClusterDetailColors.Cream,
            letterSpacing = 0.sp,
        ),
    )
}

@Composable
private fun Hairline(palette: CapsulePalette.Tokens) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(ClusterDetailColors.Rule),
    )
}

private fun String.toSourceGlyphKind(): SourceGlyphKind = when (lowercase(Locale.ROOT)) {
    "browser", "chrome", "web" -> SourceGlyphKind.chrome
    "video", "youtube" -> SourceGlyphKind.youtube
    "messaging", "sms", "messages" -> SourceGlyphKind.sms
    "social", "instagram" -> SourceGlyphKind.instagram
    "reading", "article", "news" -> SourceGlyphKind.nyt
    "email", "work_email", "gmail" -> SourceGlyphKind.gmail
    "files", "file" -> SourceGlyphKind.files
    "photos", "photo" -> SourceGlyphKind.photos
    "share" -> SourceGlyphKind.share
    else -> SourceGlyphKind.url
}

private object ClusterDetailColors {
    val BgDeep = Color(0xFF080B14)
    val Panel = Color(0xFF141A2B)
    val PanelHi = Color(0xFF1A2236)
    val Cream = Color(0xFFF3EAD8)
    val CreamDim = Color(0x8CF3EAD8)
    val CreamFaint = Color(0x38F3EAD8)
    val Rule = Color(0x1AF3EAD8)
    val RuleHi = Color(0x2EF3EAD8)
    val Accent = Color(0xFFE8B06A)
    val Green = Color(0xFF7FB38A)
}

object ClusterDetailScreenTestTags {
    const val SCREEN = "cluster-detail-screen"
    const val HERO = "cluster-detail-hero"
    const val BULLETS = "cluster-detail-bullets"
    const val CALENDAR_PROPOSAL = "cluster-detail-calendar-proposal"
}

@Preview(name = "cluster detail - quiet almanac")
@Composable
private fun ClusterDetailScreenPreview() {
    ClusterDetailScreen(
        state = ClusterDetailState(
            sessionName = "Research session · Apr 26",
            title = "Pricing",
            heroText = "How to think about pre-seed valuation when the comparison set is thin.",
            heroAccent = "pre-seed valuation",
            timeRangeLabel = "· 4 captures · Sat 9:14a -> 11:42a",
            sourceCategories = listOf("SOCIAL", "BROWSER", "VIDEO", "READING"),
            bullets = listOf(
                ClusterDetailBullet(
                    body = "Anchor the round at the top of your peers, not the middle. The comparison set does the persuasion.",
                    citations = listOf(
                        ClusterCitation("SOCIAL", "@patrick · 9:51a"),
                        ClusterCitation("VIDEO", "Acquired 14:22"),
                    ),
                ),
                ClusterDetailBullet(
                    body = "At pre-seed, dilution past 22% signals desperation to anyone reading your cap table later.",
                    citations = listOf(ClusterCitation("BROWSER", "YC memo · §3")),
                ),
            ),
            calendarProposal = ClusterCalendarProposal(
                headline = "You have 10:30 to 12:00 free tomorrow. Block it for pricing?",
                accent = "10:30 to 12:00",
                caption = "Sun · Apr 27 · 90 min · Google Calendar",
            ),
            provenanceLabel = "Every claim cites a capture you made. Tap a citation to audit the source.",
        ),
        onBack = {},
        onMore = {},
        onAddCalendarBlock = {},
        onEditCalendarBlock = {},
        onDismissCalendarBlock = {},
    )
}