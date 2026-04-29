# Quickstart: Spec 016 — Intent Set Migration

> Pickup guide for the engineer (or agent) implementing spec 016.

## TL;DR

Rename the four shipped intent labels to the v2 set, add a fifth user-pickable `READ_LATER`, retain `AMBIGUOUS` as a non-pickable sentinel, retire the user-facing `"Unassigned"` copy in favour of em-dash `"—"`, and add three nullable contact-ref columns to `intent_envelope` for forward-compat with the `FOR_SOMEONE` follow-up flow (UI deferred to spec 017).

## Mapping (memorise this)

| V3 enum | V4 enum | Action |
|---|---|---|
| `WANT_IT` | `REMIND_ME` | Rename + audit layer |
| `INTERESTING` | `INSPIRATION` | Rename + audit layer (semantically lossy — see `research.md`) |
| `REFERENCE` | `REFERENCE` | Unchanged |
| `FOR_SOMEONE` | `FOR_SOMEONE` | Unchanged; gains nullable `ContactRef` |
| `AMBIGUOUS` | `AMBIGUOUS` | Unchanged; sentinel only, **not** a chip |
| _(none)_ | `READ_LATER` | NEW user-pickable label |

User-pickable count: 5. Total enum values: 6. Chip palette display order: `Remind me, Inspiration, Reference, Read later, For someone`.

## Implementation order

Follow `tasks.md` phases 1–5 in order. Phases 1 + 2 must land in the same PR (Room schema/code coupling). Phase 3 can land in a follow-up PR. Phases 4 + 5 are verification + sign-off.

## Critical gates

- **Migration test must include a malformed-history fixture.** A row whose `intentHistoryJson` is unparseable JSON must complete the migration without crashing (the migration replaces with a fresh single-element array). FR-013.
- **Audit-history preservation test must verify the rename layer is at index N (terminal), not index 0.** Prior layers are append-only.
- **Sweep test**: `grep -rn "Unassigned\|\"Ambiguous\"" app/src/main` must return zero matches after Phase 3.
- **Cluster engine independence**: `grep -n "Intent\." app/src/main/java/com/capsule/app/cluster/*.kt` must still return zero matches after the migration. (Verifies cluster code wasn't accidentally coupled.)
- **AIDL parcels carry `intent` as `String`**, not as enum. No `.aidl` file edits required for the enum rename. ContactRef parcel fields are NOT added at v1.

## Things to know about the existing code

- `Intent.AMBIGUOUS` is referenced from **eleven** code paths (predictor fallback, silent-wrap predicate, auto-ambiguous timeout seal, cloud-LLM parse fallback, DAO query exclusion, etc.). Do not delete the enum value — that would cascade unsafely. It stays; only the chip palette stops offering it.
- `ActionsRepositoryDelegate.kt:297` creates a derived todo-seed envelope with `intent = Intent.WANT_IT`. This won't compile after Phase 1. Recommendation: switch to `Intent.REMIND_ME` (closest semantic match for a todo). Update the KDoc at line 260 to match. See `plan.md` § "ActionsRepositoryDelegate todo-seed intent".
- `IntentEnvelopeDao.kt:156` has the SQL literal `AND intent != 'AMBIGUOUS'`. The literal is still valid post-migration (sentinel value name unchanged). No code change needed.
- `DigestComposer.kt:193-197` uses string-keyed `when` (not enum-typed). Manually update keys; the Kotlin compiler will not catch missing branches there.
- Cluster code (`SimilarityEngine`, `ClusterDetector`, `ClusterDetectionWorker`) does NOT use the Intent enum — confirmed by grep. Migration does not affect cluster logic.

## Spec 015 cross-reference

Spec 015 (`015-visual-refit`) defines the IntentChip token surface (color, font, spacing) that this migration's chip palette consumes. **Spec 015 is currently on its own branch and gates on PR #5 merging to main.** This spec (016) references 015 in prose only — no relative-path Markdown links to `specs/015-visual-refit/` until both ship.

If 015 ships first, 016's chip-palette code can call into spec-015 tokens directly. If 016 ships first, the chip palette uses placeholder tokens (e.g., `Icons.Filled.Schedule` for `READ_LATER`) and 015 can replace them on its merge.

## After merge, propagate to docs

The founder kit and design doc list intent-set versions that don't match shipped code (three years of docs/code drift). After spec 016 merges, schedule a **30-min sweep** to update the following surfaces. **This is NOT spec 016's job** — too much scope and not all surfaces are in this repo:

- `product-dna.md` (founder kit synthesis) — § 2 (the wedge), § 7 (capability list)
- `personas.md` — Maya, Marcus, Anna, David example saves reference intent labels in passing
- `master-faq.md` — questions 9 and 11 reference the intent set
- `01-pitch/one-liners.md` — the 30-second pitch mentions intent labels
- `richelgomez-spec-002-intent-envelope-and-diary-design-...md` — the original design doc
- The design canvas `orbit-screen-capture.jsx` and `orbit-screen-diary.jsx` (in `../capsule-app-visual-refit/design/visual-refit-2026-04-29/`) — sibling worktree update needed

The current shipped code has been (`WANT_IT, INTERESTING, REFERENCE, FOR_SOMEONE, AMBIGUOUS`). After spec 016 the shipped set is (`REMIND_ME, INSPIRATION, REFERENCE, READ_LATER, FOR_SOMEONE, AMBIGUOUS`). Founder kit currently lists `[remind me, inspiration, in orbit, archive]` and the design canvas lists `[in orbit, remind me, inspiration, reference, read later]` — neither matches the actual code. The post-merge sweep aligns all of these to the v2 set above.

## Out of scope for this spec

- Capture-sheet contact-picker UI (deferred to spec 017).
- "Text Maya?" follow-up flow (deferred to spec 018+).
- Diary mini-intent display of contact names (deferred).
- Cluster engine / similarity logic (verified embedding-driven, intent-independent).
- Marketing copy propagation (post-merge sweep above).

## Status

- **Planning**: this branch (`016-intent-set-migration`).
- **Implementation**: separate branch + PR after planning merges.
