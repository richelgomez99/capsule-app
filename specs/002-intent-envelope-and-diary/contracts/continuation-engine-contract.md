# Continuation Engine Contract: Intent Envelope and Diary

**Feature Branch**: `002-intent-envelope-and-diary`
**Date**: 2026-04-16
**Owning process**: `:ml` (engine), with network work delegated to `:net` and ML inference delegated to on-device Gemini Nano (also in `:ml`).
**Callers**: `EnvelopeRepository.seal()` (automatic enqueue); user-initiated re-run from Diary (manual enqueue).

---

## 1. Purpose

The Continuation Engine is the background worker that turns a raw
capture into a useful artifact:

- URLs in text captures ⇒ title + 2–3 sentence summary.
- Screenshots ⇒ OCR ⇒ URL extraction ⇒ URL hydration as above.
- Future (Phase 2): transcription, entity extraction, linking.

Principle enforced: **II (Continuations Grow Captures)** and
**IV (Under-Deliver on Noise)**. The engine is opportunistic: it runs
only when the device is charging and on unmetered Wi-Fi, and it never
surfaces a continuation if the yield is low-confidence.

---

## 2. Execution model

- WorkManager. All continuation jobs are `CoroutineWorker` subclasses
  tagged with `continuation` and `type:<TYPE>`.
- Constraints per job (defaults, overridable per envelope priority):
  - `setRequiresCharging(true)`
  - `setRequiredNetworkType(NetworkType.UNMETERED)`
  - `setRequiresBatteryNotLow(true)`
- Backoff: `BackoffPolicy.EXPONENTIAL`, starting at 60s, max 1h.
- Max attempts: 3. Beyond that, Continuation.status = `FAILED_MAX_RETRIES`,
  surfaced in Settings → "What Orbit did today" but not in Diary.

---

## 3. Public API (inside `:ml`)

```kotlin
interface ContinuationEngine {
    // Called by EnvelopeRepository.seal() inside the same transaction.
    // Inserts PENDING Continuation rows and enqueues WorkManager jobs.
    fun enqueueForNewEnvelope(envelopeId: EnvelopeId, contentType: ContentType, textContent: String?, imageUri: String?)

    // Called from Diary "Try again" affordance for failed or stale continuations.
    fun retry(continuationId: ContinuationId)

    // Called from Settings / privacy switch.
    fun cancelAll(reason: String)
}
```

Not exposed via binder — engine is accessed only from within `:ml`.

---

## 4. Continuation types (v1)

### 4.1 `URL_HYDRATE`

**Trigger**: `seal()` sees `contentType == TEXT` and the text contains at
least one URL (regex + URL sanitiser). If multiple URLs are present,
one `URL_HYDRATE` continuation is enqueued **per URL**, each linked to
the same envelope.

**Steps**:
1. Worker picks up the job.
2. Binds to `INetworkGateway` in `:net`.
3. Calls `fetchPublicUrl(url, timeoutMs=10000)`.
4. On `ok=false`: write Continuation.status = `FAILED`,
   `failureReason = errorKind`. Audit `NETWORK_FETCH` entry already
   written by the gateway/engine pair.
5. On `ok=true`:
   a. Run Readability on `readableHtml`. (Readability itself runs in
      `:ml` — it's just jsoup + a port of Mozilla's algorithm, no
      network.)
   b. Build a "content slug": title + first 1500 chars of readable
      text.
   c. Ask Gemini Nano for a 2–3 sentence neutral summary. Prompt
      template lives in `prompts/url_summary.kt`. On Nano unavailable
      (SC-010 fallback), skip summary and store only title + domain.
   d. Persist `ContinuationResultEntity` with
      `title`, `domain`, `summary`, `summaryModel`.
   e. Mark Continuation.status = `SUCCEEDED`.
   f. Audit `CONTINUATION_COMPLETED`.

**Latency target**: p95 ≤ 30s from enqueue to result when charging +
unmetered (SC-003).

### 4.2 `SCREENSHOT_URL_EXTRACT`

**Trigger**: `seal()` sees `contentType == IMAGE`.

**Steps**:
1. Worker picks up the job.
2. Loads the image from `imageUri` via `ContentResolver` (read-only
   access; the file is not copied).
3. Runs ML Kit text recognition on-device. This is the only OCR path.
4. Extracts URL-like substrings from recognised text with the same
   sanitiser used at seal.
5. Persists `ContinuationResultEntity` carrying extracted text +
   domain list.
6. For each unique URL, enqueues a `URL_HYDRATE` continuation
   (reusing the pipeline above), linked to the same envelope.
7. Audit `CONTINUATION_COMPLETED` for the OCR continuation itself.

**If no URL found**: status = `SUCCEEDED_EMPTY`. We still save OCR
text because it's useful for search; Diary does not show a "link
card" for this envelope.

### 4.3 Deferred for v1

- `TEXT_SUMMARY` for long pasted text with no URL — added in v1.1.
- `THREAD_MERGE` for cross-day thread suggestions — Phase 2.

---

## 5. Scheduling fairness

- At most **5 continuations** in flight concurrently across types.
- Per-envelope cap: 10 continuations total in v1 (protects against
  pathological captures with dozens of URLs).
- Priority queue ordering:
  1. Envelopes created in the last 24h > older envelopes.
  2. User-triggered retries > automatic enqueues.
  3. OCR jobs > URL hydrations (so URLs found in screenshots can fan
     out sooner).

---

## 6. Cancellation & privacy kill-switch

Three ways a continuation can be cancelled:

1. **User archives or deletes the envelope**: repository calls
   `WorkManager.cancelAllWorkByTag("envelope:$envelopeId")`. Any
   in-flight job observes cancellation cooperatively.
2. **User toggles "Pause continuations" in Settings**: repository
   calls `ContinuationEngine.cancelAll("user_paused")`. Jobs are
   cancelled and re-enqueue is blocked until the toggle flips back.
3. **Device retention sweep**: on 30-day hard delete of an envelope,
   related continuations and results are hard-deleted.

Cancelled continuations record `Continuation.status = CANCELLED` and
`failureReason = <reason>`.

---

## 7. Determinism & testability

- Each worker is pure given its inputs — no reads from SharedPreferences
  beyond feature flags.
- The engine injects `Clock`, `Random`, and the network gateway,
  making every step unit-testable.
- A **Fake Network Gateway** and **Fake Nano Summariser** are shipped
  in `:app-test` to support offline instrumented tests.

---

## 8. Observability

- Local-only counters: `continuations_enqueued`, `continuations_succeeded`,
  `continuations_failed`, broken out by type. Readable via
  `adb shell am broadcast -a com.capsule.app.DEBUG_DUMP` (dev builds
  only).
- Each continuation writes at most 3 audit entries: `CONTINUATION_ENQUEUED`,
  (`NETWORK_FETCH` during execution), `CONTINUATION_COMPLETED` or
  `CONTINUATION_FAILED`.

---

## 9. Error taxonomy

| Continuation.status | Meaning | UI treatment |
|---|---|---|
| `PENDING` | Waiting for constraints | Hidden from Diary |
| `RUNNING` | WorkManager has picked it up | Hidden |
| `SUCCEEDED` | Result persisted | Card upgraded with summary |
| `SUCCEEDED_EMPTY` | Ran but nothing to show | No card change |
| `FAILED` | Single attempt failed, retry pending | Hidden unless last attempt |
| `FAILED_MAX_RETRIES` | No more retries | Faint "Couldn't enrich this link" + "Try again" in card menu |
| `CANCELLED` | User or system cancelled | Hidden |

---

## 10. Tests (Contract)

- `enqueueForNewEnvelope` for pure text: zero Continuations.
- For text with 1 URL: exactly one `URL_HYDRATE`.
- For text with 3 URLs: exactly three, each linked to the same envelope.
- For image: one `SCREENSHOT_URL_EXTRACT`, fanning into N `URL_HYDRATE`.
- Retry on `http_error 503`: worker re-runs up to 3 times, backoff
  respected.
- Retry on `blocked_host`: no retry, status = `FAILED_MAX_RETRIES`
  immediately (with attemptCount=1, max=1 semantic for non-retriables).
- Cancel on archive: in-flight job returns `Result.failure()` and
  status becomes `CANCELLED`.
- Pause kill-switch: all new `enqueueForNewEnvelope` calls no-op until
  resumed.
- Nano unavailable: `URL_HYDRATE` still stores title + domain, no
  summary, `summaryModel = "fallback"`.
