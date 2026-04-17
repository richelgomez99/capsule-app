# Data Model: Intent Envelope and Diary (Phase 1)

**Feature Branch**: `002-intent-envelope-and-diary`
**Date**: 2026-04-16
**Database**: `orbit.db`, Room 2.6+ with SQLCipher via `SupportFactory`, encrypted with a Keystore-wrapped 32-byte key. Opens only in the `:ml` process.

---

## Overview

All user content is persisted in an encrypted Room database owned by the
`:ml` process. The `:ui` and `:capture` processes never open the database
directly — they communicate with `:ml` via bound-service IPC (see
`contracts/envelope-repository-contract.md`). SharedPreferences inherited
from 001 remain unencrypted and carry only non-sensitive overlay state
(bubble position, service-enabled flag).

The schema below is v1 — no migrations from v0 (001 Phase 1 did not
persist captures).

---

## Enums

```kotlin
// Where the envelope came from.
enum class ContentType {
    TEXT,   // Clipboard text (001 path)
    IMAGE,  // Screenshot (002 new path)
    MIXED   // Future: share sheet, voice (Phase 2+)
}

// The four v1 intent labels + AMBIGUOUS (unassigned).
enum class Intent {
    WANT_IT,
    REFERENCE,
    FOR_SOMEONE,
    INTERESTING,
    AMBIGUOUS
}

// Categorized foreground app. Raw package name is NOT stored.
enum class AppCategory {
    WORK_EMAIL,
    MESSAGING,
    SOCIAL,
    BROWSER,
    VIDEO,
    READING,
    OTHER,
    UNKNOWN_SOURCE   // permission denied, lock screen, or no resolvable app
}

// Activity Recognition state at capture moment.
enum class ActivityState {
    STILL,
    WALKING,
    RUNNING,
    IN_VEHICLE,
    ON_BICYCLE,
    UNKNOWN          // permission denied or no signal received yet
}

enum class ContinuationType {
    URL_HYDRATE      // v1 only; Phase 2 will add TOPIC_EMBED, TRANSCRIBE, etc.
}

enum class ContinuationStatus {
    PENDING,
    RUNNING,
    SUCCEEDED,
    FAILED_TRANSIENT,   // retry eligible
    FAILED_PERMANENT    // non-HTTPS redirect, 4xx, parse failure, etc.
}

enum class AuditAction {
    ENVELOPE_CREATED,
    ENVELOPE_ARCHIVED,
    ENVELOPE_DELETED,
    INTENT_SUPERSEDED,
    INFERENCE_RUN,
    NETWORK_FETCH,
    CONTINUATION_SCHEDULED,
    CONTINUATION_COMPLETED,
    ORBIT_EXPORTED,
    PERMISSION_REVOKED_DETECTED
}
```

All enum values are stored as their `name()` string to keep the DB
human-readable in audit dumps and robust to reorderings.

---

## 1. IntentEnvelopeEntity

**Location**: `app/src/main/java/com/capsule/app/data/entities/IntentEnvelopeEntity.kt`
**Scope**: Room `@Entity(tableName = "intent_envelope")`, opened only in `:ml`.

```kotlin
@Entity(
    tableName = "intent_envelope",
    indices = [
        Index(value = ["createdAt"]),
        Index(value = ["intent"]),
        Index(value = ["day_local"])
    ]
)
data class IntentEnvelopeEntity(
    @PrimaryKey val id: String,                   // UUIDv4 at seal time
    val contentType: ContentType,
    val textContent: String?,                     // clipboard text or OCR text
    val imageUri: String?,                        // content:// URI from MediaStore
    val textContentSha256: String?,               // dedupe key; null for images
    val intent: Intent,                           // current (latest) intent
    val intentConfidence: Float?,                 // Nano-predicted confidence if silent-wrapped; null if user-assigned
    val intentSource: IntentSource,               // how intent landed (user | silent_wrap | ambiguous_timeout)
    val intentHistoryJson: String,                // JSON array of IntentAssignment (see below)
    @Embedded val state: StateSnapshot,
    val createdAt: Long,                          // epoch millis
    @ColumnInfo(name = "day_local") val dayLocal: String, // ISO yyyy-MM-dd in user's tz, for cheap day queries
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false                // soft-delete; export respects this
)

enum class IntentSource {
    USER_CHIP,
    SILENT_WRAP,
    AMBIGUOUS_TIMEOUT,
    USER_REASSIGN   // on retroactive intent assignment from Diary
}

data class IntentAssignment(
    val intent: Intent,
    val source: IntentSource,
    val at: Long,                                 // epoch millis
    val confidence: Float?                        // nullable
)
```

### Fields

| Field | Type | Nullable | Notes |
|---|---|---|---|
| `id` | `String` | no | UUIDv4 generated at seal time |
| `contentType` | `ContentType` | no | Union-type safety via enum |
| `textContent` | `String?` | yes | Required for TEXT, optional for IMAGE (OCR result merged later) |
| `imageUri` | `String?` | yes | `content://media/external/images/media/{id}` — not file path |
| `textContentSha256` | `String?` | yes | Used for near-duplicate deduplication within a 60s window |
| `intent` | `Intent` | no | Current intent (latest entry in `intentHistoryJson`) |
| `intentConfidence` | `Float?` | yes | Present only for `SILENT_WRAP` |
| `intentSource` | `IntentSource` | no | Matches the latest history entry |
| `intentHistoryJson` | `String` | no | Append-only list; every supersession adds an entry |
| `state` | `StateSnapshot` | no | Embedded; see entity 2 |
| `createdAt` | `Long` | no | Epoch millis, immutable |
| `dayLocal` | `String` | no | `yyyy-MM-dd` in user timezone at `createdAt`; simplifies day queries under DST |
| `isArchived` | `Boolean` | no | User-archived; hidden from default Diary |
| `isDeleted` | `Boolean` | no | Tombstone; removed from Diary, kept for 30 days then hard-deleted |

### Invariants

- `intentHistoryJson` is append-only. The application layer parses, appends,
  serializes back. Direct mutation APIs must go through
  `EnvelopeRepository.reassignIntent()`, which also writes an audit log entry.
- `(contentType == TEXT)` ⇒ `textContent != null` and `imageUri == null`.
- `(contentType == IMAGE)` ⇒ `imageUri != null`. `textContent` may be filled
  later by OCR continuation.
- `intent` always equals the `.intent` of the last element of
  `intentHistoryJson`. Enforced by repository layer; test covers drift.
- If `intentSource == SILENT_WRAP` ⇒ `intentConfidence != null`.

---

## 2. StateSnapshot

**Location**: `app/src/main/java/com/capsule/app/data/entities/StateSnapshot.kt`
**Scope**: `@Embedded` into `IntentEnvelopeEntity`. Does not get its own
table. Each envelope owns exactly one snapshot.

```kotlin
data class StateSnapshot(
    val appCategory: AppCategory,                 // categorized foreground app at capture
    val activityState: ActivityState,             // Activity Recognition state
    val tzId: String,                             // IANA tz, e.g. "America/New_York"
    val hourLocal: Int,                           // 0-23
    val dayOfWeekLocal: Int                       // 1-7 (Mon=1 per ISO-8601)
)
```

### Fields

| Field | Type | Notes |
|---|---|---|
| `appCategory` | `AppCategory` | Result of `AppCategoryDictionary.categorize(packageName)`. Raw package name is never stored. |
| `activityState` | `ActivityState` | Last observed transition-based state; `UNKNOWN` if permission denied or no prior transition. |
| `tzId` | `String` | For cross-timezone Diary browsing if the user travels |
| `hourLocal` | `Int` | Convenience; derivable from `createdAt` + `tzId` but cached for cheap queries |
| `dayOfWeekLocal` | `Int` | Same — supports "weekend vs weekday" grouping if Phase 2 needs it |

### Invariants

- Once sealed on an envelope, a snapshot is immutable.
- `tzId` must be a valid IANA identifier; validated on write.
- Hour/dayOfWeek must be consistent with `createdAt` + `tzId`; a DB
  consistency check runs periodically.

---

## 3. ContinuationEntity

**Location**: `app/src/main/java/com/capsule/app/data/entities/ContinuationEntity.kt`
**Scope**: Room `@Entity(tableName = "continuation")`

```kotlin
@Entity(
    tableName = "continuation",
    foreignKeys = [ForeignKey(
        entity = IntentEnvelopeEntity::class,
        parentColumns = ["id"],
        childColumns = ["envelopeId"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [
        Index(value = ["envelopeId"]),
        Index(value = ["status"]),
        Index(value = ["type", "status"])
    ]
)
data class ContinuationEntity(
    @PrimaryKey val id: String,                   // UUIDv4
    val envelopeId: String,
    val type: ContinuationType,
    val status: ContinuationStatus,
    val inputUrl: String?,                        // for URL_HYDRATE
    val scheduledAt: Long,
    val startedAt: Long?,
    val completedAt: Long?,
    val attemptCount: Int = 0,
    val failureReason: String?                    // enum code like "non_https_redirect"; null on success
)
```

### State Machine

```
PENDING
  │
  ├─[WorkManager dispatched]─► RUNNING
  │                              │
  │                              ├─[success]─► SUCCEEDED
  │                              │
  │                              ├─[transient error + attemptCount < 3]─► FAILED_TRANSIENT ──(retry)──► PENDING
  │                              │
  │                              └─[permanent error OR attemptCount == 3]─► FAILED_PERMANENT (terminal)
  │
  └─[continuation canceled by user (e.g., envelope deleted)]─► FAILED_PERMANENT
```

### Fields

| Field | Type | Notes |
|---|---|---|
| `id` | `String` | UUIDv4 |
| `envelopeId` | `String` | FK, cascade-delete |
| `type` | `ContinuationType` | v1 = `URL_HYDRATE` only |
| `status` | `ContinuationStatus` | See state machine |
| `inputUrl` | `String?` | HTTPS URL to hydrate; for future types, other input payloads |
| `scheduledAt` | `Long` | When it was enqueued |
| `startedAt` | `Long?` | When WorkManager pulled it |
| `completedAt` | `Long?` | Terminal transition timestamp |
| `attemptCount` | `Int` | 0 at schedule; ++ on each attempt |
| `failureReason` | `String?` | Canonical code; shown in Diary card on permanent failure |

### Failure Reasons (canonical codes)

| Code | Meaning |
|---|---|
| `non_https_url` | Initial URL was not HTTPS |
| `non_https_redirect` | Redirected to HTTP |
| `http_error:{code}` | Non-2xx response after retries |
| `non_html_content` | Response was not `text/html` |
| `parse_failed` | jsoup/Readability couldn't extract |
| `nano_unsupported` | AICore/Nano unavailable on device |
| `nano_inference_failed` | Nano ran but errored |
| `timeout` | Overall continuation exceeded budget |
| `envelope_deleted` | Source envelope removed before completion |

---

## 4. ContinuationResultEntity

**Location**: `app/src/main/java/com/capsule/app/data/entities/ContinuationResultEntity.kt`

Separated from `ContinuationEntity` so an envelope can accumulate multiple
result records over time (e.g., a URL that gets re-summarized after a
Nano model update).

```kotlin
@Entity(
    tableName = "continuation_result",
    foreignKeys = [
        ForeignKey(entity = ContinuationEntity::class, parentColumns = ["id"],
                   childColumns = ["continuationId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = IntentEnvelopeEntity::class, parentColumns = ["id"],
                   childColumns = ["envelopeId"], onDelete = ForeignKey.CASCADE)
    ],
    indices = [Index("continuationId"), Index("envelopeId")]
)
data class ContinuationResultEntity(
    @PrimaryKey val id: String,
    val continuationId: String,
    val envelopeId: String,
    val producedAt: Long,

    // URL_HYDRATE result fields
    val title: String?,
    val domain: String?,
    val canonicalUrl: String?,
    val excerpt: String?,       // 200-char text excerpt from Readability
    val summary: String?,       // 2-3 sentence Nano summary
    val summaryModel: String?   // e.g., "gemini-nano-3-2026-03"; for cache invalidation on model bump
)
```

### Query Pattern

The Diary joins `intent_envelope` → latest `continuation_result`
`WHERE type = URL_HYDRATE AND producedAt = MAX(...)`. Encapsulated in
`IntentEnvelopeDao.observeDayWithEnrichments(LocalDate)`.

---

## 5. AuditLogEntryEntity

**Location**: `app/src/main/java/com/capsule/app/data/entities/AuditLogEntryEntity.kt`

The audit log is user-visible (FR-018) and append-only.

```kotlin
@Entity(
    tableName = "audit_log",
    indices = [Index("at"), Index("envelopeId"), Index("action")]
)
data class AuditLogEntryEntity(
    @PrimaryKey val id: String,                   // UUIDv4
    val at: Long,                                 // epoch millis
    val action: AuditAction,
    val description: String,                      // plain-language, user-readable
    val envelopeId: String?,                      // null for app-scope actions (e.g., export)
    val extraJson: String?                        // structured detail for debugging; hidden from user surface
)
```

### Enforced Audit Events

| Event | Written by | Description format |
|---|---|---|
| `ENVELOPE_CREATED` | `EnvelopeRepository.seal()` | "Saved {contentType} from {app category}" |
| `ENVELOPE_ARCHIVED` | `EnvelopeRepository.archive()` | "Archived envelope from {date}" |
| `ENVELOPE_DELETED` | `EnvelopeRepository.delete()` | "Deleted envelope from {date}" |
| `INTENT_SUPERSEDED` | `EnvelopeRepository.reassignIntent()` | "Changed intent from {old} to {new}" |
| `INFERENCE_RUN` | `NanoClient.*` wrappers | "Summarized article from {domain}" / "Predicted intent from preview" / "Generated day summary for {date}" |
| `NETWORK_FETCH` | `:net` → `:ml` result callback | "Fetched {domain}" (always from `:net`, so exhaustive) |
| `CONTINUATION_SCHEDULED` | `ContinuationEngine.enqueue()` | "Scheduled URL enrichment" |
| `CONTINUATION_COMPLETED` | `ContinuationEngine.complete()` | "Enrichment complete for {domain}" or "Enrichment failed: {reason}" |
| `ORBIT_EXPORTED` | `ExportActivity` | "Exported {N} envelopes to {destination}" |
| `PERMISSION_REVOKED_DETECTED` | `StateSnapshotProvider` on failure | "{permission} was revoked; context labels reduced" |

### Invariants

- **Append-only**: no update or delete paths except a nightly
  retention-sweep that drops entries older than 180 days.
- **Zero omission on network**: every outbound HTTP call from `:net`
  MUST complete a `NETWORK_FETCH` entry, whether the fetch succeeded or
  failed. Enforced by the `:net` service contract and a unit test.

---

## 6. DayPage (derived, not persisted)

```kotlin
data class DayPage(
    val date: LocalDate,
    val headerParagraph: String?,      // Nano-generated; null if unavailable or < 3 envelopes
    val headerFallback: String?,       // template fallback when headerParagraph null but envelopes exist
    val threads: List<Thread>,
    val envelopeCount: Int,
    val isEmpty: Boolean               // true iff no envelopes
)
```

Computed at Diary read time by `DiaryViewModel` from the envelope +
`continuation_result` join. Cached by `(date, envelope_count, latest_envelope_id)`.

---

## 7. Thread (derived, not persisted)

```kotlin
data class Thread(
    val id: String,                    // derived: "{date}-{appCategory}-{idx}"
    val appCategory: AppCategory,
    val envelopes: List<EnvelopeView>, // sorted by createdAt asc
    val hasMultiple: Boolean           // convenience
)

data class EnvelopeView(
    val id: String,
    val intent: Intent,
    val contentPreview: String,        // first 80 chars of text / excerpt / "Screenshot"
    val imageThumbUri: String?,        // for IMAGE envelopes
    val appCategory: AppCategory,
    val activityState: ActivityState,
    val hourLocal: Int,
    val minuteLocal: Int,
    val enrichment: UrlEnrichment?     // null until continuation completes
)

data class UrlEnrichment(
    val title: String,
    val domain: String,
    val summary: String?,
    val available: Boolean,            // false if continuation failed permanently
    val failureReason: String?
)
```

Computed per query; never persisted. Thread IDs are stable only within a
single Diary load — do not use across loads.

---

## 8. SharedPreferences (inherited from 001)

**File name**: `capsule_overlay_prefs` (unchanged)

| Key | Type | Default | Source | Notes |
|---|---|---|---|---|
| `bubble_x` | `Int` | `0` | 001 | unchanged |
| `bubble_y` | `Int` | `100` | 001 | unchanged |
| `bubble_edge` | `String` | `"LEFT"` | 001 | unchanged |
| `service_enabled` | `Boolean` | `false` | 001 | unchanged |
| `restart_count` | `Int` | `0` | 001 | unchanged |
| `last_start_ts` | `Long` | `0` | 001 | unchanged |
| `last_kill_ts` | `Long` | `0` | 001 | unchanged |
| `onboarding_complete` | `Boolean` | `false` | 002 NEW | flips true after the 4-permission walkthrough ends |
| `nano_availability` | `String` | `"UNKNOWN"` | 002 NEW | cached result of last availability check; enum name |
| `last_nano_check_ts` | `Long` | `0` | 002 NEW | for periodic re-check cadence |
| `screenshot_observer_enabled` | `Boolean` | `false` | 002 NEW | user opt-in to screenshot capture in Settings |
| `db_key_ciphertext` | `String` | `""` | 002 NEW | base64 Keystore-wrapped SQLCipher key |

No user content lives in SharedPreferences. All envelope and audit data
is in the encrypted Room DB.

---

## 9. Encryption Key Lifecycle

1. **First run** (no `db_key_ciphertext`): generate a 32-byte random via
   `SecureRandom`, wrap with a Keystore AES-256 key `orbit_db_key_v1`
   (GCM, non-extractable), store ciphertext in SharedPreferences.
2. **Normal start**: unwrap via Keystore and open SQLCipher with the raw
   key.
3. **Keystore loss** (factory reset, key invalidated): the wrapped key is
   unreadable. Orbit detects the failure, surfaces a "corpus inaccessible
   — reset Orbit" dialog. User confirms → wipe DB → regenerate.
4. **Future rotation** (Phase 2): re-key via SQLCipher `PRAGMA rekey`;
   wrap new key with Keystore; update SharedPreferences atomically.

---

## 10. Schema Migration Strategy

- **v1**: this schema, shipped at Orbit 1.0 GA.
- **Fresh database**: 001 Phase 1 stored nothing; no prior data exists.
- **Future migrations**: handled with Room's `MIGRATION_n_m` plus
  `SQLCipher.SupportOpenHelper.onUpgrade`. Each migration is a named
  Kotlin function with instrumented tests.

---

## 11. Relationships (ER Overview)

```
            ┌────────────────────────┐
            │  IntentEnvelopeEntity  │
            │  (id, state, intent…)  │
            └───────────┬────────────┘
                        │ 1..N
                        ▼
            ┌────────────────────────┐
            │   ContinuationEntity   │
            │  (type, status, url…)  │
            └───────────┬────────────┘
                        │ 1..N
                        ▼
            ┌──────────────────────────────┐
            │ ContinuationResultEntity     │
            │ (title, domain, summary…)    │
            └──────────────────────────────┘

            ┌──────────────────────────────┐
            │  AuditLogEntryEntity         │
            │  (envelopeId nullable)       │
            └──────────────────────────────┘
```

- IntentEnvelope 1 ─ 0..N Continuation (cascade delete).
- Continuation 1 ─ 0..N ContinuationResult (cascade delete).
- AuditLog has an optional FK-less reference to envelope by `envelopeId`
  string — we deliberately avoid the FK so audit entries survive
  envelope deletion.

---

## 12. Phase 2 Migration Notes

When Phase 2 introduces Ripe Nudges and broader context:

- Add `NudgeEntity` (scheduled, sent, acknowledged, snoozed) — NEW.
- Add `LocationSnapshot` as a separate embedded value on StateSnapshot
  (not into the envelope table; a denormalized side-table to avoid
  bloating the envelope row).
- Add `EmbeddingEntity` keyed by `envelopeId` for topic similarity
  lookups if we move away from re-computing embeddings on-the-fly.
- Add `TOPIC_EMBED` and `TRANSCRIBE` to `ContinuationType`.
- `IntentSource` gains `AGENT_WRITE` when AppFunctions go live.

None of these require schema-breaking changes to the v1 tables — they're
all additive.
