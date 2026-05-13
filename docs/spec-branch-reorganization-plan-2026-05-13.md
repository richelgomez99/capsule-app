# Spec Branch Reorganization Plan - 2026-05-13

This plan reorganizes the current `specs/` backlog after the local-first/cloud-augmented pivot. It answers four questions:

1. Which existing spec folders are still active?
2. Which branches should exist next?
3. What must be landed or reconciled before new Speckit workflows?
4. What pre-work is required before each future Speckit branch?

Related docs:

- [Product Roadmap Audit - 2026-05-12](product-roadmap-audit-2026-05-12.md)
- [Orbit Agent Architecture Round 5 - 2026-05-12](orbit-agent-architecture-round-5-2026-05-12.md)
- [Orbit Agent Architecture Round 6 - 2026-05-12](orbit-agent-architecture-round-6-2026-05-12.md)
- [Android Architecture Verification - 2026-05-13](android-architecture-verification-2026-05-13.md)
- [Branch Debt Closeout Tasks - 2026-05-13](branch-debt-closeout-tasks-2026-05-13.md)

## Current verdict

Do not start by reopening `specs/008-orbit-agent` or creating a full agent branch.

The next work should be:

1. Close the current `015`/`016`/`017` branch debt.
2. Land the planning/truth-reset docs so the repo stops promising local-only behavior.
3. Reconcile stale task status in `003`, `013`, and `014`.
4. Then rebaseline the outdated `004` through `012` range so the next Speckit workflows use the intended product order instead of being pushed to `018+` only because old stubs already exist.

The important distinction: `013` through `017` should stay as historical/worked branch debt, while `004` through `012` should become the active replan range because most of those folders are one-file drafts whose original assumptions no longer match the cloud-augmented architecture.

## Actual audit findings

This audit used the `specs/` artifact set, task checkbox counts, file modification dates, local branches/worktrees, and GitHub PR state as of 2026-05-13.

### Speckit artifact depth

| Spec | Artifacts present | Task checkboxes | Latest spec-file modification | Interpretation |
| --- | --- | ---: | --- | --- |
| `001-core-capture-overlay` | `spec`, `plan`, `research`, `data-model`, `quickstart`, `tasks` | 47 done / 0 open | 2026-04-17 | Real completed foundation. |
| `002-intent-envelope-and-diary` | Full Speckit set | 190 done / 9 open | 2026-05-10 | Real v1 baseline; mostly completed with follow-ups. |
| `003-orbit-actions` | Full Speckit set | 84 done / 29 open | 2026-04-29 | Real worked branch, but stale task drift likely. |
| `004-ask-orbit` | `spec.md` only | none | 2026-04-25 | Stub/spec-only; do not branch directly. |
| `005-cloud-boost-byok-llm` | `spec.md` only | none | 2026-04-25 | Spec-only and stale against cloud pivot. |
| `006-orbit-cloud-storage` | `spec.md` only | none | 2026-04-25 | Spec-only draft; keep concept, replan later. |
| `007-knowledge-graph` | `spec.md` only | none | 2026-04-25 | Spec-only draft; park. |
| `008-orbit-agent` | `spec.md` only | none | 2026-04-29 | Spec-only stub; park. |
| `009-byoc-sovereign-storage` | `spec.md` only | none | 2026-04-25 | Spec-only later-tier draft. |
| `010-visual-polish-pass` | `spec.md` only | none | 2026-05-11 | Superseded by `015`. |
| `011-manual-compose` | `spec.md` only | none | 2026-05-12 | Spec-only, useful later small feature. |
| `012-resolution-semantics` | `spec.md` only | none | 2026-04-29 | Spec-only broad concept; split later. |
| `013-cloud-llm-routing` | Full Speckit set | 14 done / 14 open | 2026-04-29 | Real cloud-pivot plan; PR #1 merged, tasks stale. |
| `014-edge-function-llm-gateway` | Full Speckit set | 0 checkbox markers | 2026-05-10 | Real gateway plan; task file uses non-checkbox format, PR #1 merged. |
| `015-visual-refit` | `spec`, `plan`, `research`, `quickstart`, `tasks` | 50 done / 14 open | 2026-05-12 | Active implementation PR #21 with closeout-copy review fix pushed at `9814121`. |
| `016-intent-set-migration` | Full Speckit set | 17 done / 0 open | 2026-05-11 | Active implementation PR #19 with markdown cleanup pushed at `9431568`. |
| `017-capture-feedback-actions` | `spec`, `plan`, `data-model`, `tasks` | 30 done / 0 open | 2026-05-12 | Active implementation PR #20 with restore-from-trash duplicate-key fix pushed at `acac810`. |

Takeaway: only `004` through `012` are mostly spec-only drafts. The real work inventory is `001`, `002`, `003`, `013`, `014`, `015`, `016`, and `017`.

### Local worktree and dirty state

| Worktree | Branch | Dirty state | What it means |
| --- | --- | ---: | --- |
| `/Users/richelgomez/dev/capsule-app` | `qa/015-017-stacked` | 14 untracked docs | Current integration/docs workspace. Planning docs are not committed. |
| `/Users/richelgomez/dev/capsule-app-015-phase1-split` | `015-phase1-cluster-surface` | Clean after `9814121` | Actual `015` implementation branch pushed as PR #21. |
| `/Users/richelgomez/dev/capsule-app-spec-016` | `016-intent-set-migration` | clean after `9431568` | Actual `016` implementation branch pushed as PR #19 replacement. |
| `/Users/richelgomez/dev/capsule-app-spec-017` | `017-capture-feedback-actions` | clean after `acac810` | Actual `017` implementation branch is represented by PR #20. |
| `/Users/richelgomez/dev/capsule-app-visual-refit` | `015-visual-refit` | clean | Old planning branch, not the current implementation split. |
| `/Users/richelgomez/dev/capsule-app-015-p0c1` | `main` | clean, ahead/behind origin | Do not use as a fresh base until reconciled with `origin/main`. |

Dirty file dates confirm the staged implementation leftovers are recent:

- `015-phase1-cluster-surface`: staged files modified 2026-05-12 19:37-19:42 were committed as `275ead9` and pushed to PR #21, then review cleanup `9814121` removed stale private-by-default capture-sheet copy and fixed diff hygiene.
- `017-capture-feedback-actions`: staged files modified 2026-05-12 19:35, including `EnvelopeRepositoryImpl`, `EnvelopeStorageBackend`, `LocalRoomBackend`, `OrbitMigrations`, `IntentEnvelopeDao`, and `UrlHashDedupeContractTest`.

These staged leftovers have now been committed into their owning implementation PRs (#20 for `017`, #21 for `015`). Do not assume older draft PRs contain the current work.

### Branch and PR reality

| Branch / PR | State | Interpretation |
| --- | --- | --- |
| PR #1 `cloud-pivot` -> `main` | Merged | Cloud Day 1/Day 2 baseline is already in main; `013`/`014` tasks need reconciliation, not reimplementation. |
| PR #5 `015-visual-refit` -> `main` | Closed as superseded | Planning-only draft replaced by PR #21 from `015-phase1-cluster-surface`. |
| PR #8 `016-intent-set-migration` -> `main` | Closed stale/planning-era draft | Superseded by PR #19 from `016-intent-set-migration-closeout`. |
| PR #19 `016-intent-set-migration-closeout` -> `main` | Open replacement implementation PR | Carries the actual local `016` implementation branch state plus 2026-05-13 closeout gate notes and markdown cleanup `9431568`. |
| PR #21 `015-phase1-cluster-surface` -> `main` | Open implementation PR | Closeout commit `275ead9` plus review cleanup `9814121` pushed; land after PR #19 and preferably after PR #20 unless reviewers explicitly accept the dependency state. |
| PR #20 `017-capture-feedback-actions` -> `main` | Open implementation PR | Staged worktree debt committed as `b872e38`; review fixes pushed through `acac810`, including active duplicate-key enforcement, text-key indexing, concurrent duplicate coverage, v5-to-v7 migration coverage, and restore-from-trash duplicate-key reactivation. Depends on PR #19 unless reviewers explicitly accept the dependency state. |
| PR #22 `docs/product-truth-reset` -> `main` | Open docs reset PR | Carries README/PRD truth reset, planning docs, archived legacy `004` through `012`, refreshed active `004` through `012` placeholders, and `003`/`013`/`014` status notes. Landing is deferred pending review of the branch-debt PR stack. |
| `qa/015-017-stacked` | No upstream; 24 commits ahead of local main merge-base; docs untracked | Integration/reference branch only. Do not keep adding new implementation here. |

### What this changes

The immediate next step is not only deciding branch names. The real blocker is branch hygiene:

1. Preserve/commit the untracked planning docs from `qa/015-017-stacked` or move them to a docs branch.
2. Review PR #21 for the committed `015-phase1-cluster-surface` closeout.
3. Review PR #20 for the committed `017-capture-feedback-actions` closeout.
4. Reconcile local `016-intent-set-migration` with stale PR #8 before landing.
5. Treat PR #5 as historical/planning-only; it is now closed as superseded by PR #21.

### Phase 1 freeze note

As of the branch-debt closeout Phase 1 snapshot, `qa/015-017-stacked` is reference/docs-only until [Branch Debt Closeout Tasks - 2026-05-13](branch-debt-closeout-tasks-2026-05-13.md) is complete or an incomplete track is explicitly deferred. Do not add feature implementation to this worktree.

No rebaselined `004` through `012` Spec Kit branch should start until the closeout checklist is complete or the remaining debt is explicitly deferred in writing. The sibling-worktree implementation work for `015-phase1-cluster-surface` and `017-capture-feedback-actions` is now represented by PR #21 and PR #20, respectively, and must not be routed through `qa/015-017-stacked`.

### 016 closeout status

As of the 2026-05-13 closeout run, `016-intent-set-migration` has passed local reconciliation checks through T018: clean worktree before recording, Android gate passed, gateway `typecheck` passed, gateway `test:unit` passed, stale-label search clean, READ_LATER coverage present, and no ContactRef/schema leakage in the `origin/main...HEAD` diff.

PR #8 has been closed as superseded. Replacement PR #19 is open from `016-intent-set-migration-closeout`. Treat `016` as PR-ready, not landed; merge/landing remains deferred pending review.

### 017 closeout status

As of the 2026-05-13 closeout run, `017-capture-feedback-actions` staged work has been committed as `b872e38`, pushed to `origin/017-capture-feedback-actions`, and opened as PR #20. Review fixes were pushed through `acac810`: database-backed active duplicate keys, exact-text active-key indexing, concurrent duplicate regression coverage, v5-to-v7 migration coverage, androidTest schema assets, and conflict-safe restore-from-trash duplicate-key reactivation. Android compile/lint gates passed; the final restore regression tests compiled, but the post-`acac810` focused connected run could not execute because no Android devices were connected. Earlier focused connected instrumentation passed on SM-S928U1 and SM-X710, and S24/Tab S9 physical QA evidence is preserved in the PR body. Treat `017` as PR-ready, not landed; merge after PR #19 unless reviewers explicitly accept the dependency state.

### Docs product truth reset status

As of the 2026-05-13 docs reset run, `docs/product-truth-reset` has been committed as `b266a69`, pushed to `origin/docs/product-truth-reset`, and opened as PR #22. The branch updates README/PRD product truth to local-first/cloud-augmented, archives the old spec-only `004` through `012` folders under `specs/legacy/2026-05-13-roadmap-rebaseline`, recreates active placeholders for `004-capture-understanding` through `012-resolution-semantics`, copies the planning/research docs, and adds status reconciliation notes to `003`, `013`, and `014`. Markdown diagnostics passed on touched docs/spec files, `git diff --check` passed, and the changed paths were reviewed as docs/spec/status only with no app code changes. Treat docs reset as PR-ready, not landed; landing remains deferred pending review of the branch-debt PR stack.

## Branch count

### Before the first rebaselined Speckit feature

Use **four landing tracks total**, only **one of which is a new branch**. The existing GitHub draft PRs for `015-visual-refit` and `016-intent-set-migration` are stale planning-era PRs; do not treat them as proof that the implementation work is already represented in PR form.

| Track | Branch | New? | Purpose | Recommendation |
| --- | --- | ---: | --- | --- |
| 1 | `016-intent-set-migration` | No | Durable intent enum/label alignment. | Clean locally, but remote PR #8 is stale/diverged. Reconcile local branch vs PR before landing. |
| 2 | `017-capture-feedback-actions` | No | Duplicate capture, Already Saved, notes/reclassify/open actions. | PR #20 is open with review-fix commit `acac810`. Land after 016. |
| 3 | `015-phase1-cluster-surface` | No | Visual refit and overlay/bubble/settings polish. | PR #21 is open with closeout cleanup `9814121`. Land after 016 and preferably after 017. |
| 4 | `docs/product-truth-reset` | Yes | Commit the new research/planning docs, update stale local-only copy, reconcile spec statuses. | PR #22 is open from clean `origin/main`; land after review of the branch-debt PR stack. |

Do not create another implementation branch until these four tracks are closed or intentionally deferred.

### Rebaseline the outdated range instead of appending `018+`

After the four-track cleanup, do **not** create `018-capture-understanding` as the default. Reuse the stale draft range deliberately:

1. Archive the old `004` through `012` spec-only folders as legacy inputs during `docs/product-truth-reset`.
2. Preserve useful requirements in archive notes, follow-up notes, or the new spec body.
3. Recreate fresh Speckit folders in the same numeric slots using the rebaselined order below.
4. Keep `013` through `017` as completed/worked history, not as the numbering source for future work.

The rule is intentional: old stub folders do not get to hold the roadmap hostage. The active product order owns `004` through `012`; the stale drafts become source material.

The preferred active roadmap becomes:

| New order | Rebaselined spec / branch | Reuses or preserves | Purpose |
| ---: | --- | --- | --- |
| 004 | `004-capture-understanding` | Preserves source/detail ideas from old `004`, cloud routing constraints from `013`/`014`, and feedback/data lessons from `017`. | Source identity, evidence bundles, capture summaries, Basic/Smart/Deep controls, deletion/invalidation, capture detail insight surface. |
| 005 | `005-retrieval-and-ask-citations` | Replans old `004-ask-orbit`; preserves Ask intent but fixes the dependency order. | Retrieval chunks, cited answers, relatedness explanations, answer limitation language. |
| 006 | `006-approval-action-runtime` | Reconciles old `003` remaining tasks plus the approval portions of old `008`. | Candidate actions, approval requests, local execution boundary, receipts, undo/audit behavior. |
| 007 | `007-memory-candidates-inspector` | Replans old `007` and part of old `008` without starting with a graph backend. | Memory candidates, confirmations, inspector/delete/export controls, fact promotion rules. |
| 008 | `008-cloud-controls-storage-budgeting` | Replans old `005` and `006`; closes real `013`/`014` follow-ups. | Gateway budgets, trace IDs, cloud policy, storage/sync policy, local fallback controls. |
| 009 | `009-kg-backend-poc` | Replans old `007`; parks old BYOC storage as a later power-user tier. | Graphiti/Zep/Mem0/Supabase baseline behind adapter gates after memory controls exist. |
| 010 | `010-agent-coordinator` | Replans old `008`; old visual polish remains superseded by `015`. | Approval-first coordinator after memory, action, and cloud controls exist. |
| 011 | `011-manual-compose` | Preserves old `011` as a real small product feature. | Manual capture/composition flow, likely after capture understanding or as a tactical branch if low-risk. |
| 012 | `012-resolution-semantics` | Preserves old `012`, but splits it only when retrieval/actions produce enough real cases. | Conflict resolution, stale/duplicate meaning, merge/explain behavior across captures and memories. |

Only use `018+` if a concrete tooling failure makes numeric reuse impossible. Product-wise, the plan is to make `004` through `012` tell the current story.

## Existing specs disposition

| Spec | Current state | Keep / supersede / park | Action |
| --- | --- | --- | --- |
| `001-core-capture-overlay` | Full artifact set, 47/47 tasks done. | Keep as baseline. | No branch except regressions. |
| `002-intent-envelope-and-diary` | Full artifact set, mostly done. Round 5 counted 190/199 done. | Keep as v1 baseline. | Do not rerun Speckit. Reconcile only physical/demo/stretch follow-ups. |
| `003-orbit-actions` | Full artifact set, task drift likely. Round 5 counted 84/113 done. | Keep but reconcile before replanning. | Verify code reality, then feed remaining action concepts into rebaselined `006-approval-action-runtime`. |
| `004-ask-orbit` | Stub, assumes local embeddings by default. | Supersede as written, preserve intent. | Rebaseline the `004` slot as `004-capture-understanding`; move Ask concepts into `005-retrieval-and-ask-citations`. |
| `005-cloud-boost-byok-llm` | Draft, old BYOK/local assumptions conflict with pivot. | Supersede as written, preserve cloud-control concerns. | Rebaseline the `005` slot as Ask/retrieval; fold BYOK/budget/provider ideas into `008-cloud-controls-storage-budgeting`. |
| `006-orbit-cloud-storage` | Draft managed cloud storage. | Preserve concept, replan under controls. | Rebaseline the `006` slot as approval runtime; fold storage/sync policy into `008-cloud-controls-storage-budgeting`. |
| `007-knowledge-graph` | Draft, too early. | Preserve concept, defer backend. | Rebaseline `007` as memory candidates/inspector; KG backend POC becomes `009`. |
| `008-orbit-agent` | Stub full agent. | Preserve long-term agent intent, do not build first. | Rebaseline `008` as cloud controls/storage/budgeting; agent coordinator becomes `010`. |
| `009-byoc-sovereign-storage` | Draft v1.3 power-user storage. | Park as later tier. | Rebaseline `009` as KG backend POC; keep BYOC/sovereign storage as a future power-user extension after Orbit Cloud stabilizes. |
| `010-visual-polish-pass` | Superseded by `015`. | Supersede as historical design input. | Rebaseline `010` as agent coordinator after action, memory, and cloud controls exist. |
| `011-manual-compose` | Draft, useful small product feature. | Archive old draft, preserve concept. | Recreate as refreshed `011-manual-compose`; may be pulled forward tactically after `004` if it avoids schema churn. |
| `012-resolution-semantics` | Draft, valuable but broad. | Archive old draft, preserve concept. | Recreate as refreshed `012-resolution-semantics`; data-first resolution branch after retrieval/actions, UI treatment later. |
| `013-cloud-llm-routing` | Full artifact set, task checkboxes stale against code. | Keep as cloud baseline, reconcile. | Verify Android/provider/router and Supabase migrations; close real gaps. |
| `014-edge-function-llm-gateway` | Full artifact set but no checkbox markers in tasks. Existing code exists under `supabase/functions/llm_gateway`. | Keep as gateway baseline, reconcile. | Add status/follow-up notes; do not regenerate Day 2 from scratch. |
| `015-visual-refit` | In progress, 50/64 tasks done. | Active branch debt. | Finish or explicitly defer Phase 5/manual QA, then land. |
| `016-intent-set-migration` | 17/17 tasks done. | Active branch debt, likely ready. | Verify branch status and land before 015/017 if not merged. |
| `017-capture-feedback-actions` | 30/30 tasks done. | Active branch debt, likely ready. | Verify branch status and land after 016. |

## Planning considerations before reorganizing folders

Do this planning before renaming, deleting, or regenerating any spec folder.

### 1. Preserve history without preserving bad order

The old `004` through `012` specs should not silently disappear. They should either get a short status header or be moved to an archive/follow-up note during the docs reset. The useful parts should survive, but the old numbering should not force the future roadmap to start at `018`.

Recommended preservation rule:

- If the old spec has only `spec.md`, preserve it as legacy input and regenerate the slot.
- If the old spec has full Speckit artifacts or implemented code, do not overwrite it without a dedicated reconciliation note.
- If an old spec has product language that still matters, copy that requirement into the new spec's context rather than keeping the old spec active.

### 2. Reuse numbers only after branch debt is closed

Do not reorganize `004` through `012` while `015`, `016`, and `017` are still ambiguous. The active implementation work is now represented by PR #19, PR #20, and PR #21; finishing or explicitly deferring those PRs first prevents the rebaseline from mixing roadmap cleanup with live code integration.

### 3. Keep Speckit inputs narrow

Each rebaselined branch should answer one product question:

| Branch | Planning question |
| --- | --- |
| `004-capture-understanding` | What can Orbit truthfully know about a capture, and what evidence supports that claim? |
| `005-retrieval-and-ask-citations` | How does Orbit answer questions with citations and limitation language? |
| `006-approval-action-runtime` | How does Orbit propose and execute user-approved local actions? |
| `007-memory-candidates-inspector` | How do useful memories get promoted, inspected, corrected, exported, and deleted? |
| `008-cloud-controls-storage-budgeting` | How does cloud use stay visible, budgeted, traceable, and reversible? |
| `009-kg-backend-poc` | Which graph/memory backend earns its place behind an adapter? |
| `010-agent-coordinator` | How does the agent plan without bypassing approval, memory, cloud, or Android boundaries? |
| `011-manual-compose` | How can users intentionally create or edit captures without the overlay? |
| `012-resolution-semantics` | How does Orbit represent duplicates, conflicts, stale facts, and merged meanings? |

### 4. Required folder mechanics during `docs/product-truth-reset`

The docs reset should use one mechanic, not a fallback matrix:

- Move each stale folder `specs/004-*` through `specs/012-*` into a legacy/archive location, preserving its original `spec.md` and a short replacement note.
- Recreate fresh folders with the active roadmap names: `specs/004-capture-understanding`, `specs/005-retrieval-and-ask-citations`, `specs/006-approval-action-runtime`, `specs/007-memory-candidates-inspector`, `specs/008-cloud-controls-storage-budgeting`, `specs/009-kg-backend-poc`, `specs/010-agent-coordinator`, `specs/011-manual-compose`, and `specs/012-resolution-semantics`.
- Keep old requirements only as inputs. Do not leave a stale folder in place with the same number and different product meaning.
- Do not append `018+` unless a concrete tooling failure makes numeric reuse impossible, and if that happens document the failure and the chosen exception in this plan before generating new Speckit artifacts.

As an immediate guardrail, the current old `004` through `012` `spec.md` files now carry `Legacy status (2026-05-13)` headers. Those headers are temporary markers for the docs reset branch; they are not a substitute for the archive-and-recreate step.

### 5. Protect Android and cloud invariants

Every new spec must carry the same architectural constraints forward:

- Android network calls go through `com.capsule.app.net.*` and the `:net` gateway path.
- Binder payloads carry IDs and compact summaries, not raw HTML, screenshots, embeddings, or full evidence bundles.
- External writes stay local and user-approved.
- Cloud traces are no-content by default unless a future explicit policy says otherwise.
- Deletion, export, invalidation, tenant isolation, and cost visibility are not follow-up polish for cloud-backed knowledge features; they are acceptance criteria.

## Immediate pre-work checklist

### 0. Freeze current integration branch

Current worktree is `qa/015-017-stacked`. Treat it as a reference/integration branch only.

Pre-work:

- Do not add new feature implementation to `qa/015-017-stacked`.
- Keep the untracked planning docs together until moved to the docs/truth branch.
- Record split worktree status for `015`, `016`, and `017` before opening PRs.

### 1. Land `016-intent-set-migration`

Why first: it is the durable label set used by visual and feedback work.

Pre-work:

- Run standard Android gate in `/Users/richelgomez/dev/capsule-app-spec-016`.
- Run Supabase `llm_gateway` classifier tests if they are available locally.
- Confirm no ContactRef schema/migration work leaked into this branch.
- Confirm no stale `REMIND_ME` or `INSPIRATION` behavior remains.

Exit:

- PR ready or landed.
- `016` stays closed unless a regression appears.

### 2. Land `017-capture-feedback-actions`

Why second: behavior and data contracts should land before final visual polish.

Pre-work:

- Run standard Android gate in `/Users/richelgomez/dev/capsule-app-spec-017`.
- Confirm Room schema version and exported schemas match the branch.
- Confirm duplicate URL and exact-text lookup tests are green.
- Confirm note persistence is only via `EnvelopeNoteEntity` or the spec-owned table shape.
- Preserve S24 and Tab S9 physical QA notes in the PR body.

Exit:

- Duplicate/Already Saved behavior is no longer carried as branch debt.

### 3. Land `015-phase1-cluster-surface`

Why third: it is mostly presentation and overlay polish, and it can adapt to landed behavior.

Pre-work:

- Run standard Android gate in `/Users/richelgomez/dev/capsule-app-015-phase1-split`.
- Decide whether `T015-501` through `T015-506` block PR or become follow-up overlay customization work.
- Decide whether `T015-901`, `T015-904`, and `T015-905` block PR or become flag-flip follow-ups.
- Capture manual screenshots or explicitly record that the feature remains flag-gated.
- Preserve corrected Orbit mark philosophy in PR notes.

Exit:

- Visual refit is either landed behind flag or explicitly scoped to a remaining visual follow-up.

### 4. Create `docs/product-truth-reset`

Why fourth: once branch debt is no longer ambiguous, make the repo's public and internal claims match the new strategy.

Pre-work:

- Move/commit the new docs from this planning session together.
- Update [README.md](../README.md) and any `.specify/memory` product docs that still say local-only, no Orbit server, or BYOK/BYOC-first.
- Update status headers in stale specs `004` through `012` to say whether each one is superseded, preserved, parked, or being replaced by a rebaselined slot.
- Reconcile stale task status in `003`, `013`, and `014` by verifying code reality, not by blindly checking boxes.
- Keep app code out of this branch unless a docs build requires metadata changes.

Exit:

- README, planning docs, and spec statuses tell one story.
- New Speckit prompts can cite current product truth without re-litigating the pivot.

## First rebaselined Speckit branch: `004-capture-understanding`

Do this only after the immediate checklist above is complete or intentionally deferred.

### Pre-work before `/speckit.specify`

- Confirm current Room schema version after `017` lands.
- Confirm connected migration-test harness gap is understood: schema JSONs must be packaged into androidTest assets before connected migration gates are trusted.
- Confirm source identity bug inventory: no category-only `video -> YouTube` identity without provider evidence.
- Decide Basic/Smart/Deep defaults for dogfood builds.
- Decide what public URL evidence can use cloud automatically when cloud mode is enabled.
- Decide what requires separate consent: logged-in browsing, connected-account access, sensitive screenshot VLM, external writes, and deep enrichment.
- Confirm Android rule: `:net` is only allowed network code path, not a process-scoped permission island.

### Speckit prompt shape

Create a feature specification for Orbit capture understanding. Orbit is an Android app that is local-first and cloud-augmented. The feature introduces first-class SourceIdentity, EvidenceBundle, CaptureUnderstanding, UnderstandingDepthPolicy, UnderstandingJob, and deletion/invalidation behavior. It keeps current URL hydration compatibility, adds sidecar storage rather than destructive rewrites, preserves `:net` as the only allowed network code path, shows source/evidence/summary/limitations in capture detail, supports Basic/Smart/Deep controls, accepts per-capture overrides, and avoids claiming more than the evidence supports. It includes feedback for wrong source or wrong summary. It does not implement Ask Orbit, KG, memory inspector, full agent runtime, logged-in browsing, or external writes.

### Required artifacts

```text
specs/004-capture-understanding/
  spec.md
  plan.md
  data-model.md
  quickstart.md
  tasks.md
  contracts/
    source-identity-contract.md
    evidence-bundle-contract.md
    capture-understanding-contract.md
    understanding-depth-contract.md
    deletion-invalidation-contract.md
```

### Stop signs

- Stop if the spec starts implementing Ask Orbit.
- Stop if it adds KG tables before deletion/invalidation is specified.
- Stop if it adds generic browser automation.
- Stop if it passes raw HTML, screenshots, embeddings, or full evidence bundles over Binder.
- Stop if it adds network clients outside `com.capsule.app.net.*`.

## Later rebaselined Speckit pre-work

### `005-retrieval-and-ask-citations`

Pre-work:

- `004` must define stable `CaptureUnderstanding` and `RetrievalChunk` inputs.
- Decide local FTS vs local vector minimum viable path.
- Decide cloud pgvector retrieval boundaries and no-content trace policy.
- Define citation chip UI and answer limitation language.

Do not start if capture understanding cannot produce cited evidence.

### `006-approval-action-runtime`

Pre-work:

- Reconcile `003` task drift against existing `ActionExecutorService`, `ActionHandler`, and AppFunction schemas.
- Decide `CandidateAction` and `ApprovalRequest` contracts.
- Confirm external writes remain local and confirmed in `:capture`.
- Define undo/receipt/audit behavior before UI polish.

Do not start if the branch tries to execute actions through cloud.

### `007-memory-candidates-inspector`

Pre-work:

- `004` and `005` must emit feedback episodes and cited retrieval history.
- Define memory candidate thresholds, confirmation UI, and deletion/export behavior.
- Decide what facts are never auto-promoted.
- Decide local-only vs cloud-eligible memory classes.

Do not start with Graphiti/Zep/Mem0 as canonical product truth.

### `008-cloud-controls-storage-budgeting`

Pre-work:

- Reconcile `013` Android routing tasks with existing code.
- Reconcile `014` gateway status with actual deployed/local function behavior.
- Decide Orbit Cloud storage/sync boundaries separately from LLM routing; do not let storage become implicit just because the gateway exists.
- Decide Langfuse/Phoenix trace content policy.
- Define `BudgetDecision`, request IDs, cost receipt UI, and local fallback language.

Do not start if cloud calls are untyped or if traces can contain raw user content by default.

### `009-kg-backend-poc`

Pre-work:

- `007` memory inspector must exist first.
- Define adapter contract for Graphiti/Zep/Mem0/Supabase-only baseline.
- Define deletion, tenant isolation, export, citation, and invalidation tests before any backend POC code.

Do not start if the graph becomes the first memory layer.

### `010-agent-coordinator`

Pre-work:

- `006` approval runtime must exist.
- `007` memory controls must exist.
- `008` cloud controls must exist.
- Define `AgentRun`, `AgentPlan`, cancellation, budget caps, trace IDs, and prompt assembly boundaries.

Do not start if it creates autonomous background execution or direct network/database/action access from `:agent`.

### `011-manual-compose`

Pre-work:

- Decide whether manual compose is a tactical branch after `004` or a later creator workflow after `010`.
- Define whether manually composed captures can create `CaptureUnderstanding` records immediately or only after the same understanding job path runs.
- Confirm manual compose does not introduce a parallel envelope-writing path outside existing repository/storage contracts.
- Decide attachment limits, URL validation, and source identity behavior for user-entered material.

Do not start if it bypasses capture understanding, deletion, or provenance rules.

### `012-resolution-semantics`

Pre-work:

- `005` retrieval and `006` action runtime should have enough real duplicate/conflict/stale cases to model.
- Define resolution entities as data semantics first, not visual labels first.
- Decide how resolved captures affect citations, memory candidates, deletion, and export.
- Split UI treatment from storage semantics if the branch becomes too broad.

Do not start if it becomes a generic taxonomy rewrite or a hidden merge operation without user-visible evidence.

## Final branch order

Recommended order:

```text
016-intent-set-migration
  -> 017-capture-feedback-actions
  -> 015-phase1-cluster-surface
  -> docs/product-truth-reset
  -> 004-capture-understanding
  -> 005-retrieval-and-ask-citations
  -> 006-approval-action-runtime
  -> 007-memory-candidates-inspector
  -> 008-cloud-controls-storage-budgeting
  -> 009-kg-backend-poc
  -> 010-agent-coordinator
  -> 011-manual-compose
  -> 012-resolution-semantics
```

If a branch must be worked in parallel, only parallelize presentation/docs work against data/behavior work after the shared enum/schema branch is merged. Do not parallelize schema-bearing branches that touch Room, Supabase, or Binder contracts unless their migration numbers and AIDL surfaces are already allocated.
