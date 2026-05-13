# Memory Candidates Inspector

**Status**: Rebaselined active slot placeholder - generate fresh Speckit artifacts before implementation.

**Branch**: `007-memory-candidates-inspector`

## Purpose

Create the user-facing control surface for candidate memories before durable KG or agent memory storage exists.

## Inputs To Preserve

- Memory and graph ideas from archived `007-knowledge-graph`.
- Consent and local-only tag language from the architecture research docs.

## Stop Signs

- Candidate memories are not durable facts until approved or confirmed by policy.
- Every memory candidate needs provenance.
- Local-only/sensitive candidates must not cross `:net`.
