# Capture Overlay Follow-ups

> Tracking note only. The owning Spec Kit buckets are now:
>
> - `specs/015-visual-refit/` on `015-visual-refit` / `015-*` phase branches
> - `specs/016-intent-set-migration/` on `016-intent-set-migration`
> - `specs/017-capture-feedback-actions/` on `017-capture-feedback-actions`

## Current Dirty Worktree Split

The former `qa/015-017-stacked` dirty worktree has been split into owning
branches as of 2026-05-12. Use this map to review the applied branch changes
before opening PRs.

Verification note from the 2026-05-11 autonomous pass: `:app:compileDebugKotlin`,
`:app:testDebugUnitTest`, `:app:compileDebugAndroidTestKotlin`, `:app:lintDebug`,
and `:build-logic:lint:test` are green with the current dirty tree. Connected
Compose tests on Tab S9 fail before assertions with `No compose hierarchies
found`; the same failure reproduces on pre-existing `createComposeRule()` tests,
so treat that as a test-infrastructure issue before using connected Compose
results as a product signal.

### 015 visual refit / source identity

- `design/visual-refit-2026-04-29/**`
- `specs/015-visual-refit/**`
- `app/src/main/java/com/capsule/app/CapsuleApplication.kt`
- `app/src/main/java/com/capsule/app/diary/ui/ClusterSuggestionCard.kt`
- `app/src/main/java/com/capsule/app/diary/ui/ClusterDetailScreen.kt`
- `app/src/main/java/com/capsule/app/diary/ui/DiaryScreen.kt` visual refit and `SourceIdentityResolver` consumption
- `app/src/main/java/com/capsule/app/overlay/CaptureSheetUI.kt` visual refit and `SourceIdentityResolver` consumption
- `app/src/main/java/com/capsule/app/overlay/BubbleUI.kt`
- `app/src/main/java/com/capsule/app/settings/SettingsActivity.kt`
- `app/src/main/java/com/capsule/app/settings/SettingsScreen.kt`
- `app/src/main/java/com/capsule/app/ui/MainActivity.kt` capture setup visual/product refit
- `app/src/main/java/com/capsule/app/service/ServiceHealthMonitor.kt` false-active hardening retained as internal diagnostics
- `app/src/main/java/com/capsule/app/service/CapsuleOverlayService.kt` stop-action preference/status consistency
- `app/src/androidTest/java/com/capsule/app/service/ServiceHealthMonitorTest.kt`
- `app/src/main/java/com/capsule/app/ui/primitives/IntentChip.kt`
- `app/src/main/java/com/capsule/app/ui/primitives/SourceIdentityResolver.kt`
- `app/src/main/res/drawable/ic_orbit_logo_mark.xml`
- `app/src/main/res/drawable/ic_capsule_notification.xml`
- `app/src/main/res/drawable/ic_launcher_background.xml`
- `app/src/main/res/drawable/ic_launcher_foreground.xml`
- deleted stale density launcher WebPs under `app/src/main/res/mipmap-*/`
- `app/src/test/java/com/capsule/app/ui/primitives/SourceIdentityResolverTest.kt`
- `app/src/androidTest/java/com/capsule/app/diary/DiaryClusterSuggestionCardTest.kt`
- `app/src/androidTest/java/com/capsule/app/settings/SettingsScreenTest.kt`

### 016 intent set migration / classifier alignment

- `app/src/main/java/com/capsule/app/data/model/Intent.kt`
- `app/src/main/java/com/capsule/app/ai/DigestComposer.kt`
- `app/src/main/java/com/capsule/app/diary/ui/EnvelopeCard.kt`
- `app/src/main/java/com/capsule/app/diary/ui/EnvelopeDetailScreen.kt`
- `app/src/main/java/com/capsule/app/overlay/ChipRow.kt` semantic addition of `READ_LATER`
- `app/src/main/java/com/capsule/app/overlay/SilentWrapPill.kt`
- `app/src/main/java/com/capsule/app/ui/IntentChipPicker.kt`
- `supabase/functions/llm_gateway/handlers/classify_intent.ts`
- `supabase/functions/llm_gateway/lib/allowlists.ts`
- `supabase/functions/llm_gateway/test/anthropic_handlers.test.ts`

Spec 016's older branch text still says `REMIND_ME` / `INSPIRATION`; it must be
amended before implementation to preserve `WANT_IT` / `INTERESTING` and add
`READ_LATER`.

### 017 capture feedback actions / overlay hardening

- `specs/017-capture-feedback-actions/**`
- `app/src/androidTest/java/com/capsule/app/data/UrlHashDedupeContractTest.kt`
- `app/src/main/java/com/capsule/app/data/EnvelopeRepositoryImpl.kt`
- `app/src/main/java/com/capsule/app/data/EnvelopeStorageBackend.kt`
- `app/src/main/java/com/capsule/app/data/LocalRoomBackend.kt`
- `app/src/main/java/com/capsule/app/data/OrbitMigrations.kt`
- `app/src/main/java/com/capsule/app/data/dao/IntentEnvelopeDao.kt`
- `app/src/main/java/com/capsule/app/overlay/PostCaptureOverlay.kt`
- `app/src/main/java/com/capsule/app/service/CapsuleOverlayService.kt` compact post-capture window bounds, live landscape metrics, and stop/remove consistency if not kept with 015 setup QA

The current geometry fix may land before the full duplicate-capture behavior if
it is treated as capture hardening, but it should not be hidden inside the visual
refit PR unless the PR title/body calls out the behavior fix explicitly.

Physical testing on S24 and Tab S9 surfaced a set of related product and overlay issues that should be treated as one capture-quality track, not scattered polish tickets.

## 1. Duplicate Capture UX

Current behavior: `EnvelopeRepositoryImpl.seal()` always creates a new `intent_envelope` row. The existing URL dedupe only reuses `ContinuationResultEntity` hydration results after a canonical URL result exists; it does not dedupe the user-visible capture itself.

Target behavior:

- If a user captures the same canonical URL or exact text again, Orbit should not silently create an indistinguishable second row.
- The overlay should acknowledge the event as already saved.
- The user should get a compact action path to add a note, reclassify the intent, or open the existing capture.
- This should work even when the repeat is not immediate, because the user may think a save failed and try again later.

Implementation plan:

1. Add an envelope-level duplicate key, preferably `primaryCanonicalUrlHash` for URL captures plus populated `textContentSha256` for exact non-URL text.
2. Add DAO lookups for non-deleted, non-archived existing captures by duplicate key within a product-defined window.
3. Replace `seal(): String` semantics at the overlay boundary with a richer result: `Created(envelopeId)` vs `AlreadySaved(existingEnvelopeId, matchedBy)`.
4. Add `PostCaptureUi.AlreadySaved` with actions for note, intent reclassify, and open existing capture.
5. Audit duplicate events separately from normal envelope creation.
6. Backfill hashes for existing rows where possible.

## 2. Source Identity Consistency

Current fix: Capture and Diary now share provider-first glyph resolution, so YouTube URLs copied from browsers/messages render with the YouTube glyph.

Next steps:

- Extend the shared resolver to cluster/detail surfaces.
- Render copy as provider plus origin when they differ, e.g. `YouTube via Brave`.
- Keep category as a fallback only; `VIDEO` should not imply YouTube unless the URL or foreground app proves it.

## 3. Quiet Almanac Overlay Refit

Still old-theme surfaces:

- Bubble / draggable launcher.
- Post-capture intent chip row.
- Undo, already-saved, and confirmation pills.
- Capture-overlay settings subpages.

Target direction:

- Use the actual prototype only as visual inspiration: quiet dark surface, cream/amber accents, soft orbit mark language.
- Keep product copy specific to Orbit control/privacy behavior, not prototype philosophy copy.
- Preserve user-approved intent names: Want it, Reference, Read later, For someone, Interesting.

## 4. Overlay Geometry QA

Current fix: compact post-capture pills use `WRAP_CONTENT` width so the whole bottom row is not blocked, while chip rows can still use full width. Bubble drag now uses current window metrics so landscape rotation does not retain portrait bounds.

Verified in the 2026-05-12 Phase 5 QA pass:

- Drag-to-remove in landscape on phone and tablet.
- Dismiss target reachability near gesture nav/system bars.
- Undo pill only blocks its visible bounds.
- Chip row still has enough width and does not clip on small screens.

Remaining QA:

- Verify the bubble hit target and drag affordance with TalkBack/accessibility
  touch exploration enabled.

## 5. Overlay Customization Follow-up

Bubble size and transparency controls are intentionally deferred out of spec
015 Phase 5. The visual refit should keep one stable default tap target and one
alpha value through Demo Day QA; a follow-up customization spec can add
persisted settings, bounded defaults, and a full tap/drag reliability matrix
for every supported size and transparency value.

## 6. Settings Copy And Naming Audit

Outstanding issue: nested settings pages still use the old visual treatment and some old `Capsule` naming.

Plan:

- Audit every settings route and setup screen for `Capsule` copy.
- Convert nested pages to the Quiet Almanac settings components.
- Keep names scoped: app/product is `Orbit`; package/process/internal names can remain technical until a larger rename is scheduled.
