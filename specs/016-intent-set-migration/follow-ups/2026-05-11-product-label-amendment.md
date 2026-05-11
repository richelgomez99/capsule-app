# Follow-up: Preserve Want It / Interesting Product Labels

**Date**: 2026-05-11
**Owning branch**: `016-intent-set-migration`
**Reason**: Physical-device/product review superseded the older spec text that
renamed `WANT_IT` to `REMIND_ME` and `INTERESTING` to `INSPIRATION`.

## Required Amendment

Spec 016 must preserve the durable/product labels:

- `WANT_IT` displayed as `Want it`
- `REFERENCE` displayed as `Reference`
- `READ_LATER` displayed as `Read later`
- `FOR_SOMEONE` displayed as `For someone`
- `INTERESTING` displayed as `Interesting`
- `AMBIGUOUS` retained as defensive sentinel

Do not rename `WANT_IT` to `REMIND_ME` or `INTERESTING` to `INSPIRATION` unless
the user explicitly changes product language later.

## Impact On Existing Spec 016 Tasks

- Replace enum task text that currently says `REMIND_ME` / `INSPIRATION` with
  the preserved enum names above plus `READ_LATER`.
- Remove migration tasks that rewrite existing `WANT_IT` or `INTERESTING` rows;
  those rows should stay semantically stable.
- Keep any migration needed solely to add `READ_LATER` compatibility and future
  `ContactRef` fields.
- Keep chip palette tasks, but render chips in this order: `Want it`,
  `Reference`, `Read later`, `For someone`, `Interesting`.
- Keep Supabase classifier alignment, but align to the preserved Android enum
  names above.