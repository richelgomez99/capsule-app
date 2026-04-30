package com.capsule.app.cluster

import com.capsule.app.cluster.ClusterStateMachine.Trigger
import com.capsule.app.data.model.ClusterState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * T154 — exhaustive transition table for [ClusterStateMachine].
 *
 * Test plan:
 *   1. Every valid transition resolves to the documented next state.
 *   2. Every invalid (state, trigger) pair returns null.
 *   3. FAILED → ACTING retries are allowed up to MAX_FAILED_ACTING_RETRIES;
 *      callers (not the machine) enforce the cap.
 *   4. AGE_OUT is only allowed from SURFACED (the spec is explicit:
 *      a TAPPED cluster has been engaged with, so the 7-day idle timer
 *      no longer applies).
 *   5. Terminal states (ACTED, DISMISSED, AGED_OUT) accept no triggers.
 */
class ClusterStateMachineTest {

    // ---- 1. Valid transitions ------------------------------------------------

    @Test
    fun forming_surface_goesTo_surfaced() {
        assertEquals(
            ClusterState.SURFACED,
            ClusterStateMachine.next(ClusterState.FORMING, Trigger.SURFACE)
        )
    }

    @Test
    fun surfaced_tap_goesTo_tapped() {
        assertEquals(
            ClusterState.TAPPED,
            ClusterStateMachine.next(ClusterState.SURFACED, Trigger.TAP)
        )
    }

    @Test
    fun tapped_startActing_goesTo_acting() {
        assertEquals(
            ClusterState.ACTING,
            ClusterStateMachine.next(ClusterState.TAPPED, Trigger.START_ACTING)
        )
    }

    @Test
    fun acting_actSuccess_goesTo_acted() {
        assertEquals(
            ClusterState.ACTED,
            ClusterStateMachine.next(ClusterState.ACTING, Trigger.ACT_SUCCESS)
        )
    }

    @Test
    fun acting_actFail_goesTo_failed() {
        assertEquals(
            ClusterState.FAILED,
            ClusterStateMachine.next(ClusterState.ACTING, Trigger.ACT_FAIL)
        )
    }

    @Test
    fun failed_startActing_retriesInto_acting() {
        assertEquals(
            ClusterState.ACTING,
            ClusterStateMachine.next(ClusterState.FAILED, Trigger.START_ACTING)
        )
    }

    @Test
    fun surfaced_orphan_goesTo_dismissed() {
        assertEquals(
            ClusterState.DISMISSED,
            ClusterStateMachine.next(ClusterState.SURFACED, Trigger.ORPHAN)
        )
    }

    @Test
    fun tapped_orphan_goesTo_dismissed() {
        assertEquals(
            ClusterState.DISMISSED,
            ClusterStateMachine.next(ClusterState.TAPPED, Trigger.ORPHAN)
        )
    }

    @Test
    fun surfaced_ageOut_goesTo_agedOut() {
        assertEquals(
            ClusterState.AGED_OUT,
            ClusterStateMachine.next(ClusterState.SURFACED, Trigger.AGE_OUT)
        )
    }

    // ---- 2. Invalid transitions return null ---------------------------------

    @Test
    fun forming_anyNonSurfaceTrigger_returnsNull() {
        Trigger.values().filter { it != Trigger.SURFACE }.forEach { t ->
            assertNull(
                "FORMING should not accept $t",
                ClusterStateMachine.next(ClusterState.FORMING, t)
            )
        }
    }

    @Test
    fun surfaced_invalidTriggers_returnNull() {
        // Valid triggers from SURFACED: TAP, ORPHAN, AGE_OUT.
        val invalid = setOf(Trigger.SURFACE, Trigger.START_ACTING, Trigger.ACT_SUCCESS, Trigger.ACT_FAIL)
        invalid.forEach { t ->
            assertNull(
                "SURFACED should not accept $t",
                ClusterStateMachine.next(ClusterState.SURFACED, t)
            )
        }
    }

    @Test
    fun tapped_ageOut_returnsNull() {
        // Only SURFACED ages out — TAPPED has been engaged with.
        assertNull(ClusterStateMachine.next(ClusterState.TAPPED, Trigger.AGE_OUT))
    }

    @Test
    fun acting_ageOutOrTap_returnNull() {
        assertNull(ClusterStateMachine.next(ClusterState.ACTING, Trigger.AGE_OUT))
        assertNull(ClusterStateMachine.next(ClusterState.ACTING, Trigger.TAP))
        assertNull(ClusterStateMachine.next(ClusterState.ACTING, Trigger.SURFACE))
        assertNull(ClusterStateMachine.next(ClusterState.ACTING, Trigger.ORPHAN))
    }

    @Test
    fun failed_invalidTriggers_returnNull() {
        // Only START_ACTING (retry) is valid from FAILED.
        Trigger.values().filter { it != Trigger.START_ACTING }.forEach { t ->
            assertNull(
                "FAILED should not accept $t",
                ClusterStateMachine.next(ClusterState.FAILED, t)
            )
        }
    }

    @Test
    fun terminalStates_acceptNoTriggers() {
        val terminal = listOf(
            ClusterState.ACTED,
            ClusterState.DISMISSED,
            ClusterState.AGED_OUT,
        )
        for (s in terminal) {
            for (t in Trigger.values()) {
                assertNull(
                    "$s (terminal) should not accept $t",
                    ClusterStateMachine.next(s, t)
                )
            }
            assertTrue("$s should report isTerminal=true", ClusterStateMachine.isTerminal(s))
        }
    }

    // ---- 3. Retry budget bookkeeping ----------------------------------------

    @Test
    fun failed_acting_loop_canRunUpTo_maxRetries() {
        // The machine itself does not count attempts; assert that the
        // documented retry cap is exposed and that running START_ACTING
        // 3 times in a row from FAILED keeps yielding ACTING.
        assertEquals(3, ClusterStateMachine.MAX_FAILED_ACTING_RETRIES)

        var state: ClusterState = ClusterState.FAILED
        repeat(ClusterStateMachine.MAX_FAILED_ACTING_RETRIES) {
            val next = ClusterStateMachine.next(state, Trigger.START_ACTING)
            assertEquals(ClusterState.ACTING, next)
            // Simulate a re-fail to set up the next retry.
            state = ClusterStateMachine.next(next!!, Trigger.ACT_FAIL)!!
            assertEquals(ClusterState.FAILED, state)
        }
    }

    // ---- 4. isTerminal sanity -----------------------------------------------

    @Test
    fun isTerminal_falseFor_nonTerminalStates() {
        val nonTerminal = listOf(
            ClusterState.FORMING,
            ClusterState.SURFACED,
            ClusterState.TAPPED,
            ClusterState.ACTING,
            ClusterState.FAILED,
        )
        nonTerminal.forEach { s ->
            assertEquals("$s should report isTerminal=false", false, ClusterStateMachine.isTerminal(s))
        }
    }
}
