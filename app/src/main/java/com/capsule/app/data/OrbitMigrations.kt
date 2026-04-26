package com.capsule.app.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room schema migrations for `OrbitDatabase`.
 *
 * v1 (002 baseline):
 *   `intent_envelope`, `continuation`, `continuation_result`, `audit_log`.
 *
 * v2 (003 v1.1 — Orbit Actions):
 *   - extends `intent_envelope` with `kind`, `derivedFromEnvelopeIdsJson`, `todoMetaJson`
 *   - adds `action_proposal`, `action_execution`, `appfunction_skill`, `skill_usage`
 *   - adds the partial unique index `index_digest_unique_per_day` to enforce
 *     "exactly one DIGEST per Sunday" idempotency for `WeeklyDigestWorker`
 *
 * The migration is purely additive: every 002 query continues to return the
 * same shape because the new columns have NULL or string defaults.
 *
 * Verified by `OrbitDatabaseMigrationV1toV2Test` (T029) on a 1000-envelope
 * fixture and on-device by T111. See `specs/003-orbit-actions/data-model.md` §7.
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Extend intent_envelope. The kind column gets a NOT NULL DEFAULT so
        //    existing rows back-fill to REGULAR. derivedFromEnvelopeIdsJson and
        //    todoMetaJson are nullable — only DIGEST/DERIVED rows or todo-derived
        //    envelopes set them.
        db.execSQL("ALTER TABLE intent_envelope ADD COLUMN kind TEXT NOT NULL DEFAULT 'REGULAR'")
        db.execSQL("ALTER TABLE intent_envelope ADD COLUMN derivedFromEnvelopeIdsJson TEXT")
        db.execSQL("ALTER TABLE intent_envelope ADD COLUMN todoMetaJson TEXT")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_intent_envelope_kind_day_local ON intent_envelope(kind, day_local)")

        // 2. action_proposal — extracted candidates per envelope.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS action_proposal (
                id TEXT PRIMARY KEY NOT NULL,
                envelopeId TEXT NOT NULL,
                functionId TEXT NOT NULL,
                schemaVersion INTEGER NOT NULL,
                argsJson TEXT NOT NULL,
                previewTitle TEXT NOT NULL,
                previewSubtitle TEXT,
                confidence REAL NOT NULL,
                provenance TEXT NOT NULL,
                state TEXT NOT NULL,
                sensitivityScope TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                stateChangedAt INTEGER NOT NULL,
                FOREIGN KEY(envelopeId) REFERENCES intent_envelope(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_action_proposal_envelopeId ON action_proposal(envelopeId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_action_proposal_state ON action_proposal(state)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_action_proposal_createdAt ON action_proposal(createdAt)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_action_proposal_envelopeId_functionId ON action_proposal(envelopeId, functionId)")

        // 3. action_execution — one row per Confirm tap. Cascade-deletes with
        //    its proposal, which itself cascades from the source envelope.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS action_execution (
                id TEXT PRIMARY KEY NOT NULL,
                proposalId TEXT NOT NULL,
                functionId TEXT NOT NULL,
                outcome TEXT NOT NULL,
                outcomeReason TEXT,
                dispatchedAt INTEGER NOT NULL,
                completedAt INTEGER,
                latencyMs INTEGER,
                episodeId TEXT,
                FOREIGN KEY(proposalId) REFERENCES action_proposal(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_action_execution_proposalId ON action_execution(proposalId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_action_execution_outcome ON action_execution(outcome)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_action_execution_dispatchedAt ON action_execution(dispatchedAt)")

        // 4. appfunction_skill — registry of agent-callable functions. PK is
        //    `functionId` (soft-supersede via REPLACE on schema bumps); the
        //    composite unique index documents the schemaVersion semantics.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS appfunction_skill (
                functionId TEXT PRIMARY KEY NOT NULL,
                appPackage TEXT NOT NULL,
                displayName TEXT NOT NULL,
                description TEXT NOT NULL,
                schemaVersion INTEGER NOT NULL,
                argsSchemaJson TEXT NOT NULL,
                sideEffects TEXT NOT NULL,
                reversibility TEXT NOT NULL,
                sensitivityScope TEXT NOT NULL,
                registeredAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_appfunction_skill_functionId_schemaVersion ON appfunction_skill(functionId, schemaVersion)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_appfunction_skill_appPackage ON appfunction_skill(appPackage)")

        // 5. skill_usage — per-execution row consumed by the Settings stats UI
        //    and (forward) the v1.2 agent's planner heuristics.
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS skill_usage (
                id TEXT PRIMARY KEY NOT NULL,
                skillId TEXT NOT NULL,
                executionId TEXT NOT NULL,
                proposalId TEXT NOT NULL,
                episodeId TEXT,
                outcome TEXT NOT NULL,
                latencyMs INTEGER NOT NULL,
                invokedAt INTEGER NOT NULL,
                FOREIGN KEY(skillId) REFERENCES appfunction_skill(functionId),
                FOREIGN KEY(executionId) REFERENCES action_execution(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_skill_usage_skillId ON skill_usage(skillId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_skill_usage_executionId ON skill_usage(executionId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_skill_usage_invokedAt ON skill_usage(invokedAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_skill_usage_outcome ON skill_usage(outcome)")

        // 6. Idempotency: at most one DIGEST envelope per local day. Two
        //    concurrent WeeklyDigestWorker runs (e.g., job-cancel + retry)
        //    will see one INSERT succeed and the other observe a UNIQUE
        //    constraint failure, which the worker maps to DIGEST_SKIPPED
        //    (T074 / weekly-digest-contract.md §3).
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_digest_unique_per_day " +
                "ON intent_envelope(day_local) WHERE kind = 'DIGEST'"
        )
    }
}
