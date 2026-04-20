# Envelope Repository Contract: Intent Envelope and Diary

**Feature Branch**: `002-intent-envelope-and-diary`
**Date**: 2026-04-16
**Owning process**: `:ml`
**Callers**: `:capture` (seal path), `:ui` (read + mutate paths)

---

## 1. Purpose

The `EnvelopeRepository` is the **only** interface into the Orbit corpus.
Every other process communicates with it via an Android bound service
(binder IPC). The Room DB is opened in `:ml` alone and never handed to
another process; only typed results cross the boundary.

Principle enforced: **VI (Privilege Separation By Design)**. No caller
outside `:ml` can execute an arbitrary SQL query — the surface below is
exhaustive.

---

## 2. Service

```xml
<service
    android:name=".data.ipc.EnvelopeRepositoryService"
    android:process=":ml"
    android:exported="false" />
```

**Action**: `com.capsule.app.action.BIND_ENVELOPE_REPOSITORY`
**Returns**: `IEnvelopeRepository` AIDL binder (see §3).
**Auth**: `android:exported="false"` — only the Orbit app (any of its
processes) can bind. Orbit does not accept external binders.

---

## 2.1 Storage backend abstraction (forward-compatible)

`EnvelopeRepositoryImpl` delegates every persistence operation to an
injected `EnvelopeStorageBackend` interface (spec 002 tasks T025c,
T025d). The AIDL surface (§3) is backend-agnostic: callers never know
which backend is active.

Backends registered by release:

| Release | Backend | Role |
|---|---|---|
| v1   | `LocalRoomBackend`       | Sole backend. All reads and writes hit the on-device encrypted Room DB in `:ml`. |
| v1.1 | `OrbitCloudBackend`      | Added alongside `LocalRoomBackend` when the user opts into Orbit Cloud (spec 006). Writes go to Room first (source of truth); a mirror job enqueues the cloud write via `:net`. Reads fall back to cloud only for queries the local last-N-days projection cannot answer (e.g., KG traversal, multi-device corpus search). |
| v1.3 | `ByocPostgresBackend`    | Added when the user opts into BYOC (spec 009). Schema-identical to Orbit Cloud so the router can swap backends losslessly. |

Backend selection is a **runtime router**, not a subclass hierarchy.
At most one cloud backend is active at a time (Orbit Cloud XOR BYOC);
`LocalRoomBackend` is always active. The router is configured in
`:ml` at process start from persisted user preferences and never
crosses the AIDL surface.

**Invariants (enforced for every release)**:

1. Every write path starts at `LocalRoomBackend`. A cloud write is
   never the first write.
2. Cloud writes cross the `:ml` → `:net` boundary via a typed mirror
   job; `:ml` never opens a socket.
3. The AIDL surface (§3) remains unchanged across v1 → v1.1 → v1.3.
   Adding a cloud backend is additive — no caller recompiles, no
   binder signature changes.
4. The audit log and consent ledger (constitution Principle X
   non-negotiable condition #2) remain `LocalRoomBackend`-only in
   every release. They have no corresponding cloud write path.
5. Any payload destined for a cloud backend MUST have passed the
   `:agent` consent filter (constitution Principle XI) before the
   mirror job enters `:net`. `EnvelopeRepositoryImpl` rejects
   unfiltered payloads with `CONSENT_FILTER_REQUIRED`.

---

## 3. AIDL Interface

```aidl
// IEnvelopeRepository.aidl
package com.capsule.app.data.ipc;

import com.capsule.app.data.ipc.IntentEnvelopeDraftParcel;
import com.capsule.app.data.ipc.StateSnapshotParcel;
import com.capsule.app.data.ipc.EnvelopeViewParcel;
import com.capsule.app.data.ipc.DayPageParcel;
import com.capsule.app.data.ipc.IEnvelopeObserver;

interface IEnvelopeRepository {

    // ---- Seal path (called by :capture) ----

    // Seals a fresh capture as a new envelope. Returns envelope id.
    String seal(
        in IntentEnvelopeDraftParcel draft,
        in StateSnapshotParcel state
    );

    // ---- Read path (called by :ui) ----

    // Observes a single day. Pushes on initial load and on any mutation.
    void observeDay(
        String isoDate,                    // yyyy-MM-dd in user tz
        IEnvelopeObserver observer         // callback binder
    );

    // Stops observation started by observeDay with the same observer.
    void stopObserving(IEnvelopeObserver observer);

    // Loads a single envelope by id (for deep-link / expanded view).
    EnvelopeViewParcel getEnvelope(String envelopeId);

    // ---- Mutate path (called by :ui) ----

    // User reassigns intent from Diary. Append-only history; audit logged.
    void reassignIntent(String envelopeId, String newIntentName, String reasonOpt);

    // User archives an envelope. Audit logged.
    void archive(String envelopeId);

    // User deletes an envelope. Soft-delete with 30-day tombstone. Audit logged.
    void delete(String envelopeId);

    // Undo within 10s of seal (per FR-008).
    boolean undo(String envelopeId);   // returns false if outside undo window

    // ---- Diagnostics (called by Settings) ----

    // Basic counts for Settings + self-dogfood.
    int countAll();
    int countArchived();
    int countDeleted();
}
```

Parcel classes are straightforward `Parcelable` wrappers around the
entity and view types defined in `data-model.md`. The full field layouts
live in the implementing module.

---

## 4. Pre/Postconditions

### `seal(draft, state)`

**Preconditions**:
- `draft.contentType == TEXT` ⇒ `draft.textContent != null`
- `draft.contentType == IMAGE` ⇒ `draft.imageUri != null`
- `draft.intent != null` (AMBIGUOUS is valid)
- `state.tzId` is a valid IANA identifier
- Repository has opened the encrypted Room DB

**Postconditions**:
- Exactly one row inserted into `intent_envelope`
- Exactly one `ENVELOPE_CREATED` audit entry written
- If `draft.textContent` is a URL (detected by regex), exactly one
  `Continuation` row in status `PENDING` for `URL_HYDRATE`
- If `draft.contentType == IMAGE`, exactly one `Continuation` row in
  status `PENDING` for the OCR → URL-extract path (which itself enqueues
  `URL_HYDRATE` on any URLs found)
- All writes occur in a single Room `withTransaction { }` block
- Returned id equals the persisted `IntentEnvelopeEntity.id`

**Failure modes**:
- `IllegalArgumentException` on invariant violation
- `SQLiteException` on DB corruption (caller should retry once, then
  surface to user as "Orbit needs repair")
- Repository MUST NOT throw for network/Nano failures at seal time —
  those are deferred to continuations

### `observeDay(isoDate, observer)`

**Preconditions**:
- `isoDate` matches `yyyy-MM-dd`; otherwise silent no-op

**Postconditions**:
- Observer receives an initial `onDayLoaded(DayPageParcel)` within 1s
  on first call (SC-004 p95 ≤ 1s)
- Observer receives `onDayLoaded(DayPageParcel)` again on any mutation
  to any envelope on that day
- Multiple observers for different dates are independent

**Teardown**: caller must call `stopObserving(observer)` on unbind.
The repository weak-refs observer binders and cleans up if the caller
process dies.

### `reassignIntent(envelopeId, newIntentName, reasonOpt)`

**Preconditions**:
- Envelope exists and is not deleted
- `newIntentName` is a valid `Intent` enum name

**Postconditions**:
- Envelope's `intentHistoryJson` gains one entry
- Envelope's `intent` column equals the new intent
- Exactly one `INTENT_SUPERSEDED` audit entry written
- Observers on the envelope's day receive a refreshed `DayPageParcel`

### `archive(envelopeId)` / `delete(envelopeId)` / `undo(envelopeId)`

**Archive postconditions**: `isArchived = true`; envelope excluded
from default Diary; audit `ENVELOPE_ARCHIVED`.

**Delete postconditions**: `isDeleted = true`; envelope tombstone;
audit `ENVELOPE_DELETED`. Hard-deleted by retention-sweep after 30 days.

**Undo**: atomic reverse of `seal` **iff** `now - createdAt < 10_000 ms`.
Removes envelope row, its continuations, the `ENVELOPE_CREATED` audit
entry is marked with a follow-up `envelope_undone` entry (we do not
delete audit entries). Returns `true` on success, `false` if outside
window.

---

## 5. Observer Binder

```aidl
// IEnvelopeObserver.aidl
package com.capsule.app.data.ipc;

import com.capsule.app.data.ipc.DayPageParcel;

oneway interface IEnvelopeObserver {
    void onDayLoaded(in DayPageParcel page);
    void onRepositoryError(String reason);
}
```

`oneway` — observers never block the repository.

---

## 6. Concurrency

- **One Room DB instance** in `:ml` for the lifetime of the process
- `seal()` is thread-safe; repository uses a single-writer coroutine
  dispatcher backed by `Dispatchers.IO` + a mutex per envelope id
- `observeDay()` implementation uses Room's `Flow` internally and
  adapts to the observer binder; debounced at 150ms to avoid storming
  the `:ui` process on burst writes

---

## 7. Serialization & Size Budget

- Per-envelope parcel ≤ 8 KB (enforced at test time); images referenced
  by URI, not bytes
- Day parcel ≤ 256 KB for a "heavy" day (~50 envelopes)
- Cross-process calls use `Parcelable`, never `Serializable`
- No Room entity is passed across processes directly — only parcel DTOs

---

## 8. Error Conditions

| Condition | Repository behavior |
|---|---|
| DB open failure (wrong key, corruption) | Throw `RepositoryUnavailableException`; service returns `TRANSACTION_FAILED` to binder caller |
| Unknown envelope id on mutate | Throw `NoSuchEnvelopeException`; caller surfaces "Envelope no longer available" |
| Observer binder died | Drop silently at next mutation; log at DEBUG |
| Repository hit by too-many observers (> 16 simultaneous) | Reject new observers with `TooManyObserversException` |

---

## 9. Tests (Contract)

- `seal` happy path + every failure mode: instrumented test with real
  encrypted Room DB (in-memory SQLCipher).
- `observeDay` + concurrent `seal`: verify observer receives update
  within 500ms of mutation.
- `reassignIntent`: verify history append and audit log atomicity.
- `undo` within/outside window: boundary test.
- Binder death during observation: verify cleanup.
- `seal` under SQLCipher re-key (future): deferred to Phase 2.
