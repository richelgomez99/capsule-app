package com.capsule.app.overlay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class OverlayDuplicateFeedbackTest {

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun alreadySavedSeal_showsDuplicateFeedbackForExistingEnvelope() = runTest {
        val vm = OverlayViewModel()
        vm.sealOrchestrator = FakeSealOrchestrator(
            captureOutcome = SealOutcome.AlreadySaved("existing-1", "CANONICAL_URL")
        )

        vm.onClipboardReadResult(content())
        vm.onSaveCapture()
        advanceUntilIdle()

        val ui = vm.postCaptureUi.value
        assertTrue(ui is PostCaptureUi.AlreadySaved)
        ui as PostCaptureUi.AlreadySaved
        assertEquals("existing-1", ui.existingEnvelopeId)
        assertEquals("CANONICAL_URL", ui.matchedBy)
    }

    @Test
    fun alreadySavedOpen_invokesExistingEnvelopeCallbackAndDismisses() = runTest {
        val vm = OverlayViewModel()
        var opened: String? = null
        vm.onOpenExistingEnvelope = { opened = it }

        vm.onAlreadySavedOpen("existing-2")

        assertEquals("existing-2", opened)
        assertTrue(vm.postCaptureUi.value is PostCaptureUi.None)
    }

    @Test
    fun duplicateReclassify_updatesExistingEnvelopeWithoutNewSeal() = runTest {
        val fake = FakeSealOrchestrator(
            captureOutcome = SealOutcome.AlreadySaved("existing-3", "EXACT_TEXT")
        )
        val vm = OverlayViewModel()
        vm.sealOrchestrator = fake

        vm.onAlreadySavedReclassify("existing-3")
        assertTrue(vm.postCaptureUi.value is PostCaptureUi.ReclassifyChipRow)

        vm.onDuplicateReclassifyChipTapped("existing-3", Intent.REFERENCE)
        advanceUntilIdle()

        assertEquals("existing-3" to Intent.REFERENCE, fake.reclassified.single())
        assertTrue(vm.postCaptureUi.value is PostCaptureUi.AlreadySaved)
    }

    private fun content(): CapturedContent = CapturedContent(
        text = "https://example.com/already",
        sourcePackage = "com.example",
        timestamp = 1_700_000_000_000L,
        isSensitive = false
    )

    private class FakeSealOrchestrator(
        private val captureOutcome: SealOutcome
    ) : SealOrchestrator {
        val reclassified = mutableListOf<Pair<String, Intent>>()

        override suspend fun captureAndSeal(content: CapturedContent): SealOutcome = captureOutcome

        override suspend fun sealWithChoice(
            content: CapturedContent,
            intent: Intent,
            source: IntentSource
        ): SealOutcome = captureOutcome

        override suspend fun undo(envelopeId: String): UndoOutcome = UndoOutcome.Removed

        override suspend fun reclassifyExisting(envelopeId: String, intent: Intent): Boolean {
            reclassified += envelopeId to intent
            return true
        }
    }
}
