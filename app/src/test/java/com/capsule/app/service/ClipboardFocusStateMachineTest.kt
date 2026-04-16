package com.capsule.app.service

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for ClipboardFocusState enum transitions.
 * Full integration tests with WindowManager require instrumented tests on a physical device.
 */
class ClipboardFocusStateMachineTest {

    @Test
    fun `initial state is IDLE`() {
        assertEquals(ClipboardFocusState.IDLE, ClipboardFocusState.entries.first())
    }

    @Test
    fun `state enum has exactly 4 states`() {
        assertEquals(4, ClipboardFocusState.entries.size)
    }

    @Test
    fun `state transition order matches spec`() {
        val states = ClipboardFocusState.entries
        assertEquals(ClipboardFocusState.IDLE, states[0])
        assertEquals(ClipboardFocusState.REQUESTING_FOCUS, states[1])
        assertEquals(ClipboardFocusState.READING_CLIPBOARD, states[2])
        assertEquals(ClipboardFocusState.RESTORING_FLAGS, states[3])
    }
}
