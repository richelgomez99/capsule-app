# Retrieval And Ask Citations

**Status**: Rebaselined active slot placeholder - generate fresh Speckit artifacts before implementation.

**Branch**: `005-retrieval-and-ask-citations`

## Purpose

Build cited retrieval over captured envelopes and continuations, then expose Ask Orbit as an answer surface grounded in source envelopes and evidence.

## Inputs To Preserve

- Ask concepts from archived `004-ask-orbit`.
- Provider and quality tradeoff ideas from archived `005-cloud-boost-byok-llm`.
- Capture-understanding output contracts from `004-capture-understanding`.

## Stop Signs

- Answers require citations to source envelopes or derived evidence.
- Cloud inference must use the audited gateway path.
- No server-side prompt assembly from server-held user data.
