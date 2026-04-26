package com.capsule.app.data

import com.capsule.app.ai.extract.ActionExtractionPrefilter
import com.capsule.app.ai.extract.ActionExtractor
import com.capsule.app.ai.extract.ExtractOutcome
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.continuation.ContinuationEngine
import com.capsule.app.data.entity.ContinuationEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.ipc.EnvelopeViewParcel
import com.capsule.app.data.ipc.IEnvelopeObserver
import com.capsule.app.data.ipc.IEnvelopeRepository
import com.capsule.app.data.ipc.IntentEnvelopeDraftParcel
import com.capsule.app.data.ipc.StateSnapshotParcel
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.ContinuationStatus
import com.capsule.app.data.model.ContinuationType
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import com.capsule.app.net.CanonicalUrlHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * v1 implementation of [IEnvelopeRepository].
 *
 * Principle VI (Privilege Separation) — this class lives in the `:ml`
 * process; callers reach it only through the AIDL binder.
 * Principle X (Storage Sovereignty) — every storage op goes through
 * [EnvelopeStorageBackend]; never through a DAO directly.
 *
 * Audit atomicity (audit-log-contract.md §6) is enforced by routing every
 * mutation through a `*Transaction` call on the backend, which wraps the
 * mutation + audit insert in a single Room transaction.
 */
class EnvelopeRepositoryImpl(
    private val backend: EnvelopeStorageBackend,
    private val auditWriter: AuditLogWriter,
    private val scope: CoroutineScope,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val undoWindowMillis: Long = UNDO_WINDOW_MS,
    /**
     * T068 merge zone — optional engine used to enqueue `URL_HYDRATE` work
     * **after** `sealTransaction` commits. Nullable so existing contract
     * tests (T029–T033c) keep working; production constructs it in
     * `EnvelopeRepositoryService`.
     */
    private val continuationEngine: ContinuationEngine? = null,
    /**
     * Spec 003 v1.1 — actions delegate. Nullable so 002 contract tests stay
     * green. Production wiring constructs it in `EnvelopeRepositoryService`.
     * AIDL methods that need it short-circuit with safe defaults when null.
     */
    private val actionsDelegate: ActionsRepositoryDelegate? = null,
    /**
     * T043/T044 — extractor invoked from the ACTION_EXTRACT worker via the
     * binder method [extractActionsForEnvelope]. Nullable for the same
     * reason as [actionsDelegate]; production binds it in
     * `EnvelopeRepositoryService`.
     */
    private val actionExtractor: ActionExtractor? = null,
    /**
     * T072/T074 — weekly digest delegate invoked from
     * `WeeklyDigestWorker` via [runWeeklyDigest]. Nullable for the same
     * reason as [actionsDelegate]: 002 contract tests construct the
     * Impl with the smaller surface and never call this method.
     */
    private val weeklyDigestDelegate: WeeklyDigestDelegate? = null
) : IEnvelopeRepository.Stub() {

    /** envelopeId → millis-deadline after which `undo()` returns false. */
    private val undoWindow = ConcurrentHashMap<String, Long>()

    /** Observer binder → background collection job. */
    private val observerJobs = ConcurrentHashMap<IBinderKey, Job>()

    // ---- Seal path ----

    override fun seal(draft: IntentEnvelopeDraftParcel, state: StateSnapshotParcel): String {
        val now = clock()
        val id = UUID.randomUUID().toString()
        val dayLocal = computeDayLocal(now, state.tzId)

        // T066a + T068 merge zone — extract URLs, dedupe, and build
        // PENDING ContinuationEntity rows for the ones that need hydration.
        val contentType = ContentType.valueOf(draft.contentType)
        val urls: List<String> =
            if (contentType == ContentType.TEXT)
                ContinuationEngine.extractUrls(draft.textContent.orEmpty())
            else emptyList()

        val dedupeHits = mutableMapOf<String, String>() // url → existing result id
        val urlsToHydrate = mutableListOf<String>()
        var sharedResultId: String? = null
        for (url in urls) {
            val hash = CanonicalUrlHasher.hash(url)
            val hit = runBlocking { backend.findContinuationResultByCanonicalUrlHash(hash) }
            if (hit != null) {
                dedupeHits[url] = hit.id
                if (sharedResultId == null) sharedResultId = hit.id
            } else {
                urlsToHydrate.add(url)
            }
        }

        val envelope = IntentEnvelopeEntity(
            id = id,
            contentType = contentType,
            textContent = draft.textContent,
            imageUri = draft.imageUri,
            textContentSha256 = null,
            intent = Intent.valueOf(draft.intent),
            intentConfidence = draft.intentConfidence,
            intentSource = IntentSource.valueOf(draft.intentSource),
            intentHistoryJson = initialHistoryJson(
                at = now,
                intent = draft.intent,
                source = draft.intentSource
            ),
            state = StateSnapshot(
                appCategory = AppCategory.valueOf(state.appCategory),
                activityState = ActivityState.valueOf(state.activityState),
                tzId = state.tzId,
                hourLocal = state.hourLocal,
                dayOfWeekLocal = state.dayOfWeekLocal
            ),
            createdAt = now,
            dayLocal = dayLocal,
            sharedContinuationResultId = sharedResultId
        )

        val audit = auditWriter.build(
            action = AuditAction.ENVELOPE_CREATED,
            description = "Sealed envelope ($${draft.contentType}) as ${draft.intent} via ${draft.intentSource}",
            envelopeId = id
        )

        // T037c — if SensitivityScrubber redacted anything upstream, emit a
        // CAPTURE_SCRUBBED audit row carrying only the per-type counts.
        val auditEntries = buildList {
            add(audit)
            if (draft.redactionCountByType.isNotEmpty()) {
                add(
                    auditWriter.build(
                        action = AuditAction.CAPTURE_SCRUBBED,
                        description = "Redacted ${draft.redactionCountByType.values.sum()} sensitive token(s) before seal",
                        envelopeId = id,
                        extraJson = redactionCountsJson(draft.redactionCountByType)
                    )
                )
            }
            // T066a — one URL_DEDUPE_HIT row per URL that matched the
            // canonical-hash cache. Keeps the audit trail complete per
            // audit-log-contract.md §3.
            for ((url, existingResultId) in dedupeHits) {
                add(
                    auditWriter.build(
                        action = AuditAction.URL_DEDUPE_HIT,
                        description = "Reused existing continuation result $existingResultId for $url",
                        envelopeId = id,
                        extraJson = """{"url":${JSONObject.quote(url)},"resultId":${JSONObject.quote(existingResultId)}}"""
                    )
                )
            }
        }

        // T068 — build PENDING ContinuationEntity rows for the non-deduped
        // URLs. We assign stable continuation ids up-front so the post-txn
        // engine enqueue can reference them (see loop below).
        val continuationRows: List<ContinuationEntity> = urlsToHydrate.map { url ->
            ContinuationEntity(
                id = UUID.randomUUID().toString(),
                envelopeId = id,
                type = ContinuationType.URL_HYDRATE,
                status = ContinuationStatus.PENDING,
                inputUrl = url,
                scheduledAt = now,
                startedAt = null,
                completedAt = null,
                attemptCount = 0,
                failureReason = null
            )
        }

        runBlocking {
            backend.sealTransaction(
                envelope = envelope,
                continuations = continuationRows,
                auditEntries = auditEntries
            )
        }

        // T068 — AFTER the Room txn commits, hand each PENDING row to the
        // engine so WorkManager picks it up. Done outside the txn because
        // WorkManager owns its own durability; enqueueing inside a Room
        // transaction would deadlock on its internal DB init.
        continuationEngine?.let { engine ->
            android.util.Log.i(
                "EnvelopeRepo",
                "seal($id) enqueueing ${continuationRows.size} continuation(s)"
            )
            for (row in continuationRows) {
                engine.enqueueSingle(
                    envelopeId = id,
                    continuationId = row.id,
                    url = row.inputUrl.orEmpty()
                )
            }
            // T077 (Phase 6 US4) — fan IMAGE envelopes into
            // ScreenshotUrlExtractWorker. The worker runs on-device OCR
            // and then seeds follow-up URL_HYDRATE rows via
            // `seedScreenshotHydrations`.
            if (contentType == ContentType.IMAGE && !draft.imageUri.isNullOrBlank()) {
                engine.enqueueScreenshotOcr(envelopeId = id, imageUri = draft.imageUri!!)
            }
            // T046 — post-seal ACTION_EXTRACT enqueue. Mirrors the URL_HYDRATE
            // pattern: pre-filter synchronously (cheap regex pass), then
            // hand to ContinuationEngine which gates on charger+wifi. The
            // ActionExtractor proper runs inside the worker via the binder.
            //
            // Skip if: kind != REGULAR (DIGEST/DERIVED never propose), text
            // is empty, or prefilter sees no actionable signal. We do NOT
            // re-check sensitivity here — the extractor itself does that
            // again as defense in depth (contract §4 step 2).
            if (contentType == ContentType.TEXT &&
                !draft.textContent.isNullOrBlank() &&
                ActionExtractionPrefilter.shouldExtract(draft.textContent!!)
            ) {
                engine.enqueueActionExtract(envelopeId = id)
            }
        }

        undoWindow[id] = now + undoWindowMillis
        return id
    }

    // ---- Read path ----

    override fun getEnvelope(envelopeId: String): EnvelopeViewParcel = runBlocking {
        val entity = backend.getEnvelope(envelopeId)
            ?: throw IllegalArgumentException("Envelope $envelopeId not found")
        // Detail screen needs hydrated title/summary too — prefer the
        // dedupe-shared result if set, otherwise the envelope's own latest.
        val latest = backend.getLatestResultForEnvelope(envelopeId, entity.sharedContinuationResultId)
        entity.toViewParcel(latest)
    }

    override fun observeDay(isoDate: String, observer: IEnvelopeObserver) {
        val key = IBinderKey(observer.asBinder())
        observerJobs.remove(key)?.cancel()
        val job = scope.launch(Dispatchers.IO) {
            backend.observeDayWithResults(isoDate).collectLatest { rows ->
                try {
                    // Diagnostic (temporary): log per-envelope hydration state on every
                    // emission so we can tell, from Logcat alone, whether a Diary card
                    // that shows "Link not enriched yet" is because hydration truly
                    // failed (no continuation_result row) or because the read-side
                    // join wasn't wiring through (the @Relation fix). Keyed `DiaryObs`.
                    rows.forEach { row ->
                        val result = row.latestResult
                        android.util.Log.i(
                            "DiaryObs",
                            "env=${row.envelope.id} " +
                                "hasResult=${result != null} " +
                                "title=${result?.title?.take(40)} " +
                                "domain=${result?.domain} " +
                                "text=${row.envelope.textContent?.take(60)?.replace('\n', ' ')}"
                        )
                    }
                    val parcel = com.capsule.app.data.ipc.DayPageParcel(
                        isoDate = isoDate,
                        envelopes = rows.map { row ->
                            row.envelope.toViewParcel(row.latestResult)
                        }
                    )
                    observer.onDayLoaded(parcel)
                } catch (_: android.os.RemoteException) {
                    // Observer died — stop the collection.
                    observerJobs.remove(key)?.cancel()
                }
            }
        }
        observerJobs[key] = job
    }

    override fun stopObserving(observer: IEnvelopeObserver) {
        val key = IBinderKey(observer.asBinder())
        observerJobs.remove(key)?.cancel()
    }

    // ---- Mutate path ----

    override fun reassignIntent(envelopeId: String, newIntentName: String, reasonOpt: String?) {
        runBlocking {
            val existing = backend.getEnvelope(envelopeId) ?: return@runBlocking
            val newIntent = Intent.valueOf(newIntentName)
            val newSource = IntentSource.DIARY_REASSIGN
            val history = appendHistory(
                existing.intentHistoryJson,
                at = clock(),
                intent = newIntentName,
                source = newSource.name,
                reason = reasonOpt
            )
            val audit = auditWriter.build(
                action = AuditAction.INTENT_SUPERSEDED,
                description = "Intent reassigned ${existing.intent.name} → $newIntentName",
                envelopeId = envelopeId,
                extraJson = reasonOpt?.let { """{"reason":${JSONObject.quote(it)}}""" }
            )
            backend.reassignIntentTransaction(
                id = envelopeId,
                newIntent = newIntent.name,
                intentSource = newSource.name,
                confidence = existing.intentConfidence,
                historyJson = history,
                auditEntry = audit
            )
        }
    }

    override fun archive(envelopeId: String) {
        runBlocking {
            val audit = auditWriter.build(
                action = AuditAction.ENVELOPE_ARCHIVED,
                description = "Envelope archived",
                envelopeId = envelopeId
            )
            backend.archiveTransaction(envelopeId, audit)
        }
    }

    override fun delete(envelopeId: String) {
        // T033b — soft-delete semantics. Row persists with deletedAt set;
        // hard purge is handled by SoftDeleteRetentionWorker (T089a) after 30d.
        runBlocking {
            val now = clock()
            val audit = auditWriter.build(
                action = AuditAction.ENVELOPE_SOFT_DELETED,
                description = "Envelope moved to trash",
                envelopeId = envelopeId
            )
            backend.softDeleteTransaction(envelopeId, now, audit)
            // T075 — DIGEST provenance cascade. Procedural because the
            // FK is a JSON array, not a SQL relation. Runs in its own
            // transaction (after the soft-delete commits) so a cascade
            // failure can never roll back the user's delete.
            runCatching {
                backend.cascadeDigestInvalidation(envelopeId, now) { digestId ->
                    auditWriter.build(
                        action = AuditAction.ENVELOPE_INVALIDATED,
                        description = "Digest invalidated (lost_provenance)",
                        envelopeId = digestId,
                        extraJson = """{"reason":"lost_provenance","triggeredBy":"$envelopeId"}"""
                    )
                }
            }.onFailure { t ->
                android.util.Log.w(
                    "EnvelopeRepository",
                    "Digest cascade failed for $envelopeId: ${t.message}"
                )
            }
        }
    }

    override fun undo(envelopeId: String): Boolean {
        val deadline = undoWindow[envelopeId] ?: return false
        if (clock() > deadline) {
            undoWindow.remove(envelopeId)
            return false
        }
        runBlocking { backend.undoSealTransaction(envelopeId) }
        undoWindow.remove(envelopeId)
        return true
    }

    override fun restoreFromTrash(envelopeId: String) {
        runBlocking {
            val audit = auditWriter.build(
                action = AuditAction.ENVELOPE_RESTORED,
                description = "Envelope restored from trash",
                envelopeId = envelopeId
            )
            backend.restoreFromTrashTransaction(envelopeId, audit)
        }
    }

    override fun listSoftDeletedWithinDays(days: Int): List<EnvelopeViewParcel> = runBlocking {
        backend.listSoftDeletedWithinDays(days, clock()).map { it.toViewParcel() }
    }

    override fun countSoftDeletedWithinDays(days: Int): Int = runBlocking {
        backend.countSoftDeletedWithinDays(days, clock())
    }

    override fun hardDelete(envelopeId: String) {
        runBlocking {
            val audit = auditWriter.build(
                action = AuditAction.ENVELOPE_HARD_PURGED,
                description = "Envelope hard-purged from trash",
                envelopeId = envelopeId,
                extraJson = """{"reason":"user_purge"}"""
            )
            backend.hardDeleteTransaction(envelopeId, audit)
        }
    }

    // ---- Diagnostics ----

    override fun countAll(): Int = runBlocking { backend.countAll() }
    override fun countArchived(): Int = runBlocking { backend.countArchived() }
    override fun countDeleted(): Int = runBlocking { backend.countDeleted() }

    // ---- Multi-day pager (T056) ----
    override fun distinctDayLocalsWithContent(limit: Int, offset: Int): List<String> =
        runBlocking { backend.distinctDayLocalsWithContent(limit, offset) }

    override fun existsPriorIntent(appCategory: String, intent: String): Boolean = runBlocking {
        backend.existsNonArchivedNonDeletedInLast30Days(
            appCategory = appCategory,
            intent = intent,
            nowMillis = clock()
        )
    }

    // ---- URL hydration write-back (T066 completion) ----

    override fun completeUrlHydration(
        continuationId: String,
        envelopeId: String,
        canonicalUrl: String?,
        canonicalUrlHash: String?,
        ok: Boolean,
        title: String?,
        domain: String?,
        summary: String?,
        summaryModel: String?,
        failureReason: String?
    ) {
        runBlocking {
            val now = clock()

            // Late dedupe: if another envelope already hydrated the same
            // canonical URL (concurrent seal of the same URL before the
            // first worker finished), point this envelope at the winning
            // result instead of trying to insert a duplicate row that
            // would violate the unique canonicalUrlHash index (and
            // rollback the whole transaction, leaving the continuation
            // PENDING forever — the stuck-retry bug).
            val dedupeExisting: com.capsule.app.data.entity.ContinuationResultEntity? =
                if (ok && !canonicalUrlHash.isNullOrEmpty())
                    backend.findContinuationResultByCanonicalUrlHash(canonicalUrlHash)
                else null

            val result: com.capsule.app.data.entity.ContinuationResultEntity? = if (ok && dedupeExisting == null) {
                com.capsule.app.data.entity.ContinuationResultEntity(
                    id = UUID.randomUUID().toString(),
                    continuationId = continuationId,
                    envelopeId = envelopeId,
                    producedAt = now,
                    title = title,
                    domain = domain,
                    canonicalUrl = canonicalUrl,
                    canonicalUrlHash = canonicalUrlHash,
                    excerpt = null,
                    summary = summary,
                    summaryModel = summaryModel
                )
            } else null

            val newStatus = if (ok) ContinuationStatus.SUCCEEDED
                            else ContinuationStatus.FAILED_PERMANENT

            // AuditAction is a closed v1 enum — CONTINUATION_COMPLETED
            // carries ok=false + failureReason in extraJson until a
            // Room migration introduces CONTINUATION_FAILED (deferred).
            val extra = JSONObject().apply {
                put("ok", ok)
                put("continuationId", continuationId)
                if (failureReason != null) put("failureReason", failureReason)
                if (ok && summaryModel != null) put("summaryModel", summaryModel)
                if (dedupeExisting != null) {
                    put("dedupeHit", true)
                    put("dedupeResultId", dedupeExisting.id)
                }
            }.toString()
            val audit = auditWriter.build(
                action = AuditAction.CONTINUATION_COMPLETED,
                description = when {
                    !ok -> "URL hydration failed: ${failureReason ?: "unknown"}"
                    dedupeExisting != null -> "URL hydration deduped to ${dedupeExisting.id}"
                    else -> "URL hydration succeeded"
                },
                envelopeId = envelopeId,
                extraJson = extra
            )

            backend.completeUrlHydrationTransaction(
                continuationId = continuationId,
                result = result,
                newStatus = newStatus,
                completedAt = now,
                failureReason = failureReason,
                auditEntry = audit,
                envelopeIdForDedupe = if (dedupeExisting != null) envelopeId else null,
                dedupeExistingResultId = dedupeExisting?.id
            )
        }
    }

    override fun retryHydration(envelopeId: String) {
        val engine = continuationEngine
        if (engine == null) {
            android.util.Log.w("EnvelopeRepo", "retryHydration($envelopeId) ignored — engine null")
            return
        }
        runBlocking {
            val rows = backend.listRetryableContinuations(envelopeId)
            android.util.Log.i(
                "EnvelopeRepo",
                "retryHydration($envelopeId) → ${rows.size} retryable row(s)"
            )
            if (rows.isEmpty()) {
                // No ContinuationEntity rows exist (likely sealed before
                // the hydration pipeline landed, or URL extraction missed
                // a URL). Fall back to a best-effort fresh enqueue so the
                // user's tap isn't a no-op.
                val env = backend.getEnvelope(envelopeId)
                val text = env?.textContent.orEmpty()
                android.util.Log.i(
                    "EnvelopeRepo",
                    "retryHydration fallback — env found=${env != null}, text.len=${text.length}"
                )
                if (text.isNotBlank()) {
                    engine.enqueueForNewEnvelope(
                        envelopeId = envelopeId,
                        contentType = ContinuationEngine.ContentType.TEXT,
                        textContent = text,
                        imageUri = null
                    )
                }
                return@runBlocking
            }
            for (row in rows) {
                val url = row.inputUrl ?: continue
                android.util.Log.i(
                    "EnvelopeRepo",
                    "retryHydration enqueue row=${row.id} status=${row.status} url=$url"
                )
                engine.retry(row.id)
                engine.enqueueSingle(envelopeId = envelopeId, continuationId = row.id, url = url)
            }
        }
    }

    // ---- Screenshot OCR hydration (T076 / Phase 6 US4) ----

    override fun seedScreenshotHydrations(
        envelopeId: String,
        ocrText: String?,
        urls: Array<String>?
    ) {
        val now = clock()
        val rawUrls = urls?.toList().orEmpty()
        val uniqueUrls = LinkedHashSet(rawUrls).toList()

        val dedupeHits = mutableMapOf<String, String>() // url → existing result id
        val urlsToHydrate = mutableListOf<String>()
        runBlocking {
            for (url in uniqueUrls) {
                val hash = CanonicalUrlHasher.hash(url)
                val hit = backend.findContinuationResultByCanonicalUrlHash(hash)
                if (hit != null) dedupeHits[url] = hit.id else urlsToHydrate.add(url)
            }
        }

        val continuationRows: List<ContinuationEntity> = urlsToHydrate.map { url ->
            ContinuationEntity(
                id = UUID.randomUUID().toString(),
                envelopeId = envelopeId,
                type = ContinuationType.URL_HYDRATE,
                status = ContinuationStatus.PENDING,
                inputUrl = url,
                scheduledAt = now,
                startedAt = null,
                completedAt = null,
                attemptCount = 0,
                failureReason = null
            )
        }

        // AuditAction is a closed v1 enum — `INFERENCE_RUN` carries the
        // OCR summary (kind, ocrLen, urlCount, dedupeHits) in extraJson
        // until a Room migration introduces a dedicated `OCR_RUN` action.
        val inferenceExtra = JSONObject().apply {
            put("kind", "ocr")
            put("ocrLen", (ocrText ?: "").length)
            put("urlCount", uniqueUrls.size)
            put("dedupeHits", dedupeHits.size)
        }.toString()
        val auditEntries = buildList {
            add(
                auditWriter.build(
                    action = AuditAction.INFERENCE_RUN,
                    description = "Screenshot OCR extracted ${uniqueUrls.size} URL(s) " +
                        "(${dedupeHits.size} deduped)",
                    envelopeId = envelopeId,
                    extraJson = inferenceExtra
                )
            )
            for ((url, existingResultId) in dedupeHits) {
                add(
                    auditWriter.build(
                        action = AuditAction.URL_DEDUPE_HIT,
                        description = "Reused existing continuation result $existingResultId for $url",
                        envelopeId = envelopeId,
                        extraJson = """{"url":${JSONObject.quote(url)},"resultId":${JSONObject.quote(existingResultId)}}"""
                    )
                )
            }
        }

        runBlocking {
            backend.seedScreenshotHydrationsTransaction(
                continuations = continuationRows,
                auditEntries = auditEntries
            )
        }

        // Enqueue the URL_HYDRATE jobs AFTER the Room txn commits — same
        // rule as seal(): WorkManager owns its own durability and
        // enqueueing inside a Room transaction deadlocks its init.
        continuationEngine?.let { engine ->
            for (row in continuationRows) {
                engine.enqueueSingle(
                    envelopeId = envelopeId,
                    continuationId = row.id,
                    url = row.inputUrl.orEmpty()
                )
            }
        }
    }

    // ---- Spec 003 v1.1 — Orbit Actions ----

    override fun lookupAppFunction(functionId: String): com.capsule.app.data.ipc.AppFunctionSummaryParcel? {
        val delegate = actionsDelegate ?: return null
        return runBlocking { delegate.lookupAppFunction(functionId) }
    }

    override fun listAppFunctions(appPackage: String): List<com.capsule.app.data.ipc.AppFunctionSummaryParcel> {
        val delegate = actionsDelegate ?: return emptyList()
        return runBlocking { delegate.listAppFunctions(appPackage) }
    }

    override fun recordActionInvocation(
        executionId: String,
        proposalId: String,
        functionId: String,
        outcome: String,
        outcomeReason: String?,
        dispatchedAtMillis: Long,
        completedAtMillis: Long,
        latencyMs: Long,
        episodeId: String?
    ) {
        val delegate = actionsDelegate ?: return
        val parsed = runCatching {
            com.capsule.app.data.model.ActionExecutionOutcome.valueOf(outcome)
        }.getOrElse { com.capsule.app.data.model.ActionExecutionOutcome.FAILED }
        runBlocking {
            delegate.recordActionInvocation(
                executionId = executionId,
                proposalId = proposalId,
                functionId = functionId,
                outcome = parsed,
                outcomeReason = outcomeReason,
                dispatchedAtMillis = dispatchedAtMillis,
                completedAtMillis = completedAtMillis,
                latencyMs = latencyMs,
                episodeId = episodeId
            )
        }
    }

    override fun markProposalConfirmed(proposalId: String): Boolean {
        val delegate = actionsDelegate ?: return false
        return runBlocking { delegate.markProposalConfirmed(proposalId) }
    }

    override fun markProposalDismissed(proposalId: String): Boolean {
        val delegate = actionsDelegate ?: return false
        return runBlocking { delegate.markProposalDismissed(proposalId) }
    }

    override fun observeProposalsForEnvelope(
        envelopeId: String,
        observer: com.capsule.app.data.ipc.IActionProposalObserver
    ) {
        actionsDelegate?.observeProposalsForEnvelope(envelopeId, observer)
    }

    override fun stopObservingProposals(observer: com.capsule.app.data.ipc.IActionProposalObserver) {
        actionsDelegate?.stopObservingProposals(observer)
    }

    /**
     * T044 — entry point for [com.capsule.app.ai.extract.ActionExtractionWorker].
     * The worker runs in the default WorkManager process; binding to :ml's
     * EnvelopeRepositoryService and calling this method is the only way
     * the worker reaches [ActionExtractor] (which holds the OrbitDatabase
     * reference owned by :ml).
     *
     * Outcome encoding (kept simple-string so we don't have to expand the
     * AIDL parcel surface for one method):
     *   "PROPOSED:N"            — N rows inserted
     *   "NO_CANDIDATES"         — extractor produced nothing
     *   "SKIPPED:<reason>"      — kind/sensitivity gate skipped
     *   "FAILED:<reason>"       — Nano timeout / exception → caller retries
     *   "UNAVAILABLE"           — extractor not wired (test/contract config)
     */
    override fun extractActionsForEnvelope(envelopeId: String): String {
        val extractor = actionExtractor ?: return "UNAVAILABLE"
        return runBlocking {
            when (val outcome = extractor.extract(envelopeId)) {
                is ExtractOutcome.NoCandidates -> "NO_CANDIDATES"
                is ExtractOutcome.Proposed     -> "PROPOSED:${outcome.proposalIds.size}"
                is ExtractOutcome.Skipped      -> "SKIPPED:${outcome.reason}"
                is ExtractOutcome.Failed       -> "FAILED:${outcome.reason}"
            }
        }
    }

    // T061 — local-target dispatch for `tasks.createTodo`. Returns the
    // ids of newly created derived envelopes (in input order); empty
    // list when delegate is unavailable, parent is missing, or items
    // array is empty/malformed. See ActionsRepositoryDelegate.
    override fun createDerivedTodoEnvelope(
        parentEnvelopeId: String,
        itemsJson: String,
        proposalId: String
    ): List<String> {
        val delegate = actionsDelegate ?: return emptyList()
        return runBlocking {
            delegate.createDerivedTodoEnvelope(parentEnvelopeId, itemsJson, proposalId)
        }
    }

    // T064 — checkbox toggle for derived-todo envelopes. Silently
    // no-ops when envelope/index/json are invalid.
    override fun setTodoItemDone(envelopeId: String, itemIndex: Int, done: Boolean) {
        val delegate = actionsDelegate ?: return
        runBlocking { delegate.setTodoItemDone(envelopeId, itemIndex, done) }
    }

    // T072/T074 — weekly digest entry point. Returns a stable outcome
    // string per AIDL contract; the caller (WeeklyDigestWorker) maps
    // GENERATED:* / SKIPPED:* to Result.success() and FAILED:* to retry.
    override fun runWeeklyDigest(targetDayLocal: String): String {
        val delegate = weeklyDigestDelegate ?: return "FAILED:digest_disabled"
        return runBlocking { delegate.runWeeklyDigest(targetDayLocal) }
    }

    // ---- Helpers ----

    private fun computeDayLocal(nowMillis: Long, tzId: String): String {
        return Instant.ofEpochMilli(nowMillis)
            .atZone(ZoneId.of(tzId))
            .toLocalDate()
            .toString()
    }

    private fun initialHistoryJson(at: Long, intent: String, source: String): String {
        val arr = JSONArray()
        arr.put(
            JSONObject().apply {
                put("at", at)
                put("intent", intent)
                put("source", source)
            }
        )
        return arr.toString()
    }

    private fun redactionCountsJson(counts: Map<String, Int>): String {
        val obj = JSONObject()
        for ((k, v) in counts) obj.put(k, v)
        return obj.toString()
    }

    private fun appendHistory(
        existing: String,
        at: Long,
        intent: String,
        source: String,
        reason: String?
    ): String {
        val arr = runCatching { JSONArray(existing) }.getOrDefault(JSONArray())
        arr.put(
            JSONObject().apply {
                put("at", at)
                put("intent", intent)
                put("source", source)
                if (reason != null) put("reason", reason)
            }
        )
        return arr.toString()
    }

    private fun IntentEnvelopeEntity.toViewParcel(
        latestResult: com.capsule.app.data.entity.ContinuationResultEntity? = null
    ): EnvelopeViewParcel = EnvelopeViewParcel(
        id = id,
        contentType = contentType.name,
        textContent = textContent,
        imageUri = imageUri,
        intent = intent.name,
        intentSource = intentSource.name,
        createdAtMillis = createdAt,
        dayLocal = dayLocal,
        isArchived = isArchived,
        title = latestResult?.title,
        domain = latestResult?.domain,
        excerpt = latestResult?.excerpt,
        summary = latestResult?.summary,
        appCategory = state.appCategory.name,
        activityState = state.activityState.name,
        hourLocal = state.hourLocal,
        dayOfWeekLocal = state.dayOfWeekLocal,
        intentHistoryJson = intentHistoryJson,
        canonicalUrl = latestResult?.canonicalUrl,
        deletedAtMillis = deletedAt,
        todoMetaJson = todoMetaJson
    )

    /** Equality by the underlying IBinder identity so observer lifecycles cancel cleanly. */
    // Lifted to top-level [IBinderKey] in v1.1 (003) so [ActionsRepositoryDelegate]
    // can share the same wrapper.

    companion object {
        const val UNDO_WINDOW_MS: Long = 10_000L
    }
}
