# Data Model: Intent Set Extension

## Current Schema

`intent_envelope.intent` is stored as `TEXT NOT NULL`. The current durable values are:

```kotlin
enum class Intent {
    WANT_IT,
    REFERENCE,
    FOR_SOMEONE,
    INTERESTING,
    AMBIGUOUS
}
```

## Target Intent Set

Spec 016 extends the set by adding `READ_LATER`. It does not rename existing values.

```kotlin
enum class Intent {
    WANT_IT,
    REFERENCE,
    READ_LATER,
    FOR_SOMEONE,
    INTERESTING,
    AMBIGUOUS
}
```

Display labels:

| Stored value | Display label | User-pickable? | Notes |
|---|---|---:|---|
| `WANT_IT` | `Want it` | Yes | Preserved product label. |
| `REFERENCE` | `Reference` | Yes | Preserved product label. |
| `READ_LATER` | `Read later` | Yes | New value for captures the user wants to consume later. |
| `FOR_SOMEONE` | `For someone` | Yes | Preserved product label; future ContactRef consumer. |
| `INTERESTING` | `Interesting` | Yes | Preserved product label. |
| `AMBIGUOUS` | Existing fallback display | No | Defensive sentinel for timeout/fallback/unrecognized parse. |

## Migration Semantics

No existing `intent` value is rewritten by spec 016. In particular:

| Existing value | Post-upgrade value | Action |
|---|---|---|
| `WANT_IT` | `WANT_IT` | Preserve. |
| `REFERENCE` | `REFERENCE` | Preserve. |
| `FOR_SOMEONE` | `FOR_SOMEONE` | Preserve. |
| `INTERESTING` | `INTERESTING` | Preserve. |
| `AMBIGUOUS` | `AMBIGUOUS` | Preserve. |

Because there is no label rename, spec 016 does not append `intentHistoryJson` migration layers for intent labels.

## ContactRef Shape

`ContactRef` is a value object, not a Room entity:

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

Persisted columns on `intent_envelope` when schema work lands:

| Column | Type | Notes |
|---|---|---|
| `contactRefId` | `TEXT` nullable | Android `ContactsContract.Contacts.LOOKUP_KEY` when source is `device_contacts` or `phone_history`; null for `manual` and absent contacts. |
| `contactRefName` | `TEXT` nullable | Display name. Null means no contact attached. |
| `contactRefSource` | `TEXT` nullable | One of `manual`, `device_contacts`, `phone_history`; null means no contact attached. |

CHECK constraints:

1. `contactRefSource IN ('manual','device_contacts','phone_history') OR contactRefSource IS NULL`
2. `(contactRefId IS NULL) OR (contactRefSource IN ('device_contacts','phone_history'))`

## Why Columns Over A Join Table

The v1 `FOR_SOMEONE` flow references at most one contact per envelope, so three nullable columns match the current cardinality, avoid hot-path joins in Diary paging, and keep the migration easy to inspect. Multi-recipient support should get a separate future spec.

## Verification Surface

Tests for this spec should assert:

1. Existing rows keep their intent values after any schema migration.
2. `READ_LATER` serializes/deserializes through the same string paths as other intents.
3. App label resolvers and chip palettes cover all six enum values.
4. Cloud classifier allowlists accept the six Android labels and reject anything else to `AMBIGUOUS`.
5. Contact-ref columns, if added, are nullable, back-fill `NULL`, and enforce both CHECK constraints.
