# Implementation Plan: Orbit Actions (v1.1)

**Branch**: `spec/002-intent-envelope-and-diary` (current — v1.1 implementation will branch from here once 002 lands) | **Date**: 2026-04-26 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `specs/003-orbit-actions/spec.md`
**Governing document**: [`.specify/memory/constitution.md`](../../.specify/memory/constitution.md)
**Companion design doc**: [`.specify/memory/design.md`](../../.specify/memory/design.md)

> **Branching note.** Per user direction, this plan is authored on the
> active branch `spec/002-intent-envelope-and-diary`. Spec 003 work
> proper will move to its own branch (`spec/003-orbit-actions`) once
> spec 002 closes (T110–T113 physical-device acceptance). All file
> paths in this plan target the post-002 codebase.

## Summary

Orbit Actions turns sealed envelopes into structured, **always
user-confirmed** actions that land in the apps the user already uses.
v1.1 introduces three action kinds — calendar events, to-do items, and
a Sunday weekly digest — and the supporting infrastructure that v1.2
agent (spec 008) will reuse as its tool surface:

1. Two new `ContinuationType` values (`ACTION_EXTRACT`,
   `ACTION_EXECUTE`) plumbed through the existing `LlmProvider`
   interface (002 T025a) and `ContinuationEngine` (002).
2. A new `AppFunctionSkill` registry, persisted in a local `skills`
   table whose schema matches spec 006's `skills` table so v1.2 can
   migrate without churn.
3. An `ActionProposal` / `ActionExecution` pair of tables that record
   every suggestion, every confirmation, and every dismissal, with
   provenance back to the source envelope (Principle XII).
4. A `WeeklyDigestWorker` that runs Sunday morning (charger + wifi
   per Principle IV) and produces a new envelope of kind `DIGEST` at
   the top of that day's Diary.

**No new processes.** All proposal logic stays in `:ml`. Action
**execution** (firing the Android intent) happens in `:capture` per
FR-003-008, which already runs the persistent foreground service and
has a stable launching context. `:net` remains untouched — Orbit
Actions never opens a socket. External-app writes happen exclusively
through Android `Intent` IPC (system Calendar provider, share sheet,
or registered AppFunction handlers); the device kernel mediates
everything.

**No silent writes, ever.** A proposal exists in the DB only as data
until the user taps a confirmation chip in the Diary. Per FR-003-003
the gap between "proposal generated" and "external app receives the
intent" is always a user gesture.

## Technical Context

**Language/Version**: Kotlin 2.x (matching 001/002).

**Primary Dependencies (additions over 002)**:

- `androidx.appfunctions:appfunctions-runtime` 1.0+ — Android
  AppFunctions schema registration and execution (Android 15+
  preferred; falls back to a local registry table on 13/14).
- `androidx.appfunctions:appfunctions-compiler` (KSP) — generates
  schema bindings from `@AppFunction` annotations at compile time.
- `com.google.code.gson:gson` 2.10+ (already transitively present
  via 002) — `AppFunctionSchema` arg-shape JSON.
- `androidx.work` 2.9+ — already present from 002; adds
  `WeeklyDigestWorker`, `ActionExtractionWorker`,
  `ActionExecutionWorker`.
- No new network library. No `INTERNET`-using dependency.

Inherited from 002 (unchanged): Compose, Room+SQLCipher, AICore/Gemini
Nano, ML Kit OCR, Activity Recognition, jsoup, custom lint rule
`NoHttpClientOutsideNet`.

**Storage**:

- Same encrypted Room DB (`orbit.db`) as 002. Schema bumps from v1 to
  v2 with three additive tables (`action_proposal`,
  `action_execution`, `appfunction_skill`, `skill_usage`) and one
  new column on `intent_envelope` (`kind: EnvelopeKind` with default
  `REGULAR`). Migration is purely additive — no destructive ALTERs.
- WorkManager database (Google-managed) gains four new periodic /
  one-shot worker classes; no new persistence outside that.
- The audit log table from 002 is reused with seven new
  `AuditAction` values (additive enum).

**Testing**:

- JUnit 5 unit tests for the action-extraction parsers, the AppFunction
  schema validator, and the weekly digest input-window selector.
- Room instrumented tests for the new tables, the `EnvelopeKind`
  column migration, and idempotency keys on `ActionProposal`.
- Compose UI tests for the inline action chip in the Diary, the
  preview card, and the Sunday digest envelope rendering.
- Contract tests for the AppFunctions registry surface and the
  `:ml` ↔ `:capture` action-execute IPC.
- A new instrumented test class `NoNetworkDuringActionExecutionTest`
  asserts that an `:capture` execution path does not transitively
  load any HTTP client (delegated to the existing lint rule plus a
  runtime `Process.myUid()` socket-creation guard).
- Manual verification on a physical Pixel 8+ via `quickstart.md`.

**Target Platform**: Android 13+ (minSdk 33, targetSdk 36, matching
002). AppFunctions canonical APIs land on Android 15+; on 13/14 we
use a degraded local registry that mirrors the schema and exposes
the same `AppFunctionSkill` surface to v1.2 agent — see Constitution
Check (Principle V graceful-degrade).

**Project Type**: mobile-app (Android, 4-process split inherited from
002 — no new processes added by 003).

**Performance Goals**:

- Action proposal generation (Nano on charger + wifi): p95 < 8s per
  envelope from `ENVELOPE_CREATED` to `ACTION_PROPOSED` audit row.
- Diary render with action chip: no observable regression vs.
  002 (p95 < 1s for "today" load).
- Confirmation tap → external app visible: p95 < 600ms.
- Weekly digest generation: p95 < 45s for a 7-day, 100-envelope week
  (Nano summarisation budget).
- 60fps scroll preserved with up to 50 actionable proposals visible
  in a single Diary day.

**Constraints**:

- All AI on-device. No cloud LLM in the v1.1 path; spec 005 BYOK is
  optional and slots in via the `LlmProvider` provenance field
  without touching action logic.
- All proposal and execution data encrypted at rest in the same
  SQLCipher DB; no separate file.
- `:ml` proposes; `:ui` displays; `:capture` executes the intent.
  None of these processes hold `INTERNET`. `:net` is not invoked
  by any 003 code path.
- Action execution MUST be reversible within 24h via the standard
  002 undo affordance — for calendar inserts, that means we record
  the inserted event id and offer "undo" before the system app
  fires its own confirmation animation. After 24h the audit row is
  the only trace; user manages the inserted event in the target
  app.
- AppFunction schemas are append-only at the user level. A registered
  schema version cannot be silently mutated; bumping requires a new
  schema version row (mirrors the 002 closed-enum migration policy).

**Scale/Scope**:

- Typical user proposal volume: 0–5 per day (most envelopes generate
  no proposal).
- Weekly digest: 1 envelope per Sunday morning per user.
- AppFunction skills installed: ~3 in v1.1 (calendar insert, to-do
  add, share); planner-relevance counters maintained.
- New/modified files: ~30 Kotlin sources, 3 AIDL/contract files,
  3 Compose screens, 4 WorkManager workers.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| # | Principle | v1.1 Compliance | Status |
|---|---|---|---|
| I | Local-First Supremacy | Proposals are produced on-device by Nano via the existing `LlmProvider` interface. Execution fires Android `Intent`s — IPC only, never network. The audit log records every proposal/confirm/dismiss/execute event locally. No data leaves the device through any 003 code path. | ✅ PASS |
| II | Effortless Capture, Any Path | 003 doesn't change capture; it adds a *post-capture* chip surface. Confirmation is one tap, dismissal is a swipe — both ≤ 2s gestures. The chip never gates capture. | ✅ PASS |
| III | Intent Before Artifact | The `IntentEnvelope` remains the unit. Action proposals are *derivative* artifacts that carry an `envelopeId` foreign key (provenance). Confirming an action does not retroactively edit the source envelope's intent — it appends an `ACTION_CONFIRMED` audit row. | ✅ PASS |
| IV | Continuations Grow Captures | `ACTION_EXTRACT` runs as a charger+wifi continuation. The user opens Diary and finds proposals already there; they never wait. The Sunday digest is the same pattern at week granularity. | ✅ PASS |
| V | Under-Deliver on Noise | Zero notifications added. The chip lives inline in the Diary; if the user never opens Diary, they never see it. The weekly digest is a Diary entry, not a push. | ✅ PASS |
| VI | Privilege Separation By Design | No new processes. Proposal: `:ml` only. Display: `:ui` only. Execution: `:capture` only. None of these have `INTERNET`. The `NoHttpClientOutsideNet` lint rule continues to guard the boundary. AppFunctions registry lives in `:ml` (DB owner). | ✅ PASS |
| VII | Context Beyond Content | Action extraction reads the existing 002 `state_snapshot` (time, app category, activity state) as context — same fields, no new collection. No new signals introduced. | ✅ PASS |
| VIII | Collect Only What You Use | Three new tables, each powering a v1.1-visible feature. No speculative columns. `appfunction_skill` and `skill_usage` mirror the spec 006 schema (planner heuristics) but ship in v1.1 because v1.2 agent depends on them — not "for later." | ✅ PASS |
| IX | User-Sovereign Cloud Escape Hatch | `LlmProvider.extractActions` accepts `OrbitManaged` / `Byok` provenance like every other Nano call site. Per-capability cloud routing (FR-003-005) is a settings toggle that defaults OFF; falls back to Nano with no feature loss, only quality change. | ✅ PASS |
| X | Sovereign Cloud Storage | All 003 tables stay on-device in v1.1. The schemas are designed to be additively syncable when spec 006 (Orbit Cloud) ships, with `appfunction_skill` and `skill_usage` already matching the `skills`/`skill_usage` schema in the spec 006 contract. The audit log remains local-only forever. | ✅ PASS |
| XI | Consent-Aware Prompt Assembly | When `LlmProvider.extractActions` routes to a cloud provider (BYOK), the prompt is assembled in `:ml` and crosses the existing `:agent` consent filter (built per 002 T025a/T025b for spec 005 v1.1 entry). v1.1 ships with the filter present even though no cloud actions yet route through it — establishes the discipline. | ✅ PASS |
| XII | Provenance Or It Didn't Happen | Every `action_proposal` carries `envelopeId`. Every `action_execution` carries `proposalId` (and transitively `envelopeId`). The Sunday digest envelope carries a `derivedFromEnvelopeIds` JSON list. Every spec 006 episode written by 003 (`source_kind = 'agent_action'`) references at least one source envelope. Cascade-delete is wired: deleting the source envelope invalidates downstream proposals and executions per Principle XII. | ✅ PASS |

**Inherited from 002**:

- 4-process split, SQLCipher, lint rule `NoHttpClientOutsideNet`,
  audit-log atomicity (003 audit rows share a Room transaction with
  the proposal/execution write).
- `LlmProvider` interface and the `NanoLlmProvider` implementation
  — 003 adds one new method (`extractActions`) without altering the
  interface's existing five methods.
- `ContinuationEngine` and WorkManager scheduling discipline — 003
  registers three new continuation types using the same gating
  (charger + wifi, except `ACTION_EXECUTE` which is *user-initiated*
  and runs without those constraints because the user has explicitly
  asked).

**Post-Research Amendment** (TBD, end of Phase 0):

To be filled in after `research.md` settles the AppFunctions API
posture (canonical 15+ vs. degraded 13/14) and the date-parsing
strategy (Nano-only vs. Nano+structured-fallback).

**Post-Design Constitution Re-check** (TBD, end of Phase 1):

To be filled in after `data-model.md`, `contracts/`, and
`quickstart.md` are drafted.

## Project Structure

### Documentation (this feature)

```text
specs/003-orbit-actions/
├── plan.md                                    # This file
├── research.md                                # Phase 0 output
├── data-model.md                              # Phase 1 output
├── quickstart.md                              # Phase 1 output
├── contracts/                                 # Phase 1 output
│   ├── action-extraction-contract.md          # :ml ContinuationType.ACTION_EXTRACT surface
│   ├── appfunction-registry-contract.md       # AppFunction registration + skills/skill_usage
│   ├── action-execution-contract.md           # :capture IPC for ACTION_EXECUTE + intent dispatch
│   └── weekly-digest-contract.md              # Sunday WeeklyDigestWorker + DIGEST envelope
└── tasks.md                                   # Phase 2 output (speckit.tasks)
```

### Source Code (additions / modifications, all under `app/`)

```text
app/src/main/java/com/capsule/app/
├── data/                                      # 002 — EXTENDED
│   ├── OrbitDatabase.kt                       # MODIFIED: schema v1 → v2 migration
│   ├── entities/
│   │   ├── IntentEnvelopeEntity.kt            # MODIFIED: + kind: EnvelopeKind column
│   │   ├── ActionProposalEntity.kt            # NEW
│   │   ├── ActionExecutionEntity.kt           # NEW
│   │   ├── AppFunctionSkillEntity.kt          # NEW
│   │   └── SkillUsageEntity.kt                # NEW
│   ├── dao/
│   │   ├── ActionProposalDao.kt               # NEW
│   │   ├── ActionExecutionDao.kt              # NEW
│   │   ├── AppFunctionSkillDao.kt             # NEW
│   │   └── SkillUsageDao.kt                   # NEW
│   ├── ActionRepository.kt                    # NEW (binder-exposed via existing :ml service)
│   └── AppFunctionRegistry.kt                 # NEW
│
├── ai/                                        # 002 — EXTENDED
│   ├── LlmProvider.kt                         # MODIFIED: + extractActions(text, ctx)
│   ├── NanoLlmProvider.kt                     # MODIFIED: implement extractActions
│   ├── ActionExtractor.kt                     # NEW (orchestrates LlmProvider + parsers)
│   ├── DateTimeParser.kt                      # NEW (structured fallback for fuzzy dates)
│   └── DigestComposer.kt                      # NEW (weekly summary prompt + assembly)
│
├── continuation/                              # 002 — EXTENDED
│   ├── ContinuationEngine.kt                  # MODIFIED: register ACTION_EXTRACT, _EXECUTE, WEEKLY_DIGEST
│   └── workers/
│       ├── ActionExtractionWorker.kt          # NEW (charger+wifi, runs in :ml)
│       ├── ActionExecutionWorker.kt           # NEW (user-initiated, runs in :capture)
│       └── WeeklyDigestWorker.kt              # NEW (Sunday 06:00 local, charger+wifi, runs in :ml)
│
├── action/                                    # NEW — runs in :capture process
│   ├── ActionExecutorService.kt               # AppFunction invocation host
│   ├── CalendarInsertHandler.kt               # Intent.ACTION_INSERT to CalendarContract
│   ├── TodoAddHandler.kt                      # Intent.ACTION_SEND to user-chosen task app
│   ├── ShareHandler.kt                        # Intent.ACTION_SEND fallback
│   └── AppFunctionInvoker.kt                  # AppFunctions runtime adapter
│
├── diary/                                     # 002 — EXTENDED (:ui)
│   ├── ActionProposalChipUI.kt                # NEW (inline under EnvelopeCardUI)
│   ├── ActionPreviewCardUI.kt                 # NEW (expanded preview before confirm)
│   ├── DigestEnvelopeUI.kt                    # NEW (Sunday digest rendering)
│   └── DiaryViewModel.kt                      # MODIFIED: observe action_proposal joined
│
├── settings/                                  # 002 — EXTENDED (:ui)
│   ├── ActionsSettingsUI.kt                   # NEW (per-action-kind toggle, target task app)
│   └── DigestScheduleUI.kt                    # NEW (configurable Sunday-morning time)
│
└── audit/                                     # 002 — EXTENDED
    └── AuditAction.kt                         # MODIFIED: + 7 new actions

app/src/test/java/com/capsule/app/
├── ai/
│   ├── ActionExtractorTest.kt                 # NEW
│   ├── DateTimeParserTest.kt                  # NEW
│   └── DigestComposerTest.kt                  # NEW
├── data/
│   ├── ActionProposalDaoTest.kt               # NEW (instrumented)
│   ├── AppFunctionRegistryTest.kt             # NEW
│   └── OrbitDatabaseMigrationV1toV2Test.kt    # NEW (instrumented)
├── continuation/workers/
│   ├── ActionExtractionWorkerTest.kt          # NEW
│   ├── ActionExecutionWorkerTest.kt           # NEW
│   └── WeeklyDigestWorkerTest.kt              # NEW
├── action/
│   └── NoNetworkDuringActionExecutionTest.kt  # NEW (instrumented; UID socket-creation guard)
└── diary/
    └── ActionProposalChipUITest.kt            # NEW (Compose UI)
```

**Structure Decision**: 003 is a **purely additive** layer on the
002 architecture. No new processes, no new permissions, no new
manifest declarations beyond the AppFunctions runtime metadata. The
only existing files modified are: `OrbitDatabase.kt` (migration),
`IntentEnvelopeEntity.kt` (+1 column with default), `LlmProvider.kt`
(+1 method), `NanoLlmProvider.kt` (impl), `ContinuationEngine.kt`
(+3 worker registrations), `DiaryViewModel.kt` (joined query),
`AuditAction.kt` (+7 enum values). Every other addition lives under
new packages (`action/`) or new files in existing packages.

The new `action/` package is assigned `android:process=":capture"`
in the manifest because action execution is the user-tap-driven side
effect that the spec scopes there (FR-003-008). `:capture`'s
existing `INTERNET`-free posture means the lint rule already covers
the new package without modification.

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|---|---|---|
| Two new continuation types (`ACTION_EXTRACT`, `ACTION_EXECUTE`) plus a third for `WEEKLY_DIGEST` instead of folding all action work into a single `ACTION` continuation. | The three workloads have orthogonal scheduling. `ACTION_EXTRACT` is opportunistic (charger+wifi), `ACTION_EXECUTE` is user-initiated (no constraints), `WEEKLY_DIGEST` is calendar-pinned (Sunday 06:00 local). Conflating them would force scheduling-policy branches inside a single worker, hiding the user-initiated path's constraint exemption. | A single `ACTION` worker with internal subkind dispatch was rejected because (a) WorkManager scheduling constraints are per-WorkRequest, not per-payload, and (b) the audit log entries would lose granularity (we want `CONTINUATION_ENQUEUED kind=ACTION_EXTRACT` to be greppable distinct from `kind=ACTION_EXECUTE`). |
| AppFunctions registry table (`appfunction_skill`) shipped in v1.1 even though the agent that consumes the planner-heuristics columns lands in v1.2. | Without the registry shipping in v1.1, spec 006's `skills` table cannot be backfilled when 006 ships, and v1.2 agent has zero usage history on day one. v1.1 user *uses* the registry directly: every confirmed action increments `success_count`, which surfaces the "you usually add flights to calendar — confirm again?" UX in v1.1. | Deferring the registry to v1.2 was rejected because v1.2 agent's planner depends on having weeks of usage stats accumulated, and the schema is small (~6 columns) and additive. Principle VIII passes because the registry powers a v1.1-visible feature: per-skill success rate shown in Settings → Actions. |
| `EnvelopeKind` column added with default `REGULAR` instead of subclassing IntentEnvelopeEntity. | Room does not support polymorphic entities cleanly; adding a discriminator column with default keeps every existing query (002's full DAO surface) working unchanged while letting the Diary distinguish DIGEST envelopes for special rendering. The migration is `ALTER TABLE intent_envelope ADD COLUMN kind TEXT NOT NULL DEFAULT 'REGULAR'` — single-statement, sub-millisecond on a 5k-row corpus. | A separate `digest_envelope` table was rejected because the Diary renders all envelopes through one DAO query and would otherwise need a UNION across tables on every day-page render. The discriminator is the standard Room idiom and forward-compatible with spec 012 resolution-state additions (which will add a `state` column on the same entity). |

## Progress Tracking

- [x] **Phase 0 — Research** (`research.md`): AppFunctions API
  posture (canonical 15+ vs. 13/14 degraded), Nano date-parsing
  reliability, calendar `Intent.ACTION_INSERT` surface area, weekly
  digest input-window selection, undo-after-execute strategy,
  per-action-kind sensitivity scoping.
- [x] **Phase 1 — Design**:
  - [x] `data-model.md` — new tables, enum extensions, `EnvelopeKind`
    column, migration strategy, provenance edges.
  - [x] `contracts/action-extraction-contract.md` — `ACTION_EXTRACT`
    continuation, `LlmProvider.extractActions` signature, fallback
    policy, schema validation.
  - [x] `contracts/appfunction-registry-contract.md` — registration
    surface, schema versioning, `skills` / `skill_usage` mirror,
    sensitivity scopes.
  - [x] `contracts/action-execution-contract.md` — `ACTION_EXECUTE`
    invocation, `:capture` IPC, intent dispatch, outcome capture,
    no-network proof, undo window.
  - [x] `contracts/weekly-digest-contract.md` — `WeeklyDigestWorker`
    schedule, input window, DIGEST envelope output, idempotency.
  - [x] `quickstart.md` — bring-up, golden-path action flow, Sunday
    digest verification, acceptance checks aligned to FR-003-001..010.
- [ ] **Post-Design Constitution Re-check** — to be performed when
  this plan is reviewed in conjunction with the artifacts above.
- [ ] **Phase 2 — Tasks** (`tasks.md`) — to be generated by
  `/speckit.tasks`.
- [ ] **Phase 3 — Implement** — per `tasks.md`, enforced by the
  inherited `NoHttpClientOutsideNet` lint rule, the new
  `NoNetworkDuringActionExecutionTest`, and the contract tests
  generated for each of the four contract files above.
