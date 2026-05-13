# Resolution Semantics

**Status**: Rebaselined active slot placeholder - generate fresh Speckit artifacts before implementation.

**Branch**: `012-resolution-semantics`

## Purpose

Refresh capture lifecycle semantics after retrieval and actions produce real cases: saved, acknowledged, in motion, resolved, abandoned, snoozed, derived, and invalidated.

## Inputs To Preserve

- Lifecycle semantics from archived `012-resolution-semantics`.
- Approval/action runtime outcomes from `006-approval-action-runtime`.
- Retrieval and provenance behavior from `005-retrieval-and-ask-citations`.

## Stop Signs

- No guilt counters, streaks, or productivity-pressure UI.
- Resolution states must not claim real-world completion Orbit cannot observe.
- Syncable resolution state requires explicit cloud-control policy.
