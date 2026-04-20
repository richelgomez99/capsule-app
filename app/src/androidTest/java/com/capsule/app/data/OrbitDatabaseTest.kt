package com.capsule.app.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.data.entity.AuditLogEntryEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.AuditAction
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import com.capsule.app.data.security.KeystoreKeyProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

/**
 * Instrumented contract test: encrypted Room database opens successfully and
 * basic CRUD through DAOs works end-to-end.
 */
@RunWith(AndroidJUnit4::class)
class OrbitDatabaseTest {

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
    fun databaseOpensAndInsertReadWorks() = runTest {
        val id = UUID.randomUUID().toString()
        val envelope = IntentEnvelopeEntity(
            id = id,
            contentType = ContentType.TEXT,
            textContent = "Hello Orbit",
            imageUri = null,
            textContentSha256 = null,
            intent = Intent.SAVE,
            intentConfidence = 0.9f,
            intentSource = IntentSource.PREDICTED_SILENT,
            intentHistoryJson = "[]",
            state = StateSnapshot(
                appCategory = AppCategory.BROWSER,
                activityState = ActivityState.ACTIVE,
                tzId = "America/New_York",
                hourLocal = 14,
                dayOfWeekLocal = 3
            ),
            createdAt = System.currentTimeMillis(),
            dayLocal = "2025-01-15"
        )

        db.intentEnvelopeDao().insert(envelope)
        val loaded = db.intentEnvelopeDao().getById(id)
        assertNotNull(loaded)
        assertEquals("Hello Orbit", loaded!!.textContent)
        assertEquals(Intent.SAVE, loaded.intent)
    }

    @Test
    fun softDeleteAndRestoreWorks() = runTest {
        val id = UUID.randomUUID().toString()
        val envelope = IntentEnvelopeEntity(
            id = id,
            contentType = ContentType.TEXT,
            textContent = "To delete",
            imageUri = null,
            textContentSha256 = null,
            intent = Intent.SAVE,
            intentConfidence = null,
            intentSource = IntentSource.FALLBACK,
            intentHistoryJson = "[]",
            state = StateSnapshot(
                appCategory = AppCategory.OTHER,
                activityState = ActivityState.ACTIVE,
                tzId = "UTC",
                hourLocal = 10,
                dayOfWeekLocal = 1
            ),
            createdAt = System.currentTimeMillis(),
            dayLocal = "2025-01-15"
        )

        db.intentEnvelopeDao().insert(envelope)
        db.intentEnvelopeDao().softDelete(id, System.currentTimeMillis())

        val deleted = db.intentEnvelopeDao().getById(id)
        assertNotNull(deleted!!.deletedAt)

        db.intentEnvelopeDao().restoreFromTrash(id)
        val restored = db.intentEnvelopeDao().getById(id)
        assertNull(restored!!.deletedAt)
    }

    @Test
    fun observeDayExcludesDeletedAndArchived() = runTest {
        val day = "2025-01-16"
        val now = System.currentTimeMillis()

        fun makeEnvelope(suffix: String) = IntentEnvelopeEntity(
            id = UUID.randomUUID().toString(),
            contentType = ContentType.TEXT,
            textContent = "Envelope $suffix",
            imageUri = null,
            textContentSha256 = null,
            intent = Intent.SAVE,
            intentConfidence = null,
            intentSource = IntentSource.FALLBACK,
            intentHistoryJson = "[]",
            state = StateSnapshot(AppCategory.OTHER, ActivityState.ACTIVE, "UTC", 10, 1),
            createdAt = now,
            dayLocal = day
        )

        val e1 = makeEnvelope("visible")
        val e2 = makeEnvelope("archived").copy(isArchived = true)
        val e3 = makeEnvelope("deleted").copy(isDeleted = true, deletedAt = now)

        db.intentEnvelopeDao().insert(e1)
        db.intentEnvelopeDao().insert(e2)
        db.intentEnvelopeDao().insert(e3)

        val dayEnvelopes = db.intentEnvelopeDao().observeDay(day).first()
        assertEquals(1, dayEnvelopes.size)
        assertEquals(e1.id, dayEnvelopes[0].id)
    }

    @Test
    fun auditLogInsertAndQuery() = runTest {
        val entry = AuditLogEntryEntity(
            id = UUID.randomUUID().toString(),
            at = System.currentTimeMillis(),
            action = AuditAction.ENVELOPE_SEALED,
            description = "Sealed test envelope",
            envelopeId = "env-123",
            extraJson = null
        )

        db.auditLogDao().insert(entry)
        val results = db.auditLogDao().entriesForEnvelope("env-123")
        assertEquals(1, results.size)
        assertEquals(AuditAction.ENVELOPE_SEALED, results[0].action)
    }
}
