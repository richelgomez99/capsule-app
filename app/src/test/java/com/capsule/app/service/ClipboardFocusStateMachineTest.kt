package com.capsule.app.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * JVM unit tests for the ClipboardFocusState contract.
 *
 * Full behavioral verification (timeout, flag manipulation, clipboard read race
 * conditions) requires a real Android WindowManager + Handler + Looper and is
 * covered by the manual Phase 1 Verification Checklist on a physical device
 * (see [specs/001-core-capture-overlay/quickstart.md]).
 *
 * These JVM tests assert the invariants that MUST hold at the type level so
 * that refactors cannot silently break the spec FR-012 / SC-007 contract.
 */
class ClipboardFocusStateMachineTest {

    @Test
    fun `initial state is IDLE`() {
        assertEquals(ClipboardFocusState.IDLE, ClipboardFocusState.entries.first())
    }

    @Test
    fun `state enum has exactly 4 states per FR-012`() {
        assertEquals(4, ClipboardFocusState.entries.size)
    }

    @Test
    fun `state transition order matches spec IDLE-REQUESTING-READING-RESTORING`() {
        val states = ClipboardFocusState.entries
        assertEquals(ClipboardFocusState.IDLE, states[0])
        assertEquals(ClipboardFocusState.REQUESTING_FOCUS, states[1])
        assertEquals(ClipboardFocusState.READING_CLIPBOARD, states[2])
        assertEquals(ClipboardFocusState.RESTORING_FLAGS, states[3])
    }

    @Test
    fun `all four states are distinct`() {
        val set = ClipboardFocusState.entries.toSet()
        assertEquals(4, set.size)
    }

    @Test
    fun `enum names match contract exactly for log-based debugging`() {
        // CapsuleOverlayService + Logcat filters rely on these exact names.
        // Renaming without updating quickstart.md Verification Step E would
        // break the "Capture Sheet shows text → ClipboardFocus Logcat shows all
        // 4 state transitions" acceptance scenario.
        val names = ClipboardFocusState.entries.map { it.name }
        assertEquals(
            listOf("IDLE", "REQUESTING_FOCUS", "READING_CLIPBOARD", "RESTORING_FLAGS"),
            names
        )
    }

    @Test
    fun `resetToIdle method exists on state machine class`() {
        // Verifies the "second tap" fix surface. If someone removes or renames
        // resetToIdle(), the CaptureSheet collapse path loses its recovery hook
        // and the "second tap does nothing" bug returns.
        val method = ClipboardFocusStateMachine::class.java.methods
            .firstOrNull { it.name == "resetToIdle" && it.parameterCount == 0 }
        assertNotNull("ClipboardFocusStateMachine must expose resetToIdle()", method)
    }
}
