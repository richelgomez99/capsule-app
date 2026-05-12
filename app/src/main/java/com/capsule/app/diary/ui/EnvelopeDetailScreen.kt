package com.capsule.app.diary.ui

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.capsule.app.data.ipc.AuditEntryParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.toIntentOrAmbiguous
import com.capsule.app.diary.EnvelopeDetailUiState
import com.capsule.app.diary.EnvelopeDetailViewModel
import com.capsule.app.diary.IntentHistoryRow
import com.capsule.app.settings.QuietRule
import com.capsule.app.settings.QuietSettingsColors
import com.capsule.app.ui.IntentChipPicker
import com.capsule.app.ui.primitives.MonoLabel
import com.capsule.app.ui.primitives.SourceGlyph
import com.capsule.app.ui.primitives.SourceGlyphKind
import com.capsule.app.ui.primitives.SourceIdentityResolver
import com.capsule.app.ui.theme.LocalRuntimeFlags
import com.capsule.app.ui.tokens.CapsuleType
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * T055a — full-screen view of a single envelope.
 *
 * Opens from [EnvelopeCard]'s non-pill tap. Layout top-down:
 *   1. TopAppBar (back + overflow: Archive / Delete / Open URL / Copy / Share)
 *   2. Intent picker (reusing [IntentChipPicker] in expanded form — no countdown)
 *   3. "from {app} · {activity} · {time}" subtitle
 *   4. IMAGE thumbnail (full-width 16:9) if envelope.contentType == IMAGE
 *   5. Title (if hydrated)
 *   6. Summary (no maxLines clamp)
 *   7. Domain chip (tap → ACTION_VIEW)
 *   8. Original captured text, selectable
 *   9. Intent history (oldest-first)
 *  10. Audit trail (hidden if empty)
 *
 * Wraps the whole thing in a single scrolling [LazyColumn] to keep long
 * summaries / long capture bodies navigable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EnvelopeDetailScreen(
    viewModel: EnvelopeDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    startNote: Boolean = false
) {
    val state by viewModel.state.collectAsState()
    val finished by viewModel.finished.collectAsState()
    val context = LocalContext.current

    if (finished) {
        // Activity will observe [finished] and close; the Compose tree
        // stops recomposing after that. Short guard against a stale
        // render flashing an empty state.
        return
    }

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var noteDialogOpen by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    var consumedStartNote by remember { mutableStateOf(false) }

    LaunchedEffect(state, startNote) {
        val ready = state as? EnvelopeDetailUiState.Ready
        if (startNote && !consumedStartNote && ready != null) {
            noteDraft = ready.latestNote.orEmpty()
            noteDialogOpen = true
            consumedStartNote = true
        }
    }

    if (LocalRuntimeFlags.current.useNewVisualLanguage) {
        QuietEnvelopeDetailScreen(
            state = state,
            menuOpen = menuOpen,
            confirmDelete = confirmDelete,
            onMenuOpenChange = { menuOpen = it },
            onConfirmDeleteChange = { confirmDelete = it },
            onBack = onBack,
            onArchive = viewModel::onArchive,
            onDelete = viewModel::onDelete,
            onReassign = { picked -> viewModel.onReassignIntent(picked.name) },
            onRetry = viewModel::onRetryHydration,
            modifier = modifier,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                title = { Text("Capture") },
                actions = {
                    val ready = state as? EnvelopeDetailUiState.Ready
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More")
                    }
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = { menuOpen = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Archive") },
                            onClick = {
                                menuOpen = false
                                viewModel.onArchive()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                menuOpen = false
                                confirmDelete = true
                            }
                        )
                        val url = ready?.envelope?.canonicalUrl
                            ?: firstUrlIn(ready?.envelope?.textContent)
                        if (!url.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("Open original URL") },
                                onClick = {
                                    menuOpen = false
                                    openUrl(context, url)
                                }
                            )
                        }
                        val text = ready?.envelope?.textContent
                        if (!text.isNullOrBlank()) {
                            DropdownMenuItem(
                                text = { Text("Copy text") },
                                onClick = {
                                    menuOpen = false
                                    copyText(context, text)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Share") },
                                onClick = {
                                    menuOpen = false
                                    shareText(context, text)
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            when (val s = state) {
                is EnvelopeDetailUiState.Loading -> LoadingBox()
                is EnvelopeDetailUiState.Error -> ErrorBox(s.message)
                is EnvelopeDetailUiState.Ready -> ReadyContent(
                    envelope = s.envelope,
                    latestNote = s.latestNote,
                    intentHistory = s.intentHistory,
                    auditTrail = s.auditTrail,
                    onReassign = { picked -> viewModel.onReassignIntent(picked.name) },
                    onRetry = { viewModel.onRetryHydration() },
                    onEditNote = {
                        noteDraft = s.latestNote.orEmpty()
                        noteDialogOpen = true
                    }
                )
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this capture?") },
            text = {
                Text(
                    "It moves to the trash and is permanently removed after 30 days. " +
                        "You can restore it from Settings \u2192 Trash until then."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    if (noteDialogOpen) {
        AlertDialog(
            onDismissRequest = { noteDialogOpen = false },
            title = { Text("Capture note") },
            text = {
                OutlinedTextField(
                    value = noteDraft,
                    onValueChange = { noteDraft = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Note") },
                    minLines = 3
                )
            },
            confirmButton = {
                TextButton(
                    enabled = noteDraft.isNotBlank(),
                    onClick = {
                        noteDialogOpen = false
                        viewModel.onSaveNote(noteDraft)
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { noteDialogOpen = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun QuietEnvelopeDetailScreen(
    state: EnvelopeDetailUiState,
    menuOpen: Boolean,
    confirmDelete: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onConfirmDeleteChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onReassign: (Intent) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QuietSettingsColors.BgDeep),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "‹",
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .clickable(onClick = onBack)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                color = QuietSettingsColors.Cream,
                style = TextStyle(
                    fontFamily = CapsuleType.QuietAlmanac.bodySans,
                    fontSize = 28.sp,
                    lineHeight = 28.sp,
                    letterSpacing = 0.sp,
                ),
            )
            Column(modifier = Modifier.weight(1f)) {
                MonoLabel(text = "CAPTURE", color = QuietSettingsColors.CreamDim, size = 9.sp)
                Text(
                    text = (state as? EnvelopeDetailUiState.Ready)?.envelope?.title?.takeIf { it.isNotBlank() }
                        ?: "Saved detail",
                    color = QuietSettingsColors.Cream,
                    maxLines = 1,
                    style = TextStyle(
                        fontFamily = CapsuleType.QuietAlmanac.bodySans,
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                    ),
                )
            }

            val ready = state as? EnvelopeDetailUiState.Ready
            Box {
                IconButton(onClick = { onMenuOpenChange(true) }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = "More",
                        tint = QuietSettingsColors.Cream,
                    )
                }
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { onMenuOpenChange(false) },
                ) {
                    DropdownMenuItem(
                        text = { Text("Archive") },
                        onClick = {
                            onMenuOpenChange(false)
                            onArchive()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            onMenuOpenChange(false)
                            onConfirmDeleteChange(true)
                        },
                    )
                    val url = ready?.envelope?.canonicalUrl ?: firstUrlIn(ready?.envelope?.textContent)
                    if (!url.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = { Text("Open original URL") },
                            onClick = {
                                onMenuOpenChange(false)
                                openUrl(context, url)
                            },
                        )
                    }
                    val text = ready?.envelope?.textContent
                    if (!text.isNullOrBlank()) {
                        DropdownMenuItem(
                            text = { Text("Copy text") },
                            onClick = {
                                onMenuOpenChange(false)
                                copyText(context, text)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                onMenuOpenChange(false)
                                shareText(context, text)
                            },
                        )
                    }
                }
            }
        }

        QuietRule()

        when (state) {
            is EnvelopeDetailUiState.Loading -> QuietLoadingBox()
            is EnvelopeDetailUiState.Error -> QuietErrorBox(state.message)
            is EnvelopeDetailUiState.Ready -> QuietReadyContent(
                envelope = state.envelope,
                intentHistory = state.intentHistory,
                auditTrail = state.auditTrail,
                onReassign = onReassign,
                onRetry = onRetry,
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { onConfirmDeleteChange(false) },
            title = { Text("Delete this capture?") },
            text = {
                Text(
                    "It moves to the trash and is permanently removed after 30 days. " +
                        "You can restore it from Settings → Trash until then."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmDeleteChange(false)
                    onDelete()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { onConfirmDeleteChange(false) }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun QuietLoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = QuietSettingsColors.Accent)
    }
}

@Composable
private fun QuietErrorBox(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                text = "Couldn't load this capture",
                color = QuietSettingsColors.Red,
                style = TextStyle(
                    fontFamily = CapsuleType.QuietAlmanac.displaySerif,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                    fontStyle = FontStyle.Italic,
                    letterSpacing = 0.sp,
                ),
            )
            Text(
                text = message,
                color = QuietSettingsColors.CreamDim,
                style = TextStyle(
                    fontFamily = CapsuleType.QuietAlmanac.bodySans,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    letterSpacing = 0.sp,
                ),
            )
        }
    }
}

@Composable
private fun QuietReadyContent(
    envelope: EnvelopeViewParcel,
    intentHistory: List<IntentHistoryRow>,
    auditTrail: List<AuditEntryParcel>,
    onReassign: (Intent) -> Unit,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item(key = "source") { QuietSourceHeader(envelope) }
        item(key = "intent") {
            QuietDetailSection(label = "Intent") {
                IntentChipPicker(
                    currentIntent = envelope.intent.toIntentOrAmbiguous(),
                    onPick = { onReassign(it) },
                )
            }
        }

        if (envelope.contentType == "IMAGE" && !envelope.imageUri.isNullOrBlank()) {
            item(key = "image") {
                AsyncImage(
                    model = envelope.imageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        val title = envelope.title
        val summary = envelope.summary
        if (!title.isNullOrBlank() || !summary.isNullOrBlank()) {
            item(key = "enrichment") {
                QuietDetailSection(label = "Enriched") {
                    title?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = QuietSettingsColors.Cream,
                            style = TextStyle(
                                fontFamily = CapsuleType.QuietAlmanac.displaySerif,
                                fontSize = 23.sp,
                                lineHeight = 29.sp,
                                fontWeight = FontWeight.Normal,
                                letterSpacing = 0.sp,
                            ),
                        )
                    }
                    summary?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            color = QuietSettingsColors.CreamDim,
                            style = quietBodyStyle(),
                        )
                    }
                }
            }
        }

        val domain = envelope.domain
        if (!domain.isNullOrBlank()) {
            item(key = "domain") {
                QuietDomainRow(domain = domain, onTap = {
                    val url = envelope.canonicalUrl ?: firstUrlIn(envelope.textContent)
                    if (!url.isNullOrBlank()) openUrl(context, url)
                })
            }
        }

        if (title.isNullOrBlank() && summary.isNullOrBlank() && domain.isNullOrBlank()
            && containsUrl(envelope.textContent)
        ) {
            item(key = "retry") {
                Text(
                    text = "Link not enriched yet · Tap to retry",
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .clickable { onRetry() }
                        .background(QuietSettingsColors.AccentDim)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = QuietSettingsColors.Accent,
                    style = TextStyle(
                        fontFamily = CapsuleType.QuietAlmanac.bodySans,
                        fontSize = 13.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.sp,
                    ),
                )
            }
        }

        envelope.textContent?.takeIf { it.isNotBlank() }?.let { text ->
            item(key = "text") {
                QuietDetailSection(label = "Captured text") {
                    SelectionContainer {
                        Text(
                            text = text,
                            color = QuietSettingsColors.Cream,
                            style = TextStyle(
                                fontFamily = CapsuleType.QuietAlmanac.displaySerif,
                                fontSize = 18.sp,
                                lineHeight = 25.sp,
                                fontStyle = FontStyle.Italic,
                                letterSpacing = 0.sp,
                            ),
                        )
                    }
                }
            }
        }

        if (intentHistory.isNotEmpty()) {
            item(key = "history-label") { MonoLabel(text = "INTENT HISTORY", color = QuietSettingsColors.CreamDim, size = 9.sp) }
            items(intentHistory, key = { "h-${it.atMillis}-${it.intent}" }) { row -> QuietIntentHistoryRow(row) }
        }

        if (auditTrail.isNotEmpty()) {
            item(key = "audit-label") { MonoLabel(text = "AUDIT TRAIL", color = QuietSettingsColors.CreamDim, size = 9.sp) }
            items(auditTrail, key = { "a-${it.id}" }) { entry -> QuietAuditTrailRow(entry) }
        }

        item(key = "footer-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun QuietSourceHeader(envelope: EnvelopeViewParcel) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(QuietSettingsColors.Rule)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SourceGlyph(kind = envelope.toDetailSourceGlyphKind(), size = 28.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = buildDetailSubtitle(envelope),
                color = QuietSettingsColors.Cream,
                style = TextStyle(
                    fontFamily = CapsuleType.QuietAlmanac.bodySans,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                ),
            )
            MonoLabel(text = envelope.intent.humanize().uppercase(Locale.ROOT), color = QuietSettingsColors.CreamFaint, size = 8.5.sp)
        }
    }
}

@Composable
private fun QuietDetailSection(
    label: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MonoLabel(text = label.uppercase(Locale.ROOT), color = QuietSettingsColors.CreamDim, size = 9.sp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(QuietSettingsColors.Rule)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

private fun EnvelopeViewParcel.toDetailSourceGlyphKind(): SourceGlyphKind =
    SourceIdentityResolver.glyphKind(
        textContent = textContent,
        canonicalUrl = canonicalUrl,
        sourceAppLabel = sourceAppLabel,
        appCategory = appCategory,
    )

@Composable
private fun QuietDomainRow(domain: String, onTap: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(QuietSettingsColors.AccentDim)
            .clickable(onClick = onTap)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(QuietSettingsColors.Accent),
        )
        Text(
            text = domain,
            color = QuietSettingsColors.Accent,
            style = TextStyle(
                fontFamily = CapsuleType.QuietAlmanac.bodySans,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.sp,
            ),
        )
    }
}

@Composable
private fun QuietIntentHistoryRow(row: IntentHistoryRow) {
    QuietTimelineRow(
        title = row.intent.toIntentOrAmbiguous().displayLabel(),
        meta = "${row.source.humanize()} · ${formatAbsolute(row.atMillis)}",
        tone = QuietSettingsColors.Cream,
    )
}

@Composable
private fun QuietAuditTrailRow(entry: AuditEntryParcel) {
    QuietTimelineRow(
        title = entry.action.humanize(),
        meta = formatAbsolute(entry.atMillis),
        body = entry.description,
        tone = QuietSettingsColors.Accent,
    )
}

@Composable
private fun QuietTimelineRow(
    title: String,
    meta: String,
    body: String? = null,
    tone: Color,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(QuietSettingsColors.Rule)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = tone,
                style = TextStyle(
                    fontFamily = CapsuleType.QuietAlmanac.bodySans,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.sp,
                ),
            )
            Text(text = "·", color = QuietSettingsColors.CreamFaint)
            MonoLabel(text = meta, color = QuietSettingsColors.CreamDim, size = 8.sp)
        }
        body?.let {
            Text(text = it, color = QuietSettingsColors.CreamDim, style = quietBodyStyle())
        }
    }
}

private fun quietBodyStyle() = TextStyle(
    fontFamily = CapsuleType.QuietAlmanac.bodySans,
    fontSize = 13.sp,
    lineHeight = 19.sp,
    letterSpacing = 0.sp,
)

@Composable
private fun LoadingBox() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun ErrorBox(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = "Couldn't load this capture",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ReadyContent(
    envelope: EnvelopeViewParcel,
    latestNote: String?,
    intentHistory: List<IntentHistoryRow>,
    auditTrail: List<AuditEntryParcel>,
    onReassign: (Intent) -> Unit,
    onRetry: () -> Unit,
    onEditNote: () -> Unit
) {
    val context = LocalContext.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 2. Intent picker (expanded, no countdown).
        item(key = "picker") {
            IntentChipPicker(
                currentIntent = envelope.intent.toIntentOrAmbiguous(),
                onPick = { onReassign(it) }
            )
        }

        // 3. Subtitle.
        item(key = "subtitle") {
            Text(
                text = buildDetailSubtitle(envelope),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item(key = "note") {
            NoteBlock(note = latestNote, onEdit = onEditNote)
        }

        // 4. IMAGE thumbnail.
        if (envelope.contentType == "IMAGE" && !envelope.imageUri.isNullOrBlank()) {
            item(key = "image") {
                AsyncImage(
                    model = envelope.imageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
            }
        }

        // 5. Title.
        val title = envelope.title
        if (!title.isNullOrBlank()) {
            item(key = "title") {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // 6. Summary.
        val summary = envelope.summary
        if (!summary.isNullOrBlank()) {
            item(key = "summary") {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 7. Domain chip.
        val domain = envelope.domain
        if (!domain.isNullOrBlank()) {
            item(key = "domain") {
                DomainChip(domain = domain, onTap = {
                    val url = envelope.canonicalUrl ?: firstUrlIn(envelope.textContent)
                    if (!url.isNullOrBlank()) openUrl(context, url)
                })
            }
        }

        // Retry row when URL present but nothing hydrated.
        if (title.isNullOrBlank() && summary.isNullOrBlank() && domain.isNullOrBlank()
            && containsUrl(envelope.textContent)
        ) {
            item(key = "retry") {
                Text(
                    text = "Link not enriched yet · Tap to retry",
                    modifier = Modifier.clickable { onRetry() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // 8. Original captured text.
        val text = envelope.textContent
        if (!text.isNullOrBlank()) {
            item(key = "text-label") {
                SectionLabel("Captured text")
            }
            item(key = "text") {
                SelectionContainer {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(12.dp)
                    )
                }
            }
        }

        // 9. Intent history.
        if (intentHistory.isNotEmpty()) {
            item(key = "history-label") {
                SectionLabel("Intent history")
            }
            items(intentHistory, key = { "h-${it.atMillis}-${it.intent}" }) { row ->
                IntentHistoryRowView(row)
            }
        }

        // 10. Audit trail.
        if (auditTrail.isNotEmpty()) {
            item(key = "audit-label") {
                SectionLabel("Audit trail")
            }
            items(auditTrail, key = { "a-${it.id}" }) { entry ->
                AuditRowView(entry)
            }
        }

        item(key = "footer-spacer") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun NoteBlock(note: String?, onEdit: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onEdit),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = if (note.isNullOrBlank()) "Add note" else "Note",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
            if (!note.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DomainChip(domain: String, onTap: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(50),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onTap)
    ) {
        Text(
            text = domain,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
        modifier = Modifier.padding(top = 8.dp)
    )
}

@Composable
private fun IntentHistoryRowView(row: IntentHistoryRow) {
    val intent = row.intent.toIntentOrAmbiguous()
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = intent.displayLabel(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Text("\u00B7", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = row.source.humanize(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("\u00B7", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text = formatAbsolute(row.atMillis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AuditRowView(entry: AuditEntryParcel) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = entry.action,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = formatAbsolute(entry.atMillis),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(
            text = entry.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

// ---- helpers ----

private fun Intent.displayLabel(): String = when (this) {
    Intent.WANT_IT -> "Want it"
    Intent.REFERENCE -> "Reference"
    Intent.READ_LATER -> "Read later"
    Intent.FOR_SOMEONE -> "For someone"
    Intent.INTERESTING -> "Interesting"
    Intent.AMBIGUOUS -> "Unassigned"
}

private fun String.humanize(): String =
    lowercase(Locale.ROOT)
        .replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.ROOT) }

private fun buildDetailSubtitle(env: EnvelopeViewParcel): String {
    val app = env.appCategory.let {
        if (it == "UNKNOWN_SOURCE") "an app"
        else it.lowercase(Locale.ROOT).replace('_', ' ').replaceFirstChar { c -> c.titlecase(Locale.ROOT) }
    }
    val activity = env.activityState
        .takeUnless { it == "UNKNOWN" }
        ?.lowercase(Locale.ROOT)
        ?.replace('_', ' ')
        ?.replaceFirstChar { it.titlecase(Locale.ROOT) }
    val time = formatAbsolute(env.createdAtMillis)
    return buildString {
        append("from ").append(app)
        if (activity != null) append(" \u00B7 ").append(activity)
        append(" \u00B7 ").append(time)
    }
}

private val absoluteFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d · h:mm a", Locale.getDefault())

private fun formatAbsolute(epochMillis: Long): String =
    if (epochMillis <= 0) "—"
    else Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(absoluteFormatter)

private val URL_REGEX = Regex(
    """https?://[A-Za-z0-9._~:/?#\[\]@!${'$'}&'()*+,;=%\-]+""",
    RegexOption.IGNORE_CASE
)

private fun containsUrl(text: String?): Boolean =
    !text.isNullOrBlank() && URL_REGEX.containsMatchIn(text)

private fun firstUrlIn(text: String?): String? {
    if (text.isNullOrBlank()) return null
    return URL_REGEX.find(text)?.value
}

private fun openUrl(context: Context, url: String) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app can open this link", Toast.LENGTH_SHORT).show()
    }
}

private fun copyText(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("Orbit capture", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

private fun shareText(context: Context, text: String) {
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, text)
        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = android.content.Intent.createChooser(send, null)
        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(chooser)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "No app to share with", Toast.LENGTH_SHORT).show()
    }
}
