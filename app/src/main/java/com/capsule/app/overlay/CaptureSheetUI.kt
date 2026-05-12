package com.capsule.app.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capsule.app.ui.theme.LocalRuntimeFlags
import com.capsule.app.ui.primitives.SourceGlyph
import com.capsule.app.ui.primitives.SourceIdentityResolver
import com.capsule.app.ui.tokens.CapsuleType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CaptureSheetUI(
    content: CapturedContent?,
    isExpanded: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isExpanded && content != null,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
    ) {
        content?.let { captured ->
            if (LocalRuntimeFlags.current.useNewVisualLanguage) {
                QuietCaptureSheet(captured = captured, onSave = onSave, onDiscard = onDiscard)
            } else {
                LegacyCaptureSheet(captured = captured, onSave = onSave, onDiscard = onDiscard)
            }
        }
    }
}

@Composable
private fun LegacyCaptureSheet(
    captured: CapturedContent,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Captured",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (captured.isSensitive) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Sensitive content",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Text(
                        text = "Sensitive",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val scrollState = rememberScrollState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
            ) {
                SelectionContainer {
                    Text(
                        text = captured.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(scrollState)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val timeStr = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(Date(captured.timestamp))
            val metaText = captured.sourcePackage?.let { "$it · $timeStr" } ?: timeStr
            Text(
                text = metaText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onDiscard) {
                    Text("Discard")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = onSave) {
                    Text("Save & Close")
                }
            }
        }
    }
}

@Composable
private fun QuietCaptureSheet(
    captured: CapturedContent,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    val colors = QuietOverlayColors
    val scrollState = rememberScrollState()
    val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(captured.timestamp))
    val source = captured.sourceLabel()
    val sourceGlyph = captured.sourceGlyphKind()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
        color = colors.DeepNavy,
        contentColor = colors.Cream,
        shadowElevation = 18.dp,
        border = BorderStroke(1.dp, colors.RuleHi),
    ) {
        Column(
            modifier = Modifier.padding(top = 14.dp, start = 20.dp, end = 20.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.CreamFaint)
                    .align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(colors.Accent)
                )
                QuietMonoLabel(text = "SAVE TO ORBIT", color = colors.Accent)
                Spacer(modifier = Modifier.weight(1f))
                QuietMonoLabel(
                    text = "FROM ${source.uppercase(Locale.ROOT)} · ${timeStr.uppercase(Locale.ROOT)}",
                    color = colors.CreamFaint,
                    maxLines = 1,
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = colors.Panel,
                border = BorderStroke(1.dp, colors.Rule),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    SourceGlyph(kind = sourceGlyph, size = 22.dp)

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            QuietMonoLabel(
                                text = source.uppercase(Locale.ROOT),
                                color = colors.CreamFaint,
                                maxLines = 1,
                                modifier = Modifier.weight(1f),
                            )
                            if (captured.isSensitive) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Sensitive content",
                                    tint = colors.Accent,
                                    modifier = Modifier.size(14.dp),
                                )
                            }
                        }
                        SelectionContainer {
                            Text(
                                text = captured.text,
                                color = colors.Cream,
                                style = TextStyle(
                                    fontFamily = CapsuleType.QuietAlmanac.displaySerif,
                                    fontSize = 18.sp,
                                    lineHeight = 24.sp,
                                    fontStyle = FontStyle.Italic,
                                    letterSpacing = 0.sp,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 190.dp)
                                    .verticalScroll(scrollState),
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Button(
                    onClick = onSave,
                    modifier = Modifier.weight(2f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.Accent,
                        contentColor = Color(0xFF211607),
                    ),
                ) {
                    Text(
                        text = "Save",
                        style = TextStyle(
                            fontFamily = CapsuleType.QuietAlmanac.bodySans,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.sp,
                        ),
                    )
                }
                OutlinedButton(
                    onClick = onDiscard,
                    modifier = Modifier.weight(1f),
                    border = BorderStroke(1.dp, colors.RuleHi),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.CreamDim),
                ) {
                    Text(
                        text = "Cancel",
                        style = TextStyle(
                            fontFamily = CapsuleType.QuietAlmanac.bodySans,
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.sp,
                        ),
                    )
                }
            }

            QuietMonoLabel(
                text = "PRIVATE BY DEFAULT · USER CONTROLLED",
                color = colors.CreamFaint,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun QuietMonoLabel(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
) {
    Text(
        text = text,
        color = color,
        style = TextStyle(
            fontFamily = CapsuleType.QuietAlmanac.captionMono,
            fontSize = 9.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.2.sp,
        ),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

private fun CapturedContent.sourceLabel(): String {
    stateSnapshotAtCapture?.sourceAppLabel?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
    sourcePackage?.trim()?.takeIf { it.isNotBlank() }?.let { raw ->
        return raw.substringAfterLast('.')
            .replace('_', ' ')
            .replaceFirstChar { it.titlecase(Locale.ROOT) }
    }
    return "Orbit"
}

private fun CapturedContent.sourceGlyphKind() = SourceIdentityResolver.glyphKind(
    textContent = text,
    canonicalUrl = null,
    sourceAppLabel = stateSnapshotAtCapture?.sourceAppLabel ?: sourcePackage,
    appCategory = stateSnapshotAtCapture?.appCategory,
)
