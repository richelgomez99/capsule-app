package com.capsule.app.ai

import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * T097 (spec/003) — diagnostic seam unit tests.
 *
 * Verifies that flipping [LlmProviderDiagnostics.forceNanoUnavailable] makes
 * [NanoLlmProvider.extractActions] and [NanoLlmProvider.summarize] throw
 * [NanoUnavailableException] *before* doing any work, and that the default is
 * the no-op happy path.
 */
class LlmProviderDiagnosticsTest {

    private val state = StateSnapshot(
        appCategory = AppCategory.OTHER,
        activityState = ActivityState.STILL,
        tzId = "UTC",
        hourLocal = 12,
        dayOfWeekLocal = 3
    )

    @After
    fun tearDown() {
        // Reset the global flag so tests don't bleed into each other.
        LlmProviderDiagnostics.forceNanoUnavailable = false
    }

    @Test
    fun default_state_is_nano_available() {
        assertFalse(LlmProviderDiagnostics.forceNanoUnavailable)
    }

    @Test
    fun extractActions_returns_empty_when_flag_off() = runTest {
        val provider = NanoLlmProvider()
        val result = provider.extractActions(
            text = "buy milk",
            contentType = "text/plain",
            state = state,
            registeredFunctions = emptyList(),
            maxCandidates = 3
        )
        assertEquals(0, result.candidates.size)
    }

    @Test
    fun extractActions_throws_when_flag_on() = runTest {
        LlmProviderDiagnostics.forceNanoUnavailable = true
        val provider = NanoLlmProvider()
        try {
            provider.extractActions(
                text = "buy milk",
                contentType = "text/plain",
                state = state,
                registeredFunctions = emptyList(),
                maxCandidates = 3
            )
            fail("Expected NanoUnavailableException")
        } catch (e: NanoUnavailableException) {
            assertNotNull(e.message)
            assertTrue(
                "exception message must mention the diagnostic seam: ${e.message}",
                (e.message ?: "").contains("LlmProviderDiagnostics", ignoreCase = true)
            )
        }
    }

    @Test
    fun summarize_throws_when_flag_on() = runTest {
        LlmProviderDiagnostics.forceNanoUnavailable = true
        val provider = NanoLlmProvider()
        try {
            provider.summarize("hello world", maxTokens = 50)
            fail("Expected NanoUnavailableException")
        } catch (e: NanoUnavailableException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun toggling_flag_off_restores_happy_path() = runTest {
        LlmProviderDiagnostics.forceNanoUnavailable = true
        val provider = NanoLlmProvider()
        // First call throws.
        try {
            provider.extractActions(
                text = "x",
                contentType = "text/plain",
                state = state,
                registeredFunctions = emptyList(),
                maxCandidates = 1
            )
            fail("Expected throw while flag=true")
        } catch (_: NanoUnavailableException) { /* expected */ }

        // Flip back; happy path returns empty list again.
        LlmProviderDiagnostics.forceNanoUnavailable = false
        val result = provider.extractActions(
            text = "x",
            contentType = "text/plain",
            state = state,
            registeredFunctions = emptyList(),
            maxCandidates = 1
        )
        assertEquals(0, result.candidates.size)
    }
}
