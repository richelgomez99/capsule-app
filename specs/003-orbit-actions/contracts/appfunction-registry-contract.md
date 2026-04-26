# AppFunction Registry Contract

**Feature Branch**: `003-orbit-actions`
**Date**: 2026-04-26
**Owning process**: `:ml`
**Callers**: `:ml` (ActionExtractor), `:capture` (ActionExecutorService), `:ui` (Settings)
**Implements FR**: FR-003-007, FR-003-009

---

## 1. Purpose

The `AppFunctionRegistry` is the canonical store of every action
Orbit can take. It serves three readers:

1. **ActionExtractor** (`:ml`): "what functions can I propose?"
2. **ActionExecutorService** (`:capture`): "given a `functionId` and
   args, how do I dispatch the side effect?"
3. **v1.2 Orbit Agent** (forward-compat, spec 008): "what tools can
   the planner invoke, and what are their success rates?"

Schema mirrors spec 006's `skills` / `skill_usage` tables verbatim
so v1.1+ cloud sync is a row-level mirror.

---

## 2. Registration

### `@AppFunction` annotation

```kotlin
@AppFunction(
    id = "com.capsule.app.action.calendar_insert",
    schemaVersion = 1,
    sideEffects = SideEffect.EXTERNAL_INTENT,
    reversibility = Reversibility.EXTERNAL_MANAGED,
    sensitivityScope = SensitivityScope.PUBLIC
)
data class CalendarInsertArgs(
    val title: String,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val location: String? = null,
    val description: String? = null,
    val timeZoneId: String                       // e.g., "America/Los_Angeles"
)
```

KSP processes `@AppFunction` at compile time to generate:

- `argsSchemaJson` (JSON Schema describing the data class).
- `AppFunctionInvoker` dispatch entry per function.

The runtime `AppFunctionRegistry.register(schema)` upserts the row
into `appfunction_skill`, atomic with an `APPFUNCTION_REGISTERED`
audit row.

**Trigger**: `OrbitApplication.onCreate` in `:ml` calls
`registry.registerAll(BUILT_IN_SCHEMAS)`. Idempotent — re-running
on identical schema is a no-op.

### Schema versioning

Bumping a schema requires a new code-time `schemaVersion = N+1`.
The registry inserts a new row (the unique constraint
`(functionId, schemaVersion)` permits both rows to coexist).
Lookups by `functionId` alone return the highest `schemaVersion`.
Old rows remain for audit but are NOT used for new proposals.

Existing `action_proposal` rows referencing the old schema version
remain valid (their `argsJson` was validated against the
schema-version-at-proposal-time). Execution validates against the
**proposal's** schema version, not the latest, to honour the
proposal's intent.

---

## 3. v1.1 built-in schemas

| `functionId` | Args | sideEffects | reversibility | sensitivityScope |
|---|---|---|---|---|
| `com.capsule.app.action.calendar_insert` | `CalendarInsertArgs` | EXTERNAL_INTENT | EXTERNAL_MANAGED | PUBLIC |
| `com.capsule.app.action.todo_add` | `TodoAddArgs(text, dueEpochMillis?, listId?)` | EXTERNAL_INTENT or LOCAL_DB_WRITE (target=local) | REVERSIBLE_24H (local) / EXTERNAL_MANAGED (share) | PERSONAL |
| `com.capsule.app.action.share` | `ShareArgs(text, mimeType, subject?)` | EXTERNAL_INTENT | EXTERNAL_MANAGED | SHARE_DELEGATED |

These are the only schemas registered in v1.1. Spec 008 v1.2 adds
agent-internal functions (e.g., `summarize_cluster`,
`save_structured_list`); none ship in v1.1.

---

## 4. Read API

```kotlin
interface AppFunctionRegistry {

    suspend fun register(schema: AppFunctionSchema): RegisterResult

    suspend fun lookup(functionId: String): AppFunctionSchema?
    suspend fun lookup(functionId: String, schemaVersion: Int): AppFunctionSchema?

    suspend fun listForApp(appPackage: String): List<AppFunctionSummary>

    suspend fun stats(functionId: String, sinceMillis: Long): SkillStats?

    suspend fun recordInvocation(
        functionId: String,
        executionId: String,
        proposalId: String,
        outcome: InvocationOutcome,
        latencyMs: Long
    )
}

sealed class RegisterResult {
    object Inserted : RegisterResult()
    object Unchanged : RegisterResult()
    data class VersionBumped(val previousVersion: Int, val newVersion: Int) : RegisterResult()
}

enum class InvocationOutcome { SUCCESS, FAILED, USER_CANCELLED }

data class SkillStats(
    val skillId: String,
    val successRate: Float,
    val cancelRate: Float,
    val avgLatencyMs: Float,
    val invocationCount: Int
)
```

`recordInvocation` writes a `skill_usage` row in the same Room
transaction as the `action_execution` outcome update. The
`SkillStats` aggregation powers Settings → Actions UI and v1.2
agent's planner heuristics.

---

## 5. IPC surface

The registry is owned by `:ml`. Other processes call it via the
existing `IEnvelopeRepository` binder, extended with three new
methods:

```aidl
// IEnvelopeRepository.aidl (excerpt — additions only)

interface IEnvelopeRepository {
    // ... 002 methods preserved ...

    // 003 additions (read-only from non-:ml callers):
    AppFunctionSchemaParcel lookupAppFunction(in String functionId);
    List<AppFunctionSummaryParcel> listAppFunctions(in String appPackage);

    // Write-back from :capture after Intent dispatch:
    void recordActionInvocation(in ActionInvocationOutcomeParcel outcome);
}
```

`recordActionInvocation` is the only IPC write the `:capture`
process performs against the corpus — it carries the
`executionId`, `outcome`, and `latencyMs` and triggers the audit
row + skill_usage write atomically in `:ml`. `:capture` never
opens the DB.

---

## 6. Concurrency

- All registry writes are serialised via a single `Mutex` in
  `AppFunctionRegistry`. Concurrent `register()` calls (e.g., two
  `:ml` startup races) are safe.
- Reads use Room's standard `@Query` — many concurrent readers, no
  blocking.
- `recordInvocation` is fast-path (single Room transaction) and
  returns within p99 < 50ms; latency budget for the
  capture→ml→capture round-trip is < 200ms total.

---

## 7. Constitution alignment

| Principle | How registry honours it |
|---|---|
| VI (Privilege separation) | Registry data lives in `:ml`. `:capture` reads via binder, writes only the narrow `recordActionInvocation` surface. `:ui` reads via the same binder. |
| VIII (Collect only what you use) | Schema versioning is append-only — only schemas that power a v1.1 user-visible feature ship. Stats columns are user-visible (Settings → Actions). |
| X (Sovereign cloud storage) | Schema mirrors spec 006's `skills` / `skill_usage` so cloud sync (when 006 ships) is row-level. v1.1 keeps all data on-device. |
| XII (Provenance) | Every `skill_usage` row references both `executionId` and (transitively) `proposalId` and `envelopeId`. Stats are derivable from a single SQL aggregation rooted in source episodes. |

---

## 8. Test surface

| Test | What it verifies |
|---|---|
| `AppFunctionRegistryTest` (JVM) | Register / upsert idempotency, version bump semantics, lookup by id-only vs. id+version, stats aggregation. |
| `AppFunctionRegistryConcurrencyTest` (JVM) | Two coroutines registering simultaneously produce one Inserted + one Unchanged (or one Inserted + one VersionBumped if intentional). |
| `RegistryIpcContractTest` (instrumented) | `:capture` and `:ui` can lookup; `:capture` can recordInvocation; neither can call private methods like `register`. |
| `SchemaValidationTest` (JVM) | Generated `argsSchemaJson` rejects malformed args, accepts conforming args, handles optional fields. |
