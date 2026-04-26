package com.capsule.app.ai.extract

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.action.BuiltInAppFunctionSchemas
import com.capsule.app.ai.LlmProvider
import com.capsule.app.ai.model.ActionCandidate
import com.capsule.app.ai.model.ActionExtractionResult
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.AppFunctionRegistry
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.ActionProposalState
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.EnvelopeKind
import com.capsule.app.data.model.LlmProvenance
import com.capsule.app.data.model.SensitivityScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T034 — pipeline coverage for [ActionExtractor].
 *
 * Lives in `androidTest/` (not `test/`) because the pipeline calls
 * `database.withTransaction { }` which needs the Room runtime — same
 * deviation as T030 [AppFunctionRegistryTest] and T031
 * [ActionsRepositoryDelegateTest]. Tracked in tasks.md status log.
 *
 * Coverage matrix per action-extraction-contract.md §4:
 *   1. Envelope-not-found  → NoCandidates
 *   2. kind=DIGEST         → Skipped("non_regular_kind")
 *   3. Forbidden REDACTED  → Skipped("sensitivity_changed")
 *   4. Blank text          → NoCandidates
 *   5. Empty registry      → NoCandidates (no Nano call)
 *   6. Nano timeout        → Failed("nano_timeout") + ACTION_FAILED audit
 *   7. Nano throw          → Failed("nano_<class>") + ACTION_FAILED audit
 *   8. Below confidence    → NoCandidates (no proposal, no audit)
 *   9. Schema-shape drop   → NoCandidates (argsJson > 4096B)
 *  10. PUBLIC scope vs REDACTED-non-credential → ACTION_DISMISSED audit only
 *  11. Happy-path          → Proposed + ACTION_PROPOSED audit (atomic)
 *  12. Unique-index re-run → second extract is a no-op (no dup proposal/audit)
 */
@RunWith(AndroidJUnit4::class)
class ActionExtractorTest {

    private lateinit var db: OrbitDatabase
    private lateinit var registry: AppFunctionRegistry
    private lateinit var auditWriter: AuditLogWriter

    private var clock: Long = 1_700_000_000_000L
    private val now: () -> Long = { clock }
    private var idCounter = 0
    private val idGen: () -> String = { "test-id-${++idCounter}" }

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        auditWriter = AuditLogWriter(clock = now, idGen = idGen)
        registry = AppFunctionRegistry(
            database = db,
            skillDao = db.appFunctionSkillDao(),
            usageDao = db.skillUsageDao(),
            auditLogDao = db.auditLogDao(),
            auditWriter = auditWriter,
            now = now
        )
        registry.registerAll(BuiltInAppFunctionSchemas.ALL)
    }

    @After
    fun tearDown() {
        db.close()
    }

    // ---------- (1) envelope not found ----------

    @Test
    fun extract_envelopeMissing_returnsNoCandidates() = runBlocking {
        val outcome = newExtractor(FakeLlm.empty()).extract("does-not-exist")
        assertEquals(ExtractOutcome.NoCandidates, outcome)
        assertEquals(0, countAudit(AuditAction.ACTION_PROPOSED))
        assertEquals(0, countAudit(AuditAction.ACTION_FAILED))
    }

    // ---------- (2) non-REGULAR kind ----------

    @Test
    fun extract_digestEnvelope_isSkipped() = runBlocking {
        seedEnvelope("env-digest", text = "Weekly summary", kind = EnvelopeKind.DIGEST)
        val outcome = newExtractor(FakeLlm.empty()).extract("env-digest")
        assertTrue(outcome is ExtractOutcome.Skipped)
        assertEquals("non_regular_kind", (outcome as ExtractOutcome.Skipped).reason)
    }

    // ---------- (3) sensitivity gate (forbidden REDACTED markers) ----------

    @Test
    fun extract_envelopeWithRedactedCredential_isSkipped() = runBlocking {
        seedEnvelope("env-sec", text = "Token leaked: [REDACTED_GITHUB_TOKEN] please rotate")
        val outcome = newExtractor(FakeLlm.fail()).extract("env-sec")
        assertTrue(outcome is ExtractOutcome.Skipped)
        assertEquals("sensitivity_changed", (outcome as ExtractOutcome.Skipped).reason)
        // Nano was never invoked — no failure audit either.
        assertEquals(0, countAudit(AuditAction.ACTION_FAILED))
    }

    // ---------- (4) blank text ----------

    @Test
    fun extract_blankText_returnsNoCandidates() = runBlocking {
        seedEnvelope("env-blank", text = "   ")
        val outcome = newExtractor(FakeLlm.empty()).extract("env-blank")
        assertEquals(ExtractOutcome.NoCandidates, outcome)
    }

    // ---------- (6) Nano timeout ----------

    @Test
    fun extract_nanoTimeout_recordsFailedAudit() = runBlocking {
        seedEnvelope("env-slow", text = "Lunch with Mia at noon Friday")
        val outcome = newExtractor(
            FakeLlm.delaying(delayMillis = 200L),
            timeoutMillis = 50L
        ).extract("env-slow")
        assertTrue(outcome is ExtractOutcome.Failed)
        assertEquals("nano_timeout", (outcome as ExtractOutcome.Failed).reason)
        assertEquals(1, countAudit(AuditAction.ACTION_FAILED))
        val row = auditRowsFor(AuditAction.ACTION_FAILED).first()
        assertTrue(row.extraJson!!.contains("nano_timeout"))
        assertEquals("env-slow", row.envelopeId)
    }

    // ---------- (7) Nano throw ----------

    @Test
    fun extract_nanoThrows_recordsFailedAudit_withClassName() = runBlocking {
        seedEnvelope("env-bad", text = "Lunch with Mia at noon Friday")
        val outcome = newExtractor(FakeLlm.fail()).extract("env-bad")
        assertTrue(outcome is ExtractOutcome.Failed)
        // The fake throws IllegalStateException → reason carries the simple class name.
        assertEquals("nano_IllegalStateException", (outcome as ExtractOutcome.Failed).reason)
        assertEquals(1, countAudit(AuditAction.ACTION_FAILED))
    }

    // ---------- (8) below-floor confidence ----------

    @Test
    fun extract_belowFloorCandidates_areDropped_noProposalsNoAudit() = runBlocking {
        seedEnvelope("env-low", text = "Lunch with Mia at noon Friday")
        val outcome = newExtractor(
            FakeLlm.singleCandidate(
                functionId = "calendar.createEvent",
                argsJson = """{"title":"Lunch","startEpochMillis":${clock + 86_400_000L}}""",
                confidence = 0.40f
            )
        ).extract("env-low")
        // All candidates dropped by the 0.55 floor and no sensitivity drops →
        // contract returns NoCandidates and emits no audit row.
        assertEquals(ExtractOutcome.NoCandidates, outcome)
        assertEquals(0, countAudit(AuditAction.ACTION_PROPOSED))
        assertEquals(0, countAudit(AuditAction.ACTION_DISMISSED))
        assertEquals(0, db.actionProposalDao().listAllForEnvelope("env-low").size)
    }

    // ---------- (9) malformed argsJson (>4096B / unparseable) ----------

    @Test
    fun extract_oversizedArgsJson_isDropped() = runBlocking {
        seedEnvelope("env-big", text = "Lunch with Mia at noon Friday")
        val giantArgs = "{\"title\":\"" + "x".repeat(5_000) + "\"}"
        val outcome = newExtractor(
            FakeLlm.singleCandidate(
                functionId = "calendar.createEvent",
                argsJson = giantArgs,
                confidence = 0.95f
            )
        ).extract("env-big")
        assertEquals(ExtractOutcome.NoCandidates, outcome)
        assertEquals(0, countAudit(AuditAction.ACTION_PROPOSED))
        assertEquals(0, db.actionProposalDao().listAllForEnvelope("env-big").size)
    }

    @Test
    fun extract_unparseableArgsJson_isDropped() = runBlocking {
        seedEnvelope("env-mal", text = "Lunch with Mia at noon Friday")
        val outcome = newExtractor(
            FakeLlm.singleCandidate(
                functionId = "calendar.createEvent",
                argsJson = "not json {{{",
                confidence = 0.95f
            )
        ).extract("env-mal")
        assertEquals(ExtractOutcome.NoCandidates, outcome)
        assertEquals(0, db.actionProposalDao().listAllForEnvelope("env-mal").size)
    }

    // ---------- (11) happy path ----------

    @Test
    fun extract_validCandidate_writesProposalAndAuditAtomically() = runBlocking {
        seedEnvelope("env-ok", text = "Lunch with Mia at noon Friday")
        val args = """{"title":"Lunch","startEpochMillis":${clock + 86_400_000L}}"""
        val outcome = newExtractor(
            FakeLlm.singleCandidate(
                functionId = "calendar.createEvent",
                argsJson = args,
                confidence = 0.78f
            )
        ).extract("env-ok")

        assertTrue(outcome is ExtractOutcome.Proposed)
        val ids = (outcome as ExtractOutcome.Proposed).proposalIds
        assertEquals(1, ids.size)

        // Proposal row exists, in PROPOSED state, with the right confidence + provenance.
        val proposal = db.actionProposalDao().getById(ids.first())!!
        assertEquals("calendar.createEvent", proposal.functionId)
        assertEquals(ActionProposalState.PROPOSED, proposal.state)
        assertEquals(0.78f, proposal.confidence, 0.0001f)
        assertEquals(LlmProvenance.LOCAL_NANO, proposal.provenance)
        assertEquals(args, proposal.argsJson)

        // Exactly one ACTION_PROPOSED audit row tied to the envelope.
        val proposed = auditRowsFor(AuditAction.ACTION_PROPOSED)
        assertEquals(1, proposed.size)
        assertEquals("env-ok", proposed.first().envelopeId)
        assertTrue(proposed.first().extraJson!!.contains("\"functionId\":\"calendar.createEvent\""))
        assertTrue(proposed.first().extraJson!!.contains("\"proposalId\":\"${proposal.id}\""))
    }

    // ---------- (12) unique-index re-run idempotency ----------

    @Test
    fun extract_rerunForSameEnvelope_isIdempotent() = runBlocking {
        seedEnvelope("env-rerun", text = "Lunch with Mia at noon Friday")
        val args = """{"title":"Lunch","startEpochMillis":${clock + 86_400_000L}}"""
        val extractor = newExtractor(
            FakeLlm.singleCandidate(
                functionId = "calendar.createEvent",
                argsJson = args,
                confidence = 0.80f
            )
        )

        val first = extractor.extract("env-rerun")
        assertTrue(first is ExtractOutcome.Proposed)
        assertEquals(1, db.actionProposalDao().listAllForEnvelope("env-rerun").size)
        assertEquals(1, countAudit(AuditAction.ACTION_PROPOSED))

        // Re-running with the same (envelopeId, functionId) MUST NOT
        // duplicate the proposal nor emit a phantom audit row.
        val second = extractor.extract("env-rerun")
        // Outcome surface is NoCandidates because nothing new was inserted.
        assertEquals(ExtractOutcome.NoCandidates, second)
        assertEquals(1, db.actionProposalDao().listAllForEnvelope("env-rerun").size)
        assertEquals(1, countAudit(AuditAction.ACTION_PROPOSED))
    }

    // ---------- (10) sensitivity-scope mismatch path ----------

    @Test
    fun extract_publicSkillVsRedactedNonCredentialEnvelope_writesDismissAuditOnly() = runBlocking {
        // Use a non-credential REDACTED marker so the forbidden-list gate
        // does NOT fire — the sensitivityScope-mismatch path SHOULD.
        // (FORBIDDEN list is credentials/medical/SSN/CC/JWT only.)
        seedEnvelope(
            "env-redacted-pii",
            text = "Order confirmed, ship to [REDACTED_ADDRESS] at noon Friday"
        )
        // calendar.createEvent registers as PUBLIC scope. Envelope text
        // contains [REDACTED_ — extractor's sensitivityScopeMatches PUBLIC
        // branch returns false → drop into droppedSensitivityIds.
        val outcome = newExtractor(
            FakeLlm.singleCandidate(
                functionId = "calendar.createEvent",
                argsJson = """{"title":"Pickup","startEpochMillis":${clock + 86_400_000L}}""",
                confidence = 0.90f,
                candidateScope = SensitivityScope.PUBLIC
            )
        ).extract("env-redacted-pii")

        // No proposal row, but exactly one ACTION_DISMISSED audit row
        // explaining the sensitivity_scope_mismatch drop.
        assertEquals(0, db.actionProposalDao().listAllForEnvelope("env-redacted-pii").size)
        assertEquals(0, countAudit(AuditAction.ACTION_PROPOSED))
        val dismissed = auditRowsFor(AuditAction.ACTION_DISMISSED)
        assertEquals(1, dismissed.size)
        assertTrue(dismissed.first().extraJson!!.contains("sensitivity_scope_mismatch"))
        // Outcome: nothing accepted, dropped-only → NoCandidates per contract.
        assertEquals(ExtractOutcome.NoCandidates, outcome)
    }

    // ---------- (5) empty registry ----------

    @Test
    fun extract_emptyRegistry_returnsNoCandidates_withoutCallingNano() = runBlocking {
        // Wipe registered skills via raw SQL (no DAO deleteAll method).
        db.openHelper.writableDatabase.execSQL("DELETE FROM appfunction_skill")
        seedEnvelope("env-nores", text = "Lunch with Mia at noon Friday")
        val tracker = TrackingLlm()
        val outcome = newExtractor(tracker).extract("env-nores")
        assertEquals(ExtractOutcome.NoCandidates, outcome)
        assertEquals("Nano MUST NOT be called when registry is empty", 0, tracker.callCount)
    }

    // ===== helpers =====

    private fun newExtractor(
        provider: LlmProvider,
        timeoutMillis: Long = 8_000L,
        confidenceFloor: Float = 0.55f
    ): ActionExtractor = ActionExtractor(
        database = db,
        envelopeDao = db.intentEnvelopeDao(),
        proposalDao = db.actionProposalDao(),
        auditLogDao = db.auditLogDao(),
        registry = registry,
        llmProvider = provider,
        auditWriter = auditWriter,
        confidenceFloor = confidenceFloor,
        llmTimeoutMillis = timeoutMillis,
        now = now,
        idGen = idGen
    )

    private fun seedEnvelope(
        id: String,
        text: String?,
        kind: EnvelopeKind = EnvelopeKind.REGULAR
    ) {
        // Use raw SQL like ActionsRepositoryDelegateTest — the entity has
        // many cols and Room enum mapping in tests is fragile.
        val safeText = text?.replace("'", "''")
        val textLit = if (safeText == null) "NULL" else "'$safeText'"
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO intent_envelope(
                id, contentType, textContent, imageUri, textContentSha256,
                intent, intentConfidence, intentSource, intentHistoryJson,
                createdAt, day_local, isArchived, isDeleted, deletedAt,
                sharedContinuationResultId, appCategory, activityState,
                tzId, hourLocal, dayOfWeekLocal,
                kind, derivedFromEnvelopeIdsJson, todoMetaJson
            ) VALUES('$id', 'TEXT', $textLit, NULL, NULL,
                'ARCHIVE', NULL, 'USER', '[]',
                $clock, '2026-04-26', 0, 0, NULL, NULL,
                'OTHER', 'FOCUSED', 'UTC', 12, 5,
                '${kind.name}', NULL, NULL)
            """.trimIndent()
        )
    }

    private suspend fun countAudit(action: AuditAction): Int =
        db.auditLogDao().listAll().count { it.action == action }

    private suspend fun auditRowsFor(action: AuditAction) =
        db.auditLogDao().listAll().filter { it.action == action }

    // ===== fakes =====

    /**
     * Minimal [LlmProvider] fake — stubs the entire interface but only
     * `extractActions` is exercised by the extractor pipeline.
     */
    private open class FakeLlm(
        private val onExtract: suspend (List<AppFunctionSummary>) -> ActionExtractionResult
    ) : LlmProvider {
        override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification =
            error("not used")
        override suspend fun summarize(text: String, maxTokens: Int): SummaryResult =
            error("not used")
        override suspend fun scanSensitivity(text: String): SensitivityResult =
            error("not used")
        override suspend fun generateDayHeader(
            dayIsoDate: String,
            envelopeSummaries: List<String>
        ): DayHeaderResult = error("not used")

        override suspend fun extractActions(
            text: String,
            contentType: String,
            state: StateSnapshot,
            registeredFunctions: List<AppFunctionSummary>,
            maxCandidates: Int
        ): ActionExtractionResult = onExtract(registeredFunctions)

        companion object {
            fun empty() = FakeLlm { ActionExtractionResult(LlmProvenance.LOCAL_NANO, emptyList()) }
            fun fail() = FakeLlm { error("synthetic nano failure") }
            fun delaying(delayMillis: Long) = FakeLlm {
                delay(delayMillis)
                ActionExtractionResult(LlmProvenance.LOCAL_NANO, emptyList())
            }
            fun singleCandidate(
                functionId: String,
                argsJson: String,
                confidence: Float,
                candidateScope: SensitivityScope = SensitivityScope.PERSONAL
            ) = FakeLlm { _ ->
                ActionExtractionResult(
                    provenance = LlmProvenance.LOCAL_NANO,
                    candidates = listOf(
                        ActionCandidate(
                            functionId = functionId,
                            schemaVersion = 1,
                            argsJson = argsJson,
                            previewTitle = "preview",
                            previewSubtitle = null,
                            confidence = confidence,
                            sensitivityScope = candidateScope
                        )
                    )
                )
            }
        }
    }

    private class TrackingLlm : FakeLlm({ _ ->
        ActionExtractionResult(LlmProvenance.LOCAL_NANO, emptyList())
    }) {
        var callCount: Int = 0
        override suspend fun extractActions(
            text: String,
            contentType: String,
            state: StateSnapshot,
            registeredFunctions: List<AppFunctionSummary>,
            maxCandidates: Int
        ): ActionExtractionResult {
            callCount++
            return super.extractActions(text, contentType, state, registeredFunctions, maxCandidates)
        }
    }
}
