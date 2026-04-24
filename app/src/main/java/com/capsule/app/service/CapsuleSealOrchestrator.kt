package com.capsule.app.service

import android.content.Context
import android.util.Log
import com.capsule.app.ai.IntentPredictor
import com.capsule.app.ai.NanoLlmProvider
import com.capsule.app.capture.SensitivityScrubber
import com.capsule.app.capture.StateSnapshotCollector
import com.capsule.app.data.ipc.IEnvelopeRepository
import com.capsule.app.data.ipc.IntentEnvelopeDraftParcel
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import com.capsule.app.overlay.CapturedContent
import com.capsule.app.overlay.SealOrchestrator
import com.capsule.app.overlay.SealOutcome
import com.capsule.app.overlay.UndoOutcome
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production [SealOrchestrator] bound to an [IEnvelopeRepository] binder in
 * the `:ml` process. Runs the full pre-seal pipeline in the overlay process:
 *
 *   1. [SensitivityScrubber] — redacts keys/tokens/PII before the text ever
 *      crosses the process boundary. Per-type counts travel with the draft
 *      so `:ml` can audit the redaction without seeing the original text.
 *   2. [StateSnapshotCollector] — resolves foreground app + time-of-day.
 *   3. [IntentPredictor] — calls the local LLM with a 1.5 s timeout; any
 *      failure (`TODO()` from the v1 `NanoLlmProvider` stub, timeout, LLM
 *      error) degrades to `AMBIGUOUS/0f` → chip row.
 *   4. Silent-wrap gate — mirrors [com.capsule.app.ai.SilentWrapPredicate]'s
 *      rules (confidence ≥ 0.70 AND prior-match within 30d, never AMBIGUOUS)
 *      but sources the prior-match check through the AIDL-exposed
 *      [IEnvelopeRepository.existsPriorIntent] method so it can run in the
 *      overlay process without direct DB access.
 *   5. [IEnvelopeRepository.seal] — the single cross-process call that
 *      crosses the binder; executed on [Dispatchers.IO].
 *
 * Undo is a thin pass-through to [IEnvelopeRepository.undo]; the repository
 * enforces the 10 s window and returns `false` once expired.
 */
class CapsuleSealOrchestrator(
    appContext: Context,
    private val repositoryProvider: () -> IEnvelopeRepository?,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD
) : SealOrchestrator {

    private val stateCollector = StateSnapshotCollector.create(appContext)
    private val intentPredictor = IntentPredictor(NanoLlmProvider())

    override suspend fun captureAndSeal(content: CapturedContent): SealOutcome =
        withContext(Dispatchers.IO) {
            val repo = repositoryProvider() ?: return@withContext SealOutcome.Blocked("repo unbound")

            // (1) Scrub before anything else.
            val scrub = SensitivityScrubber.scrub(content.text)
            val cleanText = scrub.scrubbedText
            if (cleanText.isBlank()) {
                return@withContext SealOutcome.Blocked("scrubbed to empty")
            }

            // (2) State snapshot (package resolver, timezone, hour, day).
            val state = runCatching { stateCollector.snapshot() }.getOrElse {
                Log.w(TAG, "StateSnapshotCollector failed; degrading", it)
                return@withContext SealOutcome.Blocked("state snapshot failed")
            }

            // (3) Intent classification — resilient to stub/timeout/error.
            val classification = runCatching {
                intentPredictor.classify(cleanText, state.appCategory)
            }.getOrElse {
                Log.w(TAG, "IntentPredictor threw; falling back to AMBIGUOUS", it)
                null
            }

            // (4) Silent-wrap gate. Mirrors SilentWrapPredicate semantics.
            val intent = classification?.intent ?: Intent.AMBIGUOUS
            val confidence = classification?.confidence ?: 0f
            val silentWrapCandidate = intent != Intent.AMBIGUOUS &&
                confidence >= confidenceThreshold &&
                repo.existsPriorIntentSafe(state.appCategory, intent.name)

            if (silentWrapCandidate) {
                // Seal immediately as the predicted intent.
                val draft = buildDraft(
                    scrubbedText = cleanText,
                    intent = intent,
                    confidence = confidence,
                    source = IntentSource.PREDICTED_SILENT,
                    redactionCounts = scrub.redactionCountByType
                )
                val id = repo.sealSafe(draft, state) ?: return@withContext SealOutcome.Blocked("seal rpc failed")
                Log.d(TAG, "ENVELOPE_SEALED | id=$id | intent=${intent.name} | source=PREDICTED_SILENT")
                return@withContext SealOutcome.Silent(envelopeId = id, intent = intent)
            }

            // Chip row path — build draft with AMBIGUOUS placeholder. The
            // user's chip choice (or 2 s timeout → AMBIGUOUS auto-seal) is
            // resolved by the VM's onChipTapped / onChipRowTimeout callbacks,
            // which call [sealWithChoice] with the same content + a concrete
            // IntentSource. The VM stashes the original content between calls
            // so we don't need to re-run scrub/snapshot on a placeholder.
            SealOutcome.RequiresChipRow(previewText = cleanText.take(PREVIEW_CAP))
        }

    override suspend fun sealWithChoice(
        content: CapturedContent,
        intent: Intent,
        source: IntentSource
    ): SealOutcome = withContext(Dispatchers.IO) {
        val repo = repositoryProvider() ?: return@withContext SealOutcome.Blocked("repo unbound")

        val scrub = SensitivityScrubber.scrub(content.text)
        val cleanText = scrub.scrubbedText
        if (cleanText.isBlank()) {
            return@withContext SealOutcome.Blocked("scrubbed to empty")
        }

        val state = runCatching { stateCollector.snapshot() }.getOrElse {
            Log.w(TAG, "StateSnapshotCollector failed; degrading", it)
            return@withContext SealOutcome.Blocked("state snapshot failed")
        }

        val draft = buildDraft(
            scrubbedText = cleanText,
            intent = intent,
            // User chose this explicitly (or accepted the AMBIGUOUS default);
            // store confidence=1.0 for USER_CHIP and 0.0 for AUTO_AMBIGUOUS so
            // downstream audits reflect the source of truth.
            confidence = if (source == IntentSource.USER_CHIP) 1.0f else 0.0f,
            source = source,
            redactionCounts = scrub.redactionCountByType
        )
        val id = repo.sealSafe(draft, state)
            ?: return@withContext SealOutcome.Blocked("seal rpc failed")
        Log.d(TAG, "ENVELOPE_SEALED | id=$id | intent=${intent.name} | source=${source.name}")
        when (source) {
            IntentSource.USER_CHIP -> SealOutcome.UserChip(envelopeId = id, intent = intent)
            IntentSource.AUTO_AMBIGUOUS -> SealOutcome.AutoAmbiguous(envelopeId = id)
            else -> SealOutcome.UserChip(envelopeId = id, intent = intent)
        }
    }

    override suspend fun undo(envelopeId: String): UndoOutcome = withContext(Dispatchers.IO) {
        val repo = repositoryProvider() ?: return@withContext UndoOutcome.Failed("repo unbound")
        val ok = runCatching { repo.undo(envelopeId) }.getOrElse {
            Log.w(TAG, "undo() rpc failed", it)
            return@withContext UndoOutcome.Failed(it.message ?: "rpc error")
        }
        if (ok) UndoOutcome.Removed else UndoOutcome.AlreadyInDiary
    }

    private fun buildDraft(
        scrubbedText: String,
        intent: Intent,
        confidence: Float,
        source: IntentSource,
        redactionCounts: Map<String, Int>
    ): IntentEnvelopeDraftParcel = IntentEnvelopeDraftParcel(
        contentType = ContentType.TEXT.name,
        textContent = scrubbedText,
        imageUri = null,
        intent = intent.name,
        intentConfidence = confidence,
        intentSource = source.name,
        redactionCountByType = redactionCounts
    )

    private fun IEnvelopeRepository.sealSafe(
        draft: IntentEnvelopeDraftParcel,
        state: com.capsule.app.data.ipc.StateSnapshotParcel
    ): String? = runCatching { seal(draft, state) }
        .getOrElse {
            Log.e(TAG, "seal() rpc failed", it)
            null
        }

    private fun IEnvelopeRepository.existsPriorIntentSafe(
        appCategory: String,
        intent: String
    ): Boolean = runCatching { existsPriorIntent(appCategory, intent) }
        .getOrElse {
            Log.w(TAG, "existsPriorIntent rpc failed; defaulting to false", it)
            false
        }

    companion object {
        private const val TAG = "CapsuleSealOrch"
        private const val PREVIEW_CAP = 80
        const val DEFAULT_CONFIDENCE_THRESHOLD: Float = 0.70f
    }
}
