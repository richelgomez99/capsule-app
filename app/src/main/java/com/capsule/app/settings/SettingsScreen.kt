package com.capsule.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capsule.app.ui.primitives.MonoLabel
import com.capsule.app.ui.theme.LocalRuntimeFlags
import com.capsule.app.ui.tokens.CapsuleType

/**
 * T070 — minimal v1 Settings surface.
 *
 * Single switch: "Pause continuations". When flipped to on, any in-flight
 * `URL_HYDRATE` WorkManager jobs are cancelled and new enqueues are blocked
 * (see [com.capsule.app.continuation.ContinuationEngine] guard). Restored
 * on flip-off.
 *
 * Structurally a "shell" per analyze C2 — later settings (retention,
 * cloud boost opt-in, storage sovereignty, etc.) stack under this
 * [Column] as additional rows without reshaping the entry point.
 */
@Composable
fun SettingsScreen(
    paused: Boolean,
    onPauseChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)? = null,
    onOpenCaptureSetup: (() -> Unit)? = null,
    trashCount: Int = 0,
    onOpenTrash: (() -> Unit)? = null,
    onOpenAuditLog: (() -> Unit)? = null,
    onExportData: (() -> Unit)? = null,
    exportInProgress: Boolean = false,
    exportStatus: String? = null
) {
    var localPaused by remember(paused) { mutableStateOf(paused) }
    var showExportConfirm by remember { mutableStateOf(false) }
    val useNewVisualLanguage = LocalRuntimeFlags.current.useNewVisualLanguage

    if (useNewVisualLanguage) {
        QuietSettingsScreen(
            paused = localPaused,
            onPauseChange = { next ->
                localPaused = next
                onPauseChange(next)
            },
            modifier = modifier,
            onNavigateBack = onNavigateBack,
            onOpenCaptureSetup = onOpenCaptureSetup,
            trashCount = trashCount,
            onOpenTrash = onOpenTrash,
            onOpenAuditLog = onOpenAuditLog,
            onExportData = {
                if (!exportInProgress) showExportConfirm = true
            },
            exportInProgress = exportInProgress,
            exportStatus = exportStatus,
        )
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 24.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(24.dp))

            SettingsToggleRow(
                title = "Pause continuations",
                description = "Stops Orbit from fetching link summaries. Local captures still work.",
                checked = localPaused,
                onCheckedChange = { next ->
                    localPaused = next
                    onPauseChange(next)
                }
            )

            if (onOpenCaptureSetup != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(4.dp))
                SettingsNavRow(
                    title = "Bubble overlay",
                    description = "Turn on the floating capture bubble and manage overlay permissions.",
                    onClick = onOpenCaptureSetup
                )
            }

            if (onOpenTrash != null) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(4.dp))
                SettingsNavRow(
                    title = "Trash" + if (trashCount > 0) " ($trashCount)" else "",
                    description = "Restore or permanently remove deleted captures.",
                    onClick = onOpenTrash
                )
            }

            if (onOpenAuditLog != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(4.dp))
                SettingsNavRow(
                    title = "What Orbit did today",
                    description = "Audit every capture, enrichment, and network call.",
                    onClick = onOpenAuditLog
                )
            }

            if (onExportData != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(4.dp))
                val subtitle = when {
                    exportInProgress -> "Exporting…"
                    exportStatus != null -> exportStatus
                    else -> "Save an unencrypted JSON copy of your captures to Downloads."
                }
                SettingsNavRow(
                    title = "Export my data",
                    description = subtitle,
                    onClick = {
                        if (!exportInProgress) showExportConfirm = true
                    }
                )
            }
        }
    }

    if (showExportConfirm) {
        AlertDialog(
            onDismissRequest = { showExportConfirm = false },
            title = { Text("Export my data") },
            text = {
                Text(
                    "Orbit will write a folder in Downloads containing every envelope, " +
                        "enrichment, and audit row on this device as JSON.\n\n" +
                        "Export is not encrypted. Treat the files like any other plain data."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showExportConfirm = false
                    onExportData?.invoke()
                }) { Text("Export") }
            },
            dismissButton = {
                TextButton(onClick = { showExportConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun QuietSettingsScreen(
    paused: Boolean,
    onPauseChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onNavigateBack: (() -> Unit)?,
    onOpenCaptureSetup: (() -> Unit)?,
    trashCount: Int,
    onOpenTrash: (() -> Unit)?,
    onOpenAuditLog: (() -> Unit)?,
    onExportData: (() -> Unit)?,
    exportInProgress: Boolean,
    exportStatus: String?,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QuietSettingsColors.BgDeep)
    ) {
        QuietSettingsTopBar(onNavigateBack = onNavigateBack)
        QuietRule()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp)
        ) {
            QuietSettingsHero()

            QuietSettingSection(label = "Where your captures think") {
                QuietToggleRow(
                    title = "Pause continuations",
                    description = "Stops link summaries and cloud continuation jobs. Local captures still work.",
                    tag = if (paused) "PAUSED" else "ON",
                    checked = paused,
                    onCheckedChange = onPauseChange,
                )
            }

            if (onOpenCaptureSetup != null) {
                QuietSettingSection(label = "What Orbit may capture") {
                    QuietNavRow(
                        title = "Floating bubble",
                        description = "Always available across apps. Manage overlay permission from the setup flow.",
                        value = "SETUP",
                        onClick = onOpenCaptureSetup,
                        modifier = Modifier.testTag(SettingsScreenTestTags.CAPTURE_SETUP_ROW),
                    )
                }
            }

            QuietSettingSection(label = "What Orbit remembers") {
                if (onOpenTrash != null) {
                    QuietNavRow(
                        title = "Trash" + if (trashCount > 0) " ($trashCount)" else "",
                        description = "Restore or permanently remove deleted captures before the retention window closes.",
                        value = "30 days",
                        onClick = onOpenTrash,
                    )
                }
                if (onOpenAuditLog != null) {
                    QuietNavRow(
                        title = "What Orbit did today",
                        description = "Audit every capture, enrichment, and network call.",
                        value = "AUDIT",
                        onClick = onOpenAuditLog,
                    )
                }
                if (onExportData != null) {
                    val subtitle = when {
                        exportInProgress -> "Exporting…"
                        exportStatus != null -> exportStatus
                        else -> "Save an unencrypted JSON copy of your captures to Downloads."
                    }
                    QuietNavRow(
                        title = "Export my data",
                        description = subtitle,
                        value = if (exportInProgress) "WAIT" else "JSON",
                        onClick = onExportData,
                    )
                }
            }

            QuietSettingSection(label = "The hard line") {
                QuietDangerRow(onOpenTrash = onOpenTrash)
            }

            QuietSettingsFooter()
        }
    }
}

@Composable
private fun QuietSettingsTopBar(onNavigateBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 18.dp, top = 14.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (onNavigateBack != null) {
            Text(
                text = "‹",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onNavigateBack)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = QuietSettingsColors.Cream,
                style = TextStyle(
                    fontFamily = CapsuleType.QuietAlmanac.bodySans,
                    fontSize = 28.sp,
                    lineHeight = 28.sp,
                    fontWeight = FontWeight.Normal,
                ),
            )
        }
        Text(
            text = "Settings",
            color = QuietSettingsColors.Cream,
            style = TextStyle(
                fontFamily = CapsuleType.QuietAlmanac.bodySans,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun QuietSettingsHero() {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)) {
        MonoLabel(
            text = "// Principle I · Default Privacy",
            color = QuietSettingsColors.CreamDim,
            size = 9.5.sp,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = buildAnnotatedString {
                append("Orbit keeps capture ")
                withStyle(
                    SpanStyle(
                        color = QuietSettingsColors.Accent,
                        fontStyle = FontStyle.Italic,
                    )
                ) {
                    append("under your control")
                }
                append(". Choose where it can appear, when cloud work pauses, and how long deleted captures stay recoverable.")
            },
            color = QuietSettingsColors.Cream,
            style = TextStyle(
                fontFamily = CapsuleType.QuietAlmanac.displaySerif,
                fontSize = 22.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Normal,
            ),
        )
    }
}

@Composable
internal fun QuietSettingSection(
    label: String,
    content: @Composable () -> Unit,
) {
    Column {
        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 10.dp)) {
            MonoLabel(text = label, color = QuietSettingsColors.CreamDim, size = 9.5.sp)
        }
        QuietRule()
        content()
    }
}

@Composable
private fun QuietToggleRow(
    title: String,
    description: String,
    tag: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(SettingsScreenTestTags.PAUSE_TOGGLE)
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            )
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuietRowTitle(title)
                if (tag != null) QuietTag(tag)
            }
            Spacer(Modifier.height(4.dp))
            QuietRowDescription(description)
        }
        QuietToggle(checked = checked)
    }
    QuietRule()
}

@Composable
internal fun QuietNavRow(
    title: String,
    description: String,
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            QuietRowTitle(title)
            Spacer(Modifier.height(3.dp))
            QuietRowDescription(description)
        }
        Text(
            text = value,
            color = QuietSettingsColors.Accent,
            style = TextStyle(
                fontFamily = CapsuleType.QuietAlmanac.displaySerif,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontStyle = FontStyle.Italic,
            ),
        )
        Text(
            text = "›",
            color = QuietSettingsColors.CreamFaint,
            style = TextStyle(
                fontFamily = CapsuleType.QuietAlmanac.bodySans,
                fontSize = 18.sp,
                lineHeight = 18.sp,
            ),
        )
    }
    QuietRule()
}

@Composable
private fun QuietDangerRow(onOpenTrash: (() -> Unit)?) {
    Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
        Text(
            text = "Forget everything from before",
            color = QuietSettingsColors.Red,
            style = TextStyle(
                fontFamily = CapsuleType.QuietAlmanac.displaySerif,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontStyle = FontStyle.Italic,
            ),
        )
        Spacer(Modifier.height(6.dp))
        QuietRowDescription(
            "Orbit can remove local captures, envelopes, embeddings, and on-device caches. " +
                "Third-party LLM provider deletion follows their provider and gateway SLAs."
        )
        if (onOpenTrash != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Review trash",
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onOpenTrash)
                    .background(Color.Transparent)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = QuietSettingsColors.Red,
                style = TextStyle(
                    fontFamily = CapsuleType.QuietAlmanac.bodySans,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
    QuietRule()
}

@Composable
private fun QuietSettingsFooter() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MonoLabel(
            text = "Orbit · alpha · audit log open",
            color = QuietSettingsColors.CreamFaint,
            size = 9.sp,
        )
        Spacer(Modifier.height(4.dp))
        MonoLabel(
            text = "for local-model-capable phones (Pixel 8 Pro+, Galaxy S24+, capable hardware)",
            color = QuietSettingsColors.CreamFaint,
            size = 8.sp,
        )
    }
}

@Composable
internal fun QuietRowTitle(text: String) {
    Text(
        text = text,
        color = QuietSettingsColors.Cream,
        style = TextStyle(
            fontFamily = CapsuleType.QuietAlmanac.bodySans,
            fontSize = 14.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Normal,
        ),
    )
}

@Composable
internal fun QuietRowDescription(text: String) {
    Text(
        text = text,
        color = QuietSettingsColors.CreamDim,
        style = TextStyle(
            fontFamily = CapsuleType.QuietAlmanac.bodySans,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            fontWeight = FontWeight.Normal,
        ),
    )
}

@Composable
internal fun QuietTag(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(QuietSettingsColors.AccentDim)
            .padding(horizontal = 7.dp, vertical = 2.dp),
        color = QuietSettingsColors.Accent,
        style = TextStyle(
            fontFamily = CapsuleType.QuietAlmanac.captionMono,
            fontSize = 8.5.sp,
            lineHeight = 12.sp,
            letterSpacing = 1.2.sp,
        ),
    )
}

@Composable
private fun QuietToggle(checked: Boolean) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 26.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (checked) QuietSettingsColors.Accent else QuietSettingsColors.ToggleOff)
    ) {
        Box(
            modifier = Modifier
                .offset(x = if (checked) 21.dp else 3.dp, y = 3.dp)
                .size(20.dp)
                .clip(CircleShape)
                .background(if (checked) QuietSettingsColors.AccentInk else QuietSettingsColors.Cream)
        )
    }
}

@Composable
internal fun QuietRule() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(QuietSettingsColors.Rule)
    )
}

internal object QuietSettingsColors {
    val BgDeep = Color(0xFF080B14)
    val Cream = Color(0xFFF3EAD8)
    val CreamDim = Color(0x8CF3EAD8)
    val CreamFaint = Color(0x38F3EAD8)
    val Rule = Color(0x1AF3EAD8)
    val Accent = Color(0xFFE8B06A)
    val AccentDim = Color(0x29E8B06A)
    val AccentInk = Color(0xFF1A1206)
    val ToggleOff = Color(0x2EF3EAD8)
    val Red = Color(0xFFD97A6C)
}

@Composable
private fun SettingsNavRow(
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "\u203A",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SettingsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            modifier = Modifier.testTag(SettingsScreenTestTags.PAUSE_TOGGLE),
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

internal object SettingsScreenTestTags {
    const val PAUSE_TOGGLE = "settings-pause-toggle"
    const val CAPTURE_SETUP_ROW = "settings-capture-setup-row"
}
