# Plan: Intent Set Extension

## Constitution Check

- **Predictable migrations**: Existing intent values are preserved; any DB migration is additive contact-ref schema only.
- **Audit integrity**: No fake migration history is written for labels because no label is renamed.
- **LLM boundary**: Cloud classifier output is sanitized to the Android closed set.
- **Branch hygiene**: This branch owns intent enum/product-label alignment only; visual refit remains spec 015 and duplicate feedback remains spec 017.

## Phase Plan

### Phase 0 - Amendment

Replace stale `REMIND_ME` / `INSPIRATION` planning text with the product decision from 2026-05-11: preserve `WANT_IT` and `INTERESTING`, add `READ_LATER`.

### Phase 1 - Android Intent Surface

- Add `READ_LATER` to `Intent.kt`.
- Update all exhaustive `when (intent: Intent)` label resolvers in Android app code.
- Update `ChipRow` and `IntentChipPicker` to show five user-pickable chips in order: `Want it`, `Reference`, `Read later`, `For someone`, `Interesting`.
- Keep `AMBIGUOUS` as a non-pickable sentinel.

**Gate**: `:app:compileDebugKotlin` and `:app:testDebugUnitTest` pass.

### Phase 2 - Cloud Classifier Alignment

- Update `supabase/functions/llm_gateway/handlers/classify_intent.ts` prompt to enumerate Android labels.
- Update `supabase/functions/llm_gateway/lib/allowlists.ts` to accept `WANT_IT`, `REFERENCE`, `READ_LATER`, `FOR_SOMEONE`, `INTERESTING`, `AMBIGUOUS`.
- Update Supabase tests to assert accepted labels and out-of-set fallback.

**Gate**: Supabase function tests for `llm_gateway` pass.

### Phase 3 - ContactRef Schema (Deferred)

- No ContactRef value object, `IntentEnvelopeEntity` fields, Room migration, or exported schema lands in this implementation slice.
- Follow-up ContactRef work must target the next migration after the then-current Room schema version. Earlier migration names are already owned by prior schema work and MUST NOT be reused for ContactRef.
- ContactRef fields and their Room migration must still land in the same future change set.

**Gate for future slice**: migration tests prove existing intent values are unchanged and new contact-ref columns back-fill `NULL`.

### Phase 4 - Cross-Tree Verification

- Run Android compile, unit tests, Android test-source compile, lint.
- Run Supabase tests for the classifier.
- Grep for forbidden stale values in implementation code: `REMIND_ME`, `INSPIRATION`, and `intent-set rename` should not appear in spec 016 implementation paths.

## Decisions

- `WANT_IT` remains the stored enum and displays as `Want it`.
- `INTERESTING` remains the stored enum and displays as `Interesting`.
- `READ_LATER` is a new stored enum value, not a migration target for old rows.
- Todo-seed behavior in `ActionsRepositoryDelegate` can remain `Intent.WANT_IT` unless a separate product decision changes action-item routing.
- `AMBIGUOUS` remains the fallback enum; do not offer it as a chip.

## Risks

| Risk | Mitigation |
|---|---|
| Stale docs push implementation toward `REMIND_ME` / `INSPIRATION` | This amendment removes those names from active requirements and tasks. |
| Cloud returns labels Android cannot parse | Prompt and allowlist use Android enum names; sanitizer collapses unknown labels to `AMBIGUOUS`. |
| Adding ContactRef without migration breaks Room startup | ContactRef is deferred; future entity fields and schema migration must land together. |
| Five chips crowd the compact overlay | Spec 015 owns visual refinement; spec 016 verifies behavior/order only. |
