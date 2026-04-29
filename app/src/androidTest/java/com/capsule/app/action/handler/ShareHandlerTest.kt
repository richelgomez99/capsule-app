package com.capsule.app.action.handler

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.action.HandlerResult
import com.capsule.app.data.ipc.AppFunctionSummaryParcel
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T057 (003 US2) — verifies [ShareActionHandler] is intentionally
 * disabled in v1.1 per the Principle XI consent gate (deferred to
 * spec 008 — the Orbit Agent — for the SHARE_DELEGATED scope mediation).
 *
 * Per [ShareActionHandler]'s own kdoc: "Until then this handler always
 * returns FAILED with a stable reason so the UI can render the
 * 'Share-out is disabled in v1.1' toast."
 *
 * This test pins the contract: regardless of args, the handler returns
 * `Failed(reason = "share_delegate_disabled_v1_1")`. If/when v1.2 lifts
 * the gate, this test will need to be updated alongside the new
 * implementation.
 */
@RunWith(AndroidJUnit4::class)
class ShareHandlerTest {

    private val handler = ShareActionHandler()
    private val ctx get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun handle_anyArgs_returnsFailed_withStableReason() = runTest {
        val args = """
            {"text":"hello world","subject":"Greeting","mimeType":"text/plain"}
        """.trimIndent()

        val result = handler.handle(ctx, sampleSkill(), args)

        assertTrue("expected Failed, got=$result", result is HandlerResult.Failed)
        assertEquals(
            "share_delegate_disabled_v1_1",
            (result as HandlerResult.Failed).reason
        )
    }

    @Test
    fun handle_emptyArgs_alsoReturnsFailed() = runTest {
        val result = handler.handle(ctx, sampleSkill(), "{}")
        assertTrue(result is HandlerResult.Failed)
        assertEquals(
            "share_delegate_disabled_v1_1",
            (result as HandlerResult.Failed).reason
        )
    }

    @Test
    fun handle_malformedArgs_stillReturnsSameFailedReason() = runTest {
        // Distinct from CalendarActionHandler / TodoActionHandler which
        // return "args_parse_failed" for malformed JSON. ShareHandler
        // short-circuits before parsing, surfacing the disabled reason
        // unconditionally so the UI message stays consistent.
        val result = handler.handle(ctx, sampleSkill(), "not json at all")
        assertTrue(result is HandlerResult.Failed)
        assertEquals(
            "share_delegate_disabled_v1_1",
            (result as HandlerResult.Failed).reason
        )
    }

    private fun sampleSkill() = AppFunctionSummaryParcel(
        functionId = "share.delegate",
        appPackage = "com.capsule.app",
        displayName = "Share to…",
        description = "test fixture",
        schemaVersion = 1,
        argsSchemaJson = "{}",
        sideEffects = "EXTERNAL_DISPATCH",
        reversibility = "EXTERNAL_MANAGED",
        sensitivityScope = "SHARE_DELEGATED",
        registeredAtMillis = 0L,
        updatedAtMillis = 0L
    )
}
