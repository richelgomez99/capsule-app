# Capture Understanding

**Status**: Rebaselined active slot placeholder - generate fresh Speckit artifacts before implementation.

**Branch**: `004-capture-understanding`

## Purpose

Define what Orbit can truthfully know about a capture after save: source identity, canonical URL handling, evidence bundles, Basic/Smart/Deep understanding controls, compact summaries, deletion/invalidation rules, and the capture detail insight surface.

## Inputs To Preserve

- Source/detail ideas from archived `004-ask-orbit`.
- Cloud routing and trace constraints from specs `013` and `014`.
- Duplicate, hydration, app-recognition, and feedback lessons from `017-capture-feedback-actions`.

## Stop Signs

- Do not build Ask Orbit here.
- Do not add KG tables before deletion and invalidation semantics exist.
- Do not send raw HTML, screenshots, embeddings, or evidence bundles over Binder.
- Do not add network clients outside `com.capsule.app.net.*`.
