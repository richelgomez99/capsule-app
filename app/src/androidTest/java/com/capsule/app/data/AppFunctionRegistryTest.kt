package com.capsule.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.action.AppFunctionSchema
import com.capsule.app.action.BuiltInAppFunctionSchemas
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.model.ActionExecutionOutcome
import com.capsule.app.data.model.AppFunctionSideEffect
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.Reversibility
import com.capsule.app.data.model.SensitivityScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * T030 — round-trip + idempotency tests for [AppFunctionRegistry].
 *
 * Spec hooks:
 *  - data-model.md §4 (`appfunction_skill`): primary key on `functionId`,
 *    schema bumps REPLACE the row; `registeredAt` MUST be preserved across
 *    bumps (only `updatedAt` advances) so the audit log can reconstruct the
 *    "first-seen" provenance trail.
 *  - constitution Principle IX: every registry mutation writes an
 *    `APPFUNCTION_REGISTERED` audit row inside the same Room transaction.
 *  - constitution audit atomicity rule: a no-op (same `functionId` + same
 *    `schemaVersion`) MUST NOT write an audit row; otherwise process restarts
 *    would generate one phantom row per skill per boot.
 *
 * Uses an unencrypted in-memory Room DB. SQLCipher engagement is covered
 * separately by `OrbitDatabaseTest` and the migration is exercised by
 * `OrbitDatabaseMigrationV1toV2Test`.
 */
@RunWith(AndroidJUnit4::class)
class AppFunctionRegistryTest {

    private lateinit var db: OrbitDatabase
    private lateinit var registry: AppFunctionRegistry

    private var clock: Long = 1_700_000_000_000L
    private val now: () -> Long = { clock }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        registry = AppFunctionRegistry(
            database = db,
            skillDao = db.appFunctionSkillDao(),
            usageDao = db.skillUsageDao(),
            auditLogDao = db.auditLogDao(),
            auditWriter = AuditLogWriter(clock = now, idGen = { UUID.randomUUID().toString() }),
            now = now
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun registerAll_seedsBuiltIns_andEmitsOneAuditRowPerSkill() = runTest {
        registry.registerAll(BuiltInAppFunctionSchemas.ALL)

        // All built-ins are present at the registered schemaVersion.
        for (schema in BuiltInAppFunctionSchemas.ALL) {
            val row = registry.lookupLatest(schema.functionId)
            assertNotNull("missing built-in ${schema.functionId}", row)
            assertEquals(schema.schemaVersion, row!!.schemaVersion)
            assertEquals(schema.appPackage, row.appPackage)
            assertEquals(schema.displayName, row.displayName)
            assertEquals(clock, row.registeredAt)
            assertEquals(clock, row.updatedAt)
        }

        // Exactly one APPFUNCTION_REGISTERED audit row per built-in.
        val auditRows = db.auditLogDao().listAll()
        val registeredRows = auditRows.filter { it.action == AuditAction.APPFUNCTION_REGISTERED }
        assertEquals(BuiltInAppFunctionSchemas.ALL.size, registeredRows.size)
        // extraJson carries functionId + schemaVersion for forensics.
        for (schema in BuiltInAppFunctionSchemas.ALL) {
            val match = registeredRows.firstOrNull { it.extraJson?.contains(schema.functionId) == true }
            assertNotNull("audit row missing for ${schema.functionId}", match)
            assertTrue(
                "audit row for ${schema.functionId} omits schemaVersion",
                match!!.extraJson!!.contains("\"schemaVersion\":${schema.schemaVersion}")
            )
        }
    }

    @Test
    fun registerAll_isIdempotent_noOpWhenSameVersion() = runTest {
        registry.registerAll(BuiltInAppFunctionSchemas.ALL)
        val auditCountFirst = db.auditLogDao().listAll().size

        // Second call at the same version: no DB writes, no audit rows.
        clock += 60_000L
        registry.registerAll(BuiltInAppFunctionSchemas.ALL)
        val auditCountSecond = db.auditLogDao().listAll().size
        assertEquals(auditCountFirst, auditCountSecond)

        // updatedAt MUST NOT advance on the no-op path — otherwise we
        // pollute the "last changed" signal the Settings screen surfaces.
        for (schema in BuiltInAppFunctionSchemas.ALL) {
            val row = registry.lookupLatest(schema.functionId)!!
            assertEquals(1_700_000_000_000L, row.updatedAt)
            assertEquals(1_700_000_000_000L, row.registeredAt)
        }
    }

    @Test
    fun registerAll_replacesRow_butPreservesRegisteredAt_onSchemaBump() = runTest {
        val initial = AppFunctionSchema(
            functionId = "calendar.createEvent",
            appPackage = "com.capsule.app",
            displayName = "Add to Calendar",
            description = "v1",
            schemaVersion = 1,
            argsSchemaJson = "{\"type\":\"object\"}",
            sideEffects = AppFunctionSideEffect.EXTERNAL_INTENT,
            reversibility = Reversibility.EXTERNAL_MANAGED,
            // CALENDAR_WRITE was speculative; the production enum has
            // PUBLIC / PERSONAL / SHARE_DELEGATED only. Calendar writes
            // align to PERSONAL (matches BuiltInAppFunctionSchemas).
            sensitivityScope = SensitivityScope.PERSONAL
        )
        registry.registerAll(listOf(initial))
        val v1 = registry.lookupLatest("calendar.createEvent")!!
        val originalRegisteredAt = v1.registeredAt

        // Schema bump.
        clock += 5 * 60_000L
        registry.registerAll(listOf(initial.copy(schemaVersion = 2, description = "v2")))
        val v2 = registry.lookupLatest("calendar.createEvent")!!

        assertEquals(2, v2.schemaVersion)
        assertEquals("v2", v2.description)
        assertEquals(
            "registeredAt MUST be preserved across schema bumps",
            originalRegisteredAt, v2.registeredAt
        )
        assertEquals("updatedAt MUST advance to bump time", clock, v2.updatedAt)

        // lookupExact still resolves the prior version's row in case the
        // proposal pinned it. (REPLACE keeps PK semantics; we only have one
        // row at any time, so the v1 lookup MUST return null — that's the
        // signal the executor uses to fail with `schema_invalidated`.)
        assertNull(registry.lookupExact("calendar.createEvent", 1))
        assertNotNull(registry.lookupExact("calendar.createEvent", 2))

        // Two audit rows: initial register + bump.
        val auditRows = db.auditLogDao().listAll()
            .filter { it.action == AuditAction.APPFUNCTION_REGISTERED }
        assertEquals(2, auditRows.size)
    }

    @Test
    fun registerAll_emptyList_isANoOp() = runTest {
        registry.registerAll(emptyList())
        assertEquals(0, db.appFunctionSkillDao().listAll().size)
        assertEquals(0, db.auditLogDao().listAll().size)
    }

    @Test
    fun recordInvocation_writesUsageRow_andStatsAggregate() = runTest {
        registry.registerAll(BuiltInAppFunctionSchemas.ALL)

        // Seed an envelope + proposal + execution row so the FK chain is valid.
        val envelopeId = "env-1"
        seedEnvelope(envelopeId)
        val proposalId = seedProposal(envelopeId, "calendar.createEvent")
        val executionId = seedExecution(proposalId, "calendar.createEvent", ActionExecutionOutcome.DISPATCHED)

        registry.recordInvocation(
            skillId = "calendar.createEvent",
            executionId = executionId,
            proposalId = proposalId,
            episodeId = null,
            outcome = ActionExecutionOutcome.DISPATCHED,
            latencyMs = 42L
        )

        val stats = registry.stats("calendar.createEvent", sinceMillis = 0L)
        assertNotNull(stats)
        assertEquals(1, stats!!.invocationCount)
        // success = SUCCESS + DISPATCHED per SkillUsageDao aggregation contract.
        assertEquals(1.0, stats.successRate, 0.001)
        assertEquals(0.0, stats.cancelRate, 0.001)
        assertEquals(42.0, stats.avgLatencyMs, 0.001)
    }

    // ---- Helpers ----

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

    private fun seedProposal(envelopeId: String, functionId: String): String {
        val id = "p-${UUID.randomUUID()}"
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO action_proposal(
                id, envelopeId, functionId, schemaVersion, argsJson,
                previewTitle, previewSubtitle, confidence, provenance,
                state, sensitivityScope, createdAt, stateChangedAt
            ) VALUES('$id', '$envelopeId', '$functionId', 1, '{}',
                'Add', NULL, 0.9, 'LOCAL_NANO', 'CONFIRMED',
                'CALENDAR_WRITE', $clock, $clock)
            """.trimIndent()
        )
        return id
    }

    private fun seedExecution(
        proposalId: String,
        functionId: String,
        outcome: ActionExecutionOutcome
    ): String {
        val id = "e-${UUID.randomUUID()}"
        db.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO action_execution(
                id, proposalId, functionId, outcome, outcomeReason,
                dispatchedAt, completedAt, latencyMs, episodeId
            ) VALUES('$id', '$proposalId', '$functionId', '${outcome.name}', NULL,
                $clock, $clock, 42, NULL)
            """.trimIndent()
        )
        return id
    }
}
