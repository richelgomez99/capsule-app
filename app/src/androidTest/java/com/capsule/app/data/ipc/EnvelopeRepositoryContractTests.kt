package com.capsule.app.data.ipc

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.EnvelopeRepositoryImpl
import com.capsule.app.data.LocalRoomBackend
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.security.KeystoreKeyProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Base wiring shared by all EnvelopeRepositoryImpl contract tests (T029–T033c).
 *
 * ADJUSTMENT: These contract tests drive [EnvelopeRepositoryImpl] directly
 * instead of binding to [EnvelopeRepositoryService] over AIDL. The repository
 * IS the `IEnvelopeRepository.Stub` implementation, so exercising it directly
 * is semantically equivalent to an in-process bind while avoiding service
 * lifecycle flakiness in instrumented tests. Cross-process binding will be
 * exercised by an integration test in Phase 10 (quickstart.md §3).
 */
abstract class RepositoryContractTestBase {

    protected lateinit var db: OrbitDatabase
    protected lateinit var repository: EnvelopeRepositoryImpl
    protected lateinit var clock: FakeClock
    protected val scopeJob: Job = SupervisorJob()
    protected val scope: CoroutineScope = CoroutineScope(scopeJob)

    @Before
    fun baseSetUp() {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val passphrase = KeystoreKeyProvider.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)

        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()

        clock = FakeClock(1_700_000_000_000L)
        repository = EnvelopeRepositoryImpl(
            backend = LocalRoomBackend(db),
            auditWriter = AuditLogWriter(clock = { clock.now }),
            scope = scope,
            clock = { clock.now }
        )
    }

    @After
    fun baseTearDown() {
        scopeJob.cancel()
        db.close()
    }

    protected fun draftText(text: String = "Hello Orbit"): IntentEnvelopeDraftParcel =
        IntentEnvelopeDraftParcel(
            contentType = "TEXT",
            textContent = text,
            imageUri = null,
            intent = "AMBIGUOUS",
            intentConfidence = null,
            intentSource = "FALLBACK"
        )

    protected fun stateUtc(): StateSnapshotParcel = StateSnapshotParcel(
        appCategory = "OTHER",
        activityState = "STILL",
        tzId = "UTC",
        hourLocal = 10,
        dayOfWeekLocal = 2
    )

    class FakeClock(var now: Long) {
        fun advance(ms: Long) {
            now += ms
        }
    }
}

/** T029 — seal writes envelope + audit row in a single transaction. */
@RunWith(AndroidJUnit4::class)
class EnvelopeRepositorySealContractTest : RepositoryContractTestBase() {

    @Test
    fun seal_writesEnvelopeAndOneEnvelopeCreatedAudit() = runTest {
        val id = repository.seal(draftText("https://example.com/article"), stateUtc())

        val envelope = db.intentEnvelopeDao().getById(id)
        assertNotNull(envelope)
        assertEquals("https://example.com/article", envelope!!.textContent)

        val auditRows = db.auditLogDao().entriesForEnvelope(id)
        assertEquals(1, auditRows.size)
        assertEquals(AuditAction.ENVELOPE_CREATED, auditRows[0].action)

        // T068: seal writes a PENDING ContinuationEntity per non-deduped URL.
        // Engine is null in this test, so no WorkManager enqueue happens — the
        // row still lands inside the same sealTransaction. On a fresh DB no
        // prior result exists, so the one URL produces one PENDING row.
        val continuations = db.continuationDao().getByEnvelopeId(id)
        assertEquals(1, continuations.size)
        assertEquals("https://example.com/article", continuations[0].inputUrl)
        assertEquals(
            com.capsule.app.data.model.ContinuationStatus.PENDING,
            continuations[0].status
        )
    }
}

/** T030 — undo rolls back within window, refuses after window. */
@RunWith(AndroidJUnit4::class)
class EnvelopeRepositoryUndoContractTest : RepositoryContractTestBase() {

    @Test
    fun undo_withinWindow_hardDeletesAndClearsAudit() = runTest {
        val id = repository.seal(draftText(), stateUtc())
        clock.advance(5_000L)

        assertTrue(repository.undo(id))
        assertNull(db.intentEnvelopeDao().getById(id))
        assertEquals(0, db.auditLogDao().entriesForEnvelope(id).size)
    }

    @Test
    fun undo_outsideWindow_returnsFalseAndKeepsEnvelope() = runTest {
        val id = repository.seal(draftText(), stateUtc())
        clock.advance(11_000L)

        assertFalse(repository.undo(id))
        assertNotNull(db.intentEnvelopeDao().getById(id))
    }
}

/** T031 — reassignIntent appends history and writes INTENT_SUPERSEDED audit row. */
@RunWith(AndroidJUnit4::class)
class EnvelopeRepositoryReassignContractTest : RepositoryContractTestBase() {

    @Test
    fun reassign_appendsHistoryAndWritesAudit() = runTest {
        val id = repository.seal(draftText(), stateUtc())
        clock.advance(60_000L)

        repository.reassignIntent(id, "REFERENCE", "user_changed_mind")

        val envelope = db.intentEnvelopeDao().getById(id)!!
        assertEquals("REFERENCE", envelope.intent.name)
        assertEquals("DIARY_REASSIGN", envelope.intentSource.name)

        val history = org.json.JSONArray(envelope.intentHistoryJson)
        assertEquals(2, history.length())
        assertEquals("REFERENCE", history.getJSONObject(1).getString("intent"))

        val audits = db.auditLogDao().entriesForEnvelope(id)
        assertTrue(audits.any { it.action == AuditAction.INTENT_SUPERSEDED })
    }
}

/** T033c — soft-delete + restore lifecycle. */
@RunWith(AndroidJUnit4::class)
class EnvelopeRepositorySoftDeleteContractTest : RepositoryContractTestBase() {

    @Test
    fun softDelete_then_restore_emitsCorrectAuditRowsAndState() = runTest {
        val id = repository.seal(draftText(), stateUtc())
        // Move past the undo window so delete() takes the soft-delete path.
        clock.advance(30_000L)

        repository.delete(id)

        val deleted = db.intentEnvelopeDao().getById(id)
        assertNotNull(deleted)
        assertNotNull(deleted!!.deletedAt)
        assertTrue(deleted.isDeleted)

        val trashList = repository.listSoftDeletedWithinDays(30)
        assertEquals(1, trashList.size)
        assertEquals(id, trashList[0].id)
        assertEquals(1, repository.countSoftDeletedWithinDays(30))

        val audits = db.auditLogDao().entriesForEnvelope(id)
        assertTrue(audits.any { it.action == AuditAction.ENVELOPE_SOFT_DELETED })

        repository.restoreFromTrash(id)
        val restored = db.intentEnvelopeDao().getById(id)!!
        assertNull(restored.deletedAt)
        assertFalse(restored.isDeleted)

        val audits2 = db.auditLogDao().entriesForEnvelope(id)
        assertTrue(audits2.any { it.action == AuditAction.ENVELOPE_RESTORED })
    }
}
