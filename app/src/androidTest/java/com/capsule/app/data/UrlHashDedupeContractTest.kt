package com.capsule.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.audit.AuditLogWriter
import com.capsule.app.data.entity.ContinuationEntity
import com.capsule.app.data.entity.ContinuationResultEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.ipc.IntentEnvelopeDraftParcel
import com.capsule.app.data.ipc.SealResultParcel
import com.capsule.app.data.ipc.StateSnapshotParcel
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.ContinuationStatus
import com.capsule.app.data.model.ContinuationType
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import com.capsule.app.data.security.KeystoreKeyProvider
import com.capsule.app.net.CanonicalUrlHasher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
    fun pendingUrlDuplicate_returnsAlreadySaved_beforeHydrationCompletes() = runTest {
        val first = repository.sealWithResult(draft("https://example.com/pending"), state())
        clock.advance(1_000L)

        val second = repository.sealWithResult(draft("https://www.example.com/pending/"), state())

        assertEquals(SealResultParcel.STATUS_CREATED, first.status)
        assertEquals(SealResultParcel.STATUS_ALREADY_SAVED, second.status)
        assertEquals(SealResultParcel.MATCHED_BY_CANONICAL_URL, second.matchedBy)
        assertEquals(first.envelopeId, second.envelopeId)
        assertEquals(1, db.intentEnvelopeDao().countAll())
        assertEquals(1, db.continuationDao().getByEnvelopeId(first.envelopeId).size)
    }

    @Test
    fun scenarioB_trackingParamDiffers_hitsCache() = runTest {
        val rawA = "https://example.com/a?utm_source=twitter"
        val rawB = "https://example.com/a?utm_source=email"
        val seeded = seedResult(rawA)

        val result = repository.sealWithResult(draft(rawB), state())

        assertEquals(SealResultParcel.STATUS_ALREADY_SAVED, result.status)
        assertEquals(SealResultParcel.MATCHED_BY_CANONICAL_URL, result.matchedBy)
        assertEquals(seeded.envelopeId, result.envelopeId)
        assertEquals(1, db.intentEnvelopeDao().countAll())
        val duplicateRows = db.auditLogDao()
            .entriesForEnvelope(seeded.envelopeId)
            .filter { it.action == AuditAction.DUPLICATE_CAPTURE_ATTEMPT }
        assertEquals(1, duplicateRows.size)
    }

    @Test
    fun legacyHydrationCache_withoutEnvelopeDuplicateKey_returnsAlreadySaved() = runTest {
        val rawA = "https://example.com/legacy?utm_source=twitter"
        val rawB = "https://example.com/legacy?utm_source=email"
        val seeded = seedLegacyResultWithoutDuplicateKey(rawA)

        val result = repository.sealWithResult(draft(rawB), state())

        assertEquals(SealResultParcel.STATUS_ALREADY_SAVED, result.status)
        assertEquals(SealResultParcel.MATCHED_BY_CANONICAL_URL, result.matchedBy)
        assertEquals(seeded.envelopeId, result.envelopeId)
        assertEquals(1, db.intentEnvelopeDao().countAll())
        val duplicateRows = db.auditLogDao()
            .entriesForEnvelope(seeded.envelopeId)
            .filter { it.action == AuditAction.DUPLICATE_CAPTURE_ATTEMPT }
        assertEquals(1, duplicateRows.size)
    }

    @Test
    fun legacyExactText_withoutHashOrHydration_returnsAlreadySaved() = runTest {
        val seededEnvelopeId = seedLegacyEnvelopeWithoutDuplicateKeys("same old note")

        val result = repository.sealWithResult(draft("same old note"), state())

        assertEquals(SealResultParcel.STATUS_ALREADY_SAVED, result.status)
        assertEquals(SealResultParcel.MATCHED_BY_EXACT_TEXT, result.matchedBy)
        assertEquals(seededEnvelopeId, result.envelopeId)
        assertEquals(1, db.intentEnvelopeDao().countAll())
    }

    @Test
    fun scenarioC_wwwStrip_hitsCache() = runTest {
        val seeded = seedResult("https://example.com/a")
        val result = repository.sealWithResult(draft("https://www.example.com/a"), state())

        assertEquals(SealResultParcel.STATUS_ALREADY_SAVED, result.status)
        assertEquals(seeded.envelopeId, result.envelopeId)
    }

    @Test
    fun scenarioD_rootTrailingSlash_hitsCache() = runTest {
        val seeded = seedResult("https://example.com")
        val result = repository.sealWithResult(draft("https://example.com/"), state())

        assertEquals(SealResultParcel.STATUS_ALREADY_SAVED, result.status)
        assertEquals(seeded.envelopeId, result.envelopeId)
    }

    @Test
    fun scenarioE_pathCaseDiffers_missesCache() = runTest {
        seedResult("https://example.com/A")
        val id = repository.seal(draft("https://example.com/a"), state())

        // Path case preserved in canonicalisation — different hashes → PENDING row.
        assertEquals(1, db.continuationDao().getByEnvelopeId(id).size)
        assertNull(db.intentEnvelopeDao().getById(id)!!.sharedContinuationResultId)
    }

    @Test
    fun exactNonUrlTextDuplicate_returnsAlreadySaved_noSecondEnvelope() = runTest {
        val first = repository.sealWithResult(draft("same exact note"), state())
        clock.advance(1_000L)
        val second = repository.sealWithResult(draft("same exact note"), state())

        assertEquals(SealResultParcel.STATUS_CREATED, first.status)
        assertEquals(SealResultParcel.STATUS_ALREADY_SAVED, second.status)
        assertEquals(SealResultParcel.MATCHED_BY_EXACT_TEXT, second.matchedBy)
        assertEquals(first.envelopeId, second.envelopeId)
        assertEquals(1, db.intentEnvelopeDao().countAll())
        assertNotNull(db.intentEnvelopeDao().getById(first.envelopeId)!!.textContentSha256)
        val duplicateRows = db.auditLogDao()
            .entriesForEnvelope(first.envelopeId)
            .filter { it.action == AuditAction.DUPLICATE_CAPTURE_ATTEMPT }
        assertEquals(1, duplicateRows.size)
        assertTrue(duplicateRows.single().extraJson?.contains("same exact note") != true)
    }

    @Test
    fun concurrentSameUrlCapture_databaseGuardAllowsOnlyOneVisibleEnvelope() = runTest {
        val results = (0 until 12).map {
            async(Dispatchers.Default) {
                repository.sealWithResult(draft("https://example.com/race?utm_source=$it"), state())
            }
        }.awaitAll()

        assertEquals(1, results.count { it.status == SealResultParcel.STATUS_CREATED })
        assertEquals(11, results.count { it.status == SealResultParcel.STATUS_ALREADY_SAVED })
        assertEquals(1, db.intentEnvelopeDao().countAll())
        val createdId = results.single { it.status == SealResultParcel.STATUS_CREATED }.envelopeId
        assertTrue(results.all { it.envelopeId == createdId })
        val duplicateRows = db.auditLogDao()
            .entriesForEnvelope(createdId)
            .filter { it.action == AuditAction.DUPLICATE_CAPTURE_ATTEMPT }
        assertEquals(11, duplicateRows.size)
    }

    @Test
    fun duplicateLookup_ignoresArchivedAndSoftDeletedEnvelopes() = runTest {
        val archived = repository.sealWithResult(draft("https://example.com/archived"), state())
        repository.archive(archived.envelopeId)
        clock.advance(1_000L)

        val afterArchive = repository.sealWithResult(draft("https://example.com/archived"), state())

        val deleted = repository.sealWithResult(draft("https://example.com/deleted"), state())
        repository.delete(deleted.envelopeId)
        clock.advance(1_000L)

        val afterDelete = repository.sealWithResult(draft("https://example.com/deleted"), state())

        assertEquals(SealResultParcel.STATUS_CREATED, afterArchive.status)
        assertEquals(SealResultParcel.STATUS_CREATED, afterDelete.status)
        assertNotEquals(archived.envelopeId, afterArchive.envelopeId)
        assertNotEquals(deleted.envelopeId, afterDelete.envelopeId)
    }

    // ---- Helpers ----

    private suspend fun seedResult(rawUrl: String): SeededUrlResult {
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
        return SeededUrlResult(envelopeId = seedEnvelopeId, resultId = resultId)
    }

    private suspend fun seedLegacyResultWithoutDuplicateKey(rawUrl: String): SeededUrlResult {
        val envelopeId = seedLegacyEnvelopeWithoutDuplicateKeys(rawUrl)
        val continuationId = UUID.randomUUID().toString()
        val resultId = UUID.randomUUID().toString()
        db.continuationDao().insert(
            ContinuationEntity(
                id = continuationId,
                envelopeId = envelopeId,
                type = ContinuationType.URL_HYDRATE,
                status = ContinuationStatus.SUCCEEDED,
                inputUrl = rawUrl,
                scheduledAt = clock.now,
                startedAt = clock.now,
                completedAt = clock.now,
                attemptCount = 1,
                failureReason = null
            )
        )
        db.continuationResultDao().insert(
            ContinuationResultEntity(
                id = resultId,
                continuationId = continuationId,
                envelopeId = envelopeId,
                producedAt = clock.now,
                title = "Legacy seed title",
                domain = "example.com",
                canonicalUrl = rawUrl,
                canonicalUrlHash = CanonicalUrlHasher.hash(rawUrl),
                excerpt = null,
                summary = "Legacy seed summary.",
                summaryModel = "nano-v1"
            )
        )
        clock.advance(1_000L)
        return SeededUrlResult(envelopeId = envelopeId, resultId = resultId)
    }

    private suspend fun seedLegacyEnvelopeWithoutDuplicateKeys(text: String): String {
        val envelopeId = UUID.randomUUID().toString()
        db.intentEnvelopeDao().insert(
            IntentEnvelopeEntity(
                id = envelopeId,
                contentType = ContentType.TEXT,
                textContent = text,
                imageUri = null,
                textContentSha256 = null,
                intent = Intent.AMBIGUOUS,
                intentConfidence = null,
                intentSource = IntentSource.FALLBACK,
                intentHistoryJson = "[]",
                state = StateSnapshot(
                    appCategory = AppCategory.OTHER,
                    activityState = com.capsule.app.data.model.ActivityState.STILL,
                    tzId = "UTC",
                    hourLocal = 10,
                    dayOfWeekLocal = 2
                ),
                createdAt = clock.now,
                dayLocal = "2023-11-14",
                primaryCanonicalUrlHash = null
            )
        )
        clock.advance(1_000L)
        return envelopeId
    }

    private data class SeededUrlResult(val envelopeId: String, val resultId: String)

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
