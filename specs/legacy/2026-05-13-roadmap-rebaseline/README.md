# Legacy Specs Archived During 2026-05-13 Roadmap Rebaseline

These folders are preserved as historical product input, not active Speckit sources. The active roadmap now reuses `004` through `012` for the local-first, cloud-augmented plan described in [docs/spec-branch-reorganization-plan-2026-05-13.md](../../../docs/spec-branch-reorganization-plan-2026-05-13.md).

| Archived folder | Active replacement |
| --- | --- |
| `004-ask-orbit` | `004-capture-understanding`; Ask concepts move to `005-retrieval-and-ask-citations` |
| `005-cloud-boost-byok-llm` | `005-retrieval-and-ask-citations`; cloud/provider/budget concepts move to `008-cloud-controls-storage-budgeting` |
| `006-orbit-cloud-storage` | `006-approval-action-runtime`; storage/sync policy moves to `008-cloud-controls-storage-budgeting` |
| `007-knowledge-graph` | `007-memory-candidates-inspector`; backend KG proof moves to `009-kg-backend-poc` |
| `008-orbit-agent` | `008-cloud-controls-storage-budgeting`; agent coordinator moves to `010-agent-coordinator` |
| `009-byoc-sovereign-storage` | `009-kg-backend-poc`; BYOC remains a later power-user extension |
| `010-visual-polish-pass` | `010-agent-coordinator`; visual refit lives in `015-visual-refit` branch debt |
| `011-manual-compose` | Recreated as refreshed `011-manual-compose` |
| `012-resolution-semantics` | Recreated as refreshed `012-resolution-semantics` |

Do not implement directly from the archived drafts. Mine them for useful requirements only after generating fresh Speckit artifacts for the active replacement branch.
