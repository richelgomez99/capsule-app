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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.capsule.app.data.ipc.AuditEntryParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel
import com.capsule.app.data.model.Intent
import com.capsule.app.diary.EnvelopeDetailUiState
import com.capsule.app.diary.EnvelopeDetailViewModel
import com.capsule.app.diary.IntentHistoryRow
import com.capsule.app.ui.IntentChipPicker
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
    modifier: Modifier = Modifier
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
                    intentHistory = s.intentHistory,
                    auditTrail = s.auditTrail,
                    onReassign = { picked -> viewModel.onReassignIntent(picked.name) },
                    onRetry = { viewModel.onRetryHydration() }
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
}

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
    intentHistory: List<IntentHistoryRow>,
    auditTrail: List<AuditEntryParcel>,
    onReassign: (Intent) -> Unit,
    onRetry: () -> Unit
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

private fun String.toIntentOrAmbiguous(): Intent =
    runCatching { Intent.valueOf(this) }.getOrElse { Intent.AMBIGUOUS }

private fun Intent.displayLabel(): String = when (this) {
    Intent.WANT_IT -> "Want it"
    Intent.REFERENCE -> "Reference"
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
