package com.capsule.app.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.capsule.app.data.ipc.ActionProposalParcel
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/**
 * T052 — modal preview/confirm sheet for an [ActionProposalParcel].
 *
 * Renders editable fields for the calendar.createEvent function (the
 * only US1 function); other function ids fall through to a read-only
 * preview pending US2 (todo) / US2b (share). The Confirm button passes
 * the (possibly-edited) argsJson back to [onConfirm]; Cancel/back/scrim
 * dismiss invokes [onDismiss] (which the caller wires to
 * [DiaryViewModel.onDismissProposal]).
 *
 * **Reversibility honesty**: per action-execution-contract.md §5, the
 * sheet shows "Once added, you'll need to edit it in Calendar." for
 * `calendar.createEvent` so the user understands the side effect leaves
 * Orbit before they confirm.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPreviewSheet(
    proposal: ActionProposalParcel,
    onConfirm: (editedArgsJson: String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = proposal.previewTitle,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            proposal.previewSubtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            when (proposal.functionId) {
                "calendar.createEvent" -> CalendarFields(
                    proposal = proposal,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss
                )
                else -> ReadonlyFields(
                    proposal = proposal,
                    onConfirm = onConfirm,
                    onDismiss = onDismiss
                )
            }
        }
    }
}

/** Editable calendar.createEvent fields per CalendarActionHandler args contract. */
@Composable
private fun CalendarFields(
    proposal: ActionProposalParcel,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val parsed = remember(proposal.id) { parseCalendarArgs(proposal.argsJson) }

    var title by rememberSaveable(proposal.id) { mutableStateOf(parsed.title) }
    var startIso by rememberSaveable(proposal.id) { mutableStateOf(parsed.startIso) }
    var endIso by rememberSaveable(proposal.id) { mutableStateOf(parsed.endIso) }
    var location by rememberSaveable(proposal.id) { mutableStateOf(parsed.location) }
    var notes by rememberSaveable(proposal.id) { mutableStateOf(parsed.notes) }
    var tzId by rememberSaveable(proposal.id) { mutableStateOf(parsed.tzId) }
    var error by remember(proposal.id) { mutableStateOf<String?>(null) }

    OutlinedTextField(
        value = title,
        onValueChange = { title = it },
        label = { Text("Title") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = startIso,
        onValueChange = { startIso = it },
        label = { Text("Start (yyyy-MM-ddTHH:mm)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = endIso,
        onValueChange = { endIso = it },
        label = { Text("End (yyyy-MM-ddTHH:mm) — optional") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = location,
        onValueChange = { location = it },
        label = { Text("Location — optional") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = notes,
        onValueChange = { notes = it },
        label = { Text("Notes — optional") },
        modifier = Modifier.fillMaxWidth()
    )
    OutlinedTextField(
        value = tzId,
        onValueChange = { tzId = it },
        label = { Text("Time zone") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth()
    )

    Text(
        text = "Once added, you'll need to edit it in Calendar.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    error?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error
        )
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Button(
            onClick = {
                val zone = runCatching { ZoneId.of(tzId.ifBlank { ZoneId.systemDefault().id }) }
                    .getOrNull() ?: run {
                        error = "Unknown time zone"
                        return@Button
                    }
                val startMillis = parseIsoLocalToEpochMillis(startIso, zone)
                if (startMillis == null) {
                    error = "Start time is required"
                    return@Button
                }
                if (title.isBlank()) {
                    error = "Title is required"
                    return@Button
                }
                val endMillis = parseIsoLocalToEpochMillis(endIso, zone)
                val rebuilt = JSONObject().apply {
                    put("title", title)
                    put("startEpochMillis", startMillis)
                    if (endMillis != null) put("endEpochMillis", endMillis)
                    if (location.isNotBlank()) put("location", location)
                    if (notes.isNotBlank()) put("notes", notes)
                    put("tzId", zone.id)
                }
                onConfirm(rebuilt.toString())
            }
        ) { Text("Add to Calendar") }
    }
}

/**
 * Read-only fallback for non-calendar function ids — confirm passes the
 * original argsJson untouched. US2 (todo) and US2b (share) will replace
 * this with their own editable surfaces.
 */
@Composable
private fun ReadonlyFields(
    proposal: ActionProposalParcel,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Text(
        text = proposal.argsJson,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        TextButton(onClick = onDismiss) { Text("Cancel") }
        Button(onClick = { onConfirm(proposal.argsJson) }) { Text("Confirm") }
    }
}

// ---- Calendar args parsing ------------------------------------------------

internal data class CalendarFormState(
    val title: String,
    val startIso: String,
    val endIso: String,
    val location: String,
    val notes: String,
    val tzId: String
)

/**
 * Parses [argsJson] into the [CalendarFormState] backing the editable
 * fields. Tolerant of missing keys — every field falls back to a sensible
 * default so the user can fill in the gaps.
 *
 * Visible for testing.
 */
internal fun parseCalendarArgs(argsJson: String): CalendarFormState {
    val obj = try { JSONObject(argsJson) } catch (_: JSONException) { JSONObject() }
    val tzId = obj.optString("tzId").ifBlank { ZoneId.systemDefault().id }
    val zone = runCatching { ZoneId.of(tzId) }.getOrDefault(ZoneId.systemDefault())
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    val startIso = obj.optLong("startEpochMillis", -1L)
        .takeIf { it >= 0 }
        ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).format(fmt) }
        ?: ""
    val endIso = obj.optLong("endEpochMillis", -1L)
        .takeIf { it >= 0 }
        ?.let { LocalDateTime.ofInstant(Instant.ofEpochMilli(it), zone).format(fmt) }
        ?: ""
    return CalendarFormState(
        title = obj.optString("title"),
        startIso = startIso,
        endIso = endIso,
        location = obj.optString("location"),
        notes = obj.optString("notes"),
        tzId = zone.id
    )
}

/** Returns null when blank or unparseable. */
internal fun parseIsoLocalToEpochMillis(value: String, zone: ZoneId): Long? {
    if (value.isBlank()) return null
    val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm")
    return try {
        LocalDateTime.parse(value, fmt).atZone(zone).toInstant().toEpochMilli()
    } catch (_: DateTimeParseException) {
        null
    }
}
