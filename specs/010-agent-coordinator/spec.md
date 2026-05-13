# Agent Coordinator

**Status**: Rebaselined active slot placeholder - generate fresh Speckit artifacts before implementation.

**Branch**: `010-agent-coordinator`

## Purpose

Coordinate summarization, retrieval, memory candidates, and approved actions into agent flows that stay observable, consent-aware, and reversible.

## Inputs To Preserve

- Agent concepts from archived `008-orbit-agent`.
- Visual-agent voice lessons from archived `010-visual-polish-pass` and active `015-visual-refit`.

## Stop Signs

- No generic browser automation as a default capture strategy.
- No unapproved external writes.
- Agent traces should be no-content by default unless a later explicit policy says otherwise.
