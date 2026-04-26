// IEnvelopeRepository.aidl
package com.capsule.app.data.ipc;

import com.capsule.app.data.ipc.IntentEnvelopeDraftParcel;
import com.capsule.app.data.ipc.StateSnapshotParcel;
import com.capsule.app.data.ipc.EnvelopeViewParcel;
import com.capsule.app.data.ipc.DayPageParcel;
import com.capsule.app.data.ipc.IEnvelopeObserver;
import com.capsule.app.data.ipc.ActionProposalParcel;
import com.capsule.app.data.ipc.AppFunctionSummaryParcel;
import com.capsule.app.data.ipc.IActionProposalObserver;

interface IEnvelopeRepository {

    // ---- Seal path (called by :capture) ----
    String seal(
        in IntentEnvelopeDraftParcel draft,
        in StateSnapshotParcel state
    );

    // ---- Read path (called by :ui) ----
    void observeDay(String isoDate, IEnvelopeObserver observer);
    void stopObserving(IEnvelopeObserver observer);
    EnvelopeViewParcel getEnvelope(String envelopeId);

    // ---- Mutate path (called by :ui) ----
    void reassignIntent(String envelopeId, String newIntentName, String reasonOpt);
    void archive(String envelopeId);
    void delete(String envelopeId);
    boolean undo(String envelopeId);

    // ---- Soft-delete / trash ----
    void restoreFromTrash(String envelopeId);
    List<EnvelopeViewParcel> listSoftDeletedWithinDays(int days);
    int countSoftDeletedWithinDays(int days);
    // T091a — user-initiated hard purge from Trash (bypasses soft-delete,
    // audits ENVELOPE_HARD_PURGED with reason="user_purge").
    void hardDelete(String envelopeId);

    // ---- Diagnostics ----
    int countAll();
    int countArchived();
    int countDeleted();

    // ---- Multi-day pager (T056 / T050 follow-up) ----
    // Returns ISO-8601 local dates (newest first) that have at least one
    // non-archived, non-deleted envelope. Paginated so the DiaryPagingSource
    // can fetch older non-empty days in batches as the user swipes.
    List<String> distinctDayLocalsWithContent(int limit, int offset);

    // ---- Pre-seal classification support ----
    // Exposes the silent-wrap prior-match check so the UI-side
    // SilentWrapPredicate can decide chip-row vs silent-wrap before seal().
    // Mirrors EnvelopeStorageBackend.existsNonArchivedNonDeletedInLast30Days.
    boolean existsPriorIntent(String appCategory, String intent);

    // ---- URL hydration write-back (T066 merge-zone completion) ----
    // Called by UrlHydrateWorker from the default WorkManager process once
    // the network fetch + Readability + Nano summariser pass is done.
    // Writes ContinuationResultEntity (on success), updates
    // ContinuationEntity.status, and emits a CONTINUATION_COMPLETED audit
    // row — all in a single Room transaction (audit-log-contract.md §6).
    //
    // Nullable string params carry null via AIDL's standard string-null
    // handling. `ok == true` means the fetch + summariser produced a
    // persistable result; `ok == false` means terminal failure and the
    // ContinuationEntity transitions to FAILED_PERMANENT regardless of
    // the original errorKind (transient errors are retried by WorkManager
    // without ever hitting this method).
    void completeUrlHydration(
        String continuationId,
        String envelopeId,
        String canonicalUrl,
        String canonicalUrlHash,
        boolean ok,
        String title,
        String domain,
        String summary,
        String summaryModel,
        String failureReason
    );

    // ---- Manual hydration retry (T069 follow-up) ----
    // Called from Diary when the user taps the "Couldn't enrich this
    // link. Try again" affordance. Looks up non-succeeded continuations
    // for the envelope and re-enqueues each via ContinuationEngine.
    void retryHydration(String envelopeId);

    // ---- Screenshot OCR hydration (T076 / Phase 6 US4) ----
    // Called by ScreenshotUrlExtractWorker from the default WorkManager
    // process after running on-device OCR against an IMAGE envelope.
    // Writes one PENDING URL_HYDRATE ContinuationEntity per unique URL,
    // dedupes against the canonical-hash cache (emitting URL_DEDUPE_HIT
    // rows for cache hits), enqueues URL_HYDRATE WorkManager jobs, and
    // emits one INFERENCE_RUN audit row summarising the OCR pass (kind,
    // ocrLen, urlCount). All in a single Room transaction.
    //
    // `ocrText` is only used for the audit metadata (length + URL count).
    // Raw OCR text is NOT persisted anywhere (principle VIII — Collect
    // Only What You Use).
    void seedScreenshotHydrations(
        String envelopeId,
        String ocrText,
        in String[] urls
    );

    // ---- Spec 003 v1.1 — Orbit Actions ----

    // Looks up the registered AppFunction by id at the *latest* schema
    // version. Returns null when not registered.
    AppFunctionSummaryParcel lookupAppFunction(String functionId);

    // All registered AppFunctions for an app. v1.1 only ever returns the
    // built-in Orbit set; spec 008 expands to third-party packages.
    List<AppFunctionSummaryParcel> listAppFunctions(String appPackage);

    // Records a finished action invocation: writes the action_execution row,
    // updates the proposal state, and emits the matching audit row inside
    // a single Room transaction (audit-log-contract.md §6).
    //
    // `outcome` is one of "DISPATCHED" | "SUCCESS" | "FAILED" | "USER_CANCELLED".
    // `episodeId` is null in v1.1.
    void recordActionInvocation(
        String executionId,
        String proposalId,
        String functionId,
        String outcome,
        String outcomeReason,
        long dispatchedAtMillis,
        long completedAtMillis,
        long latencyMs,
        String episodeId
    );

    // User confirmed a proposal in the chip-row. Flips `state` PROPOSED→CONFIRMED
    // (no-op when already non-PROPOSED to keep concurrent taps safe) and
    // emits an ACTION_CONFIRMED audit row.
    boolean markProposalConfirmed(String proposalId);

    // User dismissed a proposal. Flips `state` PROPOSED→DISMISSED and emits
    // an ACTION_DISMISSED audit row.
    boolean markProposalDismissed(String proposalId);

    // Live feed of non-terminal proposals for an envelope, used by the
    // Diary card chip-row. Parallels `observeDay`/`stopObserving` lifecycle.
    void observeProposalsForEnvelope(String envelopeId, IActionProposalObserver observer);
    void stopObservingProposals(IActionProposalObserver observer);

    // T044 — ACTION_EXTRACT continuation entry point. Called by
    // [com.capsule.app.ai.extract.ActionExtractionWorker] from the default
    // WorkManager process; the worker binds to :ml's EnvelopeRepositoryService
    // and forwards the envelopeId. The :ml-side implementation runs the
    // [ActionExtractor] (which holds the OrbitDatabase reference) and writes
    // ActionProposal rows + audit rows in one Room transaction.
    //
    // Returns a structured outcome code:
    //   "PROPOSED:<count>"     — N rows inserted
    //   "NO_CANDIDATES"        — extractor produced nothing (success, no row)
    //   "SKIPPED:<reason>"     — kind/sensitivity gate skipped
    //   "FAILED:<reason>"      — Nano timeout / exception → caller retries
    String extractActionsForEnvelope(String envelopeId);

    // T061 — TodoActionHandler local-target dispatch entry point.
    //
    // Inserts one new envelope per parsed item in a single Room transaction:
    //   kind=REGULAR, intent=WANT_IT, intentSource=AUTO_AMBIGUOUS,
    //   todoMetaJson={"items":[{"text":..., "done":false,
    //                            "dueEpochMillis":<long-or-null>}],
    //                 "derivedFromProposalId":"<proposalId>"}.
    // Source envelope is NOT mutated (Principle III).
    //
    // `itemsJson` is a JSON array: each element either a string (text only)
    // or an object {"text":"…","dueEpochMillis":<long>}. Anything malformed
    // is dropped; if the array is empty, no envelope is created.
    //
    // Returns the list of new envelope ids (in input order).
    List<String> createDerivedTodoEnvelope(
        String parentEnvelopeId,
        String itemsJson,
        String proposalId
    );

    // T064 — checkbox toggle for derived-todo envelopes. No-op if the
    // envelope has no `todoMetaJson` or the index is out of range.
    void setTodoItemDone(String envelopeId, int itemIndex, boolean done);

    // T072 / T074 — WeeklyDigestWorker entry point. `targetDayLocal` is
    // the ISO-8601 local Sunday this digest is for (e.g. "2026-04-26").
    // Returns one of:
    //   "GENERATED:<envelopeId>"  — DIGEST envelope inserted
    //   "SKIPPED:too_sparse"      — < 3 source envelopes in window
    //   "SKIPPED:already_exists"  — partial unique index conflict
    //   "FAILED:<short_reason>"   — worker maps to Result.retry()
    String runWeeklyDigest(String targetDayLocal);
}
