// IEnvelopeRepository.aidl
package com.capsule.app.data.ipc;

import com.capsule.app.data.ipc.IntentEnvelopeDraftParcel;
import com.capsule.app.data.ipc.StateSnapshotParcel;
import com.capsule.app.data.ipc.EnvelopeViewParcel;
import com.capsule.app.data.ipc.DayPageParcel;
import com.capsule.app.data.ipc.IEnvelopeObserver;

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
}
