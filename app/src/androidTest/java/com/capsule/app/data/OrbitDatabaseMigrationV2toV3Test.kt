package com.capsule.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
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

/**
 * Migration test for the Phase 11 v2 → v3 upgrade (cluster engine).
 *
 * **Naming vs path**: file is named `MigrationV2toV3` to track the
 * Phase-11 amendment. The test in fact exercises the **chained v1 → v3
 * upgrade** (`MIGRATION_1_2` then `MIGRATION_2_3`). Reasons:
 *
 *   1. `2.json` was never committed — Block 1 bumped the schema to v3
 *      before any KSP export of v2 was preserved, so
 *      `helper.createDatabase(name, 2)` cannot resolve the v2 schema.
 *   2. Real users on existing 002 builds will run BOTH migrations on
 *      app upgrade in a single open() call, so v1 → v3 is the production
 *      path; isolating v2 → v3 would only cover a hypothetical
 *      v3-fresh-install path that doesn't run the migration at all.
 *   3. The chained run still gates the v3-specific invariants — the
 *      cluster/cluster_member tables, FK CASCADE on both parents, and
 *      the four new indexes — because they're added by `MIGRATION_2_3`
 *      and survive the `validateDroppedTables = true` validation
 *      against `3.json`.
 *
 * Constitutional priority: a bad Room migration corrupts every existing
 * user's encrypted DB on upgrade with no recovery path. This is the
 * single highest-risk file in Phase 11.
 *
 * Strategy:
 *   1. Build a v1 DB through `MigrationTestHelper.createDatabase` —
 *      this materializes the 002 baseline schema from `1.json`.
 *   2. Seed a representative fixture: 100 envelopes + 1 audit row.
 *      (We reduced from the v1 → v2 test's 1000 to 100 to keep CI runs
 *      fast; the migration is purely additive DDL so volume sensitivity
 *      is bounded by `ALTER TABLE ADD COLUMN`'s O(n) scan in MIGRATION_1_2.)
 *   3. Run **both** migrations and let the helper validate against
 *      `3.json`.
 *   4. Re-open the DB through the real Room builder and assert
 *      v3-specific semantic invariants the schema validator can't see:
 *        - `cluster` and `cluster_member` accept inserts with the
 *          documented column shape;
 *        - the composite PK on `cluster_member` rejects duplicates;
 *        - FK CASCADE on `cluster.id` removes child rows when a
 *          cluster is hard-deleted;
 *        - FK CASCADE on `intent_envelope.id` removes member rows
 *          when an envelope is hard-deleted (FR-038 surviving-count
 *          rule depends on this);
 *        - existing v1 envelopes survive the chained migration with
 *          v2-default `kind = 'REGULAR'` AND v3 leaves them untouched.
 *
 * Note: Room migration testing uses an unencrypted SQLite helper
 * (`FrameworkSQLiteOpenHelperFactory`). SQLCipher engagement is verified
 * separately by `OrbitDatabaseTest`. Migration DDL is identical because
 * SQLCipher is transparent to `execSQL`.
 */
@RunWith(AndroidJUnit4::class)
class OrbitDatabaseMigrationV2toV3Test {

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
    fun migrate_v1_to_v3_chainedAddsClusterTablesAndPreservesEnvelopes() {
        // ---- Arrange: seed v1 schema with envelope + audit fixtures.
        helper.createDatabase(DB_NAME, 1).use { db ->
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
                        put("day_local", "2024-11-15")
                        put("isArchived", 0)
                        put("isDeleted", 0)
                        put("deletedAt", null as Long?)
                        put("sharedContinuationResultId", null as String?)
                        put("appCategory", "OTHER")
                        put("activityState", "FOCUSED")
                        put("tzId", "America/Los_Angeles")
                        put("hourLocal", 12)
                        put("dayOfWeekLocal", 5)
                    }
                )
            }
            db.insert(
                "audit_log",
                android.database.sqlite.SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("id", "audit-1")
                    put("at", now)
                    put("action", "ENVELOPE_CREATED")
                    put("description", "fixture")
                    put("envelopeId", "env-0")
                    put("extraJson", null as String?)
                    put("llmProvider", null as String?)
                    put("llmModel", null as String?)
                    put("promptDigestSha256", null as String?)
                    put("tokenCount", null as Long?)
                }
            )
        }

        // ---- Act: chained v1 -> v2 -> v3, helper auto-validates final schema vs 3.json.
        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            /* version = */ 3,
            /* validateDroppedTables = */ true,
            MIGRATION_1_2,
            MIGRATION_2_3
        )
        migrated.close()

        // ---- Assert v3 invariants via real Room builder.
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val room = Room.databaseBuilder(context, OrbitDatabase::class.java, DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .allowMainThreadQueries()
            .build()
        try {
            val writable = room.openHelper.writableDatabase

            // 1. v1 envelopes survived chained migration; v2 back-fill kept them as REGULAR.
            writable.query(
                "SELECT COUNT(*) FROM intent_envelope WHERE kind = 'REGULAR'"
            ).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(ENVELOPE_FIXTURE_COUNT, c.getInt(0))
            }

            // 2. cluster + cluster_member tables exist and accept inserts.
            writable.execSQL(
                """
                INSERT INTO cluster(
                    id, cluster_type, state, timeBucketStart, timeBucketEnd,
                    similarityScore, model_label, createdAt, stateChangedAt, dismissedAt
                ) VALUES(
                    'c1', 'RESEARCH_SESSION', 'FORMING', 1000, 2000,
                    0.85, 'nano-v4-build-2026-05-01', 1000, 1000, NULL
                )
                """.trimIndent()
            )
            writable.execSQL(
                """
                INSERT INTO cluster_member(clusterId, envelopeId, memberIndex)
                VALUES('c1', 'env-0', 0)
                """.trimIndent()
            )
            writable.execSQL(
                """
                INSERT INTO cluster_member(clusterId, envelopeId, memberIndex)
                VALUES('c1', 'env-1', 1)
                """.trimIndent()
            )
            writable.execSQL(
                """
                INSERT INTO cluster_member(clusterId, envelopeId, memberIndex)
                VALUES('c1', 'env-2', 2)
                """.trimIndent()
            )

            writable.query("SELECT COUNT(*) FROM cluster").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(1, c.getInt(0))
            }
            writable.query("SELECT COUNT(*) FROM cluster_member WHERE clusterId = 'c1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(3, c.getInt(0))
            }

            // 3. Composite PK on cluster_member rejects duplicate (clusterId, envelopeId).
            try {
                writable.execSQL(
                    """
                    INSERT INTO cluster_member(clusterId, envelopeId, memberIndex)
                    VALUES('c1', 'env-0', 99)
                    """.trimIndent()
                )
                fail("Expected SQLiteConstraintException on duplicate composite PK")
            } catch (_: SQLiteConstraintException) {
                // expected
            }

            // 4. FK CASCADE on intent_envelope: hard-delete envelope removes members.
            //    Critical for FR-038 (surviving-count rule). We use raw SQL so the
            //    delete bypasses Room's soft-delete column.
            //    NB: SQLite requires `PRAGMA foreign_keys = ON` per connection; Room
            //    enables it automatically on `RoomDatabase` connections, so the
            //    `writable` handle here already has FKs on.
            writable.execSQL("DELETE FROM intent_envelope WHERE id = 'env-0'")
            writable.query("SELECT COUNT(*) FROM cluster_member WHERE envelopeId = 'env-0'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(
                    "FK CASCADE on intent_envelope.id must remove cluster_member rows",
                    0, c.getInt(0)
                )
            }
            // Cluster row is unaffected; surviving-count rule lives in app code (Block 9).
            writable.query("SELECT COUNT(*) FROM cluster_member WHERE clusterId = 'c1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(2, c.getInt(0))
            }

            // 5. FK CASCADE on cluster: hard-delete cluster removes its members.
            writable.execSQL("DELETE FROM cluster WHERE id = 'c1'")
            writable.query("SELECT COUNT(*) FROM cluster_member WHERE clusterId = 'c1'").use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(
                    "FK CASCADE on cluster.id must remove cluster_member rows",
                    0, c.getInt(0)
                )
            }

            // 6. v3-specific indexes are present (sqlite_master).
            val v3Indexes = mutableSetOf<String>()
            writable.query("SELECT name FROM sqlite_master WHERE type = 'index'").use { c ->
                while (c.moveToNext()) v3Indexes.add(c.getString(0))
            }
            assertTrue(
                "missing idx_cluster_state — got $v3Indexes",
                v3Indexes.contains("idx_cluster_state")
            )
            assertTrue(
                "missing idx_cluster_time_bucket — got $v3Indexes",
                v3Indexes.contains("idx_cluster_time_bucket")
            )
            assertTrue(
                "missing index_cluster_member_clusterId — got $v3Indexes",
                v3Indexes.contains("index_cluster_member_clusterId")
            )
            assertTrue(
                "missing idx_cluster_member_envelope — got $v3Indexes",
                v3Indexes.contains("idx_cluster_member_envelope")
            )
        } finally {
            room.close()
        }
    }

    private companion object {
        const val DB_NAME = "orbit-migration-v2-v3-test.db"
        const val ENVELOPE_FIXTURE_COUNT = 100
    }
}
