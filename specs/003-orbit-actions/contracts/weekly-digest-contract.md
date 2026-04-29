# Weekly Digest Contract

**Feature Branch**: `003-orbit-actions`
**Date**: 2026-04-26
**Owning process**: `:ml`
**Callers**: `WorkManager` scheduler (`:ml`)
**Implements FR**: FR-003-004

---

## 1. Purpose

The weekly digest extends 002's daily day-header paragraph into a
week-scoped summary, surfaced as a single new envelope of
`kind = DIGEST` at the top of every Sunday's diary page.

Per the spec, this is one of two infrastructure pieces v1.2 agent
(spec 008) reuses; the digest's prompt-assembly + envelope-output
pattern is the template for any future agent-derived envelope
(per spec 012's `EnvelopeKind.DERIVED` reservation).

---

## 2. Schedule

```kotlin
val request = PeriodicWorkRequestBuilder<WeeklyDigestWorker>(
    repeatInterval = 7, repeatIntervalTimeUnit = TimeUnit.DAYS,
    flexInterval = 4, flexIntervalTimeUnit = TimeUnit.HOURS
)
    .setInitialDelay(initialDelayUntilSunday0600(localTime), TimeUnit.MILLISECONDS)
    .setConstraints(
        Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()
    )
    .build()

WorkManager.getInstance(context).enqueueUniquePeriodicWork(
    "weekly-digest",
    ExistingPeriodicWorkPolicy.KEEP,
    request
)
```

- **Anchor**: next Sunday 06:00 in the device's `ZoneId.systemDefault()`.
- **Configurable**: user can change the local time in
  Settings → Digest schedule. Day stays Sunday in v1.1 (other days
  deferred to v1.2).
- **Constraints (Principle IV)**: charger + wifi + battery-not-low.
- **Flex interval**: 4 hours — WorkManager runs anywhere in that
  window once constraints are met.
- **Existing-policy KEEP**: re-scheduling on app update / locale
  change preserves the existing schedule's anchor, preventing
  Sunday-after-Sunday-after-Sunday duplicates.

If constraints are not met within 24h of the scheduled anchor,
the worker is skipped for that week and an audit row is written
(`DIGEST_SKIPPED reason=constraints_unmet_24h`). The next
schedule fires the following Sunday as normal — no catch-up
worker.

---

## 3. Idempotency

Unique work id: `weekly-digest`. The unique-periodic-work policy
plus an in-DB unique constraint on
`(intent_envelope.kind = 'DIGEST', day_local)` prevents duplicate
DIGEST envelopes for the same Sunday.

```sql
CREATE UNIQUE INDEX index_digest_unique_per_day
ON intent_envelope(day_local)
WHERE kind = 'DIGEST';
```

If a re-run finds an existing DIGEST envelope for the target
Sunday → audit `DIGEST_SKIPPED reason=already_exists`, return
`Result.success()`.

---

## 4. Input window

```kotlin
data class DigestWindow(
    val zoneId: ZoneId,
    val targetSunday: LocalDate,                 // the Sunday this digest surfaces on
    val windowStart: LocalDate,                  // Monday before targetSunday
    val windowEndInclusive: LocalDate            // Saturday before targetSunday
)
```

**Selection**: `[targetSunday - 7d, targetSunday - 1d]` inclusive
in local time. The target Sunday itself is excluded — its
day-header runs separately on its own day.

**Filter**: only `kind = REGULAR` envelopes that are not
soft-deleted, not archived, and whose `intent != AMBIGUOUS`. Up
to 100 envelopes are loaded; if the window contains more, the
top-100 by Nano salience score (carried from 002 day-header
generation) are kept.

---

## 5. Composition

```kotlin
class DigestComposer(
    private val llmProvider: LlmProvider,
    private val envelopeRepository: EnvelopeRepository
) {
    suspend fun compose(window: DigestWindow): DigestComposition
}

sealed class DigestComposition {
    object EmptyWindow : DigestComposition()                // < 3 envelopes total
    data class Composed(
        val text: String,
        val derivedFromEnvelopeIds: List<String>,
        val provenance: LlmProvenance,
        val locale: String                                  // "en", "en-GB", or "fallback-structured"
    ) : DigestComposition()
}
```

Prompt structure (assembled in `:ml`):

```
[SYSTEM]
You are summarising a user's week of personal captures from
their journal. Write 4–6 sentences. Friendly, observational,
not promotional. No first person plural. Use the user's intent
labels ("Want it", "Reference", "For someone", "Interesting")
when helpful. Do not enumerate every entry. Surface patterns:
recurring topics, app-category shifts, intent shifts.

[INPUT]
2026-04-27 (Mon): {day-header from 002} • Top: {3 salient titles}
2026-04-28 (Tue): {day-header} • Top: {...}
...
2026-05-02 (Sat): {day-header} • Top: {...}

[CROSS-DAY]
Repeated topics: {ThreadGrouper output where threadSpan > 1d}
App-category mode: {top categories}
Intent distribution: {counts}
```

The prompt is bounded to 4 KB. Truncation drops bottom-ranked
envelopes first; cross-day section is preserved if any salience
remains.

**Fallbacks** (in order):

| Trigger | Output |
|---|---|
| `< 3` envelopes in window | `EmptyWindow`, audit `DIGEST_SKIPPED reason=too_sparse` |
| Nano unavailable / throws | `Composed` with structured English fallback: `"X captures across Y app categories this week. Most often: ..."` and `locale = "fallback-structured"` |
| Locale unsupported (non-English) | Same structured fallback in English; `locale = "fallback-structured"` |
| Prompt > 4 KB after truncation | Reduce to top-50 envelopes; if still > 4 KB, structured fallback |

---

## 6. Output: DIGEST envelope

```kotlin
val digest = IntentEnvelopeEntity(
    id = UUID.randomUUID().toString(),
    contentType = ContentType.TEXT,
    textContent = composition.text,
    imageUri = null,
    textContentSha256 = sha256(composition.text),
    intent = Intent.REFERENCE,                       // structurally a reference object
    intentConfidence = null,
    intentSource = IntentSource.AUTO_AMBIGUOUS,      // not user-assigned
    intentHistoryJson = "[]",
    state = StateSnapshot.system(zoneId),            // synthesised; appCategory=OTHER, activityState=UNKNOWN
    createdAt = System.currentTimeMillis(),
    dayLocal = targetSunday.toEpochDay(),
    kind = EnvelopeKind.DIGEST,
    derivedFromEnvelopeIdsJson = json(composition.derivedFromEnvelopeIds)
)
```

The DIGEST envelope is inserted via the same
`EnvelopeRepository.seal()` path 002 envelopes use, with a
`source = SealSource.SYSTEM_DIGEST` discriminator that bypasses
the chip-row UX (DIGEST envelopes don't get an intent chip — they
already have an intent assigned).

The `derivedFromEnvelopeIdsJson` array is the provenance edge
required by Principle XII. Cascade behaviour: if every source
envelope is deleted, the DIGEST envelope is soft-deleted (audit
`ENVELOPE_INVALIDATED reason=lost_provenance`).

---

## 7. Diary rendering (informative, owned by `:ui`)

`DiaryViewModel` query for a Sunday with a DIGEST envelope:

```kotlin
@Query("""
    SELECT * FROM intent_envelope
    WHERE day_local = :day
      AND deletedAt IS NULL
    ORDER BY
      CASE kind WHEN 'DIGEST' THEN 0 ELSE 1 END ASC,
      createdAt DESC
""")
```

Effect: DIGEST envelope renders at the top of Sunday's diary
page. Below it, the cluster suggestion card (002 amendment) and
then the chronological feed of regular envelopes.

`DigestEnvelopeUI` renders with a distinct typography variant per
`design.md` (Fraunces 300 for the digest body, italicised "this
week" pretitle in Newsreader). Spec 010 finalises the styling.

---

## 8. Constitution alignment

| Principle | How digest honours it |
|---|---|
| I (Local-first) | Generation runs in `:ml`. No network. Output is a local envelope. |
| IV (Continuations) | Charger + wifi + battery-not-low constraints. Never blocks foreground. |
| V (Under-deliver on noise) | No notification, no badge. Surfaces only when the user opens Sunday's diary. |
| VIII (Collect only what you use) | Reuses 002 day-header and ThreadGrouper outputs — no new signal collection. |
| IX (Cloud escape hatch) | `LlmProvider` provenance routes through BYOK if user opts in (Settings → Digest → Cloud quality). v1.1 default OFF. |
| XII (Provenance) | `derivedFromEnvelopeIdsJson` mandatory; cascade-delete preserved. |

---

## 9. Test surface

| Test | What it verifies |
|---|---|
| `WeeklyDigestWorkerTest` (instrumented) | Schedule anchor calculation, idempotency on re-run, constraint enforcement (test scheduler), audit-row atomicity. |
| `DigestComposerTest` (JVM) | Empty-window short-circuit, sparse-window fallback, Nano-throw fallback, locale routing, prompt truncation under 4 KB. |
| `DigestProvenanceTest` (instrumented) | Deleting all source envelopes invalidates the DIGEST; deleting some keeps it; `derivedFromEnvelopeIdsJson` integrity. |
| `DiaryDigestRenderingTest` (Compose UI) | Sunday with DIGEST renders DIGEST first, then cluster card, then chronological feed. |
| `DigestUniquenessConstraintTest` (instrumented) | Two concurrent inserts targeting the same Sunday: one succeeds, the other observes the unique-index conflict and audits SKIPPED. |
