package com.capsule.app.continuation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T067 — pure-JVM coverage for [ContinuationEngine].
 *
 * The WorkManager side-effects (enqueue constraints, backoff policy,
 * tag set) are validated in the instrumented
 * [com.capsule.app.continuation.UrlHydrateWorkerTest]; this file covers
 * the pure URL extraction logic and engine constants.
 */
class ContinuationEngineTest {

    @Test
    fun `extractUrls returns empty list on blank input`() {
        assertEquals(emptyList<String>(), ContinuationEngine.extractUrls(""))
        assertEquals(emptyList<String>(), ContinuationEngine.extractUrls("   \n\t  "))
        assertEquals(emptyList<String>(), ContinuationEngine.extractUrls("hello world, no URLs here."))
    }

    @Test
    fun `extractUrls finds a single https URL`() {
        val result = ContinuationEngine.extractUrls("see https://example.com/a for details")
        assertEquals(listOf("https://example.com/a"), result)
    }

    @Test
    fun `extractUrls strips trailing sentence punctuation`() {
        val input = "link was https://example.com/a. Next: https://example.com/b, and (https://example.com/c)."
        val expected = listOf(
            "https://example.com/a",
            "https://example.com/b",
            "https://example.com/c"
        )
        assertEquals(expected, ContinuationEngine.extractUrls(input))
    }

    @Test
    fun `extractUrls preserves query params and fragments`() {
        val input = "https://example.com/path?utm_source=twitter&q=hello#frag"
        val expected = listOf("https://example.com/path?utm_source=twitter&q=hello#frag")
        assertEquals(expected, ContinuationEngine.extractUrls(input))
    }

    @Test
    fun `extractUrls accepts http in addition to https`() {
        // The URL_HYDRATE gateway will reject http via UrlValidator (T061);
        // the engine itself is inclusive so audit trails capture the attempt.
        val result = ContinuationEngine.extractUrls("go to http://example.com now")
        assertEquals(listOf("http://example.com"), result)
    }

    @Test
    fun `extractUrls deduplicates while preserving encounter order`() {
        val input = "https://a.com/x and then https://b.com/y, also https://a.com/x again"
        assertEquals(
            listOf("https://a.com/x", "https://b.com/y"),
            ContinuationEngine.extractUrls(input)
        )
    }

    @Test
    fun `extractUrls handles three distinct URLs for per-URL hydration contract`() {
        // contracts/continuation-engine-contract.md §4.1:
        //   "If multiple URLs are present, one URL_HYDRATE continuation
        //    is enqueued per URL".
        val input = "a https://one.example b https://two.example c https://three.example"
        val result = ContinuationEngine.extractUrls(input)
        assertEquals(3, result.size)
        assertEquals(
            listOf("https://one.example", "https://two.example", "https://three.example"),
            result
        )
    }

    @Test
    fun `backoff base and max attempts match the contract defaults`() {
        // contracts/continuation-engine-contract.md §2.
        assertEquals(60L, ContinuationEngine.BACKOFF_BASE_SECONDS)
        assertEquals(3, ContinuationEngine.MAX_ATTEMPTS)
    }

    @Test
    fun `default constraints require a connected network`() {
        // v1 dev: charger + unmetered relaxed so hydration fires off the
        // bench. Spec §2 still documents charger + unmetered + battery-
        // not-low for GA — re-tighten this test when the production
        // constraints are restored.
        val c = ContinuationEngine.DEFAULT_CONSTRAINTS
        assertEquals(androidx.work.NetworkType.CONNECTED, c.requiredNetworkType)
    }

    @Test
    fun `tag helpers are stable`() {
        assertEquals("envelope:abc", ContinuationEngine.tagForEnvelope("abc"))
        assertEquals("continuation:xyz", ContinuationEngine.tagForContinuation("xyz"))
    }
}
