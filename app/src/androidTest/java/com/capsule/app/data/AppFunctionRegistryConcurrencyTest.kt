package com.capsule.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.action.AppFunctionSchema
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.model.AppFunctionSideEffect
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.Reversibility
import com.capsule.app.data.model.SensitivityScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * T031 — concurrency safety for [AppFunctionRegistry].
 *
 * `AppFunctionRegistry` serialises every mutation through a single [Mutex] +
 * a single Room transaction (`registerAll` body). The key invariant is:
 *
 *   N concurrent `registerAll([sameSchema])` calls MUST produce
 *     • exactly 1 row in `appfunction_skill`
 *     • exactly 1 `APPFUNCTION_REGISTERED` audit row
 *
 * Without the mutex+lookup-then-insert pattern, two callers could both observe
 * "no existing row at this version", both insert (REPLACE clobbers), and both
 * write an audit row — producing one phantom audit row per process restart
 * race. The test is the contract proof.
 *
 * **Source-set deviation** mirrors T030: tasks.md §T031 specifies JVM
 * placement, but the registry depends on Room runtime via
 * `database.withTransaction`. Until a Robolectric/JDBC harness is wired up,
 * the test lives under `androidTest/` and runs on emulator. Functional
 * equivalence is preserved.
 *
 * appfunction-registry-contract.md §6.
 */
@RunWith(AndroidJUnit4::class)
class AppFunctionRegistryConcurrencyTest {

    private lateinit var db: OrbitDatabase
    private lateinit var registry: AppFunctionRegistry

    private val clock: () -> Long = { 1_700_000_000_000L }

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java).build()
        registry = AppFunctionRegistry(
            database = db,
            skillDao = db.appFunctionSkillDao(),
            usageDao = db.skillUsageDao(),
            auditLogDao = db.auditLogDao(),
            auditWriter = AuditLogWriter(clock = clock, idGen = { UUID.randomUUID().toString() }),
            now = clock
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun concurrentRegisterAll_sameSchema_producesExactlyOneRowAndOneAudit() = runBlocking {
        val schema = sampleSchema(version = 1)

        // 8 concurrent calls on a real worker pool.
        val jobs = withContext(Dispatchers.Default) {
            (1..CONCURRENCY).map {
                async { registry.registerAll(listOf(schema)) }
            }
        }
        jobs.awaitAll()

        val rows = db.appFunctionSkillDao().listAll()
        assertEquals("exactly one skill row should exist", 1, rows.size)
        assertEquals(1, rows[0].schemaVersion)

        val audit = db.auditLogDao().listAll()
            .filter { it.action == AuditAction.APPFUNCTION_REGISTERED }
        assertEquals(
            "mutex+lookup-then-insert MUST collapse N concurrent inserts to 1 audit row",
            1,
            audit.size
        )
        assertNotNull(audit[0].extraJson)
        assert(audit[0].extraJson!!.contains(schema.functionId))
    }

    @Test
    fun concurrentSchemaBump_sameVersion_producesAtMostOneBumpAudit() = runBlocking {
        // Seed v1 sequentially.
        registry.registerAll(listOf(sampleSchema(version = 1)))
        val auditAfterSeed = db.auditLogDao().listAll()
            .count { it.action == AuditAction.APPFUNCTION_REGISTERED }
        assertEquals(1, auditAfterSeed)

        // Now race N callers all trying to bump to v2.
        val v2 = sampleSchema(version = 2)
        val jobs = withContext(Dispatchers.Default) {
            (1..CONCURRENCY).map { async { registry.registerAll(listOf(v2)) } }
        }
        jobs.awaitAll()

        val rows = db.appFunctionSkillDao().listAll()
        assertEquals(1, rows.size)
        assertEquals(2, rows[0].schemaVersion)

        val auditFinal = db.auditLogDao().listAll()
            .count { it.action == AuditAction.APPFUNCTION_REGISTERED }
        // Total = 1 (seed) + 1 (bump). The other N-1 racers see v2 already
        // present and no-op.
        assertEquals("schema bump under contention emits exactly one audit row", 2, auditFinal)
    }

    @Test
    fun concurrentRegisterAll_differentSchemas_eachProducesOneRow() = runBlocking {
        val schemas = (1..4).map { sampleSchema(functionId = "test.fn.$it", version = 1) }

        val jobs = withContext(Dispatchers.Default) {
            schemas.map { schema -> async { registry.registerAll(listOf(schema)) } }
        }
        jobs.awaitAll()

        val rows = db.appFunctionSkillDao().listAll()
        assertEquals(4, rows.size)
        val audit = db.auditLogDao().listAll()
            .filter { it.action == AuditAction.APPFUNCTION_REGISTERED }
        assertEquals(4, audit.size)
    }

    // ---- helpers ----

    private fun sampleSchema(
        functionId: String = "test.fn",
        version: Int
    ): AppFunctionSchema = AppFunctionSchema(
        functionId = functionId,
        appPackage = "com.capsule.app",
        displayName = "Test ${'$'}version",
        description = "v${'$'}version",
        schemaVersion = version,
        argsSchemaJson = """{"type":"object","additionalProperties":false}""",
        sideEffects = AppFunctionSideEffect.LOCAL_DB_WRITE,
        reversibility = Reversibility.REVERSIBLE_24H,
        sensitivityScope = SensitivityScope.PERSONAL
    )

    private companion object {
        private const val CONCURRENCY = 8
    }
}
