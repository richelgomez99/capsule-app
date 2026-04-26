package com.capsule.app.ai.datetime

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Month
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * T035 — JVM tests for [DateTimeParser]. Anchors the parser against a
 * fixed instant + zone so tests are deterministic across machines and
 * survive DST transitions.
 *
 * Anchor: 2026-04-26T10:00:00 America/New_York (a Sunday).
 *  → DOW Sun, so "monday" → 2026-04-27, "next tuesday" → 2026-04-28, etc.
 */
class DateTimeParserTest {

    private val zone: ZoneId = ZoneId.of("America/New_York")
    private val anchor = ZonedDateTime.of(
        LocalDateTime.of(2026, Month.APRIL, 26, 10, 0),
        zone
    ).toInstant()

    private fun parse(raw: String): ZonedDateTime? = DateTimeParser.parse(raw, anchor, zone)

    @Test
    fun isoZonedRoundTrips() {
        val res = parse("2026-05-04T15:30:00-04:00")
        assertNotNull(res)
        assertEquals(2026, res!!.year)
        assertEquals(Month.MAY, res.month)
        assertEquals(4, res.dayOfMonth)
        assertEquals(15, res.hour)
        assertEquals(30, res.minute)
    }

    @Test
    fun isoUtcConvertsToZone() {
        val res = parse("2026-05-04T20:00:00Z")
        assertNotNull(res)
        // 20:00 UTC = 16:00 in America/New_York (EDT, UTC-4) on May 4.
        assertEquals(16, res!!.hour)
        assertEquals(zone, res.zone)
    }

    @Test
    fun naiveLocalDateTimeUsesZone() {
        val res = parse("2026-05-04T15:30")
        assertNotNull(res)
        assertEquals(15, res!!.hour)
        assertEquals(zone, res.zone)
    }

    @Test
    fun dateOnlyDefaultsTo9amLocal() {
        val res = parse("2026-05-04")
        assertNotNull(res)
        assertEquals(9, res!!.hour)
        assertEquals(0, res.minute)
        assertEquals(zone, res.zone)
    }

    @Test
    fun todayResolvesToAnchorDate() {
        val res = parse("today")
        assertNotNull(res)
        assertEquals(LocalDate.of(2026, 4, 26), res!!.toLocalDate())
        assertEquals(9, res.hour)
    }

    @Test
    fun tomorrowAddsOneDay() {
        val res = parse("tomorrow")
        assertNotNull(res)
        assertEquals(LocalDate.of(2026, 4, 27), res!!.toLocalDate())
    }

    @Test
    fun tomorrowAt3pm() {
        val res = parse("tomorrow at 3pm")
        assertNotNull(res)
        assertEquals(LocalDate.of(2026, 4, 27), res!!.toLocalDate())
        assertEquals(15, res.hour)
    }

    @Test
    fun mondayResolvesNextMonday() {
        // Anchor is Sunday → next Monday is one day forward.
        val res = parse("Monday")
        assertNotNull(res)
        assertEquals(DayOfWeek.MONDAY, res!!.dayOfWeek)
        assertEquals(LocalDate.of(2026, 4, 27), res.toLocalDate())
    }

    @Test
    fun nextTuesdayResolvesAprilTwentyEighth() {
        val res = parse("next tuesday")
        assertNotNull(res)
        assertEquals(DayOfWeek.TUESDAY, res!!.dayOfWeek)
        assertEquals(LocalDate.of(2026, 4, 28), res.toLocalDate())
    }

    @Test
    fun fridayAt5pm() {
        val res = parse("friday at 5pm")
        assertNotNull(res)
        assertEquals(DayOfWeek.FRIDAY, res!!.dayOfWeek)
        assertEquals(17, res.hour)
        assertEquals(0, res.minute)
    }

    @Test
    fun thisSundayWithSundayAnchorReturnsToday() {
        val res = parse("this sunday")
        assertNotNull(res)
        assertEquals(DayOfWeek.SUNDAY, res!!.dayOfWeek)
        assertEquals(LocalDate.of(2026, 4, 26), res.toLocalDate())
    }

    @Test
    fun inThreeDays() {
        val res = parse("in 3 days")
        assertNotNull(res)
        assertEquals(LocalDate.of(2026, 4, 29), res!!.toLocalDate())
        assertEquals(9, res.hour)
    }

    @Test
    fun inTwoHoursIsRelativeToAnchor() {
        val res = parse("in 2 hours")
        assertNotNull(res)
        assertEquals(LocalDate.of(2026, 4, 26), res!!.toLocalDate())
        assertEquals(12, res.hour)
        assertEquals(0, res.minute)
    }

    @Test
    fun unparseableReturnsNull() {
        assertNull(parse("yo what's up"))
        assertNull(parse(""))
        assertNull(parse("   "))
        assertNull(parse("the day after the eclipse"))
    }

    @Test
    fun dstSpringForwardCrossing() {
        // 2026 US DST starts Sun Mar 8. Anchor at Sat Mar 7 noon EST,
        // ask for "tomorrow at 3am" — that hour is skipped.
        val springAnchor = ZonedDateTime.of(
            LocalDateTime.of(2026, Month.MARCH, 7, 12, 0),
            zone
        ).toInstant()
        val res = DateTimeParser.parse("tomorrow at 3am", springAnchor, zone)
        assertNotNull(res)
        // 3am local on Mar 8 doesn't exist; java.time picks the next
        // valid hour or shifts forward — we just assert the result is
        // on the correct date and zone-correct (no silent wrong day).
        assertEquals(LocalDate.of(2026, 3, 8), res!!.toLocalDate())
        assertEquals(zone, res.zone)
    }

    @Test
    fun ampmEdgeCases() {
        val noon = parse("today at 12pm")!!
        assertEquals(12, noon.hour)
        val midnight = parse("today at 12am")!!
        assertEquals(0, midnight.hour)
    }

    @Test
    fun caseInsensitive() {
        val res = parse("TOMORROW AT 9AM")
        assertNotNull(res)
        assertEquals(9, res!!.hour)
    }

    @Test
    fun resultZoneAlwaysMatchesArgument() {
        val pacific = ZoneId.of("America/Los_Angeles")
        val res = DateTimeParser.parse("tomorrow", anchor, pacific)
        assertNotNull(res)
        assertEquals(pacific, res!!.zone)
    }
}
