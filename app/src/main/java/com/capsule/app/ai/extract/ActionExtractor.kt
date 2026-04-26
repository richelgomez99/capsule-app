package com.capsule.app.ai.extract

import androidx.room.withTransaction
import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.model.ActionCandidate
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.AppFunctionRegistry
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.dao.ActionProposalDao
import com.capsule.app.data.dao.AuditLogDao
import com.capsule.app.data.dao.IntentEnvelopeDao
import com.capsule.app.data.entity.ActionProposalEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.model.ActionProposalState
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.SensitivityScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import org.json.JSONException
import org.json.JSONObject
import java.util.UUID

/**
 * T043 — Orchestrator for the action-extraction pipeline.
 *
 * Owned by `:ml`. Lives off the seal-path; called only from
 * [ActionExtractionWorker] under a charger+wifi continuation.
 *
 * Pipeline (specs/003-orbit-actions/contracts/action-extraction-contract.md §4):
 *  1. Load envelope. Missing → [ExtractOutcome.NoCandidates].
 *  2. Re-check sensitivity gates (kind=REGULAR, no credentials/medical).
 *     Newly sensitive → [ExtractOutcome.Skipped].
 *  3. Resolve registered functions filtered by appPackage="com.capsule.app".
 *  4. Call `llmProvider.extractActions(...)` with 8s timeout. Exception →
 *     audit + [ExtractOutcome.Failed].
 *  5. For each candidate ≥ [confidenceFloor]:
 *      - re-validate args JSON shape against function's argsSchemaJson
 *        (defense in depth; details deferred to T021/T091)
 *      - sensitivity-scope mismatch → drop + audit ACTION_DISMISSED
 *      - else insert ActionProposalEntity (PROPOSED).
 *  6. Within the same Room transaction, write one ACTION_PROPOSED audit
 *     row per inserted proposal (Principle IX).
 *
 * Determinism for tests: [confidenceFloor], [llmTimeoutMillis], and the
 * id/clock generators are injectable.
 */
class ActionExtractor(
    private val database: OrbitDatabase,
    private val envelopeDao: IntentEnvelopeDao,
    private val proposalDao: ActionProposalDao,
    private val auditLogDao: AuditLogDao,
    private val registry: AppFunctionRegistry,
    private val llmProvider: LlmProvider,
    private val auditWriter: AuditLogWriter,
    private val confidenceFloor: Float = 0.55f,
    private val llmTimeoutMillis: Long = 8_000L,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val idGen: () -> String = { UUID.randomUUID().toString() }
) {

    suspend fun extract(envelopeId: String): ExtractOutcome {
        val envelope = envelopeDao.getById(envelopeId) ?: return ExtractOutcome.NoCandidates

        // Step 2 — sensitivity / kind gates.
        if (envelope.kind.name != "REGULAR") {
            return ExtractOutcome.Skipped("non_regular_kind")
        }
        if (containsForbiddenSensitivityMarkers(envelope.textContent)) {
            return ExtractOutcome.Skipped("sensitivity_changed")
        }
        if (envelope.textContent.isNullOrBlank()) {
            return ExtractOutcome.NoCandidates
        }

        // Step 3 — registered functions.
        val skills = registry.listForApp("com.capsule.app")
        if (skills.isEmpty()) return ExtractOutcome.NoCandidates
        val summaries: List<AppFunctionSummary> = skills.map { s ->
            AppFunctionSummary(
                functionId = s.functionId,
                schemaVersion = s.schemaVersion,
                displayName = s.displayName,
                description = s.description,
                argsSchemaJson = s.argsSchemaJson,
                sensitivityScope = s.sensitivityScope
            )
        }

        // Step 4 — Nano call with bounded timeout.
        val result = try {
            withTimeout(llmTimeoutMillis) {
                llmProvider.extractActions(
                    text = envelope.textContent!!,
                    contentType = envelope.contentType.name,
                    state = envelope.state,
                    registeredFunctions = summaries,
                    maxCandidates = 3
                )
            }
        } catch (t: TimeoutCancellationException) {
            recordExtractionFailure(envelopeId, "nano_timeout")
            return ExtractOutcome.Failed("nano_timeout")
        } catch (t: Throwable) {
            recordExtractionFailure(envelopeId, "nano_${t.javaClass.simpleName}")
            return ExtractOutcome.Failed("nano_${t.javaClass.simpleName}")
        }

        if (result.candidates.isEmpty()) return ExtractOutcome.NoCandidates

        // Step 5–6 — validate, filter, insert + audit in one txn.
        val provenance = result.provenance
        val accepted = mutableListOf<ActionProposalEntity>()
        val droppedSensitivityIds = mutableListOf<String>() // function ids dropped for scope mismatch
        val schemaByFunctionId = summaries.associateBy { it.functionId }

        for (cand in result.candidates) {
            if (cand.confidence < confidenceFloor) continue
            val skill = schemaByFunctionId[cand.functionId] ?: continue
            if (!isValidJsonObjectShape(cand.argsJson, skill.argsSchemaJson)) continue
            if (!sensitivityScopeMatches(envelope, skill.sensitivityScope)) {
                droppedSensitivityIds += cand.functionId
                continue
            }
            accepted += ActionProposalEntity(
                id = idGen(),
                envelopeId = envelopeId,
                functionId = cand.functionId,
                schemaVersion = cand.schemaVersion,
                argsJson = cand.argsJson,
                previewTitle = cand.previewTitle.take(120),
                previewSubtitle = cand.previewSubtitle?.take(160),
                confidence = cand.confidence,
                provenance = provenance,
                state = ActionProposalState.PROPOSED,
                sensitivityScope = cand.sensitivityScope,
                createdAt = now(),
                stateChangedAt = now()
            )
        }

        if (accepted.isEmpty() && droppedSensitivityIds.isEmpty()) {
            return ExtractOutcome.NoCandidates
        }

        database.withTransaction {
            var skippedDup = 0
            for (p in accepted) {
                // Unique-index (envelopeId, functionId) means a re-run after a
                // partial earlier success would conflict. Skip in that case.
                val existing = proposalDao.findByEnvelopeAndFunction(envelopeId, p.functionId)
                if (existing != null) {
                    skippedDup += 1
                    continue
                }
                proposalDao.insert(p)
                auditLogDao.insert(
                    auditWriter.build(
                        action = AuditAction.ACTION_PROPOSED,
                        description = "Proposed ${p.functionId} (conf=${"%.2f".format(p.confidence)})",
                        envelopeId = envelopeId,
                        extraJson = """{"proposalId":"${p.id}","functionId":"${p.functionId}","schemaVersion":${p.schemaVersion}}"""
                    )
                )
            }
            for (fid in droppedSensitivityIds) {
                auditLogDao.insert(
                    auditWriter.build(
                        action = AuditAction.ACTION_DISMISSED,
                        description = "Dropped $fid (sensitivity_scope_mismatch)",
                        envelopeId = envelopeId,
                        extraJson = """{"functionId":"$fid","reason":"sensitivity_scope_mismatch"}"""
                    )
                )
            }
            // T094 — re-extraction idempotency. If every accepted candidate
            // hit the unique constraint (already proposed in a prior run),
            // emit a CONTINUATION_COMPLETED outcome=noop audit row so the
            // continuation engine + audit aggregator can distinguish a
            // genuine no-candidates run from an idempotent re-run.
            if (accepted.isNotEmpty() && skippedDup == accepted.size && droppedSensitivityIds.isEmpty()) {
                auditLogDao.insert(
                    auditWriter.build(
                        action = AuditAction.CONTINUATION_COMPLETED,
                        description = "ACTION_EXTRACT noop (duplicate)",
                        envelopeId = envelopeId,
                        extraJson = """{"phase":"extract","outcome":"noop","reason":"duplicate","skipped":$skippedDup}"""
                    )
                )
            }
        }

        return if (accepted.isEmpty()) ExtractOutcome.NoCandidates
               else ExtractOutcome.Proposed(accepted.map { it.id })
    }

    /**
     * The capture-time SensitivityScrubber redacts known credential / API-
     * key shapes inline as `[REDACTED_<TYPE>]`. If that marker is present
     * AND the type is `credentials`/`medical`, we treat the envelope as
     * out-of-scope for actions per research §8.
     *
     * Conservative: any presence of these specific REDACTED tokens skips
     * extraction outright.
     */
    private fun containsForbiddenSensitivityMarkers(text: String?): Boolean {
        if (text.isNullOrEmpty()) return false
        val t = text
        return FORBIDDEN_REDACTION_MARKERS.any { t.contains(it) }
    }

    /**
     * Scope-narrowed JSON validation: the candidate's argsJson must parse
     * as a JSONObject (the schema's top-level type is always "object" for
     * the v1.1 built-ins). Full Draft 2020-12 keyword validation is
     * deferred to T021/T091 once a JSON-schema validator dependency lands
     * — until then, the LlmProvider implementations themselves are
     * responsible for shape-conformant args.
     */
    private fun isValidJsonObjectShape(argsJson: String, @Suppress("UNUSED_PARAMETER") schemaJson: String): Boolean {
        if (argsJson.isBlank() || argsJson.length > 4096) return false
        return try {
            JSONObject(argsJson)
            true
        } catch (_: JSONException) {
            false
        }
    }

    /**
     * Cross-check the registered AppFunction's [SensitivityScope] against
     * the envelope's textual signals. v1.1 rule:
     *  - PUBLIC: requires the envelope to look non-personal (no REDACTED markers).
     *  - PERSONAL: always fine.
     *  - SHARE_DELEGATED: always fine (user-initiated by definition at execute time).
     */
    private fun sensitivityScopeMatches(
        envelope: IntentEnvelopeEntity,
        functionScope: SensitivityScope
    ): Boolean = when (functionScope) {
        SensitivityScope.PUBLIC -> envelope.textContent?.contains("[REDACTED_") != true
        SensitivityScope.PERSONAL -> true
        SensitivityScope.SHARE_DELEGATED -> true
    }

    private suspend fun recordExtractionFailure(envelopeId: String, reason: String) {
        runCatching {
            auditLogDao.insert(
                auditWriter.build(
                    action = AuditAction.ACTION_FAILED,
                    description = "Extraction failed: $reason",
                    envelopeId = envelopeId,
                    extraJson = """{"phase":"extract","reason":"$reason"}"""
                )
            )
        }
    }

    companion object {
        // Markers written by [SensitivityScrubber] for categories the action
        // pipeline refuses to extract from. See research.md §8.
        private val FORBIDDEN_REDACTION_MARKERS = listOf(
            "[REDACTED_AWS_ACCESS_KEY]",
            "[REDACTED_AWS_SECRET_KEY]",
            "[REDACTED_GITHUB_TOKEN]",
            "[REDACTED_OPENAI_KEY]",
            "[REDACTED_ANTHROPIC_KEY]",
            "[REDACTED_JWT]",
            "[REDACTED_CREDIT_CARD]",
            "[REDACTED_SSN]",
            "[REDACTED_MEDICAL]"
        )
    }
}

/** Sealed result of one [ActionExtractor.extract] call. */
sealed interface ExtractOutcome {
    object NoCandidates : ExtractOutcome
    data class Proposed(val proposalIds: List<String>) : ExtractOutcome
    data class Skipped(val reason: String) : ExtractOutcome
    data class Failed(val reason: String) : ExtractOutcome
}
