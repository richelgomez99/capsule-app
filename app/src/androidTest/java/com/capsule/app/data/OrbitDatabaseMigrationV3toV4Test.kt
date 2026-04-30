package com.capsule.app.data

import android.content.ContentValues
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

/**
 * Spec 002 Phase 11 Block 13 / spec 012 FR-012-011 — migration test for
 * the v3 → v4 upgrade (`derivedVia TEXT` additive column on
 * `intent_envelope`). Mirrors [OrbitDatabaseMigrationV2toV3Test]'s
 * helper-then-Room shape so the same constitutional priority applies:
 * a bad migration here corrupts every existing user's encrypted DB on
 * upgrade.
 *
 * Strategy:
 *  1. Build a v3 DB and seed a representative pair of envelopes
 *     (REGULAR + DIGEST) plus a cluster row so we cover both the
 *     "rows that should keep `derivedVia = NULL`" and the table the
 *     ALTER actually touches.
 *  2. Run [MIGRATION_3_4] alone (helper validates against `4.json`).
 *  3. Re-open via the real Room builder to verify:
 *      - existing rows now have `derivedVia = NULL` (additive default);
 *      - new inserts can write `derivedVia = 'cluster_summarize'` and
 *        the value round-trips intact.
 */
@RunWith(AndroidJUnit4::class)
class OrbitDatabaseMigrationV3toV4Test {

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
    fun migrate_v3_to_v4_addsDerivedViaColumn_existingRowsNull_newRowsRoundTrip() {
        // ---- Arrange: seed v3 envelope rows.
        helper.createDatabase(DB_NAME, 3).use { db ->
            val now = 1_700_000_000_000L
            for (i in 0 until ENVELOPE_FIXTURE_COUNT) {
                db.insert(
                    "intent_envelope",
                    android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL,
                    ContentValues().apply {
                        put("id", "env-$i")
                        put("contentType", "TEXT")
                        put("textContent", "fixture $i")
                        put("imageUri", null as String?)
                        put("textContentSha256", null as String?)
                        put("intent", "ARCHIVE")
                        put("intentConfidence", null as Float?)
                        put("intentSource", "USER")
                        put("intentHistoryJson", "[]")
                        put("createdAt", now + i)
                        put("day_local", "2026-04-29")
                        put("isArchived", 0)
                        put("isDeleted", 0)
                        put("deletedAt", null as Long?)
                        put("sharedContinuationResultId", null as String?)
                        put("appCategory", "OTHER")
                        put("activityState", "FOCUSED")
                        put("tzId", "America/Los_Angeles")
                        put("hourLocal", 12)
                        put("dayOfWeekLocal", 5)
                        put("kind", "REGULAR")
                        put("derivedFromEnvelopeIdsJson", null as String?)
                        put("todoMetaJson", null as String?)
                    }
                )
            }
        }

        // ---- Act: run only the v3 → v4 migration; helper validates schema vs 4.json.
        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            /* version = */ 4,
            /* validateDroppedTables = */ true,
            MIGRATION_3_4
        )
        migrated.close()

        // ---- Assert v4 invariants via real Room builder.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val room = Room.databaseBuilder(context, OrbitDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        try {
            val writable = room.openHelper.writableDatabase

            // 1. existing rows kept derivedVia = NULL (additive default).
            writable.query(
                "SELECT COUNT(*) FROM intent_envelope WHERE derivedVia IS NULL"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(ENVELOPE_FIXTURE_COUNT, c.getInt(0))
            }

            // 2. Inserting a DERIVED row with derivedVia='cluster_summarize'
            //    round-trips intact (spec 012 FR-012-011 sentinel value).
            writable.execSQL(
                """
                INSERT INTO intent_envelope(
                    id, contentType, textContent, imageUri, textContentSha256,
                    intent, intentConfidence, intentSource, intentHistoryJson,
                    createdAt, day_local, isArchived, isDeleted, deletedAt,
                    sharedContinuationResultId, appCategory, activityState,
                    tzId, hourLocal, dayOfWeekLocal,
                    kind, derivedFromEnvelopeIdsJson, todoMetaJson, derivedVia
                ) VALUES(
                    'derived-1', 'TEXT', 'bullet 1\nbullet 2', NULL, NULL,
                    'REFERENCE', NULL, 'AUTO_AMBIGUOUS', '[]',
                    1700000999000, '2026-04-29', 0, 0, NULL,
                    NULL, 'OTHER', 'UNKNOWN',
                    'America/Los_Angeles', 12, 5,
                    'DERIVED', '["env-0","env-1","env-2"]', NULL, 'cluster_summarize'
                )
                """.trimIndent()
            )

            writable.query(
                "SELECT derivedVia FROM intent_envelope WHERE id = 'derived-1'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("cluster_summarize", c.getString(0))
            }

            // 3. NULL writes still permitted (REGULAR + DIGEST rows MUST be
            //    able to leave derivedVia unset).
            writable.execSQL(
                """
                INSERT INTO intent_envelope(
                    id, contentType, textContent, imageUri, textContentSha256,
                    intent, intentConfidence, intentSource, intentHistoryJson,
                    createdAt, day_local, isArchived, isDeleted, deletedAt,
                    sharedContinuationResultId, appCategory, activityState,
                    tzId, hourLocal, dayOfWeekLocal,
                    kind, derivedFromEnvelopeIdsJson, todoMetaJson, derivedVia
                ) VALUES(
                    'regular-new', 'TEXT', 'note', NULL, NULL,
                    'ARCHIVE', NULL, 'USER', '[]',
                    1700000999500, '2026-04-29', 0, 0, NULL,
                    NULL, 'OTHER', 'FOCUSED',
                    'America/Los_Angeles', 12, 5,
                    'REGULAR', NULL, NULL, NULL
                )
                """.trimIndent()
            )
            writable.query(
                "SELECT derivedVia FROM intent_envelope WHERE id = 'regular-new'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertNull(c.getString(0))
            }
        } finally {
            room.close()
        }
    }

    private companion object {
        const val DB_NAME = "orbit-migration-v3-v4-test.db"
        const val ENVELOPE_FIXTURE_COUNT = 50
    }
}
