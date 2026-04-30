package com.capsule.app.capture

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.data.ipc.IEnvelopeObserver
import com.capsule.app.data.ipc.IEnvelopeRepository
import com.capsule.app.data.ipc.IntentEnvelopeDraftParcel
import com.capsule.app.data.ipc.StateSnapshotParcel
import com.capsule.app.data.ipc.EnvelopeViewParcel
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList

/**
 * T071 (Phase 6 US4) — ScreenshotObserver contract test.
 *
 * Drives the observer with a deterministic [Hit] source (the production
 * path relies on MediaStore, which is noisy to prime in instrumented
 * tests) and asserts that:
 *   - [ScreenshotObserver.onChange] triggers a single `seal()` call on
 *     the bound [IEnvelopeRepository] with `contentType=IMAGE` and the
 *     correct content URI.
 *   - Re-firing `onChange` for the same media id is a no-op (idempotency
 *     against MediaStore's insert+metadata-update double-fire).
 *   - A newer media id triggers a second seal.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotObserverTest {

    @Test
    fun onChange_sealsImageEnvelopeWithContentUri() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = RecordingRepository()
        val collector = staticCollector()
        val observer = ScreenshotObserver.createForTest(
            contentResolver = context.contentResolver,
            repositoryProvider = { recorder },
            stateCollector = collector
        )
        val screenshotUri = Uri.parse("content://media/external/images/media/101")
        observer.hitSourceOverride = {
            ScreenshotObserver.Hit(mediaId = 101L, contentUri = screenshotUri)
        }

        observer.onChange(false, null)

        assertEquals(1, recorder.seals.size)
        val (draft, _) = recorder.seals.first()
        assertEquals(ContentType.IMAGE.name, draft.contentType)
        assertEquals(screenshotUri.toString(), draft.imageUri)
        assertNull(draft.textContent)
        assertEquals(Intent.AMBIGUOUS.name, draft.intent)
        assertEquals(IntentSource.AUTO_AMBIGUOUS.name, draft.intentSource)
    }

    @Test
    fun onChange_isIdempotentForSameMediaId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = RecordingRepository()
        val observer = ScreenshotObserver.createForTest(
            contentResolver = context.contentResolver,
            repositoryProvider = { recorder },
            stateCollector = staticCollector()
        )
        observer.hitSourceOverride = {
            ScreenshotObserver.Hit(mediaId = 42L, contentUri = Uri.parse("content://media/external/images/media/42"))
        }

        observer.onChange(false, null)
        observer.onChange(false, null)
        observer.onChange(false, null)

        assertEquals(1, recorder.seals.size)
    }

    @Test
    fun onChange_sealsTwiceForDistinctMediaIds() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val recorder = RecordingRepository()
        val observer = ScreenshotObserver.createForTest(
            contentResolver = context.contentResolver,
            repositoryProvider = { recorder },
            stateCollector = staticCollector()
        )
        var currentId = 10L
        observer.hitSourceOverride = {
            ScreenshotObserver.Hit(
                mediaId = currentId,
                contentUri = Uri.parse("content://media/external/images/media/$currentId")
            )
        }

        observer.onChange(false, null)
        currentId = 11L
        observer.onChange(false, null)

        assertEquals(2, recorder.seals.size)
    }

    private fun staticCollector(): StateSnapshotCollector = StateSnapshotCollector(
        packageResolver = { _, _ -> AppCategory.OTHER },
        activityStateSource = { ActivityState.STILL }
    )

    private class RecordingRepository : IEnvelopeRepository.Stub() {
        val seals = CopyOnWriteArrayList<Pair<IntentEnvelopeDraftParcel, StateSnapshotParcel>>()

        override fun seal(
            draft: IntentEnvelopeDraftParcel,
            state: StateSnapshotParcel
        ): String {
            seals.add(draft to state)
            return "test-envelope-${seals.size}"
        }

        // ---- Unused surface: every other method errors so mis-use is loud. ----
        override fun observeDay(isoDate: String, observer: IEnvelopeObserver) = error("unused")
        override fun stopObserving(observer: IEnvelopeObserver) = error("unused")
        override fun getEnvelope(envelopeId: String): EnvelopeViewParcel = error("unused")
        override fun reassignIntent(envelopeId: String, newIntentName: String, reasonOpt: String?) = error("unused")
        override fun archive(envelopeId: String) = error("unused")
        override fun delete(envelopeId: String) = error("unused")
        override fun undo(envelopeId: String): Boolean = error("unused")
        override fun restoreFromTrash(envelopeId: String) = error("unused")
        override fun listSoftDeletedWithinDays(days: Int): List<EnvelopeViewParcel> = error("unused")
        override fun countSoftDeletedWithinDays(days: Int): Int = error("unused")
        override fun hardDelete(envelopeId: String) = error("unused")
        override fun distinctDayLocalsWithContent(limit: Int, offset: Int): MutableList<String> = mutableListOf()
        override fun countAll(): Int = error("unused")
        override fun countArchived(): Int = error("unused")
        override fun countDeleted(): Int = error("unused")
        override fun existsPriorIntent(appCategory: String, intent: String): Boolean = error("unused")
        override fun completeUrlHydration(
            continuationId: String,
            envelopeId: String,
            canonicalUrl: String?,
            canonicalUrlHash: String?,
            ok: Boolean,
            title: String?,
            domain: String?,
            summary: String?,
            summaryModel: String?,
            failureReason: String?
        ) = error("unused")
        override fun retryHydration(envelopeId: String) = error("unused")
        override fun seedScreenshotHydrations(
            envelopeId: String,
            ocrText: String?,
            urls: Array<String>?
        ) = error("unused")

        // ---- Spec 003 v1.1 IPC additions (unused by capture-side tests). ----
        override fun lookupAppFunction(functionId: String): com.capsule.app.data.ipc.AppFunctionSummaryParcel? = null
        override fun listAppFunctions(appPackage: String): MutableList<com.capsule.app.data.ipc.AppFunctionSummaryParcel> = mutableListOf()
        override fun recordActionInvocation(
            executionId: String,
            proposalId: String,
            functionId: String,
            outcome: String,
            outcomeReason: String,
            dispatchedAtMillis: Long,
            completedAtMillis: Long,
            latencyMs: Long,
            episodeId: String?
        ) = error("unused")
        override fun markProposalConfirmed(proposalId: String): Boolean = error("unused")
        override fun markProposalDismissed(proposalId: String): Boolean = error("unused")
        override fun observeProposalsForEnvelope(
            envelopeId: String,
            observer: com.capsule.app.data.ipc.IActionProposalObserver
        ) = error("unused")
        override fun stopObservingProposals(observer: com.capsule.app.data.ipc.IActionProposalObserver) = error("unused")

        override fun observeClusters(observer: com.capsule.app.data.ipc.IClusterObserver) = error("unused")

        override fun stopObservingClusters(observer: com.capsule.app.data.ipc.IClusterObserver) = error("unused")
        override fun markClusterDismissed(clusterId: String?): Boolean = error("unused")
        override fun summarizeCluster(clusterId: String?): String = error("unused")
        override fun extractActionsForEnvelope(envelopeId: String): String = error("unused")
        override fun createDerivedTodoEnvelope(
            parentEnvelopeId: String,
            itemsJson: String,
            proposalId: String
        ): MutableList<String> = error("unused")
        override fun setTodoItemDone(envelopeId: String, itemIndex: Int, done: Boolean) = error("unused")
        override fun runWeeklyDigest(targetDayLocal: String): String = error("unused")
    }
}
