package com.capsule.app.diary

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * T052 unit tests for the [parseCalendarArgs] / [parseIsoLocalToEpochMillis]
 * helpers backing [ActionPreviewSheet]. These are pure JVM logic — no
 * Compose dependency — so they live in the JVM unit test source set.
 */
class ActionPreviewCardArgsTest {

    @Test
    fun parseCalendarArgs_fullArgs_roundTripsAllFields() {
        val zone = ZoneId.of("America/New_York")
        // 2026-04-20 09:30 in America/New_York → epoch millis.
        val startMillis = java.time.LocalDateTime.of(2026, 4, 20, 9, 30)
            .atZone(zone).toInstant().toEpochMilli()
        val endMillis = startMillis + 60 * 60 * 1000L
        val argsJson = """
            {"title":"Coffee with Sam",
             "startEpochMillis":$startMillis,
             "endEpochMillis":$endMillis,
             "location":"Blue Bottle",
             "notes":"Bring laptop",
             "tzId":"America/New_York"}
        """.trimIndent()

        val parsed = parseCalendarArgs(argsJson)

        assertEquals("Coffee with Sam", parsed.title)
        assertEquals("2026-04-20T09:30", parsed.startIso)
        assertEquals("2026-04-20T10:30", parsed.endIso)
        assertEquals("Blue Bottle", parsed.location)
        assertEquals("Bring laptop", parsed.notes)
        assertEquals("America/New_York", parsed.tzId)
    }

    @Test
    fun parseCalendarArgs_missingOptionalFields_yieldsBlankPlaceholders() {
        val parsed = parseCalendarArgs("""{"title":"X"}""")
        assertEquals("X", parsed.title)
        assertEquals("", parsed.startIso)
        assertEquals("", parsed.endIso)
        assertEquals("", parsed.location)
        assertEquals("", parsed.notes)
        // Falls back to system default rather than throwing.
        assertEquals(ZoneId.systemDefault().id, parsed.tzId)
    }

    @Test
    fun parseCalendarArgs_invalidTzId_fallsBackToSystemDefault() {
        val parsed = parseCalendarArgs("""{"title":"X","tzId":"Mars/Olympus"}""")
        assertEquals(ZoneId.systemDefault().id, parsed.tzId)
    }

    @Test
    fun parseCalendarArgs_malformedJson_returnsBlankFormState() {
        val parsed = parseCalendarArgs("not a json blob")
        assertEquals("", parsed.title)
        assertEquals("", parsed.startIso)
        assertEquals(ZoneId.systemDefault().id, parsed.tzId)
    }

    @Test
    fun parseIsoLocalToEpochMillis_validInput_roundTripsThroughZone() {
        val zone = ZoneId.of("UTC")
        val millis = parseIsoLocalToEpochMillis("2026-04-20T09:30", zone)
        assertEquals(
            java.time.LocalDateTime.of(2026, 4, 20, 9, 30)
                .atZone(zone).toInstant().toEpochMilli(),
            millis
        )
    }

    @Test
    fun parseIsoLocalToEpochMillis_blank_returnsNull() {
        assertNull(parseIsoLocalToEpochMillis("", ZoneId.systemDefault()))
        assertNull(parseIsoLocalToEpochMillis("   ", ZoneId.systemDefault()))
    }

    @Test
    fun parseIsoLocalToEpochMillis_unparseable_returnsNull() {
        assertNull(parseIsoLocalToEpochMillis("not a date", ZoneId.systemDefault()))
        assertNull(parseIsoLocalToEpochMillis("2026-13-99T99:99", ZoneId.systemDefault()))
    }

    @Test
    fun parseCalendarArgs_zoneSensitive_startIsoReflectsTzId() {
        // Same epoch instant, two different zones, two different ISO renders.
        val instant = java.time.Instant.parse("2026-04-20T13:00:00Z")
        val baseJson = """{"title":"X","startEpochMillis":${instant.toEpochMilli()},"tzId":"%s"}"""

        val ny = parseCalendarArgs(baseJson.format("America/New_York"))
        val tokyo = parseCalendarArgs(baseJson.format("Asia/Tokyo"))

        // 13:00 UTC = 09:00 EDT (April → DST) ; 22:00 in Tokyo (UTC+9).
        assertEquals("2026-04-20T09:00", ny.startIso)
        assertEquals("2026-04-20T22:00", tokyo.startIso)
        assertTrue(ny.startIso != tokyo.startIso)
    }
}
