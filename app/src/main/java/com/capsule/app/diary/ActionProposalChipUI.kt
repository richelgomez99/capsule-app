package com.capsule.app.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.capsule.app.data.ipc.ActionProposalParcel
import kotlinx.coroutines.flow.Flow

/**
 * T051 — chip-row rendered inline beneath an [com.capsule.app.diary.ui.EnvelopeCard]
 * to surface action proposals for that envelope.
 *
 * Subscription is owned by this composable's lifecycle: [produceState]
 * keyed on [envelopeId] collects the live [Flow] from [DiaryViewModel].
 * Only proposals with `state == "PROPOSED"` render — confirmed/dismissed/
 * failed proposals drop off the chip-row immediately on state change.
 *
 * Visual placement only — typography/colour deferred to design.md / spec
 * 010 per tasks.md T051.
 */
@Composable
fun ActionProposalChipRow(
    envelopeId: String,
    proposalsFlow: Flow<List<ActionProposalParcel>>,
    onChipTap: (ActionProposalParcel) -> Unit,
    modifier: Modifier = Modifier
) {
    val proposalsState: State<List<ActionProposalParcel>> = produceState(
        initialValue = emptyList(),
        key1 = envelopeId,
        key2 = proposalsFlow
    ) {
        proposalsFlow.collect { value = it }
    }
    val proposals = proposalsState.value.filter { it.state == "PROPOSED" }

    if (proposals.isEmpty()) return

    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(proposals, key = { it.id }) { proposal ->
            AssistChip(
                onClick = { onChipTap(proposal) },
                label = {
                    Text(
                        text = proposal.previewTitle,
                        style = MaterialTheme.typography.labelMedium
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                )
            )
        }
    }
}
