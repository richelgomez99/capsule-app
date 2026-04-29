# Research: Intent Set Migration (Spec 016)

## 1. Mapping rationale

The v2 intent set was selected by product (alpha-cohort feedback + founder synthesis) over the v1 four-label set shipped in spec 002. The new set sharpens action-orientation:

- `WANT_IT` → `REMIND_ME`. The old name framed a desire; the new name framing is a personal action item ("remind me to revisit / act on this"). Same semantic neighborhood.
- `INTERESTING` → `INSPIRATION`. Tightens the bucket to "this might inform creative work or thinking" rather than the catch-all "this caught my eye."
- `REFERENCE` → `REFERENCE`. Unchanged — a documentation/lookup bucket.
- `FOR_SOMEONE` → `FOR_SOMEONE`. Unchanged conceptually; gains optional `ContactRef` to enable downstream "Text Maya?" follow-ups (deferred).
- `AMBIGUOUS` → `AMBIGUOUS`. Unchanged; defensive sentinel only, not user-pickable.
- `READ_LATER` is a brand-new bucket. Recovers the long-tail "save and re-read later" use case that was previously absorbed into `INTERESTING`.

User-pickable count: 4 → 5. Total enum values: 5 → 6.

## 2. Lossiness call-outs

### 2a. `INTERESTING → INSPIRATION` is semantically lossy

"Interesting" is a strictly broader bucket than "inspiration." Some existing `INTERESTING` rows almost certainly belong in `READ_LATER` (saved articles a user wanted to re-read) or `REFERENCE` (a useful screenshot for later lookup) rather than `INSPIRATION` in the strict sense. We accept the loss because:

- **The alpha cohort is just starting.** Volume of rows affected is small (single-digit users, low-tens-of-rows each by current estimates).
- **The reclassify-as-layer pattern is the user's escape hatch.** Intent is editable from the Diary at any time; every reassignment writes a new layer to `intentHistoryJson`. A user whose `INSPIRATION` row feels wrong can re-tag it to `READ_LATER` or `REFERENCE` in one tap.
- **Migration writes a layer for every rename**, with `migrationReason: "spec-016 intent-set rename"` and the original label preserved. The audit trail is machine-readable and survives indefinitely.

### 2b. `WANT_IT → REMIND_ME` is a less-lossy rename but still a semantic shift

"Desire" vs "action item" — closely related but not identical. A user who tagged a screenshot of a product they wanted to buy may not immediately read "Remind me" the same way. Treatment is identical to 2a: append a layer with `migrationReason`, preserve the original label, rely on the in-app reassign UI as the escape hatch.

## 3. Why no legacy sentinel value

Considered: introduce `INTERESTING_LEGACY` and `WANT_IT_LEGACY` as enum values that survive the migration so the original semantic intent is still queryable. Rejected because:

- It bloats the enum to eight values with no user-facing surface (legacy values would never appear in any chip palette).
- `intentHistoryJson` already preserves the original label, in machine-readable form, with a timestamp and reason. That's a richer record than a separate enum value would provide.
- Every `when (intent: Intent)` site would need a branch for the legacy values, doubling the cognitive load on every future intent-aware code path.

The audit log is the canonical preservation channel.

## 4. Why no row recategorization beyond renames

Considered: heuristic re-bucketing during migration (e.g., "if `INTERESTING` and `textContent` matches `r/article|read this|saved` then `READ_LATER`, else `INSPIRATION`"). Rejected because:

- Migration code that runs heuristics on user data violates the "predictable, additive, never lossy" migration discipline (002 baseline + constitution).
- Heuristics get a non-trivial fraction wrong; the wrong rows would silently land in the wrong bucket with no signal to the user.
- The reassign-to-rebucket pattern already exists and is one tap. Letting users do their own re-bucketing is correct and respects the editable-intent contract.

So migration is mechanical: rename in place, append audit layer, done.

## 5. `AMBIGUOUS` retained as enum value, removed from chip palette

`AMBIGUOUS` appears in **eleven** code paths today (predictor fallback, silent-wrap predicate guard, auto-ambiguous timeout seal, cloud-LLM parse fallback, DAO query exclusion, etc.). Removing it from the enum cascades through all of them and risks introducing subtle silent-wrap or seal bugs mid-flight. The pragmatic move is:

- **Keep** `AMBIGUOUS` in the enum (defensive sentinel, code-only).
- **Remove** `AMBIGUOUS` from the chip palettes in `ChipRow` and `IntentChipPicker`.
- **Render** `AMBIGUOUS` as `"—"` (em-dash) in every UI surface that shows a label (replaces the old `"Unassigned"` copy).

This also solves the leak of the schema concept "Unassigned" into user vocabulary.

## 6. `ContactRef` shape and storage decision

User-provided shape:

```kotlin
data class ContactRef(
    val displayName: String,
    val phoneE164: String?,
    val email: String?,
)
```

Two storage options were considered. Documented in `data-model.md` § "ContactRef storage trade-off". TL;DR: chose three nullable columns on `intent_envelope` (option A) over a join table (option B) because (a) one envelope owns at most one contact, (b) join queries cost more than three nullable columns at our scale, (c) the schema is simpler to migrate and reason about.

## 7. Cluster engine independence

Verified: `grep -n "Intent\." app/src/main/java/com/capsule/app/cluster/*.kt` returns zero matches (`SimilarityEngine.kt`, `ClusterDetectionWorker.kt`, `ClusterDetector.kt`). Cluster similarity is embedding-driven and oblivious to intent labels. The migration does not affect cluster code paths.

## 8. AIDL surface independence (audit)

`IntentEnvelopeDraftParcel.kt` and `EnvelopeViewParcel.kt` carry `intent` and `intentSource` as `String` — not as enum types. Verified:

- `IntentEnvelopeDraftParcel.kt:10` — `val intent: String,`
- `EnvelopeViewParcel.kt:23` — `val intent: String,`
- `IEnvelopeRepository.aidl` — references intent only in comments, not in the typed surface.

Consequence: the enum rename flows through the parcel boundary as a string-rename, with no `.aidl` file edits required. Adding `ContactRef` parcel fields is also deferred — no consumer at v1 (UI is in spec 017).

## 9. "Unassigned" call-site audit

`grep -rn "Unassigned" app/src/main` returns two matches:

- `app/src/main/java/com/capsule/app/diary/ui/EnvelopeCard.kt:387` — `Intent.AMBIGUOUS -> "Unassigned"`
- `app/src/main/java/com/capsule/app/diary/ui/EnvelopeDetailScreen.kt:496` — `Intent.AMBIGUOUS -> "Unassigned"`

The user's earlier audit also flagged `SilentWrapPill`, `ChipRow`, `IntentChipPicker` — the actual current code uses `"Ambiguous"` (capital A) in `SilentWrapPill.kt:176`, not `"Unassigned"`. Both `"Unassigned"` and `"Ambiguous"` user-visible strings should be normalised to `"—"` for `Intent.AMBIGUOUS` in this migration. The chip pickers (`ChipRow`, `IntentChipPicker`) do not currently render an AMBIGUOUS chip option, so they're affected only by the rename of the four pickable values.

## 10. Spec 015 dependency posture

Spec 015 (`015-visual-refit`) defines the IntentChip token surface (color, font, spacing) that this migration's chip palette consumes. Spec 015 is on its own branch (PR #5) and has not merged to main. Per user direction, spec 016 references spec 015 in prose only — no relative-path links to `specs/015-visual-refit/` until both ship. This avoids merge-time broken links if 015 is renamed or restructured.
