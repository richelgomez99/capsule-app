package com.capsule.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    trashCount: Int = 0,
    onOpenTrash: (() -> Unit)? = null,
    onOpenAuditLog: (() -> Unit)? = null,
    onExportData: (() -> Unit)? = null,
    exportInProgress: Boolean = false,
    exportStatus: String? = null
) {
    var localPaused by remember(paused) { mutableStateOf(paused) }
    var showExportConfirm by remember { mutableStateOf(false) }

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
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
