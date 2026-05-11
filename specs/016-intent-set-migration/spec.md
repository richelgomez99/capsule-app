# Feature Specification: Intent Set Extension

**Feature Branch**: `016-intent-set-migration`  
**Created**: 2026-04-29  
**Amended**: 2026-05-11  
**Status**: Draft, amended for product-label preservation  
**Input**: Preserve the shipped product labels `Want it`, `Reference`, `For someone`, and `Interesting`; add `Read later`; retain `AMBIGUOUS` as a defensive sentinel; align the cloud classifier with Android enum names; keep future `ContactRef` schema work scoped and explicit.

## Clarifications

### Session 2026-05-11

- Q: Should `WANT_IT` be renamed to `REMIND_ME`? -> A: No. Preserve `WANT_IT` and display it as `Want it`.
- Q: Should `INTERESTING` be renamed to `INSPIRATION`? -> A: No. Preserve `INTERESTING` and display it as `Interesting`.
- Q: What is the five-chip product order? -> A: `Want it`, `Reference`, `Read later`, `For someone`, `Interesting`.
- Q: Is `AMBIGUOUS` user-pickable? -> A: No. It remains a defensive sentinel for timeout, fallback, and unrecognized parse paths.

### Session 2026-04-29

- Q: Should `ContactRef` be three nullable columns on `intent_envelope` or a separate join table? -> A: Three nullable `TEXT` columns on `intent_envelope`: `contactRefId`, `contactRefName`, `contactRefSource`. `contactRefSource` uses `{manual, device_contacts, phone_history}`. `contactRefId` holds Android `ContactsContract.Contacts.LOOKUP_KEY` when source is `device_contacts` or `phone_history`; it is null when source is `manual`.
- Q: Does spec 016 add contact-picker UI? -> A: No. This spec may add forward-compatible schema only; the picker and follow-up flows are deferred.

## User Scenarios & Testing

### User Story 1 - Capture Uses The Preserved Five Labels (Priority: P1)

When a user captures something and Orbit needs an explicit intent, the chip row shows the product labels in the approved order: `Want it`, `Reference`, `Read later`, `For someone`, `Interesting`. Existing concepts keep their names; `Read later` is the only new user-pickable label.

**Independent Test**: Render `ChipRow` and `IntentChipPicker`; assert exactly five chips in the documented order and no `AMBIGUOUS` chip.

**Acceptance Scenarios**:

1. **Given** the chip row is shown, **When** the user inspects the options, **Then** the row displays `Want it`, `Reference`, `Read later`, `For someone`, `Interesting`.
2. **Given** the Diary intent picker is shown, **When** the user inspects options, **Then** it displays the same five labels in the same order.
3. **Given** an auto-ambiguous fallback occurs, **When** the envelope is sealed, **Then** the stored intent may be `AMBIGUOUS` but the chip palette never offered `AMBIGUOUS` as a user choice.

### User Story 2 - Existing Captures Stay Stable (Priority: P1)

An alpha user upgrades from the existing build. Existing rows with `WANT_IT`, `REFERENCE`, `FOR_SOMEONE`, `INTERESTING`, and `AMBIGUOUS` keep those stored values. The migration must not recategorize or rename prior captures.

**Independent Test**: Run the migration over a fixture with one row per current intent and assert row count and `intent` values are unchanged. Any new `ContactRef` columns back-fill `NULL`.

**Acceptance Scenarios**:

1. **Given** a row with `intent='WANT_IT'`, **When** the app upgrades, **Then** the row remains `WANT_IT` and renders as `Want it`.
2. **Given** a row with `intent='INTERESTING'`, **When** the app upgrades, **Then** the row remains `INTERESTING` and renders as `Interesting`.
3. **Given** any existing row, **When** the migration runs, **Then** no intent-history layer is appended solely because of spec 016 label changes.

### User Story 3 - Cloud Classifier Uses Android Labels (Priority: P1)

The cloud LLM gateway returns labels that Android can parse directly: `WANT_IT`, `REFERENCE`, `READ_LATER`, `FOR_SOMEONE`, `INTERESTING`, or `AMBIGUOUS`. Out-of-set model output still collapses to `AMBIGUOUS`.

**Independent Test**: Supabase function tests assert accepted labels pass through, rejected labels become `AMBIGUOUS`, and the prompt enumerates the Android enum names.

### User Story 4 - ContactRef Columns Are Ready But Dormant (Priority: P3)

The database can add nullable `ContactRef` columns for a future `FOR_SOMEONE` flow. No UI writes them yet. Existing rows must back-fill `NULL`.

**Independent Test**: Inspect the post-migration schema, assert the three columns and both CHECK constraints exist, and verify invalid `contactRefSource` / `contactRefId` combinations fail.

## Edge Cases

- **Unknown intent string**: Parsing continues to defensive-fallback to `AMBIGUOUS`.
- **Legacy rename text in older docs**: Treat as superseded by this amendment. Do not implement `REMIND_ME` or `INSPIRATION` without a new explicit product decision.
- **`READ_LATER` in old databases**: No existing row should contain it. It is introduced for new captures only.
- **ContactRef absent**: All `contactRef*` fields may be null; readers must treat that as no contact attached.
- **Manual contact with id**: Invalid. A non-null `contactRefId` is allowed only for `device_contacts` or `phone_history`.

## Requirements

### Functional Requirements

- **FR-016-001**: `Intent` MUST contain exactly six values: `WANT_IT`, `REFERENCE`, `READ_LATER`, `FOR_SOMEONE`, `INTERESTING`, `AMBIGUOUS`.
- **FR-016-002**: Existing `WANT_IT` and `INTERESTING` rows MUST NOT be renamed, rewritten, or migrated to different enum names.
- **FR-016-003**: `ChipRow` and `IntentChipPicker` MUST render exactly five user-pickable chips in this order: `Want it`, `Reference`, `Read later`, `For someone`, `Interesting`.
- **FR-016-004**: Every app-side intent label resolver MUST cover all six enum values and preserve the display strings `Want it`, `Reference`, `Read later`, `For someone`, `Interesting`.
- **FR-016-005**: `AMBIGUOUS` MUST remain parseable and storable as a defensive sentinel, but MUST NOT appear in user-pickable chip palettes.
- **FR-016-006**: The Supabase `llm_gateway` classifier prompt and allowlist MUST use the Android labels from FR-016-001 and collapse any out-of-set label to `AMBIGUOUS`.
- **FR-016-007**: If contact-ref schema lands in this spec, `MIGRATION_3_4` MUST add nullable `TEXT` columns `contactRefId`, `contactRefName`, and `contactRefSource`, with CHECK constraints for source vocabulary and id-source coupling.
- **FR-016-008**: Contact-ref migration tests MUST verify existing rows keep their intent values and back-fill all `contactRef*` columns to `NULL`.
- **FR-016-009**: `toIntentOrAmbiguous()` and any string parsing wrappers MUST continue to return `AMBIGUOUS` for unknown strings.
- **FR-016-010**: No spec 016 task may introduce `REMIND_ME`, `INSPIRATION`, or `intent-set rename` migration behavior.

### Key Entities

- **`Intent` enum**: Five user-pickable product labels plus `AMBIGUOUS` sentinel.
- **`ContactRef`**: Future value object with `id: String?`, `name: String`, and `source: ContactRefSource`; persisted as nullable columns when schema work lands.
- **Cloud classifier intent label**: A closed-set string matching the Android enum names.

## Success Criteria

- **SC-016-001**: Existing captures keep their stored intent names after upgrade.
- **SC-016-002**: The capture chip row and Diary picker show the five approved labels in order.
- **SC-016-003**: `READ_LATER` is accepted across Android label resolvers and the Supabase classifier allowlist.
- **SC-016-004**: Unknown model labels are sanitized to `AMBIGUOUS`.
- **SC-016-005**: Contact-ref schema work, if included, is additive and preserves every existing row.
- **SC-016-006**: Android compile, unit tests, Android test-source compile, lint, and Supabase function tests pass for the touched surfaces.

## Out Of Scope

- Renaming `WANT_IT` to `REMIND_ME`.
- Renaming `INTERESTING` to `INSPIRATION`.
- Recategorizing old rows into `READ_LATER`.
- Contact-picker UI, multi-recipient UI, and Text Maya follow-up actions.
- Visual styling for chips beyond adding the `READ_LATER` option; spec 015 owns the visual refit.

## Assumptions & Cross-References

- Spec 015 owns visual treatment of the chip row and Diary surfaces.
- Spec 017 owns duplicate capture feedback and already-saved actions.
- Cluster similarity remains embedding-driven and does not consume the `Intent` enum.
