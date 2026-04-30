# Tasks: Intent Set Migration (Spec 016)

> Implementation-side tasks. The planning PR for this spec is docs-only; these tasks are landed in a follow-up implementation branch.

Tasks are grouped by the phase boundaries from `plan.md`. Each phase = one commit (or a small commit cluster) with the exact gate listed at the end. Phases are ordered by dependency; do not parallelise across phases.

## Phase 1 — Enum + entity surface

**Commit boundary**: `feat(016): T016-001..009 — intent enum + ContactRef + entity columns`

- [ ] **T016-001** — Update `app/src/main/java/com/capsule/app/data/model/Intent.kt` to the six-value v4 set: `REMIND_ME`, `INSPIRATION`, `REFERENCE`, `READ_LATER`, `FOR_SOMEONE`, `AMBIGUOUS`. Update the KDoc comment from "four v1 intent labels" to "five user-pickable v2 labels + AMBIGUOUS sentinel".
- [ ] **T016-002** — Add `MIGRATION` value to `app/src/main/java/com/capsule/app/data/model/IntentSource.kt`. KDoc: "Layer written by a Room migration; `migrationReason` field on the layer indicates which migration."
- [ ] **T016-003** — Create `app/src/main/java/com/capsule/app/data/ContactRef.kt` with the revised data class shape:
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
  KDoc on `id`: "Android `ContactsContract.Contacts.LOOKUP_KEY` (stable across contact-merges) when `source ∈ {DEVICE_CONTACTS, PHONE_HISTORY}`; `null` when `source = MANUAL`." Note: AIDL parcel surface is NOT updated at v1 (UI deferred to spec 017); no `.aidl` edits required for `ContactRef`.
- [ ] **T016-004** — Add three nullable fields to `IntentEnvelopeEntity`: `contactRefId: String? = null`, `contactRefName: String? = null`, `contactRefSource: String? = null` (the `source` is persisted as `String` and parsed to `ContactRefSource` at the entity → domain boundary). Add a `contactRef(): ContactRef?` extension that returns a populated `ContactRef` only when `contactRefName` is non-null and `contactRefSource` parses to a known `ContactRefSource` enum value.
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

**Commit boundary**: `feat(016): T016-010..016 + T016-VERIFY-CHECK — MIGRATION_3_4 + schema bump + CHECK verification`

- [ ] **T016-010** — In `app/src/main/java/com/capsule/app/data/OrbitDatabase.kt`, bump `version = 3` to `version = 4`.
- [ ] **T016-011** — In `OrbitMigrations.kt`, author `MIGRATION_3_4`. Body:
  - **Step 1 — Rename + audit-layer append (in place on the v3 table)**:
    - `val ts = Instant.now().toString()` — ISO-8601 UTC via `Instant.toString()` (e.g., `"2026-04-29T12:34:56Z"`). Reused for every row in this migration run. The `'at'` field in every appended history layer MUST use this ISO-8601 string (NOT `System.currentTimeMillis()` or any epoch-millis form).
    - `UPDATE intent_envelope SET intent='REMIND_ME', intentHistoryJson = json_insert(COALESCE(NULLIF(intentHistoryJson, ''), '[]'), '$[#]', json_object('intent', 'REMIND_ME', 'source', 'MIGRATION', 'at', :ts, 'migrationReason', 'spec-016 intent-set rename', 'fromIntent', 'WANT_IT')) WHERE intent='WANT_IT'`
    - Same shape for `INTERESTING → INSPIRATION`.
    - **Malformed `intentHistoryJson` handling**: SQLite's `json_insert` will fail on malformed JSON. Wrap the per-row history mutation in a Kotlin-side cursor pass that catches `JSONException` (or the SQLite JSON1 error). On failure, emit `Log.w("migration_3_4_malformed_history", row_id)` and overwrite `intentHistoryJson` with a fresh single-element array `'[<rename_layer>]'`. Prior history is lost — this is the locked behavior (spec.md Clarifications session 2026-04-29).
  - **Step 2 — Add 3 columns + 2 CHECK constraints via the SQLite table-rebuild dance**:
    SQLite `ALTER TABLE` does NOT support adding `CHECK` constraints to an existing table. The migration must rebuild the table:
    1. `CREATE TABLE intent_envelope_new (… all existing columns …, contactRefId TEXT, contactRefName TEXT, contactRefSource TEXT, CHECK (contactRefSource IN ('manual','device_contacts','phone_history') OR contactRefSource IS NULL), CHECK ((contactRefId IS NULL) OR (contactRefSource IN ('device_contacts','phone_history'))))` — copy all existing column definitions verbatim from the v3 schema, append the three new nullable `TEXT` columns, append the two `CHECK` constraints.
    2. `INSERT INTO intent_envelope_new (<all v3 columns>) SELECT <all v3 columns> FROM intent_envelope` — copy every existing row; the three new columns default to `NULL` for existing rows.
    3. `DROP TABLE intent_envelope`
    4. `ALTER TABLE intent_envelope_new RENAME TO intent_envelope`
    5. Recreate the `index_intent_envelope_intent` index (and any other indexes that were on the v3 table) on the renamed table — Room expects them to exist post-migration.
  - Wrap Steps 1 and 2 in a single SQLite transaction. Step 1 must run BEFORE Step 2 (the rename + history append operates on the v3 column set; the rebuild then copies the already-renamed rows into the new table).
- [ ] **T016-012** — Register `MIGRATION_3_4` in `OrbitDatabase.databaseBuilder(...).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)`.
- [ ] **T016-013** — Run `:app:assembleDebug` to generate `app/schemas/com.capsule.app.data.OrbitDatabase/4.json`. Commit it. Verify the exported JSON reflects (a) the three new column triplet (`contactRefId`, `contactRefName`, `contactRefSource`, all `TEXT NULLABLE`) and (b) the two CHECK constraints under the table's `tableInfo > checks` (or equivalent Room schema field). If the constraints are missing from the export, the table-rebuild dance in T016-011 was incomplete — fix and re-export.
- [ ] **T016-014** — Author `app/src/androidTest/java/com/capsule/app/data/OrbitDatabaseMigrationV3toV4Test.kt`. Fixtures (one envelope each):
  - `WANT_IT` with `intentHistoryJson="[]"`: assert renamed to `REMIND_ME`, terminal layer present with `at` matching ISO-8601 UTC pattern (`Instant.parse(layer.at)` succeeds and is within the test run window).
  - `WANT_IT` with non-empty history (multi-layer): assert FR-013 (preserves prior layers, appends rename layer at terminal index, terminal layer's `at` is ISO-8601).
  - `INTERESTING` with `intentHistoryJson="[]"`.
  - **`INTERESTING` with malformed JSON** (e.g., `intentHistoryJson="{"`, an unterminated object): MUST assert (1) `Log.w("migration_3_4_malformed_history", <row_id>)` is emitted (capture via a `ShadowLog` or instrumented log buffer), (2) the rename to `INSPIRATION` completes successfully, (3) `intentHistoryJson` post-migration is a fresh single-element JSON array containing only the rename layer (prior malformed content is GONE — assert `JSONArray(intentHistoryJson).length() == 1`).
  - `REFERENCE`, `FOR_SOMEONE`, `AMBIGUOUS` (one each, `intentHistoryJson="[]"`): assert no rename, no new layer, JSON byte-identical pre/post.
  - One `FOR_SOMEONE` row with non-empty history: assert no new layer, history byte-identical, and the new contact-ref columns exist with `NULL` values.
  - Schema introspection: assert `contactRefId`, `contactRefName`, `contactRefSource` columns exist with type `TEXT` and are nullable. Assert both CHECK constraints appear in the table's DDL (`SELECT sql FROM sqlite_master WHERE name='intent_envelope'` and grep for the two `CHECK (...)` clauses).
- [ ] **T016-015** — Author `OrbitDatabaseMigrationV3toV4AuditPreservationTest` (or fold into the same test class as a separate `@Test` method). Specifically asserts FR-013: an envelope with N prior layers retains all N layers byte-identically (modulo the appended layer) and the new layer is at index N (terminal).
- [ ] **T016-016** — Run `:app:connectedDebugAndroidTest --tests "com.capsule.app.data.OrbitDatabaseMigrationV3toV4*"` on an emulator (or document the skip if no emulator is available; the JVM-side `MigrationTestHelper` may suffice depending on Room version).
- [ ] **T016-VERIFY-CHECK** — Add two negative-case insert assertions to `OrbitDatabaseMigrationV3toV4Test` (or a sibling class `OrbitDatabaseV4CheckConstraintTest` if cleaner). After the migration completes, attempt:
  1. `INSERT INTO intent_envelope (… , contactRefSource) VALUES (… , 'invalid_value')` — assert SQLite throws a `SQLiteConstraintException` with a message referencing the CHECK constraint (the source-vocabulary constraint, FR-005 (a)).
  2. `INSERT INTO intent_envelope (… , contactRefId, contactRefSource) VALUES (… , 'abc', 'manual')` — assert SQLite throws a `SQLiteConstraintException` for the id-source coupling constraint (FR-005 (b)).
  Both inserts MUST otherwise be valid (all `NOT NULL` columns populated with valid values) so the failure is unambiguously attributable to the CHECK constraint, not a different schema violation.

**Gate (Phase 2)**:
- `:app:assembleDebug` succeeds; `4.json` is generated and committed; both CHECK constraints are present in the exported schema.
- All Phase-2 migration tests pass, including the malformed-history fixture (T016-014) and the two CHECK-constraint negative-case inserts (T016-VERIFY-CHECK).
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
- [ ] **T016-019** — Sweep `app/src/main/**` for `"Unassigned"` and replace with `"—"` (em-dash, **U+2014**). Sweep scope: ALL of `app/src/main/**` INCLUDING `app/src/main/res/values*/strings.xml` (every locale variant, e.g. `values/`, `values-es/`, `values-fr/`, etc.) AND `app/src/main/aidl/**/*.aidl`. EXCLUDES `app/src/test/**` and `app/src/androidTest/**` (test fixtures may legitimately reference the historical string for migration testing). Audited Kotlin sites (non-exhaustive — re-grep at task time):
  - `EnvelopeCard.kt:387`
  - `EnvelopeDetailScreen.kt:496`
  - Any `<string name="…">Unassigned</string>` entries in every `app/src/main/res/values*/strings.xml`.
  - Any `Unassigned` references in `app/src/main/aidl/**/*.aidl`.
  Post-sweep grep gates (BOTH must pass):
  1. `grep -rn "Unassigned" app/src/main` → zero matches.
  2. `grep -rn '"–"' app/src/main` (en-dash **U+2013**) → zero matches in label-resolver call sites. Only U+2014 (em-dash) is permitted as a label substitute; U+2013 is a different glyph and MUST NOT appear in this role.
- [ ] **T016-020** — Sweep `app/src/main` for the user-facing string `"Ambiguous"` (capital A, double-quoted) and replace with `"—"`. Currently affects:
  - `SilentWrapPill.kt:176` (covered by T016-007 already; T016-020 is the verification grep).
- [ ] **T016-021** — Update Compose preview functions in the affected files to render envelopes with the new labels. Confirm previews compile.
- [ ] **T016-022** — Author `app/src/androidTest/java/com/capsule/app/overlay/ChipRowPaletteTest.kt`: launches the `ChipRow` Composable, asserts the rendered chips are exactly the five new labels in the documented order, and that no chip carries `Intent.AMBIGUOUS`.
- [ ] **T016-023** — Author `app/src/androidTest/java/com/capsule/app/diary/ui/EnvelopeCardLabelTest.kt` (or extend an existing test): renders an `EnvelopeCard` with `intent=AMBIGUOUS` and asserts the visible label text is exactly `"—"`.

**Gate (Phase 3)**:
- `:app:assembleDebug` clean.
- Phase-3 instrumented tests pass.
- `grep -rn "Unassigned\|\"Ambiguous\"" app/src/main` returns zero matches.
- `grep -rn '"–"' app/src/main` (en-dash U+2013) returns zero matches in label-resolver call sites.

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
| FR-003 (audit layer on rename, ISO-8601 timestamp) | T016-011 + T016-014 + T016-015 |
| FR-004 (no layer on no-change) | T016-014 |
| FR-005 (contactRef columns + 2 CHECK constraints) | T016-011 + T016-013 + T016-014 + T016-VERIFY-CHECK |
| FR-006 (chip palette = 5) | T016-017 + T016-018 + T016-022 |
| FR-007 (Unassigned → "—", em-dash sweep scope) | T016-019 + T016-023 |
| FR-008 (when-exhaustive coverage) | T016-005..008 + Kotlin compiler |
| FR-009 (defensive fallback) | Existing `toIntentOrAmbiguous` and `Intent.valueOf` fallback unchanged |
| FR-010 (DB version bump + 4.json with CHECK constraints) | T016-010 + T016-013 |
| FR-011 (migration registered) | T016-012 |
| FR-012 (V3→V4 migration test, malformed-history fixture) | T016-014 |
| FR-013 (audit-history preservation) | T016-015 |
