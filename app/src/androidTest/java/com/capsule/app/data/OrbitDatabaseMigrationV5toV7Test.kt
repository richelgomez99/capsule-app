package com.capsule.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class OrbitDatabaseMigrationV5toV7Test {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        OrbitDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @After
    fun tearDown() {
        runCatching {
            ApplicationProvider.getApplicationContext<android.content.Context>()
                .deleteDatabase(DB_NAME)
        }
    }

    @Test
    @Throws(IOException::class)
    fun migrate_v5_to_v7_addsActiveDuplicateKeysAndNotes() {
        helper.createDatabase(DB_NAME, 5).use { db ->
            insertEnvelopeV5(db, id = "url-active", text = "https://example.com/a", createdAt = 1_000L)
            insertContinuationV5(db, id = "cont-url", envelopeId = "url-active", inputUrl = "https://example.com/a")
            insertContinuationResultV5(
                db = db,
                id = "result-url",
                continuationId = "cont-url",
                envelopeId = "url-active",
                canonicalUrlHash = "url-hash"
            )

            insertEnvelopeV5(
                db = db,
                id = "text-old",
                text = "same text",
                textContentSha256 = "text-same",
                createdAt = 2_000L
            )
            insertEnvelopeV5(
                db = db,
                id = "text-new",
                text = "same text",
                textContentSha256 = "text-same",
                createdAt = 3_000L
            )
            insertEnvelopeV5(
                db = db,
                id = "archived-text",
                text = "archived text",
                textContentSha256 = "archived-hash",
                createdAt = 4_000L,
                isArchived = true
            )
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            /* version = */ 7,
            /* validateDroppedTables = */ true,
            MIGRATION_5_6,
            MIGRATION_6_7
        )
        migrated.close()

        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val room = Room.databaseBuilder(context, OrbitDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7)
            .allowMainThreadQueries()
            .build()
        try {
            val writable = room.openHelper.writableDatabase

            assertString(writable, "url-hash", "SELECT activePrimaryCanonicalUrlHash FROM intent_envelope WHERE id = 'url-active'")
            assertString(writable, "text-same", "SELECT activeTextContentSha256 FROM intent_envelope WHERE id = 'text-old'")
            assertString(writable, null, "SELECT activeTextContentSha256 FROM intent_envelope WHERE id = 'text-new'")
            assertString(writable, null, "SELECT activeTextContentSha256 FROM intent_envelope WHERE id = 'archived-text'")

            assertIndex(writable, "index_intent_envelope_activePrimaryCanonicalUrlHash", unique = true)
            assertIndex(writable, "index_intent_envelope_activeTextContentSha256", unique = true)

            try {
                writable.execSQL(
                    "UPDATE intent_envelope SET activeTextContentSha256 = 'text-same' WHERE id = 'text-new'"
                )
                fail("Expected active text duplicate key to reject a second visible row")
            } catch (_: SQLiteConstraintException) {
                // expected
            }

            writable.execSQL(
                """
                INSERT INTO envelope_note(id, envelopeId, text, createdAt, updatedAt)
                VALUES('note-1', 'url-active', 'remember this', 5000, 5000)
                """.trimIndent()
            )
            assertInt(writable, 1, "SELECT COUNT(*) FROM envelope_note WHERE envelopeId = 'url-active'")
        } finally {
            room.close()
        }
    }

    private fun insertEnvelopeV5(
        db: SupportSQLiteDatabase,
        id: String,
        text: String,
        textContentSha256: String? = null,
        createdAt: Long,
        isArchived: Boolean = false
    ) {
        db.insert(
            "intent_envelope",
            android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL,
            ContentValues().apply {
                put("id", id)
                put("contentType", "TEXT")
                put("textContent", text)
                put("imageUri", null as String?)
                put("textContentSha256", textContentSha256)
                put("intent", "AMBIGUOUS")
                put("intentConfidence", null as Float?)
                put("intentSource", "FALLBACK")
                put("intentHistoryJson", "[]")
                put("createdAt", createdAt)
                put("day_local", "2026-05-13")
                put("isArchived", if (isArchived) 1 else 0)
                put("isDeleted", 0)
                put("deletedAt", null as Long?)
                put("sharedContinuationResultId", null as String?)
                put("kind", "REGULAR")
                put("derivedFromEnvelopeIdsJson", null as String?)
                put("todoMetaJson", null as String?)
                put("derivedVia", null as String?)
                put("appCategory", "OTHER")
                put("activityState", "STILL")
                put("tzId", "UTC")
                put("hourLocal", 10)
                put("dayOfWeekLocal", 3)
                put("sourceAppLabel", null as String?)
            }
        )
    }

    private fun insertContinuationV5(
        db: SupportSQLiteDatabase,
        id: String,
        envelopeId: String,
        inputUrl: String
    ) {
        db.insert(
            "continuation",
            android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL,
            ContentValues().apply {
                put("id", id)
                put("envelopeId", envelopeId)
                put("type", "URL_HYDRATE")
                put("status", "SUCCEEDED")
                put("inputUrl", inputUrl)
                put("scheduledAt", 1_000L)
                put("startedAt", 1_000L)
                put("completedAt", 1_000L)
                put("attemptCount", 1)
                put("failureReason", null as String?)
            }
        )
    }

    private fun insertContinuationResultV5(
        db: SupportSQLiteDatabase,
        id: String,
        continuationId: String,
        envelopeId: String,
        canonicalUrlHash: String
    ) {
        db.insert(
            "continuation_result",
            android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL,
            ContentValues().apply {
                put("id", id)
                put("continuationId", continuationId)
                put("envelopeId", envelopeId)
                put("producedAt", 1_100L)
                put("title", "Title")
                put("domain", "example.com")
                put("canonicalUrl", "https://example.com/a")
                put("canonicalUrlHash", canonicalUrlHash)
                put("excerpt", null as String?)
                put("summary", "Summary")
                put("summaryModel", "nano-v1")
            }
        )
    }

    private fun assertString(db: SupportSQLiteDatabase, expected: String?, sql: String) {
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getString(0))
        }
    }

    private fun assertInt(db: SupportSQLiteDatabase, expected: Int, sql: String) {
        db.query(sql).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(expected, cursor.getInt(0))
        }
    }

    private fun assertIndex(db: SupportSQLiteDatabase, name: String, unique: Boolean) {
        db.query("PRAGMA index_list('intent_envelope')").use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow("name")
            val uniqueIndex = cursor.getColumnIndexOrThrow("unique")
            while (cursor.moveToNext()) {
                if (cursor.getString(nameIndex) == name) {
                    assertEquals(if (unique) 1 else 0, cursor.getInt(uniqueIndex))
                    return
                }
            }
        }
        fail("Expected index $name")
    }

    private companion object {
        const val DB_NAME = "orbit-migration-v5-v7-test.db"
    }
}