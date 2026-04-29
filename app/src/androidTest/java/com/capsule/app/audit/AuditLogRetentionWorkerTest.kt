package com.capsule.app.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.capsule.app.data.OrbitDatabase
import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.security.KeystoreKeyProvider
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * T086 — `AuditLogRetentionWorker` end-to-end test.
 *
 * Per audit-log-contract.md §2 / data-model.md §Retention:
 *   • Rows older than 90 days are purged.
 *   • Retention runs **silently** — no audit-of-audit row.
 *
 * This test seeds 100 rows dated 91 days ago + 10 fresh rows, runs the
 * worker via `TestListenableWorkerBuilder`, and asserts only 10 rows
 * survive and no new audit row of any kind was written by the worker.
 *
 * Driving the worker (rather than just `purge`) catches WorkManager wiring
 * regressions — `OrbitDatabase.getInstance` opens a real on-device encrypted
 * DB inside the worker, so this also exercises the SQLCipher passphrase
 * roundtrip on the test device.
 */
@RunWith(AndroidJUnit4::class)
class AuditLogRetentionWorkerTest {

    private lateinit var db: OrbitDatabase
    private lateinit var context: Context

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        context = ApplicationProvider.getApplicationContext()
        // Open the real singleton DB so the worker (which calls
        // `OrbitDatabase.getInstance`) sees the seeded rows.
        db = OrbitDatabase.getInstance(context)
        // Make sure the table is empty before each run so prior tests can't
        // leak rows into us.
        kotlinx.coroutines.runBlocking {
            db.auditLogDao().deleteOlderThan(Long.MAX_VALUE)
        }
    }

    @After
    fun tearDown() {
        kotlinx.coroutines.runBlocking {
            db.auditLogDao().deleteOlderThan(Long.MAX_VALUE)
        }
        AuditLogRetentionWorker.clockOverride = null
    }

    @Test
    fun worker_purgesOnlyOldRows_andWritesNoAuditOfAudit() = runTest {
        val dao = db.auditLogDao()
        val now = System.currentTimeMillis()
        val ninetyOneDays = TimeUnit.DAYS.toMillis(91)

        // 100 rows aged 91 days.
        repeat(100) { i ->
            dao.insert(
                AuditLogEntryEntity(
                    id = "old-$i-${UUID.randomUUID()}",
                    at = now - ninetyOneDays - i.toLong(),
                    action = AuditAction.ENVELOPE_CREATED,
                    description = "old #$i",
                    envelopeId = UUID.randomUUID().toString(),
                    extraJson = null
                )
            )
        }
        // 10 fresh rows aged < 1 day.
        repeat(10) { i ->
            dao.insert(
                AuditLogEntryEntity(
                    id = "fresh-$i-${UUID.randomUUID()}",
                    at = now - i.toLong() * 1_000L,
                    action = AuditAction.ENVELOPE_CREATED,
                    description = "fresh #$i",
                    envelopeId = UUID.randomUUID().toString(),
                    extraJson = null
                )
            )
        }
        assertEquals(110, dao.listAll().size)

        // Pin the worker's clock so the 91-day cutoff is stable.
        AuditLogRetentionWorker.clockOverride = { now }

        val worker = TestListenableWorkerBuilder<AuditLogRetentionWorker>(context).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)

        val survivors = dao.listAll()
        assertEquals(10, survivors.size)
        assertTrue(survivors.all { it.id.startsWith("fresh-") })

        // Silent retention: the worker MUST NOT write any new row.
        // (No "audit of audit" — purge itself is invisible per the contract.)
        // We seeded only ENVELOPE_CREATED rows, so any other action would
        // betray a stray write.
        assertTrue(
            "Retention worker must not write any new audit rows",
            survivors.all { it.action == AuditAction.ENVELOPE_CREATED }
        )
    }

    @Test
    fun worker_succeedsCleanlyWhenNothingToPurge() = runTest {
        val dao = db.auditLogDao()
        val now = System.currentTimeMillis()
        repeat(3) { i ->
            dao.insert(
                AuditLogEntryEntity(
                    id = "fresh-$i-${UUID.randomUUID()}",
                    at = now - i.toLong() * 1_000L,
                    action = AuditAction.ENVELOPE_CREATED,
                    description = "fresh #$i",
                    envelopeId = null,
                    extraJson = null
                )
            )
        }
        AuditLogRetentionWorker.clockOverride = { now }

        val worker = TestListenableWorkerBuilder<AuditLogRetentionWorker>(context).build()
        val result = worker.doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals(3, dao.listAll().size)
    }
}
