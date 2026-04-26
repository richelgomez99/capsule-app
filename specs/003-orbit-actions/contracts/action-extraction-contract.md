# Action Extraction Contract

**Feature Branch**: `003-orbit-actions`
**Date**: 2026-04-26
**Owning process**: `:ml`
**Callers**: `ContinuationEngine` (`:ml`), `LlmProvider` impls (`:ml`)
**Implements FR**: FR-003-001, FR-003-005, FR-003-006

---

## 1. Purpose

Action extraction is the pipeline that turns a sealed
`IntentEnvelope` into 0..N `ActionProposal` rows. It runs as a
`ContinuationType.ACTION_EXTRACT` continuation, gated by
charger + wifi (Principle IV). It NEVER blocks the seal path.

Two-phase pipeline (see `research.md` §2):

1. Synchronous **regex pre-filter** in `:ml` after seal commits.
2. Asynchronous **Nano extraction** queued as a continuation.

---

## 2. Continuation lifecycle

### Enqueue

`ContinuationEngine` enqueues an `ACTION_EXTRACT` continuation iff:

- Envelope `kind = REGULAR` (no extraction on DIGEST/DERIVED).
- Envelope `intent != AMBIGUOUS` OR Phase A regex matches at least
  one indicator.
- Envelope `sensitivity_flags` does NOT include `credentials` or
  `medical` (these are scoped out per Principle VIII / research §8).
- No existing non-INVALIDATED proposal for the same `envelopeId`.

WorkRequest constraints:

```kotlin
Constraints.Builder()
    .setRequiresCharging(true)
    .setRequiredNetworkType(NetworkType.UNMETERED)
    .setRequiresBatteryNotLow(true)
    .build()
```

Backoff: `BackoffPolicy.EXPONENTIAL`, 30s base.

### Idempotency

Unique work id: `action-extract-${envelopeId}`. Re-enqueue while
existing work is in-flight is a no-op (`ExistingWorkPolicy.KEEP`).

---

## 3. LlmProvider extension

```kotlin
interface LlmProvider {
    // ... 002 methods preserved verbatim ...

    suspend fun extractActions(
        text: String,
        contentType: ContentType,
        state: StateSnapshot,
        registeredFunctions: List<AppFunctionSummary>,
        maxCandidates: Int = 3
    ): ActionExtractionResult
}

data class AppFunctionSummary(
    val functionId: String,
    val argsSchemaJson: String,
    val sensitivityScope: SensitivityScope
)

data class ActionExtractionResult(
    val candidates: List<ActionCandidate>,
    val provenance: LlmProvenance
)

data class ActionCandidate(
    val functionId: String,
    val argsJson: String,
    val previewTitle: String,
    val previewSubtitle: String?,
    val confidence: Float                // 0.0..1.0
)
```

**Implementations**:

| Implementation | v1.1 status |
|---|---|
| `NanoLlmProvider` | Required. Real Nano on Android with AICore; throws on devices where Nano is unavailable, then `ActionExtractor` catches and writes a no-proposal outcome. |
| `OrbitManagedLlmProvider` (spec 005) | Optional, opt-in per-capability. Routes through `:agent` consent filter (Principle XI). |
| `ByokLlmProvider` (spec 005) | Optional, opt-in. Same consent filter. |

The interface guarantees:

- **Pure function**: same `text + state + registeredFunctions` →
  same result distribution. No hidden state, no clock.
- **Bounded**: `maxCandidates` is a hard cap; implementations MUST
  truncate, not error.
- **Deterministic JSON**: `argsJson` MUST be UTF-8, ≤ 4 KB,
  parseable as JSON, and validated against the named function's
  `argsSchemaJson` BEFORE the candidate enters the result list.
  Invalid → silently dropped.

---

## 4. ActionExtractor (`:ml`)

```kotlin
class ActionExtractor(
    private val llmProvider: LlmProvider,
    private val registry: AppFunctionRegistry,
    private val proposalDao: ActionProposalDao,
    private val auditLog: AuditLogWriter,
    private val sensitivityScrubber: SensitivityScrubber,
    private val confidenceFloor: Float = 0.55f
) {
    suspend fun extract(envelopeId: String): ExtractOutcome
}

sealed interface ExtractOutcome {
    object NoCandidates : ExtractOutcome
    data class Proposed(val proposalIds: List<String>) : ExtractOutcome
    data class Skipped(val reason: String) : ExtractOutcome      // sensitivity, etc.
    data class Failed(val reason: String) : ExtractOutcome       // Nano unavailable, etc.
}
```

**Pipeline order**:

1. Load envelope from `EnvelopeRepository`. If missing → `NoCandidates`.
2. Re-check sensitivity gates from §2 enqueue conditions; if newly
   sensitive → `Skipped("sensitivity_changed")`.
3. Resolve registered functions filtered by `appPackage =
   "com.capsule.app"` (v1.1 only) — emit `AppFunctionSummary` list.
4. Call `llmProvider.extractActions(...)` with 8s timeout. On any
   exception → `Failed("nano_${exceptionClass}")` and write audit
   row. Returns `NoCandidates` to the worker (work succeeds; we
   simply produced nothing).
5. For each candidate with `confidence >= confidenceFloor`:
   - Validate `argsJson` against `argsSchemaJson` again
     (defense in depth). Fail → drop silently.
   - Cross-check `SensitivityScope` of the function against the
     envelope's flags. Mismatch → drop silently, audit
     `ACTION_DISMISSED reason=sensitivity_scope_mismatch`.
   - Insert `ActionProposalEntity` with `state = 'PROPOSED'`.
6. Within the same Room transaction, write one `ACTION_PROPOSED`
   audit row per inserted proposal.
7. Return `Proposed(ids)`.

**Determinism for tests**: `confidenceFloor` is injectable; tests
inject 0f to bypass thresholding when validating the rest of the
pipeline.

---

## 5. Worker

```kotlin
class ActionExtractionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val envelopeId = inputData.getString(KEY_ENVELOPE_ID)
            ?: return Result.failure()

        return when (extractor.extract(envelopeId)) {
            is ExtractOutcome.NoCandidates,
            is ExtractOutcome.Skipped,
            is ExtractOutcome.Proposed -> Result.success()
            is ExtractOutcome.Failed -> Result.retry()
        }
    }

    companion object {
        const val KEY_ENVELOPE_ID = "envelope_id"
        const val MAX_ATTEMPTS = 3
    }
}
```

After `MAX_ATTEMPTS` failed attempts, the worker writes
`CONTINUATION_FAILED reason=action_extract_exhausted` to audit and
gives up. The user never sees an error — graceful silence.

---

## 6. Test surface

| Test | What it verifies |
|---|---|
| `ActionExtractorTest` (JVM) | Pipeline ordering, confidence floor, schema validation drop, sensitivity gating, audit-row atomicity (verified via in-memory Room). |
| `ActionExtractionWorkerTest` (instrumented) | Worker → extractor wiring, retry behaviour on `Failed`, success on `NoCandidates`, idempotency on re-enqueue. |
| `LlmProviderExtractActionsContractTest` (JVM) | A test fixture every `LlmProvider` implementation runs to assert: bounded `maxCandidates`, valid JSON, schema-conformance of returned `argsJson`, deterministic provenance field. |
| `NoNetworkDuringExtractTest` (instrumented) | Proves the worker process (`:ml`) cannot reach the network during extraction (UID socket-creation guard, asserts `BindException` if any code path attempts a connection). |

---

## 7. Constitution alignment

| Principle | How extraction honours it |
|---|---|
| I (Local-first) | Default `NanoLlmProvider` is on-device. Cloud paths are opt-in per capability and route through the `:agent` consent filter. |
| III (Intent before artifact) | Proposals are derivative; the source envelope's intent is never mutated by extraction. `envelopeId` FK with cascade preserves provenance. |
| IV (Continuations) | Charger + wifi gating; extraction never blocks seal. |
| VI (Privilege separation) | Runs in `:ml`. The `:net` boundary is only crossed when a cloud provider is opted in, and only via the existing `:agent` filter. |
| VIII (Collect only what you use) | Only persists proposals that pass the confidence floor and schema validation. Discarded candidates leave no row. |
| IX (Cloud escape hatch) | `LlmProvider` provenance enum supports BYOK without touching this pipeline. |
| XI (Consent-aware prompts) | Cloud routing crosses `:agent` filter; extraction never assembles a prompt outside `:ml`. |
| XII (Provenance) | `action_proposal.envelopeId` mandatory FK, cascade-delete to source. |
