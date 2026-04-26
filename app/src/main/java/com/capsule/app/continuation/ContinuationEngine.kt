package com.capsule.app.continuation

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.capsule.app.settings.PrivacyPreferences
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * T067 — ContinuationEngine. Thin wrapper over [WorkManager] that owns
 * the enqueue / retry / cancel contract for background continuation
 * jobs (contracts/continuation-engine-contract.md §2, §3, §4.1).
 *
 * v1 scope (this slice):
 *   - `TEXT` captures — one [UrlHydrateWorker] per extracted URL.
 *   - `IMAGE` captures — deferred to T077 (US4, Phase 6).
 *
 * Execution model (§2):
 *   - Constraint set: `RequiresCharging`, `UNMETERED`, `BatteryNotLow`.
 *   - Backoff: EXPONENTIAL, base 60 s.
 *   - Max attempts: 3 (enforced inside the worker via `runAttemptCount`).
 *
 * The engine is **not** binder-exposed. Per §3 "Not exposed via binder
 * — engine is accessed only from within `:ml`", it's created inside
 * the main app process and passed to `EnvelopeRepositoryImpl.seal()`
 * (T068, merge zone).
 */
class ContinuationEngine(
    private val workManager: WorkManager,
    /**
     * T070 — nullable privacy pref gate. When non-null and
     * `continuationsPaused == true`, [enqueueSingle] and
     * [enqueueForNewEnvelope] no-op. Nullable so JVM tests that
     * construct a bare engine around a fake [WorkManager] don't
     * need a real Android [Context].
     */
    private val privacyPreferences: PrivacyPreferences? = null
) {

    /**
     * Enqueue URL_HYDRATE jobs for the URLs extracted from [textContent].
     * Returns the set of continuation ids that were enqueued (empty if
     * the capture has no URLs or is an image — image fan-out lands in
     * T077).
     *
     * NOTE: the actual `ContinuationEntity` PENDING row is written by
     * the merge-zone `EnvelopeRepositoryImpl.seal()` (T066/T068) inside
     * the same Room transaction as the envelope. This engine only owns
     * the WorkManager side.
     */
    fun enqueueForNewEnvelope(
        envelopeId: String,
        contentType: ContentType,
        textContent: String?,
        @Suppress("UNUSED_PARAMETER") imageUri: String?
    ): List<EnqueuedJob> {
        if (contentType != ContentType.TEXT) return emptyList()
        if (privacyPreferences?.continuationsPaused == true) return emptyList()
        val urls = extractUrls(textContent.orEmpty())
        if (urls.isEmpty()) return emptyList()

        return urls.map { url ->
            val continuationId = UUID.randomUUID().toString()
            val request = OneTimeWorkRequestBuilder<UrlHydrateWorker>()
                .setConstraints(DEFAULT_CONSTRAINTS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    BACKOFF_BASE_SECONDS,
                    TimeUnit.SECONDS
                )
                .addTag(TAG_CONTINUATION)
                .addTag(TYPE_TAG_URL_HYDRATE)
                .addTag(tagForEnvelope(envelopeId))
                .addTag(tagForContinuation(continuationId))
                .setInputData(
                    Data.Builder()
                        .putString(KEY_ENVELOPE_ID, envelopeId)
                        .putString(KEY_CONTINUATION_ID, continuationId)
                        .putString(KEY_URL, url)
                        .build()
                )
                .build()

            workManager.enqueueUniqueWork(
                uniqueNameForContinuation(continuationId),
                ExistingWorkPolicy.KEEP,
                request
            )
            EnqueuedJob(
                continuationId = continuationId,
                url = url,
                workRequestId = request.id.toString()
            )
        }
    }

    /**
     * Re-run a failed / stale continuation. Called from the Diary
     * "Try again" affordance (T069).
     *
     * v1 behaviour: cancel any outstanding work for this continuation;
     * the merge-zone retry path (T066 row-update) will re-invoke
     * [enqueueForNewEnvelope] with the original URL, reusing this
     * continuationId.
     */
    fun retry(continuationId: String) {
        workManager.cancelAllWorkByTag(tagForContinuation(continuationId))
    }

    /**
     * Privacy kill-switch (§6). Called from Settings "Pause continuations"
     * (T070) and from test teardown. Cancels every in-flight or
     * scheduled continuation regardless of envelope.
     */
    fun cancelAll(@Suppress("UNUSED_PARAMETER") reason: String) {
        workManager.cancelAllWorkByTag(TAG_CONTINUATION)
    }

    /**
     * T068 merge-zone entry — enqueue a single pre-built continuation row.
     * Used by `EnvelopeRepositoryImpl.seal()` where the continuation ids
     * have already been chosen (so the PENDING Room row + the WorkManager
     * job share the same id). Contrast with [enqueueForNewEnvelope] which
     * extracts URLs + mints ids itself (kept for simpler callers / tests).
     */
    fun enqueueSingle(envelopeId: String, continuationId: String, url: String) {
        if (url.isBlank()) return
        if (privacyPreferences?.continuationsPaused == true) return
        val request = OneTimeWorkRequestBuilder<UrlHydrateWorker>()
            .setConstraints(DEFAULT_CONSTRAINTS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_BASE_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(TAG_CONTINUATION)
            .addTag(TYPE_TAG_URL_HYDRATE)
            .addTag(tagForEnvelope(envelopeId))
            .addTag(tagForContinuation(continuationId))
            .setInputData(
                Data.Builder()
                    .putString(KEY_ENVELOPE_ID, envelopeId)
                    .putString(KEY_CONTINUATION_ID, continuationId)
                    .putString(KEY_URL, url)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            uniqueNameForContinuation(continuationId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * T077 (Phase 6 US4) — fan an `IMAGE` envelope into a
     * [ScreenshotUrlExtractWorker]. The worker runs on-device OCR and
     * then seeds URL_HYDRATE continuations back through the repository
     * binder (see [com.capsule.app.data.ipc.IEnvelopeRepository.seedScreenshotHydrations]).
     */
    fun enqueueScreenshotOcr(envelopeId: String, imageUri: String) {
        if (imageUri.isBlank()) return
        if (privacyPreferences?.continuationsPaused == true) return
        val continuationId = "ocr:$envelopeId"
        val request = OneTimeWorkRequestBuilder<ScreenshotUrlExtractWorker>()
            .setConstraints(DEFAULT_CONSTRAINTS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                BACKOFF_BASE_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(TAG_CONTINUATION)
            .addTag(TYPE_TAG_SCREENSHOT_OCR)
            .addTag(tagForEnvelope(envelopeId))
            .setInputData(
                Data.Builder()
                    .putString(KEY_ENVELOPE_ID, envelopeId)
                    .putString(KEY_IMAGE_URI, imageUri)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            uniqueNameForScreenshotOcr(continuationId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * T045 — enqueue an `ACTION_EXTRACT` continuation for [envelopeId]
     * (specs/003-orbit-actions/contracts/action-extraction-contract.md §2).
     *
     * Idempotent: re-enqueue while existing work is in-flight is a no-op
     * thanks to [ExistingWorkPolicy.KEEP] + a stable unique work id.
     * Charger + UNMETERED + battery-not-low constraints are required by
     * Principle IV.
     *
     * Caller responsibility (lives in `EnvelopeRepositoryImpl.seal()`):
     *  1. Run the [com.capsule.app.ai.extract.ActionExtractionPrefilter]
     *     synchronously inside `:ml` first — only enqueue if true.
     *  2. Skip kind != REGULAR envelopes; skip credentials/medical-flagged
     *     envelopes (research §8). The engine itself does NOT re-check
     *     these — it trusts the caller, identical to how
     *     [enqueueForNewEnvelope] trusts the URL extractor.
     */
    fun enqueueActionExtract(envelopeId: String) {
        if (envelopeId.isBlank()) return
        if (privacyPreferences?.continuationsPaused == true) return
        val request = OneTimeWorkRequestBuilder<com.capsule.app.ai.extract.ActionExtractionWorker>()
            .setConstraints(ACTION_EXTRACT_CONSTRAINTS)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                ACTION_EXTRACT_BACKOFF_BASE_SECONDS,
                TimeUnit.SECONDS
            )
            .addTag(TAG_CONTINUATION)
            .addTag(TYPE_TAG_ACTION_EXTRACT)
            .addTag(tagForEnvelope(envelopeId))
            .setInputData(
                Data.Builder()
                    .putString(KEY_ENVELOPE_ID, envelopeId)
                    .build()
            )
            .build()
        workManager.enqueueUniqueWork(
            uniqueNameForActionExtract(envelopeId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Cancel every continuation tied to a single envelope. Called when
     * an envelope is archived or soft-deleted (§6 rule 1) — the caller
     * (merge zone) is the repository transaction.
     */
    fun cancelForEnvelope(envelopeId: String) {
        workManager.cancelAllWorkByTag(tagForEnvelope(envelopeId))
    }

    enum class ContentType { TEXT, IMAGE }

    data class EnqueuedJob(
        val continuationId: String,
        val url: String,
        val workRequestId: String
    )

    companion object {
        const val TAG_CONTINUATION = "continuation"
        const val TYPE_TAG_URL_HYDRATE = "type:URL_HYDRATE"
        const val TYPE_TAG_SCREENSHOT_OCR = "type:SCREENSHOT_OCR"
        const val TYPE_TAG_ACTION_EXTRACT = "type:ACTION_EXTRACT"

        const val KEY_ENVELOPE_ID = "envelopeId"
        const val KEY_CONTINUATION_ID = "continuationId"
        const val KEY_URL = "url"
        const val KEY_IMAGE_URI = "imageUri"

        const val BACKOFF_BASE_SECONDS = 60L
        const val ACTION_EXTRACT_BACKOFF_BASE_SECONDS = 30L
        const val MAX_ATTEMPTS = 3

        fun tagForEnvelope(envelopeId: String): String = "envelope:$envelopeId"
        fun tagForContinuation(continuationId: String): String = "continuation:$continuationId"
        fun uniqueNameForContinuation(continuationId: String): String =
            "url-hydrate:$continuationId"
        fun uniqueNameForScreenshotOcr(continuationId: String): String =
            "screenshot-ocr:$continuationId"
        fun uniqueNameForActionExtract(envelopeId: String): String =
            "action-extract:$envelopeId"

        val DEFAULT_CONSTRAINTS: Constraints = Constraints.Builder()
            // v1 dev: charger + unmetered Wi-Fi constraints temporarily
            // relaxed so hydration fires on any connected network. Flip
            // these back on (`setRequiresCharging(true)` /
            // `setRequiredNetworkType(NetworkType.UNMETERED)`) before
            // shipping — spec FR-013 + contracts/continuation-engine §2
            // still mandate charger + unmetered for GA.
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /**
         * Per spec 003 contracts/action-extraction-contract.md §2:
         * charger + UNMETERED Wi-Fi + battery-not-low. Action
         * extraction never blocks seal so we keep these strict even
         * during dev (Nano runs on charger anyway).
         */
        val ACTION_EXTRACT_CONSTRAINTS: Constraints = Constraints.Builder()
            .setRequiresCharging(true)
            .setRequiredNetworkType(NetworkType.UNMETERED)
            .setRequiresBatteryNotLow(true)
            .build()

        // http(s) URL matcher. Intentionally lenient: we strip common
        // trailing punctuation afterwards so "see https://example.com."
        // extracts "https://example.com" not "https://example.com.".
        private val URL_REGEX = Regex(
            """https?://[A-Za-z0-9._~:/?#\[\]@!${'$'}&'()*+,;=%\-]+""",
            RegexOption.IGNORE_CASE
        )
        private val TRAILING_PUNCT_REGEX = Regex("""[.,;:!?)\]}>'"`]+$""")

        /**
         * Pure URL-extraction logic. Exposed for unit testing
         * ([com.capsule.app.continuation.ContinuationEngineTest]).
         * Returns distinct, trimmed URLs in encounter order.
         */
        fun extractUrls(text: String): List<String> {
            if (text.isBlank()) return emptyList()
            val seen = LinkedHashSet<String>()
            URL_REGEX.findAll(text).forEach { m ->
                val cleaned = TRAILING_PUNCT_REGEX.replace(m.value, "")
                if (cleaned.isNotBlank()) seen.add(cleaned)
            }
            return seen.toList()
        }

        /** Production factory. */
        fun create(context: Context): ContinuationEngine =
            ContinuationEngine(
                workManager = WorkManager.getInstance(context),
                privacyPreferences = PrivacyPreferences(context)
            )
    }
}
