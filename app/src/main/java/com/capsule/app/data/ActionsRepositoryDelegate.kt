package com.capsule.app.data

import androidx.room.withTransaction
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.entity.ActionExecutionEntity
import com.capsule.app.data.entity.ActionProposalEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.ipc.ActionProposalParcel
import com.capsule.app.data.ipc.AppFunctionSummaryParcel
import com.capsule.app.data.ipc.IActionProposalObserver
import com.capsule.app.data.model.ActionExecutionOutcome
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.EnvelopeKind
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Spec 003 v1.1 — actions-side delegate the [EnvelopeRepositoryImpl] forwards
 * its new AIDL methods to. Lives separately so the 678-line Impl stays
 * focused on envelope CRUD + URL hydration.
 *
 * Same audit-log atomicity contract as `EnvelopeStorageBackend.*Transaction`:
 * every state mutation here writes its audit row inside the same
 * `database.withTransaction { }` block (audit-log-contract.md §6).
 */
class ActionsRepositoryDelegate(
    private val database: OrbitDatabase,
    private val registry: AppFunctionRegistry,
    private val auditWriter: AuditLogWriter,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val proposalDao = database.actionProposalDao()
    private val executionDao = database.actionExecutionDao()
    private val skillDao = database.appFunctionSkillDao()
    private val auditDao = database.auditLogDao()
    private val envelopeDao = database.intentEnvelopeDao()

    private val observerJobs = ConcurrentHashMap<IBinderKey, Job>()

    suspend fun lookupAppFunction(functionId: String): AppFunctionSummaryParcel? =
        skillDao.lookupLatest(functionId)?.toParcel()

    suspend fun listAppFunctions(appPackage: String): List<AppFunctionSummaryParcel> =
        skillDao.listForApp(appPackage).map { it.toParcel() }

    /**
     * Persists a freshly extracted batch of proposals. Caller (the LLM
     * orchestrator) guarantees `confidence ≥ 0.55` and that each
     * `(envelopeId, functionId)` is fresh — DAO insert uses IGNORE so
     * duplicates from a re-run are silent no-ops.
     */
    suspend fun writeProposals(
        envelopeId: String,
        proposals: List<ActionProposalEntity>
    ) {
        if (proposals.isEmpty()) return
        database.withTransaction {
            for (p in proposals) {
                proposalDao.insert(p)
                auditDao.insert(
                    auditWriter.build(
                        action = AuditAction.ACTION_PROPOSED,
                        description = "Proposed ${p.functionId} (${"%.2f".format(p.confidence)})",
                        envelopeId = envelopeId,
                        extraJson = JSONObject().apply {
                            put("proposalId", p.id)
                            put("functionId", p.functionId)
                            put("confidence", p.confidence)
                            put("provenance", p.provenance.name)
                            put("sensitivity", p.sensitivityScope.name)
                        }.toString()
                    )
                )
            }
        }
    }

    /** User confirmed a chip. Returns true when state actually changed. */
    suspend fun markProposalConfirmed(proposalId: String): Boolean {
        val now = clock()
        var changed = false
        database.withTransaction {
            val rows = proposalDao.markConfirmed(proposalId, now)
            if (rows > 0) {
                changed = true
                val proposal = proposalDao.getById(proposalId)
                auditDao.insert(
                    auditWriter.build(
                        action = AuditAction.ACTION_CONFIRMED,
                        description = "Confirmed proposal $proposalId",
                        envelopeId = proposal?.envelopeId,
                        extraJson = """{"proposalId":"$proposalId","functionId":"${proposal?.functionId}"}"""
                    )
                )
            }
        }
        return changed
    }

    /** User dismissed a chip. Returns true when state actually changed. */
    suspend fun markProposalDismissed(proposalId: String): Boolean {
        val now = clock()
        var changed = false
        database.withTransaction {
            val rows = proposalDao.markDismissed(proposalId, now)
            if (rows > 0) {
                changed = true
                val proposal = proposalDao.getById(proposalId)
                auditDao.insert(
                    auditWriter.build(
                        action = AuditAction.ACTION_DISMISSED,
                        description = "Dismissed proposal $proposalId",
                        envelopeId = proposal?.envelopeId,
                        extraJson = """{"proposalId":"$proposalId","functionId":"${proposal?.functionId}"}"""
                    )
                )
            }
        }
        return changed
    }

    /**
     * Records a finished action invocation: insert or update the
     * `action_execution` row, mark the proposal CONFIRMED if not already,
     * insert the `skill_usage` aggregation row, and emit an audit row —
     * all in a single Room transaction.
     */
    suspend fun recordActionInvocation(
        executionId: String,
        proposalId: String,
        functionId: String,
        outcome: ActionExecutionOutcome,
        outcomeReason: String?,
        dispatchedAtMillis: Long,
        completedAtMillis: Long,
        latencyMs: Long,
        episodeId: String?
    ) {
        database.withTransaction {
            val existing = executionDao.getById(executionId)
            if (existing == null) {
                executionDao.insert(
                    ActionExecutionEntity(
                        id = executionId,
                        proposalId = proposalId,
                        functionId = functionId,
                        outcome = outcome,
                        outcomeReason = outcomeReason,
                        dispatchedAt = dispatchedAtMillis,
                        completedAt = completedAtMillis.takeIf { it > 0 },
                        latencyMs = latencyMs.takeIf { it >= 0 },
                        episodeId = episodeId
                    )
                )
            } else {
                executionDao.markOutcome(
                    id = executionId,
                    outcome = outcome,
                    reason = outcomeReason,
                    completedAt = completedAtMillis,
                    latencyMs = latencyMs
                )
            }

            registry.recordInvocation(
                skillId = functionId,
                executionId = executionId,
                proposalId = proposalId,
                episodeId = episodeId,
                outcome = outcome,
                latencyMs = latencyMs.coerceAtLeast(0),
                invokedAt = dispatchedAtMillis
            )

            val proposal = proposalDao.getById(proposalId)
            val envelopeId = proposal?.envelopeId
            // Per action-execution-contract.md §5: USER_CANCELLED maps to
            // ACTION_FAILED with reason=user_cancelled (not ACTION_DISMISSED —
            // that's reserved for proposal-level dismissals before execute).
            val auditAction = when (outcome) {
                ActionExecutionOutcome.PENDING -> AuditAction.ACTION_EXECUTED
                ActionExecutionOutcome.DISPATCHED -> AuditAction.ACTION_EXECUTED
                ActionExecutionOutcome.SUCCESS -> AuditAction.ACTION_EXECUTED
                ActionExecutionOutcome.FAILED -> AuditAction.ACTION_FAILED
                ActionExecutionOutcome.USER_CANCELLED -> AuditAction.ACTION_FAILED
            }
            auditDao.insert(
                auditWriter.build(
                    action = auditAction,
                    description = "Action $functionId outcome=${outcome.name}",
                    envelopeId = envelopeId,
                    extraJson = JSONObject().apply {
                        put("executionId", executionId)
                        put("proposalId", proposalId)
                        put("functionId", functionId)
                        put("outcome", outcome.name)
                        put("latencyMs", latencyMs)
                        if (outcomeReason != null) put("reason", outcomeReason)
                    }.toString()
                )
            )

            // T091 — schema-failure proposals are terminal: flip to
            // INVALIDATED so the UI hides them and a retry-tap won't
            // re-fire the (still-broken) Intent. Mirrors
            // action-execution-contract.md §4 step 1.
            if (outcome == ActionExecutionOutcome.FAILED &&
                (outcomeReason == "schema_invalidated" || outcomeReason == "schema_mismatch")
            ) {
                proposalDao.markInvalidated(proposalId, completedAtMillis.coerceAtLeast(dispatchedAtMillis))
            }
        }
    }

    /**
     * Forward-only stream of non-terminal proposals for an envelope to a UI
     * observer. Mirrors `EnvelopeRepositoryImpl.observeDay` lifecycle —
     * cancel previous job for the same binder; auto-stop on RemoteException.
     */
    fun observeProposalsForEnvelope(envelopeId: String, observer: IActionProposalObserver) {
        val key = IBinderKey(observer.asBinder())
        observerJobs.remove(key)?.cancel()
        val job = scope.launch(Dispatchers.IO) {
            proposalDao.observeProposedForEnvelope(envelopeId).collectLatest { rows ->
                try {
                    observer.onProposals(rows.map { it.toParcel() })
                } catch (_: android.os.RemoteException) {
                    observerJobs.remove(key)?.cancel()
                }
            }
        }
        observerJobs[key] = job
    }

    fun stopObservingProposals(observer: IActionProposalObserver) {
        val key = IBinderKey(observer.asBinder())
        observerJobs.remove(key)?.cancel()
    }

    // ---- T060/T061 — derived to-do envelopes ------------------------

    /**
     * T061 — local-target dispatch path for [TodoActionHandler]. Inserts
     * one new envelope per parsed item in a single Room transaction:
     *   kind=REGULAR, intent=WANT_IT, intentSource=AUTO_AMBIGUOUS,
     *   todoMetaJson populated, derivedFromEnvelopeIdsJson=[parentId].
     * Source envelope is NOT mutated (Principle III).
     *
     * `itemsJson` is a JSON array; each element is either a string
     * (text-only item) or an object `{"text":"…","dueEpochMillis":<long>}`.
     * Malformed items are skipped silently. Empty array returns `[]`.
     *
     * Each insert fires an `ENVELOPE_CREATED` audit row carrying
     * `derived_from_proposal_id` per quickstart §4 step 9.
     *
     * Returns the list of newly created envelope ids in input order.
     */
    suspend fun createDerivedTodoEnvelope(
        parentEnvelopeId: String,
        itemsJson: String,
        proposalId: String
    ): List<String> {
        val parent = envelopeDao.getById(parentEnvelopeId) ?: return emptyList()
        val parsed = parseItemsJson(itemsJson)
        if (parsed.isEmpty()) return emptyList()

        val now = clock()
        val parentIdsJson = JSONArray().apply { put(parentEnvelopeId) }.toString()
        val newIds = mutableListOf<String>()

        database.withTransaction {
            for (item in parsed) {
                val newId = UUID.randomUUID().toString()
                val todoMeta = buildTodoMetaJson(item, proposalId)
                val text = item.text
                val derived = IntentEnvelopeEntity(
                    id = newId,
                    contentType = ContentType.TEXT,
                    textContent = text,
                    imageUri = null,
                    textContentSha256 = null,
                    intent = Intent.WANT_IT,
                    intentConfidence = null,
                    intentSource = IntentSource.AUTO_AMBIGUOUS,
                    intentHistoryJson = JSONArray().put(
                        JSONObject()
                            .put("at", now)
                            .put("intent", Intent.WANT_IT.name)
                            .put("source", IntentSource.AUTO_AMBIGUOUS.name)
                    ).toString(),
                    state = parent.state.copy(),
                    createdAt = now,
                    dayLocal = computeDayLocal(now, parent.state.tzId),
                    kind = EnvelopeKind.REGULAR,
                    derivedFromEnvelopeIdsJson = parentIdsJson,
                    todoMetaJson = todoMeta
                )
                envelopeDao.insert(derived)
                auditDao.insert(
                    auditWriter.build(
                        action = AuditAction.ENVELOPE_CREATED,
                        description = "Derived to-do from proposal $proposalId",
                        envelopeId = newId,
                        extraJson = JSONObject().apply {
                            put("derived_from_proposal_id", proposalId)
                            put("parent_envelope_id", parentEnvelopeId)
                            put("source", "todo_add")
                        }.toString()
                    )
                )
                newIds.add(newId)
            }
        }
        return newIds
    }

    /**
     * T064 — toggle one item's `done` flag. Re-serialises the envelope's
     * `todoMetaJson`. No-op (returns silently) when the envelope has no
     * `todoMetaJson`, the index is out of range, or the JSON is
     * malformed. Intentionally does NOT write an audit row — checkbox
     * toggles are high-frequency UI noise per audit-log-contract §2.
     */
    suspend fun setTodoItemDone(envelopeId: String, itemIndex: Int, done: Boolean) {
        val envelope = envelopeDao.getById(envelopeId) ?: return
        val current = envelope.todoMetaJson ?: return
        val updated = runCatching {
            val obj = JSONObject(current)
            val items = obj.optJSONArray("items") ?: return@runCatching null
            if (itemIndex !in 0 until items.length()) return@runCatching null
            val item = items.optJSONObject(itemIndex) ?: return@runCatching null
            item.put("done", done)
            obj.toString()
        }.getOrNull() ?: return
        envelopeDao.updateTodoMetaJson(envelopeId, updated)
    }

    // ---- helpers ----

    private data class ParsedItem(val text: String, val dueEpochMillis: Long?)

    private fun parseItemsJson(raw: String): List<ParsedItem> {
        val arr = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        val out = mutableListOf<ParsedItem>()
        for (i in 0 until arr.length()) {
            when (val element = arr.opt(i)) {
                is String -> {
                    val t = element.trim()
                    if (t.isNotEmpty()) out.add(ParsedItem(t, null))
                }
                is JSONObject -> {
                    val t = element.optString("text").trim()
                    if (t.isNotEmpty()) {
                        val due = if (element.has("dueEpochMillis"))
                            element.optLong("dueEpochMillis", -1L).takeIf { it >= 0 }
                        else null
                        out.add(ParsedItem(t, due))
                    }
                }
            }
        }
        return out
    }

    private fun buildTodoMetaJson(item: ParsedItem, proposalId: String): String {
        val itemObj = JSONObject().apply {
            put("text", item.text)
            put("done", false)
            if (item.dueEpochMillis != null) put("dueEpochMillis", item.dueEpochMillis)
            else put("dueEpochMillis", JSONObject.NULL)
        }
        return JSONObject().apply {
            put("items", JSONArray().put(itemObj))
            put("derivedFromProposalId", proposalId)
        }.toString()
    }

    private fun computeDayLocal(nowMillis: Long, tzId: String): String =
        Instant.ofEpochMilli(nowMillis).atZone(ZoneId.of(tzId)).toLocalDate().toString()
}

private fun ActionProposalEntity.toParcel() = ActionProposalParcel(
    id = id,
    envelopeId = envelopeId,
    functionId = functionId,
    schemaVersion = schemaVersion,
    argsJson = argsJson,
    previewTitle = previewTitle,
    previewSubtitle = previewSubtitle,
    confidence = confidence,
    provenance = provenance.name,
    state = state.name,
    sensitivityScope = sensitivityScope.name,
    createdAtMillis = createdAt,
    stateChangedAtMillis = stateChangedAt
)

private fun com.capsule.app.data.entity.AppFunctionSkillEntity.toParcel() = AppFunctionSummaryParcel(
    functionId = functionId,
    appPackage = appPackage,
    displayName = displayName,
    description = description,
    schemaVersion = schemaVersion,
    argsSchemaJson = argsSchemaJson,
    sideEffects = sideEffects.name,
    reversibility = reversibility.name,
    sensitivityScope = sensitivityScope.name,
    registeredAtMillis = registeredAt,
    updatedAtMillis = updatedAt
)
