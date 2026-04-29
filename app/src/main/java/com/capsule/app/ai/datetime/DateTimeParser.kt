package com.capsule.app.ai.datetime

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters

/**
 * Pure java.time-based date/time parser that resolves user-typed or
 * Nano-emitted date strings into a concrete [ZonedDateTime] anchored
 * to the source envelope's `createdAt` and the device timezone.
 *
 * Contract — see specs/003-orbit-actions/research.md §3:
 * - Try ISO 8601 first (handles Nano's preferred output).
 * - Then relative tokens: today / tomorrow / yesterday.
 * - Then weekday tokens: "Monday", "Mon", "next Tuesday", "this Fri".
 * - Then "in N days" / "in N hours" / "in N weeks".
 * - Times default to 09:00 local when only a date is provided.
 * - Returns null on unparseable input — caller drops the proposal.
 *
 * Determinism: caller provides the anchor instant + zone (no system clock
 * read). DST boundaries are honoured by [ZonedDateTime] arithmetic.
 */
object DateTimeParser {

    /**
     * @param raw user/Nano-supplied phrase. Trimmed; case-insensitive.
     * @param anchor envelope `createdAt` instant — the "now" relative
     *               phrases resolve against.
     * @param zone device timezone — must equal [ZoneId.systemDefault()]
     *             at call time per research §3 ("never silently shift
     *             dates across DST boundaries").
     * @return resolved [ZonedDateTime] or null when the phrase is not
     *         understood.
     */
    fun parse(
        raw: String,
        anchor: Instant,
        zone: ZoneId
    ): ZonedDateTime? {
        val text = raw.trim()
        if (text.isEmpty()) return null

        val anchorZdt = anchor.atZone(zone)
        val anchorDate = anchorZdt.toLocalDate()

        // 1. ISO 8601 fast paths.
        parseIso(text, zone)?.let { return it }

        val lower = text.lowercase()

        // 2. "today" / "tomorrow" / "yesterday" optionally + " at HH:MM".
        val (relDay, restAfterRel) = matchRelativeDay(lower, anchorDate)
        if (relDay != null) {
            val time = matchTimeIn(restAfterRel) ?: DEFAULT_TIME
            return ZonedDateTime.of(relDay, time, zone)
        }

        // 3. "in N days|hours|weeks".
        matchInN(lower, anchorZdt)?.let { return it }

        // 4. weekday tokens: "monday", "mon", "next tue", "this fri", optionally + " at HH:MM".
        val (wkDate, restAfterWk) = matchWeekday(lower, anchorDate) ?: (null to "")
        if (wkDate != null) {
            val time = matchTimeIn(restAfterWk) ?: DEFAULT_TIME
            return ZonedDateTime.of(wkDate, time, zone)
        }

        return null
    }

    // ---- helpers ----

    private val DEFAULT_TIME: LocalTime = LocalTime.of(9, 0)

    private fun parseIso(text: String, zone: ZoneId): ZonedDateTime? {
        // Offset/zoned form first: "2026-05-04T15:30:00Z", "...+02:00".
        runCatching {
            return ZonedDateTime.parse(text)
        }.onFailure { /* fall through */ }
        runCatching {
            val odt = java.time.OffsetDateTime.parse(text)
            return odt.atZoneSameInstant(zone)
        }.onFailure { /* fall through */ }
        // Naive datetime: "2026-05-04T15:30" — interpreted in caller zone.
        runCatching {
            val ldt = LocalDateTime.parse(text)
            return ZonedDateTime.of(ldt, zone)
        }.onFailure { /* fall through */ }
        // Date-only: "2026-05-04" — interpreted as 09:00 caller zone.
        runCatching {
            val ld = LocalDate.parse(text)
            return ZonedDateTime.of(ld, DEFAULT_TIME, zone)
        }.onFailure { /* fall through */ }
        return null
    }

    private fun matchRelativeDay(lower: String, anchorDate: LocalDate): Pair<LocalDate?, String> {
        val patterns = listOf(
            "today" to anchorDate,
            "tomorrow" to anchorDate.plusDays(1),
            "yesterday" to anchorDate.minusDays(1),
            "tonight" to anchorDate
        )
        for ((token, date) in patterns) {
            if (lower == token) return date to ""
            if (lower.startsWith("$token ") || lower.startsWith("$token,")) {
                return date to lower.removePrefix(token).trim().trimStart(',').trim()
            }
        }
        return null to lower
    }

    private val IN_N_REGEX = Regex("""^in\s+(\d{1,3})\s+(minute|minutes|hour|hours|day|days|week|weeks)\b""")

    private fun matchInN(lower: String, anchor: ZonedDateTime): ZonedDateTime? {
        val m = IN_N_REGEX.find(lower) ?: return null
        val n = m.groupValues[1].toLongOrNull() ?: return null
        return when (m.groupValues[2]) {
            "minute", "minutes" -> anchor.plusMinutes(n)
            "hour", "hours"     -> anchor.plusHours(n)
            "day", "days"       -> anchor.plusDays(n).withHour(DEFAULT_TIME.hour).withMinute(0).withSecond(0).withNano(0)
            "week", "weeks"     -> anchor.plusWeeks(n).withHour(DEFAULT_TIME.hour).withMinute(0).withSecond(0).withNano(0)
            else -> null
        }
    }

    private val WEEKDAY_TOKENS: Map<String, DayOfWeek> = mapOf(
        "monday" to DayOfWeek.MONDAY, "mon" to DayOfWeek.MONDAY,
        "tuesday" to DayOfWeek.TUESDAY, "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY,
        "wednesday" to DayOfWeek.WEDNESDAY, "wed" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY,
        "friday" to DayOfWeek.FRIDAY, "fri" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sat" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY, "sun" to DayOfWeek.SUNDAY
    )

    private fun matchWeekday(lower: String, anchorDate: LocalDate): Pair<LocalDate, String>? {
        // Forms: "<weekday>", "<weekday> at HH:MM", "next <weekday>", "this <weekday>".
        val tokens = lower.split(Regex("\\s+"), limit = 4)
        if (tokens.isEmpty()) return null

        val (qualifier, weekdayToken, rest) = when {
            tokens.size >= 2 && (tokens[0] == "next" || tokens[0] == "this") && WEEKDAY_TOKENS.containsKey(tokens[1]) ->
                Triple(tokens[0], tokens[1], tokens.drop(2).joinToString(" "))
            WEEKDAY_TOKENS.containsKey(tokens[0]) ->
                Triple("plain", tokens[0], tokens.drop(1).joinToString(" "))
            else -> return null
        }

        val target = WEEKDAY_TOKENS[weekdayToken]!!
        val date = when (qualifier) {
            "next" -> anchorDate.with(TemporalAdjusters.next(target))
            "this" -> {
                // "this <weekday>" → upcoming occurrence in current week. If
                // today is the same weekday, return today (not next week).
                if (anchorDate.dayOfWeek == target) anchorDate
                else anchorDate.with(TemporalAdjusters.nextOrSame(target))
            }
            else -> anchorDate.with(TemporalAdjusters.nextOrSame(target))
        }
        return date to rest.trim().trimStart(',').trim()
    }

    private val TIME_REGEX = Regex(
        """\b(?:at\s+)?(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\b""",
        RegexOption.IGNORE_CASE
    )

    private fun matchTimeIn(rest: String): LocalTime? {
        if (rest.isBlank()) return null
        val m = TIME_REGEX.find(rest) ?: return null
        val hour = m.groupValues[1].toIntOrNull() ?: return null
        val minute = m.groupValues[2].toIntOrNull() ?: 0
        val mer = m.groupValues[3].lowercase()
        val resolvedHour = when (mer) {
            "pm" -> if (hour == 12) 12 else (hour + 12)
            "am" -> if (hour == 12) 0 else hour
            else -> hour
        }
        return runCatching { LocalTime.of(resolvedHour, minute) }.getOrNull()
    }
}
