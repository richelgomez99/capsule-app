package com.capsule.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.capsule.app.data.ipc.EnvelopeViewParcel
import com.capsule.app.ui.primitives.MonoLabel
import com.capsule.app.ui.theme.LocalRuntimeFlags
import com.capsule.app.ui.tokens.CapsuleType
import kotlin.math.max
import kotlin.math.min

/**
 * T091a — Trash viewer for soft-deleted envelopes.
 *
 * Lists entries newest-deleted first, shows "deleted N days ago" +
 * "auto-purges in M days", and offers Restore / Purge now per row.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    viewModel: TrashViewModel,
    onBack: () -> Unit,
    nowMillisProvider: () -> Long = { System.currentTimeMillis() },
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsState()
    var pendingPurge by remember { mutableStateOf<String?>(null) }
    val useNewVisualLanguage = LocalRuntimeFlags.current.useNewVisualLanguage

    if (useNewVisualLanguage) {
        QuietTrashScreenContent(
            state = state,
            onBack = onBack,
            nowMillisProvider = nowMillisProvider,
            onRestore = viewModel::onRestore,
            onPurgeRequest = { pendingPurge = it },
            modifier = modifier,
        )
    } else {
        LegacyTrashScreenContent(
            state = state,
            onBack = onBack,
            nowMillisProvider = nowMillisProvider,
            onRestore = viewModel::onRestore,
            onPurgeRequest = { pendingPurge = it },
            modifier = modifier,
        )
    }

    pendingPurge?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingPurge = null },
            title = { Text("Purge now?") },
            text = { Text("This capture will be permanently removed. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    pendingPurge = null
                    viewModel.onPurge(id)
                }) { Text("Purge") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPurge = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun LegacyTrashScreenContent(
    state: TrashUiState,
    onBack: () -> Unit,
    nowMillisProvider: () -> Long,
    onRestore: (String) -> Unit,
    onPurgeRequest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {

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
                title = { Text("Trash") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
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
                is TrashUiState.Loading -> LoadingBox()
                is TrashUiState.Error -> ErrorBox(s.message)
                is TrashUiState.Ready -> if (s.envelopes.isEmpty()) {
                    EmptyBox(retentionDays = s.retentionDays)
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item(key = "header") {
                            Text(
                                text = "Auto-purges ${s.retentionDays} days after deletion",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        items(s.envelopes, key = { it.id }) { env ->
                            TrashRow(
                                envelope = env,
                                nowMillis = nowMillisProvider(),
                                retentionDays = s.retentionDays,
                                onRestore = { onRestore(env.id) },
                                onPurge = { onPurgeRequest(env.id) }
                            )
                        }
                        item(key = "footer-space") { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuietTrashScreenContent(
    state: TrashUiState,
    onBack: () -> Unit,
    nowMillisProvider: () -> Long,
    onRestore: (String) -> Unit,
    onPurgeRequest: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(QuietSettingsColors.BgDeep),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 24.dp, top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
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
                    fontWeight = FontWeight.Normal,
                ),
            )
            Text(
                text = "Trash",
                color = QuietSettingsColors.Cream,
                style = TextStyle(
                    fontFamily = CapsuleType.QuietAlmanac.bodySans,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
        QuietRule()

        when (val s = state) {
            is TrashUiState.Loading -> LoadingBox()
            is TrashUiState.Error -> ErrorBox(s.message)
            is TrashUiState.Ready -> if (s.envelopes.isEmpty()) {
                QuietEmptyBox(retentionDays = s.retentionDays)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 22.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item(key = "header") {
                        MonoLabel(
                            text = "Auto-purges ${s.retentionDays} days after deletion",
                            color = QuietSettingsColors.CreamDim,
                            size = 9.5.sp,
                        )
                    }
                    items(s.envelopes, key = { it.id }) { env ->
                        QuietTrashRow(
                            envelope = env,
                            nowMillis = nowMillisProvider(),
                            retentionDays = s.retentionDays,
                            onRestore = { onRestore(env.id) },
                            onPurge = { onPurgeRequest(env.id) },
                        )
                    }
                    item(key = "footer-space") { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

@Composable
private fun QuietEmptyBox(retentionDays: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text(
                "Trash is empty",
                color = QuietSettingsColors.Cream,
                style = TextStyle(
                    fontFamily = CapsuleType.QuietAlmanac.displaySerif,
                    fontSize = 22.sp,
                    lineHeight = 28.sp,
                ),
            )
            QuietRowDescription(
                "Deleted captures show up here for $retentionDays days before being permanently removed."
            )
        }
    }
}

@Composable
private fun QuietTrashRow(
    envelope: EnvelopeViewParcel,
    nowMillis: Long,
    retentionDays: Int,
    onRestore: () -> Unit,
    onPurge: () -> Unit,
) {
    val preview = envelope.title?.takeIf { it.isNotBlank() }
        ?: envelope.textContent?.take(140)?.replace('\n', ' ')
        ?: "(no preview)"

    val deletedAt = envelope.deletedAtMillis
    val daysSinceDelete: Int = if (deletedAt != null) {
        max(0, ((nowMillis - deletedAt) / ONE_DAY_MILLIS).toInt())
    } else 0
    val daysUntilPurge: Int = if (deletedAt != null) {
        min(retentionDays, max(0, retentionDays - daysSinceDelete))
    } else retentionDays
    val lifecycleLine = buildString {
        if (deletedAt != null) {
            append(daysLabel(daysSinceDelete, prefix = "deleted ", suffix = " ago"))
            append(" \u00B7 ")
        }
        append("auto-purges in ")
        append(daysLabel(daysUntilPurge))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(QuietSettingsColors.AccentDim)
            .padding(14.dp),
    ) {
        Text(
            text = preview,
            color = QuietSettingsColors.Cream,
            style = TextStyle(
                fontFamily = CapsuleType.QuietAlmanac.displaySerif,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontStyle = FontStyle.Italic,
            ),
            maxLines = 3,
        )
        Spacer(Modifier.height(6.dp))
        MonoLabel(
            text = lifecycleLine,
            color = QuietSettingsColors.CreamDim,
            size = 8.5.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = onRestore) {
                Text("Restore", color = QuietSettingsColors.Accent)
            }
            TextButton(onClick = onPurge) {
                Text("Purge now", color = QuietSettingsColors.Red)
            }
        }
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
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Couldn't load trash",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyBox(retentionDays: Int) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "Trash is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                "Deleted captures show up here for $retentionDays days before being permanently removed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrashRow(
    envelope: EnvelopeViewParcel,
    nowMillis: Long,
    retentionDays: Int,
    onRestore: () -> Unit,
    onPurge: () -> Unit
) {
    val preview = envelope.title?.takeIf { it.isNotBlank() }
        ?: envelope.textContent?.take(140)?.replace('\n', ' ')
        ?: "(no preview)"

    val deletedAt = envelope.deletedAtMillis
    val daysSinceDelete: Int = if (deletedAt != null) {
        max(0, ((nowMillis - deletedAt) / ONE_DAY_MILLIS).toInt())
    } else 0
    val daysUntilPurge: Int = if (deletedAt != null) {
        min(retentionDays, max(0, retentionDays - daysSinceDelete))
    } else retentionDays

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(4.dp))
            val lifecycleLine = buildString {
                if (deletedAt != null) {
                    append(daysLabel(daysSinceDelete, prefix = "deleted ", suffix = " ago"))
                    append(" \u00B7 ")
                }
                append("auto-purges in ")
                append(daysLabel(daysUntilPurge))
            }
            Text(
                text = lifecycleLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onRestore) { Text("Restore") }
                TextButton(onClick = onPurge) { Text("Purge now") }
            }
        }
    }
}

private const val ONE_DAY_MILLIS: Long = 24L * 60L * 60L * 1000L

private fun daysLabel(days: Int, prefix: String = "", suffix: String = ""): String {
    val word = if (days == 1) "day" else "days"
    val number = if (days == 0) "less than 1" else days.toString()
    return "$prefix$number $word$suffix"
}
