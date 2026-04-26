# Action Execution Contract

**Feature Branch**: `003-orbit-actions`
**Date**: 2026-04-26
**Owning process**: `:capture`
**Callers**: `:ui` (Diary confirm tap), `:ml` (registry writeback)
**Implements FR**: FR-003-002, FR-003-003, FR-003-008, FR-003-010

---

## 1. Purpose

`ActionExecutorService` is the only place in Orbit where a
registered `AppFunction` is invoked. It runs in the `:capture`
process per FR-003-008, which:

- Has a stable Activity-launch context (the foreground service).
- Holds no `INTERNET` permission.
- Holds no Room connection — every persistence operation goes
  through the `:ml` binder.

Per FR-003-003, `ActionExecutorService` is invoked **only** as a
direct response to a user tap on a confirmation affordance in the
Diary. There is no scheduled, background, or autonomous path
into this service.

---

## 2. Service declaration

```xml
<service
    android:name=".action.ActionExecutorService"
    android:process=":capture"
    android:exported="false" />
```

**Action**: `com.capsule.app.action.BIND_ACTION_EXECUTOR`
**Returns**: `IActionExecutor` AIDL binder.

Only the `:ui` process binds. `android:exported="false"` prevents
external apps from invoking executions directly.

---

## 3. AIDL surface

```aidl
package com.capsule.app.action.ipc;

interface IActionExecutor {
    // Synchronously dispatches the registered AppFunction.
    // Returns immediately after Intent.startActivity() (or local DB write).
    // Outcome resolution is asynchronous via the registry writeback.
    ActionExecuteResultParcel execute(in ActionExecuteRequestParcel request);

    // Cancels a dispatched action within the 5s undo window.
    // Returns true if the cancellation was applied; false if past window.
    boolean cancelWithinUndoWindow(in String executionId);
}
```

```kotlin
// Parcels

data class ActionExecuteRequestParcel(
    val proposalId: String,
    val functionId: String,
    val schemaVersion: Int,
    val argsJson: String                // pre-validated by :ui before dispatch
)

data class ActionExecuteResultParcel(
    val executionId: String,
    val outcome: String,                // DISPATCHED | FAILED | USER_CANCELLED
    val outcomeReason: String?,
    val canUndoUntilEpochMillis: Long   // 0 if no undo window
)
```

---

## 4. Execution pipeline

```
:ui Diary tap
   ↓
   IEnvelopeRepository.markProposalConfirmed(proposalId)        [Room txn in :ml]
   ↓
   IActionExecutor.execute(ActionExecuteRequestParcel)          [crosses to :capture]
   ↓
   ActionExecutorService:
     1. Re-validate argsJson against the schema version specified
        in the request (defense in depth — schema may have bumped
        between proposal and execution; we use the proposal's
        version to honour the user's confirmed intent).
     2. Look up the function's handler in AppFunctionInvoker:
          - calendar_insert  → CalendarInsertHandler.dispatch()
          - todo_add         → TodoAddHandler.dispatch()
          - share            → ShareHandler.dispatch()
     3. Handler builds the Android Intent:
          - calendar_insert: Intent.ACTION_INSERT to
            CalendarContract.Events.CONTENT_URI with FLAG_ACTIVITY_NEW_TASK
          - todo_add (target=local): direct Room write through
            EnvelopeRepository binder (creates a new envelope of
            kind=REGULAR, intent=WANT_IT, with todo_meta JSON)
          - todo_add (target=external): Intent.ACTION_SEND with
            EXTRA_TEXT (user-chosen target app)
          - share: Intent.ACTION_SEND
     4. context.startActivity(intent) — handler context is the
        :capture foreground service Context. Intent firing
        cannot fail synchronously except for "no app handles
        this intent" (handler catches ActivityNotFoundException).
     5. IEnvelopeRepository.recordActionInvocation(
            executionId, functionId, proposalId,
            outcome=DISPATCHED, latencyMs)
     6. Schedule a 5s undo window via WorkManager
        (DelayedUndoCleanupWorker).
     7. Return ActionExecuteResultParcel.
   ↓
   :ui shows "Added to calendar — undo" toast for 5s
   ↓
   (optional) user taps undo → IActionExecutor.cancelWithinUndoWindow
                            → updates outcome=USER_CANCELLED
                            → audit ACTION_FAILED reason=user_cancelled
   ↓
   (5s elapses) → DelayedUndoCleanupWorker no-ops (outcome stays DISPATCHED)
```

**Failure modes**:

| Failure | Outcome | Audit |
|---|---|---|
| Schema validation fails | FAILED | `ACTION_FAILED reason=schema_mismatch` |
| `ActivityNotFoundException` | FAILED | `ACTION_FAILED reason=no_handler` |
| AppFunction throws | FAILED | `ACTION_FAILED reason=handler_${exceptionClass}` |
| User taps undo within 5s | USER_CANCELLED | `ACTION_FAILED reason=user_cancelled` |
| Past undo window | DISPATCHED (terminal in v1.1) | `ACTION_EXECUTED` already written |

---

## 5. Reversibility

Per the AppFunctionSkill `reversibility` field:

- **REVERSIBLE_24H** (local todo_add): the created envelope is
  soft-deleted on undo (within 5s OR via the standard 002 24h-undo
  path). After 24h, the envelope behaves like any 002 envelope.
- **EXTERNAL_MANAGED** (calendar_insert, share, external todo_add):
  the 5s in-app undo only suppresses the audit / proposal state.
  The external app may have already accepted the intent (e.g.,
  user confirmed in system Calendar within 5s). Orbit cannot
  retract the external write. The user manages the inserted item
  in the target app.

This honesty about reversibility is surfaced in the preview card:
"Once added, you'll need to edit it in Calendar."

---

## 6. No-network proof

Spec 003 Principle VI gate is enforced by:

1. **Manifest**: the `action/` package is `android:process=":capture"`,
   and `:capture` does not declare `INTERNET`.
2. **Lint**: the inherited `NoHttpClientOutsideNet` rule fails the
   build if any class in `com.capsule.app.action.*` references
   `okhttp3`, `HttpURLConnection`, or `java.net.Socket`.
3. **Runtime test**:
   `NoNetworkDuringActionExecutionTest` (instrumented) exercises
   each handler with a controlled `StrictMode` policy that
   detects any network access from the `:capture` process and
   fails the test.

---

## 7. Constitution alignment

| Principle | How execution honours it |
|---|---|
| I (Local-first) | No network in `:capture`. External-app writes are kernel-mediated `Intent` IPC, not network. |
| III (Intent before artifact) | Source envelope's intent is unchanged. Execution adds an `ACTION_CONFIRMED` then `ACTION_EXECUTED` audit row; the envelope itself is untouched. |
| V (Under-deliver on noise) | No notifications. The 5s undo is a Diary toast — non-interrupting. |
| VI (Privilege separation) | Execution in `:capture`; persistence in `:ml`; UI in `:ui`. Each crosses one binder boundary. |
| IX (Cloud escape hatch) | Execution does not call any LLM. The cloud LLM (if BYOK) was already consumed during extraction; this surface is post-extraction. |
| XII (Provenance) | Every execution carries `proposalId` (→ `envelopeId`). Cascade-delete preserved end-to-end. |

---

## 8. Test surface

| Test | What it verifies |
|---|---|
| `ActionExecutorServiceTest` (instrumented, `:capture`) | Each handler dispatches the correct Intent shape. Mocked Activity-launch context. Schema validation rejection. |
| `CalendarInsertHandlerTest` (instrumented) | Intent extras match CalendarContract spec; FLAG_ACTIVITY_NEW_TASK present; tz handled. |
| `TodoAddHandlerLocalTest` (instrumented) | Local-target todo creates a kind=REGULAR envelope with intent=WANT_IT and todo_meta JSON. |
| `TodoAddHandlerExternalTest` (instrumented) | External-target todo fires ACTION_SEND with EXTRA_TEXT; remembered target app preference. |
| `UndoWindowTest` (instrumented) | Cancel within 5s flips outcome to USER_CANCELLED; cancel after 5s no-ops. |
| `NoNetworkDuringActionExecutionTest` (instrumented) | StrictMode + UID socket guard during each handler dispatch. |
| `ExecutionIpcContractTest` (instrumented) | Unauthorised binder access denied. Only `:ui` can bind. |

---

## 9. UI flow (informative, owned by `:ui`)

The Diary chip-and-confirm flow is rendered by `ActionProposalChipUI`
and `ActionPreviewCardUI`. The contract above is the
post-confirmation surface. Pre-confirmation UI:

```
[Envelope card]
   "Flight confirmation: UA437, May 22, departing SFO 14:15…"
   ↓ ┌──────────────────────────────────────────┐
     │ + Add to calendar — UA437 May 22 14:15   │  ← chip (inline)
     └──────────────────────────────────────────┘
     ↓ tap
     ┌──────────────────────────────────────────┐
     │ Add to calendar                          │  ← preview card (modal)
     │ Title:   UA437 SFO → JFK                 │
     │ Start:   May 22, 14:15  America/Los_…    │
     │ End:     May 22, 22:30                   │
     │ Location: SFO Terminal 3                 │
     │                                          │
     │ Once added, you'll need to edit it in    │
     │ Calendar.                                │
     │                                          │
     │           [ Cancel ]  [ Confirm ]        │
     └──────────────────────────────────────────┘
     ↓ Confirm
   IActionExecutor.execute(...)
   ↓
     ┌──────────────────────────────────────────┐
     │ ✓ Added to calendar       Undo   (5s)    │  ← toast
     └──────────────────────────────────────────┘
```

Visual specifics (typography, color, motion) deferred to
`design.md` and spec 010 visual polish pass.
