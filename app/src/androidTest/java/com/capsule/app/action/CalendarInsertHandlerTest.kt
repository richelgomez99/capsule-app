package com.capsule.app.action

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.action.handler.CalendarActionHandler
import com.capsule.app.data.ipc.AppFunctionSummaryParcel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T037 — verifies [CalendarActionHandler] (T049) dispatches the correct
 * `Intent.ACTION_INSERT` against [CalendarContract.Events] with all
 * required + optional extras. Uses a [ContextWrapper] capture rather
 * than the deprecated `IntentTestRule` (which requires an Activity in
 * scope) — same observable contract, simpler harness.
 *
 * Required-args + default-end-time behaviour traces to
 * specs/003-orbit-actions/research.md §4.
 */
@RunWith(AndroidJUnit4::class)
class CalendarInsertHandlerTest {

    private val handler = CalendarActionHandler()
    private val skill = sampleSkill()

    @Test
    fun handle_required_only_dispatches_insert_with_default_one_hour_end_time() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        val start = 1_700_000_000_000L
        val args = """{"title":"Team sync","startEpochMillis":$start}"""

        val result = handler.handle(ctx, skill, args)

        assertTrue(
            "expected Dispatched, got=$result",
            result is HandlerResult.Dispatched
        )
        val captured = assertSingleIntent(ctx)

        assertEquals(Intent.ACTION_INSERT, captured.action)
        assertEquals(CalendarContract.Events.CONTENT_URI, captured.data)
        assertTrue(
            "FLAG_ACTIVITY_NEW_TASK must be set for handler dispatch from non-Activity context",
            (captured.flags and Intent.FLAG_ACTIVITY_NEW_TASK) != 0
        )
        assertEquals("Team sync", captured.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(
            start,
            captured.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L)
        )
        // Research §4: end-time defaults to start + 1h when Nano didn't extract one.
        assertEquals(
            start + 60L * 60L * 1000L,
            captured.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L)
        )
        // Optional extras must be absent when not provided.
        assertNull(captured.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertNull(captured.getStringExtra(CalendarContract.Events.DESCRIPTION))
        assertNull(captured.getStringExtra(CalendarContract.Events.EVENT_TIMEZONE))
    }

    @Test
    fun handle_full_args_propagates_all_optional_extras() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        val start = 1_700_000_000_000L
        val end = start + 90L * 60L * 1000L
        val args = """
            {
              "title":"Lunch with Sam",
              "startEpochMillis":$start,
              "endEpochMillis":$end,
              "location":"Cafe Nero, Mission",
              "notes":"Discuss Q3 plan",
              "tzId":"America/Los_Angeles"
            }
        """.trimIndent()

        val result = handler.handle(ctx, skill, args)
        assertTrue(result is HandlerResult.Dispatched)

        val captured = assertSingleIntent(ctx)
        assertEquals("Lunch with Sam", captured.getStringExtra(CalendarContract.Events.TITLE))
        assertEquals(start, captured.getLongExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, -1L))
        assertEquals(end, captured.getLongExtra(CalendarContract.EXTRA_EVENT_END_TIME, -1L))
        assertEquals(
            "Cafe Nero, Mission",
            captured.getStringExtra(CalendarContract.Events.EVENT_LOCATION)
        )
        assertEquals(
            "Discuss Q3 plan",
            captured.getStringExtra(CalendarContract.Events.DESCRIPTION)
        )
        assertEquals(
            "America/Los_Angeles",
            captured.getStringExtra(CalendarContract.Events.EVENT_TIMEZONE)
        )
    }

    @Test
    fun handle_missing_title_returns_failed_without_dispatch() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        val args = """{"startEpochMillis":1700000000000}"""

        val result = handler.handle(ctx, skill, args)

        assertTrue(result is HandlerResult.Failed)
        assertEquals("missing_title", (result as HandlerResult.Failed).reason)
        assertEquals(0, ctx.captured.size)
    }

    @Test
    fun handle_blank_title_returns_failed_without_dispatch() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        val args = """{"title":"   ","startEpochMillis":1700000000000}"""

        val result = handler.handle(ctx, skill, args)

        assertTrue(result is HandlerResult.Failed)
        assertEquals("missing_title", (result as HandlerResult.Failed).reason)
        assertEquals(0, ctx.captured.size)
    }

    @Test
    fun handle_missing_start_time_returns_failed_without_dispatch() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        val args = """{"title":"Team sync"}"""

        val result = handler.handle(ctx, skill, args)

        assertTrue(result is HandlerResult.Failed)
        assertEquals("missing_start_time", (result as HandlerResult.Failed).reason)
        assertEquals(0, ctx.captured.size)
    }

    @Test
    fun handle_unparseable_argsJson_returns_failed_without_dispatch() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        val args = "not valid json {{{"

        val result = handler.handle(ctx, skill, args)

        assertTrue(result is HandlerResult.Failed)
        assertEquals("args_parse_failed", (result as HandlerResult.Failed).reason)
        assertNotNull(result.cause)
        assertEquals(0, ctx.captured.size)
    }

    @Test
    fun handle_blank_optional_strings_are_skipped() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        val start = 1_700_000_000_000L
        // Empty strings for optional fields must NOT be propagated as
        // empty extras — verifies the `takeIf { it.isNotBlank() }` guards.
        val args = """
            {
              "title":"Sync",
              "startEpochMillis":$start,
              "location":"",
              "notes":"   ",
              "tzId":""
            }
        """.trimIndent()

        val result = handler.handle(ctx, skill, args)
        assertTrue(result is HandlerResult.Dispatched)

        val captured = assertSingleIntent(ctx)
        assertNull(captured.getStringExtra(CalendarContract.Events.EVENT_LOCATION))
        assertNull(captured.getStringExtra(CalendarContract.Events.DESCRIPTION))
        assertNull(captured.getStringExtra(CalendarContract.Events.EVENT_TIMEZONE))
    }

    @Test
    fun handle_no_calendar_app_installed_returns_failed_no_handler() = runTest {
        val ctx = ThrowingContext(
            ApplicationProvider.getApplicationContext(),
            android.content.ActivityNotFoundException("no calendar app")
        )
        val args = """{"title":"Sync","startEpochMillis":1700000000000}"""

        val result = handler.handle(ctx, skill, args)

        assertTrue(result is HandlerResult.Failed)
        assertEquals("no_handler", (result as HandlerResult.Failed).reason)
        assertNotNull(result.cause)
    }

    @Test
    fun handle_security_exception_surfaces_structured_failure() = runTest {
        val ctx = ThrowingContext(
            ApplicationProvider.getApplicationContext(),
            SecurityException("oem permission denied")
        )
        val args = """{"title":"Sync","startEpochMillis":1700000000000}"""

        val result = handler.handle(ctx, skill, args)

        assertTrue(result is HandlerResult.Failed)
        assertEquals("security_exception", (result as HandlerResult.Failed).reason)
        assertNotNull(result.cause)
    }

    // ---- helpers ---------------------------------------------------------

    private fun assertSingleIntent(ctx: CapturingContext): Intent {
        assertEquals(
            "expected exactly one startActivity dispatch",
            1,
            ctx.captured.size
        )
        return ctx.captured.single()
    }

    private fun sampleSkill() = AppFunctionSummaryParcel(
        functionId = "calendar.createEvent",
        appPackage = "com.capsule.app",
        displayName = "Create calendar event",
        description = "test fixture",
        schemaVersion = 1,
        argsSchemaJson = "{}",
        sideEffects = "EXTERNAL_DISPATCH",
        reversibility = "EXTERNAL_MANAGED",
        sensitivityScope = "PERSONAL",
        registeredAtMillis = 0L,
        updatedAtMillis = 0L
    )

    /**
     * Captures every `startActivity` call without launching the foreign
     * Calendar app under test. Avoids the device-dependency of an actual
     * Calendar resolution while still exercising the full Intent-build
     * codepath in [CalendarActionHandler].
     */
    private class CapturingContext(base: Context) : ContextWrapper(base) {
        val captured = mutableListOf<Intent>()
        override fun startActivity(intent: Intent) {
            captured += intent
        }
        override fun startActivity(intent: Intent, options: Bundle?) {
            captured += intent
        }
    }

    /** Forces a specific exception on dispatch to exercise the failure paths. */
    private class ThrowingContext(base: Context, private val toThrow: RuntimeException) :
        ContextWrapper(base) {
        override fun startActivity(intent: Intent) {
            throw toThrow
        }
        override fun startActivity(intent: Intent, options: Bundle?) {
            throw toThrow
        }
    }
}
