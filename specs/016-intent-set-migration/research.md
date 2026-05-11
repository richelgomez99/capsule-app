# Research: Intent Set Extension

## 1. Product Label Preservation

Physical-device/product review on 2026-05-11 superseded the earlier rename plan. The durable product set is now:

- `WANT_IT` -> `Want it`
- `REFERENCE` -> `Reference`
- `READ_LATER` -> `Read later`
- `FOR_SOMEONE` -> `For someone`
- `INTERESTING` -> `Interesting`
- `AMBIGUOUS` -> defensive sentinel only

The important reversal is that `WANT_IT` must not become `REMIND_ME`, and `INTERESTING` must not become `INSPIRATION`. Those names were speculative and do not match the current product direction.

## 2. Why Add `READ_LATER`

`READ_LATER` separates “I want to consume this later” from both `REFERENCE` and `INTERESTING`:

- `REFERENCE` is material the user expects to look up or cite.
- `READ_LATER` is material the user expects to return to and consume.
- `INTERESTING` remains a broader curiosity bucket.

This avoids forcing saved articles/videos into `INTERESTING` while preserving the user's existing mental model.

## 3. Why No Historical Recategorization

Existing rows are already user-authored decisions. Rewriting `INTERESTING` rows into `READ_LATER` heuristically would guess at user intent from content and create silent mistakes. Spec 016 only adds a new choice for future captures.

## 4. `AMBIGUOUS` Stays Internal

`AMBIGUOUS` is still needed for timeout, predictor failure, scrubber fallback, cloud parse fallback, and defensive DB parsing. Removing it would create cross-process risk for little product value. The correct behavior is to keep it storable and parseable while excluding it from user-pickable chip palettes.

## 5. Cloud Classifier Alignment

The Supabase gateway should speak the same closed set as Android. Prompting for abstract labels like `TASK` or for rejected labels like `REMIND_ME` creates mapping ambiguity. The classifier prompt, allowlist, and tests should use the Android enum names directly.

## 6. ContactRef Storage

Three nullable columns on `intent_envelope` remain the right v1 shape for future `FOR_SOMEONE` follow-ups. One envelope references at most one contact, Diary reads stay simple, and a future multi-recipient design can migrate to a join table when the product actually needs it.

## 7. Cluster And AIDL Independence

Cluster similarity is embedding-driven and does not consume the `Intent` enum. AIDL parcel surfaces carry intent as `String`, so adding `READ_LATER` flows through existing string parcels. ContactRef parcel fields are deferred until a UI or IPC consumer exists.
