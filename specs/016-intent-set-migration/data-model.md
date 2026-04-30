# Data Model: Intent Set Migration (Spec 016)

## Current schema (v3)

`intent_envelope` (relevant subset):

| Column | Type | Notes |
|---|---|---|
| `id` | `TEXT PK NOT NULL` | UUID |
| `intent` | `TEXT NOT NULL` | One of `WANT_IT`, `REFERENCE`, `FOR_SOMEONE`, `INTERESTING`, `AMBIGUOUS` |
| `intentSource` | `TEXT NOT NULL` | One of `USER_CHIP`, `PREDICTED_SILENT`, `AUTO_AMBIGUOUS`, `FALLBACK`, `DIARY_REASSIGN` |
| `intentConfidence` | `REAL` | Nullable |
| `intentHistoryJson` | `TEXT NOT NULL` | JSON array of layer objects; defaults to `"[]"` |
| _(other columns unaffected by this migration)_ | | |

Indexes (unchanged): `index_intent_envelope_intent` on `intent`, plus the v2/v3 indexes on `kind`, `day_local`, etc.

`Intent` enum (v3, in `app/src/main/java/com/capsule/app/data/model/Intent.kt`):

```kotlin
enum class Intent {
    WANT_IT,
    REFERENCE,
    FOR_SOMEONE,
    INTERESTING,
    AMBIGUOUS
}
```

## New schema (v4)

`intent_envelope` (delta only):

| Column | Type | Notes |
|---|---|---|
| `intent` | `TEXT NOT NULL` | One of `REMIND_ME`, `INSPIRATION`, `REFERENCE`, `READ_LATER`, `FOR_SOMEONE`, `AMBIGUOUS` |
| `contactRefId` | `TEXT` | **NEW**; nullable; back-fills `NULL` on existing rows. Android `ContactsContract.Contacts.LOOKUP_KEY` (stable across contact-merges) when `contactRefSource ∈ {device_contacts, phone_history}`; `NULL` when `contactRefSource = manual` or when no contact attached. |
| `contactRefName` | `TEXT` | **NEW**; nullable; display name of the referenced contact. `NULL` when no contact attached. |
| `contactRefSource` | `TEXT` | **NEW**; nullable; constrained vocabulary `{manual, device_contacts, phone_history}` enforced via CHECK constraint. `NULL` when no contact attached. |

Two CHECK constraints are added on `intent_envelope` in the same migration:

1. **Source vocabulary**: `contactRefSource IN ('manual','device_contacts','phone_history') OR contactRefSource IS NULL`
2. **Id-source coupling**: `(contactRefId IS NULL) OR (contactRefSource IN ('device_contacts','phone_history'))`

Constraint (2) enforces the invariant that a non-null `contactRefId` (a `LOOKUP_KEY`) only makes sense when sourced from the device's contact store; manually-typed contacts have no stable system id and therefore must have `contactRefId = NULL`.

All other columns unchanged. The existing index on `intent` continues to apply (the migration does not drop or rebuild it; SQLite index entries are updated transparently when the column values are rewritten).

`Intent` enum (v4):

```kotlin
enum class Intent {
    REMIND_ME,
    INSPIRATION,
    REFERENCE,
    READ_LATER,
    FOR_SOMEONE,
    AMBIGUOUS
}
```

## Mapping table (V3 → V4)

| V3 enum | V3 string | V4 enum | V4 string | Action | Audit layer? |
|---|---|---|---|---|---|
| `WANT_IT` | `'WANT_IT'` | `REMIND_ME` | `'REMIND_ME'` | Rename | ✅ |
| `INTERESTING` | `'INTERESTING'` | `INSPIRATION` | `'INSPIRATION'` | Rename | ✅ |
| `REFERENCE` | `'REFERENCE'` | `REFERENCE` | `'REFERENCE'` | No change | ❌ |
| `FOR_SOMEONE` | `'FOR_SOMEONE'` | `FOR_SOMEONE` | `'FOR_SOMEONE'` | No change | ❌ |
| `AMBIGUOUS` | `'AMBIGUOUS'` | `AMBIGUOUS` | `'AMBIGUOUS'` | No change | ❌ |
| _(none)_ | _(none)_ | `READ_LATER` | `'READ_LATER'` | New value | n/a (no v3 rows) |

User-pickable v4 set (chip palettes, in display order): `REMIND_ME`, `INSPIRATION`, `REFERENCE`, `READ_LATER`, `FOR_SOMEONE`. `AMBIGUOUS` is excluded — sentinel only.

## ContactRef shape

New Kotlin data class, **not** a Room entity:

```kotlin
data class ContactRef(
    val id: String?,
    val name: String,
    val source: ContactRefSource,
)

enum class ContactRefSource {
    MANUAL,
    DEVICE_CONTACTS,
    PHONE_HISTORY,
}
```

Lives in `com.capsule.app.data.model` alongside `Intent` (file path: `app/src/main/java/com/capsule/app/data/ContactRef.kt`). Persisted as three nullable columns on `intent_envelope` (`contactRefId`, `contactRefName`, `contactRefSource`) — see "Why columns over a join table" below. At v1 there is no UI write path; the columns are always `NULL` post-migration. The `IntentEnvelopeEntity` Kotlin class gains three nullable fields:

```kotlin
val contactRefId: String? = null,
val contactRefName: String? = null,
val contactRefSource: String? = null,
```

A Kotlin extension function `IntentEnvelopeEntity.contactRef(): ContactRef?` returns a populated `ContactRef` only when `contactRefName` is non-null and `contactRefSource` parses to a known `ContactRefSource` enum value (the name + source pair is the identity anchor; `id` is optional and source-conditional per CHECK constraint 2).

**Field semantics**:

- `id: String?` — Android `ContactsContract.Contacts.LOOKUP_KEY` when `source ∈ {DEVICE_CONTACTS, PHONE_HISTORY}`; `null` when `source = MANUAL`. `LOOKUP_KEY` is the documented stable identifier across contact-merge events on Android, which is critical because the user may merge duplicate contacts after attaching one to a capsule.
- `name: String` — Display name as the user sees it. Always non-null on a populated `ContactRef`.
- `source: ContactRefSource` — Provenance of the reference. Drives downstream behavior (e.g., `MANUAL` may surface a "verify number" prompt; `DEVICE_CONTACTS` and `PHONE_HISTORY` can resolve back to system contact records via `id`).

**AIDL parcel surface**: NOT updated at v1. The capture-sheet contact-picker UI is deferred to spec 017; until then, no IPC consumer needs the parcel fields. When 017 lands, the AIDL parcel addition will be a small additive change.

## Why columns over a join table

The decision is locked: three nullable columns on `intent_envelope`. Rationale:

1. **1:1 cardinality at v1** — one envelope references at most one contact. A join table is overkill.
2. **No hot-path join** — Diary card paging is hot-path code; columns avoid a `LEFT JOIN` per envelope render.
3. **Cheap-grep affordance** — column-level changes are searchable across migrations and call sites without joining query plans into the analysis.
4. **Append-only `intentHistoryJson` precedent** — the existing schema already encodes per-envelope state inline (history layers as JSON in a single row); a sibling triplet of nullable columns matches that established pattern.

> **Forward-pointer**: v2 multi-recipient migration (one envelope referencing N contacts) is a separate spec that will be authored when that requirement actually surfaces — not pre-spec'd here. Such a migration is straightforward (insert `(envelopeId, contactRefId, contactRefName, contactRefSource)` into a new join table, drop the columns, redirect reads).

## Audit history append behavior

`intentHistoryJson` is a JSON array of layer objects. The shape that already exists in v3 is approximately:

```json
[
  {"intent": "WANT_IT", "source": "USER_CHIP", "at": "2025-01-01T00:00:00Z"},
  {"intent": "REFERENCE", "source": "DIARY_REASSIGN", "at": "2025-01-02T00:00:00Z"}
]
```

The migration appends one new layer for renamed rows:

```json
{
  "intent": "REMIND_ME",
  "source": "MIGRATION",
  "at": "2026-04-29T12:34:56Z",
  "migrationReason": "spec-016 intent-set rename",
  "fromIntent": "WANT_IT"
}
```

Notes on the schema choice:

- `source: "MIGRATION"` is a **new** `IntentSource` enum value introduced in this spec (sixth value). It signals that this layer was written by a schema migration, not by a user action or predictor — useful for filtering history in future intent-history UIs.
- `migrationReason` is a free-form string scoped to the migration ID. Future migrations will use distinct reason strings (e.g., `"spec-NNN intent-set rename"`).
- `fromIntent` is the original V3 enum string. Redundant with the previous-array-element's `intent` field in well-formed history, but explicit for forensic clarity and robustness against malformed history (see edge cases in spec.md).
- `at` is the migration's wall-clock execution time formatted as **ISO-8601 UTC via `Instant.toString()`** (e.g., `"2026-04-29T12:34:56Z"`). Single value reused across all rows in the migration run; this is acceptable because per-row precision adds nothing to a same-transaction batch. ISO-8601 matches the existing `intentHistoryJson` layer convention, survives Kotlinx serialization round-trip, and is greppable in DB dumps.

Append-only invariant: the migration **appends** to the existing array. If the existing JSON is malformed (not parsable as a JSON array), the migration emits `Log.w("migration_3_4_malformed_history", row_id)` and replaces the column with a fresh single-element array containing only the rename layer (prior history is lost — locked decision, see spec.md Clarifications session 2026-04-29). Otherwise it never rewrites prior layers. The audit-history preservation test (FR-013) verifies this; the malformed-history fixture in the migration test asserts the destructive-replacement path fires the warning and completes the rename.

## Indexes

No new indexes introduced. The new `contactRef*` columns are not queried by any v1 surface (no UI consumer). When spec 017+ introduces the contact-picker and the "Text Maya?" follow-up, an index on `contactRefId` (for `LOOKUP_KEY` resolution) may be warranted; defer that decision to that spec.

## Constraints

Two CHECK constraints on `intent_envelope` (added by `MIGRATION_3_4`):

1. `contactRefSource IN ('manual','device_contacts','phone_history') OR contactRefSource IS NULL`
2. `(contactRefId IS NULL) OR (contactRefSource IN ('device_contacts','phone_history'))`

SQLite does not support `ALTER TABLE … ADD CONSTRAINT CHECK`; the migration must use the table-rebuild dance (CREATE NEW → INSERT … SELECT → DROP OLD → RENAME) to add the constraints. See `tasks.md` T016-011 for the SQL.

The columns are otherwise independently nullable — a row may have `contactRefName='Maya'` with `contactRefId=NULL` and `contactRefSource='manual'` (manually-typed contact, no system reference). At read time, callers should treat any non-null `contactRefName + contactRefSource` pair as a valid `ContactRef`; `id` is advisory and only meaningful for `device_contacts` / `phone_history` sources.

## Migration verification surface

The migration test asserts:

1. Row count is preserved (no rows added or deleted).
2. Every renamed row has the expected new `intent` value.
3. Every renamed row has a fresh terminal layer in `intentHistoryJson` with the documented shape and an ISO-8601 UTC timestamp from `Instant.toString()`.
4. Every non-renamed row has `intentHistoryJson` byte-identical to its pre-migration value.
5. The three new contact-ref columns (`contactRefId`, `contactRefName`, `contactRefSource`) exist with type `TEXT` and back-fill `NULL` on every row.
6. Both CHECK constraints are present in the post-migration schema (verified via Room's exported `4.json` schema under `tableInfo > checks`, and via negative-case inserts at runtime).
7. A separate "audit-history preservation" test verifies that an envelope with N prior layers in `intentHistoryJson` retains all N layers and gains the rename layer at index N (terminal position).
8. A malformed-history fixture asserts the migration logs `Log.w("migration_3_4_malformed_history", row_id)`, replaces the column with a fresh single-element array, and completes the rename successfully.
9. Two negative-case inserts assert the CHECK constraints fire at runtime: (a) `contactRefSource='invalid_value'` → CHECK violation; (b) `contactRefId='abc' AND contactRefSource='manual'` → CHECK violation.
