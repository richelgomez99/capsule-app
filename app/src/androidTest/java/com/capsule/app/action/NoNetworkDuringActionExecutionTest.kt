package com.capsule.app.action

import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.StrictMode
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.capsule.app.action.handler.CalendarActionHandler
import com.capsule.app.action.handler.ShareActionHandler
import com.capsule.app.action.handler.TodoActionHandler
import com.capsule.app.data.ipc.AppFunctionSummaryParcel
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * T039 — Constitution Principle VI gate: action handlers in `:capture`
 * MUST NOT trigger any network I/O. Network egress is the sole
 * responsibility of `:net`, accessed by `:ml` only.
 *
 * Strategy:
 *   1. Install `StrictMode.setThreadPolicy(detectNetwork().penaltyDeath())`
 *      on the binder thread before invoking each handler. Any socket
 *      creation, DNS lookup, or HTTP I/O on this thread is converted to
 *      a `NetworkOnMainThreadException`-style fatal — observed as a
 *      thrown `Throwable` we let propagate.
 *   2. Invoke every handler in [ActionHandlerRegistry] with realistic
 *      args. Capture any startActivity dispatches via [ContextWrapper]
 *      so the test environment's missing Calendar app doesn't pollute
 *      the assertion.
 *   3. Assert: no handler threw (no network I/O attempted) AND every
 *      handler returned a structured [HandlerResult].
 *
 * Limitations: StrictMode only detects I/O on the thread that installed
 * the policy. A handler that posts to a different thread to do its
 * network work would evade detection. The [com.capsule.app.lint] rule
 * `NoHttpClientOutsideNet` catches that statically; this test is the
 * runtime backstop for the synchronous case (which is the only case the
 * `IActionExecutor` contract actually exercises — handlers run inline
 * on the binder thread).
 *
 * See specs/003-orbit-actions/contracts/action-execution-contract.md §6
 * and contracts/network-gateway-contract.md §1.
 */
@RunWith(AndroidJUnit4::class)
class NoNetworkDuringActionExecutionTest {

    private lateinit var savedPolicy: StrictMode.ThreadPolicy

    @Before
    fun setUp() {
        // Snapshot the prior policy so other tests aren't affected by our
        // strict overlay if they run in the same process.
        savedPolicy = StrictMode.getThreadPolicy()
    }

    @After
    fun tearDown() {
        StrictMode.setThreadPolicy(savedPolicy)
    }

    @Test
    fun calendarHandler_doesNotTriggerNetworkIo() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        installNoNetworkPolicy()
        val args = """{"title":"Sync","startEpochMillis":1700000000000}"""
        val result = runCatching {
            CalendarActionHandler().handle(ctx, calendarSkill(), args)
        }
        assertSucceededWithoutNetwork(result)
        assertTrue(
            "Calendar handler must produce a Dispatched or Failed outcome",
            result.getOrNull() is HandlerResult.Dispatched ||
                result.getOrNull() is HandlerResult.Failed
        )
    }

    @Test
    fun todoHandler_doesNotTriggerNetworkIo() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        installNoNetworkPolicy()
        // Stub returns Failed("handler_not_yet_implemented") — that's fine,
        // the contract being asserted here is "no network", not handler
        // completeness.
        val args = """{"title":"Buy milk"}"""
        val result = runCatching {
            TodoActionHandler().handle(ctx, todoSkill(), args)
        }
        assertSucceededWithoutNetwork(result)
        assertTrue(result.getOrNull() is HandlerResult.Failed)
    }

    @Test
    fun shareHandler_doesNotTriggerNetworkIo() = runTest {
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        installNoNetworkPolicy()
        val args = """{"targetMimeType":"text/plain"}"""
        val result = runCatching {
            ShareActionHandler().handle(ctx, shareSkill(), args)
        }
        assertSucceededWithoutNetwork(result)
        // v1.1: share.delegate is intentionally refused — must surface as
        // Failed("share_delegate_disabled_v1_1"), not silent and not
        // "no network" being the failure reason.
        val res = result.getOrNull()
        assertTrue(res is HandlerResult.Failed)
        assertEquals(
            "share_delegate_disabled_v1_1",
            (res as HandlerResult.Failed).reason
        )
    }

    @Test
    fun allRegisteredHandlers_doNotTriggerNetworkIo() = runTest {
        // Defence-in-depth: iterate every functionId returned by the
        // registry so a future skill addition gets the same gate without
        // the test author remembering to add a case.
        val ctx = CapturingContext(ApplicationProvider.getApplicationContext())
        for (fnId in ActionHandlerRegistry.knownFunctionIds()) {
            val handler = ActionHandlerRegistry.lookup(fnId)
                ?: error("registry inconsistent for $fnId")
            installNoNetworkPolicy()
            val args = sampleArgsFor(fnId)
            val result = runCatching {
                handler.handle(ctx, dummySkill(fnId), args)
            }
            assertFalse(
                "$fnId handler triggered network I/O: ${result.exceptionOrNull()}",
                result.isFailure
            )
        }
    }

    // ---- helpers ---------------------------------------------------------

    private fun installNoNetworkPolicy() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder()
                .detectNetwork()
                .penaltyDeath()
                .build()
        )
    }

    private fun assertSucceededWithoutNetwork(result: Result<HandlerResult>) {
        if (result.isFailure) {
            val cause = result.exceptionOrNull()
            // Surface the exact violation so a future regression points at
            // the offending socket/HTTP call.
            throw AssertionError(
                "Handler threw under detectNetwork().penaltyDeath() — " +
                    "this indicates a forbidden network call from :capture. " +
                    "Original: $cause",
                cause
            )
        }
    }

    private fun calendarSkill() = AppFunctionSummaryParcel(
        functionId = "calendar.createEvent",
        appPackage = "com.capsule.app",
        displayName = "Create event",
        description = "",
        schemaVersion = 1,
        argsSchemaJson = "{}",
        sideEffects = "EXTERNAL_DISPATCH",
        reversibility = "EXTERNAL_MANAGED",
        sensitivityScope = "PERSONAL",
        registeredAtMillis = 0L,
        updatedAtMillis = 0L
    )

    private fun todoSkill() = calendarSkill().copy(
        functionId = "tasks.createTodo",
        displayName = "Create todo"
    )

    private fun shareSkill() = calendarSkill().copy(
        functionId = "share.delegate",
        displayName = "Share",
        sensitivityScope = "SHARE_DELEGATED"
    )

    private fun dummySkill(fnId: String) = calendarSkill().copy(functionId = fnId)

    private fun sampleArgsFor(fnId: String): String = when (fnId) {
        "calendar.createEvent" ->
            """{"title":"Sync","startEpochMillis":1700000000000}"""
        "tasks.createTodo"     ->
            """{"title":"Buy milk"}"""
        "share.delegate"       ->
            """{"targetMimeType":"text/plain"}"""
        else                   -> "{}"
    }

    /** Swallows startActivity so missing-Calendar in test env doesn't fail. */
    private class CapturingContext(base: Context) : ContextWrapper(base) {
        override fun startActivity(intent: Intent) { /* no-op */ }
        override fun startActivity(intent: Intent, options: Bundle?) { /* no-op */ }
    }
}
