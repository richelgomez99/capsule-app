# Quickstart: Orbit Actions (v1.1)

**Feature Branch**: `003-orbit-actions`
**Audience**: an Orbit contributor or auditor bringing up the v1.1 Actions layer on top of a v1 (002-shipped) device build, exercising the three new flows end-to-end.

This document is both a bring-up guide and the human-readable
acceptance test for FR-003-001..010.

---

## 1. Prerequisites

**Repo state**:

- Spec 002 fully landed and verified on a physical device
  (T110–T113 acceptance tasks complete).
- Branch `spec/003-orbit-actions` checked out (or `main` at the
  003 merge commit).
- Gradle sync OK; `:app:lintDebug` passes (the inherited
  `NoHttpClientOutsideNet` rule is intact).

**Hardware**: same as 002 — Pixel 8+ or any Android 14+ device
with AICore / Gemini Nano. AppFunctions canonical surface needs
Android 15+; on 13/14 the local registry path is exercised.

**Initial state**: clean app install or v1 install with at least
14 days of envelope history (the digest is more interesting with
data).

---

## 2. First run after upgrade

```bash
./gradlew :app:installDebug
adb logcat -c
adb shell am start -n com.capsule.app/.diary.DiaryActivity
adb logcat | grep -E "AppFunction|Migration"
```

Expect:

```
Migration: v1 → v2 OK (4 tables created, 1 column added)
AppFunctionRegistry: registered com.capsule.app.action.calendar_insert v1
AppFunctionRegistry: registered com.capsule.app.action.todo_add v1
AppFunctionRegistry: registered com.capsule.app.action.share v1
```

Verify the new tables exist:

```bash
adb shell run-as com.capsule.app sqlite3 \
  /data/data/com.capsule.app/databases/orbit.db \
  ".tables"
```

Expect (002 tables plus): `action_proposal`, `action_execution`,
`appfunction_skill`, `skill_usage`.

(SQLCipher: this only works in debug builds with the test key
helper enabled. Production builds are inaccessible to `adb`.)

---

## 3. Golden path A — calendar action

Goal: verify FR-003-001, FR-003-002, FR-003-003, FR-003-005,
FR-003-006, FR-003-008.

1. Plug into wall power; ensure wifi (unmetered).
2. In Gmail (or any messaging app), copy a flight-confirmation
   block:

   ```
   Your flight is confirmed.
   UA437 SFO → JFK
   Departs Friday May 22, 2026 at 2:15 PM
   Arrives 10:30 PM EDT
   Confirmation: ABC123
   ```

3. Tap the bubble to open the chip row. Pick `WANT_IT`. The
   envelope seals and appears in today's Diary.
4. Wait up to 60s (charger + wifi + Nano). The
   `ActionExtractionWorker` fires.
5. Re-open Diary. Under the flight envelope, an inline chip
   appears: **"+ Add to calendar — UA437 May 22 14:15"**.
6. Verify in audit:
   ```bash
   adb shell run-as com.capsule.app sqlite3 \
     /data/data/com.capsule.app/databases/orbit.db \
     "SELECT action, description FROM audit_log
      WHERE action IN ('ACTION_PROPOSED','CONTINUATION_ENQUEUED')
      ORDER BY createdAt DESC LIMIT 5;"
   ```
   Expect `ACTION_PROPOSED` row with the proposal id.
7. Tap the chip → preview card opens with editable fields. The
   end-time is +1h after start unless Nano extracted one (this
   sample didn't, so end = 15:15).
8. Edit end to 22:30. Tap **Confirm**.
9. The system Calendar app opens with the prefilled event. **Do
   not save in Calendar yet** — verify the toast in Orbit's
   Diary: **"Added to calendar — Undo (5s)"**.
10. Save the event in Calendar.
11. Audit row check:
    ```bash
    adb shell run-as com.capsule.app sqlite3 ... \
      "SELECT action, description FROM audit_log
       WHERE action LIKE 'ACTION_%'
       ORDER BY createdAt DESC LIMIT 5;"
    ```
    Expect, in order: `ACTION_EXECUTED`, `ACTION_CONFIRMED`,
    `ACTION_PROPOSED`.
12. Skill stats:
    ```bash
    adb shell run-as com.capsule.app sqlite3 ... \
      "SELECT skillId, outcome, latencyMs FROM skill_usage
       ORDER BY invokedAt DESC LIMIT 1;"
    ```
    Expect `com.capsule.app.action.calendar_insert | SUCCESS | <ms>`.

**Pass criteria**:

- Inline chip appeared within 60s of seal on charger+wifi.
- Confirm tap → system Calendar opens within 600ms p95.
- Toast appears for exactly 5s.
- Three audit rows present in order.
- One `skill_usage` row written.

---

## 4. Golden path B — to-do action (local target)

Goal: verify FR-003-001, FR-003-002, FR-003-009, FR-003-010.

1. Copy a list:
   ```
   For dinner Friday:
   - pick up wine
   - charge speakers
   - clean kitchen
   ```
2. Bubble → `WANT_IT`. Envelope seals.
3. Wait for `ActionExtractionWorker`.
4. Diary shows: **"+ Add to to-dos — 3 items"** chip.
5. Tap chip → preview shows three editable rows.
6. Tap **Confirm** with target = "Local Orbit list" (the default
   on first run; remembered after).
7. Three new envelopes appear in today's Diary, kind=REGULAR,
   intent=WANT_IT, with todo-styling.
8. Source envelope is unchanged (same id, same intent — Principle
   III preserved).
9. Audit rows: `ACTION_PROPOSED` (1), `ACTION_CONFIRMED` (1),
   `ACTION_EXECUTED` (1). Three new envelope-creation rows
   (`ENVELOPE_CREATED`) carry a `derived_from_proposal_id` extra.

**Pass criteria**:

- Source envelope unchanged.
- Three new envelopes carry provenance back to the proposal.
- Local-target todo writes did not invoke any Activity (no
  startActivity logged).
- `NoNetworkDuringActionExecutionTest` passes in the test suite
  for this handler.

---

## 5. Golden path C — Sunday digest

Goal: verify FR-003-004.

This requires either (a) waiting until Sunday 06:00 local OR
(b) test-driving via the WorkManager test driver:

```kotlin
// In a debug build's :app/src/debug helper Activity:
val driver = WorkManagerTestInitHelper.getTestDriver(context)!!
driver.setAllConstraintsMet(weeklyDigestWorkRequestId)
```

After triggering:

1. Open Sunday's diary page (swipe to Sunday, or open
   `DiaryActivity` with `EXTRA_DAY = today's-iso-date` if Sunday).
2. The topmost entry is a DIGEST envelope rendered with Fraunces
   300, ~150–250 words, summarising Mon–Sat of the previous week.
3. Below the digest: cluster suggestion card (if any cluster
   formed); below that: chronological feed.
4. Audit:
   ```bash
   adb shell run-as com.capsule.app sqlite3 ... \
     "SELECT action, description FROM audit_log
      WHERE action LIKE 'DIGEST_%' ORDER BY createdAt DESC LIMIT 3;"
   ```
   Expect `DIGEST_GENERATED` row with `weekId` and
   `envelopeCount` extras.
5. Cascade test: pick three envelopes from the digest's
   `derivedFromEnvelopeIdsJson` and soft-delete them. The DIGEST
   envelope remains as long as ≥ 1 source survives. Soft-delete
   all sources → DIGEST is soft-deleted with audit
   `ENVELOPE_INVALIDATED reason=lost_provenance`.
6. Idempotency: trigger the worker twice on the same Sunday.
   Second run audits `DIGEST_SKIPPED reason=already_exists`, no
   second envelope.

**Pass criteria**:

- Exactly one DIGEST envelope per Sunday.
- Renders at the top.
- Provenance cascade works in both directions (some-survive vs.
  none-survive).
- Re-runs are idempotent.

---

## 6. Negative paths

### N1. Sensitivity gating

Capture a clipboard with a credit-card pattern:
`4111 1111 1111 1111 exp 05/27 cvc 123`. The 002 sensitivity
scrubber flags it. Verify:

- No `ACTION_PROPOSED` audit row appears.
- `ACTION_EXTRACT` continuation runs once, audits
  `CONTINUATION_COMPLETED outcome=skipped reason=sensitivity`.

### N2. No Nano

Disable AICore (debug toggle in Settings → Diagnostics → Force
Nano UNAVAILABLE). Capture a flight confirmation. Verify:

- No proposal generated.
- `ACTION_EXTRACT` audits `CONTINUATION_COMPLETED outcome=failed
  reason=nano_unavailable`.
- App remains fully functional. The user sees no error UI.

### N3. Schema mismatch

Manually corrupt a proposal's `argsJson` in the DB to violate the
schema (debug builds only). Tap the chip → Confirm. Verify:

- `ActionExecutorService` audits `ACTION_FAILED
  reason=schema_mismatch`.
- No Intent fires.
- Proposal is moved to `state = INVALIDATED`.

### N4. No handler app

Pick the Share path with no app on the device that handles the
share MIME. Verify:

- `ActionExecutorService` audits `ACTION_FAILED reason=no_handler`.
- User-facing toast: "No app handles this".

### N5. Past undo window

Confirm a calendar event. Wait 6 seconds. The toast has dismissed.
Tap the system back to return to Orbit. Verify:

- No undo affordance available.
- `action_execution.outcome = DISPATCHED` (terminal).

### N6. Re-extraction is idempotent

Force the `ActionExtractionWorker` to re-run on an envelope that
already has a proposal. Verify:

- The `(envelopeId, functionId)` unique constraint prevents a
  duplicate row.
- Audit `CONTINUATION_COMPLETED outcome=noop`.

---

## 7. Constitution acceptance checks

Before declaring 003 done, verify all twelve principles using the
checks below. Each maps to a constitution principle.

| # | Principle | Verification |
|---|---|---|
| I | Local-first | `adb shell dumpsys netstats detail \| grep com.capsule.app` after running all golden paths. Expect the `:capture`, `:ml`, `:ui` UIDs to show 0 bytes RX/TX. |
| II | Effortless capture | The seal path latency p95 is unchanged from 002 (run `latency-bench` test suite). |
| III | Intent before artifact | Source envelope's `intent` and `intentHistoryJson` unchanged across all action flows. Verified by `ActionDoesNotMutateEnvelopeTest`. |
| IV | Continuations | `ActionExtractionWorker` runs only when constraints met (test driver verifies). `WeeklyDigestWorker` same. |
| V | Under-deliver on noise | Zero notifications from 003 code paths. `adb shell dumpsys notification \| grep capsule` shows only the FGS notification. |
| VI | Privilege separation | `:capture`'s manifest declares no INTERNET. The lint rule passes. `NoNetworkDuringActionExecutionTest` passes. |
| VII | Context beyond content | No new signals collected. `state_snapshot` schema unchanged. |
| VIII | Collect only what you use | Each new column populates a v1.1-visible feature. Verified by reading Settings → Actions and seeing every column rendered. |
| IX | Cloud escape hatch | Settings → Actions → Cloud quality toggle is OFF by default. With BYOK enabled, audit rows show `provenance=Byok` only when explicitly opted in. |
| X | Sovereign cloud storage | All 003 tables live in the same SQLCipher DB. No cloud writes from 003 in v1.1. |
| XI | Consent-aware prompts | When BYOK is enabled, the `:agent` consent filter logs every outbound prompt's category set. With BYOK off, no `:net` traffic at all from 003 paths. |
| XII | Provenance | Every `action_proposal` row → `envelopeId` resolves. Every `action_execution` → `proposalId` resolves. Cascade-delete works (verified in path 5 step 5). |

---

## 8. Acceptance gate

003 is acceptance-complete when:

1. All three golden paths pass on a Pixel 8 (Android 15) and a
   Pixel 6a (Android 14, AppFunctions compat path).
2. All six negative paths produce the expected audit rows and
   no UI errors.
3. All twelve constitution checks pass.
4. The migration v1 → v2 test passes against a real v1
   production-shape DB seeded with 1000+ envelopes.
5. Lint + unit + instrumented test suites green.

After acceptance, spec 010 (visual polish pass) refines the
chip / preview card / digest typography against `design.md`. No
functional change there — pure visual tightening.
