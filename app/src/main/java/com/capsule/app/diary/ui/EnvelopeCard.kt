package com.capsule.app.diary.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.capsule.app.data.ipc.EnvelopeViewParcel
import com.capsule.app.data.model.Intent
import com.capsule.app.ui.IntentChipPicker
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * T051 — per-capture card rendered inside the diary day pager.
 *
 * Shows:
 *   - the current intent as a small pill at the top (tap → expands to the
 *     shared [IntentChipPicker] for reassignment)
 *   - `from {app} · {activity} · {time}` subtitle line
 *   - the envelope's title (once US3 continuation fills it in), otherwise
 *     a single-line preview of the raw text
 *   - the summary (once US3 fills it in)
 *
 * Tap-to-reassign routes through [onReassign]; production callers pass
 * `vm::onReassignIntent`. Per FR-007: supports AMBIGUOUS → any intent and
 * any intent → any other intent.
 */
@Composable
fun EnvelopeCard(
    envelope: EnvelopeViewParcel,
    onReassign: (envelopeId: String, newIntent: Intent) -> Unit,
    modifier: Modifier = Modifier,
    onRetry: ((envelopeId: String) -> Unit)? = null,
    onDelete: ((envelopeId: String) -> Unit)? = null,
    onOpenDetail: ((envelopeId: String) -> Unit)? = null,
    /**
     * T064 (003 US2) — toggle the `done` flag on a derived to-do item.
     * Only fires when [EnvelopeViewParcel.todoMetaJson] is non-null and
     * the user taps the checkbox row. Diary screen wires this to
     * `DiaryViewModel.onToggleTodoItem` which calls
     * `IEnvelopeRepository.setTodoItemDone` on `:ml`.
     */
    onToggleTodoItem: ((envelopeId: String, itemIndex: Int, done: Boolean) -> Unit)? = null
) {
    var pickerOpen by rememberSaveable(envelope.id) { mutableStateOf(false) }
    var confirmDelete by rememberSaveable(envelope.id) { mutableStateOf(false) }

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this capture?") },
            text = {
                Text(
                    "It moves to the trash and is permanently removed after 30 days. " +
                        "You can restore it from Settings → Trash until then."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(envelope.id)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    Card(
        onClick = { onOpenDetail?.invoke(envelope.id) },
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            // Row 1: current intent pill + timestamp, right-aligned.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IntentPill(
                    intent = envelope.intent.toIntentOrAmbiguous(),
                    onTap = { pickerOpen = !pickerOpen }
                )
                Row(
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    Text(
                        text = formatLocalTime(envelope.createdAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (onDelete != null) {
                        IconButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Delete capture",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Row 2: "from {app} · {activity}" subtitle.
            Text(
                text = buildSubtitle(envelope),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            Spacer(Modifier.height(8.dp))

            // T078 (Phase 6 US4) — thumbnail for IMAGE envelopes. Sits
            // above the title/summary rows so the nested "linked article"
            // block rendered by the downstream URL_HYDRATE result stays
            // readable. Coil loads asynchronously and caches.
            if (envelope.contentType == "IMAGE" && !envelope.imageUri.isNullOrBlank()) {
                AsyncImage(
                    model = envelope.imageUri,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.height(10.dp))
            }

            // Row 3: title (US3) or preview (v1). Never render both.
            val titleOrPreview = envelope.title?.takeIf { it.isNotBlank() }
                ?: envelope.textContent?.take(200)?.replace('\n', ' ')
                ?: ""
            if (titleOrPreview.isNotBlank()) {
                Text(
                    text = titleOrPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3
                )
            }

            // Row 4: summary (US3 only).
            val summary = envelope.summary
            if (!summary.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4
                )
            }

            // Row 4b — T064 (003 US2) derived to-do checklist. Rendered
            // only when [todoMetaJson] is non-null. Each tap calls back
            // to the diary VM which writes through `:ml` and emits a
            // refreshed envelope view.
            val todoMeta = envelope.todoMetaJson
            if (!todoMeta.isNullOrBlank() && onToggleTodoItem != null) {
                val items = remember(todoMeta) { parseTodoItems(todoMeta) }
                if (items.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Column {
                        items.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onToggleTodoItem(envelope.id, index, !item.done)
                                    },
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = item.done,
                                    onCheckedChange = { checked ->
                                        onToggleTodoItem(envelope.id, index, checked)
                                    }
                                )
                                Text(
                                    text = item.text,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (item.done)
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    else
                                        MaterialTheme.colorScheme.onSurface,
                                    textDecoration = if (item.done)
                                        androidx.compose.ui.text.style.TextDecoration.LineThrough
                                    else null
                                )
                            }
                        }
                    }
                }
            }

            // Row 5 — T069 domain tag (US3 only, shown once hydration
            // has attached a canonical host).
            val domain = envelope.domain
            if (!domain.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = domain,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Row 6 — T069 retry affordance. Shown when the capture
            // contains a URL but nothing has been hydrated yet (no title,
            // no summary, no domain). Heuristic until ContinuationEntity
            // status surfaces through the parcel in the worker→Room
            // write-back slice. Clicking invokes `onRetry`, which is
            // wired by the Diary screen to `ContinuationEngine.retry`.
            if (onRetry != null && containsUrl(envelope.textContent)
                && envelope.title.isNullOrBlank()
                && envelope.summary.isNullOrBlank()
                && envelope.domain.isNullOrBlank()
            ) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Link not enriched yet · Tap to retry",
                    modifier = Modifier.clickable { onRetry(envelope.id) },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Reassignment row — collapsed by default, expanded on pill tap.
            AnimatedVisibility(visible = pickerOpen) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    IntentChipPicker(
                        currentIntent = envelope.intent.toIntentOrAmbiguous(),
                        onPick = { picked ->
                            pickerOpen = false
                            onReassign(envelope.id, picked)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun IntentPill(intent: Intent, onTap: () -> Unit) {
    val label = intent.displayLabel()
    val contentColor = when (intent) {
        Intent.AMBIGUOUS -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onTap),
        color = when (intent) {
            Intent.AMBIGUOUS -> MaterialTheme.colorScheme.surfaceVariant
            else -> MaterialTheme.colorScheme.primaryContainer
        },
        shape = RoundedCornerShape(50)
    ) {
        // Row with a trailing chevron so the pill *reads* as tappable.
        // Before this, the pill looked static — users had no way to
        // discover that tapping opens the intent reassignment picker.
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                fontWeight = FontWeight.Medium
            )
            Icon(
                imageVector = Icons.Filled.ArrowDropDown,
                contentDescription = "Change intent",
                tint = contentColor,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun buildSubtitle(env: EnvelopeViewParcel): String {
    // T083/T084 (Phase 7 US5) — render the full state label as
    // `from {appCategory} · {activityState} · {localTime}`, with
    // graceful fallbacks when a signal is missing:
    //
    //   * appCategory == UNKNOWN_SOURCE  → "from an app" (no package leak)
    //   * activityState == UNKNOWN       → activity segment dropped
    //   * localTime is always present    → derived from createdAtMillis
    //
    // Middle dot is a regular Unicode "·" which renders correctly in
    // both LTR and RTL (Compose flips the visual order automatically).
    val appPart = "from " + env.appCategory.humanizeApp()
    val activityPart = env.activityState.humanizeActivityOrNull()
    val timePart = formatLocalTime(env.createdAtMillis)

    return buildString {
        append(appPart)
        if (activityPart != null) {
            append(" · ")
            append(activityPart)
        }
        append(" · ")
        append(timePart)
    }
}

/** T084 — humanized app category string; `UNKNOWN_SOURCE` → "an app". */
private fun String.humanizeApp(): String {
    if (this == "UNKNOWN_SOURCE") return "an app"
    return lowercase(Locale.ROOT)
        .replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.ROOT) }
}

/** T084 — `UNKNOWN` drops the whole activity segment, not rendered as "Unknown". */
private fun String.humanizeActivityOrNull(): String? {
    if (this == "UNKNOWN") return null
    return lowercase(Locale.ROOT)
        .replace('_', ' ')
        .replaceFirstChar { it.titlecase(Locale.ROOT) }
}

private fun String.toIntentOrAmbiguous(): Intent =
    runCatching { Intent.valueOf(this) }.getOrElse { Intent.AMBIGUOUS }

private fun Intent.displayLabel(): String = when (this) {
    Intent.WANT_IT -> "Want it"
    Intent.REFERENCE -> "Reference"
    Intent.FOR_SOMEONE -> "For someone"
    Intent.INTERESTING -> "Interesting"
    Intent.AMBIGUOUS -> "Unassigned"
}

private val timeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm a", Locale.ROOT)

private fun formatLocalTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)

// T069 — URL presence check used to decide whether the "Couldn't
// enrich this link. Try again" affordance should show. Matches the
// same http(s) URL-extraction regex used by ContinuationEngine.
private val URL_PRESENCE_REGEX = Regex(
    """https?://[A-Za-z0-9._~:/?#\[\]@!${'$'}&'()*+,;=%\-]+""",
    RegexOption.IGNORE_CASE
)

private fun containsUrl(text: String?): Boolean {
    if (text.isNullOrBlank()) return false
    return URL_PRESENCE_REGEX.containsMatchIn(text)
}

// ---- T064 (003 US2) to-do parsing -----------------------------------

internal data class TodoItem(val text: String, val done: Boolean, val dueEpochMillis: Long?)

/**
 * Tolerant parser for `IntentEnvelopeEntity.todoMetaJson`. Returns an
 * empty list on malformed JSON, missing `items`, or empty array. Used
 * by [EnvelopeCard]'s checklist row.
 *
 * Visible for unit tests in `EnvelopeCardTodoParseTest`.
 */
internal fun parseTodoItems(json: String): List<TodoItem> {
    val obj = runCatching { org.json.JSONObject(json) }.getOrNull() ?: return emptyList()
    val items = obj.optJSONArray("items") ?: return emptyList()
    val out = mutableListOf<TodoItem>()
    for (i in 0 until items.length()) {
        val raw = items.optJSONObject(i) ?: continue
        val text = raw.optString("text").trim()
        if (text.isEmpty()) continue
        val done = raw.optBoolean("done", false)
        val due = if (raw.has("dueEpochMillis") && !raw.isNull("dueEpochMillis"))
            raw.optLong("dueEpochMillis", -1L).takeIf { it >= 0L }
        else null
        out.add(TodoItem(text = text, done = done, dueEpochMillis = due))
    }
    return out
}
