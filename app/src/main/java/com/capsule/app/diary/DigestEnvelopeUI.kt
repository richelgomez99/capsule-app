package com.capsule.app.diary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.model.EnvelopeKind

/**
 * T076 / 003 US3 — DIGEST envelope card. Visual specifics deferred to
 * design.md / spec 010; this composable wires placement only so the
 * diary UI can render `kind = DIGEST` rows above the cluster card per
 * weekly-digest-contract.md §7.
 *
 * Test contract: exposes `Modifier.testTag("digest-envelope")` so
 * [com.capsule.app.diary.DiaryDigestRenderingTest] can locate the
 * card unambiguously even when several DIGESTs cohabit a Sunday
 * (shouldn't happen — partial unique index — but the tag is stable).
 */
@Composable
fun DigestEnvelopeUI(
    envelope: IntentEnvelopeEntity,
    modifier: Modifier = Modifier
) {
    require(envelope.kind == EnvelopeKind.DIGEST) {
        "DigestEnvelopeUI is only valid for kind=DIGEST envelopes"
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag(DIGEST_TEST_TAG),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "This week",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = envelope.textContent.orEmpty(),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

const val DIGEST_TEST_TAG = "digest-envelope"
