# Data Model: Orbit Actions (Phase 1)

**Feature Branch**: `003-orbit-actions`
**Date**: 2026-04-26
**Database**: `orbit.db` (inherited from 002), Room 2.6+ with SQLCipher via `SupportFactory`. Schema bumped from v1 → v2 with additive changes only.

---

## Overview

Spec 003 adds four new tables, one new column on the existing
`intent_envelope` table, and extends two existing enums. All
changes are additive and reversible — no row in any 002 table is
mutated by the v1 → v2 migration except the new `kind` column on
`intent_envelope` (which receives a default of `'REGULAR'`).

The audit log table from 002 (`audit_log`) is reused without
schema changes; only the `AuditAction` enum gains seven new
values.

The DB still opens only in `:ml`. The new `action/` package in
`:capture` does not touch Room — it reads/writes via the existing
`:ml` binder service (`IEnvelopeRepository` extended with new
methods; see `contracts/action-execution-contract.md`).

---

## Enum Extensions

### `ContinuationType` (extended)

```kotlin
enum class ContinuationType {
    URL_HYDRATE,        // 002 v1
    // 003 v1.1 additions:
    ACTION_EXTRACT,     // run on charger+wifi, produces 0..N action_proposal rows
    ACTION_EXECUTE,     // user-initiated, no scheduling constraints, dispatches Intent
    WEEKLY_DIGEST       // periodic Sunday 06:00 local, produces 0..1 DIGEST envelope
}
```

### `AuditAction` (extended)

```kotlin
enum class AuditAction {
    // ... all 002 values preserved ...

    // 003 v1.1 additions:
    ACTION_PROPOSED,            // ACTION_EXTRACT continuation produced a proposal
    ACTION_DISMISSED,           // user swiped/declined a proposal in Diary
    ACTION_CONFIRMED,           // user tapped Confirm on preview card
    ACTION_EXECUTED,            // ACTION_EXECUTE worker dispatched the Intent successfully
    ACTION_FAILED,              // dispatch failed (no app, intent rejected, etc.)
    APPFUNCTION_REGISTERED,     // schema row inserted/updated in appfunction_skill
    DIGEST_GENERATED,           // WeeklyDigestWorker produced a DIGEST envelope
    DIGEST_SKIPPED              // worker ran but no digest produced (sparse window, etc.)
}
```

### `EnvelopeKind` (new)

```kotlin
enum class EnvelopeKind {
    REGULAR,    // default; every 002 envelope is REGULAR after migration
    DIGEST,     // weekly digest envelope produced by WeeklyDigestWorker
    DERIVED     // forward-compat reservation for spec 012 derived artefacts
                // (agent summaries, structured lists). NOT used in v1.1.
}
```

`DERIVED` is reserved but not produced by 003. Including it in v1.1
prevents a future enum migration when spec 012 / 008 lands.

---

## 1. IntentEnvelopeEntity (002 — modified)

**Change**: one new column.

```kotlin
@Entity(
    tableName = "intent_envelope",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["intent"]),
        Index(value = ["day_local"]),
        Index(value = ["kind", "day_local"], unique = false)   // NEW
    ]
)
data class IntentEnvelopeEntity(
    // ... all 002 columns preserved verbatim ...
    val kind: EnvelopeKind = EnvelopeKind.REGULAR,             // NEW
    val derivedFromEnvelopeIdsJson: String? = null             // NEW (DIGEST / DERIVED only)
)
```

**Migration**:

```sql
ALTER TABLE intent_envelope ADD COLUMN kind TEXT NOT NULL DEFAULT 'REGULAR';
ALTER TABLE intent_envelope ADD COLUMN derivedFromEnvelopeIdsJson TEXT;
CREATE INDEX index_intent_envelope_kind_day_local ON intent_envelope(kind, day_local);
```

`derivedFromEnvelopeIdsJson` is a JSON array of envelope IDs that
the DIGEST envelope summarises. Required for Principle XII
provenance: deleting any source envelope triggers a re-evaluation
of the DIGEST (kept if at least one source survives, otherwise
soft-deleted with `INVALIDATED` audit row).

---

## 2. ActionProposalEntity (new)

**Location**: `app/src/main/java/com/capsule/app/data/entities/ActionProposalEntity.kt`

```kotlin
@Entity(
    tableName = "action_proposal",
    foreignKeys = [
        ForeignKey(
            entity = IntentEnvelopeEntity::class,
            parentColumns = ["id"],
            childColumns = ["envelopeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["envelopeId"]),
        Index(value = ["state"]),
        Index(value = ["createdAt"]),
        Index(value = ["envelopeId", "functionId"], unique = true)  // dedupe
    ]
)
data class ActionProposalEntity(
    @PrimaryKey val id: String,                  // UUIDv4
    val envelopeId: String,                      // FK → intent_envelope.id
    val functionId: String,                      // FK → appfunction_skill.functionId
    val argsJson: String,                        // JSON args validated against schema
    val previewTitle: String,                    // human-readable summary for chip
    val previewSubtitle: String?,                // optional second line
    val confidence: Float,                       // 0.0..1.0 from extractor
    val provenance: String,                      // 'LocalNano' | 'OrbitManaged' | 'Byok'
    val state: String,                           // PROPOSED | DISMISSED | CONFIRMED | INVALIDATED
    val sensitivityScope: String,                // PUBLIC | PERSONAL | SHARE_DELEGATED
    val createdAt: Long,                         // epoch millis
    val stateChangedAt: Long                     // last state transition
)
```

| Column | Notes |
|---|---|
| `id` | UUIDv4 generated at extraction time. |
| `envelopeId` | Cascade-delete: removing the source envelope removes proposals. |
| `functionId` | Must exist in `appfunction_skill`; enforced at write time (not via SQL FK because schema versions allow soft-supersede). |
| `argsJson` | Validated against the schema's argument shape before write; rejects on mismatch. |
| `confidence` | Below 0.55 floor → not persisted (extractor-side gate). |
| `state` | State machine: PROPOSED → (DISMISSED \| CONFIRMED). CONFIRMED → INVALIDATED on source-envelope cascade or user-cancel within 5s undo. |
| `sensitivityScope` | Copied from the schema's declared scope at write time. |

**Unique constraint** `(envelopeId, functionId)` prevents the same
extraction worker run from producing duplicate proposals. Re-runs
no-op via `OnConflictStrategy.IGNORE`.

---

## 3. ActionExecutionEntity (new)

**Location**: `app/src/main/java/com/capsule/app/data/entities/ActionExecutionEntity.kt`

```kotlin
@Entity(
    tableName = "action_execution",
    foreignKeys = [
        ForeignKey(
            entity = ActionProposalEntity::class,
            parentColumns = ["id"],
            childColumns = ["proposalId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["proposalId"]),
        Index(value = ["outcome"]),
        Index(value = ["dispatchedAt"])
    ]
)
data class ActionExecutionEntity(
    @PrimaryKey val id: String,                  // UUIDv4
    val proposalId: String,                      // FK → action_proposal.id
    val functionId: String,                      // denormalised for fast queries
    val outcome: String,                         // PENDING | DISPATCHED | SUCCESS | FAILED | USER_CANCELLED
    val outcomeReason: String?,                  // free-text on FAILED
    val dispatchedAt: Long,                      // when Intent fired
    val completedAt: Long?,                      // when outcome resolved
    val latencyMs: Long?,                        // completedAt - dispatchedAt
    val episodeId: String?                       // FK → episodes (spec 006); null in v1.1 standalone
)
```

`outcome = DISPATCHED` is the v1.1 terminal state for external
intents (we cannot read back system Calendar / share completion
without permissions we don't request — see research §4). The
USER_CANCELLED state is reachable only within the 5s in-app undo
window.

`episodeId` is null in v1.1 but the column exists for forward
compat with spec 006 — when 006 ships, the
`source_kind='agent_action'` episode is written and its id
backfilled.

---

## 4. AppFunctionSkillEntity (new)

**Location**: `app/src/main/java/com/capsule/app/data/entities/AppFunctionSkillEntity.kt`

Schema **mirrors spec 006's `skills` table verbatim** so v1.1+
sync is a row-level mirror with no transform.

```kotlin
@Entity(
    tableName = "appfunction_skill",
    indices = [
        Index(value = ["functionId", "schemaVersion"], unique = true),
        Index(value = ["appPackage"])
    ]
)
data class AppFunctionSkillEntity(
    @PrimaryKey val functionId: String,          // e.g., "com.capsule.app.action.calendar_insert"
    val appPackage: String,                      // "com.capsule.app" for v1.1
    val displayName: String,                     // "Add to calendar"
    val description: String,                     // shown in Settings → Actions
    val schemaVersion: Int,                      // bumps on schema change
    val argsSchemaJson: String,                  // JSON Schema for args validation
    val sideEffects: String,                     // EXTERNAL_INTENT | LOCAL_DB_WRITE
    val reversibility: String,                   // REVERSIBLE_24H | EXTERNAL_MANAGED | NONE
    val sensitivityScope: String,                // PUBLIC | PERSONAL | SHARE_DELEGATED
    val registeredAt: Long,                      // first registration time
    val updatedAt: Long                          // last schema bump
)
```

**Initial v1.1 rows** (seeded at app first run):

| functionId | displayName | reversibility | sensitivityScope |
|---|---|---|---|
| `com.capsule.app.action.calendar_insert` | Add to calendar | EXTERNAL_MANAGED | PUBLIC |
| `com.capsule.app.action.todo_add` | Add to to-dos | REVERSIBLE_24H | PERSONAL |
| `com.capsule.app.action.share` | Share | EXTERNAL_MANAGED | SHARE_DELEGATED |

---

## 5. SkillUsageEntity (new)

**Location**: `app/src/main/java/com/capsule/app/data/entities/SkillUsageEntity.kt`

Schema **mirrors spec 006's `skill_usage` table verbatim**.

```kotlin
@Entity(
    tableName = "skill_usage",
    foreignKeys = [
        ForeignKey(
            entity = AppFunctionSkillEntity::class,
            parentColumns = ["functionId"],
            childColumns = ["skillId"]
        ),
        ForeignKey(
            entity = ActionExecutionEntity::class,
            parentColumns = ["id"],
            childColumns = ["executionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["skillId"]),
        Index(value = ["invokedAt"]),
        Index(value = ["outcome"])
    ]
)
data class SkillUsageEntity(
    @PrimaryKey val id: String,                  // UUIDv4
    val skillId: String,                         // FK → appfunction_skill.functionId
    val executionId: String,                     // FK → action_execution.id
    val proposalId: String,                      // denormalised
    val episodeId: String?,                      // forward-compat for spec 006
    val outcome: String,                         // SUCCESS | FAILED | USER_CANCELLED
    val latencyMs: Long,
    val invokedAt: Long
)
```

A row is written exactly once per `ActionExecution` after outcome
resolution. The DAO exposes aggregations:

```kotlin
@Query("""
    SELECT
        skillId,
        SUM(CASE WHEN outcome = 'SUCCESS' THEN 1 ELSE 0 END) * 1.0 / COUNT(*) AS successRate,
        SUM(CASE WHEN outcome = 'USER_CANCELLED' THEN 1 ELSE 0 END) * 1.0 / COUNT(*) AS cancelRate,
        AVG(latencyMs) AS avgLatencyMs,
        COUNT(*) AS invocationCount
    FROM skill_usage
    WHERE skillId = :skillId AND invokedAt >= :sinceMillis
    GROUP BY skillId
""")
suspend fun aggregate(skillId: String, sinceMillis: Long): SkillStats?
```

---

## 6. Audit Log (002 — reused)

No schema change. Seven new `AuditAction` values flow through the
same write API. Every action transition writes within the same Room
transaction as the data mutation:

| Mutation | Audit row written atomically |
|---|---|
| `ActionProposalDao.insert(proposal)` | `ACTION_PROPOSED` with `envelopeId`, `functionId`, `confidence` in extra |
| `ActionProposalDao.markDismissed(id)` | `ACTION_DISMISSED` |
| `ActionProposalDao.markConfirmed(id)` | `ACTION_CONFIRMED` |
| `ActionExecutionDao.insertDispatched(...)` | `ACTION_EXECUTED` with `latencyMs`, `outcome` in extra |
| `ActionExecutionDao.markFailed(id, reason)` | `ACTION_FAILED` with `reason` in extra |
| `AppFunctionSkillDao.upsert(...)` | `APPFUNCTION_REGISTERED` with `functionId`, `schemaVersion` in extra |
| `IntentEnvelopeDao.insertDigest(...)` | `DIGEST_GENERATED` with `weekId`, `envelopeCount` in extra |
| `WeeklyDigestWorker.skip(reason)` | `DIGEST_SKIPPED` with `reason` in extra |

Audit retention remains 90 days (002 contract). Action-related
rows aggregate cleanly into "What Orbit did today" with
copy-templates per action.

---

## 7. Migration v1 → v2

```kotlin
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Extend intent_envelope
        db.execSQL("ALTER TABLE intent_envelope ADD COLUMN kind TEXT NOT NULL DEFAULT 'REGULAR'")
        db.execSQL("ALTER TABLE intent_envelope ADD COLUMN derivedFromEnvelopeIdsJson TEXT")
        db.execSQL("CREATE INDEX index_intent_envelope_kind_day_local ON intent_envelope(kind, day_local)")

        // Create new tables
        db.execSQL("""
            CREATE TABLE action_proposal (
                id TEXT PRIMARY KEY NOT NULL,
                envelopeId TEXT NOT NULL,
                functionId TEXT NOT NULL,
                argsJson TEXT NOT NULL,
                previewTitle TEXT NOT NULL,
                previewSubtitle TEXT,
                confidence REAL NOT NULL,
                provenance TEXT NOT NULL,
                state TEXT NOT NULL,
                sensitivityScope TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                stateChangedAt INTEGER NOT NULL,
                FOREIGN KEY (envelopeId) REFERENCES intent_envelope(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_action_proposal_envelopeId ON action_proposal(envelopeId)")
        db.execSQL("CREATE INDEX index_action_proposal_state ON action_proposal(state)")
        db.execSQL("CREATE INDEX index_action_proposal_createdAt ON action_proposal(createdAt)")
        db.execSQL("CREATE UNIQUE INDEX index_action_proposal_envelopeId_functionId ON action_proposal(envelopeId, functionId)")

        db.execSQL("""
            CREATE TABLE action_execution (
                id TEXT PRIMARY KEY NOT NULL,
                proposalId TEXT NOT NULL,
                functionId TEXT NOT NULL,
                outcome TEXT NOT NULL,
                outcomeReason TEXT,
                dispatchedAt INTEGER NOT NULL,
                completedAt INTEGER,
                latencyMs INTEGER,
                episodeId TEXT,
                FOREIGN KEY (proposalId) REFERENCES action_proposal(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_action_execution_proposalId ON action_execution(proposalId)")
        db.execSQL("CREATE INDEX index_action_execution_outcome ON action_execution(outcome)")
        db.execSQL("CREATE INDEX index_action_execution_dispatchedAt ON action_execution(dispatchedAt)")

        db.execSQL("""
            CREATE TABLE appfunction_skill (
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
        """.trimIndent())
        db.execSQL("CREATE UNIQUE INDEX index_appfunction_skill_functionId_schemaVersion ON appfunction_skill(functionId, schemaVersion)")
        db.execSQL("CREATE INDEX index_appfunction_skill_appPackage ON appfunction_skill(appPackage)")

        db.execSQL("""
            CREATE TABLE skill_usage (
                id TEXT PRIMARY KEY NOT NULL,
                skillId TEXT NOT NULL,
                executionId TEXT NOT NULL,
                proposalId TEXT NOT NULL,
                episodeId TEXT,
                outcome TEXT NOT NULL,
                latencyMs INTEGER NOT NULL,
                invokedAt INTEGER NOT NULL,
                FOREIGN KEY (skillId) REFERENCES appfunction_skill(functionId),
                FOREIGN KEY (executionId) REFERENCES action_execution(id) ON DELETE CASCADE
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX index_skill_usage_skillId ON skill_usage(skillId)")
        db.execSQL("CREATE INDEX index_skill_usage_invokedAt ON skill_usage(invokedAt)")
        db.execSQL("CREATE INDEX index_skill_usage_outcome ON skill_usage(outcome)")
    }
}
```

The migration is fully tested in `OrbitDatabaseMigrationV1toV2Test`
with a real Room test helper opening a v1 DB, running the
migration, and asserting all queries on both old and new tables
pass.

---

## 8. Provenance Edges (Principle XII)

Every derived fact written by 003 carries explicit provenance:

| Derived row | Source(s) | Cascade behaviour |
|---|---|---|
| `action_proposal` | `intent_envelope.id` (1:1) | Source delete → CASCADE delete proposals. |
| `action_execution` | `action_proposal.id` (1:1) | Proposal delete → CASCADE delete executions. |
| `skill_usage` | `action_execution.id` + `appfunction_skill.functionId` | Execution delete → CASCADE delete usage row. Skill row reference remains (skills are not deleted, only superseded). |
| `intent_envelope` (kind=DIGEST) | `derivedFromEnvelopeIdsJson` array | Source envelope delete → re-evaluate; if all sources gone, soft-delete the DIGEST. |
| (future) `episodes.source_kind=agent_action` | `action_execution.id` | Defined by spec 006 contract; v1.1 leaves `episodeId` null until 006 ships. |

The cascade rules are enforced at the SQLite level via foreign-key
ON DELETE CASCADE. The DIGEST re-evaluation is enforced at the
`EnvelopeRepository.delete()` write path (procedural, because
JSON-array foreign keys aren't expressible in SQL).

---

## 9. Forward Compatibility

| Future spec | Touchpoint in 003 schema | Required change |
|---|---|---|
| Spec 005 (BYOK) | `action_proposal.provenance` accepts `Byok` already. | None. |
| Spec 006 (Orbit Cloud) | `appfunction_skill` and `skill_usage` schemas mirror the cloud `skills` / `skill_usage` schemas. | None — row-level mirror. |
| Spec 008 (Agent) | `appfunction_skill` is the agent's tool registry; `skill_usage` is its planner heuristic. | None — both ship in v1.1. |
| Spec 010 (Visual polish) | `EnvelopeKind` discriminator for DIGEST styling. | None — column exists. |
| Spec 012 (Resolution semantics) | `action_execution.outcome = SUCCESS` is the resolution-trigger source. | Spec 012 will add a `resolution_state` column to `intent_envelope`; not added in v1.1. |

The intent is for spec 003 to be the *only* schema change between
v1 and v1.1, and for v1.2 specs to layer on without modifying any
003 column or constraint.
