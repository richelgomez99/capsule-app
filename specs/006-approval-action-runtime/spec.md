# Approval Action Runtime

**Status**: Rebaselined active slot placeholder - generate fresh Speckit artifacts before implementation.

**Branch**: `006-approval-action-runtime`

## Purpose

Turn useful action ideas into explicit, user-approved runtime flows. External writes stay local and user-approved, and Orbit records what happened in the local audit log.

## Inputs To Preserve

- Orbit Actions concepts from `003-orbit-actions`.
- Storage/sync constraints from archived `006-orbit-cloud-storage` only where they affect action provenance.

## Stop Signs

- No silent external writes.
- No agent autonomy beyond explicit approval contracts.
- Do not hide failures or retries from the audit surface.
