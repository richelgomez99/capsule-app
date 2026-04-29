package com.capsule.app.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * T079 / 003 US4 — JVM unit tests for the pure stats formatter feeding
 * the Compose Settings → Actions screen. Locks the contract referenced
 * by ActionsSettingsTest (instrumented).
 */
class ActionsSettingsFormatTest {

    @Test
    fun formatStats_neverUsed_whenInvocationCountZero() {
        val row = row(invocationCount = 0)
        assertEquals("Never used", ActionsSettingsFormat.formatStats(row))
    }

    @Test
    fun formatStats_happyPath_renders_percent_and_latency() {
        val row = row(
            invocationCount = 4,
            successRate = 0.75,
            cancelRate = 0.25,
            avgLatencyMs = 250.0
        )
        assertEquals(
            "75% success • 25% cancelled • 250 ms • 4 run(s)",
            ActionsSettingsFormat.formatStats(row)
        )
    }

    @Test
    fun formatStats_emDashesWhenStatsAreNull() {
        val row = row(
            invocationCount = 1,
            successRate = null,
            cancelRate = null,
            avgLatencyMs = null
        )
        assertEquals("— • — • — • 1 run(s)", ActionsSettingsFormat.formatStats(row))
    }

    @Test
    fun formatStats_truncatesLatencyToInt() {
        val row = row(
            invocationCount = 6,
            successRate = 4.0 / 6.0,
            cancelRate = 1.0 / 6.0,
            avgLatencyMs = 283.3333
        )
        // 4/6 = 0.6666… → 66%, 1/6 = 0.1666… → 16% (toInt truncates).
        assertEquals(
            "66% success • 16% cancelled • 283 ms • 6 run(s)",
            ActionsSettingsFormat.formatStats(row)
        )
    }

    private fun row(
        invocationCount: Int,
        successRate: Double? = 1.0,
        cancelRate: Double? = 0.0,
        avgLatencyMs: Double? = 100.0
    ): SkillSettingsRow = SkillSettingsRow(
        skillId = "calendar.createEvent",
        displayName = "Create event",
        enabled = true,
        invocationCount = invocationCount,
        successRate = successRate,
        cancelRate = cancelRate,
        avgLatencyMs = avgLatencyMs
    )
}
