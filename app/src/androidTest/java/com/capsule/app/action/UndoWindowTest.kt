package com.capsule.app.action

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.ActionsRepositoryDelegate
import com.capsule.app.data.AppFunctionRegistry
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.entity.ActionProposalEntity
import com.capsule.app.data.model.ActionExecutionOutcome
import com.capsule.app.data.model.ActionProposalState
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.LlmProvenance
import com.capsule.app.data.model.SensitivityScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * T038 — undo-window contract tests covering the Phase 3 US1 cancel path.
 *
 * Scope is split across two layers so each invariant lands at the
 * appropriate seam:
 *   1. **DB-level contract** (this file, via [ActionsRepositoryDelegate]):
 *      cancelling a DISPATCHED action via a follow-up
 *      `recordActionInvocation` with [ActionExecutionOutcome.USER_CANCELLED]
 *      MUST update the existing `action_execution` row in-place
 *      (not insert a duplicate) AND write a single
 *      `ACTION_FAILED reason=user_cancelled` audit row alongside the
 *      original `ACTION_EXECUTED` audit. Per
 *      `specs/003-orbit-actions/contracts/action-execution-contract.md`
 *      §5 dispatch table.
 *   2. **Process-singleton expiry guard** (this file, via
 *      [ActionExecutorService.Companion]): once
 *      [DelayedUndoCleanupWorker] fires `expireUndoWindow`, the
 *      executionId is observable via `isExpired` so any subsequent
 *      `cancelWithinUndoWindow` short-circuits to `false` regardless of
 *      whether `pendingUndo` still has the hook.
 *
 * The full binder-level wire — `IActionExecutor.cancelWithinUndoWindow`
 * crossing process boundaries from `:ui` to `:capture` — is covered by
 * T040 [ExecutionIpcContractTest].
 */
@RunWith(AndroidJUnit4::class)
class UndoWindowTest {

    private lateinit var db: OrbitDatabase
    private lateinit var registry: AppFunctionRegistry
    private lateinit var delegate: ActionsRepositoryDelegate
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var clock: Long = 1_700_000_000_000L
    private val now: () -> Long = { clock }

    @Before
    fun setUp() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val auditWriter = AuditLogWriter(clock = now, idGen = { UUID.randomUUID().toString() })
        registry = AppFunctionRegistry(
            database = db,
            skillDao = db.appFunctionSkillDao(),
            usageDao = db.skillUsageDao(),
            auditLogDao = db.auditLogDao(),
            auditWriter = auditWriter,
            now = now
        )
        registry.registerAll(BuiltInAppFunctionSchemas.ALL)
        delegate = ActionsRepositoryDelegate(
            database = db,
            registry = registry,
            auditWriter = auditWriter,
            scope = scope,
            clock = now
        )
        seedEnvelope("env-1")
        delegate.writeProposals("env-1", listOf(makeProposal("p1", "calendar.createEvent")))
        ActionExecutorService.clearExpiredForTesting()
    }

    @After
    fun tearDown() {
        db.close()
        ActionExecutorService.clearExpiredForTesting()
    }

    @Test
    fun cancelWithinWindow_flipsOutcomeToUserCancelled_inPlace() = runTest {
        val executionId = "exec-cancel-1"
        val dispatchedAt = clock

        // 1. Initial DISPATCHED record (mirrors ActionExecutorService.execute path).
        delegate.recordActionInvocation(
            executionId = executionId,
            proposalId = "p1",
            functionId = "calendar.createEvent",
            outcome = ActionExecutionOutcome.DISPATCHED,
            outcomeReason = null,
            dispatchedAtMillis = dispatchedAt,
            completedAtMillis = dispatchedAt,
            latencyMs = 12L,
            episodeId = null
        )
        val initial = db.actionExecutionDao().getById(executionId)!!
        assertEquals(ActionExecutionOutcome.DISPATCHED, initial.outcome)

        // 2. User taps undo within the 5s window. ActionExecutorService's
        //    pendingUndo hook re-enters recordActionInvocation with
        //    USER_CANCELLED. Simulate the same call directly.
        clock += 1_500L
        delegate.recordActionInvocation(
            executionId = executionId,
            proposalId = "p1",
            functionId = "calendar.createEvent",
            outcome = ActionExecutionOutcome.USER_CANCELLED,
            outcomeReason = "user_cancelled",
            dispatchedAtMillis = dispatchedAt,
            completedAtMillis = clock,
            latencyMs = clock - dispatchedAt,
            episodeId = null
        )

        // 3. Outcome flipped IN-PLACE — single row, USER_CANCELLED.
        val flipped = db.actionExecutionDao().getById(executionId)!!
        assertEquals(ActionExecutionOutcome.USER_CANCELLED, flipped.outcome)
        assertEquals("user_cancelled", flipped.outcomeReason)
        // dispatchedAt preserved (executionDao.markOutcome only rewrites outcome/completed/latency).
        assertEquals(dispatchedAt, flipped.dispatchedAt)

        // 4. Audit log: original ACTION_EXECUTED (from DISPATCHED) PLUS
        //    a new ACTION_FAILED reason=user_cancelled (per contract §5).
        val audits = db.auditLogDao().listAll()
        val executed = audits.filter { it.action == AuditAction.ACTION_EXECUTED }
        val failed = audits.filter { it.action == AuditAction.ACTION_FAILED }
        assertEquals("dispatched audit row preserved", 1, executed.size)
        assertEquals("cancel writes one ACTION_FAILED row", 1, failed.size)
        val cancelPayload = failed.first().extraJson!!
        assertTrue(
            "audit payload must carry user_cancelled reason",
            cancelPayload.contains("\"reason\":\"user_cancelled\"")
        )
        assertTrue(
            "audit payload must carry the executionId",
            cancelPayload.contains("\"executionId\":\"$executionId\"")
        )
        assertTrue(
            "audit payload outcome field must read USER_CANCELLED",
            cancelPayload.contains("\"outcome\":\"USER_CANCELLED\"")
        )
    }

    @Test
    fun cancelAfterExpiry_isObservedByIsExpired() {
        // Process-singleton invariant: once the cleanup worker fires, the
        // executionId is in the expired set and any subsequent
        // cancelWithinUndoWindow call short-circuits to false.
        val executionId = "exec-expired-1"
        assertFalse(
            "fresh executionId must not appear expired",
            ActionExecutorService.isExpired(executionId)
        )

        ActionExecutorService.expireUndoWindow(executionId)
        assertTrue(
            "expireUndoWindow must mark id as expired",
            ActionExecutorService.isExpired(executionId)
        )

        // Other ids are unaffected — set is keyed per executionId.
        assertFalse(ActionExecutorService.isExpired("exec-other"))
    }

    @Test
    fun cleanupWorker_noOps_whenOutcomeAlreadyTerminal() = runTest {
        // The "no-op if outcome already terminal" rule is observable as:
        // even after the cleanup-worker fires expireUndoWindow, the
        // already-flipped USER_CANCELLED row is left untouched. There's
        // no DB write from the worker — its only effect is the
        // companion-level expiry flag. Validate by flipping then expiring
        // and confirming the execution row + audit log are unchanged.
        val executionId = "exec-terminal-1"
        val dispatchedAt = clock
        delegate.recordActionInvocation(
            executionId, "p1", "calendar.createEvent",
            ActionExecutionOutcome.DISPATCHED, null,
            dispatchedAt, dispatchedAt, 8L, null
        )
        clock += 800L
        delegate.recordActionInvocation(
            executionId, "p1", "calendar.createEvent",
            ActionExecutionOutcome.USER_CANCELLED, "user_cancelled",
            dispatchedAt, clock, clock - dispatchedAt, null
        )
        val rowBefore = db.actionExecutionDao().getById(executionId)!!
        val auditsBefore = db.auditLogDao().listAll().size

        // Worker fires its expiry flag — should NOT touch the DB.
        ActionExecutorService.expireUndoWindow(executionId)

        val rowAfter = db.actionExecutionDao().getById(executionId)!!
        val auditsAfter = db.auditLogDao().listAll().size
        assertEquals(rowBefore.outcome, rowAfter.outcome)
        assertEquals(rowBefore.outcomeReason, rowAfter.outcomeReason)
        assertEquals(rowBefore.completedAt, rowAfter.completedAt)
        assertEquals(
            "expireUndoWindow MUST NOT write any audit row",
            auditsBefore, auditsAfter
        )
        assertTrue(ActionExecutorService.isExpired(executionId))
    }

    @Test
    fun dispatchedRow_singleton_underReentrantCancel() = runTest {
        // Defensive: even if cancel is fired twice (e.g., a duplicate
        // user-tap before the toast disappears), recordActionInvocation
        // routes the second call through executionDao.markOutcome, never
        // duplicating the action_execution row.
        val executionId = "exec-dup-cancel"
        val dispatchedAt = clock
        delegate.recordActionInvocation(
            executionId, "p1", "calendar.createEvent",
            ActionExecutionOutcome.DISPATCHED, null,
            dispatchedAt, dispatchedAt, 4L, null
        )
        delegate.recordActionInvocation(
            executionId, "p1", "calendar.createEvent",
            ActionExecutionOutcome.USER_CANCELLED, "user_cancelled",
            dispatchedAt, clock + 100L, 100L, null
        )
        delegate.recordActionInvocation(
            executionId, "p1", "calendar.createEvent",
            ActionExecutionOutcome.USER_CANCELLED, "user_cancelled",
            dispatchedAt, clock + 200L, 200L, null
        )
        // Still exactly one row (no duplicate inserts under reentrant cancel).
        assertEquals(1, db.actionExecutionDao().countForProposal("p1"))
        // And the row settled at USER_CANCELLED with the correct reason.
        val finalRow = db.actionExecutionDao().getById(executionId)!!
        assertEquals(ActionExecutionOutcome.USER_CANCELLED, finalRow.outcome)
        assertEquals("user_cancelled", finalRow.outcomeReason)
    }

    @Test
    fun originalDispatchedRow_isInsert_notUpdate_onFirstWrite() = runTest {
        // Sanity: the very first recordActionInvocation creates the row;
        // no prior INSERT exists, so the delegate path takes the
        // executionDao.insert branch, not markOutcome. Validates that the
        // T038 cancel path's "update in-place" semantics are predicated
        // on a previously-inserted row.
        val executionId = "exec-fresh"
        assertNull(db.actionExecutionDao().getById(executionId))
        delegate.recordActionInvocation(
            executionId, "p1", "calendar.createEvent",
            ActionExecutionOutcome.DISPATCHED, null,
            clock, clock, 3L, null
        )
        val row = db.actionExecutionDao().getById(executionId)
        assertNotNull(row)
        assertEquals(ActionExecutionOutcome.DISPATCHED, row!!.outcome)
    }

    // ---- Helpers ----

    private fun makeProposal(id: String, functionId: String) = ActionProposalEntity(
        id = id,
        envelopeId = "env-1",
        functionId = functionId,
        schemaVersion = 1,
        argsJson = "{\"title\":\"Lunch\"}",
        previewTitle = "Lunch",
        previewSubtitle = null,
        confidence = 0.82f,
        provenance = LlmProvenance.LOCAL_NANO,
        state = ActionProposalState.PROPOSED,
        sensitivityScope = SensitivityScope.PUBLIC,
        createdAt = clock,
        stateChangedAt = clock
    )

    private fun seedEnvelope(id: String) {
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO intent_envelope(
                id, contentType, textContent, imageUri, textContentSha256,
                intent, intentConfidence, intentSource, intentHistoryJson,
                createdAt, day_local, isArchived, isDeleted, deletedAt,
                sharedContinuationResultId, appCategory, activityState,
                tzId, hourLocal, dayOfWeekLocal,
                kind, derivedFromEnvelopeIdsJson, todoMetaJson
            ) VALUES('$id', 'TEXT', 't', NULL, NULL,
                'ARCHIVE', NULL, 'USER', '[]',
                $clock, '2026-04-26', 0, 0, NULL, NULL,
                'OTHER', 'FOCUSED', 'UTC', 12, 5,
                'REGULAR', NULL, NULL)
            """.trimIndent()
        )
    }
}
