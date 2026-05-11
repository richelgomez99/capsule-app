# Tasks: Intent Set Extension (Spec 016)

**Input**: [spec.md](spec.md), [plan.md](plan.md), [data-model.md](data-model.md)  
**Branch**: `016-intent-set-migration`  
**Workflow**: specify -> plan -> tasks -> implement. Run `speckit.analyze` after this amendment before committing implementation work.

## Phase 0 - Product-label amendment

- [x] **T016-001** Amend spec 016 so `WANT_IT` and `INTERESTING` are preserved and `READ_LATER` is added as the only new user-pickable intent.
- [x] **T016-002** Remove stale `REMIND_ME`, `INSPIRATION`, and label-rename migration requirements from active spec, plan, data model, research, quickstart, and tasks.
- [x] **T016-003** Run `speckit.analyze` against amended spec 016 and fix any blocking/high findings before committing.

## Phase 1 - Android intent surface

- [x] **T016-101** Update `Intent.kt` to exactly `WANT_IT`, `REFERENCE`, `READ_LATER`, `FOR_SOMEONE`, `INTERESTING`, `AMBIGUOUS`.
- [x] **T016-102** Update `EnvelopeCard.kt`, `EnvelopeDetailScreen.kt`, `SilentWrapPill.kt`, and `DigestComposer.kt` label resolvers to cover `READ_LATER` while preserving existing product copy.
- [x] **T016-103** Update `ChipRow.kt` to render five chips in order: `Want it`, `Reference`, `Read later`, `For someone`, `Interesting`.
- [x] **T016-104** Update `IntentChipPicker.kt` to render the same five chips in the same order.
- [x] **T016-105** Verify no user-pickable chip path offers `Intent.AMBIGUOUS`.

**Gate**: `:app:compileDebugKotlin` and `:app:testDebugUnitTest` pass.

## Phase 2 - Cloud classifier alignment

- [x] **T016-201** Update `supabase/functions/llm_gateway/handlers/classify_intent.ts` to prompt for Android labels: `WANT_IT`, `REFERENCE`, `READ_LATER`, `FOR_SOMEONE`, `INTERESTING`, `AMBIGUOUS`.
- [x] **T016-202** Update `supabase/functions/llm_gateway/lib/allowlists.ts` so the closed set matches Android.
- [x] **T016-203** Update Supabase tests to cover accepted `WANT_IT` and `READ_LATER` labels plus out-of-set fallback to `AMBIGUOUS`.

**Gate**: Supabase `llm_gateway` tests pass.

## Phase 3 - ContactRef schema, if included in this branch

- [ ] **T016-301** Add `ContactRef` and `ContactRefSource` value types.
- [ ] **T016-302** Add nullable `contactRefId`, `contactRefName`, and `contactRefSource` fields to `IntentEnvelopeEntity` only in the same change set as the Room migration.
- [ ] **T016-303** Add Room migration for contact-ref columns and CHECK constraints; do not rewrite existing `intent` values.
- [ ] **T016-304** Generate and commit the exported Room schema for the new version.
- [ ] **T016-305** Add migration tests proving row count and all existing `intent` values are preserved, contact-ref columns back-fill `NULL`, and both CHECK constraints reject invalid rows.

**Gate**: migration tests pass and exported schema includes the contact-ref columns/constraints.

## Phase 4 - Verification and branch hygiene

- [x] **T016-401** Run Android gate: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`.
- [x] **T016-402** Run Supabase classifier tests.
- [x] **T016-403** Confirm implementation paths contain no stale `REMIND_ME`, `INSPIRATION`, or `intent-set rename` behavior.
- [x] **T016-404** Commit amended spec 016 and implementation slices separately enough for review.

## Verification Matrix

| Requirement | Verifying task |
|---|---|
| FR-016-001 enum set | T016-101 + compile |
| FR-016-002 no historical rename | T016-003 + T016-303/T016-305 if migration lands |
| FR-016-003 chip order | T016-103 + T016-104 |
| FR-016-004 label resolvers | T016-102 + compile |
| FR-016-005 no Ambiguous chip | T016-105 |
| FR-016-006 cloud labels | T016-201..203 |
| FR-016-007 contact-ref schema | T016-301..305 |
| FR-016-009 fallback parse | Existing parser + T016-401 |
| FR-016-010 no stale rename work | T016-003 + T016-403 |
