# Data Model: Capture Feedback Actions

## Existing State

`intent_envelope` already has:

- `id`: primary key.
- `textContent`: captured text.
- `textContentSha256`: nullable, currently not populated by the primary seal
  path.
- `sharedContinuationResultId`: optional hydration-result reuse pointer.
- `isArchived`, `isDeleted`, `deletedAt`: visibility/lifecycle flags.

`continuation_result` already has `canonicalUrlHash`, but that is a hydration
result cache. It is not enough for envelope-level dedupe because pending captures
may not have a completed result yet, and because saving a second envelope is a
user-visible duplicate even when hydration reuse works correctly.

## Proposed Additions

### IntentEnvelopeEntity.primaryCanonicalUrlHash

Nullable `TEXT` column on `intent_envelope`.

- Populated when the captured text has at least one URL and Orbit chooses a
  primary URL for duplicate detection.
- Value is produced by `CanonicalUrlHasher.hash(primaryUrl)`.
- Indexed for lookup among non-deleted, non-archived rows.
- `NULL` for non-URL captures.

If a capture has multiple URLs, v1 uses the first URL returned by
`ContinuationEngine.extractUrls(...)` as the primary duplicate key and leaves
multi-URL set matching for a future spec.

### IntentEnvelopeEntity.textContentSha256

Existing nullable `TEXT` column.

- Populate for exact non-URL text captures.
- May also be populated for URL captures for forensics, but URL duplicate
  matching should prefer `primaryCanonicalUrlHash`.

### SealResult

Typed result crossing the repository/Binder boundary.

```text
Created(envelopeId)
AlreadySaved(existingEnvelopeId, matchedBy)
```

`matchedBy` values:

- `CANONICAL_URL`
- `EXACT_TEXT`

### Duplicate Audit Event

Add an audit action distinct from `ENVELOPE_CREATED`, for example
`DUPLICATE_CAPTURE_ATTEMPT`, with bounded metadata:

- `existingEnvelopeId`
- `matchedBy`
- optional hash prefix or redacted hash marker if needed for debugging

Do not log raw captured content or full URLs in audit metadata.

### EnvelopeNoteEntity

New Room entity for notes attached to existing captures.

| Column | Type | Notes |
|---|---|---|
| `id` | `TEXT PK NOT NULL` | UUID |
| `envelopeId` | `TEXT NOT NULL` | FK to `intent_envelope.id`, cascade delete |
| `text` | `TEXT NOT NULL` | User-authored note body |
| `createdAt` | `INTEGER NOT NULL` | Wall-clock millis |
| `updatedAt` | `INTEGER` | Null until edited |

V1 behavior: the `Already saved` note action creates or edits the latest note for
the existing envelope. A future richer notes UI can support multiple notes per
envelope without changing the duplicate capture contract.

## Lookup Rules

1. Ignore deleted envelopes.
2. Ignore archived envelopes for v1 unless product copy explicitly offers to
   restore/open archived captures.
3. Prefer URL duplicate matching when `primaryCanonicalUrlHash` is present.
4. Fall back to exact text hash matching when no primary URL exists.
5. Existing undo window behavior applies only to newly created envelopes.

## Migration Notes

- Add `primaryCanonicalUrlHash` with a forward-only Room migration.
- Add `envelope_note` with a forward-only Room migration.
- Backfill `textContentSha256` for existing rows where feasible.
- Backfill `primaryCanonicalUrlHash` for existing text rows with detectable URLs
  if the migration can safely reuse the existing URL extraction/canonicalization
  logic outside Android framework dependencies; otherwise document no backfill
  and let new captures populate it going forward.