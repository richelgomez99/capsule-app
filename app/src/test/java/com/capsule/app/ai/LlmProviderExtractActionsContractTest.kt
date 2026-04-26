package com.capsule.app.ai

import com.capsule.app.ai.model.ActionExtractionResult
import com.capsule.app.ai.model.AppFunctionSummary
import com.capsule.app.ai.model.LlmProvenance
import com.capsule.app.data.entity.StateSnapshot
import com.capsule.app.data.model.ActivityState
import com.capsule.app.data.model.AppCategory
import com.capsule.app.data.model.SensitivityScope
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T033 — abstract contract every [LlmProvider] implementation MUST extend.
 *
 * Subclasses provide the concrete provider via [provider]. The provider may be
 * a real implementation (eg. NanoLlmProvider with mocked AICore), a fake, or a
 * deterministic seeded mock. Any candidate the provider returns flows through
 * the same invariants:
 *
 *   1. Bounded result size:    `candidates.size ≤ maxCandidates`
 *   2. JSON validity:          every `argsJson` parses as a JSON object
 *   3. Size bound:              every `argsJson` ≤ 4096 bytes (action-extraction-contract.md §3)
 *   4. Function-id whitelist:  every `functionId` ∈ `registeredFunctions`
 *   5. Schema-version pinning: `schemaVersion` matches the registered entry
 *   6. Confidence range:       `0.0 ≤ confidence ≤ 1.0`
 *   7. Sort order:              candidates emitted in confidence-descending order
 *   8. Deterministic provenance: result carries non-null [LlmProvenance]
 *   9. Sensitivity coherence:   candidate's `sensitivityScope` matches the
 *                               registered function's scope (model can't
 *                               smuggle a CALENDAR_WRITE arg via a PERSONAL skill)
 *
 * `extractActions` MUST return a non-null result (empty candidate list is OK)
 * and MUST NOT throw for benign inputs. Implementations that need network
 * (none in v1.1) or that are AICore-dependent should throw cleanly only on
 * Nano.UNAVAILABLE — that's the orchestrator's signal to audit `nano_unavailable`
 * and bail.
 *
 * action-extraction-contract.md §3.
 */
abstract class LlmProviderExtractActionsContractTest {

    /** SUT — fresh instance per test. */
    protected abstract fun provider(): LlmProvider

    @Test
    fun extractActions_emptyText_returnsEmptyCandidates() = runTest {
        val out = provider().extractActions(
            text = "",
            contentType = "TEXT",
            state = SAMPLE_STATE,
            registeredFunctions = SAMPLE_FUNCTIONS,
            maxCandidates = 3
        )
        assertEqualsContractInvariants(out, registered = SAMPLE_FUNCTIONS, maxCandidates = 3)
        assertTrue("empty text should not produce action candidates", out.candidates.isEmpty())
    }

    @Test
    fun extractActions_purelyInformationalText_returnsEmpty() = runTest {
        val out = provider().extractActions(
            text = "The chef recommends a medium-rare ribeye with chimichurri.",
            contentType = "TEXT",
            state = SAMPLE_STATE,
            registeredFunctions = SAMPLE_FUNCTIONS,
            maxCandidates = 3
        )
        assertEqualsContractInvariants(out, registered = SAMPLE_FUNCTIONS, maxCandidates = 3)
        // Implementations may choose to surface 0 candidates here. The hard
        // contract is the invariants (no invented functionId, no invalid JSON).
    }

    @Test
    fun extractActions_eventLikeText_invariantsHold() = runTest {
        val out = provider().extractActions(
            text = "Lunch with Maya next Tuesday at noon at La Esquina",
            contentType = "TEXT",
            state = SAMPLE_STATE,
            registeredFunctions = SAMPLE_FUNCTIONS,
            maxCandidates = 3
        )
        assertEqualsContractInvariants(out, registered = SAMPLE_FUNCTIONS, maxCandidates = 3)
    }

    @Test
    fun extractActions_emptyRegistry_returnsEmpty() = runTest {
        val out = provider().extractActions(
            text = "Lunch with Maya next Tuesday at noon",
            contentType = "TEXT",
            state = SAMPLE_STATE,
            registeredFunctions = emptyList(),
            maxCandidates = 3
        )
        assertNotNull("result must never be null", out)
        assertTrue(
            "no registered functions => no candidates can possibly be valid",
            out.candidates.isEmpty()
        )
    }

    @Test
    fun extractActions_maxCandidatesIsRespected() = runTest {
        val out = provider().extractActions(
            text = "Email Sara, call Mom, finish slide deck, RSVP to the party",
            contentType = "TEXT",
            state = SAMPLE_STATE,
            registeredFunctions = SAMPLE_FUNCTIONS,
            maxCandidates = 1
        )
        assertEqualsContractInvariants(out, registered = SAMPLE_FUNCTIONS, maxCandidates = 1)
    }

    // ---- shared invariant assertion ----

    private fun assertEqualsContractInvariants(
        out: ActionExtractionResult,
        registered: List<AppFunctionSummary>,
        maxCandidates: Int
    ) {
        assertNotNull("result must never be null", out)
        assertNotNull("provenance must never be null (Principle IX)", out.provenance)
        assertTrue(
            "candidate count ${out.candidates.size} exceeds maxCandidates $maxCandidates",
            out.candidates.size <= maxCandidates
        )

        val registeredById = registered.associateBy { it.functionId }
        var prevConfidence = Float.POSITIVE_INFINITY
        for (c in out.candidates) {
            // 4 — function id whitelist
            val regEntry = registeredById[c.functionId]
            assertNotNull(
                "candidate functionId '${c.functionId}' is not in the registered set",
                regEntry
            )
            // 5 — schema-version pinning
            assertEquals(
                "candidate schemaVersion does not match registered version",
                regEntry!!.schemaVersion,
                c.schemaVersion
            )
            // 9 — sensitivity coherence
            assertEquals(
                "candidate sensitivityScope does not match registered function scope",
                regEntry.sensitivityScope,
                c.sensitivityScope
            )
            // 6 — confidence range
            assertTrue(
                "confidence ${c.confidence} out of [0,1]",
                c.confidence in 0.0f..1.0f
            )
            // 7 — sort order (descending)
            assertTrue(
                "candidates not sorted by confidence descending: prev=$prevConfidence, cur=${c.confidence}",
                c.confidence <= prevConfidence
            )
            prevConfidence = c.confidence
            // 3 — argsJson size bound
            assertTrue(
                "argsJson exceeds 4096-byte cap",
                c.argsJson.toByteArray(Charsets.UTF_8).size <= 4096
            )
            // 2 — JSON validity (parse must succeed and yield an object)
            try {
                JSONObject(c.argsJson)
            } catch (t: Throwable) {
                throw AssertionError(
                    "candidate ${c.functionId} produced invalid argsJson: ${t.message}",
                    t
                )
            }
        }
    }

    private companion object {
        private val SAMPLE_STATE = StateSnapshot(
            appCategory = AppCategory.OTHER,
            activityState = ActivityState.STILL,
            tzId = "America/New_York",
            hourLocal = 12,
            dayOfWeekLocal = 5
        )

        private val SAMPLE_FUNCTIONS = listOf(
            AppFunctionSummary(
                functionId = "calendar.createEvent",
                schemaVersion = 1,
                displayName = "Add to Calendar",
                description = "Create a calendar event",
                argsSchemaJson = """{"type":"object","required":["title","startEpochMillis"],"properties":{"title":{"type":"string"},"startEpochMillis":{"type":"integer"}},"additionalProperties":false}""",
                sensitivityScope = SensitivityScope.PERSONAL
            ),
            AppFunctionSummary(
                functionId = "tasks.createTodo",
                schemaVersion = 1,
                displayName = "Add to-do",
                description = "Add a to-do",
                argsSchemaJson = """{"type":"object","required":["title"],"properties":{"title":{"type":"string"}},"additionalProperties":false}""",
                sensitivityScope = SensitivityScope.PERSONAL
            )
        )
    }
}

/**
 * Concrete subclass that proves the abstract harness against [NanoLlmProvider].
 *
 * In v1.1 NanoLlmProvider's `extractActions` is a stub returning an empty
 * candidate list — this is a valid contract outcome (model decided no actions
 * were present). The full positive-path proof lands once the AICore
 * integration ships in T021.
 */
class NanoLlmProviderExtractActionsContractTest : LlmProviderExtractActionsContractTest() {
    override fun provider(): LlmProvider = NanoLlmProvider()
}
