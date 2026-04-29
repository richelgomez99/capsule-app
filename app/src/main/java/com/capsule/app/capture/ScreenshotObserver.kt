package com.capsule.app.capture

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.provider.MediaStore
import android.util.Log
import com.capsule.app.data.ipc.IEnvelopeRepository
import com.capsule.app.data.ipc.IntentEnvelopeDraftParcel
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import java.util.concurrent.atomic.AtomicLong

/**
 * T073 (Phase 6 US4) — silently observes the user's screenshot folder and
 * turns new screenshots into `IMAGE` envelopes.
 *
 * Registered on [MediaStore.Images.Media.EXTERNAL_CONTENT_URI] by
 * [com.capsule.app.service.CapsuleOverlayService]; every `onChange`
 * triggers [queryLatestScreenshot]. The path filter keeps us scoped to
 * `Pictures/Screenshots/` and `DCIM/Screenshots/` (research.md §Screenshot
 * Observation) so camera images and user downloads are never captured.
 *
 * Seal is delegated to the running `:ml` repository binder held by the
 * overlay service. Intent classification is skipped for IMAGE envelopes
 * in v1 — they are sealed as [Intent.AMBIGUOUS] with source
 * [IntentSource.AUTO_AMBIGUOUS] so the user can reassign them from the
 * Diary card the same as any other envelope.
 *
 * This observer is **idempotent**: the MediaStore can fire multiple
 * `onChange` calls for a single capture (insert + metadata update), so
 * [lastSealedMediaIdState] de-duplicates by media-store row id.
 */
class ScreenshotObserver private constructor(
    private val contentResolver: ContentResolver,
    private val repositoryProvider: () -> IEnvelopeRepository?,
    private val stateCollector: StateSnapshotCollector,
    handler: Handler?
) : ContentObserver(handler) {

    private val lastSealedMediaIdState = AtomicLong(-1L)

    /**
     * Test seam — when non-null, [onChange] uses this provider instead of
     * [queryLatestScreenshot]. Lets `ScreenshotObserverTest` drive the
     * observer without priming a real MediaStore row.
     */
    @Volatile internal var hitSourceOverride: (() -> Hit?)? = null

    override fun onChange(selfChange: Boolean, uri: Uri?) {
        try {
            val latest = hitSourceOverride?.invoke() ?: queryLatestScreenshot() ?: return
            val prev = lastSealedMediaIdState.get()
            if (latest.mediaId <= prev) return
            lastSealedMediaIdState.set(latest.mediaId)
            sealScreenshot(latest.contentUri)
        } catch (t: Throwable) {
            Log.w(TAG, "onChange failed", t)
        }
    }

    /**
     * Query MediaStore for the most recent image under a Screenshots
     * folder. Returns null if no match (e.g. the event was for a
     * non-screenshot image).
     */
    internal fun queryLatestScreenshot(): Hit? {
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.DATE_ADDED
        )
        // `RELATIVE_PATH` values end with `/`. `Pictures/Screenshots/`
        // is the stock Android location; `DCIM/Screenshots/` covers
        // OEM skins that store there instead.
        val selection =
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ? OR " +
                "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val args = arrayOf("Pictures/Screenshots/%", "DCIM/Screenshots/%")
        val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            args,
            sort
        )?.use { c ->
            if (c.moveToFirst()) {
                val idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val mediaId = c.getLong(idCol)
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    mediaId
                )
                return Hit(mediaId = mediaId, contentUri = contentUri)
            }
        }
        return null
    }

    private fun sealScreenshot(imageUri: Uri) {
        val repo = repositoryProvider() ?: run {
            Log.d(TAG, "skip — repo unbound")
            return
        }
        val state = runCatching { stateCollector.snapshot() }.getOrNull()
            ?: run {
                Log.w(TAG, "skip — state snapshot failed")
                return
            }
        val draft = IntentEnvelopeDraftParcel(
            contentType = ContentType.IMAGE.name,
            textContent = null,
            imageUri = imageUri.toString(),
            intent = Intent.AMBIGUOUS.name,
            intentConfidence = 0f,
            intentSource = IntentSource.AUTO_AMBIGUOUS.name,
            redactionCountByType = emptyMap()
        )
        try {
            val id = repo.seal(draft, state)
            Log.d(TAG, "ENVELOPE_SEALED | id=$id | contentType=IMAGE | uri=$imageUri")
        } catch (t: Throwable) {
            Log.w(TAG, "seal() rpc failed for screenshot", t)
        }
    }

    data class Hit(val mediaId: Long, val contentUri: Uri)

    companion object {
        private const val TAG = "ScreenshotObserver"

        /** Production factory — wires a real [StateSnapshotCollector]. */
        fun create(
            context: Context,
            repositoryProvider: () -> IEnvelopeRepository?,
            handler: Handler? = null
        ): ScreenshotObserver = ScreenshotObserver(
            contentResolver = context.contentResolver,
            repositoryProvider = repositoryProvider,
            stateCollector = StateSnapshotCollector.create(context),
            handler = handler
        )

        /** Test factory — instrumented tests inject fakes. */
        internal fun createForTest(
            contentResolver: ContentResolver,
            repositoryProvider: () -> IEnvelopeRepository?,
            stateCollector: StateSnapshotCollector,
            handler: Handler? = null
        ): ScreenshotObserver = ScreenshotObserver(
            contentResolver = contentResolver,
            repositoryProvider = repositoryProvider,
            stateCollector = stateCollector,
            handler = handler
        )
    }
}
