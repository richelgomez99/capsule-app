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
| `contactRefDisplayName` | `TEXT` | **NEW**; nullable; back-fills NULL on existing rows |
| `contactRefPhoneE164` | `TEXT` | **NEW**; nullable; back-fills NULL |
| `contactRefEmail` | `TEXT` | **NEW**; nullable; back-fills NULL |

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
    val displayName: String,
    val phoneE164: String?,
    val email: String?,
)
```

Lives in `com.capsule.app.data.model` alongside `Intent`. Persisted as three nullable columns on `intent_envelope` (see "ContactRef storage trade-off" below). At v1 there is no UI write path; the columns are always `NULL` post-migration. The `IntentEnvelopeEntity` Kotlin class gains three nullable fields:

```kotlin
val contactRefDisplayName: String? = null,
val contactRefPhoneE164: String? = null,
val contactRefEmail: String? = null,
```

A Kotlin extension function `IntentEnvelopeEntity.contactRef(): ContactRef?` returns a populated `ContactRef` only when `contactRefDisplayName` is non-null (the display name is the contact's identity anchor; phone and email are optional contact channels).

## Audit history append behavior

`intentHistoryJson` is a JSON array of layer objects. The shape that already exists in v3 is approximately:

```json
[
  {"intent": "WANT_IT", "source": "USER_CHIP", "at": 1735689600000},
  {"intent": "REFERENCE", "source": "DIARY_REASSIGN", "at": 1735776000000}
]
```

The migration appends one new layer for renamed rows:

```json
{
  "intent": "REMIND_ME",
  "source": "MIGRATION",
  "at": <migration_run_timestamp>,
  "migrationReason": "spec-016 intent-set rename",
  "fromIntent": "WANT_IT"
}
```

Notes on the schema choice:

- `source: "MIGRATION"` is a **new** `IntentSource` enum value introduced in this spec (sixth value). It signals that this layer was written by a schema migration, not by a user action or predictor — useful for filtering history in future intent-history UIs.
- `migrationReason` is a free-form string scoped to the migration ID. Future migrations will use distinct reason strings (e.g., `"spec-NNN intent-set rename"`).
- `fromIntent` is the original V3 enum string. Redundant with the previous-array-element's `intent` field in well-formed history, but explicit for forensic clarity and robustness against malformed history (see edge cases in spec.md).
- `at` is the migration's wall-clock execution time (`System.currentTimeMillis()`). Single value reused across all rows in the migration run; this is acceptable because per-row precision adds nothing to a same-transaction batch.

Append-only invariant: the migration **appends** to the existing array (or replaces with a fresh single-element array if the existing JSON is malformed; see spec.md edge cases). It never rewrites prior layers. The audit-history preservation test (FR-013) verifies this.

## ContactRef storage trade-off (Option A vs Option B)

Two options were considered for persisting `ContactRef`:

### Option A — Three nullable columns on `intent_envelope` (CHOSEN)

```sql
ALTER TABLE intent_envelope ADD COLUMN contactRefDisplayName TEXT;
ALTER TABLE intent_envelope ADD COLUMN contactRefPhoneE164 TEXT;
ALTER TABLE intent_envelope ADD COLUMN contactRefEmail TEXT;
```

**Pros**:

- One envelope = at most one contact (1:1 FK in spirit). A join table is overkill.
- Simpler migration (additive `ALTER TABLE`, no new table or constraints).
- Single-row reads stay single-row (no `LEFT JOIN` cost on every Diary card render).
- No referential-integrity machinery to maintain (an envelope owning a contact is just three columns).

**Cons**:

- The three columns are sparsely populated (only `FOR_SOMEONE` envelopes ever set them), wasting a few bytes per non-FOR_SOMEONE row. SQLite's variable-length encoding makes this near-zero in practice.
- If contacts later need to be reused across envelopes (e.g., a single canonical Maya record referenced by 50 envelopes), this denormalised storage requires a follow-up migration.

### Option B — Separate `contact_ref` join table (REJECTED for v1)

```sql
CREATE TABLE contact_ref (
    id TEXT PK NOT NULL,
    envelopeId TEXT NOT NULL,
    displayName TEXT NOT NULL,
    phoneE164 TEXT,
    email TEXT,
    FOREIGN KEY(envelopeId) REFERENCES intent_envelope(id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX idx_contact_ref_envelopeId ON contact_ref(envelopeId);
```

**Pros**:

- Clean separation; if contacts become first-class (with their own ID, used across envelopes), the table is already there.
- Future-proof for an address-book integration that wants its own canonical contact records.

**Cons**:

- Requires `LEFT JOIN` on every read, or a second query. Diary card paging is hot-path code; an extra query per envelope is non-trivial.
- New table means new DAO, new migration step, new test surface.
- Premature: at v1 we have no plan to reuse contacts across envelopes.

### Decision

**Option A.** The 1:1 cardinality, the simplicity of additive `ALTER TABLE`, and the read-path performance argument all favour columns. If a future spec promotes contacts to first-class entities, a follow-up migration can extract the columns into a join table — that migration is straightforward (insert `(envelopeId, contactRefDisplayName, ...)` into the new table, drop the columns, redirect reads). The cost of the future migration is acceptable; the cost of premature normalisation today is not.

## Indexes

No new indexes introduced. The new `contactRef*` columns are not queried by any v1 surface (no UI consumer). When spec 017+ introduces the contact-picker and the "Text Maya?" follow-up, an index on `contactRefPhoneE164` may be warranted; defer that decision to that spec.

## Constraints

No new constraints. The contact-ref columns are independently nullable — a row may have `contactRefDisplayName='Maya'` with both phone and email NULL (user only entered a name). At read time, callers should treat any non-null `contactRefDisplayName` as a valid `ContactRef`; phone/email are advisory channels.

## Migration verification surface

The migration test asserts:

1. Row count is preserved (no rows added or deleted).
2. Every renamed row has the expected new `intent` value.
3. Every renamed row has a fresh terminal layer in `intentHistoryJson` with the documented shape.
4. Every non-renamed row has `intentHistoryJson` byte-identical to its pre-migration value.
5. The three new contact-ref columns exist with type `TEXT` and back-fill `NULL` on every row.
6. A separate "audit-history preservation" test verifies that an envelope with N prior layers in `intentHistoryJson` retains all N layers and gains the rename layer at index N (terminal position).
