package com.capsule.app.ai

import com.capsule.app.ai.model.ActionExtractionResult
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.ai.model.DayHeaderResult
import com.capsule.app.ai.model.IntentClassification
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.ai.model.SensitivityResult
import com.capsule.app.ai.model.SummaryResult
import com.capsule.app.data.dao.ClusterMemberWithEnvelope
import com.capsule.app.data.dao.ClusterWithMembers
import com.capsule.app.data.entity.ClusterEntity
import com.capsule.app.data.entity.ClusterMemberEntity
import com.capsule.app.data.entity.IntentEnvelopeEntity
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.ClusterState
import com.capsule.app.data.model.ClusterType
import com.capsule.app.data.model.ContentType
import com.capsule.app.data.model.Intent
import com.capsule.app.data.model.IntentSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T139 — JVM coverage for [ClusterSummariser].
 *
 * Asserts the post-hoc guards the agent owns: prompt assembly for
 * varying member counts, output bounds (≤3 bullets, ≤240 chars,
 * truncation with `…`), citation requirement, modelLabel stamp, and
 * graceful degrade (provider throws / model returns SKIP / output
 * fails [PromptSanitizer.validateOutput]).
 *
 * Hostile-input corpus is in [ClusterSummariserHostileTest].
 */
class ClusterSummariserTest {

    @Test
    fun `assembles prompt with all members and stamps modelLabel`() = runTest {
        val cluster = clusterOf("c-1", listOf("env-a", "env-b", "env-c"))
        var captured: String? = null
        val provider = FakeProvider { prompt ->
            captured = prompt
            okSummary(
                """
                - Three articles on rust borrow checker [env-a].
                - Comparison with C++ ownership [env-b] [env-c].
                """.trimIndent()
            )
        }

        val result = ClusterSummariser(provider, modelLabel = "test-model").summarise(cluster)

        assertNotNull(result)
        assertEquals("test-model", result!!.model)
        assertEquals(2, result.bullets.size)
        // Prompt mentions every envelope id.
        val promptText = checkNotNull(captured)
        listOf("env-a", "env-b", "env-c").forEach { id ->
            assertTrue("prompt should reference $id", promptText.contains(id))
        }
    }

    @Test
    fun `assembles prompt for N=4 captures and respects bullet cap`() = runTest {
        val cluster = clusterOf("c-2", listOf("env-w", "env-x", "env-y", "env-z"))
        val provider = FakeProvider {
            okSummary(
                """
                - First [env-w].
                - Second [env-x].
                - Third [env-y].
                - Fourth — should be dropped [env-z].
                """.trimIndent()
            )
        }

        val result = ClusterSummariser(provider).summarise(cluster)

        assertNotNull(result)
        // ClusterSummaryPrompt.MAX_BULLETS = 3.
        assertEquals(3, result!!.bullets.size)
        assertTrue(result.bullets.last().contains("env-y"))
    }

    @Test
    fun `truncates over-long bullets to MAX_BULLET_CHARS with ellipsis`() = runTest {
        val cluster = clusterOf("c-3", listOf("env-a", "env-b", "env-c"))
        // Citation goes first so truncation does not strip it.
        val long = "x".repeat(400)
        val provider = FakeProvider {
            okSummary("- [env-a] $long")
        }

        val result = ClusterSummariser(provider).summarise(cluster)

        assertNotNull(result)
        val bullet = result!!.bullets.single()
        assertEquals(240, bullet.length)
        assertTrue(bullet.endsWith("…"))
        assertTrue(bullet.startsWith("[env-a]"))
    }

    @Test
    fun `returns null when any bullet missing a citation`() = runTest {
        val cluster = clusterOf("c-4", listOf("env-a", "env-b", "env-c"))
        val provider = FakeProvider {
            okSummary(
                """
                - Cited bullet [env-a].
                - Uncited bullet — has no env reference.
                """.trimIndent()
            )
        }

        assertNull(ClusterSummariser(provider).summarise(cluster))
    }

    @Test
    fun `returns null when citation references non-member envelope id`() = runTest {
        val cluster = clusterOf("c-5", listOf("env-a", "env-b", "env-c"))
        val provider = FakeProvider {
            okSummary("- Bullet [env-fabricated].")
        }

        // env-fabricated is not in valid set -> bullet treated as uncited.
        assertNull(ClusterSummariser(provider).summarise(cluster))
    }

    @Test
    fun `returns null on SKIP sentinel`() = runTest {
        val cluster = clusterOf("c-6", listOf("env-a", "env-b", "env-c"))
        val provider = FakeProvider { okSummary("SKIP") }
        assertNull(ClusterSummariser(provider).summarise(cluster))
    }

    @Test
    fun `returns null when provider throws NotImplementedError (v1 stub)`() = runTest {
        val cluster = clusterOf("c-7", listOf("env-a", "env-b", "env-c"))
        val provider = FakeProvider { throw NotImplementedError("AICore — TODO") }
        assertNull(ClusterSummariser(provider).summarise(cluster))
    }

    @Test
    fun `returns null when provider throws generic exception`() = runTest {
        val cluster = clusterOf("c-8", listOf("env-a", "env-b", "env-c"))
        val provider = FakeProvider { throw RuntimeException("oom") }
        assertNull(ClusterSummariser(provider).summarise(cluster))
    }

    @Test
    fun `returns null when no members hydrate (all soft-deleted)`() = runTest {
        val cluster = clusterOf(
            id = "c-9",
            envelopeIds = listOf("env-a", "env-b", "env-c"),
            allDeleted = true
        )
        val provider = FakeProvider { error("should not be called") }
        assertNull(ClusterSummariser(provider).summarise(cluster))
    }

    @Test
    fun `returns null when bullets empty (model produced prose without bullets)`() = runTest {
        val cluster = clusterOf("c-10", listOf("env-a", "env-b", "env-c"))
        val provider = FakeProvider {
            okSummary("This is some prose with no leading dash. [env-a]")
        }
        assertNull(ClusterSummariser(provider).summarise(cluster))
    }

    @Test
    fun `accepts unicode bullet markers (asterisk and bullet char)`() = runTest {
        val cluster = clusterOf("c-11", listOf("env-a", "env-b", "env-c"))
        val provider = FakeProvider {
            okSummary(
                """
                * One [env-a].
                • Two [env-b].
                """.trimIndent()
            )
        }
        val result = ClusterSummariser(provider).summarise(cluster)
        assertNotNull(result)
        assertEquals(2, result!!.bullets.size)
    }

    // ---- helpers ----------------------------------------------------

    private fun okSummary(text: String) = SummaryResult(
        text = text,
        generationLocale = "en",
        provenance = LlmProvenance.LocalNano
    )

    private fun clusterOf(
        id: String,
        envelopeIds: List<String>,
        allDeleted: Boolean = false
    ): ClusterWithMembers {
        val cluster = ClusterEntity(
            id = id,
            clusterType = ClusterType.RESEARCH_SESSION,
            state = ClusterState.SURFACED,
            timeBucketStart = 1_000L,
            timeBucketEnd = 1_000L + 4 * 60 * 60 * 1_000L,
            similarityScore = 0.8f,
            modelLabel = "test-model",
            createdAt = 1_500L,
            stateChangedAt = 1_500L
        )
        val members = envelopeIds.mapIndexed { idx, envId ->
            ClusterMemberWithEnvelope(
                member = ClusterMemberEntity(
                    clusterId = id,
                    envelopeId = envId,
                    memberIndex = idx
                ),
                envelope = envelope(envId, deleted = allDeleted)
            )
        }
        return ClusterWithMembers(cluster, members)
    }

    private fun envelope(id: String, deleted: Boolean): IntentEnvelopeEntity =
        IntentEnvelopeEntity(
            id = id,
            contentType = ContentType.TEXT,
            textContent = "Some captured text about the topic of $id, used as the cluster excerpt.",
            imageUri = null,
            textContentSha256 = null,
            intent = Intent.AMBIGUOUS,
            intentConfidence = null,
            intentSource = IntentSource.FALLBACK,
            intentHistoryJson = "[]",
            state = StateSnapshot(
                appCategory = AppCategory.OTHER,
                activityState = ActivityState.STILL,
                tzId = "UTC",
                hourLocal = 10,
                dayOfWeekLocal = 2
            ),
            createdAt = 1_000L,
            dayLocal = "2026-04-29",
            isArchived = false,
            isDeleted = deleted,
            deletedAt = if (deleted) 2_000L else null
        )

    private class FakeProvider(
        private val behaviour: (String) -> SummaryResult
    ) : LlmProvider {
        override suspend fun classifyIntent(text: String, appCategory: String): IntentClassification =
            error("unused")

        override suspend fun summarize(text: String, maxTokens: Int): SummaryResult =
            behaviour(text)

        override suspend fun scanSensitivity(text: String): SensitivityResult = error("unused")

        override suspend fun generateDayHeader(
            dayIsoDate: String,
            envelopeSummaries: List<String>
        ): DayHeaderResult = error("unused")

        override suspend fun extractActions(
            text: String,
            contentType: String,
            state: StateSnapshot,
            registeredFunctions: List<AppFunctionSummary>,
            maxCandidates: Int
        ): ActionExtractionResult = error("unused")

        override suspend fun embed(text: String): EmbeddingResult? = null
    }
}
