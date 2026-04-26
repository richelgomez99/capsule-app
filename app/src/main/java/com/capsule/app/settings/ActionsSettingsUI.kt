package com.capsule.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.capsule.app.R

/**
 * T079 / 003 US4 — Settings → Actions screen. One row per registered
 * skill with displayName, success/cancel/latency stats, and a
 * per-skill enable toggle. Plus a "Forget remembered to-do app" row
 * (T065) when a target is currently remembered.
 *
 * Visual finalisation deferred to spec 010; this composable wires
 * placement + test hooks only. Stats formatting lives in
 * [com.capsule.app.settings.ActionsSettingsFormat] so it's
 * unit-testable on the JVM.
 */
@Composable
fun ActionsSettingsUI(
    rows: List<SkillSettingsRow>,
    rememberedTodoPackage: String?,
    onToggleSkill: (skillId: String, enabled: Boolean) -> Unit,
    onClearRememberedTodoTarget: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth().testTag(ROOT_TEST_TAG)) {
        Text(
            text = stringResource(R.string.actions_settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp)
        )

        if (rememberedTodoPackage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag(REMEMBERED_TARGET_TEST_TAG),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.share_target_remembered_package),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        rememberedTodoPackage,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                TextButton(
                    onClick = onClearRememberedTodoTarget,
                    modifier = Modifier.testTag(REMEMBERED_TARGET_CLEAR_TEST_TAG)
                ) {
                    Text("Forget")
                }
            }
            Divider()
        }

        if (rows.isEmpty()) {
            Text(
                stringResource(R.string.actions_settings_no_skills),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(rows, key = { it.skillId }) { row ->
                    SkillRow(row = row, onToggleSkill = onToggleSkill)
                    Divider()
                }
            }
        }
    }
}

@Composable
private fun SkillRow(
    row: SkillSettingsRow,
    onToggleSkill: (String, Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(skillRowTestTag(row.skillId)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(row.displayName, style = MaterialTheme.typography.bodyLarge)
            Text(
                ActionsSettingsFormat.formatStats(row),
                style = MaterialTheme.typography.bodySmall
            )
        }
        Switch(
            checked = row.enabled,
            onCheckedChange = { onToggleSkill(row.skillId, it) },
            modifier = Modifier.testTag(skillToggleTestTag(row.skillId))
        )
    }
}

/**
 * Per-skill row data for [ActionsSettingsUI]. Pure data so the screen
 * can be hoisted in tests with hand-built rows; the
 * `ActionsSettingsViewModel` (deferred) projects from
 * `AppFunctionSkillEntity` + `SkillStats`.
 */
data class SkillSettingsRow(
    val skillId: String,
    val displayName: String,
    val enabled: Boolean,
    val invocationCount: Int,
    val successRate: Double?,
    val cancelRate: Double?,
    val avgLatencyMs: Double?
)

const val ROOT_TEST_TAG = "actions-settings-root"
const val REMEMBERED_TARGET_TEST_TAG = "actions-settings-remembered-target"
const val REMEMBERED_TARGET_CLEAR_TEST_TAG = "actions-settings-remembered-clear"
fun skillRowTestTag(skillId: String): String = "actions-settings-row-$skillId"
fun skillToggleTestTag(skillId: String): String = "actions-settings-toggle-$skillId"

/**
 * Pure formatting helpers — JVM-testable.
 */
object ActionsSettingsFormat {
    fun formatStats(row: SkillSettingsRow): String {
        if (row.invocationCount == 0) {
            return "Never used"
        }
        val success = row.successRate?.let { "${(it * 100).toInt()}% success" } ?: "—"
        val cancel = row.cancelRate?.let { "${(it * 100).toInt()}% cancelled" } ?: "—"
        val latency = row.avgLatencyMs?.let { "${it.toInt()} ms" } ?: "—"
        return "$success • $cancel • $latency • ${row.invocationCount} run(s)"
    }
}
