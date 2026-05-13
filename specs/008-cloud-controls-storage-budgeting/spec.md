# Cloud Controls, Storage, And Budgeting

**Status**: Rebaselined active slot placeholder - generate fresh Speckit artifacts before implementation.

**Branch**: `008-cloud-controls-storage-budgeting`

## Purpose

Define user-visible controls for Orbit Cloud, LLM routing, storage categories, budgeting, deletion/export, and provider fallback.

## Inputs To Preserve

- Provider, BYOK, budget, and consent ideas from archived `005-cloud-boost-byok-llm`.
- Storage/sync/export/deletion ideas from archived `006-orbit-cloud-storage`.
- BYOC ideas from archived `009-byoc-sovereign-storage` as later power-user extension input.

## Stop Signs

- Do not promise BYOC as the default path.
- Cloud copies are never authoritative over the local SQLCipher corpus.
- Audit log and consent ledger remain local unless a later explicit policy changes that.
