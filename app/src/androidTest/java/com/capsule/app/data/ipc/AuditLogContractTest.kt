package com.capsule.app.data.ipc

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.audit.AuditLogImpl
import com.capsule.app.audit.AuditLogRetentionWorker
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.EnvelopeRepositoryImpl
import com.capsule.app.data.LocalRoomBackend
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.security.KeystoreKeyProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * T085 — Audit log contract test (audit-log-contract.md §9).
 *
 * Verifies the closed audit-action surface end-to-end:
 *   (a) Each mutation through `EnvelopeRepositoryImpl` produces exactly one
 *       audit row with the correct `AuditAction`:
 *         seal      → ENVELOPE_CREATED
 *         reassign  → INTENT_SUPERSEDED
 *         archive   → ENVELOPE_ARCHIVED
 *         delete    → ENVELOPE_SOFT_DELETED   (T033b: delete is soft-delete)
 *         restore   → ENVELOPE_RESTORED
 *         hardDel   → ENVELOPE_HARD_PURGED
 *   (b) `IAuditLog.entriesForDay` returns only that day's rows (LIMIT 1000).
 *   (c) `IAuditLog.countForDay` matches the row count for an action.
 *   (d) Retention worker (T089) deletes rows older than 90 days; nothing
 *       is fabricated for fresh rows.
 *
 * Pattern matches `EnvelopeRepositoryContractTests.kt` — drives the impl
 * directly because the impl IS the binder Stub.
 */
@RunWith(AndroidJUnit4::class)
class AuditLogContractTest {

    private lateinit var db: OrbitDatabase
    private lateinit var repository: EnvelopeRepositoryImpl
    private lateinit var auditBinder: AuditLogImpl
    private val clock = MutableClock(BASE_MILLIS)
    private val scopeJob: Job = SupervisorJob()
    private val scope: CoroutineScope = CoroutineScope(scopeJob)

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val passphrase = KeystoreKeyProvider.getOrCreatePassphrase(context)
        val factory = SupportOpenHelperFactory(passphrase)

        db = Room.inMemoryDatabaseBuilder(context, OrbitDatabase::class.java)
            .openHelperFactory(factory)
            .allowMainThreadQueries()
            .build()

        repository = EnvelopeRepositoryImpl(
            backend = LocalRoomBackend(db),
            auditWriter = AuditLogWriter(clock = { clock.now }),
            scope = scope,
            clock = { clock.now }
        )
        auditBinder = AuditLogImpl(db.auditLogDao(), zoneId = ZoneId.of("UTC"))
    }

    @After
    fun tearDown() {
        scopeJob.cancel()
        db.close()
    }

    /** (a) seal writes exactly one ENVELOPE_CREATED row tagged with the envelope id. */
    @Test
    fun seal_writesExactlyOneEnvelopeCreatedAuditRow() = runTest {
        val id = repository.seal(textDraft("hello"), state())

        val rows = db.auditLogDao().entriesForEnvelope(id)
            .filter { it.action == AuditAction.ENVELOPE_CREATED }
        assertEquals(1, rows.size)
        assertEquals(id, rows[0].envelopeId)
    }

    /** (a) reassign writes exactly one INTENT_SUPERSEDED row. */
    @Test
    fun reassignIntent_writesExactlyOneIntentSupersededRow() = runTest {
        val id = repository.seal(textDraft("hello"), state())
        clock.advance(60_000L)

        repository.reassignIntent(id, "REFERENCE", "user_changed_mind")

        val rows = db.auditLogDao().entriesForEnvelope(id)
            .filter { it.action == AuditAction.INTENT_SUPERSEDED }
        assertEquals(1, rows.size)
        assertTrue(rows[0].extraJson?.contains("user_changed_mind") == true)
    }

    /** (a) archive writes exactly one ENVELOPE_ARCHIVED row. */
    @Test
    fun archive_writesExactlyOneEnvelopeArchivedRow() = runTest {
        val id = repository.seal(textDraft("hello"), state())
        clock.advance(30_000L)

        repository.archive(id)

        val rows = db.auditLogDao().entriesForEnvelope(id)
            .filter { it.action == AuditAction.ENVELOPE_ARCHIVED }
        assertEquals(1, rows.size)
    }

    /**
     * (a) delete writes exactly one ENVELOPE_SOFT_DELETED row (T033b: delete
     * is soft-delete by default in v1).
     */
    @Test
    fun delete_writesExactlyOneEnvelopeSoftDeletedRow() = runTest {
        val id = repository.seal(textDraft("hello"), state())
        clock.advance(30_000L)

        repository.delete(id)

        val rows = db.auditLogDao().entriesForEnvelope(id)
            .filter { it.action == AuditAction.ENVELOPE_SOFT_DELETED }
        assertEquals(1, rows.size)
    }

    /** (a) restoreFromTrash writes exactly one ENVELOPE_RESTORED row. */
    @Test
    fun restoreFromTrash_writesExactlyOneEnvelopeRestoredRow() = runTest {
        val id = repository.seal(textDraft("hello"), state())
        clock.advance(30_000L)
        repository.delete(id)
        clock.advance(60_000L)

        repository.restoreFromTrash(id)

        val rows = db.auditLogDao().entriesForEnvelope(id)
            .filter { it.action == AuditAction.ENVELOPE_RESTORED }
        assertEquals(1, rows.size)
    }

    /** (a) hardDelete writes exactly one ENVELOPE_HARD_PURGED row. */
    @Test
    fun hardDelete_writesExactlyOneEnvelopeHardPurgedRow() = runTest {
        val id = repository.seal(textDraft("hello"), state())
        clock.advance(30_000L)
        repository.delete(id)
        clock.advance(60_000L)

        repository.hardDelete(id)

        // The envelope row is gone, but the audit row about its purge survives.
        val rows = db.auditLogDao().entriesForEnvelope(id)
            .filter { it.action == AuditAction.ENVELOPE_HARD_PURGED }
        assertEquals(1, rows.size)
        assertTrue(rows[0].extraJson?.contains("user_purge") == true)
    }

    /**
     * (b) entriesForDay returns only rows from the requested local day, and
     *     in newest-first order.
     */
    @Test
    fun entriesForDay_returnsOnlyThatDay_newestFirst() = runTest {
        // Two seals today, one seal yesterday.
        val today = todayUtc()
        val yesterday = today.minusDays(1)

        clock.now = today.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() + 9_000_000L
        repository.seal(textDraft("today-1"), state())
        clock.advance(1_000L)
        repository.seal(textDraft("today-2"), state())

        clock.now = yesterday.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() + 9_000_000L
        repository.seal(textDraft("yesterday-1"), state())

        val todayEntries = auditBinder.entriesForDay(today.toString())
        val yesterdayEntries = auditBinder.entriesForDay(yesterday.toString())

        assertEquals(2, todayEntries.count { it.action == AuditAction.ENVELOPE_CREATED.name })
        assertEquals(1, yesterdayEntries.count { it.action == AuditAction.ENVELOPE_CREATED.name })

        // Newest-first ordering inside a day.
        for (i in 1 until todayEntries.size) {
            assertTrue(
                "Expected entriesForDay to be sorted descending by atMillis",
                todayEntries[i - 1].atMillis >= todayEntries[i].atMillis
            )
        }
    }

    /** (c) countForDay matches the actual row count for a given action. */
    @Test
    fun countForDay_matchesEnvelopeCreatedCount() = runTest {
        val today = todayUtc()
        clock.now = today.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli() + 9_000_000L

        repository.seal(textDraft("a"), state())
        clock.advance(1_000L)
        repository.seal(textDraft("b"), state())
        clock.advance(1_000L)
        repository.seal(textDraft("c"), state())

        val count = auditBinder.countForDay(
            today.toString(),
            AuditAction.ENVELOPE_CREATED.name
        )
        assertEquals(3, count)
    }

    /**
     * (d) Retention worker prunes rows older than [AuditLogRetentionWorker.RETENTION_DAYS].
     * Seeds 5 rows aged 91d + 5 fresh rows; expects only fresh rows to remain.
     * Fully covers T086 's retention assertion as well.
     */
    @Test
    fun retentionWorker_purgesOnlyEntriesOlderThanRetentionWindow() = runTest {
        val now = BASE_MILLIS
        val ninetyOneDays = TimeUnit.DAYS.toMillis(91)

        // Insert directly via DAO so we can control timestamps.
        val dao = db.auditLogDao()
        repeat(5) { i ->
            dao.insert(
                AuditLogEntryEntity(
                    id = "old-$i",
                    at = now - ninetyOneDays - i.toLong(),
                    action = AuditAction.ENVELOPE_CREATED,
                    description = "old #$i",
                    envelopeId = UUID.randomUUID().toString(),
                    extraJson = null
                )
            )
        }
        repeat(5) { i ->
            dao.insert(
                AuditLogEntryEntity(
                    id = "fresh-$i",
                    at = now - i.toLong(),
                    action = AuditAction.ENVELOPE_CREATED,
                    description = "fresh #$i",
                    envelopeId = UUID.randomUUID().toString(),
                    extraJson = null
                )
            )
        }
        assertEquals(10, dao.listAll().size)

        // Drive the static purge helper directly with a fixed cutoff —
        // semantically equivalent to running the worker, without WorkManager.
        val cutoff = now - AuditLogRetentionWorker.retentionWindowMillis()
        val deleted = AuditLogRetentionWorker.purge(dao, cutoff)

        assertEquals(5, deleted)
        val survivors = dao.listAll()
        assertEquals(5, survivors.size)
        assertTrue(survivors.all { it.id.startsWith("fresh-") })
    }

    // ---- helpers ----

    private fun textDraft(text: String): IntentEnvelopeDraftParcel =
        IntentEnvelopeDraftParcel(
            contentType = "TEXT",
            textContent = text,
            imageUri = null,
            intent = "AMBIGUOUS",
            intentConfidence = null,
            intentSource = "FALLBACK"
        )

    private fun state(): StateSnapshotParcel = StateSnapshotParcel(
        appCategory = "OTHER",
        activityState = "STILL",
        tzId = "UTC",
        hourLocal = 10,
        dayOfWeekLocal = 2
    )

    private fun todayUtc(): LocalDate =
        Instant.ofEpochMilli(clock.now).atZone(ZoneId.of("UTC")).toLocalDate()

    private class MutableClock(var now: Long) {
        fun advance(ms: Long) { now += ms }
    }

    companion object {
        private const val BASE_MILLIS = 1_700_000_000_000L
    }
}
