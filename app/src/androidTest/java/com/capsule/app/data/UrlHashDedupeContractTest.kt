package com.capsule.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.entity.ContinuationResultEntity
import com.capsule.app.data.ipc.IntentEnvelopeDraftParcel
import com.capsule.app.data.ipc.StateSnapshotParcel
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.security.KeystoreKeyProvider
import com.capsule.app.net.CanonicalUrlHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
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
 * T066b — URL-hash dedupe contract.
 *
 * Drives [EnvelopeRepositoryImpl] directly (same pattern as
 * `EnvelopeRepositoryContractTests`) with a null [ContinuationEngine] so
 * no WorkManager fan-out happens — the test exercises the in-transaction
 * dedupe branch of `seal()` only.
 *
 * Scenarios (per T066b in tasks.md):
 *  - A: seal URL A → PENDING row + no dedupe hit.
 *  - Seed a [ContinuationResultEntity] for that canonical hash (simulating
 *    the worker having completed). Seal URL B (same canonical hash via
 *    tracking-param strip): no new PENDING row, `sharedContinuationResultId`
 *    matches seeded, exactly one `URL_DEDUPE_HIT` audit row.
 *  - C: seal with `https://www.example.com/a` after seeding `https://example.com/a`
 *    — hits (www-strip).
 *  - D: seal root `https://example.com/` after seeding `https://example.com`
 *    — hits (root preserved, empty path canonicalised same).
 *  - E: seal path-case-differing URL after seeding uppercase — MISSES
 *    (path case preserved).
 *  - F: seal `mailto:` twice — two independent envelopes, no dedupe beyond
 *    the identical-raw-input collision (skipped: not a meaningful contract).
 */
@RunWith(AndroidJUnit4::class)
class UrlHashDedupeContractTest {

    private lateinit var db: OrbitDatabase
    private lateinit var repository: EnvelopeRepositoryImpl
    private val clock = FakeClock(1_700_000_000_000L)
    private val scopeJob: Job = SupervisorJob()
    private val scope = CoroutineScope(scopeJob)

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
            // continuationEngine = null — no WorkManager in this test.
        )
    }

    @After
    fun tearDown() {
        scopeJob.cancel()
        db.close()
    }

    @Test
    fun scenarioA_firstSealProducesPendingRow_noHit() = runTest {
        val url = "https://example.com/a?utm_source=twitter"
        val id = repository.seal(draft(url), state())

        val continuations = db.continuationDao().getByEnvelopeId(id)
        assertEquals(1, continuations.size)
        assertNull(db.intentEnvelopeDao().getById(id)!!.sharedContinuationResultId)
        assertEquals(
            0,
            db.auditLogDao().entriesForEnvelope(id).count { it.action == AuditAction.URL_DEDUPE_HIT }
        )
    }

    @Test
    fun scenarioB_trackingParamDiffers_hitsCache() = runTest {
        val rawA = "https://example.com/a?utm_source=twitter"
        val rawB = "https://example.com/a?utm_source=email"
        val seededResultId = seedResult(rawA)

        val idB = repository.seal(draft(rawB), state())

        // No new PENDING row — dedupe hit short-circuits.
        assertEquals(0, db.continuationDao().getByEnvelopeId(idB).size)
        assertEquals(
            seededResultId,
            db.intentEnvelopeDao().getById(idB)!!.sharedContinuationResultId
        )
        val dedupeRows = db.auditLogDao()
            .entriesForEnvelope(idB)
            .filter { it.action == AuditAction.URL_DEDUPE_HIT }
        assertEquals(1, dedupeRows.size)
    }

    @Test
    fun scenarioC_wwwStrip_hitsCache() = runTest {
        seedResult("https://example.com/a")
        val id = repository.seal(draft("https://www.example.com/a"), state())

        assertEquals(0, db.continuationDao().getByEnvelopeId(id).size)
        assertNotNull(db.intentEnvelopeDao().getById(id)!!.sharedContinuationResultId)
    }

    @Test
    fun scenarioD_rootTrailingSlash_hitsCache() = runTest {
        seedResult("https://example.com")
        val id = repository.seal(draft("https://example.com/"), state())

        assertEquals(0, db.continuationDao().getByEnvelopeId(id).size)
        assertNotNull(db.intentEnvelopeDao().getById(id)!!.sharedContinuationResultId)
    }

    @Test
    fun scenarioE_pathCaseDiffers_missesCache() = runTest {
        seedResult("https://example.com/A")
        val id = repository.seal(draft("https://example.com/a"), state())

        // Path case preserved in canonicalisation — different hashes → PENDING row.
        assertEquals(1, db.continuationDao().getByEnvelopeId(id).size)
        assertNull(db.intentEnvelopeDao().getById(id)!!.sharedContinuationResultId)
    }

    // ---- Helpers ----

    private suspend fun seedResult(rawUrl: String): String {
        // To insert a ContinuationResultEntity we need a parent envelope +
        // continuation row (FK constraints). Seed via a seal(), then insert
        // a result keyed by the canonical hash.
        val seedEnvelopeId = repository.seal(draft(rawUrl), state())
        val seedContinuationId = db.continuationDao().getByEnvelopeId(seedEnvelopeId).single().id
        val resultId = UUID.randomUUID().toString()
        db.continuationResultDao().insert(
            ContinuationResultEntity(
                id = resultId,
                continuationId = seedContinuationId,
                envelopeId = seedEnvelopeId,
                producedAt = clock.now,
                title = "Seed title",
                domain = "example.com",
                canonicalUrl = rawUrl,
                canonicalUrlHash = CanonicalUrlHasher.hash(rawUrl),
                excerpt = null,
                summary = "Seed summary.",
                summaryModel = "nano-v1"
            )
        )
        clock.advance(1_000L)
        return resultId
    }

    private fun draft(url: String): IntentEnvelopeDraftParcel =
        IntentEnvelopeDraftParcel(
            contentType = "TEXT",
            textContent = url,
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

    class FakeClock(var now: Long) {
        fun advance(ms: Long) { now += ms }
    }
}
