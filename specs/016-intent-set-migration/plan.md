# Plan: Intent Set Migration (Spec 016)

## Constitution check

- **Predictable, additive migrations** ✅ — `MIGRATION_3_4` is purely additive (renames in place, adds nullable columns, appends to existing JSON array). No row deletes, no column drops, no destructive rewrites.
- **Audit completeness** ✅ — Every renamed row gets a layer with `migrationReason`. The audit-history preservation test (FR-013) is mandatory.
- **LLM Sovereignty / Encryption** ✅ — Out of scope; no encrypted columns touched.
- **No "ambiguous gets a chip"** ✅ — `AMBIGUOUS` retained as enum sentinel; removed from chip palettes.
- **No marketing copy in code** ✅ — Copy changes are user-facing labels only (`"Unassigned" → "—"`); no taglines or product copy in this migration.
- **Testability** ✅ — Migration tested with golden fixtures; chip-palette change verifiable via Compose preview / instrumented assertion.

## Phase plan

This migration is **planning-only** in this spec branch. Implementation happens in a follow-up branch. The phases below are the implementation order; they map 1:1 to the commit boundaries in `tasks.md`.

### Phase 0 — Spec authoring (this commit)

Single commit: `docs(016): planning — intent set migration`. Pushes the planning artifacts; no code change.

### Phase 1 — Enum + entity surface (foundational, no UI yet)

- Update `Intent.kt` to the new six-value set.
- Update `IntentSource.kt` to add `MIGRATION`.
- Add `ContactRef` data class.
- Add three nullable contact-ref columns to `IntentEnvelopeEntity`.
- Update every `when (intent: Intent)` exhaustive branch in `app/src/main/**`. Audited call-sites:
  - `EnvelopeCard.kt` (label resolver) — rename branches + `AMBIGUOUS → "—"` + add `READ_LATER` branch.
  - `EnvelopeDetailScreen.kt` (label resolver) — same.
  - `SilentWrapPill.kt` (`Intent.label()` extension) — same.
  - `DigestComposer.kt` (string-keyed `when`) — rename keys + add `READ_LATER` and `INSPIRATION` keys.
  - Any other Compose preview / test fixture that hard-codes `Intent.WANT_IT` or `Intent.INTERESTING`.
- Update string-literal references in DAO queries: `IntentEnvelopeDao.kt:156` (`AND intent != 'AMBIGUOUS'`) — no change, the literal stays valid.
- Verify build: `:app:compileDebugKotlin` clean.

**Gate**: compiles + unit tests for entity/enum pass. No migration registered yet (DB version still 3 in this phase if we want a clean isolation, or bumped to 4 with migration in Phase 2 — see tasks.md for the exact split).

### Phase 2 — Migration

- Bump `OrbitDatabase.version` to 4.
- Author `MIGRATION_3_4` in `OrbitMigrations.kt`:
  - `UPDATE intent_envelope SET intent='REMIND_ME' WHERE intent='WANT_IT'`
  - `UPDATE intent_envelope SET intent='INSPIRATION' WHERE intent='INTERESTING'`
  - For each row in those two updates, also append a layer to `intentHistoryJson` (single SQL `UPDATE` using `json_insert(...)` or, if `json1` is not guaranteed on min-SDK SQLite, a Kotlin-side cursor pass — `json1` is bundled in Android since API 21, so direct SQL is fine).
  - `ALTER TABLE intent_envelope ADD COLUMN contactRefDisplayName TEXT`
  - `ALTER TABLE intent_envelope ADD COLUMN contactRefPhoneE164 TEXT`
  - `ALTER TABLE intent_envelope ADD COLUMN contactRefEmail TEXT`
- Register `MIGRATION_3_4` in `OrbitDatabase.databaseBuilder(...).addMigrations(...)`.
- Generate `app/schemas/com.capsule.app.data.OrbitDatabase/4.json` via `:app:assembleDebug` (Room's `exportSchema = true` writes it).
- Author migration test `OrbitDatabaseMigrationV3toV4Test` with golden fixtures (one row per legacy label + a malformed-history fixture + a multi-layer history fixture).

**Gate**: migration test passes; `:app:assembleDebug` succeeds; `4.json` is generated and committed.

### Phase 3 — UI sweep (chips + label copy)

- `ChipRow.kt`: replace four chips with five new chips in the documented order. Update `IntentChip(intent = Intent.WANT_IT, label = "Want it", ...)` → `Intent.REMIND_ME, "Remind me"`, etc. Add `Intent.READ_LATER, "Read later"` chip. Pick an icon for `READ_LATER` (defer icon-selection to spec 015's IntentChip palette if possible; otherwise use a sensible Material default like `Icons.Filled.Schedule` and flag it for spec 015 to override).
- `IntentChipPicker.kt`: replace four chips with five.
- All `Intent.AMBIGUOUS -> "Unassigned"` and `Intent.AMBIGUOUS -> "Ambiguous"` label sites → `"—"`. Audited sites:
  - `EnvelopeCard.kt:387`
  - `EnvelopeDetailScreen.kt:496`
  - `SilentWrapPill.kt:176`
- Verify `grep -rn "Unassigned\|\"Ambiguous\"" app/src/main` returns zero matches post-sweep.

**Gate**: Compose previews render correctly; instrumented chip-palette test asserts the five-chip surface; no `"Unassigned"`/`"Ambiguous"` literals in `app/src/main`.

### Phase 4 — DigestComposer + sentinel-path verification

- Update `DigestComposer.kt:193-197` string-keyed label `when` to use the new keys (`"REMIND_ME" -> "Remind me"`, `"INSPIRATION" -> "Inspiration"`, `"READ_LATER" -> "Read later"`, drop `"WANT_IT"` and `"INTERESTING"`, keep `"AMBIGUOUS"` returning `"—"` for consistency or `"Ambiguous"` if the digest is internal-only — decide per digest UX).
- Verify the auto-ambiguous sentinel paths still produce `Intent.AMBIGUOUS` correctly:
  - `OverlayViewModel.kt:293, 340` — sealed-in path, unchanged.
  - `CapsuleSealOrchestrator.kt:73, 78, 80, 98, 128, 140` — fallback / silent-wrap guard, unchanged.
  - `IntentPredictor.kt:47` — predictor failure default, unchanged.
  - `SilentWrapPredicate.kt:36, 58` — silent-wrap gate, unchanged.
  - `ScreenshotObserver.kt:120` — initial seal-on-capture default, unchanged.
  - `CloudLlmProvider.kt:225` — defensive parse fallback, unchanged.
  - `WeeklyDigestDelegate.kt:182` — delegate digest sentinel, unchanged.
  - `ActionsRepositoryDelegate.kt:297` — `WANT_IT` is the historical "todo seed" intent; **this needs review**: the comment at line 260 says `kind=REGULAR, intent=WANT_IT`. Decide whether todo-seed envelopes should now be `intent=REMIND_ME` (semantically more accurate) or stay legacy. Document the decision in the implementation commit; surface as a question in tasks.md.
- Run the full `:app:testDebugUnitTest` JVM test suite to catch any missed `when` exhaustive failure.

**Gate**: full unit-test suite green; lint baseline unchanged.

### Phase 5 — Smoke + sign-off

- `:app:assembleDebug` clean; `4.json` committed.
- `:app:lintDebug` baseline unchanged from parent branch.
- Manual smoke on a v3 fixture device (optional; the migration test covers the SQL).
- Open implementation PR.

## Out-of-band concerns

### `ActionsRepositoryDelegate` todo-seed intent

`ActionsRepositoryDelegate.kt:297` creates a derived envelope with `intent = Intent.WANT_IT` to seed a todo. After this migration, that line either:

- **(a)** Stays as `Intent.WANT_IT`, which won't compile because the enum value no longer exists. Forced change.
- **(b)** Becomes `Intent.REMIND_ME` — the closest semantic equivalent, and arguably more accurate ("remind me to do this todo"). Recommended.
- **(c)** Becomes `Intent.AMBIGUOUS` — defensive, lets the user choose. Probably wrong; todo-derived envelopes have a clear action-item character.

**Recommendation**: (b). Document in the implementation commit body; flag as `T016-013` in tasks.md.

### Spec 015 dependency posture

Spec 015's IntentChip palette is the visual home for the new chips. If 015 ships first, 016's chip-palette code can call into spec-015 tokens directly. If 016 ships first, the chip palette uses placeholder tokens and 015 can replace them on its merge. Either order is acceptable. Spec 016 references 015 in prose only (no relative-path links) until both ship.

### Cluster engine (verified independent)

`grep -n "Intent\." app/src/main/java/com/capsule/app/cluster/*.kt` — zero matches. Cluster code is not affected.

### AIDL surface (verified parcels carry strings)

`IntentEnvelopeDraftParcel.kt:10` and `EnvelopeViewParcel.kt:23` declare `val intent: String`. The enum-name change flows through the parcel boundary as a string-rename; no `.aidl` file edits required for the enum migration. ContactRef parcel fields are not added at v1 (no consumer; UI deferred to spec 017). When spec 017 wires the contact-picker, that spec's plan should add `contactRefDisplayName: String?` etc. to `IntentEnvelopeDraftParcel.aidl/.kt` and bump `EnvelopeViewParcel.aidl/.kt` analogously.

## Risk register

| Risk | Likelihood | Mitigation |
|---|---|---|
| Migration corrupts `intentHistoryJson` for a row with malformed JSON | Low | Migration handles malformed JSON by replacing with a fresh single-element layer (loses prior history but preserves the rename guarantee). Test fixture covers this case. |
| `READ_LATER` chip ships without a settled icon | Medium | Use a Material default (`Icons.Filled.Schedule`) and flag for spec 015 to refine. |
| `ActionsRepositoryDelegate` todo-seed intent change cascades into todo-seed UX in unexpected ways | Low-medium | Recommendation (b) above. Test the full todo-seed flow during implementation. |
| Alpha user installs the v4 build, then downgrades to v3 | Negligible | Room rejects downgrades by default; user would have to reinstall the app fresh. Acceptable. |
| `INTERESTING → INSPIRATION` lossiness causes user confusion | Medium | Audit log preserves original; reassign UI is one tap. Documented in `research.md` and `quickstart.md`. |
| AIDL parcel string round-trip emits a stale `WANT_IT` from a sibling app version that hasn't been updated | Negligible (single-app system) | `toIntentOrAmbiguous()` defensive fallback to `AMBIGUOUS` covers this. |

## Acceptance for the planning PR

The planning PR (this branch) is **docs-only**. Acceptance is:

- All six planning files exist (`spec.md`, `plan.md`, `data-model.md`, `research.md`, `tasks.md`, `quickstart.md`).
- `checklists/requirements.md` passes (no `[NEEDS CLARIFICATION]` markers, all P1/P2 stories independently testable).
- PR is opened as a draft against `main` for review.

Implementation lands in a separate branch in subsequent PRs, with the commit boundaries defined in `tasks.md`.
