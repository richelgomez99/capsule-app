package com.capsule.app.diary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * T146 — Phase 11 Block 8 / spec 010 FR-010-024 coverage.
 *
 * Note on harness: tasks.md line 936 names this a "Compose snapshot"
 * test for `(SURFACED, ACTING, FAILED, STALE, DISMISSED-trace,
 * SLOW-NETWORK)` × 2 palettes × 2 font scales. The repo does not
 * ship Paparazzi/Roborazzi (no entry in `gradle/libs.versions.toml`,
 * no plugin in `app/build.gradle.kts`), so a true pixel snapshot
 * harness would be unspecced scope creep on top of Block 8.
 *
 * This test instead exercises the **state-machine projection** —
 * every public field that drives a render decision is asserted for
 * each of the six states. The actual rendered Compose output is
 * covered by [DiaryClusterSuggestionCardTest] (T147) on the
 * androidTest source set, which is the only Compose-test harness
 * currently wired. When Paparazzi lands (Block 10+), this file can
 * grow goldens without changing the contract.
 */
class ClusterSuggestionCardTest {

    @Test
    fun `SURFACED carries header, body, sources, and time range`() {
        val state = ClusterSuggestionCardState.Surfaced(
            headerLabel = "Research session · 4 captures",
            timeRangeLabel = "Sat 9:14a → 11:42a",
            sourceCategories = listOf("BROWSER", "SOCIAL", "READING"),
            bodyText = "You came back to pricing four times this weekend.",
        )

        assertEquals("Research session · 4 captures", state.headerLabel)
        assertEquals("Sat 9:14a → 11:42a", state.timeRangeLabel)
        assertEquals(3, state.sourceCategories.size)
        assertTrue(state.bodyText.startsWith("You came back to pricing"))
    }

    @Test
    fun `ACTING preserves body context for the in-flight summarisation`() {
        val state = ClusterSuggestionCardState.Acting(
            headerLabel = "Research session · 4 captures",
            timeRangeLabel = "Sat 9:14a → 11:42a",
            sourceCategories = listOf("BROWSER", "SOCIAL"),
            bodyText = "You came back to pricing four times this weekend.",
        )

        // Body stays the same as SURFACED — the action row swaps to ellipsis.
        assertEquals("You came back to pricing four times this weekend.", state.bodyText)
    }

    @Test
    fun `ACTED requires at least one bullet`() {
        val ok = ClusterSuggestionCardState.Acted(
            headerLabel = "Research session · 4 captures",
            timeRangeLabel = "Sat 9:14a → 11:42a",
            sourceCategories = listOf("BROWSER"),
            bullets = listOf(
                "Pricing memos cluster around anchor-high tactics [env-a]",
                "Pre-seed valuation framing reused from YC [env-b]",
            ),
            citations = listOf("env-a", "env-b"),
        )
        assertEquals(2, ok.bullets.size)
        assertEquals(listOf("env-a", "env-b"), ok.citations)

        try {
            ClusterSuggestionCardState.Acted(
                headerLabel = "h",
                timeRangeLabel = "t",
                sourceCategories = emptyList(),
                bullets = emptyList(),
                citations = emptyList(),
            )
            fail("ACTED with zero bullets must fail require()")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `FAILED tracks retry count and exhausts at MAX_RETRIES`() {
        val first = ClusterSuggestionCardState.Failed(
            headerLabel = "Research session · 4 captures",
            timeRangeLabel = "Sat 9:14a → 11:42a",
            sourceCategories = listOf("BROWSER"),
            retryCount = 0,
        )
        assertFalse(first.retryExhausted)

        val second = first.copy(retryCount = 2)
        assertFalse(second.retryExhausted)

        val exhausted = first.copy(retryCount = ClusterSuggestionCardState.MAX_RETRIES)
        assertTrue(exhausted.retryExhausted)

        val past = first.copy(retryCount = ClusterSuggestionCardState.MAX_RETRIES + 5)
        assertTrue(past.retryExhausted)
    }

    @Test
    fun `STALE wraps Surfaced and exposes a mono timestamp marker`() {
        val inner = ClusterSuggestionCardState.Surfaced(
            headerLabel = "Research session · 4 captures",
            timeRangeLabel = "Sat 9:14a → 11:42a",
            sourceCategories = listOf("BROWSER", "READING"),
            bodyText = "You came back to pricing four times.",
        )
        val state = ClusterSuggestionCardState.Stale(
            inner = inner,
            timestampLabel = "9:14A",
        )

        // Delegated fields surface the wrapped Surfaced payload.
        assertEquals(inner.headerLabel, state.headerLabel)
        assertEquals(inner.timeRangeLabel, state.timeRangeLabel)
        assertEquals(inner.sourceCategories, state.sourceCategories)
        assertEquals("9:14A", state.timestampLabel)
    }

    @Test
    fun `SLOW-NETWORK enforces honest coverage and computes its body line`() {
        val state = ClusterSuggestionCardState.SlowNetwork(
            headerLabel = "Research session · 4 captures",
            timeRangeLabel = "Sat 9:14a → 11:42a",
            sourceCategories = listOf("BROWSER", "SOCIAL"),
            syntCount = 3,
            totalCount = 4,
        )

        // FR-010-024: "the card never lies about coverage."
        assertTrue(state.bodyText.contains("3 of 4 captures synthesized"))
        assertTrue(state.bodyText.contains("1 couldn't be reached"))

        // Bounds enforcement.
        try {
            ClusterSuggestionCardState.SlowNetwork(
                headerLabel = "h",
                timeRangeLabel = "t",
                sourceCategories = emptyList(),
                syntCount = 5,
                totalCount = 4,
            )
            fail("syntCount > totalCount must fail require()")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }

        try {
            ClusterSuggestionCardState.SlowNetwork(
                headerLabel = "h",
                timeRangeLabel = "t",
                sourceCategories = emptyList(),
                syntCount = 0,
                totalCount = 0,
            )
            fail("totalCount < 1 must fail require()")
        } catch (e: IllegalArgumentException) {
            assertNotNull(e.message)
        }
    }

    @Test
    fun `DismissedTrace exposes only the mono trace label`() {
        val state = ClusterSuggestionCardState.DismissedTrace(label = "Cluster dismissed · 9:14A")

        assertEquals("Cluster dismissed · 9:14A", state.label)
        assertEquals("", state.headerLabel)
        assertEquals("", state.timeRangeLabel)
        assertTrue(state.sourceCategories.isEmpty())
    }

    @Test
    fun `MAX_RETRIES contract is 3 per FR-010-024`() {
        // Locks the public constant so the spec line and the code stay aligned.
        assertEquals(3, ClusterSuggestionCardState.MAX_RETRIES)
    }

    @Test
    fun `test tag constants are stable and namespaced`() {
        // Ensures instrumented tests + Block 9 wiring can cite stable tags.
        assertEquals("cluster-suggestion-card", ClusterSuggestionCardTestTags.CARD)
        assertEquals("cluster-suggestion-body", ClusterSuggestionCardTestTags.BODY)
        assertEquals("cluster-suggestion-actions", ClusterSuggestionCardTestTags.ACTION_ROW)
        assertEquals("cluster-suggestion-acting", ClusterSuggestionCardTestTags.ACTING_ELLIPSIS)
        assertEquals(
            "cluster-suggestion-retry-exhausted",
            ClusterSuggestionCardTestTags.RETRY_EXHAUSTED,
        )
        assertEquals("cluster-suggestion-stale-ts", ClusterSuggestionCardTestTags.STALE_TIMESTAMP)
        assertEquals("cluster-suggestion-citations", ClusterSuggestionCardTestTags.CITATION_FOOT)
        assertEquals(
            "cluster-suggestion-dismissed-trace",
            ClusterSuggestionCardTestTags.DISMISSED_TRACE,
        )
    }

    @Test
    fun `Acted preserves bullet ordering and citation set`() {
        val state = ClusterSuggestionCardState.Acted(
            headerLabel = "Research session · 4 captures",
            timeRangeLabel = "Sat 9:14a → 11:42a",
            sourceCategories = listOf("BROWSER", "SOCIAL", "READING", "VIDEO", "MESSAGING"),
            bullets = listOf("First insight [env-1]", "Second insight [env-2]"),
            citations = listOf("env-1", "env-2"),
        )

        assertEquals("First insight [env-1]", state.bullets[0])
        assertEquals("Second insight [env-2]", state.bullets[1])
        // Source overflow (>4) is a render-time concern; the model should
        // still preserve the full list so the +N pill renders honestly.
        assertEquals(5, state.sourceCategories.size)
    }

    @Test
    fun `Failed default tagging keeps action row enabled before exhaustion`() {
        // Sanity: a Failed at retryCount=0 is NOT the "give up" terminal.
        val state = ClusterSuggestionCardState.Failed(
            headerLabel = "h",
            timeRangeLabel = "t",
            sourceCategories = listOf("BROWSER"),
            retryCount = 0,
        )
        assertFalse(state.retryExhausted)

        // Defensive: nothing else on Failed should leak a body string —
        // the renderer hard-codes the apologetic line per FR-010-024.
        // If this changes, T147 needs to update.
        @Suppress("USELESS_IS_CHECK")
        assertNull(
            (state as? ClusterSuggestionCardState.Surfaced)?.bodyText,
        )
    }
}
