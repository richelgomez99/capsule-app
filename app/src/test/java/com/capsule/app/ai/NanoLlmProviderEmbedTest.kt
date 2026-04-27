package com.capsule.app.ai

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Block-2 follow-up regression: locks in the **intentional asymmetry** in
 * [NanoLlmProvider.embed] vs [NanoLlmProvider.summarize] /
 * [NanoLlmProvider.extractActions].
 *
 * `summarize` and `extractActions` THROW [NanoUnavailableException] when
 * [LlmProviderDiagnostics.forceNanoUnavailable] is set. `embed` deliberately
 * does NOT throw — it returns `null` instead. See the kdoc on
 * [LlmProvider.embed] for the full rationale (cluster worker batches N
 * embeds back-to-back; per-call exceptions are too expensive and risk
 * fast-failing on first envelope).
 *
 * If a future refactor "unifies" the diagnostic seam to throw consistently,
 * this test will fail loudly so the deviation is debated explicitly rather
 * than silently undone.
 */
class NanoLlmProviderEmbedTest {

    @After
    fun tearDown() {
        LlmProviderDiagnostics.forceNanoUnavailable = false
    }

    @Test
    fun `embed returns null when forceNanoUnavailable is set`() = runTest {
        val provider = NanoLlmProvider()
        LlmProviderDiagnostics.forceNanoUnavailable = true

        val result = provider.embed("any non-blank text the cluster worker might send")

        assertNull(
            "embed() must NOT throw on forceNanoUnavailable; intentional asymmetry vs summarize/extractActions",
            result
        )
    }

    @Test
    fun `embed returns null on blank input regardless of flag`() = runTest {
        val provider = NanoLlmProvider()
        // flag off
        assertNull(provider.embed(""))
        assertNull(provider.embed("   "))
        // flag on
        LlmProviderDiagnostics.forceNanoUnavailable = true
        assertNull(provider.embed(""))
        assertNull(provider.embed("   "))
    }

    @Test
    fun `MODEL_LABEL is the v4 build constant the worker stamps on clusters`() {
        // FR-038/FR-039: cluster_member.model_label and cluster.model_label
        // are stamped from this single source. Bumping it elsewhere would
        // silently de-couple the embedder from the cluster row.
        assertEquals("nano-v4-build-2026-05-01", NanoLlmProvider.MODEL_LABEL)
    }
}
