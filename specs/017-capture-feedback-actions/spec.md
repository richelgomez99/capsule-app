# Feature Specification: Capture Feedback Actions

**Feature Branch**: `017-capture-feedback-actions`
**Created**: 2026-05-11
**Status**: Draft
**Input**: Physical-device testing on S24 and Tab S9 showed that duplicate saves,
post-capture feedback, and quick correction actions need a product-level flow,
not a loose follow-up note.

## Summary

When a user captures content that Orbit has already saved, Orbit should recognize
the repeat, avoid creating an indistinguishable duplicate diary row, and show an
`Already saved` feedback state with fast actions. The user can add a note,
reclassify the intent, or open the existing capture.

This is separate from spec 015 because it changes repository, IPC, and user
action semantics. It may consume the visual styling from spec 015, but the
duplicate contract must be correct with the new visual language both ON and OFF.

## Branch Placement

- Land on `017-capture-feedback-actions`, off fresh `origin/main` after spec 016
  is either merged or explicitly rebased into this branch if it touches the same
  intent picker surfaces.
- Do not land this behavior on `015-visual-refit`; spec 015 is presentation-only.
- Small overlay geometry fixes that preserve existing visuals may land here as a
  Phase 0 hardening slice if they are needed for the `Already saved` / undo
  feedback contract.

## User Scenarios & Testing

### User Story 1 - Duplicate URL capture is acknowledged, not duplicated (P1)

A user captures the same YouTube or article URL again because they are unsure it
saved. Orbit recognizes the canonical URL, keeps the existing capture as the
primary record, and shows an `Already saved` state instead of silently inserting
a second identical row.

**Independent Test**: Save URL A. Capture the same URL again with tracking
parameters, fragment changes, or host casing differences covered by
`CanonicalUrlHasher`. Assert the second capture returns an `AlreadySaved` seal
result and no second user-visible envelope row is inserted.

### User Story 2 - Duplicate text capture is acknowledged (P1)

A user captures the same exact non-URL text again later. Orbit recognizes the
exact text hash and shows `Already saved`, with the same action affordances.

**Independent Test**: Save exact text T. Capture exact text T again after the
undo window has expired. Assert `AlreadySaved` and no duplicate row.

### User Story 3 - User can correct or enrich the existing capture (P2)

From the `Already saved` state, the user can add a note, reclassify the intent,
or open the existing diary entry.

**Independent Test**: Trigger an `Already saved` state, tap reclassify, choose a
new intent, and assert the existing envelope receives the new intent layer rather
than creating a new envelope.

## Requirements

- **FR-017-001**: Repository seal logic MUST return a typed result:
  `Created(envelopeId)` or `AlreadySaved(existingEnvelopeId, matchedBy)`.
- **FR-017-002**: Duplicate URL detection MUST use an envelope-level canonical
  URL key, not only continuation-result reuse. Pending and hydrated captures must
  both be detectable. For v1, when a capture contains multiple URLs, the primary
  duplicate key is the first URL returned by `ContinuationEngine.extractUrls(...)`.
- **FR-017-003**: Exact non-URL text duplicate detection MUST populate and query
  `textContentSha256` for non-deleted, non-archived envelopes.
- **FR-017-004**: The overlay/Binder contract MUST carry the typed seal result
  across process boundaries without stringly encoding.
- **FR-017-005**: `Already saved` feedback MUST offer note, reclassify, and open
  existing capture actions. Note support is backed by this feature's note data
  model; do not add an ad-hoc text field outside the spec 017 migration.
- **FR-017-006**: Duplicate matching MUST ignore deleted envelopes. Archived
  envelopes are not shown as blocking duplicates unless product copy explicitly
  offers to restore/open them.
- **FR-017-007**: Every duplicate event MUST write an audit entry distinct from
  normal `ENVELOPE_CREATED`.
- **FR-017-008**: Existing URL hydration dedupe behavior MUST remain intact; this
  feature adds envelope-level duplicate handling, it does not remove hydration
  result reuse.
- **FR-017-009**: Compact post-capture feedback states MUST use visible-content
  touch bounds. Chip rows may span the screen when visibly full-width; compact
  pills must not block adjacent launcher/app icons. Bubble drag and dismiss
  bounds MUST use live window metrics after orientation changes.

## Success Criteria

- **SC-017-001**: Capturing the same canonical URL twice results in one visible
  diary row and one duplicate audit event.
- **SC-017-002**: Capturing the same exact text twice results in one visible
  diary row and an `Already saved` overlay state.
- **SC-017-003**: Reclassify from `Already saved` updates the existing envelope's
  intent history and does not create a second envelope.
- **SC-017-004**: S24 and Tab S9 physical QA confirms adjacent app icons remain
  tappable outside the visible feedback pill.
- **SC-017-005**: Existing URL hydration-result reuse tests still pass after
  envelope-level duplicate handling is added.

## Dependencies

- Spec 016 owns durable intent enum/label migration and should merge before this
  branch if this work touches intent picker labels.
- Spec 015 owns visual treatment for post-capture pills and chip rows. This spec
  may use those components but must keep behavior testable with the flag OFF.

## Out of Scope

- Quiet Almanac visual styling for the bubble or settings screens (spec 015).
- Cloud URL hydration provider changes.
- Multi-recipient contact routing beyond a single quick reclassify/contact stub.