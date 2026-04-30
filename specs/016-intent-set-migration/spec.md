# Feature Specification: Intent Set Migration (v1 → v2 labels + ContactRef)

**Feature Branch**: `016-intent-set-migration`
**Created**: 2026-04-29
**Status**: Draft (planning only)
**Input**: Rename four shipped intent labels to the v2 set, add `READ_LATER`, add nullable `ContactRef` for `FOR_SOMEONE`, retire the user-facing `"Unassigned"` copy in favour of `"—"`. No row recategorization beyond renames; sentinel `AMBIGUOUS` retained but removed from the chip palette.

## Clarifications

### Session 2026-04-29

- Q: Should `ContactRef` be 3 nullable cols on `intent_envelope` or a separate join table? → A: 3 nullable TEXT cols on `intent_envelope` (1:1 cardinality at v1; no hot-path join; cheap-grep affordance; precedent matches `intentHistoryJson`'s append-only single-row design). FR-005 column shape REVISED to source-keyed: `contactRefId TEXT`, `contactRefName TEXT`, `contactRefSource TEXT` with constrained vocabulary `{manual, device_contacts, phone_history}` enforced via Room CHECK constraint. `contactRefId` holds Android `ContactsContract.Contacts.LOOKUP_KEY` (stable across contact-merges) when source is `device_contacts` or `phone_history`; null when source is `manual`. CHECK constraint enforces: `contactRefId NOT NULL ⇔ contactRefSource IN ('device_contacts','phone_history')`. v2 multi-recipient migration → separate spec when that requirement actually surfaces; not pre-spec'd here.
- Q: Migration audit-layer timestamp format? → A: ISO-8601 UTC via `Instant.toString()` (e.g., `"2026-04-29T12:34:56Z"`). Matches existing `intentHistoryJson` layer convention; survives Kotlinx serialization round-trip; greppable in DB dumps.
- Q: Malformed `intentHistoryJson` behavior — destructive replacement is acceptable? → A: Locked. Malformed history is REPLACED with a fresh single-element array containing only the rename layer (prior history lost). `Log.w("migration_3_4_malformed_history", row_id)` emitted per occurrence. Migration test MUST include one malformed-history fixture asserting the warning fires and the rename completes successfully.
- Q: "—" character — em-dash (U+2014) vs en-dash (U+2013)? → A: **Em-dash U+2014** confirmed. Grep gate added to FR-007: `grep -rn '"—"' app/src/main` shows em-dash only; U+2013 must not appear as a label substitute.
- Q: "Unassigned" sweep scope — which file trees? → A: Sweep covers all of `app/src/main/**` INCLUDING `app/src/main/res/values*/strings.xml` (all locale variants) AND `app/src/main/aidl/**/*.aidl`. EXCLUDES `app/src/test/**` and `app/src/androidTest/**` (test fixtures may legitimately reference the historical string for migration testing). Grep gates: `grep -rn "Unassigned" app/src/main` returns zero; `grep -rn "Unassigned" app/src/test app/src/androidTest` may return matches and that is allowed.

## User Scenarios & Testing *(mandatory)*

### User Story 1 — Existing alpha user upgrades the app (Priority: P1)

An alpha-cohort user with already-saved capsules updates to a build containing this migration. On launch, Room runs `MIGRATION_3_4`, every existing row's intent label is renamed in place (`WANT_IT → REMIND_ME`, `INTERESTING → INSPIRATION`), and an audit layer is appended to `intentHistoryJson` documenting the rename. No data is lost; no row is reassigned to a different semantic bucket. The Diary renders the same envelopes with new labels.

**Why this priority**: This is the only user-visible behavior of the migration. If it breaks, alpha users lose data — the worst possible outcome for a pre-launch product.

**Independent Test**: Drop a v3 fixture database with one envelope per legacy label (`WANT_IT`, `INTERESTING`, `REFERENCE`, `FOR_SOMEONE`, `AMBIGUOUS`), run the migration, assert (a) the row count is unchanged, (b) the new `intent` column values are `REMIND_ME`, `INSPIRATION`, `REFERENCE`, `FOR_SOMEONE`, `AMBIGUOUS`, and (c) every renamed row has a fresh layer in `intentHistoryJson` with `migrationReason: "spec-016 intent-set rename"` preserving the original label.

**Acceptance Scenarios**:

1. **Given** a v3 envelope `intent='WANT_IT'`, **When** `MIGRATION_3_4` runs, **Then** the row's `intent` is `'REMIND_ME'` and `intentHistoryJson` ends with a layer recording `from='WANT_IT'`, `to='REMIND_ME'`, `reason='spec-016 intent-set rename'`.
2. **Given** a v3 envelope `intent='INTERESTING'`, **When** `MIGRATION_3_4` runs, **Then** the row's `intent` is `'INSPIRATION'` with the analogous audit layer.
3. **Given** a v3 envelope `intent='AMBIGUOUS'`, **When** `MIGRATION_3_4` runs, **Then** the row's `intent` is unchanged and **no** audit layer is appended (no rename occurred).
4. **Given** a v3 envelope `intent='REFERENCE'` or `intent='FOR_SOMEONE'`, **When** `MIGRATION_3_4` runs, **Then** the row's `intent` is unchanged and no audit layer is appended.

---

### User Story 2 — Capture chip palette uses the new five labels (Priority: P1)

When a user takes a screenshot and sees the chip row (or opens the Diary intent picker), the four legacy chips are replaced by the five new user-pickable labels: `Remind me`, `Inspiration`, `Reference`, `Read later`, `For someone`. `AMBIGUOUS` is no longer offered as a chip; it remains as a defensive sentinel produced only by the auto-ambiguous path (timeout, predictor failure, unrecognized parse).

**Why this priority**: The chip palette is the user's primary interaction with the intent set on every save. If chips don't match the spec, every save is mis-tagged.

**Independent Test**: Render `ChipRow` and `IntentChipPicker` Composables in a preview/instrumented test; assert the rendered chips are exactly the five user-pickable labels in the documented order, and that no chip carries `Intent.AMBIGUOUS`.

**Acceptance Scenarios**:

1. **Given** the chip row is shown after a screenshot, **When** the user inspects the available chips, **Then** the row displays exactly `Remind me`, `Inspiration`, `Reference`, `Read later`, `For someone` (5 chips).
2. **Given** the Diary intent reassign picker is opened on an envelope, **When** the user inspects the picker, **Then** the same five labels are offered and the currently-selected one is highlighted (or none, if the row is `AMBIGUOUS`).

---

### User Story 3 — `"Unassigned"` is rendered as `"—"` everywhere (Priority: P2)

Every UI surface that previously displayed `"Unassigned"` for an `AMBIGUOUS` envelope now displays the em-dash `"—"`. This is a copy-only sweep across the Diary card, Diary detail screen, silent-wrap pill, and any other surface that resolves the intent label.

**Why this priority**: Lower priority than P1 because it's cosmetic, but bundled into this migration because (a) it's a one-line change per call-site, (b) it removes the implementation-leak word "unassigned" from the user vocabulary in lockstep with the new label set.

**Independent Test**: `grep -rn "Unassigned" app/src/main` returns zero matches after the change; render previews of an `AMBIGUOUS` envelope in `EnvelopeCard`, `EnvelopeDetailScreen`, and `SilentWrapPill` and assert the displayed label is `—`.

**Acceptance Scenarios**:

1. **Given** an envelope with `intent=AMBIGUOUS`, **When** `EnvelopeCard` is rendered, **Then** the label area displays `—` (em-dash).
2. **Given** an envelope with `intent=AMBIGUOUS`, **When** `EnvelopeDetailScreen` is rendered, **Then** the label displays `—`.
3. **Given** the source tree, **When** `grep -rn "Unassigned" app/src/main` runs, **Then** no matches are returned.

---

### User Story 4 — `ContactRef` schema columns exist on `intent_envelope` (Priority: P3)

The entity gains three nullable columns — `contactRefId`, `contactRefName`, `contactRefSource` — for forward-compatibility with the `FOR_SOMEONE` follow-up flow. No UI writes to these columns yet (capture-sheet contact-picker is deferred to spec 017). Migration adds the columns; existing rows back-fill `NULL` on all three.

`contactRefSource` is a constrained vocabulary enforced at the schema layer via Room CHECK constraint. Allowed values for v1: `manual` (user typed/pasted), `device_contacts` (resolved against the contact picker), `phone_history` (recent calls/messages). `contactRefId` stores Android `ContactsContract.Contacts.LOOKUP_KEY` (stable across contact-merges) when source ∈ `{device_contacts, phone_history}`; `NULL` when source = `manual`. A second CHECK constraint enforces: `contactRefId NOT NULL ⇔ contactRefSource IN ('device_contacts','phone_history')`.

**Why this priority**: Schema-only at v1; user value lands in spec 017+. Bundled into this migration so future schema bumps don't have to touch the intent set again.

**Independent Test**: Inspect the post-migration schema; assert the three columns exist with type `TEXT` and `DEFAULT NULL` and that both CHECK constraints are present. Insert a v3-fixture envelope with `intent='FOR_SOMEONE'`; assert the three columns are `NULL` post-migration. Insert a v4 row with `contactRefSource='manual'` + `contactRefId='abc'` and assert the second CHECK constraint rejects it.

**Acceptance Scenarios**:

1. **Given** the v4 schema, **When** `PRAGMA table_info(intent_envelope)` is queried, **Then** the result includes columns `contactRefId TEXT`, `contactRefName TEXT`, `contactRefSource TEXT`, all nullable.
2. **Given** the v4 schema, **When** the table DDL is inspected, **Then** two CHECK constraints exist: (a) `contactRefSource IN ('manual','device_contacts','phone_history') OR contactRefSource IS NULL`, (b) `(contactRefId IS NULL) OR (contactRefSource IN ('device_contacts','phone_history'))`.
3. **Given** a `FOR_SOMEONE` envelope post-migration, **When** the row is read, **Then** all three contact-ref columns are `NULL` (no fabricated data).
4. **Given** a v4 row insert with `contactRefSource='manual'` and `contactRefId='abc'`, **When** the insert runs, **Then** SQLite raises a CHECK constraint violation.

---

### Edge Cases

- **Unknown legacy label string in DB**: Should not happen (only the five known values are emitted by current code), but if encountered, the migration treats it as a no-op (leaves the value unchanged) rather than failing the upgrade. Cloud round-trip later would surface it as `AMBIGUOUS` via `toIntentOrAmbiguous()`.
- **Empty `intentHistoryJson`** (`""` or `null`): Treated as `"[]"` before appending the rename layer; final value is a single-element JSON array.
- **Malformed `intentHistoryJson`** (not parsable as JSON array): Migration emits `Log.w("migration_3_4_malformed_history", row_id)` and replaces with a fresh single-element array containing only the rename layer, preserving the rename guarantee at the cost of losing any prior history. The migration test MUST include a malformed-history fixture asserting both the warning fires and the rename completes successfully. (Locked 2026-04-29 — see Clarifications.)
- **A row already migrated** (e.g., re-run scenario): The migration is gated by Room's version table; not re-runnable. No idempotency concern.
- **A row with `intent='AMBIGUOUS'` and a non-empty history**: History preserved as-is; no new layer appended (no rename happened).
- **`READ_LATER` value is never present in v3 data**: Correct — it's a brand-new label introduced post-migration. Migration writes no `READ_LATER` rows.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The `Intent` enum MUST contain exactly six values: `REMIND_ME`, `INSPIRATION`, `REFERENCE`, `READ_LATER`, `FOR_SOMEONE`, `AMBIGUOUS`. Five user-pickable + one defensive sentinel.
- **FR-002**: `MIGRATION_3_4` MUST rename every `intent_envelope.intent` value from `'WANT_IT' → 'REMIND_ME'` and `'INTERESTING' → 'INSPIRATION'`. `'REFERENCE'`, `'FOR_SOMEONE'`, `'AMBIGUOUS'` MUST be left unchanged.
- **FR-003**: For each row whose `intent` is renamed by FR-002, the migration MUST append a new layer to `intentHistoryJson` recording the original label, the new label, the migration reason `"spec-016 intent-set rename"`, and a timestamp in **ISO-8601 UTC format via `Instant.toString()`** (e.g., `"2026-04-29T12:34:56Z"`).
- **FR-004**: Rows whose `intent` is unchanged (`REFERENCE`, `FOR_SOMEONE`, `AMBIGUOUS`) MUST NOT receive a new history layer.
- **FR-005**: `MIGRATION_3_4` MUST add three nullable `TEXT` columns to `intent_envelope`: `contactRefId`, `contactRefName`, `contactRefSource`. All existing rows back-fill `NULL` on all three. Two CHECK constraints MUST be added in the same migration: (a) `contactRefSource IN ('manual','device_contacts','phone_history') OR contactRefSource IS NULL` (constrained vocabulary), (b) `(contactRefId IS NULL) OR (contactRefSource IN ('device_contacts','phone_history'))` (id-source coupling). `contactRefId` is the Android `ContactsContract.Contacts.LOOKUP_KEY` when source ∈ `{device_contacts, phone_history}`; null otherwise.
- **FR-006**: The chip palettes in `ChipRow` (overlay) and `IntentChipPicker` (Diary reassign) MUST render exactly the five user-pickable labels in the documented order (Remind me, Inspiration, Reference, Read later, For someone). `AMBIGUOUS` MUST NOT appear as a chip.
- **FR-007**: All UI surfaces that resolve a label for `Intent.AMBIGUOUS` MUST display `"—"` (em-dash, **U+2014**) instead of `"Unassigned"`. Sweep scope: all of `app/src/main/**` INCLUDING `app/src/main/res/values*/strings.xml` (all locale variants) AND `app/src/main/aidl/**/*.aidl`; EXCLUDING `app/src/test/**` and `app/src/androidTest/**` (test fixtures may legitimately reference the historical string). Grep gates: `grep -rn "Unassigned" app/src/main` returns zero matches; en-dash U+2013 MUST NOT appear as a label substitute (`grep -rn '"–"' app/src/main` returns zero matches in label-resolver call sites).
- **FR-008**: Every `when (intent: Intent)` exhaustive branch and every string-keyed label resolver in `app/src/main/**` MUST cover all six new enum values without falling through to `AMBIGUOUS` for renamed labels.
- **FR-009**: `Intent.valueOf(...)` and `toIntentOrAmbiguous()` MUST continue to defensive-fallback to `AMBIGUOUS` on unrecognized strings (e.g., a stale cloud response with an old label name).
- **FR-010**: `OrbitDatabase.version` MUST be bumped from 3 to 4 and `app/schemas/com.capsule.app.data.OrbitDatabase/4.json` MUST be generated and committed.
- **FR-011**: `MIGRATION_3_4` MUST be registered in the database builder's `addMigrations(...)` call alongside `MIGRATION_1_2` and `MIGRATION_2_3`.
- **FR-012**: A migration test (`OrbitDatabaseMigrationV3toV4Test`) MUST exercise V3→V4 with golden fixtures covering one row per legacy label and assert the FR-002, FR-003, FR-004, and FR-005 outcomes.
- **FR-013**: An audit-history preservation test MUST verify that an envelope with prior layers in `intentHistoryJson` retains those layers and gains the rename layer at the end (not at the start).

### Key Entities

- **`Intent` enum** — Six values; five user-pickable, one sentinel. Stored in `intent_envelope.intent` as `TEXT`.
- **`intent_envelope.intentHistoryJson`** — JSON array of layer objects; each layer records a label transition. The migration appends one layer per renamed row.
- **`ContactRef`** (new Kotlin data class, no Room entity yet) — `id: String?` (Android `ContactsContract.Contacts.LOOKUP_KEY`; null when source = `manual`), `name: String` (display name), `source: ContactRefSource` (sealed/enum: `MANUAL`, `DEVICE_CONTACTS`, `PHONE_HISTORY`). Maps to three nullable columns on `intent_envelope` (`contactRefId`, `contactRefName`, `contactRefSource`). Used by `FOR_SOMEONE` envelopes; null otherwise. Two CHECK constraints enforce vocabulary + id-source coupling at the schema layer (see FR-005). UI surface deferred to spec 017.

## Success Criteria *(mandatory)*

- **SC-001**: After upgrading from a v3 build to a v4 build, an alpha user's capsule count is unchanged and every previously-saved envelope renders with the new label or `"—"` (for `AMBIGUOUS`).
- **SC-002**: A user reviewing a renamed envelope's intent history (deferred UI; visible via DB inspector at v1) sees both the original label and the rename event.
- **SC-003**: A user inspecting the chip row sees exactly five chips, in the order `Remind me, Inspiration, Reference, Read later, For someone`.
- **SC-004**: The string `"Unassigned"` does not appear in any user-facing surface; `AMBIGUOUS` envelopes display `"—"`.
- **SC-005**: The V3→V4 migration completes in under 200 ms on a 1000-envelope fixture (matches existing migration budgets in 002/003).
- **SC-006**: All instrumented and JVM tests pass; lint baseline is unchanged from the parent branch.

## Out of Scope (but Needed Soon)

These are deliberately deferred to keep this migration tight. Each is a real product need, just not part of this spec:

- **Capture-sheet contact-picker UI** — `FOR_SOMEONE` chip currently has no contact-picker flow. Deferred to spec 017.
- **"Text Maya?" follow-up flow** — The downstream interaction once a `ContactRef` is attached. Deferred to spec 018+.
- **Diary mini-intent display of contact names** — Showing "For Maya" instead of "For someone" on the card. Deferred.
- **Address-book sync / contact picker UX** — User types or pastes a name/phone/email; no system contact integration at v1. Deferred.
- **Marketing copy & docs propagation** — Founder kit (`product-dna.md`, `personas.md`, `master-faq.md`, `01-pitch/one-liners.md`), the original spec/002 design doc, and the `015-visual-refit/design/...` canvas all reference older intent-set versions. See `quickstart.md` § "After merge, propagate to docs" for the post-merge sweep TODO. **Not this spec's job.**

## Assumptions & Cross-References

- **Spec 015 (`015-visual-refit`)** is the consumer of the new chip palette via its IntentChip palette token contract. Spec 015 is currently on its own branch and gates on PR #5 merging to main; spec 016 references it in prose only (no relative-path links until both ship).
- **No legacy sentinel value** is added (e.g., `INTERESTING_LEGACY`) — the rename is a clean substitution, with audit history serving as the preservation channel.
- **No row recategorization** beyond the documented renames. We accept that the `INTERESTING → INSPIRATION` rename is semantically lossy; the user's escape hatch is the existing intent-reassign action in Diary.
- **Cluster engine** does NOT consume the `Intent` enum (verified: `grep -n "Intent\." app/src/main/java/com/capsule/app/cluster/*.kt` returns zero matches). Cluster similarity is embedding-driven.
- **AIDL parcel surface** carries `intent` as `String` (verified in `IntentEnvelopeDraftParcel` and `EnvelopeViewParcel`). The enum-name change flows through transparently; no `.aidl` file edits required for the enum migration. `ContactRef` parcel fields are NOT added at v1 (no consumer; UI deferred).
