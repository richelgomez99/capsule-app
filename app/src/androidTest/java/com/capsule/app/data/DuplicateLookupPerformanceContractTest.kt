package com.capsule.app.data

import android.content.Context
import android.os.SystemClock
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import com.capsule.app.data.security.KeystoreKeyProvider
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DuplicateLookupPerformanceContractTest {

    private lateinit var db: OrbitDatabase

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
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun duplicateLookup_usesIndexedKeysWithinBudget() = runTest {
        seedVisibleEnvelopes(count = 1_000)
        seedArchivedAndDeletedControls()

        val dao = db.intentEnvelopeDao()
        val urlDurations = mutableListOf<Long>()
        val textDurations = mutableListOf<Long>()

        repeat(120) { iteration ->
            val urlHash = "url-${iteration % 500}"
            val textHash = "text-${iteration % 500}"

            val urlStart = SystemClock.elapsedRealtimeNanos()
            assertEquals(urlHash, dao.findActiveByPrimaryCanonicalUrlHash(urlHash)?.primaryCanonicalUrlHash)
            urlDurations += SystemClock.elapsedRealtimeNanos() - urlStart

            val textStart = SystemClock.elapsedRealtimeNanos()
            assertEquals(textHash, dao.findActiveByTextContentSha256(textHash)?.textContentSha256)
            textDurations += SystemClock.elapsedRealtimeNanos() - textStart
        }

        assertTrue("URL duplicate lookup p95 must stay under 50 ms", p95Millis(urlDurations) < 50.0)
        assertTrue("Text duplicate lookup p95 must stay under 50 ms", p95Millis(textDurations) < 50.0)
    }

    private suspend fun seedVisibleEnvelopes(count: Int) {
        repeat(count) { index ->
            val isUrlRow = index < count / 2
            db.intentEnvelopeDao().insert(
                envelope(
                    id = "env-$index",
                    textContent = if (isUrlRow) "https://example.com/$index" else "note $index",
                    primaryCanonicalUrlHash = if (isUrlRow) "url-$index" else null,
                    textContentSha256 = if (isUrlRow) null else "text-${index - count / 2}",
                    createdAt = 1_700_000_000_000L + index
                )
            )
        }
    }

    private suspend fun seedArchivedAndDeletedControls() {
        db.intentEnvelopeDao().insert(
            envelope(
                id = "archived-control",
                textContent = "https://example.com/archived",
                primaryCanonicalUrlHash = "url-42",
                textContentSha256 = null,
                createdAt = 1_700_000_999_000L,
                isArchived = true
            )
        )
        db.intentEnvelopeDao().insert(
            envelope(
                id = "deleted-control",
                textContent = "deleted note",
                primaryCanonicalUrlHash = null,
                textContentSha256 = "text-42",
                createdAt = 1_700_000_999_001L,
                isDeleted = true,
                deletedAt = 1_700_000_999_001L
            )
        )
    }

    private fun envelope(
        id: String,
        textContent: String,
        primaryCanonicalUrlHash: String?,
        textContentSha256: String?,
        createdAt: Long,
        isArchived: Boolean = false,
        isDeleted: Boolean = false,
        deletedAt: Long? = null
    ): IntentEnvelopeEntity = IntentEnvelopeEntity(
        id = id,
        contentType = ContentType.TEXT,
        textContent = textContent,
        imageUri = null,
        textContentSha256 = textContentSha256,
        intent = Intent.AMBIGUOUS,
        intentConfidence = null,
        intentSource = IntentSource.FALLBACK,
        intentHistoryJson = "[]",
        state = StateSnapshot(
            appCategory = AppCategory.OTHER,
            activityState = ActivityState.STILL,
            tzId = "UTC",
            hourLocal = 10,
            dayOfWeekLocal = 2
        ),
        createdAt = createdAt,
        dayLocal = "2023-11-14",
        isArchived = isArchived,
        isDeleted = isDeleted,
        deletedAt = deletedAt,
        primaryCanonicalUrlHash = primaryCanonicalUrlHash
    )

    private fun p95Millis(durationsNanos: List<Long>): Double {
        val sorted = durationsNanos.sorted()
        val index = ((sorted.size - 1) * 0.95).toInt()
        return sorted[index] / 1_000_000.0
    }
}
