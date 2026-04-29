package com.capsule.app.ai.extract

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bonus JVM coverage for [ActionExtractionPrefilter] (companion to T042
 * impl; not numbered — listed in tasks.md status log).
 */
class ActionExtractionPrefilterTest {

    @Test
    fun blankIsRejected() {
        assertFalse(ActionExtractionPrefilter.shouldExtract(""))
        assertFalse(ActionExtractionPrefilter.shouldExtract("    \n  "))
    }

    @Test
    fun pureNoiseIsRejected() {
        assertFalse(ActionExtractionPrefilter.shouldExtract("just thinking out loud about life"))
    }

    @Test
    fun flightCodeMatches() {
        assertTrue(ActionExtractionPrefilter.shouldExtract("Flight UA437 confirmed"))
    }

    @Test
    fun weekdayMatches() {
        assertTrue(ActionExtractionPrefilter.shouldExtract("dinner with mom Friday"))
        assertTrue(ActionExtractionPrefilter.shouldExtract("call Tue about the proposal"))
    }

    @Test
    fun imperativeListMatches() {
        val md = """
            shopping:
            - milk
            - eggs
        """.trimIndent()
        assertTrue(ActionExtractionPrefilter.shouldExtract(md))
        assertTrue(ActionExtractionPrefilter.shouldExtract("1. Reply to Alice\n2. Pick up dry cleaning"))
    }

    @Test
    fun rsvpAndConfirmMatch() {
        assertTrue(ActionExtractionPrefilter.shouldExtract("Please RSVP by Sunday"))
        assertTrue(ActionExtractionPrefilter.shouldExtract("Confirm your reservation"))
    }

    @Test
    fun currencyMatches() {
        assertTrue(ActionExtractionPrefilter.shouldExtract("Charge of $42.99 pending"))
        assertTrue(ActionExtractionPrefilter.shouldExtract("Price: €15"))
    }

    @Test
    fun timeOfDayMatches() {
        assertTrue(ActionExtractionPrefilter.shouldExtract("see you at 3pm"))
        assertTrue(ActionExtractionPrefilter.shouldExtract("Standup 09:30"))
    }

    @Test
    fun relativeDateMatches() {
        assertTrue(ActionExtractionPrefilter.shouldExtract("tomorrow's deadline"))
        assertTrue(ActionExtractionPrefilter.shouldExtract("in 3 days"))
        assertTrue(ActionExtractionPrefilter.shouldExtract("next week we ship"))
    }
}
