# Audit Log Contract: Intent Envelope and Diary

**Feature Branch**: `002-intent-envelope-and-diary`
**Date**: 2026-04-16
**Owning process**: `:ml`
**Callers**: `:capture`, `:ml` (internal), `:net`, `:ui` (read-only)

---

## 1. Purpose

Every non-trivial action Orbit takes is recorded in a local, encrypted
audit log. The log is user-readable in Settings → "What Orbit did today"
and is the ground truth for the principle:

> **III. Transparency Of Intelligence.** Anything Orbit infers or acts
> on autonomously — categorisation, summary generation, network fetch,
> scheduled retry — must be explainable and inspectable by the user
> from within the app.

This contract defines what is logged, how, and who can read it.

---

## 2. Storage

- Table: `audit_log` (see `data-model.md`).
- Same encrypted Room DB as envelopes; no separate file.
- Retention: **90 days** rolling window. A daily `AuditRetentionWorker`
  hard-deletes entries older than 90 days. This is aggressive because
  audit data exists to show the user what happened recently, not to
  build a long-term ledger, and minimising log size minimises data at
  rest.

---

## 3. Actions (closed set for v1)

```kotlin
enum class AuditAction {
    ENVELOPE_CREATED,       // after seal()
    INTENT_SUPERSEDED,      // after reassignIntent()
    ENVELOPE_ARCHIVED,
    ENVELOPE_DELETED,
    CONTINUATION_ENQUEUED,
    CONTINUATION_COMPLETED,
    CONTINUATION_FAILED,
    NETWORK_FETCH,
    NANO_INFERENCE,         // every on-device LLM call
    OCR_RUN,                // every ML Kit OCR call
    SERVICE_STARTED,
    SERVICE_STOPPED,
    PERMISSION_GRANTED,
    PERMISSION_REVOKED,
    PRIVACY_PAUSED,         // user paused continuations
    PRIVACY_RESUMED,
    EXPORT_STARTED,
    EXPORT_COMPLETED
}
```

Adding a new action requires a constitution-compliant review: new
actions must be covered in the "What Orbit did today" view before
shipping.

---

## 4. Write API (internal to `:ml`)

```kotlin
interface AuditLogWriter {
    suspend fun write(
        action: AuditAction,
        description: String,
        envelopeId: EnvelopeId? = null,
        extra: Map<String, Any?> = emptyMap()
    )
}
```

**Contract**:

- `description` is the one-sentence, user-facing text shown in the
  audit view. It MUST NOT contain PII beyond what the user already
  saw or typed (e.g. "Fetched nytimes.com for envelope created at
  9:14" is fine; the full URL is not shown in the description — it's
  stored in `extraJson` for the "Details" drawer if the user taps in).
- `extra` is serialised to JSON and stored in `extraJson`. This field
  is for forensic detail; the UI treats it as opt-in detail.
- `write` is **fire-and-forget** for callers, but MUST succeed for the
  transaction in `EnvelopeRepository.seal()` to succeed. Implementation
  uses a single shared coroutine scope and the same Room transaction
  for seal-time writes.

---

## 5. Read API (exposed to `:ui`)

Exposed via the `EnvelopeRepository` service as an additional AIDL:

```aidl
// IAuditLog.aidl
package com.capsule.app.data.ipc;

import com.capsule.app.data.ipc.AuditEntryParcel;

interface IAuditLog {
    // Returns the entries for a given day, most recent first.
    // Capped at 1000 rows to avoid oversize parcels.
    List<AuditEntryParcel> entriesForDay(String isoDate);

    // Returns entries tied to a single envelope, most recent first.
    List<AuditEntryParcel> entriesForEnvelope(String envelopeId);

    // Counts for Settings summary row (e.g. "23 captures, 41 enrichments today").
    int countForDay(String isoDate, String actionName);
}
```

Parcel layout:

```kotlin
data class AuditEntryParcel(
    val id: String,
    val atMillis: Long,
    val action: String,
    val description: String,
    val envelopeId: String?,
    val extraJson: String?  // opaque; UI renders as pretty JSON on demand
) : Parcelable
```

---

## 6. Guarantees

1. **Atomicity with envelope writes**. `ENVELOPE_CREATED`,
   `INTENT_SUPERSEDED`, `ENVELOPE_ARCHIVED`, `ENVELOPE_DELETED` are
   written inside the same Room transaction as the envelope mutation.
   Either both commit or neither.
2. **Append-only**. The only way a row is removed is by the retention
   worker. No `UPDATE`, no user-facing "clear audit log" in v1.
3. **User-readable**. Every row has a non-empty `description`.
4. **No silent writes**. Writing to the audit log is the responsibility
   of the component performing the action; the writer API will not
   auto-log unrelated events.

---

## 7. Export

Orbit supports a one-shot, user-initiated, local-only export:

- Triggered from Settings → "Export my data".
- Writes an unencrypted JSON bundle to `Downloads/Orbit-Export-<ts>/`:
  - `envelopes.json`
  - `continuations.json`
  - `results.json`
  - `audit.json`
  - `README.md` — explains format, the 30-day tombstone rule, and that
    the export is not e2e-signed.
- Audit entries `EXPORT_STARTED` and `EXPORT_COMPLETED` are written
  before and after.
- No network involvement. No cloud option in v1.

---

## 8. UI bindings

Settings surface "What Orbit did today":

- Today (default).
- Yesterday / previous 7 days via simple day picker.
- Grouped by action, each group collapsed by default:
  - "23 captures"
  - "41 enrichments (38 succeeded, 3 failed)"
  - "12 network fetches to 8 domains"
  - "7 Nano summaries generated"
  - "2 envelopes archived"
- Tap a group ⇒ chronological list of matching `AuditEntryParcel`s.
- Tap a row ⇒ details drawer showing description + pretty-printed
  `extraJson` + "Open envelope" deep link (if `envelopeId` present).

---

## 9. Tests (Contract)

- `seal()` writes exactly one `ENVELOPE_CREATED` row with the correct
  envelope id, at the same time as the envelope row.
- `reassignIntent()` writes exactly one `INTENT_SUPERSEDED` row.
- Simulated 10 continuation fetches write exactly 10
  `NETWORK_FETCH` + 10 `CONTINUATION_COMPLETED` (or `FAILED`) entries.
- Retention worker removes entries older than 90d and nothing newer.
- `entriesForDay` is bounded at 1000 rows (verify truncation).
- Export produces a valid JSON bundle and the audit "export" rows bracket
  the operation.
- Parcel size remains < 256 KB for a heavy day (enforced in test).
