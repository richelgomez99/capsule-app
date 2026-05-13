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
- [x] **T016-106** Add an Android unit test for shared intent string parsing: all durable enum names round-trip and unknown/stale values fallback to `AMBIGUOUS`.

**Gate**: `:app:compileDebugKotlin` and `:app:testDebugUnitTest` pass.

## Phase 2 - Cloud classifier alignment

- [x] **T016-201** Update `supabase/functions/llm_gateway/handlers/classify_intent.ts` to prompt for Android labels: `WANT_IT`, `REFERENCE`, `READ_LATER`, `FOR_SOMEONE`, `INTERESTING`, `AMBIGUOUS`.
- [x] **T016-202** Update `supabase/functions/llm_gateway/lib/allowlists.ts` so the closed set matches Android.
- [x] **T016-203** Update Supabase tests to cover representative handler responses, all six allowlisted Android labels, and out-of-set fallback to `AMBIGUOUS`.

**Gate**: Supabase `llm_gateway` tests pass.

## Phase 3 - ContactRef schema (deferred follow-up; no active tasks in this branch)

- ContactRef value types, `IntentEnvelopeEntity` fields, Room migration, exported schema, and migration tests are deferred to a future schema slice.
- That future slice must target the next migration after the then-current Room schema version; do not reuse already-consumed earlier migration names.

**Gate for future slice**: migration tests pass and exported schema includes the contact-ref columns/constraints.

## Branch Debt Closeout Reconciliation - 2026-05-13

- Clean status: `/Users/richelgomez/dev/capsule-app-spec-016` returned no `git status --short` output before reconciliation checks.
- Diff against `origin/main...HEAD`: 22 files changed, 630 insertions, 37 deletions. Scope is spec docs, Android intent/label surfaces, Android parsing test, and Supabase classifier/allowlist/tests.
- Diff against `origin/016-intent-set-migration...HEAD`: branch is materially diverged from the stale PR branch; PR #8 should be updated or replaced after the gates are reviewed.
- Android gate passed: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, and `:app:lintDebug` succeeded.
- Supabase gateway package has no `npm test` script. Actual package gates passed with `npm run typecheck` and `npm run test:unit`; Vitest reported 6 files passed and 53 tests passed.
- Stale-label search returned no implementation hits for `REMIND_ME`, `INSPIRATION`, or `intent-set rename` under `app/src/main` and `supabase/functions/llm_gateway`.
- READ_LATER coverage search confirmed Android and cloud classifier coverage in `DigestComposer`, `Intent`, Diary surfaces, overlay chips, picker, classifier prompt, allowlist, and Anthropic handler tests.
- ContactRef/schema leakage check: no `ContactRef`, `contact_ref`, `contactRef`, `OrbitMigrations`, `OrbitDatabase`, or `app/schemas` paths appear in the `origin/main...HEAD` diff.

## Phase 4 - Verification and branch hygiene

- [x] **T016-401** Run Android gate: `:app:compileDebugKotlin`, `:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`.
- [x] **T016-402** Run Supabase classifier tests.
- [x] **T016-403** Confirm implementation paths contain no stale `REMIND_ME`, `INSPIRATION`, or `intent-set rename` behavior.
- [x] **T016-404** Commit amended spec 016 and implementation slices separately enough for review.
- [x] **T016-405** Confirm the spec 016 diff contains no Room entity, migration, or exported schema changes for ContactRef or intent rewrites.

## Verification Matrix

| Requirement | Verifying task |
| --- | --- |
| FR-016-001 enum set | T016-101 + compile |
| FR-016-002 no historical rename | T016-106 + T016-403 + T016-405 |
| FR-016-003 chip order | T016-103 + T016-104 |
| FR-016-004 label resolvers | T016-102 + compile |
| FR-016-005 no Ambiguous chip | T016-105 |
| FR-016-006 cloud labels | T016-201..203 |
| FR-016-007 no ContactRef schema in this slice | T016-405 |
| FR-016-008 existing intents preserved | T016-106 + T016-405 |
| FR-016-009 fallback parse | T016-106 + T016-401 |
| FR-016-010 no stale rename work | T016-003 + T016-403 |
