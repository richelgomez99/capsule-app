package com.capsule.app.settings

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.capsule.app.data.ipc.EnvelopeViewParcel
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
                                onRestore = { viewModel.onRestore(env.id) },
                                onPurge = { pendingPurge = env.id }
                            )
                        }
                        item(key = "footer-space") { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
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
