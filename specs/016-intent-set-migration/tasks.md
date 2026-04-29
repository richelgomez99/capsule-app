# Tasks: Intent Set Migration (Spec 016)

> Implementation-side tasks. The planning PR for this spec is docs-only; these tasks are landed in a follow-up implementation branch.

Tasks are grouped by the phase boundaries from `plan.md`. Each phase = one commit (or a small commit cluster) with the exact gate listed at the end. Phases are ordered by dependency; do not parallelise across phases.

## Phase 1 — Enum + entity surface

**Commit boundary**: `feat(016): T016-001..009 — intent enum + ContactRef + entity columns`

- [ ] **T016-001** — Update `app/src/main/java/com/capsule/app/data/model/Intent.kt` to the six-value v4 set: `REMIND_ME`, `INSPIRATION`, `REFERENCE`, `READ_LATER`, `FOR_SOMEONE`, `AMBIGUOUS`. Update the KDoc comment from "four v1 intent labels" to "five user-pickable v2 labels + AMBIGUOUS sentinel".
- [ ] **T016-002** — Add `MIGRATION` value to `app/src/main/java/com/capsule/app/data/model/IntentSource.kt`. KDoc: "Layer written by a Room migration; `migrationReason` field on the layer indicates which migration."
- [ ] **T016-003** — Create `app/src/main/java/com/capsule/app/data/model/ContactRef.kt` with the `ContactRef(displayName, phoneE164, email)` data class.
- [ ] **T016-004** — Add three nullable fields to `IntentEnvelopeEntity`: `contactRefDisplayName: String? = null`, `contactRefPhoneE164: String? = null`, `contactRefEmail: String? = null`. Add a `contactRef(): ContactRef?` extension.
- [ ] **T016-005** — Update `EnvelopeCard.kt` `Intent → label` `when` to cover all six values. Map: `REMIND_ME → "Remind me"`, `INSPIRATION → "Inspiration"`, `REFERENCE → "Reference"`, `READ_LATER → "Read later"`, `FOR_SOMEONE → "For someone"`, `AMBIGUOUS → "—"`.
- [ ] **T016-006** — Same update for `EnvelopeDetailScreen.kt`.
- [ ] **T016-007** — Update `SilentWrapPill.kt`'s `Intent.label()` extension to the same six-value mapping (with `AMBIGUOUS → "—"`).
- [ ] **T016-008** — Update `DigestComposer.kt:193-197` string-keyed `when` to the new keys: `"REMIND_ME" -> "Remind me"`, `"INSPIRATION" -> "Inspiration"`, `"READ_LATER" -> "Read later"`, `"REFERENCE" -> "Reference"`, `"FOR_SOMEONE" -> "For someone"`, `"AMBIGUOUS" -> "—"`. Drop `"WANT_IT"` and `"INTERESTING"` keys.
- [ ] **T016-009** — Update `ActionsRepositoryDelegate.kt:297` todo-seed intent from `Intent.WANT_IT` to `Intent.REMIND_ME` per `plan.md` recommendation (b). Update the KDoc comment at line 260 to match. Confirm with the test suite that the todo-seed flow still works end-to-end.

**Gate (Phase 1)**:
- `:app:compileDebugKotlin` clean.
- All `when (intent: Intent)` sites compile (Kotlin compiler enforces exhaustiveness).
- DB version still **3** at this point (migration lands in Phase 2). The new entity columns are declared in Kotlin but not yet in the SQLite schema — Room will detect this mismatch and fail at startup. **Therefore Phase 1 cannot be merged independently of Phase 2** — they must land together.

> **Implementation note**: Although Phase 1 and Phase 2 must land in the same PR (Room schema/code coupling), keeping them as distinct logical commits inside the PR makes review and bisection cleaner.

## Phase 2 — Migration + DB version bump

**Commit boundary**: `feat(016): T016-010..016 — MIGRATION_3_4 + schema bump`

- [ ] **T016-010** — In `app/src/main/java/com/capsule/app/data/OrbitDatabase.kt`, bump `version = 3` to `version = 4`.
- [ ] **T016-011** — In `OrbitMigrations.kt`, author `MIGRATION_3_4`. Body:
  - `UPDATE intent_envelope SET intent='REMIND_ME', intentHistoryJson = json_insert(COALESCE(NULLIF(intentHistoryJson, ''), '[]'), '$[#]', json_object('intent', 'REMIND_ME', 'source', 'MIGRATION', 'at', <ts>, 'migrationReason', 'spec-016 intent-set rename', 'fromIntent', 'WANT_IT')) WHERE intent='WANT_IT'`
  - Same shape for `INTERESTING → INSPIRATION`.
  - `ALTER TABLE intent_envelope ADD COLUMN contactRefDisplayName TEXT`
  - `ALTER TABLE intent_envelope ADD COLUMN contactRefPhoneE164 TEXT`
  - `ALTER TABLE intent_envelope ADD COLUMN contactRefEmail TEXT`
  - Defensive: if `json1` parse fails on a row, fall back to a Kotlin-side cursor pass that catches the exception per row and writes a fresh `[<rename layer>]` array. Log the row id at WARN level.
- [ ] **T016-012** — Register `MIGRATION_3_4` in `OrbitDatabase.databaseBuilder(...).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.
- [ ] **T016-013** — Run `:app:assembleDebug` to generate `app/schemas/com.capsule.app.data.OrbitDatabase/4.json`. Commit it.
- [ ] **T016-014** — Author `app/src/androidTest/java/com/capsule/app/data/OrbitDatabaseMigrationV3toV4Test.kt`. Fixtures (one envelope each):
  - `WANT_IT` with `intentHistoryJson="[]"`
  - `WANT_IT` with non-empty history (multi-layer): assert FR-013 (preserves prior layers, appends rename layer at terminal index).
  - `INTERESTING` with `intentHistoryJson="[]"`
  - `INTERESTING` with malformed JSON (e.g., `"{"`): assert defensive fallback writes a fresh single-element array and the migration completes.
  - `REFERENCE`, `FOR_SOMEONE`, `AMBIGUOUS` (one each, `intentHistoryJson="[]"`): assert no rename, no new layer, JSON byte-identical pre/post.
  - One `FOR_SOMEONE` row with non-empty history: assert no new layer, history byte-identical, and the new contact-ref columns exist with NULL values.
  - Schema introspection: assert `contactRefDisplayName`, `contactRefPhoneE164`, `contactRefEmail` columns exist with type `TEXT` and are nullable.
- [ ] **T016-015** — Author `OrbitDatabaseMigrationV3toV4AuditPreservationTest` (or fold into the same test class as a separate `@Test` method). Specifically asserts FR-013: an envelope with N prior layers retains all N layers byte-identically (modulo the appended layer) and the new layer is at index N (terminal).
- [ ] **T016-016** — Run `:app:connectedDebugAndroidTest --tests "com.capsule.app.data.OrbitDatabaseMigrationV3toV4*"` on an emulator (or document the skip if no emulator is available; the JVM-side `MigrationTestHelper` may suffice depending on Room version).

**Gate (Phase 2)**:
- `:app:assembleDebug` succeeds; `4.json` is generated and committed.
- All Phase-2 migration tests pass.
- `:app:testDebugUnitTest` full suite: green.
- `:app:lintDebug`: baseline unchanged from parent branch.

## Phase 3 — Chip palette + Unassigned sweep

**Commit boundary**: `feat(016): T016-017..023 — chip palette + Unassigned → "—" sweep`

- [ ] **T016-017** — Update `app/src/main/java/com/capsule/app/overlay/ChipRow.kt` to render five chips (was four) in the order `Remind me, Inspiration, Reference, Read later, For someone`. Each chip uses the corresponding `Intent.*` value and an icon. Tentative icon mapping (final icons may be redefined by spec 015):
  - `REMIND_ME` → `Icons.Filled.NotificationsActive` (was `Icons.Filled.Favorite` for `WANT_IT`)
  - `INSPIRATION` → `Icons.Filled.AutoAwesome` (was the same for `INTERESTING`)
  - `REFERENCE` → `Icons.Filled.Bookmark` (unchanged)
  - `READ_LATER` → `Icons.Filled.Schedule` (NEW; flag for spec 015 to refine)
  - `FOR_SOMEONE` → `Icons.AutoMirrored.Filled.Send` (unchanged)
- [ ] **T016-018** — Update `app/src/main/java/com/capsule/app/ui/IntentChipPicker.kt` analogously to render the same five chips in the same order.
- [ ] **T016-019** — Sweep `app/src/main` for `"Unassigned"` and replace with `"—"` (em-dash, U+2014). Audited sites:
  - `EnvelopeCard.kt:387`
  - `EnvelopeDetailScreen.kt:496`
  - Run `grep -rn "Unassigned" app/src/main` post-sweep; assert zero matches.
- [ ] **T016-020** — Sweep `app/src/main` for the user-facing string `"Ambiguous"` (capital A, double-quoted) and replace with `"—"`. Currently affects:
  - `SilentWrapPill.kt:176` (covered by T016-007 already; T016-020 is the verification grep).
- [ ] **T016-021** — Update Compose preview functions in the affected files to render envelopes with the new labels. Confirm previews compile.
- [ ] **T016-022** — Author `app/src/androidTest/java/com/capsule/app/overlay/ChipRowPaletteTest.kt`: launches the `ChipRow` Composable, asserts the rendered chips are exactly the five new labels in the documented order, and that no chip carries `Intent.AMBIGUOUS`.
- [ ] **T016-023** — Author `app/src/androidTest/java/com/capsule/app/diary/ui/EnvelopeCardLabelTest.kt` (or extend an existing test): renders an `EnvelopeCard` with `intent=AMBIGUOUS` and asserts the visible label text is exactly `"—"`.

**Gate (Phase 3)**:
- `:app:assembleDebug` clean.
- Phase-3 instrumented tests pass.
- `grep -rn "Unassigned\|\"Ambiguous\"" app/src/main` returns zero matches.

## Phase 4 — Cross-tree verification

**Commit boundary**: `chore(016): T016-024..026 — cross-tree verification + status log`

- [ ] **T016-024** — Run the full JVM unit-test suite: `:app:testDebugUnitTest`. All tests green; investigate and fix any incidental failures from `when` exhaustiveness changes that were missed.
- [ ] **T016-025** — Confirm cluster code is unaffected: `grep -n "Intent\." app/src/main/java/com/capsule/app/cluster/*.kt` should still return zero matches. (No code change expected; this is a verification gate.)
- [ ] **T016-026** — Update `TODOS.md` with a status entry for spec 016 implementation (if the project uses that file as a status log). Tick `[ ]` boxes for the deliverables.

**Gate (Phase 4)**:
- All test suites green.
- Lint baseline unchanged.
- No `Intent.WANT_IT` / `Intent.INTERESTING` references in `app/src/main` or `app/src/test` or `app/src/androidTest` (sweep the entire tree, including tests).

## Phase 5 — PR + sign-off

**Commit boundary**: not a code commit — the PR opens against `main` after Phases 1–4 land.

- [ ] **T016-027** — Open PR against `main`. Title: `spec 016: intent-set migration — implementation`. Body summarises the four phase commits, lists the gates passed, and links back to this `tasks.md`.
- [ ] **T016-028** — On merge, kick off the post-merge doc sweep tracked in `quickstart.md` § "After merge, propagate to docs". This is a separate task list, not part of spec 016's code surface.

## Verification matrix

| Requirement | Verifying task |
|---|---|
| FR-001 (six enum values) | T016-001 + Kotlin compiler |
| FR-002 (rename SQL) | T016-011 + T016-014 |
| FR-003 (audit layer on rename) | T016-011 + T016-014 + T016-015 |
| FR-004 (no layer on no-change) | T016-014 |
| FR-005 (contactRef columns) | T016-011 + T016-014 |
| FR-006 (chip palette = 5) | T016-017 + T016-018 + T016-022 |
| FR-007 (Unassigned → "—") | T016-019 + T016-023 |
| FR-008 (when-exhaustive coverage) | T016-005..008 + Kotlin compiler |
| FR-009 (defensive fallback) | Existing `toIntentOrAmbiguous` and `Intent.valueOf` fallback unchanged |
| FR-010 (DB version bump + 4.json) | T016-010 + T016-013 |
| FR-011 (migration registered) | T016-012 |
| FR-012 (V3→V4 migration test) | T016-014 |
| FR-013 (audit-history preservation) | T016-015 |
